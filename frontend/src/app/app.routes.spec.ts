import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { APP_PATHS } from './core/app-paths';
import { capabilityGuard } from './core/capabilities/capability.guard';
import { CapabilityService } from './core/capabilities/capability.service';
import { authGuard } from './core/session/auth.guard';
import { IdleService } from './core/session/idle.service';
import { SessionService } from './core/session/session.service';
import { LoadError } from './features/errors/load-error';
import { Shell } from './features/shell/shell';
import { WorkspaceService } from './features/shell/workspace.service';
import { FakeWorkspace } from './features/testing/fixtures';

const shell = () => routes.find((route) => route.component === Shell);
const child = (path: string) => shell()?.children?.find((route) => route.path === path);

// Las rutas de las pantallas de la feature 001 (T22): las guardas ya existen desde T21.
describe('rutas', () => {
  it('toda la aplicación, salvo el error, vive dentro de la estructura general y exige sesión', () => {
    expect(shell()?.path).toBe('');
    expect(shell()?.canActivate).toEqual([authGuard]);
  });

  it('«Error» no exige sesión: sale precisamente cuando no se pudo saber si la hay', () => {
    const error = routes.find((route) => route.component === LoadError);

    expect(error?.path).toBe(APP_PATHS.error.slice(1));
    expect(error?.canActivate).toBeUndefined();
  });

  it('Sin permiso y Próximamente están dentro de la estructura, con las rutas que fijó T21', () => {
    expect(child(APP_PATHS.forbidden.slice(1))).toBeDefined();
    expect(child(APP_PATHS.comingSoon.slice(1))).toBeDefined();
  });

  it('Inicio es la ruta raíz', () => {
    expect(shell()?.children?.[0]?.path).toBe('');
    expect(shell()?.children?.[0]?.pathMatch).toBe('full');
  });

  it('las pantallas dentro de la estructura general se cargan en diferido', () => {
    const screens = shell()?.children ?? [];

    expect(screens.length).toBeGreaterThan(0);
    for (const screen of screens) {
      expect(screen.component, screen.path).toBeUndefined();
      expect(screen.loadComponent, screen.path).toBeDefined();
    }
  });

  it('cualquier opción del menú se abre por URL por una ruta con la guarda de capacidades (001-16)', () => {
    const option = child('opcion/:code');

    expect(option?.canActivate).toEqual([capabilityGuard]);
    expect(option?.data).toEqual({ capabilityParam: 'code' });
  });

  it('una dirección desconocida vuelve a Inicio, sin inventar una pantalla', () => {
    const fallback = routes.find((route) => route.path === '**');

    expect(fallback?.redirectTo).toBe('');
  });

  // Con las rutas reales y las guardas reales (solo se simulan la sesión y las capacidades): lo que la persona ve
  // al escribir la dirección (001-15, 001-16).
  describe('al navegar por URL', () => {
    const CAPABILITIES = {
      items: [
        {
          code: '1',
          status: 'COMING_SOON',
          children: [{ code: '1.5.9', status: 'COMING_SOON', children: [] }],
        },
        { code: '3', status: 'COMING_SOON', children: [] },
      ],
    };
    const find = (code: string, items: typeof CAPABILITIES.items): unknown => {
      for (const item of items as { code: string; children: typeof items }[]) {
        if (item.code === code) return item;
        const found = find(code, item.children);
        if (found) return found;
      }
      return undefined;
    };

    const open = async (url: string) => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter(routes),
          { provide: WorkspaceService, useValue: new FakeWorkspace() },
          {
            provide: IdleService,
            useValue: { warning: signal(undefined), watch: () => undefined },
          },
          {
            provide: SessionService,
            useValue: {
              status: () => Promise.resolve({ idleSecondsLeft: 900, absoluteSecondsLeft: 28_000 }),
              redirectToLogin: () => undefined,
            },
          },
          {
            provide: CapabilityService,
            useValue: {
              load: () => Promise.resolve(CAPABILITIES),
              find: (code: string) => find(code, CAPABILITIES.items),
            },
          },
        ],
      });
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl(url);
      harness.detectChanges();
      return { harness, router: TestBed.inject(Router) };
    };

    it('una opción que la persona no tiene lleva a «Sin permiso» (001-16)', async () => {
      const { harness, router } = await open('/opcion/4.1');

      expect(router.url).toBe('/sin-permiso');
      expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain(
        'No tienes permiso',
      );
    });

    it('una opción visible pero sin construir lleva a «Próximamente» (001-15)', async () => {
      const { router } = await open('/opcion/1.5.9');

      expect(router.url).toBe('/proximamente');
    });

    it('un código que no es un número de opción lleva a «Sin permiso»', async () => {
      const { router } = await open('/opcion/constructor');

      expect(router.url).toBe('/sin-permiso');
    });

    it('una dirección desconocida lleva a Inicio', async () => {
      const { router } = await open('/no-existe');

      expect(router.url).toBe('/');
    });

    it('Inicio se abre con la estructura general', async () => {
      const { harness } = await open('/');

      expect(harness.routeNativeElement?.querySelector('liv-side-nav')).not.toBeNull();
    });
  });
});
