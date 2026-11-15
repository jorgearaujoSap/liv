import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ButtonComponent } from '../button/button.component';
import { ICON_NAMES, IconName } from '../icon/icons';
import { RadioGroupComponent } from '../radio-group/radio-group.component';
import { RadioOptionComponent } from '../radio-group/radio-option.component';
import { DialogComponent, DialogSize, DialogTone } from './dialog.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-354';
const figmaBody =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=73-482';
const figmaWarning =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=65-472';
const figmaPrimary =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=73-467';

interface DialogArgs {
  open: boolean;
  heading: string;
  description?: string;
  icon?: IconName;
  dismissible: boolean;
  size: DialogSize;
  tone: DialogTone;
  /** Año marcado en la historia con cuerpo; `null` es sin elección. */
  year: string | null;
}

const sessionActions = `
  <liv-button variant="secondary">Cerrar sesión</liv-button>
  <liv-button data-initial-focus>Seguir conectado</liv-button>
`;

const createActions = `
  <liv-radio-group livDialogBody legend="Año de la vigencia" [(value)]="year">
    <liv-radio-option value="2026" label="2026" description="Año en curso" />
    <liv-radio-option value="2027" label="2027" description="Año siguiente al año en curso" />
  </liv-radio-group>
  <liv-button variant="secondary">Cancelar</liv-button>
  <liv-button>Crear vigencia</liv-button>
`;

/** La plantilla de `liv-dialog` con todas sus entradas, o sin `size` ni `tone` (el uso por omisión). */
function dialogTemplate(content: string, bindSizeAndTone = true): string {
  const sizeAndTone = bindSizeAndTone ? '[size]="size" [tone]="tone"' : '';
  return `
    <liv-dialog
      [open]="open"
      [heading]="heading"
      [description]="description"
      [icon]="icon"
      [dismissible]="dismissible"
      ${sizeAndTone}
    >${content}</liv-dialog>
  `;
}

const meta: Meta<DialogArgs> = {
  title: 'Componentes/Dialog',
  component: DialogComponent,
  tags: ['autodocs'],
  decorators: [
    moduleMetadata({
      imports: [DialogComponent, ButtonComponent, RadioGroupComponent, RadioOptionComponent],
    }),
  ],
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 460 },
      description: {
        component: `Diálogo modal sobre \`bg/overlay\`. Atrapa el foco, lo devuelve al cerrarse y Escape emite \`closeRequested\` si \`dismissible\` es verdadero. Lo que lleva el atributo \`livDialogBody\` es el **cuerpo**, entre el texto y las acciones; todo lo demás son las acciones (sin cuerpo, el diálogo es el de siempre). \`size\` es \`md\` (440 px, por omisión) o \`lg\` (520 px); \`tone\`, el color del mosaico del icono: \`warning\` (por omisión, avisos como la sesión por expirar) o \`primary\` (una acción principal, como crear una vigencia). El foco inicial va al elemento con \`data-initial-focus\` o a la primera parada de Tab: un grupo de radios es una sola parada (el marcado o, si no hay ninguno, el primero al avanzar y el último al retroceder). [Diálogo en Figma](${figma}) · [Con cuerpo y tono](${figmaBody}) · [Warning](${figmaWarning}) · [Primary](${figmaPrimary})`,
      },
    },
  },
  argTypes: {
    icon: { control: 'select', options: [undefined, ...ICON_NAMES] },
    size: { control: 'inline-radio', options: ['md', 'lg'] },
    tone: { control: 'inline-radio', options: ['warning', 'primary'] },
  },
  args: {
    open: true,
    heading: 'Tu sesión está por expirar',
    description:
      'Por seguridad cerraremos tu sesión en 2 minutos por inactividad. ¿Quieres seguir conectado?',
    dismissible: true,
    size: 'md',
    tone: 'warning',
    year: '2026',
  },
  render: (args) => ({ props: args, template: dialogTemplate(sessionActions) }),
};

export default meta;
type Story = StoryObj<DialogArgs>;

/** Sin cuerpo, `size` ni `tone`: el aviso de sesión de siempre (440 px, tono de advertencia). */
export const SesionPorExpirar: Story = {
  name: 'Sesión por expirar',
  parameters: { controls: { exclude: ['size', 'tone', 'year'] } },
  render: (args) => ({ props: args, template: dialogTemplate(sessionActions, false) }),
};

export const SinDescripcion: Story = {
  args: { heading: 'Cerrar sesión', description: undefined, icon: 'log-out' },
};

export const NoSePuedeDescartar: Story = {
  name: 'Escape no cierra (acción crítica)',
  args: { dismissible: false },
};

/**
 * Con cuerpo, 520 px y tono primario, como el diálogo de crear una vigencia (Figma 73:467): un `RadioGroup`
 * proyectado con `livDialogBody`. El foco inicial va al año marcado.
 */
export const ConCuerpoYTonoPrimario: Story = {
  name: 'Con cuerpo (RadioGroup), lg y tono primario',
  args: {
    heading: 'Crear vigencia',
    description: 'Elige el año fiscal que vas a preparar.',
    icon: 'plus',
    size: 'lg',
    tone: 'primary',
  },
  render: (args) => ({ props: args, template: dialogTemplate(createActions) }),
};

/** Con cuerpo y tono de advertencia (Figma 65:472), 520 px, sin año elegido: el foco entra por el primer radio. */
export const ConCuerpoYTonoDeAdvertencia: Story = {
  name: 'Con cuerpo (RadioGroup), lg y advertencia, sin elegir',
  args: { ...ConCuerpoYTonoPrimario.args, tone: 'warning', year: null, icon: undefined },
  render: ConCuerpoYTonoPrimario.render,
};
