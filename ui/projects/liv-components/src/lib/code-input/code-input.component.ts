import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Dígitos del código del segundo factor (TOTP). */
const CODE_LENGTH = 6;

let nextId = 0;

/**
 * Campo para el código de 6 dígitos del segundo factor (Figma: `CodeInput`). Es un solo input con
 * teclado numérico (`inputmode="numeric"`) y `autocomplete="one-time-code"`, con dígitos grandes y
 * espaciados para leerlos sin error. Acepta pegar el código completo: descarta espacios, guiones y
 * cualquier otro carácter que no sea dígito, y se queda con los seis primeros. El valor se enlaza con
 * `[(value)]`.
 *
 * Con `error` pasa al estado Error: `aria-invalid="true"` y el mensaje reemplaza a la ayuda, enlazado
 * con `aria-describedby`.
 */
@Component({
  selector: 'liv-code-input',
  imports: [IconComponent],
  templateUrl: './code-input.component.html',
  styleUrl: './code-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-state]': "errorText() ? 'error' : 'default'",
  },
})
export class CodeInputComponent {
  readonly label = input.required<string>();
  readonly value = model('');
  readonly name = input<string>();
  /** Texto de ejemplo (p. ej. "000000"); nunca reemplaza a la etiqueta. */
  readonly placeholder = input<string>();
  /** Ayuda bajo el campo (`Show help` y `Help` de Figma). */
  readonly help = input<string>();
  /** Mensaje de error que dice cómo corregir; si no está vacío, el campo está en Error. */
  readonly error = input<string>();

  protected readonly maxLength = CODE_LENGTH;
  protected readonly inputId = `liv-code-input-${nextId++}`;
  protected readonly messageId = `${this.inputId}-message`;

  protected readonly errorText = computed(() => this.error()?.trim() || null);
  protected readonly message = computed(() => this.errorText() ?? (this.help()?.trim() || null));

  protected onInput(event: Event): void {
    const field = event.target as HTMLInputElement;
    const digits = field.value.replace(/\D/g, '').slice(0, CODE_LENGTH);
    if (field.value !== digits) {
      field.value = digits;
    }
    this.value.set(digits);
  }
}
