import { inject, Injectable } from '@angular/core';

import { BffApi } from '../../api/bff/bff-api';
import { getSession, keepAliveSession, logout } from '../../api/bff/functions';
import type { SessionStatus } from '../../api/bff/models';
import { LOGIN_PATH } from '../app-paths';
import { BrowserNavigation } from '../browser/browser-navigation.service';
import { hasStatus } from '../http/http-errors';
import { withTimeout } from '../http/with-timeout';
import { isOwnPath, isReturnTarget } from '../own-path';
import { SessionChannel } from './session-channel';

/** Lo que se espera al BFF antes de darlo por caído. */
const BFF_TIMEOUT_MS = 10_000;

/**
 * La sesión del BFF vista desde el navegador (feature 001): lo que le queda, seguir conectado, salir
 * e ir a iniciar sesión. No guarda ni ve *tokens*: la sesión es una cookie `HttpOnly` (001-11).
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly bff = inject(BffApi);
  private readonly navigation = inject(BrowserNavigation);
  private readonly channel = inject(SessionChannel);

  private pendingStatus?: Promise<SessionStatus | null>;
  private redirecting = false;

  /**
   * Lo que le queda a la sesión, sin extenderla (`GET /bff/session`). `null` si no hay sesión abierta.
   * Cualquier otro fallo se propaga: no saber no es lo mismo que no tener sesión. Varias llamadas a la
   * vez (p. ej. varios 401) comparten una sola petición.
   */
  status(): Promise<SessionStatus | null> {
    this.pendingStatus ??= this.orNullWithoutSession(() => this.bff.invoke(getSession)).finally(
      () => (this.pendingStatus = undefined),
    );
    return this.pendingStatus;
  }

  /** La persona elige seguir conectada (001-22). `null` si la sesión ya no existe. */
  keepAlive(): Promise<SessionStatus | null> {
    return this.orNullWithoutSession(() => this.bff.invoke(keepAliveSession));
  }

  /**
   * Cierra la sesión (001-09) y navega por completo a la ruta que responde el BFF, para que no quede
   * nada de la sesión en la página. Si la petición falla, va igual a `/bff/login`, que cierra la sesión
   * que siga abierta antes de pedir las credenciales.
   */
  async logout(): Promise<void> {
    let target: string = LOGIN_PATH;
    try {
      const result = await withTimeout(this.bff.invoke(logout), BFF_TIMEOUT_MS);
      if (isOwnPath(result.redirectTo)) {
        target = result.redirectTo;
      }
    } catch {
      // El BFF cierra cada paso aunque falle el anterior; si ni respondió, /bff/login la cierra.
    }
    this.channel.post({ type: 'ended' });
    this.navigation.replace(target);
  }

  /**
   * Lleva a iniciar sesión y, al autenticarse, de vuelta a `returnTo` (001-01). Solo actúa la primera
   * vez: si varias cosas lo piden a la vez (la guarda y un 401), vale la ruta del primero.
   */
  redirectToLogin(returnTo: string): void {
    if (this.redirecting) {
      return;
    }
    this.redirecting = true;
    const target = isReturnTarget(returnTo) ? returnTo : '/';
    this.navigation.replace(`${LOGIN_PATH}?returnTo=${encodeURIComponent(target)}`);
  }

  /** Recarga la aplicación desde el inicio, con la sesión que haya. */
  restart(): void {
    this.navigation.replace('/');
  }

  /** Una llamada al BFF que devuelve lo que le queda a la sesión: un 401 es «no hay sesión», no un fallo. */
  private async orNullWithoutSession(
    call: () => Promise<SessionStatus>,
  ): Promise<SessionStatus | null> {
    try {
      return await withTimeout(call(), BFF_TIMEOUT_MS);
    } catch (error) {
      if (hasStatus(error, 401)) {
        return null;
      }
      throw error;
    }
  }
}
