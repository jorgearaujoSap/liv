import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { declaredPlaceholderStyle, declaredStyle } from '../../testing/css';
import { CodeInputComponent } from './code-input.component';

@Component({
  imports: [CodeInputComponent],
  template: `
    <liv-code-input
      label="Código de verificación"
      [(value)]="value"
      [help]="help"
      [error]="error"
      placeholder="000000"
    />
  `,
})
class HostComponent {
  value = '';
  help?: string = 'El código cambia cada 30 segundos.';
  error?: string;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-code-input') as HTMLElement;
  const input = host.querySelector('input') as HTMLInputElement;
  return { fixture, host, input, page: fixture.componentInstance };
}

function type(
  fixture: ReturnType<typeof render>['fixture'],
  input: HTMLInputElement,
  text: string,
) {
  input.value = text;
  input.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

/** Pegado como lo hace el navegador: con el texto en `clipboardData` y antes de aplicar maxlength. */
function paste(
  fixture: ReturnType<typeof render>['fixture'],
  input: HTMLInputElement,
  text: string,
) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { getData: () => text } });
  input.dispatchEvent(event);
  fixture.detectChanges();
  return event;
}

describe('CodeInput', () => {
  it('es un solo input con teclado numérico y autocompletado del código de un solo uso', () => {
    const { host, input } = render();
    expect(host.querySelectorAll('input')).toHaveLength(1);
    expect(input.getAttribute('inputmode')).toBe('numeric');
    expect(input.getAttribute('autocomplete')).toBe('one-time-code');
    expect(input.getAttribute('maxlength')).toBe('6');
    expect(input.type).toBe('text');
  });

  it('la etiqueta siempre está visible y nombra al campo', () => {
    const { host, input } = render();
    const label = host.querySelector('label') as HTMLLabelElement;
    expect(label.textContent?.trim()).toBe('Código de verificación');
    expect(label.htmlFor).toBe(input.id);
  });

  it('enlaza el valor en los dos sentidos', () => {
    const { fixture, input, page } = render({ value: '482193' });
    expect(input.value).toBe('482193');
    type(fixture, input, '123');
    expect(page.value).toBe('123');
  });

  it('acepta pegar el código completo con espacios o guiones y deja solo seis dígitos', () => {
    const { fixture, input, page } = render();
    type(fixture, input, ' 482-193 ');
    expect(input.value).toBe('482193');
    expect(page.value).toBe('482193');

    type(fixture, input, '48219399');
    expect(input.value).toBe('482193');
    expect(page.value).toBe('482193');
  });

  it('al pegar filtra antes de que maxlength recorte: no pierde dígitos', () => {
    const { fixture, input, page } = render();
    const event = paste(fixture, input, ' 482-193 ');
    expect(event.defaultPrevented).toBe(true);
    expect(input.value).toBe('482193');
    expect(page.value).toBe('482193');

    const second = render();
    paste(second.fixture, second.input, '482 193');
    expect(second.input.value).toBe('482193');
  });

  it('pegar un código completo sobre un campo lleno lo reemplaza', () => {
    const { fixture, input, page } = render({ value: '111111' });
    input.setSelectionRange(6, 6);
    paste(fixture, input, '482 193');
    expect(page.value).toBe('482193');
  });

  it('pegar unos dígitos en medio no empuja fuera los que ya estaban', () => {
    const { fixture, input, page } = render({ value: '4893' });
    input.setSelectionRange(2, 2);
    paste(fixture, input, '2-1 7');
    expect(page.value).toBe('482193');
    expect(input.selectionStart).toBe(4);
  });

  it('al soltar texto arrastrado lo filtra antes de que maxlength lo recorte', () => {
    const { fixture, input, page } = render();
    const event = new InputEvent('beforeinput', {
      inputType: 'insertFromDrop',
      data: ' 482-193 ',
      bubbles: true,
      cancelable: true,
    });
    input.dispatchEvent(event);
    fixture.detectChanges();
    expect(event.defaultPrevented).toBe(true);
    expect(page.value).toBe('482193');
  });

  it('conserva el cursor al descartar un carácter que no es dígito', () => {
    const { fixture, input } = render({ value: '4821' });
    input.focus();
    input.value = '48x21';
    input.setSelectionRange(3, 3);
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(input.value).toBe('4821');
    expect(input.selectionStart).toBe(2);
  });

  it('no reescribe el campo durante una composición de IME', () => {
    const { fixture, input, page } = render();
    input.value = '４';
    input.dispatchEvent(new InputEvent('input', { isComposing: true }));
    fixture.detectChanges();
    expect(input.value).toBe('４');
    input.dispatchEvent(new CompositionEvent('compositionend'));
    fixture.detectChanges();
    expect(page.value).toBe('4');
  });

  it('convierte los dígitos de ancho completo a dígitos ASCII', () => {
    const { fixture, input, page } = render();
    paste(fixture, input, '４８２１９３');
    expect(page.value).toBe('482193');
  });

  it('toma el valor también del evento change (autocompletado)', () => {
    const { fixture, input, page } = render();
    input.value = '482193';
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(page.value).toBe('482193');
  });

  it('muestra solo dígitos aunque el valor llegue de fuera con otros caracteres', () => {
    expect(render({ value: '482 193' }).input.value).toBe('482193');
  });

  it('descarta lo que no es dígito', () => {
    const { fixture, input, page } = render();
    type(fixture, input, '4a8');
    expect(input.value).toBe('48');
    expect(page.value).toBe('48');
  });

  it('enlaza la ayuda por aria-describedby, sin aria-invalid', () => {
    const { host, input } = render();
    const help = host.querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(help?.textContent?.trim()).toBe('El código cambia cada 30 segundos.');
    expect(input.hasAttribute('aria-invalid')).toBe(false);
    expect(help?.querySelector('liv-icon')).toBeNull();
  });

  it('en error expone aria-invalid y aria-describedby con el mensaje', () => {
    const { host, input } = render({ error: 'El código no es válido. Escribe el código actual.' });
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(host.dataset['state']).toBe('error');
    const message = host.querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(message?.textContent?.trim()).toBe('El código no es válido. Escribe el código actual.');
    const icon = host.querySelector('.code-input__help liv-icon svg');
    expect(icon?.getAttribute('aria-hidden')).toBe('true');
    expect(icon?.getAttribute('width')).toBe('16');
  });

  it('el placeholder usa text/muted, que cumple 4,5:1 (T04a)', () => {
    const { input } = render();
    expect(declaredPlaceholderStyle(input, 'color')).toContain('var(--color-text-muted,');
  });

  it('el borde es border/strong y, en error, status/danger', () => {
    const control = (inputs: Partial<HostComponent>) =>
      render(inputs).host.querySelector('.code-input__control') as HTMLElement;
    expect(declaredStyle(control({}), 'border-color')).toContain('var(--color-border-strong,');
    expect(declaredStyle(control({ error: 'No es válido.' }), 'border-color')).toContain(
      'var(--color-status-danger,',
    );
    expect(declaredStyle(control({}), 'box-sizing')).toBe('border-box');
  });

  it('el foco sigue visible en alto contraste (forced-colors)', () => {
    const { host, input } = render();
    input.focus();
    const control = host.querySelector('.code-input__control') as HTMLElement;
    expect(declaredStyle(control, 'outline', 'forced-colors')).toContain('solid');
    input.blur();
    expect(declaredStyle(control, 'outline', 'forced-colors')).toBe('');
  });

  it('usa el estilo code/lg y el alto de 56 px de Figma', () => {
    const { host, input } = render();
    expect(getComputedStyle(input).fontSize).toContain('var(--text-code-lg');
    const control = host.querySelector('.code-input__control') as HTMLElement;
    expect(getComputedStyle(control).height).toContain('56px');
  });

  it('cumple axe (001-19) con ayuda, con error y sin ayuda', async () => {
    await expectNoAxeViolations(render({ value: '482193' }).host);
    await expectNoAxeViolations(render({ error: 'El código no es válido.' }).host);
    await expectNoAxeViolations(render({ help: undefined }).host);
  });
});
