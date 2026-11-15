import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow, usesToken } from '../../testing/css';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { RadioGroupComponent } from '../radio-group/radio-group.component';
import { RadioOptionComponent } from '../radio-group/radio-option.component';
import { DialogComponent, DialogSize, DialogTone } from './dialog.component';

@Component({
  imports: [DialogComponent],
  template: `
    <button id="trigger" type="button">Abrir</button>
    <liv-dialog
      [open]="open"
      [heading]="heading"
      [description]="description"
      [icon]="icon"
      [dismissible]="dismissible"
      (closeRequested)="closes = closes + 1"
    >
      <button id="secondary" type="button">Cerrar sesión</button>
      <button
        id="primary"
        type="button"
        [attr.data-initial-focus]="initialFocusOnPrimary ? '' : null"
      >
        Seguir conectado
      </button>
    </liv-dialog>
  `,
})
class HostComponent {
  open = true;
  heading = 'Tu sesión está por expirar';
  description: string | undefined = 'Por seguridad cerraremos tu sesión en 2 minutos.';
  icon: IconName | undefined;
  dismissible = true;
  initialFocusOnPrimary = false;
  closes = 0;
}

@Component({
  imports: [DialogComponent],
  template: `<liv-dialog [open]="true" heading="Aviso"></liv-dialog>`,
})
class WithoutActionsHostComponent {}

@Component({
  imports: [DialogComponent],
  template: `
    <liv-dialog [open]="true" heading="Aviso">
      <button id="only" type="button">Entendido</button>
    </liv-dialog>
  `,
})
class SingleActionHostComponent {}

@Component({
  imports: [DialogComponent],
  template: `
    <liv-dialog [open]="firstOpen" heading="Primero" (closeRequested)="closes.push('primero')">
      <button id="first-action" type="button">Uno</button>
    </liv-dialog>
    <liv-dialog
      [open]="secondOpen"
      [dismissible]="secondDismissible"
      heading="Segundo"
      (closeRequested)="closes.push('segundo')"
    >
      <button id="second-action" type="button">Dos</button>
    </liv-dialog>
  `,
})
class StackedHostComponent {
  firstOpen = true;
  secondOpen = true;
  secondDismissible = true;
  closes: string[] = [];
}

/** El uso por omisión de la 001 (aviso de sesión): sin cuerpo, sin `size` y sin `tone`. */
@Component({
  imports: [DialogComponent],
  template: `
    <liv-dialog [open]="true" heading="Tu sesión está por expirar" description="Dos minutos.">
      <button id="secondary" type="button">Cerrar sesión</button>
      <button id="primary" type="button">Seguir conectado</button>
    </liv-dialog>
  `,
})
class DefaultUseHostComponent {}

/** Un diálogo con cuerpo (`livDialogBody`), `size` y `tone`, y acciones sin selector. */
@Component({
  imports: [DialogComponent],
  template: `
    <liv-dialog [open]="true" heading="Crear vigencia" [size]="size" [tone]="tone">
      <p livDialogBody id="body-text">Texto del cuerpo</p>
      <button id="cancel" type="button">Cancelar</button>
      <button id="create" type="button">Crear</button>
    </liv-dialog>
  `,
})
class BodyHostComponent {
  size: DialogSize | undefined;
  tone: DialogTone | undefined;
}

/**
 * Un diálogo de creación con un `RadioGroup` en el cuerpo, como el de la 007: `years` opciones, de las
 * cuales `value` está marcada, y un segundo grupo opcional.
 */
@Component({
  imports: [DialogComponent, RadioGroupComponent, RadioOptionComponent],
  template: `
    <liv-dialog [open]="true" heading="Crear vigencia" tone="primary" size="lg">
      @if (withOtherGroup) {
        <liv-radio-group livDialogBody legend="Otro" name="other" [value]="otherValue">
          <liv-radio-option value="a" label="A" />
          <liv-radio-option value="b" label="B" />
        </liv-radio-group>
      }
      <liv-radio-group
        livDialogBody
        legend="Año"
        name="year"
        [value]="value"
        [disabled]="groupDisabled"
      >
        <liv-radio-option value="2026" label="2026" [disabled]="firstDisabled" />
        @if (years === 2) {
          <liv-radio-option value="2027" label="2027" />
        }
      </liv-radio-group>
      <button id="cancel" type="button">Cancelar</button>
      <button id="create" type="button">Crear</button>
    </liv-dialog>
  `,
})
class RadioHostComponent {
  years: 1 | 2 = 2;
  value: string | null = null;
  groupDisabled = false;
  firstDisabled = false;
  withOtherGroup = false;
  otherValue: string | null = null;
}

function renderRadios(inputs: Partial<RadioHostComponent> = {}) {
  const fixture = TestBed.createComponent(RadioHostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const radio = (year: string) => root.querySelector<HTMLInputElement>(`input[value="${year}"]`)!;
  return {
    root,
    radio,
    cancel: root.querySelector<HTMLButtonElement>('#cancel')!,
    create: root.querySelector<HTMLButtonElement>('#create')!,
  };
}

/**
 * Controles nativos: un `fieldset` inhabilitado al final (sus controles no tienen `disabled` propio, así
 * que solo el `fieldset` los excluye) con un botón en su primera `legend`, que sí cuenta.
 */
@Component({
  imports: [DialogComponent],
  template: `
    <liv-dialog [open]="true" heading="Fieldset">
      <button id="first" type="button">Primero</button>
      <fieldset disabled>
        <legend><button id="in-legend" type="button">En la leyenda</button></legend>
        <button id="blocked" type="button" data-initial-focus>Bloqueado</button>
        <input id="blocked-radio" type="radio" name="blocked" aria-label="Bloqueado" />
        <fieldset><button id="nested" type="button">Anidado</button></fieldset>
      </fieldset>
    </liv-dialog>
  `,
})
class FieldsetHostComponent {}

/** Radios del mismo `name` en dos formularios, y dos radios sin `name`. */
@Component({
  imports: [DialogComponent],
  template: `
    <liv-dialog [open]="true" heading="Formularios">
      <form livDialogBody>
        <input id="a1" type="radio" name="g" value="a" checked aria-label="A1" />
      </form>
      <form livDialogBody>
        <input id="b1" type="radio" name="g" value="b" aria-label="B1" />
      </form>
      <input id="n1" type="radio" checked aria-label="Sin nombre 1" />
      <input id="n2" type="radio" aria-label="Sin nombre 2" />
    </liv-dialog>
  `,
})
class FormsHostComponent {}

/** El grupo de radios es la última parada, después de un botón; `tabindex` negativo al final. */
@Component({
  imports: [DialogComponent, RadioGroupComponent, RadioOptionComponent],
  template: `
    <liv-dialog [open]="true" heading="Grupo al final">
      <button id="first" type="button">Primero</button>
      <liv-radio-group legend="Año" name="last-group" [value]="value">
        <liv-radio-option value="2026" label="2026" />
        <liv-radio-option value="2027" label="2027" />
      </liv-radio-group>
      <button id="skipped" type="button" tabindex="-1">Fuera del orden de Tab</button>
    </liv-dialog>
  `,
})
class GroupLastHostComponent {
  value: string | null = null;
}

/** La marca de foco inicial sobre el contenedor del grupo (no sobre uno de sus radios). */
@Component({
  imports: [DialogComponent, RadioGroupComponent, RadioOptionComponent],
  template: `
    <liv-dialog [open]="true" heading="Marca en el grupo">
      <button id="first" type="button">Primero</button>
      <liv-radio-group data-initial-focus legend="Año" name="marked-group" value="2027">
        <liv-radio-option value="2026" label="2026" />
        <liv-radio-option value="2027" label="2027" />
      </liv-radio-group>
    </liv-dialog>
  `,
})
class MarkedGroupHostComponent {}

function renderHost<T>(type: new () => T) {
  const fixture = TestBed.createComponent(type);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return {
    root,
    el: (selector: string) => root.querySelector<HTMLElement>(selector)!,
    panel: root.querySelector<HTMLElement>('[role="dialog"]')!,
  };
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    host: fixture.componentInstance,
    root,
    dialog: root.querySelector('liv-dialog') as HTMLElement,
    trigger: root.querySelector('#trigger') as HTMLButtonElement,
    primary: root.querySelector('#primary') as HTMLButtonElement,
    secondary: root.querySelector('#secondary') as HTMLButtonElement,
  };
}

/** Como `render`, para un diálogo abierto: el panel existe o la prueba falla aquí. */
function renderOpen(inputs: Partial<HostComponent> = {}) {
  const rendered = render(inputs);
  const panel = rendered.root.querySelector<HTMLElement>('[role="dialog"]');
  if (!panel) {
    throw new Error('El diálogo abierto no dibujó su panel');
  }
  return { ...rendered, panel };
}

/** Abre o cierra el diálogo después de dibujado: el anfitrión de prueba no usa señales, así que se marca. */
function setOpen(rendered: ReturnType<typeof render>, open: boolean) {
  rendered.host.open = open;
  rendered.fixture.changeDetectorRef.markForCheck();
  rendered.fixture.detectChanges();
}

/** Dos diálogos abiertos, el segundo encima; `set` abre o cierra uno después de dibujado. */
function renderStack(inputs: Partial<StackedHostComponent> = {}) {
  const fixture = TestBed.createComponent(StackedHostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  return {
    host: fixture.componentInstance,
    root: fixture.nativeElement as HTMLElement,
    set(changes: Partial<StackedHostComponent>) {
      Object.assign(fixture.componentInstance, changes);
      fixture.changeDetectorRef.markForCheck();
      fixture.detectChanges();
    },
  };
}

function pressKey(target: EventTarget, key: string, init: KeyboardEventInit = {}) {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
  target.dispatchEvent(event);
  return event;
}

describe('Dialog', () => {
  it('cerrado no dibuja nada', () => {
    const { root } = render({ open: false });
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(root.querySelector('.dialog__overlay')).toBeNull();
  });

  it('abierto es un diálogo modal con nombre y descripción accesibles', () => {
    const { panel } = renderOpen();
    expect(panel.getAttribute('aria-modal')).toBe('true');
    const name = document.getElementById(panel.getAttribute('aria-labelledby') ?? '');
    expect(name?.textContent?.trim()).toBe('Tu sesión está por expirar');
    const description = document.getElementById(panel.getAttribute('aria-describedby') ?? '');
    expect(description?.textContent?.trim()).toContain('cerraremos tu sesión en 2 minutos');
  });

  it('sin descripción no declara aria-describedby', () => {
    const { panel, root } = renderOpen({ description: undefined });
    expect(panel.hasAttribute('aria-describedby')).toBe(false);
    expect(root.querySelector('.dialog__description')).toBeNull();
  });

  it('dos diálogos no comparten identificadores', () => {
    const first = renderOpen().panel;
    const second = renderOpen().panel;
    expect(first.getAttribute('aria-labelledby')).not.toBe(second.getAttribute('aria-labelledby'));
  });

  it('muestra el icono de Figma (history) y proyecta las acciones', () => {
    const { root, dialog } = renderOpen();
    const reference = TestBed.createComponent(IconComponent);
    reference.componentRef.setInput('name', 'history');
    reference.detectChanges();
    const expected = (reference.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
    expect(dialog.querySelector('liv-icon svg')?.innerHTML).toBe(expected);
    expect(dialog.querySelector('liv-icon svg')?.getAttribute('width')).toBe('20');
    expect(root.querySelector('.dialog__actions #primary')).not.toBeNull();
    expect(root.querySelector('.dialog__actions #secondary')).not.toBeNull();
  });

  it('usa los tokens de Figma para fondo, panel, radio y sombra', () => {
    const { panel, root } = renderOpen();
    const overlay = root.querySelector('.dialog__overlay') as HTMLElement;
    expect(getComputedStyle(overlay).backgroundColor).toContain('var(--color-bg-overlay');
    expect(getComputedStyle(panel).backgroundColor).toContain('var(--color-bg-surface');
    expect(declaredStyle(panel, 'border-radius')).toContain('var(--radius-xl');
    expect(paintsShadow(panel, cssVariable(theme, '--shadow-elevation-xl'))).toBe(true);
  });

  describe('foco', () => {
    it('al abrir, va al primer enfocable', () => {
      const { secondary } = renderOpen();
      expect(document.activeElement).toBe(secondary);
    });

    it('al abrir, va al elemento marcado con data-initial-focus', () => {
      const { primary } = renderOpen({ initialFocusOnPrimary: true });
      expect(document.activeElement).toBe(primary);
    });

    it('al abrirse después, mueve el foco adentro', () => {
      const rendered = render({ open: false });
      rendered.trigger.focus();
      setOpen(rendered, true);
      // Cerrado, las acciones no están en el DOM: se buscan después de abrir.
      expect(document.activeElement).toBe(rendered.root.querySelector('#secondary'));
    });

    it('al cerrarse, devuelve el foco a quien lo tenía', () => {
      const rendered = render({ open: false });
      rendered.trigger.focus();
      setOpen(rendered, true);
      setOpen(rendered, false);
      expect(document.activeElement).toBe(rendered.trigger);
    });

    it('sin elementos enfocables, enfoca el propio diálogo', () => {
      const fixture = TestBed.createComponent(WithoutActionsHostComponent);
      fixture.detectChanges();
      expect(document.activeElement).toBe(fixture.nativeElement.querySelector('[role="dialog"]'));
    });

    it('Tab en el último enfocable salta al primero', () => {
      const { primary, secondary } = renderOpen();
      primary.focus();
      const event = pressKey(primary, 'Tab');
      expect(event.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(secondary);
    });

    it('Mayús+Tab en el primer enfocable salta al último', () => {
      const { primary, secondary } = renderOpen();
      secondary.focus();
      const event = pressKey(secondary, 'Tab', { shiftKey: true });
      expect(event.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(primary);
    });

    it('con un solo enfocable, Tab y Mayús+Tab no lo sueltan', () => {
      const fixture = TestBed.createComponent(SingleActionHostComponent);
      fixture.detectChanges();
      const only = fixture.nativeElement.querySelector('#only') as HTMLButtonElement;
      expect(document.activeElement).toBe(only);
      expect(pressKey(only, 'Tab').defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(only);
      expect(pressKey(only, 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(only);
    });

    it('sin enfocables, Tab se queda en el diálogo', () => {
      const fixture = TestBed.createComponent(WithoutActionsHostComponent);
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
      expect(pressKey(panel, 'Tab').defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(panel);
    });

    it('con dos diálogos abiertos, solo el último atrapa el foco y no se pelean', () => {
      const errors: unknown[] = [];
      const spy = vi.spyOn(console, 'error').mockImplementation((...args) => errors.push(args));
      const fixture = TestBed.createComponent(StackedHostComponent);
      try {
        fixture.detectChanges();
      } finally {
        spy.mockRestore();
      }
      expect(errors).toEqual([]);
      expect(document.activeElement).toBe(fixture.nativeElement.querySelector('#second-action'));
    });

    it('con dos diálogos abiertos, Escape cierra solo el de arriba', () => {
      const fixture = TestBed.createComponent(StackedHostComponent);
      fixture.detectChanges();
      pressKey(document, 'Escape');
      expect(fixture.componentInstance.closes).toEqual(['segundo']);
    });

    it('cerrado el de arriba, el de abajo vuelve a atender Escape', () => {
      const stack = renderStack();
      stack.set({ secondOpen: false });
      pressKey(document, 'Escape');
      expect(stack.host.closes).toEqual(['primero']);
    });

    it('el de arriba, si no se puede descartar, no deja pasar Escape al de abajo', () => {
      const stack = renderStack({ secondDismissible: false });
      pressKey(document, 'Escape');
      expect(stack.host.closes).toEqual([]);
    });

    it('cerrado el de abajo primero, el de arriba conserva el foco y Escape', () => {
      const stack = renderStack();
      stack.set({ firstOpen: false });
      expect(document.activeElement).toBe(stack.root.querySelector('#second-action'));
      pressKey(document, 'Escape');
      expect(stack.host.closes).toEqual(['segundo']);
    });

    it('Tab fuera de los extremos (del primero al último y al revés) no se intercepta', () => {
      const { primary, secondary } = renderOpen();
      secondary.focus();
      expect(pressKey(secondary, 'Tab').defaultPrevented).toBe(false);
      primary.focus();
      expect(pressKey(primary, 'Tab', { shiftKey: true }).defaultPrevented).toBe(false);
    });

    it('si el foco escapa fuera del diálogo, vuelve adentro', () => {
      const { trigger, secondary } = renderOpen();
      trigger.focus();
      trigger.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      expect(document.activeElement).toBe(secondary);
    });

    it('cerrado, no retiene el foco ni escucha el teclado', () => {
      const { trigger, host } = render({ open: false });
      trigger.focus();
      trigger.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      expect(document.activeElement).toBe(trigger);
      pressKey(document, 'Escape');
      expect(host.closes).toBe(0);
    });
  });

  describe('Escape', () => {
    it('emite closed cuando se puede descartar', () => {
      const { host } = renderOpen();
      const event = pressKey(document, 'Escape');
      expect(host.closes).toBe(1);
      expect(event.defaultPrevented).toBe(true);
    });

    it('con dismissible=false no cierra', () => {
      const { host } = renderOpen({ dismissible: false });
      const event = pressKey(document, 'Escape');
      expect(host.closes).toBe(0);
      expect(event.defaultPrevented).toBe(false);
    });

    it('un Escape que otro control ya atendió no cierra', () => {
      const { host } = renderOpen();
      pressKey(document, 'Escape', { cancelable: true });
      expect(host.closes).toBe(1);
      const consumed = new KeyboardEvent('keydown', {
        key: 'Escape',
        bubbles: true,
        cancelable: true,
      });
      consumed.preventDefault();
      document.dispatchEvent(consumed);
      expect(host.closes).toBe(1);
    });

    it('un Escape durante una composición de texto (IME) no cierra', () => {
      const { host } = renderOpen();
      pressKey(document, 'Escape', { isComposing: true });
      expect(host.closes).toBe(0);
    });

    it('cerrado después de abierto, ya no escucha el teclado ni el foco', () => {
      const rendered = render();
      setOpen(rendered, false);
      pressKey(document, 'Escape');
      rendered.trigger.focus();
      rendered.trigger.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
      expect(rendered.host.closes).toBe(0);
      expect(document.activeElement).toBe(rendered.trigger);
    });

    it('destruido abierto, ya no escucha el teclado', () => {
      const rendered = render();
      rendered.fixture.destroy();
      pressKey(document, 'Escape');
      expect(rendered.host.closes).toBe(0);
    });

    it('otra tecla no cierra', () => {
      const { host } = renderOpen();
      pressKey(document, 'Enter');
      expect(host.closes).toBe(0);
    });
  });

  it('el fondo se desplaza si el panel no cabe', () => {
    const { root } = renderOpen();
    const overlay = root.querySelector('.dialog__overlay') as HTMLElement;
    expect(declaredStyle(overlay, 'overflow-y')).toBe('auto');
  });

  it('el panel se centra con margen automático, para que el fondo pueda desplazarlo', () => {
    expect(declaredStyle(renderOpen().panel, 'margin')).toBe('auto');
  });

  it('cumple axe (001-19), con y sin descripción', async () => {
    await expectNoAxeViolations(renderOpen().root);
    await expectNoAxeViolations(renderOpen({ description: undefined }).root);
  });

  describe('uso por omisión (el aviso de sesión de la 001, H2‑11)', () => {
    function renderDefault() {
      const fixture = TestBed.createComponent(DefaultUseHostComponent);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;
      return {
        root,
        panel: root.querySelector<HTMLElement>('[role="dialog"]')!,
        tile: root.querySelector<HTMLElement>('.dialog__tile')!,
        actions: root.querySelector<HTMLElement>('.dialog__actions')!,
      };
    }

    it('mide 440 px', () => {
      expect(declaredStyle(renderDefault().panel, 'width')).toBe('440px');
    });

    it('usa el tono de advertencia en el mosaico del icono', () => {
      const { tile } = renderDefault();
      expect(
        usesToken(declaredStyle(tile, 'background-color'), '--color-status-warning-subtle'),
      ).toBe(true);
      expect(usesToken(declaredStyle(tile, 'color'), '--color-status-warning')).toBe(true);
    });

    it('lleva todo el contenido sin selector a las acciones y no deja un cuerpo visible', () => {
      const { root, actions } = renderDefault();
      expect(actions.querySelector('#secondary')).not.toBeNull();
      expect(actions.querySelector('#primary')).not.toBeNull();
      const body = root.querySelector<HTMLElement>('.dialog__body');
      expect(body).not.toBeNull();
      expect(body!.hasChildNodes()).toBe(false);
      expect(declaredStyle(body!, 'display')).toBe('none');
    });
  });

  describe('cuerpo (livDialogBody)', () => {
    function renderBody(inputs: Partial<BodyHostComponent> = {}) {
      const fixture = TestBed.createComponent(BodyHostComponent);
      Object.assign(fixture.componentInstance, inputs);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;
      return {
        root,
        panel: root.querySelector<HTMLElement>('[role="dialog"]')!,
        tile: root.querySelector<HTMLElement>('.dialog__tile')!,
        body: root.querySelector<HTMLElement>('.dialog__body')!,
        actions: root.querySelector<HTMLElement>('.dialog__actions')!,
      };
    }

    it('va entre el texto y las acciones, y lo demás sigue yendo a las acciones', () => {
      const { panel, body, actions, root } = renderBody();
      expect(body.querySelector('#body-text')?.textContent).toBe('Texto del cuerpo');
      expect(actions.querySelector('#body-text')).toBeNull();
      expect(actions.querySelector('#cancel')).not.toBeNull();
      expect(actions.querySelector('#create')).not.toBeNull();
      expect(body.querySelector('#cancel')).toBeNull();
      const order = Array.from(panel.children).map((child) => child.className);
      expect(order).toEqual(['dialog__content', 'dialog__body', 'dialog__actions']);
      expect(root.querySelectorAll('.dialog__body')).toHaveLength(1);
    });

    it('se sangra 56 px, alineado con el texto (mosaico de 40 px y separación de 16 px)', () => {
      const { body } = renderBody();
      expect(declaredStyle(body, 'padding-left')).toBe('56px');
      expect(declaredStyle(body, 'display')).not.toBe('none');
    });

    it('cuenta en el foco: Tab del último enfocable salta al primero', () => {
      const { root } = renderBody();
      const create = root.querySelector<HTMLElement>('#create')!;
      create.focus();
      expect(pressKey(create, 'Tab').defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(root.querySelector('#cancel'));
    });
  });

  describe('tamaño (size)', () => {
    function widthOf(size: DialogSize | undefined) {
      const fixture = TestBed.createComponent(BodyHostComponent);
      fixture.componentInstance.size = size;
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
      return declaredStyle(panel, 'width');
    }

    it('md mide 440 px y es el valor por omisión', () => {
      expect(widthOf('md')).toBe('440px');
      expect(widthOf(undefined)).toBe('440px');
    });

    it('lg mide 520 px', () => {
      expect(widthOf('lg')).toBe('520px');
    });

    it('un valor desconocido mide lo que md', () => {
      expect(widthOf('xl' as DialogSize)).toBe('440px');
    });

    it('en una pantalla estrecha el panel no se sale del fondo', () => {
      const fixture = TestBed.createComponent(BodyHostComponent);
      fixture.componentInstance.size = 'lg';
      fixture.detectChanges();
      const panel = fixture.nativeElement.querySelector('[role="dialog"]') as HTMLElement;
      expect(declaredStyle(panel, 'max-width')).toBe('100%');
    });
  });

  describe('tono (tone)', () => {
    function tileOf(tone: DialogTone | undefined) {
      const fixture = TestBed.createComponent(BodyHostComponent);
      fixture.componentInstance.tone = tone;
      fixture.detectChanges();
      return fixture.nativeElement.querySelector('.dialog__tile') as HTMLElement;
    }

    it('warning (por omisión) pinta el mosaico con status/warning-subtle y el icono con status/warning', () => {
      for (const tone of ['warning', undefined] as const) {
        const tile = tileOf(tone);
        expect(
          usesToken(declaredStyle(tile, 'background-color'), '--color-status-warning-subtle'),
        ).toBe(true);
        expect(usesToken(declaredStyle(tile, 'color'), '--color-status-warning')).toBe(true);
      }
    });

    it('primary pinta el mosaico con action/primary-subtle y el icono con action/primary', () => {
      const tile = tileOf('primary');
      expect(
        usesToken(declaredStyle(tile, 'background-color'), '--color-action-primary-subtle'),
      ).toBe(true);
      expect(usesToken(declaredStyle(tile, 'color'), '--color-action-primary')).toBe(true);
    });

    it('un valor desconocido usa el tono de advertencia', () => {
      const tile = tileOf('danger' as DialogTone);
      expect(
        usesToken(declaredStyle(tile, 'background-color'), '--color-status-warning-subtle'),
      ).toBe(true);
    });

    it('el icono sigue siendo history en los dos tonos y toma el color del mosaico', () => {
      const reference = TestBed.createComponent(IconComponent);
      reference.componentRef.setInput('name', 'history');
      reference.detectChanges();
      const expected = (reference.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
      for (const tone of ['warning', 'primary'] as const) {
        const tile = tileOf(tone);
        expect(tile.querySelector('liv-icon svg')?.innerHTML).toBe(expected);
      }
    });
  });

  describe('trampa de foco con un RadioGroup en el cuerpo (007-21)', () => {
    describe('dos años', () => {
      it('con uno marcado, el primer foco va al marcado, no al primero', () => {
        const { radio } = renderRadios({ value: '2027' });
        expect(document.activeElement).toBe(radio('2027'));
      });

      it('sin ninguno marcado, el primer foco va al primer radio', () => {
        const { radio } = renderRadios();
        expect(document.activeElement).toBe(radio('2026'));
      });

      it('con uno marcado, Tab desde Crear vuelve al marcado', () => {
        const { radio, create } = renderRadios({ value: '2027' });
        create.focus();
        expect(pressKey(create, 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(radio('2027'));
      });

      it('con uno marcado, Mayús+Tab desde el marcado va a Crear', () => {
        const { radio, create } = renderRadios({ value: '2027' });
        radio('2027').focus();
        expect(pressKey(radio('2027'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(create);
      });

      it('con uno marcado, Tab desde el marcado no se intercepta (el navegador sigue a Cancelar)', () => {
        const { radio } = renderRadios({ value: '2026' });
        radio('2026').focus();
        expect(pressKey(radio('2026'), 'Tab').defaultPrevented).toBe(false);
      });

      it('sin ninguno marcado, Tab desde Crear va al primero', () => {
        const { radio, create } = renderRadios();
        create.focus();
        expect(pressKey(create, 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(radio('2026'));
      });

      it('sin ninguno marcado, Mayús+Tab desde el primero (al que se entró con Tab) va a Crear', () => {
        const { radio, create } = renderRadios();
        radio('2026').focus();
        expect(pressKey(radio('2026'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(create);
      });

      it('sin ninguno marcado, Mayús+Tab desde Cancelar no se intercepta (entraría por el último)', () => {
        const { cancel } = renderRadios();
        cancel.focus();
        expect(pressKey(cancel, 'Tab', { shiftKey: true }).defaultPrevented).toBe(false);
      });

      it('sin ninguno marcado, Tab desde el primero no se intercepta (el grupo es una sola parada)', () => {
        const { radio } = renderRadios();
        radio('2026').focus();
        expect(pressKey(radio('2026'), 'Tab').defaultPrevented).toBe(false);
      });
    });

    describe('un año', () => {
      it('marcado: es la primera parada y el salto de los extremos pasa por él', () => {
        const { radio, create } = renderRadios({ years: 1, value: '2026' });
        expect(document.activeElement).toBe(radio('2026'));
        create.focus();
        pressKey(create, 'Tab');
        expect(document.activeElement).toBe(radio('2026'));
        expect(pressKey(radio('2026'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(create);
      });

      it('sin marcar: también es la primera parada', () => {
        const { radio, create } = renderRadios({ years: 1 });
        expect(document.activeElement).toBe(radio('2026'));
        create.focus();
        pressKey(create, 'Tab');
        expect(document.activeElement).toBe(radio('2026'));
        expect(pressKey(radio('2026'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(create);
      });
    });

    describe('radios inhabilitados', () => {
      it('un grupo inhabilitado (fieldset) no cuenta: el primer foco va a Cancelar y el salto salta de Crear a Cancelar', () => {
        const { cancel, create } = renderRadios({ groupDisabled: true, value: '2026' });
        expect(document.activeElement).toBe(cancel);
        create.focus();
        expect(pressKey(create, 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(cancel);
        expect(pressKey(cancel, 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(create);
      });

      it('sin marcar, la primera parada es el primer radio habilitado', () => {
        const { radio, create } = renderRadios({ firstDisabled: true });
        expect(document.activeElement).toBe(radio('2027'));
        create.focus();
        pressKey(create, 'Tab');
        expect(document.activeElement).toBe(radio('2027'));
      });

      it('con una sola opción inhabilitada, el grupo no cuenta', () => {
        const { cancel } = renderRadios({ years: 1, firstDisabled: true });
        expect(document.activeElement).toBe(cancel);
      });
    });

    it('cada grupo es una parada: con dos grupos, uno marcado y otro no, el salto entra por cada uno', () => {
      const { radio, root, create } = renderRadios({
        withOtherGroup: true,
        otherValue: 'b',
        value: null,
      });
      const other = (value: string) =>
        root.querySelector<HTMLInputElement>(`input[name="other"][value="${value}"]`)!;
      expect(document.activeElement).toBe(other('b'));
      create.focus();
      pressKey(create, 'Tab');
      expect(document.activeElement).toBe(other('b'));
      // Sin marcar, el grupo de años se entra por el primero al avanzar y por el último al retroceder.
      expect(pressKey(other('b'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(create);
      radio('2026').focus();
      expect(pressKey(radio('2026'), 'Tab').defaultPrevented).toBe(false);
    });

    it('con el marcado inhabilitado, la parada es el primer radio habilitado (como Chromium)', () => {
      const { radio, cancel, create } = renderRadios({ value: '2026', firstDisabled: true });
      expect(document.activeElement).toBe(radio('2027'));
      cancel.focus();
      expect(pressKey(cancel, 'Tab', { shiftKey: true }).defaultPrevented).toBe(false);
      create.focus();
      pressKey(create, 'Tab');
      expect(document.activeElement).toBe(radio('2027'));
    });

    describe('grupo como última parada', () => {
      function renderLast(value: string | null = null) {
        const fixture = TestBed.createComponent(GroupLastHostComponent);
        fixture.componentInstance.value = value;
        fixture.detectChanges();
        const root = fixture.nativeElement as HTMLElement;
        return {
          first: root.querySelector<HTMLElement>('#first')!,
          radio: (year: string) => root.querySelector<HTMLInputElement>(`input[value="${year}"]`)!,
        };
      }

      it('sin marcar, Mayús+Tab desde el primero entra por el último radio', () => {
        const { first, radio } = renderLast();
        first.focus();
        expect(pressKey(first, 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(radio('2027'));
      });

      it('sin marcar, Tab desde un radio del grupo final (también el último) da la vuelta; un botón con tabindex -1 no es parada', () => {
        const { first, radio } = renderLast();
        radio('2026').focus();
        expect(pressKey(radio('2026'), 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(first);
        radio('2027').focus();
        expect(pressKey(radio('2027'), 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(first);
      });

      it('con uno marcado, Mayús+Tab desde el primero entra por el marcado', () => {
        const { first, radio } = renderLast('2026');
        first.focus();
        pressKey(first, 'Tab', { shiftKey: true });
        expect(document.activeElement).toBe(radio('2026'));
      });

      it('Tab desde el primero no se intercepta', () => {
        const { first } = renderLast();
        first.focus();
        expect(pressKey(first, 'Tab').defaultPrevented).toBe(false);
      });
    });

    it('la marca data-initial-focus sobre el grupo lleva el foco a su parada, el radio marcado', () => {
      const { el } = renderHost(MarkedGroupHostComponent);
      expect(document.activeElement).toBe(el('input[value="2027"]'));
    });

    it('con el foco en el propio panel, Mayús+Tab da la vuelta al último; Tab no se intercepta', () => {
      const { radio, create } = renderRadios({ value: '2026' });
      const panel = radio('2026').closest<HTMLElement>('[role="dialog"]')!;
      panel.focus();
      expect(pressKey(panel, 'Tab').defaultPrevented).toBe(false);
      expect(pressKey(panel, 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(create);
    });

    describe('controles nativos', () => {
      it('un fieldset inhabilitado excluye a sus controles, menos a los de su primera legend', () => {
        const { el } = renderHost(FieldsetHostComponent);
        // La marca de foco inicial está en un botón inhabilitado por su fieldset: se ignora.
        expect(document.activeElement).toBe(el('#first'));
        el('#first').focus();
        expect(pressKey(el('#first'), 'Tab').defaultPrevented).toBe(false);
        // La última parada es el botón de la leyenda, no los controles del fieldset.
        el('#in-legend').focus();
        expect(pressKey(el('#in-legend'), 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(el('#first'));
        expect(pressKey(el('#first'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(el('#in-legend'));
      });

      it('los radios del mismo name en formularios distintos son grupos distintos, y sin name no hay grupo', () => {
        const { el } = renderHost(FormsHostComponent);
        // a1 está marcado y b1 no: son dos grupos, así que b1 no es de la parada de a1 y,
        // hacia atrás, no es la primera parada.
        el('#b1').focus();
        expect(pressKey(el('#b1'), 'Tab', { shiftKey: true }).defaultPrevented).toBe(false);
        // Sin name, cada radio es una parada: n1 no es la última, n2 sí.
        el('#n1').focus();
        expect(pressKey(el('#n1'), 'Tab').defaultPrevented).toBe(false);
        el('#n2').focus();
        expect(pressKey(el('#n2'), 'Tab').defaultPrevented).toBe(true);
        expect(document.activeElement).toBe(el('#a1'));
      });
    });

    it('cumple axe con el grupo, marcado y sin marcar (007-21)', async () => {
      await expectNoAxeViolations(renderRadios({ value: '2026' }).root);
      await expectNoAxeViolations(renderRadios().root);
      await expectNoAxeViolations(renderRadios({ years: 1 }).root);
      await expectNoAxeViolations(renderRadios({ groupDisabled: true }).root);
    });
  });

  it('cumple axe en cada tono y tamaño, con cuerpo (007-21)', async () => {
    for (const size of ['md', 'lg'] as const) {
      for (const tone of ['warning', 'primary'] as const) {
        const fixture = TestBed.createComponent(BodyHostComponent);
        fixture.componentInstance.size = size;
        fixture.componentInstance.tone = tone;
        fixture.detectChanges();
        await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
      }
    }
  });

  it('el uso por omisión cumple axe (007-21)', async () => {
    const fixture = TestBed.createComponent(DefaultUseHostComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });
});
