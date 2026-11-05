import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { APP_PATHS } from '../app-paths';
import { capabilityGuard } from './capability.guard';
import { CapabilityService } from './capability.service';

const CAPABILITIES = {
  items: [
    {
      code: '1',
      status: 'AVAILABLE',
      children: [
        {
          code: '1.5',
          status: 'AVAILABLE',
          children: [
            { code: '1.5.1', status: 'AVAILABLE', children: [] },
            { code: '1.5.9', status: 'COMING_SOON', children: [] },
          ],
        },
      ],
    },
  ],
};

// 001-16: una opción que no está en las capacidades lleva a «sin permiso» (403) aunque se abra por URL;
// una visible pero aún no construida lleva a «Próximamente» (001-15).
describe('capabilityGuard', () => {
  let http: HttpTestingController;
  let router: Router;

  const run = async (data: Record<string, unknown>, respond = true) => {
    const pending = TestBed.runInInjectionContext(
      () =>
        capabilityGuard(
          { data } as unknown as ActivatedRouteSnapshot,
          {} as RouterStateSnapshot,
        ) as Promise<boolean | UrlTree>,
    );
    if (respond) {
      http.expectOne('/api/v1/me/capabilities').flush(CAPABILITIES);
    }
    return pending;
  };

  const target = (result: boolean | UrlTree) => router.serializeUrl(result as UrlTree);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  it('una opción disponible, aunque esté en el tercer nivel, navega normalmente', async () => {
    expect(await run({ capability: '1.5.1' })).toBe(true);
  });

  it('una rama disponible navega', async () => {
    expect(await run({ capability: '1.5' })).toBe(true);
  });

  it('una opción visible pero COMING_SOON lleva a «Próximamente»', async () => {
    expect(target(await run({ capability: '1.5.9' }))).toBe(APP_PATHS.comingSoon);
  });

  it('una opción que no está en las capacidades lleva a 403', async () => {
    expect(target(await run({ capability: '3.2' }))).toBe(APP_PATHS.forbidden);
  });

  it('una ruta sin código de capacidad se niega: falla cerrado', async () => {
    expect(target(await run({}, false))).toBe(APP_PATHS.forbidden);
  });

  it('carga las capacidades una sola vez para varias navegaciones', async () => {
    await run({ capability: '1.5.1' });
    expect(await run({ capability: '1.5' }, false)).toBe(true);
  });

  it('si las capacidades no se pueden leer (500) lleva a la pantalla de error y reintenta la próxima vez', async () => {
    const pending = TestBed.runInInjectionContext(
      () =>
        capabilityGuard(
          { data: { capability: '1.5.1' } } as unknown as ActivatedRouteSnapshot,
          {} as RouterStateSnapshot,
        ) as Promise<boolean | UrlTree>,
    );
    http
      .expectOne('/api/v1/me/capabilities')
      .flush(null, { status: 500, statusText: 'Server Error' });
    expect(target(await pending)).toBe(APP_PATHS.error);

    expect(await run({ capability: '1.5.1' })).toBe(true);
  });

  it('CapabilityService.reload() vuelve a pedirlas, p. ej. tras un 403', async () => {
    await run({ capability: '1.5.1' });

    const service = TestBed.inject(CapabilityService);
    const reloaded = service.reload();
    http
      .expectOne('/api/v1/me/capabilities')
      .flush({ items: [{ code: '1', status: 'AVAILABLE', children: [] }] });
    await reloaded;

    expect(service.find('1.5.1')).toBeUndefined();
    expect(service.find('1')?.status).toBe('AVAILABLE');
  });
});
