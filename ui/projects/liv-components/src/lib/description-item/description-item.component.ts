import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BadgeComponent, BadgeTone } from '../badge/badge.component';

/** Tipo del valor: texto simple (`Text`) o un estado dentro de un `Badge` (`Badge`). */
export type DescriptionItemType = 'text' | 'badge';

/**
 * Par etiqueta–valor de solo lectura (Figma: `DescriptionItem`, nodo 64:583), para los datos de una
 * vigencia. Se usa como `<div livDescriptionItem>` dentro de una lista de descripción (`<dl>`): la
 * etiqueta es el `dt` y el valor, el `dd`. La pantalla agrupa varios en una cuadrícula.
 *
 * `Type=Badge` muestra el valor como estado dentro de un `Badge`; su `tone` (por omisión `primary`, el del
 * diseño) lo elige la pantalla según el estado. El texto siempre dice el estado: el color no es la única
 * señal. `tone` no afecta a `Type=Text`.
 */
@Component({
  // Selector de atributo a propósito: el anfitrión es el `div` que agrupa `dt` y `dd` dentro de un `dl`; un
  // elemento `liv-*` en medio rompería la estructura de la lista de descripción (regla `definition-list`
  // de axe).
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'div[livDescriptionItem]',
  imports: [BadgeComponent],
  templateUrl: './description-item.component.html',
  styleUrl: './description-item.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-type]': 'type()',
  },
})
export class DescriptionItemComponent {
  readonly type = input<DescriptionItemType>('text');
  /** Etiqueta del dato (`Label` de Figma). */
  readonly label = input.required<string>();
  /** Valor del dato (`Value` de Figma). */
  readonly value = input.required<string>();
  /** Tono del `Badge` de `Type=Badge`. */
  readonly tone = input<BadgeTone>('primary');
}
