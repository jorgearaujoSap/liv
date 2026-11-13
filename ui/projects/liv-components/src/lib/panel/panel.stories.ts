import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { DescriptionItemComponent } from '../description-item/description-item.component';
import { PanelComponent } from './panel.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=64-631';

interface PanelArgs {
  heading: string;
  description: string;
  headingLevel: 2 | 3;
}

const meta: Meta<PanelArgs> = {
  title: 'Componentes/Panel',
  component: PanelComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [PanelComponent, DescriptionItemComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Superficie con borde que agrupa un bloque de una pantalla: título, descripción opcional y el contenido, que se proyecta. Es una región (\`section\`) nombrada por su título; \`headingLevel\` elige \`h2\` (por omisión) o \`h3\` sin cambiar el aspecto. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    headingLevel: { control: 'inline-radio', options: [2, 3] },
  },
  args: {
    heading: 'Título del panel',
    description: 'Descripción breve de lo que contiene el panel.',
    headingLevel: 2,
  },
  render: (args) => ({
    props: args,
    template: `
      <liv-panel class="w-[1104px]" [heading]="heading" [description]="description" [headingLevel]="headingLevel">
        <p class="m-0 text-body-sm text-text-muted">Contenido del panel</p>
      </liv-panel>
    `,
  }),
};

export default meta;
type Story = StoryObj<PanelArgs>;

/** Con título, descripción y contenido (nodo 64:631). */
export const ConDescripcion: Story = {};

/** `Show description` apagado. */
export const SinDescripcion: Story = { args: { description: '' } };

export const TituloH3: Story = { args: { headingLevel: 3 } };

/** «Datos de la vigencia» (68:3846): un panel con la lista de descripción de la vigencia. */
export const DatosDeLaVigencia: Story = {
  parameters: { controls: { disable: true } },
  render: () => ({
    template: `
      <liv-panel class="w-[1104px]" heading="Datos de la vigencia" description="La vigencia va del 1 de enero al 31 de diciembre del año.">
        <dl class="m-0 grid grid-cols-5 gap-x-12 gap-y-4">
          <div livDescriptionItem label="Año" value="2027"></div>
          <div livDescriptionItem label="Inicio" value="1 de enero de 2027"></div>
          <div livDescriptionItem label="Fin" value="31 de diciembre de 2027"></div>
          <div livDescriptionItem type="badge" tone="neutral" label="Estado" value="Planeada"></div>
          <div livDescriptionItem label="Liquidación" value="No habilitada"></div>
        </dl>
      </liv-panel>
    `,
  }),
};
