import { type Meta, type StoryObj } from '@storybook/angular';
import { ICON_NAMES } from '../icon/icons';
import { IconButtonComponent } from './icon-button.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-364';

const meta: Meta<IconButtonComponent> = {
  title: 'Componentes/IconButton',
  component: IconButtonComponent,
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: `Botón de solo icono (32 o 40 px). Su \`label\` en español es el \`aria-label\` y el tooltip. En pantallas táctiles el área de toque crece a 44 px. Hover y Focus son los del navegador. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    icon: { control: 'select', options: ICON_NAMES },
    variant: { control: 'inline-radio', options: ['ghost', 'secondary'] },
    size: { control: 'inline-radio', options: ['sm', 'md'] },
  },
  args: { icon: 'eye', label: 'Mostrar contraseña', variant: 'ghost', size: 'sm', disabled: false },
};

export default meta;
type Story = StoryObj<IconButtonComponent>;

export const Ghost: Story = {};

export const GhostMd: Story = { args: { size: 'md' } };

export const Secondary: Story = { args: { variant: 'secondary' } };

export const SecondaryMd: Story = { args: { variant: 'secondary', size: 'md' } };

export const GhostDisabled: Story = { args: { disabled: true } };

export const SecondaryDisabled: Story = { args: { variant: 'secondary', disabled: true } };
