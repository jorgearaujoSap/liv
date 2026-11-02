import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { declaredStyle } from '../../testing/css';
import { NavItemComponent } from '../nav-item/nav-item.component';
import { NavSectionComponent } from './nav-section.component';
import { SideNavComponent } from './side-nav.component';

@Component({
  imports: [SideNavComponent, NavSectionComponent, NavItemComponent],
  template: `
    <liv-side-nav [label]="label" [footer]="footer">
      <liv-nav-item icon="house" href="/" [active]="true">Inicio</liv-nav-item>
      <liv-nav-section>Módulos</liv-nav-section>
      <liv-nav-item icon="settings" [disabled]="true">Administración</liv-nav-item>
      <liv-nav-item icon="calculator" [disabled]="true">Liquidación</liv-nav-item>
    </liv-side-nav>
  `,
})
class HostComponent {
  label: string | undefined;
  footer: string | undefined = 'LIV · versión 0.1.0';
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return { root, sideNav: root.querySelector('liv-side-nav') as HTMLElement };
}

describe('SideNav', () => {
  it('es la región de navegación principal, con nombre accesible', () => {
    const { root } = render();
    const nav = root.querySelector('nav');
    expect(nav?.getAttribute('aria-label')).toBe('Menú principal');
  });

  it('acepta otro nombre accesible y vuelve al predeterminado si queda vacío', () => {
    expect(
      render({ label: 'Menú del módulo' }).root.querySelector('nav')?.getAttribute('aria-label'),
    ).toBe('Menú del módulo');
    expect(render({ label: ' ' }).root.querySelector('nav')?.getAttribute('aria-label')).toBe(
      'Menú principal',
    );
  });

  it('muestra el logotipo, las opciones proyectadas y el pie', () => {
    const { sideNav } = render();
    expect(sideNav.querySelector('.side-nav__brand liv-logo')).not.toBeNull();
    // Las tres opciones `liv-nav-item` del anfitrión (la sección no cuenta).
    expect(sideNav.querySelectorAll('.side-nav__menu liv-nav-item')).toHaveLength(3);
    expect(sideNav.querySelector('.side-nav__footer')?.textContent?.trim()).toBe(
      'LIV · versión 0.1.0',
    );
  });

  it('el pie es opcional', () => {
    expect(render({ footer: undefined }).sideNav.querySelector('.side-nav__footer')).toBeNull();
    expect(render({ footer: ' ' }).sideNav.querySelector('.side-nav__footer')).toBeNull();
  });

  it('usa los tokens de Figma: fondo bg-surface, borde border-default y ancho de 272 px', () => {
    const { sideNav } = render();
    expect(getComputedStyle(sideNav).backgroundColor).toContain('var(--color-bg-surface');
    expect(declaredStyle(sideNav, 'border-color')).toContain('var(--color-border-default');
    expect(declaredStyle(sideNav, 'width')).toContain('272px');
    const footer = sideNav.querySelector('.side-nav__footer') as HTMLElement;
    expect(getComputedStyle(footer).color).toContain('var(--color-text-muted');
  });

  it('la etiqueta de sección usa text-muted y el texto proyectado', () => {
    const { sideNav } = render();
    const section = sideNav.querySelector('liv-nav-section') as HTMLElement;
    expect(section.textContent?.trim()).toBe('Módulos');
    expect(getComputedStyle(section).color).toContain('var(--color-text-muted');
  });

  it('cumple axe (001-19)', async () => {
    await expectNoAxeViolations(render().root);
    await expectNoAxeViolations(render({ footer: undefined }).root);
  });
});
