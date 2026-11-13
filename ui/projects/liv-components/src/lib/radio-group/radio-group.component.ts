import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
} from '@angular/core';

let nextId = 0;

/**
 * Grupo de selección única (Figma: `RadioGroup`, nodo 73:514). En código es un `fieldset` con su `legend`
 * visible, que le da el nombre accesible, y las opciones (`liv-radio-option`) proyectadas dentro. Cada
 * opción es un radio nativo: todos comparten un `name`, así que el navegador da las flechas para cambiar
 * de opción y un solo Tab para entrar al grupo (a la opción marcada o, si no hay ninguna, a la primera).
 * jsdom no implementa ese comportamiento: se prueba en `e2e/`.
 *
 * El valor se enlaza con `[(value)]` y es el `value` de la opción marcada; `null` es sin elección. Con
 * `disabled` se deshabilitan el `fieldset` y todas sus opciones. El grupo solo presenta: qué opciones hay
 * y cuál viene elegida lo decide la pantalla.
 */
@Component({
  selector: 'liv-radio-group',
  templateUrl: './radio-group.component.html',
  styleUrl: './radio-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadioGroupComponent {
  /** Título visible del grupo (`Legend` de Figma): es su nombre accesible. */
  readonly legend = input.required<string>();
  /** `value` de la opción elegida, o `null` si no hay ninguna. */
  readonly value = model<string | null>(null);
  /** `name` de los radios; por omisión, uno propio de cada grupo. */
  readonly name = input<string>();
  readonly disabled = input(false, { transform: booleanAttribute });

  private readonly defaultName = `liv-radio-group-${nextId++}`;

  /** `name` común de los radios del grupo. */
  readonly groupName = computed(() => this.name()?.trim() || this.defaultName);

  /** Lo llaman las opciones al elegirse; un grupo deshabilitado no cambia de valor. */
  select(value: string): void {
    if (!this.disabled()) {
      this.value.set(value);
    }
  }
}
