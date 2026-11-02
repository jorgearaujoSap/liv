import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { IconButtonComponent } from '../icon-button/icon-button.component';
import { ICON_NAMES, IconName } from '../icon/icons';
import { TextFieldComponent, TextFieldType } from './text-field.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-182';

interface TextFieldArgs {
  label: string;
  value: string;
  type: TextFieldType;
  placeholder?: string;
  help?: string;
  error?: string;
  leadingIcon?: IconName;
  disabled: boolean;
  showAction: boolean;
}

const meta: Meta<TextFieldArgs> = {
  title: 'Componentes/TextField',
  component: TextFieldComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [TextFieldComponent, IconButtonComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Campo de texto de 44 px con la etiqueta siempre visible, icono inicial opcional y acción final opcional (un \`liv-icon-button\` proyectado). El estado Focus es el del navegador: enfoca el campo. Con \`error\` expone \`aria-invalid\` y el mensaje queda enlazado con \`aria-describedby\`. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    type: {
      control: 'select',
      options: ['text', 'password', 'email', 'search', 'tel', 'url'],
    },
    leadingIcon: { control: 'select', options: [undefined, ...ICON_NAMES] },
  },
  args: {
    label: 'Usuario',
    value: 'lmartinez',
    type: 'text',
    leadingIcon: 'user',
    disabled: false,
    showAction: false,
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="max-w-[320px]">
        <liv-text-field
          [label]="label"
          [(value)]="value"
          [type]="type"
          [placeholder]="placeholder"
          [help]="help"
          [error]="error"
          [leadingIcon]="leadingIcon"
          [disabled]="disabled"
        >
          @if (showAction) {
            <liv-icon-button
              [icon]="type === 'password' ? 'eye' : 'eye-off'"
              [label]="type === 'password' ? 'Mostrar contraseña' : 'Ocultar contraseña'"
              [disabled]="disabled"
              (click)="type = type === 'password' ? 'text' : 'password'"
            />
          }
        </liv-text-field>
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<TextFieldArgs>;

export const Filled: Story = {};

export const Placeholder: Story = { args: { value: '', placeholder: 'lmartinez' } };

export const ConAyuda: Story = { args: { help: 'Es el usuario que te asignó tu entidad.' } };

export const StateError: Story = {
  name: 'Error',
  args: { value: '', error: 'Escribe tu usuario.' },
};

export const Disabled: Story = { args: { disabled: true } };

export const ConAccionFinal: Story = {
  args: {
    label: 'Contraseña',
    value: 'secreta',
    type: 'password',
    leadingIcon: 'lock',
    showAction: true,
  },
};

export const SinIcono: Story = { args: { leadingIcon: undefined } };
