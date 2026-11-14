import { Component, signal } from '@angular/core';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { contrastRatio, solidColor } from '../../testing/contrast';
import { cssVariable, declaredStyle, paintsShadow, usesToken } from '../../testing/css';
import { IconName } from '../icon/icons';
import {
  ButtonComponent,
  ButtonSize,
  ButtonType,
  ButtonVariant,
  ButtonReasonAlign,
} from './button.component';

const REASON = 'Faltan 6 puntos obligatorios de la lista de preparación.';

/** Selectores (uno por elemento de cada lista) de las reglas de la hoja que contienen `fragment`. */
function rulesWith(fragment: string): string[] {
  const found: string[] = [];
  const visit = (rules: CSSRuleList) => {
    for (const rule of Array.from(rules)) {
      if (rule instanceof CSSStyleRule) {
        const selectors = rule.selectorText
          .replace(/["']/g, '')
          .split(/,(?![^(]*\))/)
          .map((item) => item.trim());
        found.push(...selectors.filter((selector) => selector.includes(fragment)));
      } else if ('cssRules' in rule) {
        visit((rule as CSSGroupingRule).cssRules);
      }
    }
  };
  for (const sheet of Array.from(document.styleSheets)) {
    visit(sheet.cssRules);
  }
  return found;
}

@Component({
  imports: [ButtonComponent],
  template: `
    <form (submit)="onSubmit($event)">
      <liv-button
        [variant]="variant"
        [size]="size"
        [type]="type"
        [disabled]="disabled"
        [loading]="loading"
        [disabledReason]="disabledReason"
        [reasonAlign]="reasonAlign"
        [leadingIcon]="leadingIcon"
        [trailingIcon]="trailingIcon"
        (click)="clicks = clicks + 1"
        >{{ label }}</liv-button
      >
    </form>
  `,
})
class HostComponent {
  variant: ButtonVariant = 'primary';
  size: ButtonSize = 'md';
  type: ButtonType = 'button';
  disabled = false;
  loading = false;
  disabledReason?: string;
  reasonAlign: ButtonReasonAlign = 'start';
  leadingIcon?: IconName;
  trailingIcon?: IconName;
  label = 'Ingresar';
  clicks = 0;
  submits = 0;

  onSubmit(event: Event) {
    event.preventDefault();
    this.submits++;
  }
}

/** Anfitrión con señales, como lo usaría una pantalla. El texto visible no cambia (Figma, Loading). */
@Component({
  imports: [ButtonComponent],
  template: `<liv-button [loading]="loading()" loadingLabel="Ingresando…">Ingresar</liv-button>`,
})
class LiveHostComponent {
  readonly loading = signal(false);
}

/** Anfitrión con señales para cambiar `disabled` y el motivo después de dibujar. */
@Component({
  imports: [ButtonComponent],
  template: `<liv-button
    [disabled]="disabled()"
    [disabledReason]="reason()"
    (click)="clicks = clicks + 1"
    >Habilitar</liv-button
  >`,
})
class ReasonHostComponent {
  readonly disabled = signal(false);
  readonly reason = signal<string | undefined>(undefined);
  clicks = 0;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-button') as HTMLElement;
  const button = host.querySelector('button') as HTMLButtonElement;
  return { fixture, host, button, page: fixture.componentInstance };
}

describe('Button', () => {
  it('es un botón nativo de tipo button por defecto, con el texto proyectado', () => {
    const { button } = render();
    expect(button.getAttribute('type')).toBe('button');
    expect(button.textContent?.trim()).toBe('Ingresar');
  });

  it('usa Primary y Md por defecto', () => {
    const fixture = TestBed.createComponent(ButtonComponent);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.dataset['variant']).toBe('primary');
    expect(host.dataset['size']).toBe('md');
  });

  it.each<ButtonVariant>(['primary', 'secondary', 'ghost'])('admite la variante %s', (variant) => {
    expect(render({ variant }).host.dataset['variant']).toBe(variant);
  });

  it('emite clic y envía el formulario si es de tipo submit', () => {
    const { fixture, button, page } = render({ type: 'submit' });
    button.click();
    fixture.detectChanges();
    expect(page.clicks).toBe(1);
    expect(page.submits).toBe(1);
  });

  describe('Loading', () => {
    it('no emite clic ni envía el formulario', () => {
      const { fixture, button, page } = render({ type: 'submit', loading: true });
      button.click();
      fixture.detectChanges();
      expect(page.clicks).toBe(0);
      expect(page.submits).toBe(0);
    });

    it('sigue siendo enfocable: usa aria-disabled en lugar de disabled', () => {
      const { button } = render({ loading: true });
      expect(button.disabled).toBe(false);
      expect(button.getAttribute('aria-disabled')).toBe('true');
    });

    it('muestra el spinner en el lugar del icono inicial, oculto para los lectores de pantalla', () => {
      const { button } = render({ loading: true, leadingIcon: 'log-out' });
      const spinner = button.querySelector('liv-spinner');
      expect(spinner).not.toBeNull();
      expect(spinner?.closest('[aria-hidden="true"]')).not.toBeNull();
      expect(button.querySelector('.button__leading-icon')).toBeNull();
    });

    it('el nombre accesible del botón es solo su texto', () => {
      const { button } = render({ loading: true, label: 'Ingresando…' });
      const exposed = button.cloneNode(true) as HTMLElement;
      exposed.querySelectorAll('[aria-hidden="true"]').forEach((node) => node.remove());
      expect(exposed.textContent?.trim()).toBe('Ingresando…');
    });

    it('usa el spinner Inverse en Primary y Default en las demás variantes', () => {
      const tone = (variant: ButtonVariant) =>
        render({ variant, loading: true })
          .button.querySelector('liv-spinner')
          ?.getAttribute('data-tone');
      expect(tone('primary')).toBe('inverse');
      expect(tone('secondary')).toBe('default');
      expect(tone('ghost')).toBe('default');
    });

    it('anuncia loadingLabel en una región viva que existe desde el inicio', async () => {
      const fixture = TestBed.createComponent(LiveHostComponent);
      await fixture.whenStable();
      const host = fixture.nativeElement.querySelector('liv-button') as HTMLElement;
      const status = host.querySelector(':scope > [role="status"]') as HTMLElement;
      expect(status).not.toBeNull();
      expect(status.closest('button')).toBeNull();
      expect(status.textContent?.trim()).toBe('');

      fixture.componentInstance.loading.set(true);
      await fixture.whenStable();
      expect(host.querySelector(':scope > [role="status"]')).toBe(status);
      expect(status.textContent?.trim()).toBe('Ingresando…');
      expect(host.querySelector('.button__label')?.textContent?.trim()).toBe('Ingresar');

      fixture.componentInstance.loading.set(false);
      await fixture.whenStable();
      expect(status.textContent?.trim()).toBe('');
    });

    it('sin loadingLabel anuncia "Cargando"', () => {
      const { host } = render({ loading: true });
      expect(host.querySelector(':scope > [role="status"]')?.textContent?.trim()).toBe('Cargando');
    });

    it.each<[ButtonVariant, string]>([
      ['primary', '--color-action-primary-hover'],
      ['secondary', '--color-bg-subtle'],
      ['ghost', '--color-action-primary-subtle'],
    ])('en %s usa el fondo de Figma (%s)', (variant, background) => {
      const { button } = render({ variant, loading: true });
      expect(getComputedStyle(button).backgroundColor).toContain(`var(${background}`);
    });
  });

  it.each<[ButtonVariant, string]>([
    ['primary', '--color-bg-subtle'],
    ['secondary', '--color-bg-surface'],
  ])('deshabilitado en %s usa el fondo %s y el texto text/disabled', (variant, background) => {
    const style = getComputedStyle(render({ variant, disabled: true }).button);
    expect(style.backgroundColor).toContain(`var(${background}`);
    expect(style.color).toContain('var(--color-text-disabled');
  });

  it('Disabled prevalece sobre Loading: sin spinner, sin anuncio y deshabilitado', () => {
    const { host, button } = render({ disabled: true, loading: true });
    expect(button.disabled).toBe(true);
    expect(button.hasAttribute('aria-disabled')).toBe(false);
    expect(button.querySelector('liv-spinner')).toBeNull();
    expect(host.querySelector(':scope > [role="status"]')?.textContent?.trim()).toBe('');
  });

  it('no emite clic deshabilitado ni en Loading, aunque el evento se despache por código', () => {
    const disabled = render({ disabled: true });
    disabled.button.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(disabled.page.clicks).toBe(0);

    const loading = render({ loading: true });
    loading.host.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    loading.host
      .querySelector(':scope > [role="status"]')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(loading.page.clicks).toBe(0);
  });

  it('deshabilitado no emite clic', () => {
    const { fixture, button, page } = render({ disabled: true });
    expect(button.disabled).toBe(true);
    button.click();
    fixture.detectChanges();
    expect(page.clicks).toBe(0);
  });

  describe('Disabled con motivo (007-21, H2-15)', () => {
    const reasonOf = (host: HTMLElement) =>
      host.querySelector<HTMLElement>('.button__reason') as HTMLElement;

    it('muestra el motivo como texto visible debajo del botón, enlazado con aria-describedby', () => {
      const { host, button } = render({ disabled: true, disabledReason: REASON });
      const reason = reasonOf(host);
      expect(reason.textContent?.trim()).toBe(REASON);
      expect(reason.id).not.toBe('');
      expect(button.getAttribute('aria-describedby')).toBe(reason.id);
      expect(button.contains(reason)).toBe(false);
      expect(reason.hidden).toBe(false);
      expect(reason.getAttribute('aria-hidden')).toBeNull();
    });

    it('cada botón tiene su propio identificador del motivo', () => {
      const first = render({ disabled: true, disabledReason: REASON });
      const second = render({ disabled: true, disabledReason: REASON });
      expect(reasonOf(first.host).id).not.toBe(reasonOf(second.host).id);
    });

    it('usa aria-disabled="true" en lugar de disabled, así que sigue recibiendo el foco', () => {
      const { button } = render({ disabled: true, disabledReason: REASON });
      expect(button.disabled).toBe(false);
      expect(button.getAttribute('aria-disabled')).toBe('true');
      button.focus();
      expect(document.activeElement).toBe(button);
    });

    it('pone su estado propio en el anfitrión, con y sin motivo', () => {
      expect(render({ disabled: true, disabledReason: REASON }).host.dataset['state']).toBe(
        'disabled',
      );
      expect(render({ disabled: true }).host.dataset['state']).toBe('disabled');
      expect(render().host.dataset['state']).toBeUndefined();
    });

    it('sin disabled no muestra el motivo ni cambia el botón', () => {
      const { host, button } = render({ disabledReason: REASON });
      expect(host.querySelector('.button__reason')).toBeNull();
      expect(button.hasAttribute('aria-describedby')).toBe(false);
      expect(button.hasAttribute('aria-disabled')).toBe(false);
      expect(button.disabled).toBe(false);
    });

    it.each([undefined, '', '   '])(
      'con el motivo %j no dibuja el texto y usa disabled',
      (reason) => {
        const { host, button } = render({ disabled: true, disabledReason: reason });
        expect(host.querySelector('.button__reason')).toBeNull();
        expect(button.hasAttribute('aria-describedby')).toBe(false);
        expect(button.disabled).toBe(true);
        expect(button.hasAttribute('aria-disabled')).toBe(false);
      },
    );

    it('al quitar el motivo vuelve a disabled nativo, y al quitar disabled el botón queda activo', async () => {
      const fixture = TestBed.createComponent(ReasonHostComponent);
      fixture.componentInstance.disabled.set(true);
      fixture.componentInstance.reason.set(REASON);
      await fixture.whenStable();
      const host = fixture.nativeElement.querySelector('liv-button') as HTMLElement;
      const button = host.querySelector('button') as HTMLButtonElement;
      expect(button.disabled).toBe(false);
      expect(button.getAttribute('aria-disabled')).toBe('true');

      fixture.componentInstance.reason.set(undefined);
      await fixture.whenStable();
      expect(button.disabled).toBe(true);
      expect(button.hasAttribute('aria-disabled')).toBe(false);
      expect(host.querySelector('.button__reason')).toBeNull();

      fixture.componentInstance.reason.set(REASON);
      fixture.componentInstance.disabled.set(false);
      await fixture.whenStable();
      expect(button.disabled).toBe(false);
      expect(button.hasAttribute('aria-disabled')).toBe(false);
      expect(button.hasAttribute('aria-describedby')).toBe(false);
      expect(host.querySelector('.button__reason')).toBeNull();
      button.click();
      expect(fixture.componentInstance.clicks).toBe(1);
    });

    it('no emite clic ni envía el formulario por ningún clic, ni el sintético ni el de un envío implícito', () => {
      // En un navegador, Enter y Espacio sobre el botón enfocado, y Enter en un campo del formulario
      // (envío implícito), se reducen a un `click` sobre el botón: es lo que se prueba aquí. jsdom no
      // convierte el teclado en clic y un KeyboardEvent sintético no activa un botón: el teclado real se
      // prueba en e2e/ (T35) y en la revisión manual de Storybook.
      const { fixture, host, button, page } = render({
        type: 'submit',
        disabled: true,
        disabledReason: REASON,
      });
      button.click();
      button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      host.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      reasonOf(host).dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      fixture.detectChanges();
      expect(page.clicks).toBe(0);
      expect(page.submits).toBe(0);
    });

    it('cancela el clic y no deja el formulario enviarse por defecto', () => {
      const { button } = render({ type: 'submit', disabled: true, disabledReason: REASON });
      const click = new MouseEvent('click', { bubbles: true, cancelable: true });
      button.dispatchEvent(click);
      expect(click.defaultPrevented).toBe(true);
    });

    it('con motivo, el aspecto es el mismo que sin él', () => {
      for (const variant of ['primary', 'secondary', 'ghost'] as ButtonVariant[]) {
        const plain = render({ variant, disabled: true }).button;
        const reasoned = render({ variant, disabled: true, disabledReason: REASON }).button;
        const props = ['background-color', 'color', 'border-color', 'cursor', 'box-shadow'];
        for (const property of props) {
          expect(declaredStyle(reasoned, property)).toBe(declaredStyle(plain, property));
        }
        const a = getComputedStyle(plain);
        const b = getComputedStyle(reasoned);
        expect(b.backgroundColor).toBe(a.backgroundColor);
        expect(b.color).toBe(a.color);
        expect(b.height).toBe(a.height);
      }
    });

    it.each<[ButtonVariant, string]>([
      ['primary', '--color-bg-subtle'],
      ['secondary', '--color-bg-surface'],
    ])('en %s usa el fondo %s y el texto text/disabled, como sin motivo', (variant, background) => {
      const style = getComputedStyle(
        render({ variant, disabled: true, disabledReason: REASON }).button,
      );
      expect(style.backgroundColor).toContain(`var(${background}`);
      expect(style.color).toContain('var(--color-text-disabled');
    });

    it.each<ButtonVariant>(['primary', 'secondary', 'ghost'])(
      '%s, con el foco del teclado, pinta el anillo focus/ring aunque esté deshabilitado',
      (variant) => {
        const { button } = render({ variant, disabled: true, disabledReason: REASON });
        const ring = cssVariable(theme, '--shadow-focus-ring');
        expect(paintsShadow(button, ring)).toBe(false);
        document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
        button.focus();
        expect(paintsShadow(button, ring)).toBe(true);
      },
    );

    it('el cursor es el de deshabilitado, no el de cargando', () => {
      const { button } = render({ disabled: true, disabledReason: REASON });
      expect(declaredStyle(button, 'cursor')).toBe('not-allowed');
    });

    it('cada selector de hover y de cargando excluye el estado disabled en la parte del anfitrión', () => {
      // jsdom no evalúa :hover; la comprobación manual está en Storybook (Disabled con motivo).
      render({ disabled: true, disabledReason: REASON });
      const hostPart = (selector: string) => selector.split(' .button')[0];
      const hover = rulesWith(':hover');
      const loading = rulesWith('aria-disabled');
      // Tres variantes en hover, y tres en cargando más el cursor de cargando.
      expect(hover).toHaveLength(3);
      expect(loading).toHaveLength(3 + 1);
      for (const selector of [...hover, ...loading]) {
        expect(hostPart(selector)).toContain(':not([data-state=disabled])');
      }
    });

    it('el anillo de foco tiene al menos la especificidad de la regla Disabled de cada variante', () => {
      // Con `aria-disabled` el botón se enfoca y la regla Disabled (`shadow-none`) no debe quitarle el
      // anillo. `declaredStyle` no resuelve la especificidad: se calcula sobre el texto del selector.
      render({ disabled: true, disabledReason: REASON });
      const specificity = (selector: string) =>
        (selector.match(/\[[^\]]*\]/g) ?? []).length +
        (selector.match(/\.[\w-]/g) ?? []).length +
        (selector.match(/:(?!:|not\()[\w-]+/g) ?? []).length;
      const ring = rulesWith(':focus-visible').filter((selector) => selector.includes('.button'));
      const disabled = rulesWith('data-state=disabled]').filter(
        (selector) => !selector.includes(':not(') && !selector.includes('.button__'),
      );
      expect(ring.length).toBeGreaterThan(0);
      expect(disabled.length).toBeGreaterThanOrEqual(3);
      for (const selector of disabled) {
        expect(specificity(ring[0])).toBeGreaterThanOrEqual(specificity(selector));
      }
    });

    it('Loading conserva su precedencia: disabled gana, sin spinner ni anuncio', () => {
      const { host, button } = render({
        disabled: true,
        loading: true,
        disabledReason: REASON,
      });
      expect(button.querySelector('liv-spinner')).toBeNull();
      expect(host.querySelector(':scope > [role="status"]')?.textContent?.trim()).toBe('');
      expect(button.getAttribute('aria-disabled')).toBe('true');
      expect(host.dataset['state']).toBe('disabled');
    });

    it('con loading pero sin disabled, el motivo no se muestra y Loading es el de siempre', () => {
      const { host, button } = render({ loading: true, disabledReason: REASON });
      expect(host.querySelector('.button__reason')).toBeNull();
      expect(button.getAttribute('aria-disabled')).toBe('true');
      expect(host.dataset['state']).toBeUndefined();
      expect(host.querySelector(':scope > [role="status"]')?.textContent?.trim()).toBe('Cargando');
    });

    it('en alto contraste (forced-colors) se ve inhabilitado: texto y borde GrayText', () => {
      // `aria-disabled` no recibe el trato de `:disabled` del navegador, así que se pinta a mano.
      // `declaredStyle` devuelve las palabras clave de color en minúsculas (`GrayText` en el CSS).
      const { button } = render({ variant: 'secondary', disabled: true, disabledReason: REASON });
      expect(declaredStyle(button, 'color', 'forced-colors')).toBe('graytext');
      expect(declaredStyle(button, 'border-color', 'forced-colors')).toBe('graytext');
      const active = render({ variant: 'secondary' }).button;
      expect(declaredStyle(active, 'color', 'forced-colors')).toBe('');
    });

    it('con motivo, el anfitrión es una columna, el botón se ajusta a su contenido y el motivo se alinea', () => {
      const start = render({ disabled: true, disabledReason: REASON });
      expect(declaredStyle(start.host, 'flex-direction')).toBe('column');
      expect(declaredStyle(start.host, 'align-items')).toBe('flex-start');
      expect(declaredStyle(start.host, 'gap')).toContain('--spacing-2');
      expect(declaredStyle(start.button, 'width')).toBe('auto');
      expect(declaredStyle(reasonOf(start.host), 'text-align')).toBe('start');

      const end = render({ disabled: true, disabledReason: REASON, reasonAlign: 'end' });
      expect(declaredStyle(end.host, 'align-items')).toBe('flex-end');
      expect(declaredStyle(reasonOf(end.host), 'text-align')).toBe('end');
    });

    it('sin motivo el anfitrión sigue en fila y el botón ocupa el ancho del anfitrión', () => {
      for (const inputs of [{}, { disabled: true }, { disabled: true, disabledReason: ' ' }]) {
        const { host, button } = render(inputs);
        expect(declaredStyle(host, 'flex-direction')).toBe('');
        expect(declaredStyle(button, 'width')).toBe('100%');
      }
    });

    it('el motivo cumple el contraste de texto (WCAG 1.4.3): text/secondary sobre bg/surface', () => {
      const color = (name: string) => solidColor(cssVariable(theme, `--color-${name}`));
      expect(contrastRatio(color('text-secondary'), color('bg-surface'))).toBeGreaterThanOrEqual(
        4.5,
      );
    });

    it('el motivo cambia en caliente y aria-describedby sigue a su texto', async () => {
      const fixture = TestBed.createComponent(ReasonHostComponent);
      fixture.componentInstance.disabled.set(true);
      fixture.componentInstance.reason.set(REASON);
      await fixture.whenStable();
      const host = fixture.nativeElement.querySelector('liv-button') as HTMLElement;
      const button = host.querySelector('button') as HTMLButtonElement;
      fixture.componentInstance.reason.set('  Otro motivo.  ');
      await fixture.whenStable();
      expect(reasonOf(host).textContent).toBe('Otro motivo.');
      expect(button.getAttribute('aria-describedby')).toBe(reasonOf(host).id);
    });

    it('data-reason-align solo está con motivo', () => {
      expect(render().host.hasAttribute('data-reason-align')).toBe(false);
      expect(render({ disabled: true }).host.hasAttribute('data-reason-align')).toBe(false);
      expect(
        render({ disabled: true, disabledReason: REASON }).host.hasAttribute('data-reason-align'),
      ).toBe(true);
    });

    it('reasonAlign es start por omisión y admite end', () => {
      expect(render({ disabled: true, disabledReason: REASON }).host.dataset['reasonAlign']).toBe(
        'start',
      );
      expect(
        render({ disabled: true, disabledReason: REASON, reasonAlign: 'end' }).host.dataset[
          'reasonAlign'
        ],
      ).toBe('end');
    });

    it('el motivo usa body/sm y text/secondary', () => {
      const reason = reasonOf(render({ disabled: true, disabledReason: REASON }).host);
      expect(usesToken(declaredStyle(reason, 'color'), '--color-text-secondary')).toBe(true);
      expect(declaredStyle(reason, 'font-size')).toContain('--text-body-sm');
    });
  });

  it.each<[ButtonSize, string]>([
    ['md', '16'],
    ['lg', '20'],
  ])('en tamaño %s los iconos miden %s px y son decorativos', (size, px) => {
    const { button } = render({ size, leadingIcon: 'log-out', trailingIcon: 'arrow-right' });
    const icons = Array.from(button.querySelectorAll('liv-icon svg'));
    expect(icons).toHaveLength(2);
    for (const svg of icons) {
      expect(svg.getAttribute('width')).toBe(px);
      expect(svg.getAttribute('aria-hidden')).toBe('true');
    }
    expect(button.querySelector('.button__leading-icon')).not.toBeNull();
    expect(button.querySelector('.button__trailing-icon')).not.toBeNull();
  });

  it('sin iconos no dibuja ninguno', () => {
    expect(render().button.querySelectorAll('liv-icon')).toHaveLength(0);
  });

  it.each<[ButtonVariant, string, string]>([
    ['primary', '--color-action-primary,', '--color-text-inverse'],
    ['secondary', '--color-bg-surface', '--color-text-primary'],
  ])('la variante %s usa los tokens de fondo y de texto de Figma', (variant, background, text) => {
    const style = getComputedStyle(render({ variant }).button);
    expect(style.backgroundColor).toContain(`var(${background}`);
    expect(style.color).toContain(`var(${text}`);
  });

  it('la variante Ghost usa el color de enlace', () => {
    expect(getComputedStyle(render({ variant: 'ghost' }).button).color).toContain(
      'var(--color-text-link',
    );
  });

  it('la variante Secondary tiene borde border/strong y, deshabilitada, border/default', () => {
    expect(declaredStyle(render({ variant: 'secondary' }).button, 'border-color')).toContain(
      'var(--color-border-strong,',
    );
    expect(
      declaredStyle(render({ variant: 'secondary', disabled: true }).button, 'border-color'),
    ).toContain('var(--color-border-default,');
  });

  it.each<ButtonVariant>(['primary', 'secondary', 'ghost'])(
    '%s pinta el anillo focus/ring de theme.css solo con el foco del teclado',
    (variant) => {
      const { button } = render({ variant });
      const ring = cssVariable(theme, '--shadow-focus-ring');
      expect(paintsShadow(button, ring)).toBe(false);
      document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
      button.focus();
      expect(paintsShadow(button, ring)).toBe(true);
      button.blur();
      expect(paintsShadow(button, ring)).toBe(false);
    },
  );

  it('el foco sigue visible en alto contraste (forced-colors)', () => {
    const { button } = render();
    // Foco con el teclado (Tab), como lo distingue :focus-visible.
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    button.focus();
    expect(declaredStyle(button, 'outline', 'forced-colors')).toContain('solid');
    button.blur();
    expect(declaredStyle(button, 'outline', 'forced-colors')).toBe('');
  });

  it.each<[ButtonSize, string]>([
    ['md', '--spacing-10'],
    ['lg', '--spacing-12'],
  ])('el tamaño %s tiene el alto de Figma', (size, height) => {
    expect(getComputedStyle(render({ size }).button).height).toContain(`var(${height}`);
  });

  it('cumple axe (001-19) en cada variante y estado', async () => {
    for (const variant of ['primary', 'secondary', 'ghost'] as ButtonVariant[]) {
      await expectNoAxeViolations(render({ variant }).host);
    }
    await expectNoAxeViolations(render({ size: 'lg', leadingIcon: 'log-out' }).host);
    await expectNoAxeViolations(render({ disabled: true }).host);
    await expectNoAxeViolations(render({ loading: true, label: 'Ingresando…' }).host);
  });

  it('cumple axe (007-21) con el botón deshabilitado y su motivo, en cada variante y alineación', async () => {
    for (const variant of ['primary', 'secondary', 'ghost'] as ButtonVariant[]) {
      for (const reasonAlign of ['start', 'end'] as ButtonReasonAlign[]) {
        await expectNoAxeViolations(
          render({ variant, disabled: true, disabledReason: REASON, reasonAlign }).host,
        );
      }
    }
    await expectNoAxeViolations(
      render({ size: 'lg', disabled: true, disabledReason: REASON, leadingIcon: 'log-out' }).host,
    );
  });
});
