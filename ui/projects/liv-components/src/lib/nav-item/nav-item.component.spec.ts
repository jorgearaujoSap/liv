import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow } from '../../testing/css';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { NavItemComponent, NavItemType } from './nav-item.component';

@Component({
  imports: [NavItemComponent],
  template: `
    <liv-nav-item
      [type]="type"
      [icon]="icon"
      [href]="href"
      [active]="active"
      [disabled]="disabled"
      [(expanded)]="expanded"
      (navigate)="navigations.push($event)"
      (click)="clicks = clicks + 1"
      >Registro automotor</liv-nav-item
    >
  `,
})
class ItemHostComponent {
  type: NavItemType = 'item';
  icon: IconName | undefined = 'car';
  href: string | undefined = '/registro';
  active = false;
  disabled = false;
  expanded = false;
  navigations: string[] = [];
  clicks = 0;
}

@Component({
  imports: [NavItemComponent],
  template: `
    <liv-nav-item type="group" icon="settings" [(expanded)]="expanded">
      Administración
      <liv-nav-item type="subitem" href="/vigencias" [active]="true">Vigencias</liv-nav-item>
      <liv-nav-item type="subitem" href="/tablas">Tablas base</liv-nav-item>
    </liv-nav-item>
  `,
})
class GroupHostComponent {
  expanded = false;
}

function render(inputs: Partial<ItemHostComponent> = {}) {
  const fixture = TestBed.createComponent(ItemHostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    host: fixture.componentInstance,
    root,
    item: root.querySelector('liv-nav-item') as HTMLElement,
    control: root.querySelector('.nav-item__control') as HTMLElement,
  };
}

function renderGroup() {
  const fixture = TestBed.createComponent(GroupHostComponent);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    host: fixture.componentInstance,
    root,
    toggle: root.querySelector('button.nav-item__control') as HTMLButtonElement,
  };
}

function iconMarkup(name: IconName) {
  const fixture = TestBed.createComponent(IconComponent);
  fixture.componentRef.setInput('name', name);
  fixture.detectChanges();
  return (fixture.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
}

/** Foco con el teclado (Tab), como lo distingue `:focus-visible`. */
function focusWithKeyboard(element: HTMLElement) {
  document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
  element.focus();
}

/** Un `MouseEvent` a medida, porque `element.click()` no admite teclas modificadoras ni otro botón. */
function click(target: Element, init: MouseEventInit = {}) {
  const event = new MouseEvent('click', { bubbles: true, cancelable: true, button: 0, ...init });
  target.dispatchEvent(event);
  return event;
}

describe('NavItem', () => {
  describe('Item', () => {
    it('es un enlace con el icono de 20 px y el texto proyectado', () => {
      const { control } = render();
      expect(control.tagName).toBe('A');
      expect(control.getAttribute('href')).toBe('/registro');
      expect(control.querySelector('liv-icon svg')?.innerHTML).toBe(iconMarkup('car'));
      expect(control.querySelector('liv-icon svg')?.getAttribute('width')).toBe('20');
      expect(control.querySelector('.nav-item__label')?.textContent?.trim()).toBe(
        'Registro automotor',
      );
    });

    it('Default usa los tokens de Figma: icono text-muted y texto text-secondary', () => {
      const { control } = render();
      expect(getComputedStyle(control.querySelector('liv-icon') as HTMLElement).color).toContain(
        'var(--color-text-muted',
      );
      expect(
        getComputedStyle(control.querySelector('.nav-item__label') as HTMLElement).color,
      ).toContain('var(--color-text-secondary');
    });

    it('Active (001-15) marca la pantalla actual con aria-current="page" y usa los tokens de Figma', () => {
      const { control } = render({ active: true });
      expect(control.getAttribute('aria-current')).toBe('page');
      expect(getComputedStyle(control).backgroundColor).toContain(
        'var(--color-action-primary-subtle',
      );
      expect(getComputedStyle(control.querySelector('liv-icon') as HTMLElement).color).toContain(
        'var(--color-action-primary',
      );
      expect(
        getComputedStyle(control.querySelector('.nav-item__label') as HTMLElement).color,
      ).toContain('var(--color-action-primary');
    });

    it('sin Active no declara aria-current', () => {
      expect(render().control.hasAttribute('aria-current')).toBe(false);
    });

    it('Disabled (001-15) declara aria-disabled, no tiene href y explica por qué', () => {
      const { control } = render({ disabled: true });
      expect(control.getAttribute('aria-disabled')).toBe('true');
      expect(control.hasAttribute('href')).toBe(false);
      expect(control.getAttribute('role')).toBe('link');
      expect(control.getAttribute('title')).toBe('Disponible próximamente');
    });

    it('Disabled usa text-disabled en el icono y en el texto', () => {
      const { control } = render({ disabled: true });
      expect(getComputedStyle(control.querySelector('liv-icon') as HTMLElement).color).toContain(
        'var(--color-text-disabled',
      );
      expect(
        getComputedStyle(control.querySelector('.nav-item__label') as HTMLElement).color,
      ).toContain('var(--color-text-disabled');
    });

    it('Disabled no navega', () => {
      // No emite navigate, cancela el clic y este no llega a quien escucha el anfitrión.
      const { control, host } = render({ disabled: true });
      const event = click(control);
      expect(event.defaultPrevented).toBe(true);
      expect(host.navigations).toEqual([]);
      expect(host.clicks).toBe(0);
    });

    it('un clic normal emite navigate con el destino y cancela la navegación del navegador', () => {
      const { control, host } = render();
      const event = click(control);
      expect(host.navigations).toEqual(['/registro']);
      expect(event.defaultPrevented).toBe(true);
    });

    it.each<[string, MouseEventInit]>([
      ['Ctrl', { ctrlKey: true }],
      ['Cmd', { metaKey: true }],
      ['Mayús', { shiftKey: true }],
      ['Alt', { altKey: true }],
      ['botón central', { button: 1 }],
    ])('un clic con %s lo resuelve el navegador (nueva pestaña): no emite navigate', (_, init) => {
      const { control, host } = render();
      const event = click(control, init);
      expect(host.navigations).toEqual([]);
      expect(event.defaultPrevented).toBe(false);
    });

    it('pinta el anillo focus/ring de theme.css solo con el foco del teclado', () => {
      const { control } = render();
      const ring = cssVariable(theme, '--shadow-focus-ring');
      expect(paintsShadow(control, ring)).toBe(false);
      focusWithKeyboard(control);
      expect(paintsShadow(control, ring)).toBe(true);
      control.blur();
      expect(paintsShadow(control, ring)).toBe(false);
    });

    it('el foco sigue visible en alto contraste (forced-colors)', () => {
      const { control } = render();
      focusWithKeyboard(control);
      expect(declaredStyle(control, 'outline', 'forced-colors')).toContain('solid');
    });

    it('sin icono no dibuja ninguno', () => {
      expect(render({ icon: undefined }).control.querySelector('liv-icon')).toBeNull();
    });
  });

  describe('Subitem', () => {
    it('no dibuja icono aunque se le dé uno y se alinea con el texto', () => {
      const { control, item } = render({ type: 'subitem', icon: 'car' });
      expect(item.dataset['type']).toBe('subitem');
      expect(control.querySelector('liv-icon')).toBeNull();
      expect(declaredStyle(control, 'padding-left')).toContain('44px');
    });

    it('Active también marca aria-current="page"', () => {
      expect(render({ type: 'subitem', active: true }).control.getAttribute('aria-current')).toBe(
        'page',
      );
    });
  });

  describe('Group', () => {
    it('es un botón que se expande y se contrae, y anuncia su estado', () => {
      const { fixture, host, toggle } = renderGroup();
      expect(toggle.tagName).toBe('BUTTON');
      expect(toggle.getAttribute('type')).toBe('button');
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      expect(toggle.querySelector('.nav-item__chevron svg')?.innerHTML).toBe(
        iconMarkup('chevron-right'),
      );
      click(toggle);
      fixture.detectChanges();
      expect(host.expanded).toBe(true);
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      expect(toggle.querySelector('.nav-item__chevron svg')?.innerHTML).toBe(
        iconMarkup('chevron-down'),
      );
    });

    it('contraído oculta sus subopciones y expandido las muestra', () => {
      const { fixture, root, toggle } = renderGroup();
      expect(root.querySelector('.nav-item__children')).toBeNull();
      click(toggle);
      fixture.detectChanges();
      const children = root.querySelectorAll('.nav-item__children liv-nav-item');
      expect(Array.from(children).map((child) => child.textContent?.trim())).toEqual([
        'Vigencias',
        'Tablas base',
      ]);
      expect(root.querySelector('.nav-item__children')?.getAttribute('role')).toBe('group');
    });

    it('el texto del grupo es solo el texto propio, sin el de sus subopciones', () => {
      expect(renderGroup().toggle.querySelector('.nav-item__label')?.textContent?.trim()).toBe(
        'Administración',
      );
    });

    it('Disabled no se expande, no muestra el chevron y declara aria-disabled', () => {
      const fixture = TestBed.createComponent(ItemHostComponent);
      Object.assign(fixture.componentInstance, { type: 'group', disabled: true, href: undefined });
      fixture.detectChanges();
      const toggle = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
        '.nav-item__control',
      ) as HTMLElement;
      expect(toggle.getAttribute('aria-disabled')).toBe('true');
      expect(toggle.querySelector('.nav-item__chevron')).toBeNull();
      click(toggle);
      fixture.detectChanges();
      expect(fixture.componentInstance.expanded).toBe(false);
      expect(toggle.hasAttribute('aria-expanded')).toBe(false);
    });

    it('Active usa el fondo y el texto de la pantalla actual, pero sin aria-current', () => {
      const fixture = TestBed.createComponent(ItemHostComponent);
      Object.assign(fixture.componentInstance, { type: 'group', active: true, href: undefined });
      fixture.detectChanges();
      const toggle = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
        '.nav-item__control',
      ) as HTMLElement;
      expect(toggle.hasAttribute('aria-current')).toBe(false);
      expect(getComputedStyle(toggle).backgroundColor).toContain(
        'var(--color-action-primary-subtle',
      );
    });
  });

  it('cumple axe (001-19) en Item, Active, Disabled, Subitem y Group expandido', async () => {
    await expectNoAxeViolations(render().root);
    await expectNoAxeViolations(render({ active: true }).root);
    await expectNoAxeViolations(render({ disabled: true }).root);
    await expectNoAxeViolations(render({ type: 'subitem' }).root);
    const { fixture, root, toggle } = renderGroup();
    click(toggle);
    fixture.detectChanges();
    await expectNoAxeViolations(root);
  });
});
