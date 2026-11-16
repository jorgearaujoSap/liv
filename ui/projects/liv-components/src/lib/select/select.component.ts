import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
} from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/** Una opción de la lista: el `value` que emite el control y el texto que ve la persona. */
export interface SelectOption {
  value: string;
  label: string;
}

/** Estado visible, como la propiedad `State` de Figma. Focus y Open los da el foco del `<select>`. */
type SelectState = 'default' | 'error' | 'disabled';

let nextId = 0;

/**
 * Lista desplegable de selección única (Figma: `Select`, conjunto 81:629). Envuelve un `<select>` nativo
 * con su `<label>` siempre visible: el teclado, el lector de pantalla y la lista abierta los da el
 * navegador, y esa lista no se diseña ni se implementa (no hay un `role="listbox"` propio). El valor se
 * enlaza con `[(value)]` y es un texto; `null` es «sin elegir».
 *
 * - `options` se muestran en el orden recibido. Los `value` deben ser únicos y no vacíos: el vacío es la
 *   opción del `placeholder`.
 * - `placeholder` es el texto de esa opción vacía, que se muestra mientras el valor es `null` (o no está
 *   en las opciones) y no se puede volver a elegir ni aparece en la lista (`Content=Placeholder`).
 * - `help` es un texto de ayuda enlazado con `aria-describedby`. Con `error` el control pasa al estado
 *   Error: expone `aria-invalid="true"` y el mensaje, que dice cómo corregir, reemplaza a la ayuda.
 *   Inhabilitado, el error no se muestra porque no se puede corregir.
 * - `Open` se pinta igual que `Focus`: CSS no puede saber si la lista del sistema está abierta, y el
 *   `<select>` conserva el foco mientras tanto.
 */
@Component({
  selector: 'liv-select',
  imports: [IconComponent],
  templateUrl: './select.component.html',
  styleUrl: './select.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-state]': 'state()',
    '[attr.data-content]': 'content()',
  },
})
export class SelectComponent {
  readonly label = input.required<string>();
  readonly value = model<string | null>(null);
  readonly options = input<readonly SelectOption[]>([]);
  /** Texto de la opción vacía; nunca reemplaza a la etiqueta. */
  readonly placeholder = input<string>();
  /** Ayuda bajo el control (`Show help` y `Help` de Figma). */
  readonly help = input<string>();
  /** Mensaje de error que dice cómo corregir; si no está vacío, el control está en Error. */
  readonly error = input<string>();
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly selectId = `liv-select-${nextId++}`;
  protected readonly messageId = `${this.selectId}-message`;

  /** El valor que el control muestra: uno que no está en las opciones cuenta como «sin elegir». */
  protected readonly shownValue = computed(() => {
    const value = this.value();
    return value !== null && this.options().some((option) => option.value === value) ? value : null;
  });
  protected readonly content = computed(() =>
    this.shownValue() === null ? 'placeholder' : 'filled',
  );
  /*
   * Error, ayuda y estado siguen la misma regla que `TextField`. No se comparte código a propósito: tocar
   * `TextField` es un cambio crítico aparte.
   */
  /** Un error vacío cuenta como ausente, y un control inhabilitado no muestra error. */
  protected readonly errorText = computed(() =>
    this.disabled() ? null : this.error()?.trim() || null,
  );
  protected readonly message = computed(() => this.errorText() ?? (this.help()?.trim() || null));
  protected readonly state = computed<SelectState>(() => {
    if (this.disabled()) return 'disabled';
    return this.errorText() ? 'error' : 'default';
  });

  protected onChange(event: Event): void {
    this.value.set((event.target as HTMLSelectElement).value);
  }
}
