import { Router } from '@angular/router';

/**
 * La ruta a la que va la persona. Mientras hay una navegación en curso, `router.url` todavía es la
 * anterior (en la carga inicial, `/`): volver a ella tras iniciar sesión perdería el enlace profundo.
 */
export function currentUrl(router: Router): string {
  const target = router.currentNavigation()?.finalUrl;
  return target ? router.serializeUrl(target) : router.url;
}
