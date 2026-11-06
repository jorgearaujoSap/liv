import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import axe from 'axe-core';
import { vi } from 'vitest';

import { buildMenu } from '../core/navigation/menu-presentation';
import { IdleService, type SessionWarning } from '../core/session/idle.service';
import { ComingSoon } from './errors/coming-soon';
import { Forbidden } from './errors/forbidden';
import { LoadError } from './errors/load-error';
import { Home } from './home/home';
import { Shell } from './shell/shell';
import { WorkspaceService } from './shell/workspace.service';
import { FakeWorkspace, leaf } from './testing/fixtures';

/**
 * Ejecuta axe-core sobre la pantalla. En jsdom no se puede calcular el contraste de color, así que esta
 * prueba no lo cubre: lo cubren los *tokens* de `liv-components` (contraste verificado en la librería) y,
 * sobre el navegador real, el e2e de T23.
 */
async function violations(element: Element): Promise<string[]> {
  const results = await axe.run(element, { rules: { 'color-contrast': { enabled: false } } });
  return results.violations.map(
    (violation) =>
      `${violation.id}: ${violation.help} (${violation.nodes.map((node) => node.target).join(' | ')})`,
  );
}

class FakeIdle {
  readonly warning = signal<SessionWarning | undefined>(undefined);
  readonly extend = vi.fn().mockResolvedValue(true);
  readonly logout = vi.fn().mockResolvedValue(undefined);
}

// 001-19: las pantallas de la aplicación cumplen WCAG 2.1 AA (axe-core).
// axe recorre todo el DOM de la estructura general, y en jsdom tarda: más de los 5 s predeterminados.
describe('accesibilidad de las pantallas (001-19)', { timeout: 60_000 }, () => {
  let workspace: FakeWorkspace;
  let idle: FakeIdle;

  const open = async (url: string) => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    harness.detectChanges();
    return harness.fixture.nativeElement as HTMLElement;
  };

  beforeEach(() => {
    workspace = new FakeWorkspace();
    idle = new FakeIdle();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: Shell,
            children: [
              { path: '', pathMatch: 'full', component: Home },
              { path: 'sin-permiso', component: Forbidden },
              { path: 'proximamente', component: ComingSoon },
            ],
          },
          { path: 'error', component: LoadError },
        ]),
        { provide: WorkspaceService, useValue: workspace },
        { provide: IdleService, useValue: idle },
      ],
    });
  });

  it('Inicio', async () => {
    const page = await open('/');

    expect(page.querySelector('h1')?.textContent).toContain('Hola, Laura');
    expect(await violations(page)).toEqual([]);
  });

  it('Inicio con el menú abierto en un módulo con subopciones', async () => {
    const page = await open('/');
    const group = page.querySelector('liv-nav-item[data-type="group"]') as HTMLElement;
    (group.querySelector('button') as HTMLButtonElement).click();
    TestBed.tick();

    expect(group.querySelector('button')?.getAttribute('aria-expanded')).toBe('true');
    expect(group.querySelectorAll('liv-nav-item[data-type="subitem"]').length).toBeGreaterThan(0);
    expect(await violations(page)).toEqual([]);
  });

  it('Inicio con opciones disponibles (enlaces) y sin construir', async () => {
    workspace.menu.set(
      buildMenu({
        items: [
          { code: '1', status: 'AVAILABLE', children: [leaf('1.2', 'AVAILABLE'), leaf('1.4')] },
          leaf('3', 'AVAILABLE'),
          leaf('4'),
        ],
      }),
    );
    const page = await open('/');
    (page.querySelector('liv-nav-item[data-type="group"] button') as HTMLButtonElement).click();
    TestBed.tick();

    expect(page.querySelectorAll('a[href^="/opcion/"]').length).toBeGreaterThan(2);
    expect(await violations(page)).toEqual([]);
  });

  it('Aviso de sesión por expirar', async () => {
    const page = await open('/');
    idle.warning.set({ kind: 'idle', secondsLeft: 120 });
    TestBed.tick();

    expect(page.querySelector('[role="dialog"]')).not.toBeNull();
    expect(await violations(page)).toEqual([]);
  });

  it('Aviso del límite de 8 horas', async () => {
    const page = await open('/');
    idle.warning.set({ kind: 'absolute', secondsLeft: 300 });
    TestBed.tick();

    expect(page.querySelector('[role="dialog"]')).not.toBeNull();
    expect(await violations(page)).toEqual([]);
  });

  it('Sin permiso', async () => {
    const page = await open('/sin-permiso');

    expect(page.querySelector('h1')?.textContent).toContain('No tienes permiso');
    expect(await violations(page)).toEqual([]);
  });

  it('Próximamente', async () => {
    const page = await open('/proximamente');

    expect(page.querySelector('h1')?.textContent).toContain('disponible pronto');
    expect(await violations(page)).toEqual([]);
  });

  it('Cargando', async () => {
    workspace.loading = new Promise<void>(() => undefined);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    harness.detectChanges();
    const page = harness.fixture.nativeElement as HTMLElement;

    expect(page.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(await violations(page)).toEqual([]);
  });

  it('Error', async () => {
    const page = await open('/error');

    expect(page.querySelector('h1')?.textContent).toContain('No pudimos cargar tu sesión');
    expect(await violations(page)).toEqual([]);
  });
});
