import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

/** Tipos de texto que admite el campo. */
export type TextFieldType = 'text' | 'password' | 'email' | 'search' | 'tel' | 'url';

/** Estado visible del campo, como la propiedad `State` de Figma (Focus lo da `:focus-within`). */
type TextFieldState = 'default' | 'error' | 'disabled';

let nextId = 0;

/**
 * Campo de texto (Figma: `TextField`). La etiqueta siempre está visible; el `placeholder` es solo un
 * ejemplo y nunca la reemplaza (`Content=Placeholder` en Figma). El valor se enlaza con `[(value)]`.
 *
 * Con `error` el campo pasa al estado Error: expone `aria-invalid="true"` y el mensaje, que dice cómo
 * corregir, reemplaza a la ayuda y queda enlazado con `aria-describedby`. La acción final (por ejemplo
 * "Mostrar contraseña") se proyecta como un `liv-icon-button` dentro del campo.
 */
@Component({
  selector: 'liv-text-field',
  imports: [IconComponent],
  templateUrl: './text-field.component.html',
  styleUrl: './text-field.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-state]': 'state()',
  },
})
export class TextFieldComponent {
  readonly label = input.required<string>();
  readonly value = model('');
  readonly type = input<TextFieldType>('text');
  readonly name = input<string>();
  readonly autocomplete = input<string>();
  /** Texto de ejemplo; nunca reemplaza a la etiqueta. */
  readonly placeholder = input<string>();
  /** Ayuda bajo el campo (`Show help` y `Help` de Figma). */
  readonly help = input<string>();
  /** Mensaje de error que dice cómo corregir; si no está vacío, el campo está en Error. */
  readonly error = input<string>();
  /** Icono al inicio (`Show leading icon` y `Leading icon` de Figma); es decorativo. */
  readonly leadingIcon = input<IconName>();
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });

  protected readonly inputId = `liv-text-field-${nextId++}`;
  protected readonly messageId = `${this.inputId}-message`;

  /** Un error vacío cuenta como ausente. */
  protected readonly errorText = computed(() => this.error()?.trim() || null);
  protected readonly message = computed(() => this.errorText() ?? (this.help()?.trim() || null));
  protected readonly state = computed<TextFieldState>(() => {
    if (this.disabled()) return 'disabled';
    return this.errorText() ? 'error' : 'default';
  });

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLInputElement).value);
  }
}
