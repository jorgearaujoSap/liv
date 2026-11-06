import { HttpContext } from '@angular/common/http';
import type { HttpClient, HttpResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { BffApi } from '../../api/bff/bff-api';
import { getSession } from '../../api/bff/functions';
import { CoreApi } from '../../api/core/core-api';
import { getCurrentUser } from '../../api/core/functions';
import { APP_PATHS } from '../app-paths';
import { BrowserNavigation } from '../browser/browser-navigation.service';
import { CapabilityService } from '../capabilities/capability.service';
import { SessionChannel } from '../session/session-channel';
import { provideLivHttp } from './provide-liv-http';

class FakeNavigation {
  readonly replaced: string[] = [];
  replace(url: string): void {
    this.replaced.push(url);
  }
}

const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };
const FORBIDDEN = { status: 403, statusText: 'Forbidden' };
const SESSION = { idleSecondsLeft: 600, absoluteSecondsLeft: 20000 };

// Interceptor de la feature 001 (T21): correlación, CSRF (001-12), reintento tras 401 solo en métodos
// seguros (001-01) y recarga de capacidades tras un 403 (001-16). Las llamadas pasan por el cliente
// generado: el código a mano no usa HttpClient (la función `send` solo recibe el que le entrega el cliente).
describe('interceptor de la API', () => {
  let http: HttpTestingController;
  let navigation: FakeNavigation;
  let router: Router;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  let core: CoreApi;

  /** Una llamada con cualquier método por el cliente generado, que es quien posee el HttpClient. */
  const send =
    (
      method: string,
      url: string,
      headers: Record<string, string> = {},
      responseType: 'json' | 'blob' = 'json',
    ) =>
    (client: HttpClient, _root: string, _params?: unknown, context?: HttpContext) =>
      client.request(method, url, { observe: 'response', headers, context, responseType }) as never;
  const call = (
    method: string,
    url: string,
    headers?: Record<string, string>,
    options: { context?: HttpContext; responseType?: 'json' | 'blob' } = {},
  ) =>
    core.invoke$Response(
      send(method, url, headers, options.responseType),
      undefined,
      options.context,
    ) as Promise<HttpResponse<unknown>>;

  const nextRequest = (url: string) => vi.waitFor(() => http.expectOne(url));

  beforeEach(() => {
    navigation = new FakeNavigation();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideLivHttp(),
        provideHttpClientTesting(),
        { provide: BrowserNavigation, useValue: navigation },
        {
          provide: SessionChannel,
          useValue: { post: () => undefined, listen: () => () => undefined },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/liquidacion?anio=2026');
    core = TestBed.inject(CoreApi);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  describe('cabeceras', () => {
    it('agrega X-Correlation-Id, un UUID, a cada petición', async () => {
      const result = core.invoke(getCurrentUser);
      const request = http.expectOne('/api/v1/me');
      expect(request.request.headers.get('X-Correlation-Id')).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
      );
      request.flush({});
      await result;
    });

    it('cada petición lleva su propio identificador', async () => {
      const first = core.invoke(getCurrentUser);
      const second = core.invoke(getCurrentUser);
      const [a, b] = http.match('/api/v1/me');
      expect(a.request.headers.get('X-Correlation-Id')).not.toBe(
        b.request.headers.get('X-Correlation-Id'),
      );
      a.flush({});
      b.flush({});
      await Promise.all([first, second]);
    });

    it('sin crypto.randomUUID (página que no es HTTPS) igual agrega un UUID, y la petición sale', async () => {
      const real = globalThis.crypto;
      vi.stubGlobal('crypto', { getRandomValues: real.getRandomValues.bind(real) });

      const result = core.invoke(getCurrentUser);
      const request = http.expectOne('/api/v1/me');
      expect(request.request.headers.get('X-Correlation-Id')).toMatch(
        /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
      );
      request.flush({});
      await result;
    });

    it('a otro origen no le agrega ninguna cabecera: ni correlación ni token anti-CSRF', async () => {
      document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
      const result = call('POST', 'https://otro.example/pagos');
      const request = await nextRequest('https://otro.example/pagos');
      expect(request.request.headers.has('X-Correlation-Id')).toBe(false);
      expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      request.flush({});
      await result;
    });

    it('respeta el identificador que ya traía la petición', async () => {
      const result = call('GET', '/api/v1/me', { 'X-Correlation-Id': 'ya-puesto' });
      const request = await nextRequest('/api/v1/me');
      expect(request.request.headers.get('X-Correlation-Id')).toBe('ya-puesto');
      request.flush({});
      await result;
    });
  });

  describe('CSRF (001-12)', () => {
    it('una petición que modifica datos copia la cookie XSRF-TOKEN a la cabecera X-XSRF-TOKEN', async () => {
      document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
      const result = TestBed.inject(BffApi).invoke$Response(send('POST', '/bff/session/keepalive'));
      const request = await nextRequest('/bff/session/keepalive');
      expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('token-de-prueba');
      request.flush(SESSION);
      await result;
    });

    it.each(['POST', 'PUT', 'PATCH', 'DELETE'])(
      'un %s a /api/** también lleva el token',
      async (method) => {
        document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
        const result = call(method, '/api/v1/pagos/1');
        const request = await nextRequest('/api/v1/pagos/1');
        expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('token-de-prueba');
        request.flush({});
        await result;
      },
    );

    it('una lectura no lleva el token', async () => {
      document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';
      const result = core.invoke(getCurrentUser);
      const request = http.expectOne('/api/v1/me');
      expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
      request.flush({});
      await result;
    });
  });

  describe('401 (001-01)', () => {
    it('con la sesión abierta, reintenta una vez una lectura y entrega la respuesta del reintento', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(SESSION);
      (await nextRequest('/api/v1/me')).flush({ id: 'u-1' });

      expect(await result).toEqual({ id: 'u-1' });
      expect(navigation.replaced).toEqual([]);
    });

    it('si el reintento también da 401, no reintenta más y va a iniciar sesión con la ruta actual', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(SESSION);
      (await nextRequest('/api/v1/me')).flush(null, UNAUTHORIZED);

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%3Fanio%3D2026']);
    });

    it('el reintento no marca el contexto de quien llamó: la próxima petición con él sigue reintentándose', async () => {
      const shared = new HttpContext();

      const first = call('GET', '/api/v1/me', undefined, { context: shared });
      (await nextRequest('/api/v1/me')).flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(SESSION);
      (await nextRequest('/api/v1/me')).flush({});
      await first;

      const second = call('GET', '/api/v1/me', undefined, { context: shared });
      (await nextRequest('/api/v1/me')).flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(SESSION);
      (await nextRequest('/api/v1/me')).flush({ ok: true });

      expect(((await second) as HttpResponse<unknown>).body).toEqual({ ok: true });
    });

    it('si hay una navegación en curso, vuelve a la ruta que se pidió y no a la anterior', async () => {
      vi.spyOn(router, 'currentNavigation').mockReturnValue({
        finalUrl: router.parseUrl('/liquidacion/lista?anio=2027'),
      } as never);

      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(null, UNAUTHORIZED);

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(navigation.replaced).toEqual([
        '/bff/login?returnTo=%2Fliquidacion%2Flista%3Fanio%3D2027',
      ]);
    });

    it('varios 401 a la vez hacen una sola consulta de sesión y una sola redirección', async () => {
      const results = [
        core.invoke(getCurrentUser),
        core.invoke(getCurrentUser),
        core.invoke(getCurrentUser),
      ];
      for (const request of http.match('/api/v1/me')) {
        request.flush(null, UNAUTHORIZED);
      }
      (await nextRequest('/bff/session')).flush(null, UNAUTHORIZED);

      for (const result of results) {
        await expect(result).rejects.toMatchObject({ status: 401 });
      }
      expect(navigation.replaced).toHaveLength(1);
    });

    it('sin sesión, va a iniciar sesión sin reintentar', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(null, UNAUTHORIZED);

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%3Fanio%3D2026']);
    });

    it('un método que modifica datos no se reintenta nunca, aunque la sesión siga abierta', async () => {
      const result = call('POST', '/api/v1/pagos');
      (await nextRequest('/api/v1/pagos')).flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(SESSION);

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%3Fanio%3D2026']);
    });

    it('con Idempotency-Key, un método que modifica datos sí se reintenta una vez', async () => {
      const result = call('POST', '/api/v1/pagos', { 'Idempotency-Key': 'clave-1' });
      (await nextRequest('/api/v1/pagos')).flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(SESSION);
      const retry = await nextRequest('/api/v1/pagos');
      expect(retry.request.headers.get('Idempotency-Key')).toBe('clave-1');
      retry.flush({ ok: true });

      expect(((await result) as HttpResponse<unknown>).body).toEqual({ ok: true });
      expect(navigation.replaced).toEqual([]);
    });

    it('si no se puede saber si hay sesión (502), entrega el 401 original y no navega', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, UNAUTHORIZED);
      (await nextRequest('/bff/session')).flush(null, { status: 502, statusText: 'Bad Gateway' });

      await expect(result).rejects.toMatchObject({ status: 401 });
      expect(navigation.replaced).toEqual([]);
    });

    it('un 401 de una ruta del BFF no lo maneja el interceptor: no consulta la sesión', async () => {
      const result = TestBed.inject(BffApi).invoke(getSession);
      http.expectOne('/bff/session').flush(null, UNAUTHORIZED);

      await expect(result).rejects.toMatchObject({ status: 401 });
      http.expectNone('/bff/session');
      expect(navigation.replaced).toEqual([]);
    });
  });

  describe('403 (001-16)', () => {
    it('recarga las capacidades y lleva a «sin permiso»; el error llega a quien llamó', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, FORBIDDEN);
      (await nextRequest('/api/v1/me/capabilities')).flush({ items: [] });

      await expect(result).rejects.toMatchObject({ status: 403 });
      expect(navigateByUrl).toHaveBeenCalledWith(APP_PATHS.forbidden);
      expect(TestBed.inject(CapabilityService).capabilities()).toEqual({ items: [] });
    });

    it('si la recarga de capacidades falla, igual lleva a «sin permiso»', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush(null, FORBIDDEN);
      (await nextRequest('/api/v1/me/capabilities')).flush(null, {
        status: 500,
        statusText: 'Server Error',
      });

      await expect(result).rejects.toMatchObject({ status: 403 });
      expect(navigateByUrl).toHaveBeenCalledWith(APP_PATHS.forbidden);
    });

    it('un 403 de la propia recarga no la dispara otra vez', async () => {
      const result = TestBed.inject(CapabilityService).reload();
      http.expectOne('/api/v1/me/capabilities').flush(null, FORBIDDEN);

      await expect(result).rejects.toMatchObject({ status: 403 });
      http.expectNone('/api/v1/me/capabilities');
    });

    it('el rechazo anti-CSRF tampoco es falta de permiso cuando la respuesta se esperaba como archivo', async () => {
      const result = call('GET', '/api/v1/exportes/1', undefined, { responseType: 'blob' });
      (await nextRequest('/api/v1/exportes/1')).flush(
        new Blob([JSON.stringify({ status: 403, code: '001-12' })], {
          type: 'application/problem+json',
        }),
        FORBIDDEN,
      );

      await expect(result).rejects.toMatchObject({ status: 403 });
      http.expectNone('/api/v1/me/capabilities');
      expect(navigateByUrl).not.toHaveBeenCalled();
    });

    it('un 403 por falta de token anti-CSRF (001-12) no es falta de permiso', async () => {
      const result = core.invoke(getCurrentUser);
      http.expectOne('/api/v1/me').flush({ status: 403, code: '001-12' }, FORBIDDEN);

      await expect(result).rejects.toMatchObject({ status: 403 });
      http.expectNone('/api/v1/me/capabilities');
      expect(navigateByUrl).not.toHaveBeenCalled();
    });
  });

  it('los demás errores llegan sin cambios', async () => {
    const result = core.invoke(getCurrentUser);
    http.expectOne('/api/v1/me').flush(null, { status: 500, statusText: 'Server Error' });

    await expect(result).rejects.toMatchObject({ status: 500 });
    expect(navigation.replaced).toEqual([]);
    expect(navigateByUrl).not.toHaveBeenCalled();
  });
});
