import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { BadgeTone } from '../badge/badge.component';
import { DescriptionItemComponent, DescriptionItemType } from './description-item.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=64-583';

interface DescriptionItemArgs {
  type: DescriptionItemType;
  label: string;
  value: string;
  tone: BadgeTone;
}

const meta: Meta<DescriptionItemArgs> = {
  title: 'Componentes/DescriptionItem',
  component: DescriptionItemComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [DescriptionItemComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Par etiqueta–valor de solo lectura. Es un \`<div livDescriptionItem>\` dentro de una lista de descripción (\`<dl>\`): la etiqueta es el \`dt\` y el valor, el \`dd\`. \`Type=Badge\` muestra el valor como estado; su \`tone\` (\`primary\` por omisión, el del diseño) lo elige la pantalla según el estado. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    type: { control: 'inline-radio', options: ['text', 'badge'] },
    tone: {
      control: 'select',
      options: ['neutral', 'primary', 'success', 'warning', 'danger', 'info'],
    },
  },
  args: { type: 'text', label: 'Año', value: '2026', tone: 'primary' },
  render: (args) => ({
    props: args,
    template: `
      <dl class="m-0">
        <div livDescriptionItem [type]="type" [label]="label" [value]="value" [tone]="tone"></div>
      </dl>
    `,
  }),
};

export default meta;
type Story = StoryObj<DescriptionItemArgs>;

/** `Type=Text`. */
export const Texto: Story = {};

/** `Type=Badge`, con el tono del diseño (`primary`). */
export const ConBadge: Story = {
  args: { type: 'badge', label: 'Estado', value: 'En preparación' },
};

export const ConBadgeNeutro: Story = {
  args: { type: 'badge', label: 'Estado', value: 'Planeada', tone: 'neutral' },
};

/** Los cinco datos de una vigencia en una cuadrícula, como en el detalle (68:3846). */
export const DatosDeUnaVigencia: Story = {
  parameters: { controls: { disable: true } },
  render: () => ({
    template: `
      <dl class="m-0 grid grid-cols-5 gap-x-12 gap-y-4">
        <div livDescriptionItem label="Año" value="2027"></div>
        <div livDescriptionItem label="Inicio" value="1 de enero de 2027"></div>
        <div livDescriptionItem label="Fin" value="31 de diciembre de 2027"></div>
        <div livDescriptionItem type="badge" tone="neutral" label="Estado" value="Planeada"></div>
        <div livDescriptionItem label="Liquidación" value="No habilitada"></div>
      </dl>
    `,
  }),
};
