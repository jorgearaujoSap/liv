import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ICON_NAMES, IconName } from '../icon/icons';
import { ModuleCardComponent } from './module-card.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-220';

interface ModuleCardArgs {
  heading: string;
  description: string;
  icon: IconName;
  href?: string;
  disabled: boolean;
  disabledLabel: string;
}

const meta: Meta<ModuleCardArgs> = {
  title: 'Componentes/ModuleCard',
  component: ModuleCardComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [ModuleCardComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Acceso a un módulo desde el inicio: toda la tarjeta es el enlace. Hover es el del navegador (borde y sombra mayores). Deshabilitada es un módulo aún no disponible: muestra la insignia, no navega y declara \`aria-disabled\`. Solo se muestran los módulos que permiten las capacidades del usuario. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    icon: { control: 'select', options: ICON_NAMES },
  },
  args: {
    heading: 'Administración',
    description: 'Entidades, usuarios, normas, vigencias y tablas base de liquidación.',
    icon: 'settings',
    href: '#administracion',
    disabled: false,
    disabledLabel: 'Próximamente',
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="w-[340px]">
        <liv-module-card
          [heading]="heading"
          [description]="description"
          [icon]="icon"
          [href]="href"
          [disabled]="disabled"
          [disabledLabel]="disabledLabel"
        />
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<ModuleCardArgs>;

export const Default: Story = {};

export const Deshabilitada: Story = {
  args: {
    heading: 'Liquidación',
    description: 'Cálculo individual y masivo del impuesto de cada vehículo.',
    icon: 'calculator',
    href: undefined,
    disabled: true,
  },
};

export const Cuadricula: Story = {
  name: 'En cuadrícula',
  render: () => ({
    template: `
      <div class="grid grid-cols-3 gap-6">
        <liv-module-card heading="Administración" description="Entidades, usuarios, normas, vigencias y tablas base de liquidación." icon="settings" href="#administracion" />
        <liv-module-card heading="Liquidación" description="Cálculo individual y masivo del impuesto de cada vehículo." icon="calculator" [disabled]="true" />
        <liv-module-card heading="Cuenta corriente y recaudo" description="Obligaciones, pagos, conciliación y saldos a favor." icon="wallet" [disabled]="true" />
      </div>
    `,
  }),
};
