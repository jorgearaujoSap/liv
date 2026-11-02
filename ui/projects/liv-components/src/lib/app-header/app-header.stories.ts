import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { AppHeaderComponent } from './app-header.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-354';

interface AppHeaderArgs {
  administration: string;
  userName: string;
  role?: string;
}

const meta: Meta<AppHeaderArgs> = {
  title: 'Componentes/AppHeader',
  component: AppHeaderComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [AppHeaderComponent] })],
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component: `Barra superior de 64 px: la administración activa a la izquierda; el avatar con las iniciales, el nombre, el rol y "Salir" a la derecha. "Salir" solo emite \`signOut\`: la aplicación cierra la sesión en LIV y en el proveedor de identidad. [Diseño en Figma](${figma})`,
      },
    },
  },
  args: {
    administration: 'Municipio de Ipiales',
    userName: 'Laura Martínez',
    role: 'Administración del sistema',
  },
  render: (args) => ({
    props: args,
    template: `<liv-app-header [administration]="administration" [userName]="userName" [role]="role" />`,
  }),
};

export default meta;
type Story = StoryObj<AppHeaderArgs>;

export const Completo: Story = {};

export const SinRol: Story = { args: { role: undefined } };

export const NombresLargos: Story = {
  args: {
    administration: 'Gobernación del Departamento de Nariño — Secretaría de Hacienda',
    userName: 'María del Carmen Rodríguez Ordóñez',
    role: 'Liquidador de impuesto vehicular',
  },
};
