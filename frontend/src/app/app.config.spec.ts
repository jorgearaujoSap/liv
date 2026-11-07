import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, ErrorHandler } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { CoreApi } from './api/core/core-api';
import { getCurrentUser } from './api/core/functions';
import { appConfig } from './app.config';
import { APP_PATHS } from './core/app-paths';

@Component({ template: '' })
class Blank {}

// La configuración real de la aplicación registra el interceptor de la feature 001 (T21).
describe('appConfig', () => {
  it('las peticiones de la aplicación pasan por el interceptor', async () => {
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);

    const result = TestBed.inject(CoreApi).invoke(getCurrentUser);
    const request = http.expectOne('/api/v1/me');
    expect(request.request.headers.has('X-Correlation-Id')).toBe(true);
    request.flush({});
    await result;
    http.verify();
  });

  // Las pantallas se cargan en diferido: si su paquete no llega (red caída, o un despliegue nuevo que cambió los
  // nombres de los archivos), la persona no se queda ante una página en blanco.
  describe('cuando falla una navegación', () => {
    const chunkError = new Error('Failed to fetch dynamically imported module');
    let handleError: ReturnType<typeof vi.fn>;

    const setUp = (errorScreenLoads: boolean) => {
      handleError = vi.fn();
      TestBed.configureTestingModule({
        providers: [
          ...appConfig.providers,
          provideHttpClientTesting(),
          { provide: ErrorHandler, useValue: { handleError } },
        ],
      });
      const router = TestBed.inject(Router);
      router.resetConfig([
        errorScreenLoads
          ? { path: APP_PATHS.error.slice(1), component: Blank }
          : { path: APP_PATHS.error.slice(1), loadComponent: () => Promise.reject(chunkError) },
        { path: 'pantalla', loadComponent: () => Promise.reject(chunkError) },
      ]);
      return router;
    };

    it('lleva a «Error» y reporta el fallo', async () => {
      const router = setUp(true);

      await router.navigateByUrl('/pantalla');

      expect(router.url).toBe(APP_PATHS.error);
      expect(handleError).toHaveBeenCalledWith(chunkError);
    });

    it('si la que falla es «Error», no vuelve a intentarla', async () => {
      const router = setUp(false);

      await expect(router.navigateByUrl(APP_PATHS.error)).rejects.toBe(chunkError);
      expect(router.url).toBe('/');
    });
  });
});
