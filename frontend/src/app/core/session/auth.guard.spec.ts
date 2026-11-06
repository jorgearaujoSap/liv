import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';

import { APP_PATHS } from '../app-paths';
import { BrowserNavigation } from '../browser/browser-navigation.service';
import { authGuard } from './auth.guard';
import { IdleService } from './idle.service';
import { SessionChannel } from './session-channel';

class FakeNavigation {
  readonly replaced: string[] = [];
  replace(url: string): void {
    this.replaced.push(url);
  }
}

// 001-01: sin sesión, cualquier ruta lleva a iniciar sesión y, al autenticarse, vuelve a la pedida.
describe('authGuard', () => {
  let http: HttpTestingController;
  let navigation: FakeNavigation;
  let watched: unknown[];

  const run = (url: string) =>
    TestBed.runInInjectionContext(
      () =>
        authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as Promise<
          boolean | UrlTree
        >,
    );

  beforeEach(() => {
    navigation = new FakeNavigation();
    watched = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BrowserNavigation, useValue: navigation },
        {
          provide: SessionChannel,
          useValue: { post: () => undefined, listen: () => () => undefined },
        },
        { provide: IdleService, useValue: { watch: (status: unknown) => watched.push(status) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('con sesión abierta deja pasar y empieza a vigilar la inactividad', async () => {
    const result = run('/liquidacion');
    http.expectOne('/bff/session').flush({ idleSecondsLeft: 900, absoluteSecondsLeft: 28800 });

    expect(await result).toBe(true);
    expect(watched).toEqual([{ idleSecondsLeft: 900, absoluteSecondsLeft: 28800 }]);
  });

  it('sin sesión navega por completo a /bff/login con la ruta pedida como returnTo', async () => {
    const result = run('/liquidacion/lista?anio=2026');
    http.expectOne('/bff/session').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await result).toBe(false);
    expect(navigation.replaced).toEqual([
      '/bff/login?returnTo=%2Fliquidacion%2Flista%3Fanio%3D2026',
    ]);
    expect(watched).toEqual([]);
  });

  it('si no se puede saber si hay sesión (502), lleva a la pantalla de error y no a iniciar sesión', async () => {
    const result = await (async () => {
      const pending = run('/liquidacion');
      http.expectOne('/bff/session').flush(null, { status: 502, statusText: 'Bad Gateway' });
      return pending;
    })();

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe(APP_PATHS.error);
    expect(navigation.replaced).toEqual([]);
  });
});
