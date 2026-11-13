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
        component: `Tabla de datos de solo lectura, una tabla HTML real: \`<table livTable>\` con \`tr[livTableRow]\`, \`th[livTableCell]\` y \`td[livTableCell]\`. Su \`caption\` es obligatorio (es el nombre accesible, oculto a la vista) y \`loading\` declara \`aria-busy\`. La celda \`link\` dibuja su propio enlace y emite \`navigate\`; solo ese enlace recibe el foco, y la fila pinta Hover y Focus (pasa el ratón y usa el Tab para verlos). El estado vacío, la barra y la paginación no entran: la 1.4 usa \`ResultState\`. [Table en Figma](${figmaTable}) · [TableRow en Figma](${figmaRow}) · [TableCell en Figma](${figmaCell})`,
      },
    },
  },
  args: {
    caption: 'Vigencias fiscales',
    loading: false,
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
                <td livTableCell type="link" href="/vigencias/2026">2026</td>
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
 */
export const ConDatos: Story = {};

/**
 * `State=Loading`: `aria-busy="true"` y filas de `liv-skeleton` en lugar de los datos. Las filas cargando
 * son de la pantalla: aquí cada marcador va dentro de su celda, alineado con su columna.
 */
export const Cargando: Story = { args: { loading: true } };
