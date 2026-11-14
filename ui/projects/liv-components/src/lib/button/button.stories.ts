import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ICON_NAMES, IconName } from '../icon/icons';
import { ButtonComponent, ButtonReasonAlign, ButtonSize, ButtonVariant } from './button.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-298';
const figmaDisabledReason =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=65-456';
const disabledReasonText = 'Faltan 6 puntos obligatorios de la lista de preparación.';

interface ButtonArgs {
  variant: ButtonVariant;
  size: ButtonSize;
  disabled: boolean;
  loading: boolean;
  disabledReason?: string;
  reasonAlign: ButtonReasonAlign;
  leadingIcon?: IconName;
  trailingIcon?: IconName;
  loadingLabel: string;
  label: string;
}

const meta: Meta<ButtonArgs> = {
  title: 'Componentes/Button',
  component: ButtonComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [ButtonComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Botón con tres variantes (Primary es la acción principal, una por vista), dos tamaños (Md 40 px en la aplicación, Lg 48 px en los formularios de acceso y en móvil) e iconos opcionales. Hover y Focus son los del navegador: pasa el puntero o enfoca con Tab. En Loading no emite clic, sigue enfocable y, como en Figma, mantiene su texto y anuncia \`loadingLabel\`. Deshabilitado con \`disabledReason\` muestra el motivo debajo, enlazado con \`aria-describedby\`; el botón usa \`aria-disabled\` para seguir enfocable (pruébalo con Tab). [Diseño en Figma](${figma}) · [Deshabilitado con motivo](${figmaDisabledReason})`,
      },
    },
  },
  argTypes: {
    variant: { control: 'inline-radio', options: ['primary', 'secondary', 'ghost'] },
    size: { control: 'inline-radio', options: ['md', 'lg'] },
    reasonAlign: { control: 'inline-radio', options: ['start', 'end'] },
    leadingIcon: { control: 'select', options: [undefined, ...ICON_NAMES] },
    trailingIcon: { control: 'select', options: [undefined, ...ICON_NAMES] },
  },
  args: {
    variant: 'primary',
    size: 'md',
    disabled: false,
    loading: false,
    reasonAlign: 'start',
    loadingLabel: 'Cargando',
    label: 'Ingresar',
  },
  render: (args) => ({
    props: args,
    template: `
      <liv-button
        [variant]="variant"
        [size]="size"
        [disabled]="disabled"
        [loading]="loading"
        [disabledReason]="disabledReason"
        [reasonAlign]="reasonAlign"
        [leadingIcon]="leadingIcon"
        [trailingIcon]="trailingIcon"
        [loadingLabel]="loadingLabel"
        >{{ label }}</liv-button
      >
    `,
  }),
};

export default meta;
type Story = StoryObj<ButtonArgs>;

export const Primary: Story = {};

export const Secondary: Story = { args: { variant: 'secondary', label: 'Cancelar' } };

export const Ghost: Story = { args: { variant: 'ghost', label: 'Olvidé mi contraseña' } };

export const TamanoLg: Story = { args: { size: 'lg' } };

export const ConIconoInicial: Story = { args: { leadingIcon: 'log-out', label: 'Salir' } };

export const ConIconoFinal: Story = { args: { trailingIcon: 'arrow-right', label: 'Continuar' } };

export const Disabled: Story = { args: { disabled: true } };

export const SecondaryDisabled: Story = {
  args: { variant: 'secondary', disabled: true, label: 'Cancelar' },
};

export const GhostDisabled: Story = {
  args: { variant: 'ghost', disabled: true, label: 'Olvidé mi contraseña' },
};

/** Figma 65:456: el motivo a la izquierda, como en el detalle de la vigencia (68:3460). */
export const DisabledConMotivo: Story = {
  args: { disabled: true, label: 'Habilitar liquidación', disabledReason: disabledReasonText },
};

/** El motivo a la derecha, como en la lista de vigencias (66:2513). */
export const DisabledConMotivoAlFinal: Story = {
  args: {
    disabled: true,
    label: 'Crear vigencia',
    leadingIcon: 'plus',
    disabledReason: 'Ya existe la vigencia 2027, la última que se puede crear.',
    reasonAlign: 'end',
  },
};

export const SecondaryDisabledConMotivo: Story = {
  args: {
    variant: 'secondary',
    disabled: true,
    label: 'Cancelar',
    disabledReason: disabledReasonText,
  },
};

export const GhostDisabledConMotivo: Story = {
  args: {
    variant: 'ghost',
    disabled: true,
    label: 'Reintentar',
    disabledReason: disabledReasonText,
  },
};

export const Loading: Story = {
  args: { loading: true, label: 'Ingresar', loadingLabel: 'Ingresando…' },
};

export const LoadingLg: Story = {
  args: { loading: true, size: 'lg', label: 'Ingresar', loadingLabel: 'Ingresando…' },
};

export const SecondaryLoading: Story = {
  args: { variant: 'secondary', loading: true, label: 'Guardar', loadingLabel: 'Guardando…' },
};

export const GhostLoading: Story = {
  args: { variant: 'ghost', loading: true, label: 'Enviar', loadingLabel: 'Enviando…' },
};
