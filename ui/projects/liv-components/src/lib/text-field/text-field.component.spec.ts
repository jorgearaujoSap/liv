import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../testing/axe';
import { IconButtonComponent } from '../icon-button/icon-button.component';
import { IconName } from '../icon/icons';
import { TextFieldComponent, TextFieldType } from './text-field.component';

@Component({
  imports: [TextFieldComponent, IconButtonComponent],
  template: `
    <liv-text-field
      label="Usuario"
      [(value)]="value"
      [type]="type"
      [placeholder]="placeholder"
      [help]="help"
      [error]="error"
      [leadingIcon]="leadingIcon"
      [disabled]="disabled"
      autocomplete="username"
    >
      @if (withAction) {
        <liv-icon-button icon="eye" label="Mostrar contraseña" />
      }
    </liv-text-field>
  `,
})
class HostComponent {
  value = '';
  type: TextFieldType = 'text';
  placeholder?: string;
  help?: string;
  error?: string;
  leadingIcon?: IconName;
  disabled = false;
  withAction = false;
}

function render(inputs: Partial<HostComponent> = {}) {
  const fixture = TestBed.createComponent(HostComponent);
  Object.assign(fixture.componentInstance, inputs);
  fixture.detectChanges();
  const host = fixture.nativeElement.querySelector('liv-text-field') as HTMLElement;
  const input = host.querySelector('input') as HTMLInputElement;
  return { fixture, host, input, page: fixture.componentInstance };
}

describe('TextField', () => {
  it('la etiqueta siempre está visible y nombra al campo', () => {
    const { host, input } = render({ placeholder: 'lmartinez' });
    const label = host.querySelector('label') as HTMLLabelElement;
    expect(label.textContent?.trim()).toBe('Usuario');
    expect(label.htmlFor).toBe(input.id);
    expect(input.id).not.toBe('');
  });

  it('cada campo tiene identificadores únicos', () => {
    expect(render().input.id).not.toBe(render().input.id);
  });

  it('pasa al input el tipo, el placeholder y el autocompletado', () => {
    const { input } = render({ type: 'password', placeholder: 'Tu contraseña' });
    expect(input.type).toBe('password');
    expect(input.placeholder).toBe('Tu contraseña');
    expect(input.getAttribute('autocomplete')).toBe('username');
  });

  it('enlaza el valor en los dos sentidos', () => {
    const { fixture, input, page } = render({ value: 'lmartinez' });
    expect(input.value).toBe('lmartinez');
    input.value = 'jperez';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(page.value).toBe('jperez');
  });

  it('sin error no marca aria-invalid; con ayuda la enlaza por aria-describedby', () => {
    const plain = render();
    expect(plain.input.hasAttribute('aria-invalid')).toBe(false);
    expect(plain.input.hasAttribute('aria-describedby')).toBe(false);

    const { host, input } = render({ help: 'Tu usuario de la entidad.' });
    const help = host.querySelector(`#${input.getAttribute('aria-describedby')}`);
    expect(help?.textContent?.trim()).toBe('Tu usuario de la entidad.');
    expect(input.hasAttribute('aria-invalid')).toBe(false);
  });

  describe('Error', () => {
    it('expone aria-invalid y aria-describedby con el mensaje que dice cómo corregir', () => {
      const { host, input } = render({
        help: 'Tu usuario de la entidad.',
        error: 'Escribe tu usuario.',
      });
      expect(input.getAttribute('aria-invalid')).toBe('true');
      const describedBy = input.getAttribute('aria-describedby');
      expect(describedBy).toBeTruthy();
      const message = host.querySelector(`#${describedBy}`) as HTMLElement;
      expect(message.textContent?.trim()).toBe('Escribe tu usuario.');
    });

    it('marca el estado y muestra el icono de error junto al mensaje', () => {
      const { host } = render({ error: 'Escribe tu usuario.' });
      expect(host.dataset['state']).toBe('error');
      const icon = host.querySelector('.text-field__help liv-icon svg');
      expect(icon?.getAttribute('aria-hidden')).toBe('true');
    });

    it('el borde y la ayuda usan status/danger', () => {
      const { host } = render({ error: 'Escribe tu usuario.' });
      const control = host.querySelector('.text-field__control') as HTMLElement;
      expect(getComputedStyle(control).borderColor).toContain('var(--color-status-danger');
      const help = host.querySelector('.text-field__help') as HTMLElement;
      expect(getComputedStyle(help).color).toContain('var(--color-status-danger');
    });

    it('un error vacío no cuenta como error', () => {
      const { host, input } = render({ error: '  ' });
      expect(input.hasAttribute('aria-invalid')).toBe(false);
      expect(host.dataset['state']).toBe('default');
    });
  });

  it('deshabilitado desactiva el input y marca el estado', () => {
    const { host, input } = render({ disabled: true });
    expect(input.disabled).toBe(true);
    expect(host.dataset['state']).toBe('disabled');
  });

  it('el icono inicial es opcional, de 20 px y decorativo', () => {
    expect(render().host.querySelector('liv-icon')).toBeNull();
    const svg = render({ leadingIcon: 'user' }).host.querySelector(
      '.text-field__control liv-icon svg',
    );
    expect(svg?.getAttribute('width')).toBe('20');
    expect(svg?.getAttribute('aria-hidden')).toBe('true');
  });

  it('proyecta la acción final (IconButton) dentro del campo', () => {
    const { host } = render({ withAction: true });
    expect(host.querySelector('.text-field__control liv-icon-button')).not.toBeNull();
  });

  it('el campo mide 44 px de alto con el borde border/strong', () => {
    const control = render().host.querySelector('.text-field__control') as HTMLElement;
    expect(getComputedStyle(control).height).toContain('44px');
    expect(getComputedStyle(control).borderColor).toContain('var(--color-border-strong');
  });

  it('cumple axe en cada estado', async () => {
    await expectNoAxeViolations(render({ leadingIcon: 'user', value: 'lmartinez' }).host);
    await expectNoAxeViolations(render({ placeholder: 'lmartinez', help: 'Ayuda.' }).host);
    await expectNoAxeViolations(render({ error: 'Escribe tu usuario.' }).host);
    await expectNoAxeViolations(render({ disabled: true }).host);
    await expectNoAxeViolations(render({ type: 'password', withAction: true }).host);
  });
});
