import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow } from '../../testing/css';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { DialogComponent } from './dialog.component';

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
    <liv-dialog [open]="true" heading="Primero" (closeRequested)="closes.push('primero')">
      <button id="first-action" type="button">Uno</button>
    </liv-dialog>
    <liv-dialog [open]="true" heading="Segundo" (closeRequested)="closes.push('segundo')">
      <button id="second-action" type="button">Dos</button>
    </liv-dialog>
  `,
})
class StackedHostComponent {
  closes: string[] = [];
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

  it('cumple axe (001-19), con y sin descripción', async () => {
    await expectNoAxeViolations(renderOpen().root);
    await expectNoAxeViolations(renderOpen({ description: undefined }).root);
  });
});
