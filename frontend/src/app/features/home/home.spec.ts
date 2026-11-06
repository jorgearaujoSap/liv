import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { buildMenu } from '../../core/navigation/menu-presentation';
import { WorkspaceService } from '../shell/workspace.service';
import { FakeWorkspace, leaf } from '../testing/fixtures';
import { Home } from './home';

const text = (element: Element | null | undefined) =>
  element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

describe('Inicio (001-02, 001-15)', () => {
  let workspace: FakeWorkspace;

  const render = () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };
  const cards = (host: HTMLElement) => Array.from(host.querySelectorAll('liv-module-card'));

  beforeEach(() => {
    workspace = new FakeWorkspace();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: WorkspaceService, useValue: workspace }],
    });
  });

  it('saluda por el primer nombre y dice qué se ve en la administración', () => {
    const host = render();

    expect(text(host.querySelector('h1'))).toBe('Hola, Laura');
    expect(text(host)).toContain(
      'Estas son las opciones disponibles para tu rol en Municipio de Ipiales. Los módulos se habilitan a medida que se publican.',
    );
  });

  it('con un nombre vacío saluda sin nombre', () => {
    workspace.user.update((user) => user && { ...user, fullName: '   ' });

    expect(text(render().querySelector('h1'))).toBe('Hola');
  });

  it('muestra una tarjeta por módulo permitido, con su descripción', () => {
    const host = render();

    expect(cards(host).map((card) => text(card.querySelector('.module-card__title')))).toEqual([
      'Administración',
      'Registro automotor y contribuyentes',
      'Liquidación',
      'Cuenta corriente y recaudo',
      'Cartera',
      'Cobro',
      'Medidas cautelares',
      'Documentos y notificaciones',
      'Auditoría e integraciones',
    ]);
    expect(text(cards(host)[5])).toContain('Gestión del cobro persuasivo y coactivo.');
  });

  it('un módulo que la persona no puede ver no aparece', () => {
    workspace.menu.set(buildMenu({ items: [leaf('3'), leaf('9')] }));

    expect(cards(render())).toHaveLength(2);
    expect(text(render())).not.toContain('Cartera');
  });

  it('sin saber quién es la persona, saluda sin nombre y habla de «tu entidad»', () => {
    workspace.user.set(undefined);
    const host = render();

    expect(text(host.querySelector('h1'))).toBe('Hola');
    expect(text(host)).toContain('para tu rol en tu entidad.');
  });

  it('con un nombre de varias palabras saluda por la primera', () => {
    workspace.user.update((user) => user && { ...user, fullName: '  María   José Pérez ' });

    expect(text(render().querySelector('h1'))).toBe('Hola, María');
  });

  it('un módulo con una opción disponible es una tarjeta habilitada que lleva a esa opción', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    workspace.menu.set(
      buildMenu({
        items: [{ code: '1', status: 'AVAILABLE', children: [leaf('1.2', 'AVAILABLE')] }],
      }),
    );
    const [card] = cards(render());

    const link = card.querySelector('a[href="/opcion/1.2"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    link.click();
    expect(navigate).toHaveBeenCalledWith('/opcion/1.2');
  });

  it('un módulo sin construir queda deshabilitado, con la insignia «Próximamente», y no navega', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const [first] = cards(render());

    expect(text(first)).toContain('Próximamente');
    expect(first.querySelector('[aria-disabled="true"]')).not.toBeNull();
    expect(first.querySelector('a[href]')).toBeNull();
    (first.querySelector('[aria-disabled="true"]') as HTMLElement).click();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('un módulo disponible es un enlace a su ruta y navega con el enrutador', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    workspace.menu.set(buildMenu({ items: [leaf('3', 'AVAILABLE')] }));
    const [card] = cards(render());

    const link = card.querySelector('a[href="/opcion/3"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(text(card)).not.toContain('Próximamente');
    link.click();
    expect(navigate).toHaveBeenCalledWith('/opcion/3');
  });

  it('sin módulos, sigue saludando y no inventa tarjetas', () => {
    workspace.menu.set(buildMenu({ items: [] }));
    const host = render();

    expect(cards(host)).toHaveLength(0);
    expect(text(host.querySelector('h1'))).toBe('Hola, Laura');
  });
});
