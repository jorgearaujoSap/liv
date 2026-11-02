import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { CodeInputComponent } from './code-input.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-244';

interface CodeInputArgs {
  label: string;
  value: string;
  placeholder?: string;
  help?: string;
  error?: string;
}

const meta: Meta<CodeInputArgs> = {
  title: 'Componentes/CodeInput',
  component: CodeInputComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [CodeInputComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Código de 6 dígitos del segundo factor en un solo input (\`inputmode="numeric"\`, \`autocomplete="one-time-code"\`). Acepta pegar el código completo y descarta lo que no sea dígito. El estado Focus es el del navegador. [Diseño en Figma](${figma})`,
      },
    },
  },
  args: {
    label: 'Código de verificación',
    value: '482193',
    placeholder: '000000',
    help: 'El código cambia cada 30 segundos.',
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="max-w-[320px]">
        <liv-code-input
          [label]="label"
          [(value)]="value"
          [placeholder]="placeholder"
          [help]="help"
          [error]="error"
        />
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<CodeInputArgs>;

export const Filled: Story = {};

export const Placeholder: Story = { args: { value: '' } };

export const StateError: Story = {
  name: 'Error',
  args: {
    value: '123456',
    error: 'El código no es válido. Escribe el código que muestra tu aplicación.',
  },
};
