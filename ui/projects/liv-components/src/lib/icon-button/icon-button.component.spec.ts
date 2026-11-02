import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { declaredStyle } from '../../testing/css';
import { IconName } from '../icon/icons';
import { IconButtonComponent, IconButtonSize, IconButtonVariant } from './icon-button.component';

@Component({
  imports: [IconButtonComponent],
  template: `
    <liv-icon-button
      [icon]="icon"
      [label]="label"
      [variant]="variant"
      [size]="size"
      [disabled]="disabled"
      (click)="clicks = clicks + 1"
    />
  `,
})
class HostComponent {
  icon: IconName = 'eye';
  label = 'Mostrar contraseña';
  variant: IconButtonVariant = 'ghost';
  size: IconButtonSize = 'sm';
  disabled = false;
  clicks = 0;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-icon-button') as HTMLElement;
  const button = host.querySelector('button') as HTMLButtonElement;
  return { fixture, host, button, page: fixture.componentInstance };
}

describe('IconButton', () => {
  it('es un botón nativo de tipo button con aria-label y tooltip en español', () => {
    const { button } = render();
    expect(button.getAttribute('type')).toBe('button');
    expect(button.getAttribute('aria-label')).toBe('Mostrar contraseña');
    expect(button.getAttribute('title')).toBe('Mostrar contraseña');
  });

  it('usa Ghost y Sm por defecto', () => {
    const fixture = TestBed.createComponent(IconButtonComponent);
    fixture.componentRef.setInput('icon', 'eye');
    fixture.componentRef.setInput('label', 'Mostrar contraseña');
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.dataset['variant']).toBe('ghost');
    expect(host.dataset['size']).toBe('sm');
  });

  it('el icono es decorativo: el nombre lo da aria-label', () => {
    const svg = render().button.querySelector('liv-icon svg') as SVGElement;
    expect(svg.getAttribute('aria-hidden')).toBe('true');
  });

  it.each<[IconButtonSize, string, string]>([
    ['sm', '--spacing-8', '16'],
    ['md', '--spacing-10', '20'],
  ])('el tamaño %s mide lo de Figma y su icono %s px', (size, box, icon) => {
    const { button } = render({ size });
    expect(getComputedStyle(button).width).toContain(`var(${box}`);
    expect(getComputedStyle(button).height).toContain(`var(${box}`);
    expect(button.querySelector('liv-icon svg')?.getAttribute('width')).toBe(icon);
  });

  it.each<IconButtonVariant>(['ghost', 'secondary'])('admite la variante %s', (variant) => {
    expect(render({ variant }).host.dataset['variant']).toBe(variant);
  });

  it('el icono usa text/secondary y la variante Secondary tiene fondo y borde', () => {
    expect(getComputedStyle(render().button).color).toContain('var(--color-text-secondary');
    const secondary = getComputedStyle(render({ variant: 'secondary' }).button);
    expect(secondary.backgroundColor).toContain('var(--color-bg-surface');
    expect(declaredStyle(render({ variant: 'secondary' }).button, 'border-color')).toContain(
      'var(--color-border-strong,',
    );
  });

  it('el foco sigue visible en alto contraste (forced-colors)', () => {
    const { button } = render();
    // Foco con el teclado (Tab), como lo distingue :focus-visible.
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    button.focus();
    expect(declaredStyle(button, 'outline', 'forced-colors')).toContain('solid');
    button.blur();
    expect(declaredStyle(button, 'outline', 'forced-colors')).toBe('');
  });

  it('emite clic y, deshabilitado, no lo emite', () => {
    const enabled = render();
    enabled.button.click();
    expect(enabled.page.clicks).toBe(1);

    const disabled = render({ disabled: true });
    expect(disabled.button.disabled).toBe(true);
    disabled.button.click();
    expect(disabled.page.clicks).toBe(0);
  });

  it('cumple axe (001-19) en cada variante y deshabilitado', async () => {
    await expectNoAxeViolations(render().host);
    await expectNoAxeViolations(render({ variant: 'secondary', size: 'md' }).host);
    await expectNoAxeViolations(render({ disabled: true }).host);
  });
});
