import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { BadgeComponent, BadgeTone } from './badge.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-384';

interface BadgeArgs {
  tone: BadgeTone;
  showDot: boolean;
  label: string;
}

const meta: Meta<BadgeArgs> = {
  title: 'Componentes/Badge',
  component: BadgeComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [BadgeComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Etiqueta de estado corta. El texto, que se proyecta como contenido, siempre dice el estado: el color no es la única señal. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    tone: {
      control: 'select',
      options: ['neutral', 'primary', 'success', 'warning', 'danger', 'info'],
    },
  },
  args: { tone: 'neutral', showDot: false, label: 'Próximamente' },
  render: (args) => ({
    props: args,
    template: `<liv-badge [tone]="tone" [showDot]="showDot">{{ label }}</liv-badge>`,
  }),
};

export default meta;
type Story = StoryObj<BadgeArgs>;

export const Neutral: Story = {};

export const Primary: Story = { args: { tone: 'primary', label: 'En revisión' } };

export const Success: Story = { args: { tone: 'success', label: 'Aprobado' } };

export const Warning: Story = { args: { tone: 'warning', label: 'Por vencer' } };

export const Danger: Story = { args: { tone: 'danger', label: 'Rechazado' } };

export const Info: Story = { args: { tone: 'info', label: 'Publicado' } };

export const ConPunto: Story = { args: { tone: 'success', label: 'Activo', showDot: true } };
