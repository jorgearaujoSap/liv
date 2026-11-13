import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { BadgeComponent } from '../badge/badge.component';
import { SkeletonComponent } from '../skeleton/skeleton.component';
import { TableCellComponent } from './table-cell.component';
import { TableRowComponent } from './table-row.component';
import { TableComponent } from './table.component';

const figmaTable =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=64-446';
const figmaRow =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=63-352';
const figmaCell =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=63-283';

interface TableArgs {
  caption: string;
  loading: boolean;
  /** Quita el destino del enlace de 2026, para ver una celda `link` inerte. */
  withoutHref: boolean;
}

const meta: Meta<TableArgs> = {
  title: 'Componentes/Table',
  component: TableComponent,
  tags: ['autodocs'],
  decorators: [
    moduleMetadata({
      imports: [
        TableComponent,
        TableRowComponent,
        TableCellComponent,
        BadgeComponent,
        SkeletonComponent,
      ],
    }),
  ],
  parameters: {
    docs: {
      description: {
        component:
          'Tabla de datos de solo lectura, una tabla HTML real: `<table livTable>` con ' +
          '`tr[livTableRow]`, `th[livTableCell]` y `td[livTableCell]`. Su nombre accesible es ' +
          'obligatorio: `caption` (oculto a la vista) o `aria-label`; `loading` declara ' +
          '`aria-busy`. La celda `link` dibuja su propio enlace y emite `navigate` (la pantalla ' +
          'valida el destino). **La fila no es clicable**: Hover pinta toda la fila, pero solo ' +
          'navega el enlace del año, que es el único elemento enfocable. Focus dibuja el contorno ' +
          'de la fila solo con el teclado (usa el Tab; un clic con el ratón no lo pinta). Todas las filas tienen las mismas columnas que el encabezado, y en un ancho ' +
          'estrecho la pantalla envuelve la tabla en un contenedor con desplazamiento horizontal. ' +
          'El estado vacío, la barra y la paginación no entran: la 1.4 usa `ResultState`. ' +
          `[Table en Figma](${figmaTable}) · [TableRow en Figma](${figmaRow}) · ` +
          `[TableCell en Figma](${figmaCell})`,
      },
    },
  },
  args: {
    caption: 'Vigencias fiscales',
    loading: false,
    withoutHref: false,
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="w-[1104px]">
        <table livTable [caption]="caption" [loading]="loading">
          <thead>
            <tr livTableRow type="header">
              <th livTableCell>Año</th>
              <th livTableCell>Inicio</th>
              <th livTableCell>Fin</th>
              <th livTableCell>Estado</th>
              <th livTableCell>Liquidación</th>
              <th livTableCell type="action">Ver detalle</th>
            </tr>
          </thead>
          <tbody>
            @if (loading) {
              @for (row of [1, 2, 3]; track row) {
                <tr livTableRow>
                  <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
                  <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
                  <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
                  <td livTableCell><liv-skeleton width="88px" height="20px" /></td>
                  <td livTableCell><liv-skeleton width="120px" height="14px" /></td>
                  <td livTableCell></td>
                </tr>
              }
            } @else {
              <tr livTableRow>
                <td livTableCell type="link" href="/vigencias/2027">2027</td>
                <td livTableCell>1 de enero de 2027</td>
                <td livTableCell>31 de diciembre de 2027</td>
                <td livTableCell type="badge"><liv-badge>Planeada</liv-badge></td>
                <td livTableCell>No habilitada</td>
                <td livTableCell type="action"></td>
              </tr>
              <tr livTableRow>
                <td livTableCell type="link" [href]="withoutHref ? undefined : '/vigencias/2026'">2026</td>
                <td livTableCell>1 de enero de 2026</td>
                <td livTableCell>31 de diciembre de 2026</td>
                <td livTableCell type="badge"><liv-badge tone="primary">En preparación</liv-badge></td>
                <td livTableCell>No habilitada</td>
                <td livTableCell type="action"></td>
              </tr>
              <tr livTableRow>
                <td livTableCell type="link" href="/vigencias/2025">2025</td>
                <td livTableCell>1 de enero de 2025</td>
                <td livTableCell>31 de diciembre de 2025</td>
                <td livTableCell type="badge"><liv-badge tone="success">Habilitada</liv-badge></td>
                <td livTableCell>Habilitada</td>
                <td livTableCell type="action"></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<TableArgs>;

/**
 * Con datos, como la lista de vigencias de la 1.4 (Figma 64:446, `State=Default`). Cubre `TableRow`
 * (63:352) con Header y Body y `TableCell` (63:283) con Header, Text, Link, Badge y Action. Hover y Focus
 * no se pueden fijar desde la historia: pasa el ratón por una fila y usa el Tab para enfocar su enlace.
 * Solo el enlace del año navega: hacer clic en el resto de la fila, o en el chevron, no hace nada.
 */
export const ConDatos: Story = { name: 'Con datos' };

/**
 * `State=Loading`: `aria-busy="true"` y filas de `liv-skeleton` en lugar de los datos. Las filas cargando
 * son de la pantalla: aquí cada marcador va dentro de su celda, alineado con su columna.
 */
export const Cargando: Story = { name: 'Cargando', args: { loading: true } };

/**
 * Un enlace sin destino (`href` ausente o vacío) queda inerte y se pinta como texto, no como enlace: la
 * fila de 2026 no tiene nada que recibir el foco.
 */
export const EnlaceSinDestino: Story = {
  name: 'Enlace sin destino',
  args: { withoutHref: true },
};
