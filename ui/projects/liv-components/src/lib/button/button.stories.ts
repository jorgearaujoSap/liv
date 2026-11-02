import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ICON_NAMES, IconName } from '../icon/icons';
import { ButtonComponent, ButtonSize, ButtonVariant } from './button.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-298';

interface ButtonArgs {
  variant: ButtonVariant;
  size: ButtonSize;
  disabled: boolean;
  loading: boolean;
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
        component: `Botón con tres variantes (Primary es la acción principal, una por vista), dos tamaños (Md 40 px en la aplicación, Lg 48 px en los formularios de acceso y en móvil) e iconos opcionales. Hover y Focus son los del navegador: pasa el puntero o enfoca con Tab. En Loading no emite clic, sigue enfocable y, como en Figma, mantiene su texto y anuncia \`loadingLabel\`. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    variant: { control: 'inline-radio', options: ['primary', 'secondary', 'ghost'] },
    size: { control: 'inline-radio', options: ['md', 'lg'] },
    leadingIcon: { control: 'select', options: [undefined, ...ICON_NAMES] },
    trailingIcon: { control: 'select', options: [undefined, ...ICON_NAMES] },
  },
  args: {
    variant: 'primary',
    size: 'md',
    disabled: false,
    loading: false,
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
