import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { declaredStyle } from '../../testing/css';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { AppHeaderComponent } from './app-header.component';

@Component({
  imports: [AppHeaderComponent],
  template: `
    <liv-app-header
      [administration]="administration"
      [userName]="userName"
      [role]="role"
      (signOut)="signOuts = signOuts + 1"
    />
  `,
})
class HostComponent {
  administration = 'Municipio de Ipiales';
  userName = 'Laura Martínez';
  role: string | undefined = 'Administración del sistema';
  signOuts = 0;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return {
    host: fixture.componentInstance,
    root,
    header: root.querySelector('liv-app-header') as HTMLElement,
  };
}

function iconMarkup(name: IconName) {
  const fixture = TestBed.createComponent(IconComponent);
  fixture.componentRef.setInput('name', name);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
}

describe('AppHeader', () => {
  it('es el encabezado de la aplicación (banner)', () => {
    expect(render().root.querySelector('header')).not.toBeNull();
  });

  it('muestra la administración activa con el icono landmark de 20 px', () => {
    const { header } = render();
    expect(header.querySelector('.app-header__administration')?.textContent?.trim()).toBe(
      'Municipio de Ipiales',
    );
    const icon = header.querySelector('.app-header__context liv-icon svg');
    expect(icon?.innerHTML).toBe(iconMarkup('landmark'));
    expect(icon?.getAttribute('width')).toBe('20');
  });

  it('muestra el nombre y el rol del usuario con su avatar de iniciales', () => {
    const { header } = render();
    expect(header.querySelector('.app-header__name')?.textContent?.trim()).toBe('Laura Martínez');
    expect(header.querySelector('.app-header__role')?.textContent?.trim()).toBe(
      'Administración del sistema',
    );
    const avatar = header.querySelector('liv-avatar') as HTMLElement;
    expect(avatar.textContent?.trim()).toBe('LM');
    expect(avatar.dataset['size']).toBe('md');
  });

  it('el rol es opcional', () => {
    expect(render({ role: undefined }).header.querySelector('.app-header__role')).toBeNull();
  });

  it.each<[string, string]>([
    ['Laura Martínez', 'LM'],
    ['  laura   del  carmen martínez ', 'LM'],
    ['Administrador', 'A'],
    ['J', 'J'],
    ['😀 Ana', '😀A'],
    ['   ', '?'],
    ['', '?'],
  ])('las iniciales de "%s" son "%s"', (userName, initials) => {
    expect(render({ userName }).header.querySelector('liv-avatar')?.textContent?.trim()).toBe(
      initials,
    );
  });

  it('"Salir" es un botón Ghost con el icono log-out que emite signOut', () => {
    const { header, host } = render();
    const button = header.querySelector('liv-button') as HTMLElement;
    expect(button.dataset['variant']).toBe('ghost');
    expect(button.textContent?.trim()).toBe('Salir');
    expect(button.querySelector('liv-icon svg')?.innerHTML).toBe(iconMarkup('log-out'));
    (button.querySelector('button') as HTMLButtonElement).click();
    expect(host.signOuts).toBe(1);
  });

  it('usa los tokens de Figma: fondo bg-surface, borde border-default y alto de 64 px', () => {
    const { header } = render();
    expect(getComputedStyle(header).backgroundColor).toContain('var(--color-bg-surface');
    expect(declaredStyle(header, 'border-color')).toContain('var(--color-border-default');
    expect(declaredStyle(header, 'height')).toContain('64px');
  });

  it.each<[string, string]>([
    ['.app-header__administration', '--color-text-primary'],
    ['.app-header__name', '--color-text-primary'],
    ['.app-header__role', '--color-text-secondary'],
    ['.app-header__icon', '--color-text-muted'],
  ])('%s usa el color %s', (selector, token) => {
    const element = render().header.querySelector(selector) as HTMLElement;
    expect(getComputedStyle(element).color).toContain(`var(${token}`);
  });

  it('cumple axe (001-19), con y sin rol', async () => {
    await expectNoAxeViolations(render().root);
    await expectNoAxeViolations(render({ role: undefined }).root);
  });
});
