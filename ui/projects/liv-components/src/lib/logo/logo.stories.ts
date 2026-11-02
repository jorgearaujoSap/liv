import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { LogoComponent } from './logo.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-228';

const meta: Meta<LogoComponent> = {
  title: 'Componentes/Logo',
  component: LogoComponent,
  tags: ['autodocs'],
  parameters: {
    design: { type: 'figma', url: figma },
    docs: {
      description: {
        component: `Logotipo de LIV: monograma y nombre del producto. Provisional hasta tener la identidad institucional. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    tone: { control: 'inline-radio', options: ['default', 'inverse'] },
  },
  args: { tone: 'default', showWordmark: true },
};

export default meta;
type Story = StoryObj<LogoComponent>;

export const Default: Story = {};

/** El tono Inverse se usa sobre `bg-inverse`. */
const sobreFondoOscuro: Story['render'] = (args) => ({
  props: args,
  template: `
    <div class="bg-bg-inverse p-6">
      <liv-logo [tone]="tone" [showWordmark]="showWordmark" />
    </div>
  `,
});

export const SinNombre: Story = { args: { showWordmark: false } };

export const Inverse: Story = {
  args: { tone: 'inverse' },
  decorators: [moduleMetadata({ imports: [LogoComponent] })],
  render: sobreFondoOscuro,
};

export const InverseSinNombre: Story = {
  args: { tone: 'inverse', showWordmark: false },
  decorators: [moduleMetadata({ imports: [LogoComponent] })],
  render: sobreFondoOscuro,
};
