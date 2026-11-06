import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow, usesToken } from '../../testing/css';
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

@Component({
  imports: [NavItemComponent],
  template: `
    <liv-nav-item type="group" icon="settings" [active]="true" [expanded]="true">
      Administración
      <liv-nav-item id="current" type="subitem" href="/vigencias" [active]="true"
        >Vigencias</liv-nav-item
      >
      <liv-nav-item id="other" type="subitem" href="/tablas">Tablas base</liv-nav-item>
      <liv-nav-item id="soon" type="subitem" [disabled]="true">Próximamente</liv-nav-item>
    </liv-nav-item>
  `,
})
class ActiveGroupHostComponent {}

@Component({
  imports: [NavItemComponent],
  template: `
    <liv-nav-item id="module" type="group" icon="settings" [active]="true" [expanded]="true">
      Administración
      <liv-nav-item id="plain" type="subitem" href="/vigencias">Vigencias fiscales</liv-nav-item>
      <liv-nav-item
        id="branch"
        type="subgroup"
        [active]="branchActive"
        [disabled]="branchDisabled"
        [(expanded)]="branchExpanded"
      >
        Cargue de tablas base por vigencia
        <liv-nav-item
          id="n1"
          type="nested"
          href="/opcion/1.5.1"
          (navigate)="navigations.push($event)"
          >Asistente de preparación de vigencia</liv-nav-item
        >
        <liv-nav-item id="n3" type="nested" href="/opcion/1.5.3" [active]="true"
          >Cargar archivo de avalúos</liv-nav-item
        >
        <liv-nav-item id="n6" type="nested" [disabled]="true">Calendario tributario</liv-nav-item>
      </liv-nav-item>
      <liv-nav-item id="after" type="subitem" href="/parametros">Parámetros generales</liv-nav-item>
    </liv-nav-item>
  `,
})
class ThreeLevelsHostComponent {
  branchExpanded = false;
  branchActive = false;
  branchDisabled = false;
  navigations: string[] = [];
}

function renderLevels(inputs: Partial<ThreeLevelsHostComponent> = {}) {
  const fixture = TestBed.createComponent(ThreeLevelsHostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const control = (id: string) => root.querySelector(`#${id} > .nav-item__control`) as HTMLElement;
  return { fixture, host: fixture.componentInstance, root, control, branch: control('branch') };
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
      expect(
        usesToken(getComputedStyle(control).backgroundColor, '--color-action-primary-subtle'),
      ).toBe(true);
      const icon = control.querySelector('liv-icon') as HTMLElement;
      expect(usesToken(getComputedStyle(icon).color, '--color-action-primary')).toBe(true);
      const label = control.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-action-primary')).toBe(true);
    });

    it('sin Active no declara aria-current', () => {
      expect(render().control.hasAttribute('aria-current')).toBe(false);
    });

    it('Active y Disabled a la vez no declara aria-current: no hay destino', () => {
      expect(render({ active: true, disabled: true }).control.hasAttribute('aria-current')).toBe(
        false,
      );
    });

    it.each(['', '   '])('un href vacío (%j) no dibuja href ni navega', (href) => {
      const { control, host } = render({ href });
      expect(control.hasAttribute('href')).toBe(false);
      click(control);
      expect(host.navigations).toEqual([]);
    });

    it('Angular sanea un href javascript: en el DOM (el valor emitido es responsabilidad de la aplicación)', () => {
      const { control, host } = render({ href: 'javascript:alert(1)' });
      expect(control.getAttribute('href') ?? '').not.toMatch(/^javascript:/i);
      click(control);
      expect(host.navigations).toEqual(['javascript:alert(1)']);
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

    describe('con el grupo activo y expandido', () => {
      function renderActiveGroup() {
        const fixture = TestBed.createComponent(ActiveGroupHostComponent);
        fixture.detectChanges();
        const root = fixture.nativeElement as HTMLElement;
        const control = (id: string) =>
          root.querySelector(`#${id} .nav-item__control`) as HTMLElement;
        return { control };
      }

      it('sus subopciones no heredan el fondo ni el color de Active del grupo', () => {
        const { control } = renderActiveGroup();
        for (const id of ['other', 'soon']) {
          const style = getComputedStyle(control(id));
          expect(usesToken(style.backgroundColor, '--color-action-primary-subtle')).toBe(false);
          expect(usesToken(style.color, '--color-action-primary')).toBe(false);
        }
      });

      it('una subopción inactiva conserva el texto de un Subitem (text-secondary)', () => {
        const { control } = renderActiveGroup();
        const label = control('other').querySelector('.nav-item__label') as HTMLElement;
        expect(usesToken(getComputedStyle(label).color, '--color-text-secondary')).toBe(true);
      });

      it('solo la subopción actual se pinta como Active', () => {
        const { control } = renderActiveGroup();
        expect(
          usesToken(
            getComputedStyle(control('current')).backgroundColor,
            '--color-action-primary-subtle',
          ),
        ).toBe(true);
      });

      it('el icono y el chevron del grupo activo se pintan con action-primary', () => {
        const fixture = TestBed.createComponent(ActiveGroupHostComponent);
        fixture.detectChanges();
        const group = (fixture.nativeElement as HTMLElement).querySelector(
          'liv-nav-item',
        ) as HTMLElement;
        for (const selector of ['.nav-item__icon', '.nav-item__chevron']) {
          const element = group.querySelector(`:scope > .nav-item__control ${selector}`);
          expect(
            usesToken(getComputedStyle(element as HTMLElement).color, '--color-action-primary'),
          ).toBe(true);
        }
      });

      it('una subopción deshabilitada conserva text-disabled', () => {
        const { control } = renderActiveGroup();
        const label = control('soon').querySelector('.nav-item__label') as HTMLElement;
        expect(usesToken(getComputedStyle(label).color, '--color-text-disabled')).toBe(true);
      });
    });

    it('el botón del grupo y su lista de subopciones se relacionan para los lectores de pantalla', () => {
      const { fixture, root, toggle } = renderGroup();
      click(toggle);
      fixture.detectChanges();
      const children = root.querySelector('.nav-item__children') as HTMLElement;
      expect(toggle.getAttribute('aria-controls')).toBe(children.id);
      expect(children.getAttribute('aria-labelledby')).toBe(toggle.id);
      expect(children.id).not.toBe('');
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

  // Tercer nivel del menú (1.5.x y 1.6.x), diseño aprobado el 2026-11-05: Figma NavItem Type=Subgroup y Type=Nested.
  describe('Subgroup (opción de segundo nivel con subopciones)', () => {
    it('es un botón que se expande y se contrae, y anuncia su estado', () => {
      const { fixture, host, branch } = renderLevels();
      expect(branch.tagName).toBe('BUTTON');
      expect(branch.getAttribute('type')).toBe('button');
      expect(branch.getAttribute('aria-expanded')).toBe('false');
      expect(branch.querySelector('.nav-item__chevron svg')?.innerHTML).toBe(
        iconMarkup('chevron-right'),
      );
      click(branch);
      fixture.detectChanges();
      expect(host.branchExpanded).toBe(true);
      expect(branch.getAttribute('aria-expanded')).toBe('true');
      expect(branch.querySelector('.nav-item__chevron svg')?.innerHTML).toBe(
        iconMarkup('chevron-down'),
      );
    });

    it('no dibuja icono y alinea el texto con el de las subopciones (44 px)', () => {
      const { root, branch } = renderLevels();
      expect(root.querySelector<HTMLElement>('#branch')?.dataset['type']).toBe('subgroup');
      expect(branch.querySelector('.nav-item__icon')).toBeNull();
      expect(declaredStyle(branch, 'padding-left')).toContain('44px');
    });

    it('contraído oculta sus opciones de tercer nivel y expandido las muestra', () => {
      const { fixture, root, branch } = renderLevels();
      expect(root.querySelector('#branch .nav-item__children')).toBeNull();
      click(branch);
      fixture.detectChanges();
      const nested = root.querySelectorAll('#branch .nav-item__children liv-nav-item');
      expect(Array.from(nested).map((item) => item.textContent?.trim())).toEqual([
        'Asistente de preparación de vigencia',
        'Cargar archivo de avalúos',
        'Calendario tributario',
      ]);
      expect(root.querySelector('#branch .nav-item__children')?.getAttribute('role')).toBe('group');
    });

    it('abrir o cerrar el subgrupo no cierra el módulo que lo contiene', () => {
      const { fixture, root, branch } = renderLevels();
      const module = root.querySelector('#module > .nav-item__control') as HTMLElement;
      click(branch);
      fixture.detectChanges();
      click(branch);
      fixture.detectChanges();
      expect(module.getAttribute('aria-expanded')).toBe('true');
      expect(root.querySelector('#plain')).not.toBeNull();
    });

    it('el texto del subgrupo es solo el propio, sin el de sus opciones', () => {
      const { branch } = renderLevels();
      expect(branch.querySelector('.nav-item__label')?.textContent?.trim()).toBe(
        'Cargue de tablas base por vigencia',
      );
    });

    it('el botón y su lista se relacionan para los lectores de pantalla, sin repetir ids con el módulo', () => {
      const { fixture, root, branch } = renderLevels({ branchExpanded: true });
      const module = root.querySelector('#module > .nav-item__control') as HTMLElement;
      const children = root.querySelector('#branch .nav-item__children') as HTMLElement;
      expect(branch.getAttribute('aria-controls')).toBe(children.id);
      expect(children.getAttribute('aria-labelledby')).toBe(branch.id);
      expect(branch.id).not.toBe(module.id);
      expect(new Set([...root.querySelectorAll('[id]')].map((e) => e.id)).size).toBe(
        root.querySelectorAll('[id]').length,
      );
      fixture.destroy();
    });

    it('no dibuja icono aunque se le dé uno', () => {
      const { control } = render({ type: 'subgroup', icon: 'car', href: undefined });
      expect(control.querySelector('.nav-item__icon')).toBeNull();
      expect(control.querySelector('liv-icon[class*="nav-item__icon"]')).toBeNull();
    });

    it('cerrado no declara aria-controls: no apunta a una lista que no está en el DOM', () => {
      const { branch, root } = renderLevels();
      expect(branch.hasAttribute('aria-controls')).toBe(false);
      expect(root.querySelector('#branch .nav-item__children')).toBeNull();
    });

    it('no hereda el fondo de Active del módulo que lo contiene', () => {
      const { branch, control } = renderLevels();
      for (const element of [branch, control('plain')]) {
        expect(
          usesToken(getComputedStyle(element).backgroundColor, '--color-action-primary-subtle'),
        ).toBe(false);
      }
    });

    it('Default usa el texto label/md; Active, body/md-strong', () => {
      const { branch } = renderLevels();
      expect(declaredStyle(branch, 'font-size')).toContain('--text-label-md');
      const active = renderLevels({ branchActive: true }).branch;
      expect(declaredStyle(active, 'font-size')).toContain('--text-body-md-strong');
    });

    it('Default usa text-secondary (no el text-primary de un módulo) y el chevron text-muted', () => {
      const { branch } = renderLevels();
      const label = branch.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-secondary')).toBe(true);
      expect(
        usesToken(
          getComputedStyle(branch.querySelector('.nav-item__chevron') as HTMLElement).color,
          '--color-text-muted',
        ),
      ).toBe(true);
    });

    it('Active (el grupo que contiene la pantalla actual) solo cambia el texto a action-primary, sin relleno ni aria-current', () => {
      const { branch } = renderLevels({ branchActive: true });
      expect(branch.hasAttribute('aria-current')).toBe(false);
      const style = getComputedStyle(branch);
      expect(usesToken(style.backgroundColor, '--color-action-primary-subtle')).toBe(false);
      expect(declaredStyle(branch, 'background-color')).toContain('transparent');
      const label = branch.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-action-primary')).toBe(true);
    });

    it('Active deja el chevron en text-muted, como en Figma', () => {
      const { branch } = renderLevels({ branchActive: true });
      const chevron = branch.querySelector('.nav-item__chevron') as HTMLElement;
      expect(usesToken(getComputedStyle(chevron).color, '--color-text-muted')).toBe(true);
    });

    it('Active no pinta a sus opciones de tercer nivel, pero la actual conserva su fondo', () => {
      const { control } = renderLevels({ branchActive: true, branchExpanded: true });
      for (const id of ['n1', 'n6']) {
        const style = getComputedStyle(control(id));
        expect(usesToken(style.backgroundColor, '--color-action-primary-subtle')).toBe(false);
        expect(usesToken(style.color, '--color-action-primary')).toBe(false);
      }
      // La regla de Active del subgrupo es solo para su propio control (`>`): no quita el fondo de la actual.
      expect(control('n3').getAttribute('aria-current')).toBe('page');
      expect(
        usesToken(getComputedStyle(control('n3')).backgroundColor, '--color-action-primary-subtle'),
      ).toBe(true);
      const label = control('n3').querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-action-primary')).toBe(true);
    });

    it('Active y Disabled a la vez: texto text-disabled, sin chevron ni aria-current', () => {
      const { branch } = renderLevels({ branchActive: true, branchDisabled: true });
      expect(branch.hasAttribute('aria-current')).toBe(false);
      expect(branch.querySelector('.nav-item__chevron')).toBeNull();
      const label = branch.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-disabled')).toBe(true);
    });

    it('Disabled no se expande, no muestra el chevron y declara aria-disabled', () => {
      const { fixture, host, branch } = renderLevels({ branchDisabled: true });
      expect(branch.getAttribute('aria-disabled')).toBe('true');
      expect(branch.hasAttribute('aria-expanded')).toBe(false);
      expect(branch.querySelector('.nav-item__chevron')).toBeNull();
      expect(branch.getAttribute('title')).toBe('Disponible próximamente');
      click(branch);
      fixture.detectChanges();
      expect(host.branchExpanded).toBe(false);
      const label = branch.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-disabled')).toBe(true);
    });

    it('pinta el anillo focus/ring solo con el foco del teclado', () => {
      const { branch } = renderLevels();
      const ring = cssVariable(theme, '--shadow-focus-ring');
      expect(paintsShadow(branch, ring)).toBe(false);
      focusWithKeyboard(branch);
      expect(paintsShadow(branch, ring)).toBe(true);
    });
  });

  describe('Nested (opción de tercer nivel)', () => {
    it('es un enlace sin icono, sangrado 16 px más que un Subitem (60 px)', () => {
      const { root, control } = renderLevels({ branchExpanded: true });
      expect(root.querySelector<HTMLElement>('#n1')?.dataset['type']).toBe('nested');
      expect(control('n1').tagName).toBe('A');
      expect(control('n1').getAttribute('href')).toBe('/opcion/1.5.1');
      expect(control('n1').querySelector('.nav-item__icon')).toBeNull();
      expect(declaredStyle(control('n1'), 'padding-left')).toContain('60px');
      expect(declaredStyle(control('plain'), 'padding-left')).toContain('44px');
    });

    it('no dibuja icono aunque se le dé uno', () => {
      const { control } = render({ type: 'nested', icon: 'car' });
      expect(control.querySelector('liv-icon')).toBeNull();
    });

    it('un clic normal emite navigate con el destino y cancela la navegación del navegador', () => {
      const { host, control } = renderLevels({ branchExpanded: true });
      const event = click(control('n1'));
      expect(host.navigations).toEqual(['/opcion/1.5.1']);
      expect(event.defaultPrevented).toBe(true);
    });

    it('Default usa text-secondary y Active marca aria-current="page" con el fondo y el texto de la pantalla actual', () => {
      const { control } = renderLevels({ branchExpanded: true });
      const label = control('n1').querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-secondary')).toBe(true);
      expect(control('n1').hasAttribute('aria-current')).toBe(false);
      expect(control('n3').getAttribute('aria-current')).toBe('page');
      expect(
        usesToken(getComputedStyle(control('n3')).backgroundColor, '--color-action-primary-subtle'),
      ).toBe(true);
      const current = control('n3').querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(current).color, '--color-action-primary')).toBe(true);
    });

    it('pinta el anillo focus/ring solo con el foco del teclado', () => {
      const { control } = renderLevels({ branchExpanded: true });
      const ring = cssVariable(theme, '--shadow-focus-ring');
      expect(paintsShadow(control('n1'), ring)).toBe(false);
      focusWithKeyboard(control('n1'));
      expect(paintsShadow(control('n1'), ring)).toBe(true);
    });

    it('Active y Disabled a la vez: texto text-disabled y sin aria-current (no hay destino)', () => {
      const { control } = render({ type: 'nested', active: true, disabled: true });
      expect(control.hasAttribute('aria-current')).toBe(false);
      expect(control.hasAttribute('href')).toBe(false);
      const label = control.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-disabled')).toBe(true);
    });

    it('Disabled declara aria-disabled, no tiene href, no navega y usa text-disabled', () => {
      const { host, control } = renderLevels({ branchExpanded: true });
      const disabled = control('n6');
      expect(disabled.getAttribute('aria-disabled')).toBe('true');
      expect(disabled.hasAttribute('href')).toBe(false);
      expect(disabled.getAttribute('title')).toBe('Disponible próximamente');
      expect(click(disabled).defaultPrevented).toBe(true);
      expect(host.navigations).toEqual([]);
      const label = disabled.querySelector('.nav-item__label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-disabled')).toBe(true);
    });
  });

  it('cumple axe (001-19) con los tres niveles: módulo, subgrupo (cerrado, abierto, activo, deshabilitado) y opciones', async () => {
    await expectNoAxeViolations(renderLevels().root);
    await expectNoAxeViolations(renderLevels({ branchExpanded: true }).root);
    await expectNoAxeViolations(renderLevels({ branchExpanded: true, branchActive: true }).root);
    await expectNoAxeViolations(renderLevels({ branchDisabled: true }).root);
    await expectNoAxeViolations(render({ type: 'nested' }).root);
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
