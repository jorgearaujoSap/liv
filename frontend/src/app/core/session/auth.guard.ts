import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { APP_PATHS } from '../app-paths';
import { IdleService } from './idle.service';
import { SessionService } from './session.service';

/**
 * Deja pasar solo con sesión abierta (001-01). Sin sesión, navega por completo a `/bff/login` con la
 * ruta pedida como `returnTo`. Es solo de experiencia de usuario: la autorización real está en la API.
 */
export const authGuard: CanActivateFn & CanActivateChildFn = async (_route, state) => {
  const session = inject(SessionService);
  const idle = inject(IdleService);
  const router = inject(Router);

  let status;
  try {
    status = await session.status();
  } catch {
    // No se pudo saber si hay sesión: mandar a iniciar sesión sería engañoso.
    return router.parseUrl(APP_PATHS.error);
  }
  if (!status) {
    session.redirectToLogin(state.url);
    return false;
  }
  idle.watch(status);
  return true;
};
