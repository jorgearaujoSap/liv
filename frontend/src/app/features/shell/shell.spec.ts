import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { buildMenu } from '../../core/navigation/menu-presentation';
import { IdleService, type SessionWarning } from '../../core/session/idle.service';
import { SessionService } from '../../core/session/session.service';
import { FakeWorkspace, leaf, USER } from '../testing/fixtures';
import { Shell } from './shell';
import { WorkspaceService } from './workspace.service';

const text = (element: Element | null | undefined) =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

class FakeIdle {
  readonly warning = signal<SessionWarning | undefined>(undefined);
  readonly extend = vi.fn().mockResolvedValue(true);
  readonly logout = vi.fn().mockResolvedValue(undefined);
}

describe('estructura general (001-02, 001-15)', () => {
  let workspace: FakeWorkspace;
  let idle: FakeIdle;
  let session: { isRedirecting: boolean };
  let router: Router;

  const render = async () => {
    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  };
  const host = (fixture: { nativeElement: unknown }) => fixture.nativeElement as HTMLElement;

  beforeEach(() => {
    workspace = new FakeWorkspace();
    idle = new FakeIdle();
    session = { isRedirecting: false };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', children: [] },
          { path: 'sin-permiso', children: [] },
        ]),
        { provide: WorkspaceService, useValue: workspace },
        { provide: IdleService, useValue: idle },
        { provide: SessionService, useValue: session },
      ],
    });
    router = TestBed.inject(Router);
  });

  describe('cabecera', () => {
    it('muestra el nombre, la administración y el rol de la persona (001-02)', async () => {
      const header = host(await render()).querySelector('liv-app-header');

      expect(text(header)).toContain('Laura Martínez');
      expect(text(header)).toContain('Municipio de Ipiales');
      expect(text(header)).toContain('Administración del sistema');
    });

    it('con varios roles los junta, y uno desconocido sale legible', async () => {
      workspace.user.set({ ...USER, roles: ['AUDITOR', 'NUEVO_ROL_DE_PRUEBA'] });
      const header = host(await render()).querySelector('liv-app-header');

      expect(text(header)).toContain('Auditor, Nuevo rol de prueba');
    });

    it('«Salir» cierra la sesión', async () => {
      const fixture = await render();
      const signOut = Array.from(host(fixture).querySelectorAll('liv-app-header button')).find(
        (button) => text(button) === 'Salir',
      ) as HTMLButtonElement;

      signOut.click();

      expect(idle.logout).toHaveBeenCalledOnce();
    });
  });

  describe('menú lateral', () => {
    it('trae «Inicio», la sección «Módulos» y un elemento por módulo permitido', async () => {
      const nav = host(await render()).querySelector('liv-side-nav');

      expect(text(nav)).toContain('Inicio');
      expect(text(nav)).toContain('Módulos');
      for (const label of [
        'Administración',
        'Registro automotor y contribuyentes',
        'Liquidación',
        'Cuenta corriente y recaudo',
        'Cartera',
        'Cobro',
        'Medidas cautelares',
        'Documentos y notificaciones',
        'Auditoría e integraciones',
      ]) {
        expect(text(nav)).toContain(label);
      }
      expect(text(nav)).toContain('LIV · versión 0.1.0');
    });

    it('solo trae lo que la persona puede ver', async () => {
      workspace.menu.set(buildMenu({ items: [leaf('3'), leaf('9')] }));
      const nav = host(await render()).querySelector('liv-side-nav');

      expect(text(nav)).toContain('Liquidación');
      expect(text(nav)).not.toContain('Cartera');
    });

    it('las opciones sin construir (COMING_SOON) están deshabilitadas: no navegan', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      const nav = host(await render()).querySelector('liv-side-nav') as HTMLElement;
      const disabled = Array.from(nav.querySelectorAll('liv-nav-item[data-disabled]'));

      // Los ocho módulos sin subopciones.
      expect(disabled).toHaveLength(8);
      for (const item of disabled) {
        expect(item.querySelector('[aria-disabled="true"]')).not.toBeNull();
        expect(item.querySelector('a[href]')).toBeNull();
        (item.querySelector('[aria-disabled="true"]') as HTMLElement).click();
      }
      expect(navigate).not.toHaveBeenCalled();
    });

    it('un módulo con subopciones se abre y muestra las suyas, también deshabilitadas', async () => {
      const fixture = await render();
      const nav = host(fixture).querySelector('liv-side-nav') as HTMLElement;
      const group = nav.querySelector('liv-nav-item[data-type="group"]') as HTMLElement;

      expect(group.getAttribute('data-disabled')).toBeNull();
      expect(group.querySelector('button')?.getAttribute('aria-expanded')).toBe('false');
      (group.querySelector('button') as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(group.querySelector('button')?.getAttribute('aria-expanded')).toBe('true');

      const subitems = Array.from(group.querySelectorAll('liv-nav-item[data-type="subitem"]'));
      expect(subitems.map(text)).toEqual([
        'Entidades y administraciones tributarias',
        'Usuarios, roles y permisos',
        'Vigencias fiscales',
      ]);
      expect(subitems.every((item) => item.hasAttribute('data-disabled'))).toBe(true);
    });

    it('una opción disponible navega con el enrutador', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      workspace.menu.set(buildMenu({ items: [leaf('3', 'AVAILABLE')] }));
      const nav = host(await render()).querySelector('liv-side-nav') as HTMLElement;

      (nav.querySelector('a[href="/opcion/3"]') as HTMLAnchorElement).click();

      expect(navigate).toHaveBeenCalledWith('/opcion/3');
    });

    it('una opción disponible dentro de un módulo navega, y sus hermanas sin construir no', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      workspace.menu.set(
        buildMenu({
          items: [
            {
              code: '1',
              status: 'AVAILABLE',
              children: [leaf('1.1'), leaf('1.2', 'AVAILABLE'), leaf('1.4')],
            },
          ],
        }),
      );
      const fixture = await render();
      const nav = host(fixture).querySelector('liv-side-nav') as HTMLElement;
      (nav.querySelector('liv-nav-item[data-type="group"] button') as HTMLButtonElement).click();
      fixture.detectChanges();

      const subitems = Array.from(nav.querySelectorAll('liv-nav-item[data-type="subitem"]'));
      expect(subitems.map((item) => item.hasAttribute('data-disabled'))).toEqual([
        true,
        false,
        true,
      ]);
      (subitems[1].querySelector('a[href]') as HTMLAnchorElement).click();
      expect(navigate).toHaveBeenCalledWith('/opcion/1.2');
      expect(navigate).toHaveBeenCalledTimes(1);
    });

    it('en una opción del módulo, el módulo se marca y se abre; en otro con prefijo parecido, no', async () => {
      workspace.menu.set(
        buildMenu({
          items: [
            { code: '1', status: 'AVAILABLE', children: [leaf('1.2', 'AVAILABLE')] },
            { code: '10', status: 'AVAILABLE', children: [leaf('10.1', 'AVAILABLE')] },
          ],
        }),
      );
      router.resetConfig([{ path: 'opcion/:code', children: [] }]);
      await router.navigateByUrl('/');
      const fixture = await render();
      const groups = () =>
        Array.from(host(fixture).querySelectorAll('liv-nav-item[data-type="group"]'));
      const expanded = () =>
        groups().map((group) => group.querySelector('button')?.getAttribute('aria-expanded'));

      expect(expanded()).toEqual(['false', 'false']);

      await router.navigateByUrl('/opcion/1.2');
      fixture.detectChanges();

      expect(expanded()).toEqual(['true', 'false']);
      expect(groups()[0].hasAttribute('data-active')).toBe(true);
      expect(groups()[1].hasAttribute('data-active')).toBe(false);
      expect(
        host(fixture).querySelectorAll('liv-nav-item[data-type="subitem"] [aria-current="page"]'),
      ).toHaveLength(1);
    });

    describe('tercer nivel (1.5.x y 1.6.x)', () => {
      const withLoads = () =>
        workspace.menu.set(
          buildMenu({
            items: [
              {
                code: '1',
                status: 'AVAILABLE',
                children: [
                  leaf('1.4', 'AVAILABLE'),
                  {
                    code: '1.5',
                    status: 'AVAILABLE',
                    children: [leaf('1.5.3', 'AVAILABLE'), leaf('1.5.4')],
                  },
                  { code: '1.6', status: 'COMING_SOON', children: [leaf('1.6.1')] },
                ],
              },
            ],
          }),
        );
      const open = (fixture: { detectChanges(): void }, item: Element) => {
        (item.querySelector('button') as HTMLButtonElement).click();
        fixture.detectChanges();
      };

      it('una opción con subopciones es un subgrupo con las suyas en el tercer nivel, también deshabilitadas', async () => {
        withLoads();
        const fixture = await render();
        const nav = host(fixture).querySelector('liv-side-nav') as HTMLElement;
        open(fixture, nav.querySelector('liv-nav-item[data-type="group"]') as HTMLElement);

        const subgroups = Array.from(nav.querySelectorAll('liv-nav-item[data-type="subgroup"]'));
        expect(subgroups.map((item) => text(item.querySelector('button')))).toEqual([
          'Cargue de tablas base por vigencia',
          'Consultas y exportación de tablas base',
        ]);
        expect(subgroups.map((item) => item.hasAttribute('data-disabled'))).toEqual([false, true]);
        // 1.4 sigue siendo una opción de segundo nivel.
        expect(
          Array.from(nav.querySelectorAll('liv-nav-item[data-type="subitem"]')).map(text),
        ).toEqual(['Vigencias fiscales']);

        open(fixture, subgroups[0]);
        const nested = Array.from(
          subgroups[0].querySelectorAll('liv-nav-item[data-type="nested"]'),
        );
        expect(nested.map(text)).toEqual(['Cargar archivo de avalúos', 'Bandeja de lotes']);
        expect(nested.map((item) => item.hasAttribute('data-disabled'))).toEqual([false, true]);
      });

      it('una opción de tercer nivel navega con el enrutador', async () => {
        const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
        withLoads();
        const fixture = await render();
        const nav = host(fixture).querySelector('liv-side-nav') as HTMLElement;
        open(fixture, nav.querySelector('liv-nav-item[data-type="group"]') as HTMLElement);
        open(fixture, nav.querySelector('liv-nav-item[data-type="subgroup"]') as HTMLElement);

        (nav.querySelector('a[href="/opcion/1.5.3"]') as HTMLAnchorElement).click();

        expect(navigate).toHaveBeenCalledWith('/opcion/1.5.3');
      });

      it('en una opción de tercer nivel, el módulo y su subgrupo se marcan y se abren, y la opción es la actual', async () => {
        withLoads();
        router.resetConfig([{ path: 'opcion/:code', children: [] }]);
        await router.navigateByUrl('/opcion/1.5.3');
        const fixture = await render();
        const nav = host(fixture).querySelector('liv-side-nav') as HTMLElement;
        const group = nav.querySelector('liv-nav-item[data-type="group"]') as HTMLElement;
        const subgroups = Array.from(nav.querySelectorAll('liv-nav-item[data-type="subgroup"]'));

        expect(group.querySelector('button')?.getAttribute('aria-expanded')).toBe('true');
        expect(
          subgroups.map((item) => item.querySelector('button')?.getAttribute('aria-expanded')),
        ).toEqual(['true', 'false']);
        expect(subgroups.map((item) => item.hasAttribute('data-active'))).toEqual([true, false]);
        const current = nav.querySelectorAll('[aria-current="page"]');
        expect(current).toHaveLength(1);
        expect(current[0].closest('liv-nav-item')?.getAttribute('data-type')).toBe('nested');
      });
    });

    it('«Inicio» marca la pantalla actual, y solo en la ruta raíz', async () => {
      await router.navigateByUrl('/');
      const fixture = await render();
      const inicio = () =>
        host(fixture).querySelector('liv-nav-item[data-type="item"] [aria-current="page"]');

      expect(text(inicio())).toBe('Inicio');
      await router.navigateByUrl('/sin-permiso');
      fixture.detectChanges();
      expect(inicio()).toBeNull();
    });
  });

  describe('carga', () => {
    it('mientras carga muestra el marco con marcadores, ocupado, sin menú ni cabecera', async () => {
      workspace.loading = new Promise<void>(() => undefined);
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();

      expect(host(fixture).querySelector('[aria-busy="true"]')).not.toBeNull();
      expect(host(fixture).querySelectorAll('liv-skeleton').length).toBeGreaterThan(10);
      expect(host(fixture).querySelector('liv-logo')).not.toBeNull();
      expect(host(fixture).querySelector('liv-app-header')).toBeNull();
      expect(host(fixture).querySelector('liv-side-nav')).toBeNull();
    });

    it('al terminar, quita el marco de carga', async () => {
      const fixture = await render();

      expect(host(fixture).querySelector('[aria-busy="true"]')).toBeNull();
      expect(host(fixture).querySelector('liv-skeleton')).toBeNull();
    });

    it('si no carga porque no hay sesión (401) y ya va a iniciar sesión, no va a «Error»', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      session.isRedirecting = true;
      workspace.loading = Promise.reject(new HttpErrorResponse({ status: 401 }));
      await render();

      expect(navigate).not.toHaveBeenCalled();
    });

    it('un 401 sin redirección en curso (no se pudo averiguar si hay sesión) va a «Error»: no queda en «Cargando»', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      session.isRedirecting = false;
      workspace.loading = Promise.reject(new HttpErrorResponse({ status: 401 }));
      await render();

      expect(navigate).toHaveBeenCalledWith('/error');
    });

    it('si se destruye mientras carga, un fallo posterior no navega', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      let fail: (reason: unknown) => void = () => undefined;
      workspace.loading = new Promise<void>((_resolve, reject) => (fail = reject));
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();

      fixture.destroy();
      fail(new Error('sin servicio'));
      await Promise.resolve();
      await Promise.resolve();

      expect(navigate).not.toHaveBeenCalled();
    });

    it('el aviso «Cargando tu sesión…» está fuera del contenedor ocupado, para que se anuncie', async () => {
      workspace.loading = new Promise<void>(() => undefined);
      const fixture = TestBed.createComponent(Shell);
      fixture.detectChanges();
      const status = host(fixture).querySelector('[role="status"]');

      expect(text(status)).toBe('Cargando tu sesión…');
      expect(status?.closest('[aria-busy="true"]')).toBeNull();
    });

    it('si no carga, va a la pantalla de error', async () => {
      const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
      workspace.loading = Promise.reject(new Error('sin servicio'));
      await render();

      expect(navigate).toHaveBeenCalledWith('/error');
    });
  });

  it('no muestra aviso de sesión mientras no haya nada que avisar', async () => {
    const fixture = await render();

    expect(host(fixture).querySelector('[role="dialog"]')).toBeNull();
  });

  it('el aviso de sesión se abre con lo que informa IdleService', async () => {
    const fixture = await render();

    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    fixture.detectChanges();

    expect(host(fixture).querySelector('[role="dialog"]')).not.toBeNull();
  });
});
