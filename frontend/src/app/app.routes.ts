import { Routes } from '@angular/router';

import { APP_PATHS } from './core/app-paths';
import { capabilityGuard } from './core/capabilities/capability.guard';
import { authGuard } from './core/session/auth.guard';
import { ComingSoon } from './features/errors/coming-soon';
import { Forbidden } from './features/errors/forbidden';
import { LoadError } from './features/errors/load-error';
import { Home } from './features/home/home';
import { Shell } from './features/shell/shell';

/** Quita la barra inicial: las rutas de `APP_PATHS` son absolutas, las del enrutador no. */
const path = (appPath: string) => appPath.slice(1);

export const routes: Routes = [
  // Sale justo cuando no se pudo saber si hay sesión, así que no la exige.
  { path: path(APP_PATHS.error), component: LoadError },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', component: Home },
      { path: path(APP_PATHS.forbidden), component: Forbidden },
      { path: path(APP_PATHS.comingSoon), component: ComingSoon },
      // Cualquier opción del menú, por URL (001-16): cada feature registra antes la ruta de su opción.
      // Una opción sin pantalla propia (aún sin construir) cae aquí y la guarda decide: 403 si no está en
      // las capacidades, «Próximamente» si todavía no existe.
      {
        path: 'opcion/:code',
        canActivate: [capabilityGuard],
        data: { capabilityParam: 'code' },
        component: ComingSoon,
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
