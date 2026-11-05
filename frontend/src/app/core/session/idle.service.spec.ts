import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import type { SessionStatus } from '../../api/bff/models';
import { BrowserNavigation } from '../browser/browser-navigation.service';
import { IdleService } from './idle.service';
import { SessionChannel, SessionMessage } from './session-channel';

class FakeNavigation {
  readonly replaced: string[] = [];
  replace(url: string): void {
    this.replaced.push(url);
  }
}

class FakeChannel {
  readonly posted: SessionMessage[] = [];
  handler?: (message: SessionMessage) => void;
  post(message: SessionMessage): void {
    this.posted.push(message);
  }
  listen(handler: (message: SessionMessage) => void): () => void {
    this.handler = handler;
    return () => (this.handler = undefined);
  }
}

const MINUTE = 60_000;
const status = (idle: number, absolute = 28_000): SessionStatus => ({
  idleSecondsLeft: idle,
  absoluteSecondsLeft: absolute,
});

// 001-22: el aviso sale 2 minutos antes de que venza la sesión por inactividad (a los 13 de 15), las
// pestañas se sincronizan y no hay peticiones de fondo: la única consulta es la que decide si avisar.
describe('IdleService', () => {
  let http: HttpTestingController;
  let navigation: FakeNavigation;
  let channel: FakeChannel;
  let idle: IdleService;

  const advance = (ms: number) => vi.advanceTimersByTimeAsync(ms);
  /** Atiende la consulta de /bff/session que haya pendiente. */
  const answerSession = async (body: SessionStatus | null) => {
    const request = http.expectOne('/bff/session');
    expect(request.request.method).toBe('GET');
    if (body) {
      request.flush(body);
    } else {
      request.flush(null, { status: 401, statusText: 'Unauthorized' });
    }
    await advance(0);
  };

  beforeEach(() => {
    vi.useFakeTimers();
    navigation = new FakeNavigation();
    channel = new FakeChannel();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BrowserNavigation, useValue: navigation },
        { provide: SessionChannel, useValue: channel },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue('/liquidacion?anio=2026');
    idle = TestBed.inject(IdleService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  describe('aviso a los 13 minutos', () => {
    it('no hace ninguna petición mientras no llegue la hora: nada de peticiones de fondo', async () => {
      idle.watch(status(900));

      await advance(13 * MINUTE - 1000);

      http.expectNone(() => true);
      expect(idle.warning()).toBeUndefined();
    });

    it('a los 13 minutos consulta /bff/session una vez y avisa con lo que queda', async () => {
      idle.watch(status(900));

      await advance(13 * MINUTE);
      await answerSession(status(120));

      expect(idle.warning()).toEqual({ kind: 'idle', secondsLeft: 120 });
      http.expectNone(() => true);
    });

    it('si hubo actividad en otra pestaña, no avisa y vuelve a calcular la hora del aviso', async () => {
      idle.watch(status(900));

      await advance(13 * MINUTE);
      await answerSession(status(900));
      expect(idle.warning()).toBeUndefined();

      await advance(13 * MINUTE - 1000);
      http.expectNone(() => true);
      await advance(1000);
      await answerSession(status(120));
      expect(idle.warning()).toEqual({ kind: 'idle', secondsLeft: 120 });
    });

    it('la cuenta regresiva baja cada segundo sin pedir nada al servidor', async () => {
      idle.watch(status(120));
      await advance(0);
      await answerSession(status(120));

      await advance(5000);

      expect(idle.warning()).toEqual({ kind: 'idle', secondsLeft: 115 });
      http.expectNone(() => true);
    });

    it('al llegar a cero vuelve a consultar y, si la sesión venció, lleva a iniciar sesión', async () => {
      idle.watch(status(120));
      await advance(0);
      await answerSession(status(120));

      await advance(120 * 1000);
      await answerSession(null);

      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%3Fanio%3D2026']);
      expect(idle.warning()).toBeUndefined();
    });

    it('si se alcanzó la hora pero la sesión se extendió en otro sitio, el aviso se cierra', async () => {
      idle.watch(status(120));
      await advance(0);
      await answerSession(status(120));

      await advance(120 * 1000);
      await answerSession(status(900));

      expect(idle.warning()).toBeUndefined();
      expect(navigation.replaced).toEqual([]);
    });

    it('si /bff/session falla, no avisa ni cierra: reintenta en 30 segundos', async () => {
      idle.watch(status(900));
      await advance(13 * MINUTE);
      http.expectOne('/bff/session').flush(null, { status: 502, statusText: 'Bad Gateway' });
      await advance(0);

      expect(idle.warning()).toBeUndefined();
      expect(navigation.replaced).toEqual([]);
      await advance(30_000);
      await answerSession(status(90));
      expect(idle.warning()).toEqual({ kind: 'idle', secondsLeft: 90 });
    });
  });

  describe('«Seguir conectado»', () => {
    const warn = async () => {
      idle.watch(status(120));
      await advance(0);
      await answerSession(status(120));
    };

    it('pide keepalive, cierra el aviso, avisa a las demás pestañas y reprograma el aviso', async () => {
      await warn();

      const result = idle.extend();
      const request = http.expectOne('/bff/session/keepalive');
      expect(request.request.method).toBe('POST');
      request.flush(status(900));

      expect(await result).toBe(true);
      expect(idle.warning()).toBeUndefined();
      expect(channel.posted).toEqual([{ type: 'extended', status: status(900) }]);
      await advance(13 * MINUTE - 1000);
      http.expectNone(() => true);
      await advance(1000);
      http.expectOne('/bff/session').flush(status(120));
    });

    it('si la sesión ya no existe (401), lleva a iniciar sesión', async () => {
      await warn();

      const result = idle.extend();
      http
        .expectOne('/bff/session/keepalive')
        .flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(await result).toBe(false);
      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%3Fanio%3D2026']);
    });

    it('si el proveedor no responde (502), el aviso sigue y se puede intentar de nuevo', async () => {
      await warn();

      const result = idle.extend();
      http
        .expectOne('/bff/session/keepalive')
        .flush(null, { status: 502, statusText: 'Bad Gateway' });

      expect(await result).toBe(false);
      expect(idle.warning()).toEqual({ kind: 'idle', secondsLeft: 120 });
      expect(navigation.replaced).toEqual([]);
    });
  });

  describe('límite absoluto', () => {
    it('avisa aparte cuando faltan 5 minutos para las 8 horas; no se puede extender', async () => {
      idle.watch(status(900, 290));
      await advance(0);
      await answerSession(status(900, 290));

      expect(idle.warning()).toEqual({ kind: 'absolute', secondsLeft: 290 });
    });

    it('«Seguir conectado» no quita el aviso del límite: keepalive no pasa de las 8 horas', async () => {
      idle.watch(status(900, 290));
      await advance(0);
      await answerSession(status(900, 290));

      const result = idle.extend();
      http.expectOne('/bff/session/keepalive').flush(status(290, 280));

      expect(await result).toBe(true);
      expect(idle.warning()).toEqual({ kind: 'absolute', secondsLeft: 280 });
    });

    it('al llegar a cero, lleva a iniciar sesión', async () => {
      idle.watch(status(900, 10));
      await advance(0);
      await answerSession(status(900, 10));

      await advance(10_000);
      await answerSession(status(0, 0));

      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2Fliquidacion%3Fanio%3D2026']);
    });
  });

  describe('varias pestañas', () => {
    it('si otra pestaña extendió la sesión, cierra el aviso y recalcula', async () => {
      idle.watch(status(120));
      await advance(0);
      await answerSession(status(120));

      channel.handler?.({ type: 'extended', status: status(900) });

      expect(idle.warning()).toBeUndefined();
      await advance(13 * MINUTE - 1000);
      http.expectNone(() => true);
    });

    it('si otra pestaña cerró la sesión, esta vuelve a iniciar sesión', async () => {
      idle.watch(status(900));

      channel.handler?.({ type: 'ended' });

      expect(navigation.replaced).toEqual(['/bff/login?returnTo=%2F']);
    });

    it('al volver a ser visible la pestaña, consulta de inmediato: los temporizadores se frenan en segundo plano', async () => {
      idle.watch(status(900));
      vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');

      document.dispatchEvent(new Event('visibilitychange'));
      await advance(0);

      await answerSession(status(100));
      expect(idle.warning()).toEqual({ kind: 'idle', secondsLeft: 100 });
    });

    it('oculta no consulta', async () => {
      idle.watch(status(900));
      vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');

      document.dispatchEvent(new Event('visibilitychange'));
      await advance(0);

      http.expectNone(() => true);
    });
  });

  describe('cierre de sesión y limpieza', () => {
    it('logout() cierra la sesión por el BFF y deja de vigilar', async () => {
      idle.watch(status(900));

      const result = idle.logout();
      http.expectOne('/bff/logout').flush({ redirectTo: '/bff/login' });
      await result;

      expect(navigation.replaced).toEqual(['/bff/login']);
      await advance(30 * MINUTE);
      http.expectNone(() => true);
    });

    it('volver a vigilar reemplaza la vigilancia anterior: no se duplican las consultas', async () => {
      idle.watch(status(900));
      idle.watch(status(900));

      await advance(13 * MINUTE);

      await answerSession(status(120));
    });

    it('al destruirse el servicio no queda ningún temporizador', async () => {
      idle.watch(status(900));

      TestBed.resetTestingModule();
      await advance(30 * MINUTE);

      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
