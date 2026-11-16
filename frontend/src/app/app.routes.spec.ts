import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { APP_PATHS, OPTION_ROUTE } from './core/app-paths';
import { capabilityGuard } from './core/capabilities/capability.guard';
import { CapabilityService } from './core/capabilities/capability.service';
import { authGuard } from './core/session/auth.guard';
import { IdleService } from './core/session/idle.service';
import { SessionService } from './core/session/session.service';
import { LoadError } from './features/errors/load-error';
import { Shell } from './features/shell/shell';
import { WorkspaceService } from './features/shell/workspace.service';
import { FiscalYearsService } from './features/fiscal-years/fiscal-years.service';
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

  // 007-02: la opción 1.4 tiene su pantalla, con la guarda de capacidades, antes de la ruta genérica.
  it('Vigencias fiscales (1.4) tiene su ruta, con la guarda de capacidades, antes de la genérica', () => {
    const children = shell()?.children ?? [];
    const own = children.findIndex((route) => route.path === `${OPTION_ROUTE}/1.4`);
    const generic = children.findIndex((route) => route.path === `${OPTION_ROUTE}/:code`);

    expect(own).toBeGreaterThanOrEqual(0);
    expect(own).toBeLessThan(generic);
    expect(children[own].canActivate).toEqual([capabilityGuard]);
    expect(children[own].data).toEqual({ capability: '1.4' });
    expect(children[own].loadComponent).toBeDefined();
  });

  // 007-02: el detalle de una vigencia (`1.4/:year`) lleva la misma capacidad y va antes de la genérica.
  it('el detalle de la vigencia (1.4/:year) tiene su ruta, con la capacidad 1.4, antes de la genérica', () => {
    const children = shell()?.children ?? [];
    const own = children.findIndex((route) => route.path === `${OPTION_ROUTE}/1.4/:year`);
    const generic = children.findIndex((route) => route.path === `${OPTION_ROUTE}/:code`);

    expect(own).toBeGreaterThanOrEqual(0);
    expect(own).toBeLessThan(generic);
    expect(children[own].canActivate).toEqual([capabilityGuard]);
    expect(children[own].data).toEqual({ capability: '1.4' });
    expect(children[own].loadComponent).toBeDefined();
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

    const open = async (url: string, capabilities = CAPABILITIES) => {
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
              load: () => Promise.resolve(capabilities),
              find: (code: string) => find(code, capabilities.items),
            },
          },
          {
            provide: FiscalYearsService,
            useValue: {
              list: () => Promise.resolve({ items: [], canCreate: false, creatableYears: [] }),
              get: () => new Promise(() => undefined),
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

    // 007-02: «Sin permiso» por URL sin la opción, «Próximamente» mientras siga así, y la pantalla cuando
    // el backend la declara disponible.
    describe('Vigencias fiscales (1.4)', () => {
      const withOption = (status: string) => ({
        items: [
          { code: '1', status: 'AVAILABLE', children: [{ code: '1.4', status, children: [] }] },
        ],
      });

      it('sin la opción en las capacidades lleva a «Sin permiso» (007-02)', async () => {
        const { router } = await open('/opcion/1.4');

        expect(router.url).toBe('/sin-permiso');
      });

      it('mientras la opción siga en «Próximamente», lleva a «Próximamente»', async () => {
        const { router } = await open('/opcion/1.4', withOption('COMING_SOON'));

        expect(router.url).toBe('/proximamente');
      });

      it('con la opción disponible abre la lista de vigencias', async () => {
        const { harness, router } = await open('/opcion/1.4', withOption('AVAILABLE'));
        await harness.fixture.whenStable();
        harness.detectChanges();

        expect(router.url).toBe('/opcion/1.4');
        expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain(
          'Vigencias fiscales',
        );
      });
    });

    // 007-02: el detalle de una vigencia, por URL, con la misma capacidad que la lista.
    describe('Detalle de la vigencia (1.4/:year)', () => {
      const withOption = (status: string) => ({
        items: [
          { code: '1', status: 'AVAILABLE', children: [{ code: '1.4', status, children: [] }] },
        ],
      });

      it('sin la opción en las capacidades lleva a «Sin permiso» (007-02)', async () => {
        const { router } = await open('/opcion/1.4/2026');

        expect(router.url).toBe('/sin-permiso');
      });

      it('mientras la opción siga en «Próximamente», lleva a «Próximamente»', async () => {
        const { router } = await open('/opcion/1.4/2026', withOption('COMING_SOON'));

        expect(router.url).toBe('/proximamente');
      });

      it('con la opción disponible abre el detalle de ese año', async () => {
        const { harness, router } = await open('/opcion/1.4/2026', withOption('AVAILABLE'));
        await harness.fixture.whenStable();
        harness.detectChanges();

        expect(router.url).toBe('/opcion/1.4/2026');
        expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toContain('Vigencia');
        expect(harness.routeNativeElement?.textContent).toContain('Cargando los datos…');
      });
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
