import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { RadioGroupComponent } from './radio-group.component';

let nextId = 0;

/**
 * Opción de un `liv-radio-group` (Figma: `RadioOption`, nodo 64:629). Es un radio nativo con una
 * tarjeta alrededor: la tarjeta entera es su `label`. El nombre accesible es solo la etiqueta; la
 * descripción se anuncia aparte, con `aria-describedby`. La opción elegida lleva borde, fondo y punto
 * interior, así que no depende solo del color. Hover y Focus los da el navegador; Disabled, la propiedad
 * `disabled` o la del grupo.
 *
 * Solo existe dentro de un `liv-radio-group`, que le da el `name` y recibe su elección.
 */
@Component({
  selector: 'liv-radio-option',
  templateUrl: './radio-option.component.html',
  styleUrl: './radio-option.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-selected]': 'selected() ? "true" : "false"',
    '[attr.data-state]': 'isDisabled() ? "disabled" : "default"',
  },
})
export class RadioOptionComponent {
  private readonly group = inject(RadioGroupComponent);

  /** Valor del grupo cuando esta opción está elegida. */
  readonly value = input.required<string>();
  /** Texto principal (`Label` de Figma). */
  readonly label = input.required<string>();
  /** Texto de apoyo (`Description` y `Show description` de Figma); vacío, no se muestra. */
  readonly description = input<string>();
  readonly disabled = input(false, { transform: booleanAttribute });

  private readonly id = `liv-radio-option-${nextId++}`;
  protected readonly labelId = `${this.id}-label`;
  protected readonly descriptionId = `${this.id}-description`;

  protected readonly groupName = this.group.groupName;
  protected readonly selected = computed(() => this.group.value() === this.value());
  protected readonly isDisabled = computed(() => this.disabled() || this.group.disabled());
  protected readonly descriptionText = computed(() => this.description()?.trim() || null);

  protected onChange(): void {
    this.group.select(this.value());
  }
}
