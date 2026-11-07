import { ErrorHandler, inject } from '@angular/core';
import { NavigationError, PRIMARY_OUTLET, RedirectCommand, Router } from '@angular/router';

import { APP_PATHS } from '../app-paths';

/**
 * Una navegación que falla lleva a «Error» en vez de dejar la página en blanco. El caso esperado es que no
 * llegue el paquete de una pantalla diferida: la red se cayó, o un despliegue nuevo cambió los nombres de los
 * archivos. «Reintentar» recarga la aplicación, y con ella la versión vigente.
 *
 * Al redirigir, el enrutador ya no relanza el error, así que se reporta aquí (`ErrorHandler`). Si la que falla
 * es la navegación a «Error», no se redirige a ella otra vez, que sería un ciclo: el enrutador relanza el error
 * como siempre.
 */
export function redirectToLoadError(navigation: NavigationError): RedirectCommand | undefined {
  const router = inject(Router);
  const segments = router.parseUrl(navigation.url).root.children[PRIMARY_OUTLET]?.segments ?? [];
  if (`/${segments.map((segment) => segment.path).join('/')}` === APP_PATHS.error) {
    return undefined;
  }
  inject(ErrorHandler).handleError(navigation.error);
  return new RedirectCommand(router.parseUrl(APP_PATHS.error));
}
