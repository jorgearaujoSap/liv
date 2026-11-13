import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  isDevMode,
} from '@angular/core';

/**
 * Tabla de datos de solo lectura (Figma: `Table`, nodo 64:446). Es una tabla HTML real: el componente
 * vive en un `<table livTable>` y las filas y celdas son `tr[livTableRow]`, `th[livTableCell]` y
 * `td[livTableCell]` que la pantalla compone dentro de su `<thead>` y su `<tbody>`. Los tres selectores
 * solo valen sobre esas etiquetas nativas, y la pantalla importa los tres componentes: sin uno de ellos,
 * Angular deja la etiqueta sin estilos ni `scope`, sin avisar.
 *
 * La tabla necesita un nombre accesible: su `caption` (un `<caption>` oculto a la vista; en Figma, el
 * título de la pantalla) o su `aria-label`, como atributo o como `[aria-label]`. Con los dos manda el
 * `aria-label` y el `<caption>` se conserva. Si faltan los dos, o quedan vacíos, es un error del
 * desarrollador y lanza una excepción solo en desarrollo, también si ocurre después; en producción no
 * rompe la pantalla.
 *
 * `loading` (`State=Loading` de Figma) declara `aria-busy="true"`; la pantalla muestra entonces sus filas
 * de `liv-skeleton` en lugar de los datos, nunca datos viejos.
 *
 * Todas las filas deben tener las mismas columnas que la del encabezado: el layout es fijo y toma los
 * anchos de la primera fila. La tabla no desplaza ni acorta: en un ancho estrecho (zoom, teléfono) la
 * pantalla la envuelve en un contenedor con desplazamiento horizontal.
 *
 * Sin `State=Empty`, barra de herramientas ni paginación: la 1.4 usa `ResultState` para el vacío, y los
 * tres entran con la primera consulta 1.6 (Figma 64:450).
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector -- tabla HTML real: se aplica al <table> nativo (plan 007, paso 8)
  selector: 'table[livTable]',
  templateUrl: './table.component.html',
  styleUrl: './table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[attr.aria-busy]': 'loading() ? "true" : null',
    '[attr.aria-label]': 'labelText()',
  },
})
export class TableComponent {
  /** Nombre accesible por un `<caption>` oculto a la vista (uno vacío o de espacios cuenta como ausente). */
  readonly caption = input<string>();
  /** Nombre accesible por `aria-label`; si se da, manda sobre el `caption`. */
  readonly ariaLabel = input<string>(undefined, { alias: 'aria-label' });
  /** Estado `Loading`: la tabla está ocupada y sus filas son marcadores de contenido. */
  readonly loading = input(false, { transform: booleanAttribute });

  protected readonly captionText = computed(() => this.caption()?.trim() || null);
  protected readonly labelText = computed(() => this.ariaLabel()?.trim() || null);

  constructor() {
    if (isDevMode()) {
      effect(() => {
        if (!this.captionText() && !this.labelText()) {
          throw new Error(
            'La tabla necesita su nombre accesible: `caption` o `aria-label`, y no pueden ir los dos vacíos.',
          );
        }
      });
    }
  }
}
