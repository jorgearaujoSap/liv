import { Routes } from '@angular/router';

import { APP_PATHS, OPTION_ROUTE } from './core/app-paths';
import { capabilityGuard } from './core/capabilities/capability.guard';
import { authGuard } from './core/session/auth.guard';
import { LoadError } from './features/errors/load-error';
import { Shell } from './features/shell/shell';

/** Quita la barra inicial: las rutas de `APP_PATHS` son absolutas, las del enrutador no. */
const path = (appPath: string) => appPath.slice(1);

const loadComingSoon = () => import('./features/errors/coming-soon').then((m) => m.ComingSoon);

// Las pantallas se cargan en diferido (`loadComponent`), salvo dos que van en el paquete inicial:
// - `Shell`, porque envuelve todas las demás: toda visita la necesita, y cargarla aparte solo agrega una petición;
// - `LoadError`, porque sale cuando el servidor no responde, y entonces puede que tampoco llegue su paquete.
export const routes: Routes = [
  // Sale justo cuando no se pudo saber si hay sesión, así que no la exige.
  { path: path(APP_PATHS.error), component: LoadError },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: path(APP_PATHS.forbidden),
        loadComponent: () => import('./features/errors/forbidden').then((m) => m.Forbidden),
      },
      { path: path(APP_PATHS.comingSoon), loadComponent: loadComingSoon },
      // Cualquier opción del menú, por URL (001-16): cada feature registra antes la ruta de su opción.
      // Una opción sin pantalla propia (aún sin construir) cae aquí y la guarda decide: 403 si no está en
      // las capacidades, «Próximamente» si todavía no existe.
      {
        path: `${OPTION_ROUTE}/:code`,
        canActivate: [capabilityGuard],
        data: { capabilityParam: 'code' },
        loadComponent: loadComingSoon,
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
