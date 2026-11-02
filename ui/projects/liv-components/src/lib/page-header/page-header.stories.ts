import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { PageHeaderComponent } from './page-header.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-360';

interface PageHeaderArgs {
  heading: string;
  breadcrumb: string[];
  description?: string;
}

const meta: Meta<PageHeaderArgs> = {
  title: 'Componentes/PageHeader',
  component: PageHeaderComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [PageHeaderComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Encabezado de cada pantalla: ruta de navegación, título (\`h1\`, uno por pantalla) y descripción opcional de máximo dos líneas. [Diseño en Figma](${figma})`,
      },
    },
  },
  args: {
    heading: 'Vigencias fiscales',
    breadcrumb: ['Administración', '1.4 Vigencias fiscales'],
    description: 'Años fiscales en los que se liquida el impuesto y sus fechas de cierre.',
  },
  render: (args) => ({
    props: args,
    template: `<liv-page-header [heading]="heading" [breadcrumb]="breadcrumb" [description]="description" />`,
  }),
};

export default meta;
type Story = StoryObj<PageHeaderArgs>;

export const Completo: Story = {};

export const Inicio: Story = {
  args: { heading: 'Inicio', breadcrumb: ['Inicio'], description: 'Elige un módulo para empezar.' },
};

export const SinRutaNiDescripcion: Story = {
  name: 'Solo título',
  args: { breadcrumb: [], description: undefined },
};

export const DescripcionLarga: Story = {
  name: 'Descripción de más de dos líneas',
  args: {
    description:
      'Esta descripción es deliberadamente larga para mostrar que el encabezado la limita a dos líneas y corta el resto con puntos suspensivos, de modo que el título y el contenido de la pantalla no se desplacen cuando el texto crece sin control en pantallas angostas o con tipografía más grande.',
  },
};
