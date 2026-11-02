import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Dígitos del código del segundo factor (TOTP). */
const CODE_LENGTH = 6;

/** Estado visible del campo, como la propiedad `State` de Figma (Focus lo da el foco del input). */
type CodeInputState = 'default' | 'error';

let nextId = 0;

/**
 * Deja solo los dígitos, en ASCII y hasta seis. NFKC convierte los dígitos de ancho completo de
 * algunos teclados (`４８２`) en dígitos ASCII.
 */
function toDigits(text: string): string {
  return text.normalize('NFKC').replace(/\D/g, '');
}

function toCode(text: string): string {
  return toDigits(text).slice(0, CODE_LENGTH);
}

/**
 * Campo para el código de 6 dígitos del segundo factor (Figma: `CodeInput`). Es un solo input con
 * teclado numérico (`inputmode="numeric"`), `autocomplete="one-time-code"` y `maxlength` 6, con
 * dígitos grandes y espaciados para leerlos sin error. Acepta pegar el código completo: el pegado se
 * filtra antes de que `maxlength` lo recorte, así que descarta espacios, guiones y cualquier otro
 * carácter que no sea dígito. Un código completo pegado reemplaza lo escrito; unos pocos dígitos se
 * insertan sin empujar fuera los que ya estaban. Lo mismo vale para el texto que se suelta arrastrado.
 *
 * El valor se enlaza con `[(value)]`. Si la pantalla asigna un valor con otros caracteres, el campo lo
 * muestra filtrado, pero el modelo no cambia hasta que el usuario edite: la pantalla debe asignar solo
 * dígitos.
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
    '[attr.data-state]': 'state()',
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

  protected readonly code = computed(() => toCode(this.value()));
  protected readonly errorText = computed(() => this.error()?.trim() || null);
  protected readonly message = computed(() => this.errorText() ?? (this.help()?.trim() || null));
  protected readonly state = computed<CodeInputState>(() =>
    this.errorText() ? 'error' : 'default',
  );

  /**
   * `input` al escribir, `change` para el autocompletado que no dispara `input` y `compositionend` al
   * terminar una composición de IME. Durante la composición no se reescribe el campo: hacerlo la
   * cancelaría o duplicaría los caracteres.
   */
  protected onInput(event: Event): void {
    if ((event as InputEvent).isComposing) {
      return;
    }
    const field = event.target as HTMLInputElement;
    this.write(field, field.value, field.selectionStart ?? field.value.length);
  }

  /** El navegador aplica `maxlength` antes de `input`: el pegado se filtra aquí, completo. */
  protected onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    this.insert(event.target as HTMLInputElement, event.clipboardData?.getData('text') ?? '');
  }

  /** Igual que el pegado, para el texto que se suelta arrastrado o que reemplaza un autocompletado. */
  protected onBeforeInput(event: InputEvent): void {
    if (event.inputType !== 'insertFromDrop' && event.inputType !== 'insertReplacementText') {
      return;
    }
    const text = event.data ?? event.dataTransfer?.getData('text') ?? '';
    event.preventDefault();
    this.insert(event.target as HTMLInputElement, text);
  }

  /**
   * Un código completo reemplaza todo el valor (el caso de pegar el código nuevo sobre uno vencido).
   * Unos pocos dígitos se insertan en el cursor hasta llenar los seis, sin empujar fuera los demás.
   */
  private insert(field: HTMLInputElement, text: string): void {
    const digits = toDigits(text);
    if (digits.length >= CODE_LENGTH) {
      this.write(field, digits, CODE_LENGTH);
      return;
    }
    const start = field.selectionStart ?? field.value.length;
    const end = field.selectionEnd ?? start;
    const before = field.value.slice(0, start);
    const after = field.value.slice(end);
    const room = CODE_LENGTH - toDigits(before + after).length;
    const inserted = digits.slice(0, Math.max(room, 0));
    this.write(field, before + inserted + after, start + inserted.length);
  }

  private write(field: HTMLInputElement, text: string, caret: number): void {
    const code = toCode(text);
    // Si el código filtrado coincide con el valor anterior, el enlace [value] no cambia y Angular no
    // reescribe el input: por eso se escribe aquí, conservando el cursor.
    if (field.value !== code) {
      field.value = code;
      const position = toCode(text.slice(0, caret)).length;
      field.setSelectionRange(position, position);
    }
    this.value.set(code);
  }
}
