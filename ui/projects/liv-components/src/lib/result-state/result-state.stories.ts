import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ButtonComponent } from '../button/button.component';
import { ICON_NAMES, IconName } from '../icon/icons';
import { ResultStateComponent, ResultStateTone } from './result-state.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-324';

interface ResultStateArgs {
  tone: ResultStateTone;
  icon: IconName;
  eyebrow?: string;
  heading: string;
  headingLevel: 1 | 2 | 3;
  description?: string;
  secondaryAction: boolean;
}

const meta: Meta<ResultStateArgs> = {
  title: 'Componentes/ResultState',
  component: ResultStateComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [ResultStateComponent, ButtonComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Página o bloque de resultado: sin permiso (403), error de carga, sesión terminada o vacío. Tres tonos, icono grande, título que dice qué pasó, descripción que dice qué hacer y una o dos acciones proyectadas como contenido. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    tone: { control: 'inline-radio', options: ['danger', 'warning', 'info'] },
    icon: { control: 'select', options: ICON_NAMES },
    headingLevel: { control: 'inline-radio', options: [1, 2, 3] },
  },
  args: {
    tone: 'danger',
    icon: 'shield-x',
    eyebrow: 'Error 403',
    heading: 'No tienes permiso para ver esta página',
    headingLevel: 2,
    description:
      'Tu rol no incluye esta opción. Si necesitas acceso, solicítalo al administrador de tu entidad.',
    secondaryAction: false,
  },
  render: (args) => ({
    props: args,
    template: `
      <liv-result-state
        [tone]="tone"
        [icon]="icon"
        [eyebrow]="eyebrow"
        [heading]="heading"
        [headingLevel]="headingLevel"
        [description]="description"
      >
        @if (secondaryAction) {
          <liv-button variant="secondary">Cerrar sesión</liv-button>
        }
        <liv-button>Volver al inicio</liv-button>
      </liv-result-state>
    `,
  }),
};

export default meta;
type Story = StoryObj<ResultStateArgs>;

export const SinPermiso: Story = { name: 'Danger · sin permiso (403)' };

export const ConDosAcciones: Story = {
  name: 'Danger · con acción secundaria',
  args: {
    icon: 'cloud-off',
    eyebrow: undefined,
    heading: 'No pudimos cargar tu sesión',
    description: 'Revisa tu conexión e inténtalo de nuevo. Si el problema sigue, avisa a soporte.',
    secondaryAction: true,
  },
};

export const Warning: Story = {
  args: {
    tone: 'warning',
    icon: 'triangle-alert',
    eyebrow: undefined,
    heading: 'Tu sesión terminó',
    description:
      'Por seguridad cerramos tu sesión por inactividad. Ingresa de nuevo para continuar.',
  },
};

export const Info: Story = {
  name: 'Info · próximamente',
  args: {
    tone: 'info',
    icon: 'history',
    eyebrow: 'Próximamente',
    heading: 'Esta opción aún no está disponible',
    description: 'Estamos trabajando en ella. Te avisaremos cuando puedas usarla.',
  },
};
