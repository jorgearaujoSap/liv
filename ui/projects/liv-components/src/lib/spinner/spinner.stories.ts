import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { SpinnerComponent } from './spinner.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-8';

const meta: Meta<SpinnerComponent> = {
  title: 'Componentes/Spinner',
  component: SpinnerComponent,
  tags: ['autodocs'],
  parameters: {
    design: { type: 'figma', url: figma },
    docs: {
      description: {
        component: `Carga indeterminada de 20 px, escalable a 16 o 24. Siempre dice qué se carga (texto solo para lectores de pantalla) y no gira con prefers-reduced-motion. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    tone: { control: 'inline-radio', options: ['default', 'inverse'] },
    size: { control: 'inline-radio', options: [16, 20, 24] },
  },
  args: { tone: 'default', size: 20, label: 'Cargando' },
};

export default meta;
type Story = StoryObj<SpinnerComponent>;

export const Default: Story = {};

/** El tono Inverse se usa dentro de botones primarios. */
export const Inverse: Story = {
  args: { tone: 'inverse', label: 'Ingresando' },
  decorators: [moduleMetadata({ imports: [SpinnerComponent] })],
  render: (args) => ({
    props: args,
    template: `
      <div class="rounded-md bg-action-primary p-3">
        <liv-spinner [tone]="tone" [size]="size" [label]="label" />
      </div>
    `,
  }),
};

export const Tamano16: Story = { args: { size: 16 } };

export const Tamano24: Story = { args: { size: 24 } };
