import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { BrowserNavigation } from '../browser/browser-navigation.service';
import { SessionChannel, SessionMessage } from './session-channel';
import { SessionService } from './session.service';

class FakeNavigation {
  readonly replaced: string[] = [];
  replace(url: string): void {
    this.replaced.push(url);
  }
}

class FakeChannel {
  readonly posted: SessionMessage[] = [];
  post(message: SessionMessage): void {
    this.posted.push(message);
  }
  listen(): () => void {
    return () => undefined;
  }
}

// SessionService sobre BffApi: /bff/session, keepalive y logout (feature 001, T21; 001-09, 001-22).
describe('SessionService', () => {
  let http: HttpTestingController;
  let navigation: FakeNavigation;
  let channel: FakeChannel;
  let service: SessionService;

  beforeEach(() => {
    navigation = new FakeNavigation();
    channel = new FakeChannel();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BrowserNavigation, useValue: navigation },
        { provide: SessionChannel, useValue: channel },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(SessionService);
  });

  afterEach(() => http.verify());

  describe('status', () => {
    it('devuelve lo que le queda a la sesión, leído de GET /bff/session', async () => {
      const result = service.status();
      const request = http.expectOne('/bff/session');
      expect(request.request.method).toBe('GET');
      request.flush({ idleSecondsLeft: 900, absoluteSecondsLeft: 28800 });

      expect(await result).toEqual({ idleSecondsLeft: 900, absoluteSecondsLeft: 28800 });
    });

    it('devuelve null cuando no hay sesión abierta (401)', async () => {
      const result = service.status();
      http.expectOne('/bff/session').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(await result).toBeNull();
    });

    it('un fallo que no es 401 se propaga: no se confunde con una sesión cerrada', async () => {
      const result = service.status();
      http.expectOne('/bff/session').flush(null, { status: 502, statusText: 'Bad Gateway' });

      await expect(result).rejects.toMatchObject({ status: 502 });
    });
  });

  describe('keepAlive (001-22)', () => {
    it('hace POST /bff/session/keepalive y devuelve lo que queda', async () => {
      const result = service.keepAlive();
      const request = http.expectOne('/bff/session/keepalive');
      expect(request.request.method).toBe('POST');
      request.flush({ idleSecondsLeft: 900, absoluteSecondsLeft: 20000 });

      expect(await result).toEqual({ idleSecondsLeft: 900, absoluteSecondsLeft: 20000 });
    });

    it('devuelve null si la sesión ya no existe (401)', async () => {
      const result = service.keepAlive();
      http
        .expectOne('/bff/session/keepalive')
        .flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(await result).toBeNull();
    });

    it('un 502 del proveedor se propaga para poder reintentar', async () => {
      const result = service.keepAlive();
      http
        .expectOne('/bff/session/keepalive')
        .flush(null, { status: 502, statusText: 'Bad Gateway' });

      await expect(result).rejects.toMatchObject({ status: 502 });
    });
  });

  describe('logout (001-09)', () => {
    it('hace POST /bff/logout y navega por completo a la ruta que responde', async () => {
      const result = service.logout();
      const request = http.expectOne('/bff/logout');
      expect(request.request.method).toBe('POST');
      request.flush({ redirectTo: '/bff/login' });
      await result;

      expect(navigation.replaced).toEqual(['/bff/login']);
    });

    it('avisa a las demás pestañas de que la sesión terminó', async () => {
      const result = service.logout();
      http.expectOne('/bff/logout').flush({ redirectTo: '/bff/login' });
      await result;

      expect(channel.posted).toEqual([{ type: 'ended' }]);
    });

    it.each([
      'https://evil.example/',
      '//evil.example',
      '/\\evil.example',
      '/\t/evil.example',
      '/\n/evil.example',
      'bff/login',
      '',
    ])('no navega a un destino que no es una ruta de LIV: %j', async (redirectTo) => {
      const result = service.logout();
      http.expectOne('/bff/logout').flush({ redirectTo });
      await result;

      expect(navigation.replaced).toEqual(['/bff/login']);
    });

    it('si la petición falla, igual navega a /bff/login, que cierra la sesión abierta', async () => {
      const result = service.logout();
      http.expectOne('/bff/logout').flush(null, { status: 403, statusText: 'Forbidden' });
      await result;

      expect(navigation.replaced).toEqual(['/bff/login']);
      expect(channel.posted).toEqual([{ type: 'ended' }]);
    });
  });

  describe('redirectToLogin (001-01)', () => {
    it('navega por completo a /bff/login con la ruta pedida como returnTo', () => {
      service.redirectToLogin('/liquidacion/lista?anio=2026#fila');

      expect(navigation.replaced).toEqual([
        '/bff/login?returnTo=%2Fliquidacion%2Flista%3Fanio%3D2026%23fila',
      ]);
    });

    it.each([
      'https://evil.example/',
      '//evil.example',
      '/\\evil.example',
      '/\t/evil.example',
      '/\r\n/evil.example',
      'liquidacion',
      '',
      '/bff/login',
      '/bff/logout',
      '/api/v1/me',
      '/internal/v1/sessions',
      '/login/oauth2/code/liv',
    ])('una ruta que no es de LIV, o es del BFF o de la API, se cambia por /: %j', (returnTo) => {
      service.redirectToLogin(returnTo);

      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2F']);
    });
  });
  describe('varias llamadas a la vez', () => {
    it('redirectToLogin actúa una sola vez: la primera ruta pedida es la que vale', () => {
      service.redirectToLogin('/liquidacion/lista');
      service.redirectToLogin('/');

      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%2Flista']);
    });

    it('status() comparte una sola petición mientras haya una en vuelo', async () => {
      const first = service.status();
      const second = service.status();
      http.expectOne('/bff/session').flush({ idleSecondsLeft: 100, absoluteSecondsLeft: 200 });

      expect(await first).toEqual(await second);
    });

    it('una vez respondida, la siguiente status() vuelve a preguntar', async () => {
      const first = service.status();
      http.expectOne('/bff/session').flush({ idleSecondsLeft: 100, absoluteSecondsLeft: 200 });
      await first;

      const second = service.status();
      http.expectOne('/bff/session').flush(null, { status: 401, statusText: 'Unauthorized' });
      expect(await second).toBeNull();
    });
  });

  describe('si el BFF no responde', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('status() falla a los 10 segundos y no deja nada colgado', async () => {
      const result = service.status();
      const outcome = result.then(
        () => 'respondió',
        (error: Error) => error.name,
      );
      http.expectOne('/bff/session');
      await vi.advanceTimersByTimeAsync(10_000);

      expect(await outcome).toBe('TimeoutError');
      // La siguiente consulta no hereda la que se perdió.
      const next = service.status();
      http.expectOne('/bff/session').flush(null, { status: 401, statusText: 'Unauthorized' });
      expect(await next).toBeNull();
    });

    it('keepAlive() falla a los 10 segundos', async () => {
      const outcome = service.keepAlive().then(
        () => 'respondió',
        (error: Error) => error.name,
      );
      http.expectOne('/bff/session/keepalive');
      await vi.advanceTimersByTimeAsync(10_000);

      expect(await outcome).toBe('TimeoutError');
    });

    it('logout() igual navega a /bff/login, que cierra la sesión abierta', async () => {
      const result = service.logout();
      http.expectOne('/bff/logout');
      await vi.advanceTimersByTimeAsync(10_000);
      await result;

      expect(navigation.replaced).toEqual(['/bff/login']);
    });
  });
});
