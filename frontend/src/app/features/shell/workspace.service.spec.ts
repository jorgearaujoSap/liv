import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CapabilityService } from '../../core/capabilities/capability.service';
import { SKIP_FORBIDDEN_HANDLING } from '../../core/http/api-context';
import { TimeoutError } from '../../core/http/with-timeout';

import { ALL_MODULES, USER } from '../testing/fixtures';
import { WorkspaceService } from './workspace.service';

// El contexto de trabajo de la persona (001-02, 001-15): quién es y qué menú ve, ambos del servidor.
describe('WorkspaceService', () => {
  let http: HttpTestingController;
  let workspace: WorkspaceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    workspace = TestBed.inject(WorkspaceService);
  });

  afterEach(() => http.verify());

  it('antes de cargar no hay persona ni menú', () => {
    expect(workspace.user()).toBeUndefined();
    expect(workspace.menu()).toEqual([]);
  });

  it('carga la persona y sus capacidades a la vez, y arma el menú', async () => {
    const loading = workspace.load();
    http.expectOne('/api/v1/me').flush(USER);
    http.expectOne('/api/v1/me/capabilities').flush(ALL_MODULES);
    await loading;

    expect(workspace.user()?.fullName).toBe('Laura Martínez');
    expect(workspace.user()?.administration.name).toBe('Municipio de Ipiales');
    expect(workspace.menu().map((module) => module.code)).toEqual([
      '1',
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
      '8',
      '9',
    ]);
  });

  it('si falla una de las dos, falla la carga y no deja la persona a medias', async () => {
    const loading = workspace.load();
    http.expectOne('/api/v1/me').flush(USER);
    http
      .expectOne('/api/v1/me/capabilities')
      .flush(null, { status: 500, statusText: 'Server Error' });

    await expect(loading).rejects.toBeDefined();
    expect(workspace.user()).toBeUndefined();
  });

  it('un fallo no se guarda: el próximo intento vuelve a pedir', async () => {
    const first = workspace.load();
    http.expectOne('/api/v1/me').flush(null, { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/v1/me/capabilities').flush(ALL_MODULES);
    await expect(first).rejects.toBeDefined();

    const second = workspace.load();
    http.expectOne('/api/v1/me').flush(USER);
    http.match('/api/v1/me/capabilities').forEach((request) => request.flush(ALL_MODULES));
    await second;

    expect(workspace.user()?.id).toBe(USER.id);
  });

  it('si el servidor no responde, la carga falla por tiempo límite en lugar de quedar colgada', async () => {
    vi.useFakeTimers();
    try {
      const loading = workspace.load();
      const rejection = expect(loading).rejects.toBeInstanceOf(TimeoutError);
      http.expectOne('/api/v1/me');
      http.expectOne('/api/v1/me/capabilities');

      await vi.advanceTimersByTimeAsync(10_000);

      await rejection;
      expect(workspace.user()).toBeUndefined();
    } finally {
      vi.useRealTimers();
    }
  });

  // 001-02 y el cambio de rol con la sesión abierta: tras un 403 el interceptor recarga las capacidades, y la
  // cabecera (rol y administración) no puede quedarse con la persona anterior.
  it('si las capacidades se recargan (p. ej. tras un 403), vuelve a pedir la persona', async () => {
    const loading = workspace.load();
    http.expectOne('/api/v1/me').flush(USER);
    http.expectOne('/api/v1/me/capabilities').flush(ALL_MODULES);
    await loading;
    TestBed.tick();
    http.expectNone('/api/v1/me');

    const reloaded = TestBed.inject(CapabilityService).reload();
    http.expectOne('/api/v1/me/capabilities').flush({ items: [] });
    await reloaded;
    TestBed.tick();

    http.expectOne('/api/v1/me').flush({ ...USER, roles: ['AUDITOR'] });
    await Promise.resolve();
    expect(workspace.user()?.roles).toEqual(['AUDITOR']);
    expect(workspace.menu()).toEqual([]);
  });

  // Un 403 al pedir quién es la persona no es «sin permiso a una opción»: no hay con qué armar la estructura
  // general. No debe disparar el manejo del 403 del interceptor (recargar capacidades y navegar a /sin-permiso).
  it('pedir la persona no dispara el manejo del 403 del interceptor, ni al cargar ni al refrescar', async () => {
    const loading = workspace.load();
    const first = http.expectOne('/api/v1/me');
    expect(first.request.context.get(SKIP_FORBIDDEN_HANDLING)).toBe(true);
    first.flush(USER);
    http.expectOne('/api/v1/me/capabilities').flush(ALL_MODULES);
    await loading;
    TestBed.tick();

    const reloaded = TestBed.inject(CapabilityService).reload();
    http.expectOne('/api/v1/me/capabilities').flush({ items: [] });
    await reloaded;
    TestBed.tick();

    const refresh = http.expectOne('/api/v1/me');
    expect(refresh.request.context.get(SKIP_FORBIDDEN_HANDLING)).toBe(true);
    refresh.flush(USER);
  });

  it('si refrescar la persona falla, se conserva la anterior', async () => {
    const loading = workspace.load();
    http.expectOne('/api/v1/me').flush(USER);
    http.expectOne('/api/v1/me/capabilities').flush(ALL_MODULES);
    await loading;
    TestBed.tick();

    const reloaded = TestBed.inject(CapabilityService).reload();
    http.expectOne('/api/v1/me/capabilities').flush({ items: [] });
    await reloaded;
    TestBed.tick();
    http.expectOne('/api/v1/me').flush(null, { status: 500, statusText: 'Server Error' });
    await vi.waitFor(() => expect(workspace.user()?.roles).toEqual(['ADMIN_SISTEMA']));
  });
});
