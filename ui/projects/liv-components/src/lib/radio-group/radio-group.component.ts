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
 * de opción y un solo Tab para entrar al grupo (a la opción marcada o, si no hay ninguna, a la primera
 * con Tab y a la última con Mayús+Tab). jsdom no implementa ese comportamiento: se prueba en `e2e/`.
 *
 * El valor se enlaza con `[(value)]` y es el `value` de la opción marcada; `null` es sin elección. Es un
 * texto (`string`): una pantalla con valores numéricos, como un año, lo convierte en los dos sentidos.
 * Con `disabled` se deshabilitan el `fieldset` y todas sus opciones.
 *
 * El grupo solo presenta: qué opciones hay y cuál viene elegida lo decide la pantalla. Por eso no corrige
 * su valor si la opción elegida desaparece o cambia de `value`: el grupo queda sin ninguna marcada y
 * `value()` conserva el valor viejo hasta que la pantalla lo reasigne. Los `value` de las opciones deben
 * ser únicos.
 *
 * Dos propiedades de Figma no son entradas: `Options` (cuántas opciones hay) es el contenido proyectado, y
 * `Show description`, de `RadioOption`, es una `description` vacía.
 */
@Component({
  selector: 'liv-radio-group',
  templateUrl: './radio-group.component.html',
  styleUrl: './radio-group.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RadioGroupComponent {
  /** Título visible del grupo (`Legend` de Figma): es su nombre accesible, así que no puede ir vacío. */
  readonly legend = input.required<string>();
  /** `value` de la opción elegida, o `null` si no hay ninguna. */
  readonly value = model<string | null>(null);
  /**
   * `name` de los radios; por omisión, uno propio de cada grupo. Si se da, debe ser único en la página:
   * con el mismo `name`, dos grupos serían uno solo para el navegador (las flechas pasarían de uno a otro).
   */
  readonly name = input<string>();
  readonly disabled = input(false, { transform: booleanAttribute });

  private readonly defaultName = `liv-radio-group-${nextId++}`;

  /** Uso interno de `liv-radio-option`: las pantallas no lo leen. `name` común de los radios. */
  readonly groupName = computed(() => this.name()?.trim() || this.defaultName);

  /** Uso interno de `liv-radio-option`: las pantallas no lo llaman. Un grupo deshabilitado no cambia de valor. */
  select(value: string): void {
    if (!this.disabled()) {
      this.value.set(value);
    }
  }
}
