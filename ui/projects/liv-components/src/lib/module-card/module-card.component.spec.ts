import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow, usesToken } from '../../testing/css';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { ModuleCardComponent } from './module-card.component';

@Component({
  imports: [ModuleCardComponent],
  template: `
    <liv-module-card
      [heading]="heading"
      [description]="description"
      [icon]="icon"
      [href]="href"
      [disabled]="disabled"
      (navigate)="navigations.push($event)"
      (click)="clicks = clicks + 1"
    />
  `,
})
class HostComponent {
  heading = 'Administración';
  description = 'Entidades, usuarios, normas, vigencias y tablas base de liquidación.';
  icon: IconName = 'settings';
  href: string | undefined = '/administracion';
  disabled = false;
  navigations: string[] = [];
  clicks = 0;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return {
    host: fixture.componentInstance,
    root,
    card: root.querySelector('liv-module-card') as HTMLElement,
    link: root.querySelector('.module-card__link') as HTMLElement,
  };
}

function iconMarkup(name: IconName) {
  const fixture = TestBed.createComponent(IconComponent);
  fixture.componentRef.setInput('name', name);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
}

/** Un `MouseEvent` a medida, porque `element.click()` no admite teclas modificadoras ni otro botón. */
function click(target: Element, init: MouseEventInit = {}) {
  const event = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ...init });
  target.dispatchEvent(event);
  return event;
}

/**
 * Valor de una propiedad en las reglas `:hover` del elemento. jsdom no aplica `:hover`, así que se busca
 * la regla de la hoja de estilos cuyo selector, sin `:hover`, selecciona al elemento.
 */
function declaredHover(element: Element, property: string): string {
  let value = '';
  const visit = (rules: CSSRuleList) => {
    for (const rule of Array.from(rules)) {
      if (rule instanceof CSSStyleRule && rule.selectorText.includes(':hover')) {
        const declared = rule.style.getPropertyValue(property);
        const selector = rule.selectorText.replace(/:hover/g, '');
        if (declared && element.matches(selector)) {
          value = declared;
        }
      } else if ('cssRules' in rule) {
        visit((rule as CSSGroupingRule).cssRules);
      }
    }
  };
  for (const sheet of Array.from(element.ownerDocument.styleSheets)) {
    visit(sheet.cssRules);
  }
  return value;
}

describe('ModuleCard', () => {
  it('toda la tarjeta es un enlace con el título y la descripción', () => {
    const { link } = render();
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe('/administracion');
    expect(link.querySelector('.module-card__title')?.textContent?.trim()).toBe('Administración');
    expect(link.querySelector('.module-card__description')?.textContent?.trim()).toContain(
      'Entidades, usuarios, normas',
    );
  });

  it('muestra el icono de 20 px en el cuadro de color primario suave', () => {
    const { card } = render();
    const icon = card.querySelector('.module-card__tile liv-icon svg');
    expect(icon?.innerHTML).toBe(iconMarkup('settings'));
    expect(icon?.getAttribute('width')).toBe('20');
    expect(
      getComputedStyle(card.querySelector('.module-card__tile') as HTMLElement).backgroundColor,
    ).toContain('var(--color-action-primary-subtle');
    expect(
      usesToken(
        getComputedStyle(card.querySelector('liv-icon') as HTMLElement).color,
        '--color-action-primary',
      ),
    ).toBe(true);
  });

  it('Default usa los tokens de Figma: fondo, borde, radio y sombra sm', () => {
    const { card, link } = render();
    expect(getComputedStyle(link).backgroundColor).toContain('var(--color-bg-surface');
    expect(declaredStyle(link, 'border-color')).toContain('var(--color-border-default');
    expect(declaredStyle(link, 'border-radius')).toContain('var(--radius-xl');
    expect(paintsShadow(link, cssVariable(theme, '--shadow-elevation-sm'))).toBe(true);
    expect(
      getComputedStyle(card.querySelector('.module-card__title') as HTMLElement).color,
    ).toContain('var(--color-text-primary');
    expect(
      getComputedStyle(card.querySelector('.module-card__description') as HTMLElement).color,
    ).toContain('var(--color-text-secondary');
  });

  it('Hover cambia a border-strong y sombra md, y no aplica a la deshabilitada', () => {
    const enabled = render().link;
    expect(declaredHover(enabled, 'border-color')).toContain('var(--color-border-strong');
    expect(declaredHover(enabled, '--tw-shadow')).toBe(
      cssVariable(theme, '--shadow-elevation-md').replace(
        /rgb\(([^)]*)\)/g,
        'var(--tw-shadow-color, rgb($1))',
      ),
    );
    expect(declaredHover(render({ disabled: true }).link, 'border-color')).toBe('');
  });

  it('sin Disabled no muestra la insignia', () => {
    expect(render().card.querySelector('liv-badge')).toBeNull();
  });

  it('Disabled muestra "Próximamente", sin href y con aria-disabled', () => {
    // Módulo aún no disponible.
    const { card, link } = render({ disabled: true });
    expect(card.querySelector('liv-badge')?.textContent?.trim()).toBe('Próximamente');
    expect(link.hasAttribute('href')).toBe(false);
    expect(link.getAttribute('role')).toBe('link');
    expect(link.getAttribute('aria-disabled')).toBe('true');
  });

  it('Disabled usa el cuadro bg-subtle, el icono text-muted y el título text-secondary', () => {
    const { card } = render({ disabled: true });
    expect(
      getComputedStyle(card.querySelector('.module-card__tile') as HTMLElement).backgroundColor,
    ).toContain('var(--color-bg-subtle');
    expect(getComputedStyle(card.querySelector('liv-icon') as HTMLElement).color).toContain(
      'var(--color-text-muted',
    );
    expect(
      getComputedStyle(card.querySelector('.module-card__title') as HTMLElement).color,
    ).toContain('var(--color-text-secondary');
  });

  it('Disabled no navega', () => {
    // No emite navigate, cancela el clic y este no llega a quien escucha el anfitrión.
    const { link, host } = render({ disabled: true });
    const event = click(link);
    expect(event.defaultPrevented).toBe(true);
    expect(host.navigations).toEqual([]);
    expect(host.clicks).toBe(0);
  });

  it('un clic normal emite navigate con el destino y cancela la navegación del navegador', () => {
    const { link, host } = render();
    const event = click(link);
    expect(host.navigations).toEqual(['/administracion']);
    expect(event.defaultPrevented).toBe(true);
  });

  it.each<[string, MouseEventInit]>([
    ['Ctrl', { ctrlKey: true }],
    ['Cmd', { metaKey: true }],
    ['Mayús', { shiftKey: true }],
    ['Alt', { altKey: true }],
    ['botón central', { button: 1 }],
  ])('un clic con %s lo resuelve el navegador (nueva pestaña): no emite navigate', (_, init) => {
    const { link, host } = render();
    const event = click(link, init);
    expect(host.navigations).toEqual([]);
    expect(event.defaultPrevented).toBe(false);
  });

  it.each(['', '   '])('un href vacío (%j) no dibuja href ni navega', (href) => {
    const { link, host } = render({ href });
    expect(link.hasAttribute('href')).toBe(false);
    click(link);
    expect(host.navigations).toEqual([]);
  });

  it('con foco del teclado, la regla de hover no aplica y el anillo de foco gana', () => {
    // jsdom no aplica :hover: se comprueba que la regla de hover deja de valer cuando hay foco visible.
    const { link } = render();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    link.focus();
    expect(declaredHover(link, '--tw-shadow')).toBe('');
    expect(declaredHover(link, 'border-color')).toBe('');
    link.blur();
    expect(declaredHover(link, '--tw-shadow')).not.toBe('');
  });

  it('la descripción vacía no deja un bloque vacío', () => {
    expect(
      render({ description: '  ' }).card.querySelector('.module-card__description'),
    ).toBeNull();
  });

  it('pinta el anillo focus/ring de theme.css solo con el foco del teclado', () => {
    const { link } = render();
    const ring = cssVariable(theme, '--shadow-focus-ring');
    expect(paintsShadow(link, ring)).toBe(false);
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    link.focus();
    expect(paintsShadow(link, ring)).toBe(true);
    link.blur();
    expect(paintsShadow(link, ring)).toBe(false);
  });

  it('cumple axe (001-19), habilitada y deshabilitada', async () => {
    await expectNoAxeViolations(render().root);
    await expectNoAxeViolations(render({ disabled: true }).root);
  });
});
