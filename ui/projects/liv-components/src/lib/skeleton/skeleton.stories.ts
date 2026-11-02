import type { Meta, StoryObj } from '@storybook/angular';
import { SkeletonComponent } from './skeleton.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-397';

const meta: Meta<SkeletonComponent> = {
  title: 'Componentes/Skeleton',
  component: SkeletonComponent,
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: `Marcador de contenido mientras carga. Es decorativo: el contenedor lleva aria-busy="true". Pulsa suavemente salvo con prefers-reduced-motion. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    shape: { control: 'inline-radio', options: ['text', 'circle', 'block'] },
  },
  args: { shape: 'text' },
};

export default meta;
type Story = StoryObj<SkeletonComponent>;

export const Text: Story = {};

export const Circle: Story = { args: { shape: 'circle' } };

export const Block: Story = { args: { shape: 'block' } };

/** Se redimensiona según el contenido real que reemplaza. */
export const ConMedidasPropias: Story = {
  args: { shape: 'block', width: '20rem', height: '5rem' },
};
