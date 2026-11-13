import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
} from '@angular/core';

/** Tipo de fila: la de encabezados (`bg/subtle`) o una de datos. */
export type TableRowType = 'header' | 'body';

/**
 * Fila de una `<table livTable>` (Figma: `TableRow`, nodo 63:352), sobre un `<tr livTableRow>`. Las
 * columnas las define cada pantalla con celdas `livTableCell`.
 *
 * Sin `type`, la fila de un `<thead>` es el encabezado (`header`) y las demás son de datos (`body`).
 * En una fila de datos, Hover pinta `bg/subtle` y Focus dibuja un contorno `border/focus` de 2 px dentro
 * de la fila mientras el enlace de la primera celda tiene el foco del teclado (`:has(:focus-visible)`;
 * un clic con el ratón no lo pinta). Los dos los da el navegador.
 *
 * La fila no es clicable: Hover pinta toda la fila, pero solo navega el enlace de la primera celda, que
 * es el único elemento enfocable.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- tabla HTML real: se aplica al <tr> nativo
  selector: 'tr[livTableRow]',
  template: '<ng-content />',
  styleUrl: './table-row.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.data-type]': 'kind()',
  },
})
export class TableRowComponent {
  readonly type = input<TableRowType>();

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  // Se calcula al primer uso, con la fila ya en el árbol: en el constructor aún no tiene padre.
  protected readonly kind = computed<TableRowType>(
    () => this.type() ?? (this.element.parentElement?.tagName === 'THEAD' ? 'header' : 'body'),
  );
}
