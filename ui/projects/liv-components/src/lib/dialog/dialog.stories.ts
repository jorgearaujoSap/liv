import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ButtonComponent } from '../button/button.component';
import { ICON_NAMES, IconName } from '../icon/icons';
import { DialogComponent } from './dialog.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-354';

interface DialogArgs {
  open: boolean;
  heading: string;
  description?: string;
  icon?: IconName;
  dismissible: boolean;
}

const meta: Meta<DialogArgs> = {
  title: 'Componentes/Dialog',
  component: DialogComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [DialogComponent, ButtonComponent] })],
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 360 },
      description: {
        component: `Diálogo modal sobre \`bg/overlay\`. Atrapa el foco, lo devuelve al cerrarse y Escape emite \`closed\` si \`dismissible\` es verdadero. Las acciones se proyectan como contenido; el foco inicial va al elemento con \`data-initial-focus\` o al primer enfocable. Se usa, entre otros, para el aviso de sesión por expirar (WCAG 2.2.1). [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    icon: { control: 'select', options: [undefined, ...ICON_NAMES] },
  },
  args: {
    open: true,
    heading: 'Tu sesión está por expirar',
    description:
      'Por seguridad cerraremos tu sesión en 2 minutos por inactividad. ¿Quieres seguir conectado?',
    dismissible: true,
  },
  render: (args) => ({
    props: args,
    template: `
      <liv-dialog
        [open]="open"
        [heading]="heading"
        [description]="description"
        [icon]="icon"
        [dismissible]="dismissible"
      >
        <liv-button variant="secondary">Cerrar sesión</liv-button>
        <liv-button data-initial-focus>Seguir conectado</liv-button>
      </liv-dialog>
    `,
  }),
};

export default meta;
type Story = StoryObj<DialogArgs>;

export const SesionPorExpirar: Story = {
  name: 'Sesión por expirar',
};

export const SinDescripcion: Story = {
  args: { heading: 'Cerrar sesión', description: undefined, icon: 'log-out' },
};

export const NoSePuedeDescartar: Story = {
  name: 'Escape no cierra (acción crítica)',
  args: { dismissible: false },
};
