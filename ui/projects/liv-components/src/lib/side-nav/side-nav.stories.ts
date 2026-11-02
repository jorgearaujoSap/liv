import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { NavItemComponent } from '../nav-item/nav-item.component';
import { NavSectionComponent } from './nav-section.component';
import { SideNavComponent } from './side-nav.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-318';

interface SideNavArgs {
  label?: string;
  footer?: string;
}

const meta: Meta<SideNavArgs> = {
  title: 'Componentes/SideNav',
  component: SideNavComponent,
  tags: ['autodocs'],
  decorators: [
    moduleMetadata({ imports: [SideNavComponent, NavSectionComponent, NavItemComponent] }),
  ],
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 640 },
      description: {
        component: `Menú lateral de 272 px: logotipo, opciones (\`liv-nav-item\`) agrupadas con etiquetas de sección (\`liv-nav-section\`) y un pie con la versión. Es la región de navegación principal. Las opciones aún no construidas aparecen deshabilitadas. [Diseño en Figma](${figma})`,
      },
    },
  },
  args: { footer: 'LIV · versión 0.1.0' },
  render: (args) => ({
    props: args,
    template: `
      <div class="h-screen">
        <liv-side-nav [label]="label" [footer]="footer">
          <liv-nav-item icon="house" href="#inicio" [active]="true">Inicio</liv-nav-item>
          <liv-nav-section>Módulos</liv-nav-section>
          <liv-nav-item type="group" icon="settings" [expanded]="true">
            Administración
            <liv-nav-item type="subitem" href="#vigencias">Vigencias fiscales</liv-nav-item>
            <liv-nav-item type="subitem" href="#tablas">Tablas base</liv-nav-item>
          </liv-nav-item>
          <liv-nav-item icon="car" [disabled]="true">Registro automotor y contribuyentes</liv-nav-item>
          <liv-nav-item icon="calculator" [disabled]="true">Liquidación</liv-nav-item>
          <liv-nav-item icon="wallet" [disabled]="true">Cuenta corriente y recaudo</liv-nav-item>
          <liv-nav-item icon="hand-coins" [disabled]="true">Cartera</liv-nav-item>
          <liv-nav-item icon="gavel" [disabled]="true">Cobro</liv-nav-item>
          <liv-nav-item icon="lock" [disabled]="true">Medidas cautelares</liv-nav-item>
          <liv-nav-item icon="folder-open" [disabled]="true">Documentos y notificaciones</liv-nav-item>
          <liv-nav-item icon="shield-check" [disabled]="true">Auditoría e integraciones</liv-nav-item>
        </liv-side-nav>
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<SideNavArgs>;

export const Completo: Story = {};

export const SinPie: Story = { args: { footer: undefined } };
