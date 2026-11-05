import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CoreApi } from './core/core-api';
import { getCurrentUser } from './core/functions';
import type { CurrentUser } from './core/models';
import { getCapabilities } from './platform/functions';
import type { Capabilities } from './platform/models';
import { PlatformApi } from './platform/platform-api';
import { BffApi } from './bff/bff-api';
import { getSession, keepAliveSession, logout } from './bff/functions';
import type { SessionStatus } from './bff/models';

// T20 (feature 001): el cliente se genera de los contratos fijados en api-spec/ y compila contra las
// tres rutas que usa la pantalla de inicio. Todas las llamadas salen por el BFF, a la misma raíz.
describe('cliente generado de la API', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GET /api/v1/me devuelve el usuario de core', async () => {
    const user: CurrentUser = {
      id: '8f4c6a0e-0000-4000-8000-000000000001',
      fullName: 'Usuario de prueba',
      email: 'prueba@liv.test',
      administration: { id: '8f4c6a0e-0000-4000-8000-000000000002', name: 'Ipiales' },
      roles: ['ADMIN'],
      permissions: ['core.user.read'],
    };

    const result = TestBed.inject(CoreApi).invoke(getCurrentUser);
    const request = http.expectOne('/api/v1/me');
    expect(request.request.method).toBe('GET');
    request.flush(user);

    expect(await result).toEqual(user);
  });

  it('GET /api/v1/me/capabilities devuelve el árbol de opciones de platform', async () => {
    const capabilities: Capabilities = {
      items: [{ code: '1', status: 'AVAILABLE', children: [] }],
    };

    const result = TestBed.inject(PlatformApi).invoke(getCapabilities);
    const request = http.expectOne('/api/v1/me/capabilities');
    expect(request.request.method).toBe('GET');
    request.flush(capabilities);

    expect(await result).toEqual(capabilities);
  });

  it('GET /bff/session devuelve lo que le queda a la sesión', async () => {
    const status: SessionStatus = { idleSecondsLeft: 900, absoluteSecondsLeft: 28800 };

    const result = TestBed.inject(BffApi).invoke(getSession);
    const request = http.expectOne('/bff/session');
    expect(request.request.method).toBe('GET');
    request.flush(status);

    expect(await result).toEqual(status);
  });

  it('keepalive y cierre de sesión son POST del BFF', () => {
    const bff = TestBed.inject(BffApi);
    bff.invoke(keepAliveSession);
    bff.invoke(logout);

    expect(http.expectOne('/bff/session/keepalive').request.method).toBe('POST');
    expect(http.expectOne('/bff/logout').request.method).toBe('POST');
  });
});
