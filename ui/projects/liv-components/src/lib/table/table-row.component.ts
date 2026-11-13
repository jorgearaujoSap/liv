import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Tipo de fila: la de encabezados (`bg/subtle`) o una de datos. */
export type TableRowType = 'header' | 'body';

/**
 * Fila de una `liv-table` (Figma: `TableRow`, nodo 63:352), sobre un `<tr livTableRow>`. Las columnas las
 * define cada pantalla con celdas `livTableCell`. En una fila de datos, Hover pinta `bg/subtle` y Focus
 * dibuja un borde `border/focus` de 2 px dentro de la fila mientras el enlace de la primera celda tiene el
 * foco (`:focus-within`): solo ese enlace recibe el foco. Los dos los da el navegador.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- tabla HTML real: el componente se aplica a <tr> nativo para conservar su semántica (plan 007, paso 8).
  selector: 'tr[livTableRow]',
  template: '<ng-content />',
  styleUrl: './table-row.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-type]': 'type()',
  },
})
export class TableRowComponent {
  readonly type = input<TableRowType>('body');
}
