import { booleanAttribute, ChangeDetectionStrategy, Component, input, OnInit } from '@angular/core';

/**
 * Tabla de datos de solo lectura (Figma: `Table`, nodo 64:446). Es una tabla HTML real: el componente
 * vive en un `<table livTable>` y las filas y celdas son `tr[livTableRow]`, `th[livTableCell]` y
 * `td[livTableCell]` que la pantalla compone dentro de su `<thead>` y su `<tbody>`.
 *
 * `caption` es obligatorio y es el nombre accesible de la tabla: se dibuja como `<caption>` oculto a la
 * vista (en Figma, el título de la pantalla). `loading` (`State=Loading` de Figma) declara
 * `aria-busy="true"`; la pantalla muestra entonces sus filas de `liv-skeleton` en lugar de los datos, nunca
 * datos viejos.
 *
 * Sin `State=Empty`, barra de herramientas ni paginación: la 1.4 usa `ResultState` para el vacío, y los
 * tres entran con la primera consulta 1.6 (Figma 64:450).
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- tabla HTML real: el componente se aplica a <table> nativo para conservar su semántica (plan 007, paso 8).
  selector: 'table[livTable]',
  templateUrl: './table.component.html',
  styleUrl: './table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.aria-busy]': 'loading() ? "true" : null',
  },
})
export class TableComponent implements OnInit {
  /** Nombre accesible de la tabla (el `caption`, oculto a la vista). */
  readonly caption = input.required<string>();
  /** Estado `Loading`: la tabla está ocupada y sus filas son marcadores de contenido. */
  readonly loading = input(false, { transform: booleanAttribute });

  ngOnInit(): void {
    if (!this.caption().trim()) {
      throw new Error(
        'La tabla necesita su `caption`: es su nombre accesible y no puede ir vacío.',
      );
    }
  }
}
