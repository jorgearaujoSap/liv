import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { ButtonComponent, DialogComponent } from 'liv-components';

import { IdleService } from '../../core/session/idle.service';

/**
 * Aviso de sesión por expirar (001-22, WCAG 2.2.1). Lee `IdleService.warning` y deja elegir:
 *
 * - por inactividad (2 minutos antes): «Seguir conectado» o «Cerrar sesión»;
 * - por el límite de 8 horas (5 minutos antes): solo «Cerrar sesión», porque seguir conectado no extiende
 *   un límite absoluto; el aviso da tiempo para guardar lo que se esté haciendo. **El texto de este segundo
 *   aviso no está en el diseño de Figma** (que solo muestra el de inactividad): usa la misma estructura.
 *
 * Diseño: Figma LIV-Diseño, `03 Pantallas`, «Aviso» (nodo 26:1157). No se cierra con Escape: la persona
 * elige una de las dos acciones.
 */
@Component({
  selector: 'app-session-warning-dialog',
  imports: [DialogComponent, ButtonComponent],
  templateUrl: './session-warning-dialog.html',
  styleUrl: './session-warning-dialog.css',
})
export class SessionWarningDialog {
  private readonly idle = inject(IdleService);

  protected readonly warning = this.idle.warning;
  protected readonly extending = signal(false);
  /** El último intento de seguir conectado no se pudo: se dice en el aviso y se deja reintentar. */
  protected readonly failed = signal(false);

  /**
   * El tipo de aviso vigente, como lista de cero o un elemento. La plantilla crea el diálogo por cada tipo:
   * si la inactividad pasa al límite de 8 horas con el aviso abierto, el botón enfocado desaparece y un
   * diálogo nuevo vuelve a poner el foco adentro.
   */
  protected readonly kinds = computed(() => {
    const kind = this.warning()?.kind;
    return kind ? [kind] : [];
  });

  /** El tipo de aviso vigente; no cambia mientras el aviso sea del mismo tipo. */
  private readonly kind = computed(() => this.warning()?.kind);

  constructor() {
    // Un aviso nuevo, o de otro tipo, empieza sin el fallo del anterior.
    effect(() => {
      this.kind();
      untracked(() => this.failed.set(false));
    });
  }

  protected readonly failure = computed(() =>
    this.failed() ? 'No pudimos extender tu sesión. Inténtalo de nuevo.' : '',
  );

  protected readonly heading = computed(() =>
    this.warning()?.kind === 'absolute'
      ? 'Tu sesión está por terminar'
      : 'Tu sesión está por expirar',
  );

  protected readonly description = computed(() => {
    const warning = this.warning();
    if (!warning) {
      return '';
    }
    // Los minutos se redondean hacia arriba: el texto solo cambia una vez por minuto, no cada segundo.
    const minutes = Math.ceil(warning.secondsLeft / 60);
    const time =
      minutes <= 0 ? 'unos segundos' : `${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}`;
    const text =
      warning.kind === 'absolute'
        ? `Por seguridad, ninguna sesión dura más de 8 horas. Cerraremos la tuya en ${time}. Guarda lo que estés haciendo y vuelve a iniciar sesión.`
        : `Por seguridad cerraremos tu sesión en ${time} por inactividad. ¿Quieres seguir conectado?`;
    return this.failed() ? `${text} ${this.failure()}` : text;
  });

  protected async keepConnected(): Promise<void> {
    if (this.extending()) {
      return;
    }
    this.extending.set(true);
    const kind = this.kind();
    try {
      const renewed = await this.idle.extend();
      // Solo vale si sigue abierto el mismo aviso: si otra pestaña extendió o el aviso cambió, este
      // resultado ya es de otro momento.
      if (this.kind() === kind) {
        this.failed.set(!renewed);
      }
    } finally {
      this.extending.set(false);
    }
  }

  protected signOut(): void {
    void this.idle.logout();
  }
}
