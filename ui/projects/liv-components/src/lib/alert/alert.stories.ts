import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { AlertComponent, AlertStatus } from './alert.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-277';

interface AlertArgs {
  status: AlertStatus;
  heading?: string;
  message: string;
}

const meta: Meta<AlertArgs> = {
  title: 'Componentes/Alert',
  component: AlertComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [AlertComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Mensaje en línea con icono y fondo suave. El título dice qué pasó y el mensaje, proyectado como contenido, qué hacer. Error usa \`role="alert"\`; los demás \`role="status"\`. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    status: { control: 'inline-radio', options: ['error', 'warning', 'info', 'success'] },
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="max-w-[400px]">
        <liv-alert [status]="status" [heading]="heading">{{ message }}</liv-alert>
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<AlertArgs>;

export const StatusError: Story = {
  name: 'Error',
  args: {
    status: 'error',
    heading: 'No pudimos iniciar tu sesión',
    message: 'Revisa el usuario y la contraseña e inténtalo de nuevo.',
  },
};

export const Warning: Story = {
  args: {
    status: 'warning',
    heading: 'Tu contraseña vence pronto',
    message: 'Cámbiala antes del 30 de noviembre para no perder el acceso.',
  },
};

export const Info: Story = {
  args: {
    status: 'info',
    heading: 'Sesión cerrada',
    message: 'Cerraste tu sesión. Ingresa de nuevo para continuar.',
  },
};

export const Success: Story = {
  args: {
    status: 'success',
    heading: 'Segundo factor configurado',
    message: 'Desde ahora te pediremos el código de tu aplicación al ingresar.',
  },
};

export const SinTitulo: Story = {
  args: { status: 'info', message: 'Mensaje para el usuario, en una o dos frases.' },
};
