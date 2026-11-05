import { inject, Injectable } from '@angular/core';

import { BffApi } from '../../api/bff/bff-api';
import { getSession, keepAliveSession, logout } from '../../api/bff/functions';
import type { SessionStatus } from '../../api/bff/models';
import { LOGIN_PATH } from '../app-paths';
import { BrowserNavigation } from '../browser/browser-navigation.service';
import { hasStatus } from '../http/http-errors';
import { SessionChannel } from './session-channel';

/**
 * Una ruta propia de LIV: empieza por una sola `/` y no lleva `\`, con lo que ningún navegador la toma
 * por otro sitio. El BFF repite la validación de `returnTo`; esta evita siquiera pedirle otra cosa.
 */
function isOwnPath(path: string): boolean {
  return /^\/(?![/\\])[^\\]*$/.test(path);
}

/**
 * La sesión del BFF vista desde el navegador (feature 001): lo que le queda, seguir conectado, salir
 * e ir a iniciar sesión. No guarda ni ve *tokens*: la sesión es una cookie `HttpOnly` (001-11).
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly bff = inject(BffApi);
  private readonly navigation = inject(BrowserNavigation);
  private readonly channel = inject(SessionChannel);

  /**
   * Lo que le queda a la sesión, sin extenderla (`GET /bff/session`). `null` si no hay sesión abierta.
   * Cualquier otro fallo se propaga: no saber no es lo mismo que no tener sesión.
   */
  async status(): Promise<SessionStatus | null> {
    try {
      return await this.bff.invoke(getSession);
    } catch (error) {
      if (hasStatus(error, 401)) {
        return null;
      }
      throw error;
    }
  }

  /** La persona elige seguir conectada (001-22). `null` si la sesión ya no existe. */
  async keepAlive(): Promise<SessionStatus | null> {
    try {
      return await this.bff.invoke(keepAliveSession);
    } catch (error) {
      if (hasStatus(error, 401)) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Cierra la sesión (001-09) y navega por completo a la ruta que responde el BFF, para que no quede
   * nada de la sesión en la página. Si la petición falla, va igual a `/bff/login`, que cierra la sesión
   * que siga abierta antes de pedir las credenciales.
   */
  async logout(): Promise<void> {
    let target: string = LOGIN_PATH;
    try {
      const result = await this.bff.invoke(logout);
      if (isOwnPath(result.redirectTo)) {
        target = result.redirectTo;
      }
    } catch {
      // El BFF cierra cada paso aunque falle el anterior; si ni respondió, /bff/login la cierra.
    }
    this.channel.post({ type: 'ended' });
    this.navigation.replace(target);
  }

  /** Lleva a iniciar sesión y, al autenticarse, de vuelta a `returnTo` (001-01). */
  redirectToLogin(returnTo: string): void {
    const safe = isOwnPath(returnTo) ? returnTo : '/';
    this.navigation.replace(`${LOGIN_PATH}?returnTo=${encodeURIComponent(safe)}`);
  }
}
