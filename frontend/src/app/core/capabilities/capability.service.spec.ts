import { provideHttpClient } from '@angular/common/http';
import { vi } from 'vitest';

import { TimeoutError } from '../http/with-timeout';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CapabilityService } from './capability.service';

const only = (code: string) => ({ items: [{ code, status: 'AVAILABLE', children: [] }] });

// Las capacidades se recargan tras un 403 (001-16); varias recargas a la vez no se pisan entre sí.
describe('CapabilityService', () => {
  let http: HttpTestingController;
  let service: CapabilityService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CapabilityService);
  });

  afterEach(() => http.verify());

  it('si el servidor no responde, falla por tiempo límite y el próximo intento vuelve a pedir', async () => {
    vi.useFakeTimers();
    try {
      const loading = service.load();
      const rejection = expect(loading).rejects.toBeInstanceOf(TimeoutError);
      http.expectOne('/api/v1/me/capabilities');

      await vi.advanceTimersByTimeAsync(10_000);
      await rejection;
    } finally {
      vi.useRealTimers();
    }

    const again = service.load();
    http.expectOne('/api/v1/me/capabilities').flush(only('1'));
    expect(await again).toEqual(only('1'));
  });

  it('varias recargas a la vez comparten una sola petición', async () => {
    const results = [service.reload(), service.reload(), service.reload()];

    http.expectOne('/api/v1/me/capabilities').flush(only('1'));

    for (const result of results) {
      expect(await result).toEqual(only('1'));
    }
  });

  it('una respuesta vieja no pisa a una más reciente', async () => {
    const loading = service.load();
    const old = http.expectOne('/api/v1/me/capabilities');
    const reloading = service.reload();
    const recent = http.expectOne('/api/v1/me/capabilities');

    recent.flush(only('nueva'));
    await reloading;
    old.flush(only('vieja'));
    await loading;

    expect(service.find('nueva')).toBeDefined();
    expect(service.find('vieja')).toBeUndefined();
  });

  it('un fallo de la carga vieja no borra la recarga que sigue en curso', async () => {
    const loading = service.load();
    const old = http.expectOne('/api/v1/me/capabilities');
    const reloading = service.reload();
    const recent = http.expectOne('/api/v1/me/capabilities');

    old.flush(null, { status: 500, statusText: 'Server Error' });
    await expect(loading).rejects.toMatchObject({ status: 500 });
    const next = service.load();
    recent.flush(only('1'));

    expect(await next).toEqual(only('1'));
    await reloading;
  });

  it('si la recarga más nueva falla, la respuesta anterior que sí llegó no se pierde', async () => {
    const loading = service.load();
    const old = http.expectOne('/api/v1/me/capabilities');
    const reloading = service.reload();
    const recent = http.expectOne('/api/v1/me/capabilities');

    recent.flush(null, { status: 500, statusText: 'Server Error' });
    await expect(reloading).rejects.toMatchObject({ status: 500 });
    old.flush(only('1'));
    await loading;

    expect(service.find('1')).toBeDefined();
  });

  it('tras un fallo, la siguiente carga vuelve a pedirlas', async () => {
    const failed = service.load();
    http
      .expectOne('/api/v1/me/capabilities')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await expect(failed).rejects.toMatchObject({ status: 500 });

    const again = service.load();
    http.expectOne('/api/v1/me/capabilities').flush(only('1'));

    expect(await again).toEqual(only('1'));
  });
});
