import { inject } from '@angular/core';
import { CanActivateChildFn, CanActivateFn, Router } from '@angular/router';

import { APP_PATHS } from '../app-paths';
import { CapabilityService } from './capability.service';

/**
 * Guarda de una opción de menú. La ruta declara el código de la opción en `data: { capability: '1.5.9' }`.
 *
 * - Disponible: navega.
 * - Visible pero `COMING_SOON`: lleva a «Próximamente» (001-15).
 * - Ausente de las capacidades: lleva a «sin permiso», 403 (001-16), aunque se abra por URL.
 *
 * Es solo de experiencia de usuario: cada llamada a la API la autoriza el backend.
 */
export const capabilityGuard: CanActivateFn & CanActivateChildFn = async (route) => {
  const capabilities = inject(CapabilityService);
  const router = inject(Router);

  const code = route.data['capability'];
  if (typeof code !== 'string') {
    // Una ruta protegida sin código es un error de configuración: se niega.
    return router.parseUrl(APP_PATHS.forbidden);
  }
  try {
    await capabilities.load();
  } catch {
    return router.parseUrl(APP_PATHS.error);
  }
  const capability = capabilities.find(code);
  if (!capability) {
    return router.parseUrl(APP_PATHS.forbidden);
  }
  return capability.status === 'AVAILABLE' ? true : router.parseUrl(APP_PATHS.comingSoon);
};
