import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { BrowserNavigation } from '../../core/browser/browser-navigation.service';
import { SessionService } from '../../core/session/session.service';
import { WorkspaceService } from '../shell/workspace.service';
import { FakeWorkspace } from '../testing/fixtures';
import { ComingSoon } from './coming-soon';
import { Forbidden } from './forbidden';
import { LoadError } from './load-error';

const text = (host: HTMLElement) => host.textContent?.replace(/\s+/g, ' ').trim() ?? '';
const buttons = (host: HTMLElement) =>
  Array.from(host.querySelectorAll('button')).map((button) => button.textContent?.trim());

describe('pantallas de resultado', () => {
  let workspace: FakeWorkspace;

  beforeEach(() => {
    workspace = new FakeWorkspace();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: WorkspaceService, useValue: workspace }],
    });
  });

  describe('Sin permiso (001-16)', () => {
    const render = () => {
      const fixture = TestBed.createComponent(Forbidden);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    };

    it('dice qué pasó y qué hacer, con la administración de la persona', () => {
      const host = render();

      expect(host.querySelector('h1')?.textContent?.trim()).toBe(
        'No tienes permiso para ver esta página',
      );
      expect(text(host)).toContain('Error 403');
      expect(text(host)).toContain(
        'Tu rol no incluye esta opción en Municipio de Ipiales. Si necesitas acceso, solicítalo al administrador de tu entidad.',
      );
    });

    it('sin saber la administración, habla de «tu entidad»', () => {
      workspace.user.set(undefined);

      expect(text(render())).toContain('Tu rol no incluye esta opción en tu entidad.');
    });

    it('«Volver al inicio» lleva a Inicio', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const host = render();

      expect(buttons(host)).toEqual(['Volver al inicio']);
      host.querySelector('button')?.click();
      expect(navigate).toHaveBeenCalledWith('/');
    });
  });

  describe('Próximamente (001-15)', () => {
    const render = () => {
      const fixture = TestBed.createComponent(ComingSoon);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    };

    it('avisa que la opción se habilitará cuando esté lista', () => {
      const host = render();

      expect(host.querySelector('h1')?.textContent?.trim()).toBe(
        'Esta opción estará disponible pronto',
      );
      expect(text(host)).toContain(
        'Estamos construyendo esta parte de LIV. Cuando esté lista, aparecerá habilitada en el menú.',
      );
    });

    it('«Volver al inicio» lleva a Inicio', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const host = render();

      host.querySelector('button')?.click();
      expect(navigate).toHaveBeenCalledWith('/');
    });
  });

  describe('Error al cargar la sesión', () => {
    const navigation = { replace: vi.fn() };
    const session = { logout: vi.fn().mockResolvedValue(undefined) };
    const render = () => {
      const fixture = TestBed.createComponent(LoadError);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    };

    beforeEach(() => {
      navigation.replace.mockClear();
      session.logout.mockClear();
      TestBed.overrideProvider(BrowserNavigation, { useValue: navigation });
      TestBed.overrideProvider(SessionService, { useValue: session });
    });

    it('dice que el servicio no responde y ofrece salir o reintentar', () => {
      const host = render();

      expect(host.querySelector('h1')?.textContent?.trim()).toBe('No pudimos cargar tu sesión');
      expect(text(host)).toContain(
        'El servicio no responde. Inténtalo de nuevo en unos minutos; si el problema continúa, comunícate con soporte técnico.',
      );
      expect(buttons(host)).toEqual(['Salir', 'Reintentar']);
    });

    it('«Reintentar» recarga la aplicación desde el inicio', () => {
      render().querySelectorAll('button')[1].click();

      expect(navigation.replace).toHaveBeenCalledWith('/');
    });

    it('«Salir» cierra la sesión', () => {
      render().querySelectorAll('button')[0].click();

      expect(session.logout).toHaveBeenCalledOnce();
    });
  });
});
