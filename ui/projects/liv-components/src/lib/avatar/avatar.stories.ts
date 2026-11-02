import type { Meta, StoryObj } from '@storybook/angular';
import { AvatarComponent } from './avatar.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-392';

const meta: Meta<AvatarComponent> = {
  title: 'Componentes/Avatar',
  component: AvatarComponent,
  tags: ['autodocs'],
  parameters: {
    docs: {
      description: {
        component: `Iniciales del usuario (24, 32 o 40 px). Es decorativo: el nombre completo siempre aparece junto a él. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    size: { control: 'inline-radio', options: ['sm', 'md', 'lg'] },
  },
  args: { initials: 'LM', size: 'sm' },
};

export default meta;
type Story = StoryObj<AvatarComponent>;

export const Sm: Story = {};

export const Md: Story = { args: { size: 'md' } };

export const Lg: Story = { args: { size: 'lg' } };
