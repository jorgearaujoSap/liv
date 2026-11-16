import { Component } from '@angular/core';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { contrastRatio, solidColor } from '../../testing/contrast';
import { cssVariable, declaredStyle, paintsShadow, usesToken } from '../../testing/css';
import { DialogComponent } from '../dialog/dialog.component';
import { IconComponent } from '../icon/icon.component';
import { SelectComponent, SelectOption } from './select.component';

const YEARS: SelectOption[] = [
  { value: '2027', label: '2027 · Año siguiente al año en curso' },
  { value: '2026', label: '2026 · Año en curso' },
  { value: '2025', label: '2025' },
  { value: '2024', label: '2024' },
];

@Component({
  imports: [SelectComponent],
  template: `
    <liv-select
      label="Año de la vigencia"
      [(value)]="value"
      [options]="options"
      [placeholder]="placeholder"
      [help]="help"
      [error]="error"
      [disabled]="disabled"
    />
  `,
})
class HostComponent {
  value: string | null = '2026';
  options: SelectOption[] = YEARS;
  placeholder: string | undefined = 'Elige un año';
  help: string | undefined;
  error: string | undefined;
  disabled = false;
}

/** El `Select` dentro de un formulario nativo, para probar su restablecimiento (`reset`). */
@Component({
  imports: [SelectComponent],
  template: `
    <form>
      <liv-select label="Año de la vigencia" [(value)]="value" [options]="options" />
    </form>
  `,
})
class FormHostComponent {
  value: string | null = '2026';
  options = YEARS;
}

/** El `Select` como cuerpo de un `Dialog` real, como lo usa la pantalla de crear una vigencia. */
@Component({
  imports: [DialogComponent, SelectComponent],
  template: `
    <button id="trigger" type="button">Abrir</button>
    <liv-dialog [open]="true" heading="Crear vigencia" tone="primary" size="lg">
      @if (withBefore) {
        <button livDialogBody id="before" type="button">Enlace previo</button>
      }
      <liv-select
        livDialogBody
        label="Año de la vigencia"
        [options]="options"
        [value]="value"
        [disabled]="disabled"
        [attr.data-initial-focus]="initialFocus ? '' : null"
      />
      <button id="cancel" type="button">Cancelar</button>
      <button id="create" type="button">Crear vigencia</button>
    </liv-dialog>
  `,
})
class DialogHostComponent {
  options = YEARS;
  value: string | null = null;
  disabled = false;
  initialFocus = true;
  /** Un control enfocable antes del `Select`, para distinguir la marca `data-initial-focus`. */
  withBefore = false;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-select') as HTMLElement;
  const select = host.querySelector('select') as HTMLSelectElement;
  const control = host.querySelector('.select__control') as HTMLElement;
  return { fixture, host, select, control, page: fixture.componentInstance };
}

function choose(
  fixture: ReturnType<typeof render>['fixture'],
  select: HTMLSelectElement,
  value: string,
) {
  select.value = value;
  select.dispatchEvent(new Event('change'));
  fixture.detectChanges();
}

/** Cambia el anfitrión después de dibujado: no usa señales, así que se marca para revisar. */
function update(fixture: ReturnType<typeof render>['fixture'], changes: Partial<HostComponent>) {
  Object.assign(fixture.componentInstance, changes);
  fixture.changeDetectorRef.markForCheck();
  fixture.detectChanges();
}

/** Las opciones de la lista, sin la vacía del placeholder. */
function realOptions(select: HTMLSelectElement): HTMLOptionElement[] {
  return Array.from(select.options).filter((option) => option.value !== '');
}

function shownText(select: HTMLSelectElement): string {
  return select.selectedOptions[0]?.textContent?.trim() ?? '';
}

function pressKey(target: EventTarget, key: string, init: KeyboardEventInit = {}) {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...init });
  target.dispatchEvent(event);
  return event;
}

/**
 * Cómo se llega a cada `State` de Figma: con qué entradas y si el `<select>` tiene el foco. `Open` es
 * el mismo foco: la lista que abre el navegador no se puede observar.
 */
const STATES: Record<string, { inputs: Partial<HostComponent>; focused: boolean }> = {
  Default: { inputs: {}, focused: false },
  Focus: { inputs: {}, focused: true },
  Open: { inputs: {}, focused: true },
  Disabled: { inputs: { disabled: true }, focused: false },
  Error: { inputs: { error: 'Elige un año.' }, focused: false },
};
const CONTENTS: Record<string, Partial<HostComponent>> = {
  Filled: { value: '2026' },
  Placeholder: { value: null },
};

describe('Select', () => {
  describe('el control nativo (007-22)', () => {
    it('envuelve un <select> con su <label> visible, enlazados por id (007-22)', () => {
      const { host, select } = render();
      const label = host.querySelector('label') as HTMLLabelElement;
      expect(label.textContent?.trim()).toBe('Año de la vigencia');
      expect(label.htmlFor).toBe(select.id);
      expect(select.id).not.toBe('');
      expect(select.tagName).toBe('SELECT');
      expect(select.multiple).toBe(false);
    });

    it('el nombre accesible sale de la etiqueta, sin aria-label', () => {
      const { select } = render();
      expect(select.labels?.[0]?.textContent?.trim()).toBe('Año de la vigencia');
      expect(select.hasAttribute('aria-label')).toBe(false);
    });

    it('no hay una lista propia con role="listbox": el navegador dibuja la lista', () => {
      const { host, select } = render();
      expect(host.querySelector('[role="listbox"]')).toBeNull();
      expect(select.hasAttribute('role')).toBe(false);
      expect(select.hasAttribute('aria-expanded')).toBe(false);
    });

    it('cada instancia tiene identificadores únicos, también para su mensaje', () => {
      const first = render({ help: 'Uno.' });
      const second = render({ help: 'Dos.' });
      expect(first.select.id).not.toBe(second.select.id);
      const describedBy = (rendered: ReturnType<typeof render>) =>
        rendered.select.getAttribute('aria-describedby');
      expect(describedBy(first)).not.toBe(describedBy(second));
      expect(first.host.querySelector(`#${describedBy(first)}`)?.textContent).toContain('Uno.');
    });
  });

  describe('las opciones (007-22)', () => {
    it('salen en el orden recibido, con su texto y su valor', () => {
      const { select } = render();
      const options = realOptions(select);
      expect(options.map((option) => option.value)).toEqual(['2027', '2026', '2025', '2024']);
      expect(options.map((option) => option.textContent?.trim())).toEqual(
        YEARS.map((year) => year.label),
      );
    });

    it('no reordena: el orden es el del servidor', () => {
      const { select } = render({
        options: [
          { value: 'b', label: 'B' },
          { value: 'a', label: 'A' },
        ],
        value: 'a',
      });
      expect(realOptions(select).map((option) => option.value)).toEqual(['b', 'a']);
    });

    it('la opción elegida sigue siendo la misma si las opciones se reemplazan', () => {
      const { fixture, select } = render({ value: '2025' });
      update(fixture, { options: [...YEARS].reverse() });
      expect(realOptions(select).map((option) => option.value)).toEqual([
        '2024',
        '2025',
        '2026',
        '2027',
      ]);
      expect(select.value).toBe('2025');
      expect(shownText(select)).toBe('2025');
    });
  });

  describe('las opciones que cambian después de dibujado (007-22)', () => {
    it('si desaparece la opción elegida, se muestra sin elegir', () => {
      const { fixture, host, select } = render({ value: '2026' });
      update(fixture, { options: YEARS.filter((year) => year.value !== '2026') });
      expect(select.value).toBe('');
      expect(shownText(select)).toBe('Elige un año');
      expect(host.dataset['content']).toBe('placeholder');
    });

    it('si las opciones llegan después del valor (una carga), muestra el valor', () => {
      const { fixture, host, select } = render({ value: '2025', options: [] });
      expect(shownText(select)).toBe('Elige un año');
      update(fixture, { options: YEARS });
      expect(select.value).toBe('2025');
      expect(host.dataset['content']).toBe('filled');
    });

    it('con dos opciones del mismo valor dibuja las dos (el valor debe ser único)', () => {
      const { select } = render({
        value: null,
        options: [
          { value: 'a', label: 'A uno' },
          { value: 'a', label: 'A dos' },
        ],
      });
      expect(realOptions(select).map((option) => option.textContent?.trim())).toEqual([
        'A uno',
        'A dos',
      ]);
    });
  });

  describe('dentro de un formulario nativo', () => {
    it('al restablecer el formulario, el control sigue mostrando el valor del modelo', () => {
      const fixture = TestBed.createComponent(FormHostComponent);
      fixture.detectChanges();
      const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
      const select = form.querySelector('select') as HTMLSelectElement;
      select.value = '2025';
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      expect(fixture.componentInstance.value).toBe('2025');
      form.reset();
      // Sin un `selected` por omisión el navegador elegiría la primera opción y el modelo no se enteraría.
      expect(select.value).toBe(fixture.componentInstance.value);
    });
  });

  describe('el valor (007-22)', () => {
    it('de entrada: muestra la opción elegida', () => {
      const { select } = render({ value: '2025' });
      expect(select.value).toBe('2025');
      expect(shownText(select)).toBe('2025');
    });

    it('de salida: elegir una opción emite el valor y lo enlaza con [(value)]', () => {
      const { fixture, select, page } = render({ value: null });
      choose(fixture, select, '2027');
      expect(page.value).toBe('2027');
      expect(select.value).toBe('2027');
    });

    it('cambiar el valor desde fuera cambia la opción mostrada', () => {
      const { fixture, select } = render({ value: '2026' });
      update(fixture, { value: '2024' });
      expect(select.value).toBe('2024');
    });

    it('un valor que no está en las opciones se muestra como sin elegir', () => {
      const { host, select } = render({ value: '1999' });
      expect(select.value).toBe('');
      expect(shownText(select)).toBe('Elige un año');
      expect(host.dataset['content']).toBe('placeholder');
    });
  });

  describe('la opción vacía (Content=Placeholder, 007-22)', () => {
    it('con null muestra el placeholder, que no se puede volver a elegir ni se lista', () => {
      const { select } = render({ value: null });
      const empty = select.options[0];
      expect(empty.value).toBe('');
      expect(empty.textContent?.trim()).toBe('Elige un año');
      expect(empty.disabled).toBe(true);
      expect(empty.hidden).toBe(true);
      expect(select.value).toBe('');
      expect(shownText(select)).toBe('Elige un año');
    });

    it('sigue siendo no elegible después de elegir un año', () => {
      const { fixture, select } = render({ value: null });
      choose(fixture, select, '2026');
      const empty = select.options[0];
      expect(empty.disabled).toBe(true);
      expect(empty.hidden).toBe(true);
      expect(select.value).toBe('2026');
    });

    it('sin placeholder la opción vacía no tiene texto', () => {
      const { select } = render({ value: null, placeholder: undefined });
      expect(select.options[0].value).toBe('');
      expect(select.options[0].textContent?.trim()).toBe('');
      expect(select.options[0].disabled).toBe(true);
    });

    it('marca el contenido como filled con un valor y como placeholder con null', () => {
      expect(render({ value: '2026' }).host.dataset['content']).toBe('filled');
      expect(render({ value: null }).host.dataset['content']).toBe('placeholder');
    });

    it('el valor elegido usa text/primary y el placeholder, text/muted', () => {
      const filled = render({ value: '2026' }).select;
      expect(usesToken(getComputedStyle(filled).color, '--color-text-primary')).toBe(true);
      const empty = render({ value: null }).select;
      expect(usesToken(getComputedStyle(empty).color, '--color-text-muted')).toBe(true);
    });

    it('las opciones de la lista usan text/primary aunque el control muestre el placeholder', () => {
      const { select } = render({ value: null });
      const year = select.querySelector('option[value="2026"]') as HTMLOptionElement;
      expect(usesToken(getComputedStyle(year).color, '--color-text-primary')).toBe(true);
    });
  });

  describe('Disabled', () => {
    it('desactiva el <select>, marca el estado y usa los estilos de Figma', () => {
      const { host, select, control } = render({ disabled: true });
      expect(select.disabled).toBe(true);
      expect(host.dataset['state']).toBe('disabled');
      expect(declaredStyle(control, 'border-color')).toContain('var(--color-border-default,');
      expect(usesToken(getComputedStyle(control).backgroundColor, '--color-bg-subtle')).toBe(true);
      expect(usesToken(getComputedStyle(select).color, '--color-text-disabled')).toBe(true);
      const label = host.querySelector('label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-disabled')).toBe(true);
      const chevron = host.querySelector('.select__icon') as HTMLElement;
      expect(usesToken(getComputedStyle(chevron).color, '--color-text-disabled')).toBe(true);
    });

    it('habilitado no está inhabilitado', () => {
      const { host, select } = render();
      expect(select.disabled).toBe(false);
      expect(host.dataset['state']).toBe('default');
    });

    it('el placeholder inhabilitado también usa text/disabled', () => {
      const { select } = render({ disabled: true, value: null });
      expect(usesToken(getComputedStyle(select).color, '--color-text-disabled')).toBe(true);
    });

    it('en alto contraste (forced-colors) se ve inhabilitado: borde, etiqueta y chevron GrayText', () => {
      const { host, control } = render({ disabled: true });
      expect(declaredStyle(control, 'border-color', 'forced-colors')).toBe('graytext');
      const label = host.querySelector('label') as HTMLElement;
      const chevron = host.querySelector('.select__icon') as HTMLElement;
      expect(declaredStyle(label, 'color', 'forced-colors')).toBe('graytext');
      expect(declaredStyle(chevron, 'color', 'forced-colors')).toBe('graytext');
      const active = render().control;
      expect(declaredStyle(active, 'border-color', 'forced-colors')).toBe('');
    });
  });

  describe('Help y Show help', () => {
    it('sin ayuda no declara aria-describedby ni dibuja la fila', () => {
      const { host, select } = render();
      expect(select.hasAttribute('aria-describedby')).toBe(false);
      expect(host.querySelector('.select__help')).toBeNull();
    });

    it('con ayuda la enlaza con aria-describedby, en text/secondary y sin icono', () => {
      const { host, select } = render({ help: 'Elige el año que vas a abrir.' });
      const help = host.querySelector(`#${select.getAttribute('aria-describedby')}`) as HTMLElement;
      expect(help.textContent?.trim()).toBe('Elige el año que vas a abrir.');
      expect(select.hasAttribute('aria-invalid')).toBe(false);
      expect(help.querySelector('liv-icon')).toBeNull();
      expect(usesToken(getComputedStyle(help).color, '--color-text-secondary')).toBe(true);
    });

    it('una ayuda vacía cuenta como ausente', () => {
      const { host, select } = render({ help: '   ' });
      expect(select.hasAttribute('aria-describedby')).toBe(false);
      expect(host.querySelector('.select__help')).toBeNull();
    });

    it('inhabilitado, la ayuda se muestra en text/secondary, como en Figma', () => {
      const { host } = render({ disabled: true, help: 'Ayuda.' });
      const help = host.querySelector('.select__help') as HTMLElement;
      expect(help.textContent?.trim()).toBe('Ayuda.');
      expect(usesToken(getComputedStyle(help).color, '--color-text-secondary')).toBe(true);
    });
  });

  describe('Error', () => {
    it('expone aria-invalid y el mensaje de error enlazado con aria-describedby', () => {
      const { host, select } = render({ help: 'Una ayuda.', error: 'Elige un año.' });
      expect(select.getAttribute('aria-invalid')).toBe('true');
      const message = host.querySelector(
        `#${select.getAttribute('aria-describedby')}`,
      ) as HTMLElement;
      expect(message.textContent?.trim()).toBe('Elige un año.');
      expect(host.dataset['state']).toBe('error');
    });

    it('muestra el icono circle-alert de 16 px, decorativo, junto al mensaje', () => {
      const { host } = render({ error: 'Elige un año.' });
      const icon = host.querySelector('.select__help liv-icon svg');
      expect(icon?.getAttribute('aria-hidden')).toBe('true');
      expect(icon?.getAttribute('width')).toBe('16');
    });

    it('el borde (1,5 px) y el mensaje usan status/danger', () => {
      const { host, control } = render({ error: 'Elige un año.' });
      expect(declaredStyle(control, 'border-color')).toContain('var(--color-status-danger,');
      const message = host.querySelector('.select__help') as HTMLElement;
      expect(usesToken(getComputedStyle(message).color, '--color-status-danger')).toBe(true);
    });

    it('un error vacío no cuenta como error', () => {
      const { host, select } = render({ error: '  ' });
      expect(select.hasAttribute('aria-invalid')).toBe(false);
      expect(host.dataset['state']).toBe('default');
    });

    it('inhabilitado prevalece sobre el error: no marca aria-invalid y muestra la ayuda', () => {
      const { host, select } = render({ disabled: true, error: 'Elige un año.', help: 'Ayuda.' });
      expect(host.dataset['state']).toBe('disabled');
      expect(select.hasAttribute('aria-invalid')).toBe(false);
      expect(host.querySelector('.select__help')?.textContent?.trim()).toBe('Ayuda.');
      expect(host.querySelector('.select__help liv-icon')).toBeNull();
    });

    it('en Error con foco conserva el borde status/danger y pinta el anillo de foco', () => {
      const { select, control } = render({ error: 'Elige un año.' });
      select.focus();
      // `declaredStyle` toma la última regla declarada: las dos reglas deben tener la misma especificidad
      // y la de Error ir después de la de foco, como en el CSS.
      expect(declaredStyle(control, 'border-color')).toContain('var(--color-status-danger,');
      expect(paintsShadow(control, cssVariable(theme, '--shadow-focus-ring'))).toBe(true);
    });
  });

  describe('Focus y Open', () => {
    it('sin foco el borde es border/strong y no hay anillo', () => {
      const { control } = render();
      expect(declaredStyle(control, 'border-color')).toContain('var(--color-border-strong,');
      expect(paintsShadow(control, cssVariable(theme, '--shadow-focus-ring'))).toBe(false);
    });

    // Open se pinta como Focus: CSS no sabe si la lista del sistema está abierta y el <select> conserva el foco.
    it.each(['Focus', 'Open'])(
      'State=%s: con el <select> enfocado, border/focus y el anillo focus/ring',
      () => {
        const { select, control } = render();
        const ring = cssVariable(theme, '--shadow-focus-ring');
        select.focus();
        expect(declaredStyle(control, 'border-color')).toContain('var(--color-border-focus,');
        expect(paintsShadow(control, ring)).toBe(true);
        select.blur();
        expect(paintsShadow(control, ring)).toBe(false);
      },
    );

    it('Open no dibuja una lista propia: el navegador la dibuja', () => {
      const { host, select } = render();
      select.focus();
      expect(host.querySelector('[role="listbox"], [aria-expanded]')).toBeNull();
    });

    it('el foco sigue visible en alto contraste (forced-colors)', () => {
      const { select, control } = render();
      select.focus();
      expect(declaredStyle(control, 'outline', 'forced-colors')).toContain('solid');
      select.blur();
      expect(declaredStyle(control, 'outline', 'forced-colors')).toBe('');
    });
  });

  describe('medidas y estilos', () => {
    it('el control mide 44 px de alto, con radio lg y borde de border/strong', () => {
      const { control } = render();
      expect(getComputedStyle(control).height).toContain('44px');
      expect(declaredStyle(control, 'border-radius')).toContain('var(--radius-lg');
      expect(declaredStyle(control, 'border-color')).toContain('var(--color-border-strong,');
      expect(declaredStyle(control, 'box-sizing')).toBe('border-box');
      expect(usesToken(getComputedStyle(control).backgroundColor, '--color-bg-surface')).toBe(true);
    });

    it('el <select> pierde su aspecto nativo, para dibujar el chevron propio', () => {
      const { select } = render();
      expect(declaredStyle(select, 'appearance')).toBe('none');
    });

    it('el chevron es el icono chevron-down de 20 px, decorativo y sin capturar el clic', () => {
      const { host, control } = render();
      const reference = TestBed.createComponent(IconComponent);
      reference.componentRef.setInput('name', 'chevron-down');
      reference.detectChanges();
      const expected = (reference.nativeElement as HTMLElement).querySelector('svg')?.innerHTML;
      const svg = control.querySelector('liv-icon svg');
      expect(svg?.innerHTML).toBe(expected);
      expect(svg?.getAttribute('width')).toBe('20');
      expect(svg?.getAttribute('aria-hidden')).toBe('true');
      const icon = host.querySelector('.select__icon') as HTMLElement;
      expect(declaredStyle(icon, 'pointer-events')).toBe('none');
      expect(usesToken(getComputedStyle(icon).color, '--color-text-secondary')).toBe(true);
    });

    it('la etiqueta usa label/md en text/primary', () => {
      const { host } = render();
      const label = host.querySelector('label') as HTMLElement;
      expect(usesToken(getComputedStyle(label).color, '--color-text-primary')).toBe(true);
    });

    it.each([
      ['el placeholder (text/muted)', 'text-muted', 'bg-surface', 4.5],
      ['el texto elegido (text/primary)', 'text-primary', 'bg-surface', 4.5],
      ['la ayuda (text/secondary)', 'text-secondary', 'bg-surface', 4.5],
      ['el error (status/danger)', 'status-danger', 'bg-surface', 4.5],
      ['el borde (border/strong)', 'border-strong', 'bg-surface', 3],
      ['el borde de foco (border/focus)', 'border-focus', 'bg-surface', 3],
    ])('contraste de %s sobre bg/surface', (_name, foreground, background, minimum) => {
      const color = (token: string) => solidColor(cssVariable(theme, `--color-${token}`));
      expect(contrastRatio(color(foreground), color(background))).toBeGreaterThanOrEqual(minimum);
    });

    it('no usa el atributo style ni utilidades en la plantilla', () => {
      const { host } = render({ help: 'Ayuda.' });
      expect(host.querySelector('[style]')).toBeNull();
      for (const element of Array.from(host.querySelectorAll('[class]'))) {
        for (const name of Array.from(element.classList)) {
          expect(name).toMatch(/^select__/);
        }
      }
    });
  });

  describe('dentro de un Dialog (007-22)', () => {
    function renderDialog(inputs: Partial<DialogHostComponent> = {}) {
      const fixture = TestBed.createComponent(DialogHostComponent);
      Object.assign(fixture.componentInstance, inputs);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;
      return {
        fixture,
        select: root.querySelector('select') as HTMLSelectElement,
        cancel: root.querySelector('#cancel') as HTMLButtonElement,
        create: root.querySelector('#create') as HTMLButtonElement,
        root,
      };
    }

    it('con data-initial-focus recibe el primer foco al abrir', () => {
      const { select } = renderDialog();
      expect(document.activeElement).toBe(select);
    });

    it('con un control antes del select, la marca data-initial-focus lleva el foco al select', () => {
      const { select } = renderDialog({ withBefore: true });
      expect(document.activeElement).toBe(select);
    });

    it('sin la marca, el primer foco es el del control que va antes del select', () => {
      const { root } = renderDialog({ withBefore: true, initialFocus: false });
      expect(document.activeElement).toBe(root.querySelector('#before'));
    });

    it('es una parada más de la trampa: Tab desde la última acción vuelve al select', () => {
      const { create, select } = renderDialog();
      create.focus();
      const event = pressKey(create, 'Tab');
      expect(event.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(select);
    });

    it('Mayús+Tab desde el select, la primera parada, salta a la última acción', () => {
      const { create, select } = renderDialog();
      select.focus();
      const event = pressKey(select, 'Tab', { shiftKey: true });
      expect(event.defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(create);
    });

    it('Tab desde el select no se intercepta: el navegador sigue a la acción siguiente', () => {
      const { select } = renderDialog();
      select.focus();
      expect(pressKey(select, 'Tab').defaultPrevented).toBe(false);
    });

    it('un select inhabilitado deja de ser una parada: la primera es Cancelar', () => {
      const { cancel, create } = renderDialog({ disabled: true });
      expect(document.activeElement).toBe(cancel);
      create.focus();
      expect(pressKey(create, 'Tab').defaultPrevented).toBe(true);
      expect(document.activeElement).toBe(cancel);
    });

    it('cumple axe en el diálogo', async () => {
      const { root } = renderDialog();
      await expectNoAxeViolations(root);
    });
  });

  describe('cumple axe (007-21) en cada combinación de State y Content', () => {
    for (const [state, { inputs, focused }] of Object.entries(STATES)) {
      for (const [content, value] of Object.entries(CONTENTS)) {
        it(`State=${state}, Content=${content}`, async () => {
          const { host, select } = render({ ...value, ...inputs });
          if (focused) {
            select.focus();
          }
          await expectNoAxeViolations(host);
        });
      }
    }

    it('con ayuda, también inhabilitado y en Error', async () => {
      await expectNoAxeViolations(render({ help: 'Ayuda.' }).host);
      await expectNoAxeViolations(render({ help: 'Ayuda.', disabled: true }).host);
      await expectNoAxeViolations(render({ help: 'Ayuda.', error: 'Elige un año.' }).host);
    });
  });
});
