import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ALL_MODULES, USER } from '../testing/fixtures';
import { WorkspaceService } from './workspace.service';

// El contexto de trabajo de la persona (001-02, 001-15): quién es y qué menú ve, ambos del servidor.
describe('WorkspaceService', () => {
  let http: HttpTestingController;
  let workspace: WorkspaceService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
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
});
