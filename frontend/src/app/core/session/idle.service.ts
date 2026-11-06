import { inject, Injectable, OnDestroy, signal } from '@angular/core';
import { Router } from '@angular/router';

import type { SessionStatus } from '../../api/bff/models';
import { SessionChannel, SessionMessage } from './session-channel';
import { SessionService } from './session.service';

/** El aviso de inactividad sale cuando faltan 2 minutos para que venza la sesión (001-22, WCAG 2.2.1). */
export const IDLE_WARNING_SECONDS = 120;
/** Cerca de las 8 horas, la persona ya no puede extender la sesión: se le avisa con más margen. */
export const ABSOLUTE_WARNING_SECONDS = 300;

const SECOND = 1000;
const RETRY_AFTER_FAILURE_MS = 30 * SECOND;

/**
 * Qué se le avisa a la persona. `idle` se puede evitar con «Seguir conectado»; `absolute` es el límite
 * de 8 horas del BFF y solo se puede aceptar: guardar lo que haga falta y salir.
 */
export interface SessionWarning {
  readonly kind: 'idle' | 'absolute';
  readonly secondsLeft: number;
}

/**
 * Vigila cuánto le queda a la sesión y avisa antes de que venza (001-22).
 *
 * - La autoridad es el BFF: se usan los segundos que informa `/bff/session`, que no extiende la sesión.
 * - **No hace peticiones de fondo:** no hay sondeo. Programa una sola consulta para cuando toque avisar y
 *   otra al volver a ver la pestaña (los temporizadores se frenan en segundo plano); ambas van al BFF,
 *   nunca a `/api`. Esa consulta es la que dice si hubo actividad en otra pestaña antes de avisar o cerrar.
 * - Las pestañas se sincronizan con `BroadcastChannel`: seguir conectado o salir en una vale para todas.
 *
 * La pantalla (T22) lee `warning` y llama a `extend()` o `logout()`.
 */
@Injectable({ providedIn: 'root' })
export class IdleService implements OnDestroy {
  private readonly session = inject(SessionService);
  private readonly channel = inject(SessionChannel);
  private readonly router = inject(Router);

  private readonly current = signal<SessionWarning | undefined>(undefined);
  /** El aviso vigente; `undefined` si no hay que avisar nada. */
  readonly warning = this.current.asReadonly();

  private watching = false;
  private checking = false;
  /** Instantes (ms desde 1970) en que vencen la inactividad y el límite absoluto, según el BFF. */
  private idleDeadline = 0;
  private absoluteDeadline = 0;
  private warningDeadline = 0;
  private checkTimer?: ReturnType<typeof setTimeout>;
  private countdownTimer?: ReturnType<typeof setInterval>;
  private unlisten?: () => void;

  private readonly onVisibilityChange = () => {
    if (this.watching && document.visibilityState === 'visible') {
      void this.check();
    }
  };

  /** Empieza a vigilar con lo que informó el BFF. Llamarlo de nuevo reemplaza la vigilancia anterior. */
  watch(status: SessionStatus): void {
    this.stop();
    this.watching = true;
    this.unlisten = this.channel.listen((message) => this.onMessage(message));
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.setDeadlines(status);
    this.schedule();
  }

  /**
   * «Seguir conectado»: el BFF renueva la sesión y las demás pestañas cierran su aviso. Devuelve `false`
   * si no se pudo (el aviso sigue y se puede intentar de nuevo) o si la sesión ya no existe (va a iniciar
   * sesión).
   */
  async extend(): Promise<boolean> {
    let status: SessionStatus | null;
    try {
      status = await this.session.keepAlive();
    } catch {
      return false;
    }
    if (!status) {
      this.expire();
      return false;
    }
    this.apply(status);
    this.channel.post({ type: 'extended', status });
    return true;
  }

  /** Cierra la sesión (001-09) y deja de vigilar. */
  async logout(): Promise<void> {
    this.stop();
    await this.session.logout();
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private stop(): void {
    this.watching = false;
    this.checking = false;
    clearTimeout(this.checkTimer);
    clearInterval(this.countdownTimer);
    this.checkTimer = this.countdownTimer = undefined;
    this.unlisten?.();
    this.unlisten = undefined;
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    this.current.set(undefined);
  }

  private onMessage(message: SessionMessage): void {
    if (!this.watching) {
      return;
    }
    if (message.type === 'extended') {
      this.apply(message.status);
    } else {
      this.stop();
      this.session.redirectToLogin('/');
    }
  }

  private setDeadlines(status: SessionStatus): void {
    const now = Date.now();
    this.idleDeadline = now + status.idleSecondsLeft * SECOND;
    this.absoluteDeadline = now + status.absoluteSecondsLeft * SECOND;
  }

  /** Programa la consulta para cuando toque avisar, lo que ocurra primero entre los dos avisos. */
  private schedule(): void {
    clearTimeout(this.checkTimer);
    const warnAt = Math.min(
      this.idleDeadline - IDLE_WARNING_SECONDS * SECOND,
      this.absoluteDeadline - ABSOLUTE_WARNING_SECONDS * SECOND,
    );
    this.checkTimer = setTimeout(() => void this.check(), Math.max(0, warnAt - Date.now()));
  }

  /** Pregunta al BFF, sin extender la sesión, y decide: avisar, cerrar o volver a esperar. */
  private async check(): Promise<void> {
    if (!this.watching || this.checking) {
      return;
    }
    this.checking = true;
    let status: SessionStatus | null;
    try {
      status = await this.session.status();
    } catch {
      // Sin respuesta no se sabe nada: ni avisar ni cerrar. Se vuelve a preguntar más tarde.
      if (this.watching) {
        this.checking = false;
        clearTimeout(this.checkTimer);
        this.checkTimer = setTimeout(() => void this.check(), RETRY_AFTER_FAILURE_MS);
      }
      return;
    }
    if (!this.watching) {
      return;
    }
    this.checking = false;
    if (status) {
      this.apply(status);
    } else {
      this.expire();
    }
  }

  private apply(status: SessionStatus): void {
    this.setDeadlines(status);
    const { idleSecondsLeft: idle, absoluteSecondsLeft: absolute } = status;
    if (idle <= 0 || absolute <= 0) {
      this.expire();
    } else if (idle <= IDLE_WARNING_SECONDS && idle < absolute) {
      this.warn('idle', idle);
    } else if (absolute <= ABSOLUTE_WARNING_SECONDS) {
      this.warn('absolute', absolute);
    } else {
      this.clearWarning();
      this.schedule();
    }
  }

  private warn(kind: SessionWarning['kind'], secondsLeft: number): void {
    clearTimeout(this.checkTimer);
    clearInterval(this.countdownTimer);
    this.warningDeadline = Date.now() + secondsLeft * SECOND;
    this.current.set({ kind, secondsLeft });
    // La cuenta regresiva es local: no pide nada al servidor. Al llegar a cero se vuelve a preguntar.
    this.countdownTimer = setInterval(() => {
      const left = Math.ceil((this.warningDeadline - Date.now()) / SECOND);
      if (left > 0) {
        this.current.set({ kind, secondsLeft: left });
      } else {
        clearInterval(this.countdownTimer);
        this.countdownTimer = undefined;
        void this.check();
      }
    }, SECOND);
  }

  private clearWarning(): void {
    clearInterval(this.countdownTimer);
    this.countdownTimer = undefined;
    this.current.set(undefined);
  }

  /** La sesión terminó: iniciar sesión de nuevo y volver a la ruta actual. */
  private expire(): void {
    this.stop();
    this.session.redirectToLogin(this.router.url);
  }
}
