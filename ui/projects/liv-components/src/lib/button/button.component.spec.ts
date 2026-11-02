import { Component, signal } from '@angular/core';
import theme from '../../styles/theme.css' with { loader: 'text' };
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { cssVariable, declaredStyle, paintsShadow } from '../../testing/css';
import { IconName } from '../icon/icons';
import { ButtonComponent, ButtonSize, ButtonType, ButtonVariant } from './button.component';

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
});
