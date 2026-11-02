import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { PageHeaderComponent } from './page-header.component';

@Component({
  imports: [PageHeaderComponent],
  template: `
    <liv-page-header [heading]="heading" [breadcrumb]="breadcrumb" [description]="description" />
  `,
})
class HostComponent {
  heading = 'Vigencias fiscales';
  breadcrumb: string[] = ['Administración', '1.4 Vigencias fiscales'];
  description: string | undefined = 'Años fiscales en los que se liquida el impuesto.';
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('liv-page-header') as HTMLElement;
}

describe('PageHeader', () => {
  it('el título es el único h1 de la pantalla', () => {
    const title = render().querySelector('.page-header__title');
    expect(title?.tagName).toBe('H1');
    expect(title?.textContent?.trim()).toBe('Vigencias fiscales');
  });

  it('la ruta de navegación es una lista con nombre accesible', () => {
    const nav = render().querySelector('nav');
    expect(nav?.getAttribute('aria-label')).toBe('Ruta de navegación');
    const items = Array.from(nav?.querySelectorAll('li') ?? []).map((item) =>
      item.textContent?.trim(),
    );
    expect(items).toEqual(['Administración', '1.4 Vigencias fiscales']);
  });

  it('separa las partes con chevrons decorativos y la última es la pantalla actual', () => {
    const header = render();
    const separators = header.querySelectorAll('.page-header__separator');
    expect(separators).toHaveLength(1);
    expect(separators[0].querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    const last = header.querySelector('li:last-child');
    expect(last?.getAttribute('aria-current')).toBe('page');
    expect(header.querySelector('li:first-child')?.hasAttribute('aria-current')).toBe(false);
  });

  it('sin ruta no dibuja la navegación', () => {
    expect(render({ breadcrumb: [] }).querySelector('nav')).toBeNull();
  });

  it('la descripción es opcional', () => {
    expect(
      render({ description: undefined }).querySelector('.page-header__description'),
    ).toBeNull();
    expect(render({ description: ' ' }).querySelector('.page-header__description')).toBeNull();
    expect(render().querySelector('.page-header__description')?.textContent?.trim()).toBe(
      'Años fiscales en los que se liquida el impuesto.',
    );
  });

  it('usa los tokens de Figma para la ruta, el título y la descripción', () => {
    const header = render();
    expect(
      getComputedStyle(header.querySelector('.page-header__breadcrumb') as HTMLElement).color,
    ).toContain('var(--color-text-muted');
    expect(
      getComputedStyle(header.querySelector('.page-header__title') as HTMLElement).color,
    ).toContain('var(--color-text-primary');
    expect(
      getComputedStyle(header.querySelector('.page-header__description') as HTMLElement).color,
    ).toContain('var(--color-text-secondary');
  });

  it('cumple axe (001-19), con y sin ruta ni descripción', async () => {
    await expectNoAxeViolations(render());
    await expectNoAxeViolations(render({ breadcrumb: [], description: undefined }));
  });
});
