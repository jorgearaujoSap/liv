import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ICON_NAMES, IconName } from '../icon/icons';
import { NavItemComponent, NavItemType } from './nav-item.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-156';
const figmaMenu =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=51-1519';

interface NavItemArgs {
  type: NavItemType;
  icon?: IconName;
  href?: string;
  active: boolean;
  disabled: boolean;
  expanded: boolean;
  label: string;
}

const meta: Meta<NavItemArgs> = {
  title: 'Componentes/NavItem',
  component: NavItemComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [NavItemComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Opción del menú lateral. \`item\`, \`subitem\` y \`nested\` son enlaces: un clic normal emite \`navigate\` y la aplicación navega con su enrutador. \`group\` (módulo) y \`subgroup\` (opción de segundo nivel, como 1.5 y 1.6) son botones que expanden las opciones que contienen: el menú llega a tres niveles. Activo marca la pantalla actual (\`aria-current="page"\`); deshabilitado es una opción aún no construida: no navega y lo explica con un tooltip. Hover y Focus son los del navegador (fondo \`bg-subtle\` y anillo \`focus/ring\`). [Diseño en Figma](${figma}) · [Menú de tres niveles](${figmaMenu})`,
      },
    },
  },
  argTypes: {
    type: { control: 'inline-radio', options: ['item', 'group', 'subgroup', 'subitem', 'nested'] },
    icon: { control: 'select', options: [undefined, ...ICON_NAMES] },
  },
  args: {
    type: 'item',
    icon: 'house',
    href: '#inicio',
    active: false,
    disabled: false,
    expanded: false,
    label: 'Inicio',
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="w-[240px]">
        <liv-nav-item
          [type]="type"
          [icon]="icon"
          [href]="href"
          [active]="active"
          [disabled]="disabled"
          [expanded]="expanded"
        >{{ label }}</liv-nav-item>
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<NavItemArgs>;

export const Item: Story = {};

export const ItemActivo: Story = { name: 'Item · activo', args: { active: true } };

export const ItemDeshabilitado: Story = {
  name: 'Item · deshabilitado',
  args: { disabled: true, href: undefined, label: 'Liquidación', icon: 'calculator' },
};

export const TextoLargo: Story = {
  name: 'Item · texto largo en dos líneas',
  args: { label: 'Registro automotor y contribuyentes', icon: 'car' },
};

export const Group: Story = {
  args: { type: 'group', icon: 'settings', href: undefined, label: 'Administración' },
};

export const GroupActivo: Story = {
  name: 'Group · activo',
  args: { type: 'group', icon: 'settings', href: undefined, active: true, label: 'Administración' },
};

export const GroupDeshabilitado: Story = {
  name: 'Group · deshabilitado',
  args: { type: 'group', icon: 'wallet', href: undefined, disabled: true, label: 'Cartera' },
};

export const Subitem: Story = { args: { type: 'subitem', label: 'Vigencias fiscales' } };

export const SubitemActivo: Story = {
  name: 'Subitem · activo',
  args: { type: 'subitem', active: true, label: 'Vigencias fiscales' },
};

export const Subgroup: Story = {
  name: 'Subgroup · opción de segundo nivel',
  args: {
    type: 'subgroup',
    icon: undefined,
    href: undefined,
    label: 'Cargue de tablas base por vigencia',
  },
};

export const SubgroupActivo: Story = {
  name: 'Subgroup · activo (solo el texto)',
  args: {
    type: 'subgroup',
    icon: undefined,
    href: undefined,
    active: true,
    label: 'Cargue de tablas base por vigencia',
  },
};

export const SubgroupDeshabilitado: Story = {
  name: 'Subgroup · deshabilitado',
  args: {
    type: 'subgroup',
    icon: undefined,
    href: undefined,
    disabled: true,
    label: 'Consultas y exportación de tablas base',
  },
};

export const Nested: Story = {
  name: 'Nested · opción de tercer nivel',
  args: { type: 'nested', icon: undefined, label: 'Cargar archivo de avalúos' },
};

export const NestedActivo: Story = {
  name: 'Nested · activo',
  args: { type: 'nested', icon: undefined, active: true, label: 'Cargar archivo de avalúos' },
};

export const NestedDeshabilitado: Story = {
  name: 'Nested · deshabilitado',
  args: {
    type: 'nested',
    icon: undefined,
    href: undefined,
    disabled: true,
    label: 'Calendario tributario',
  },
};

export const SubgroupExpandido: Story = {
  name: 'Subgroup · expandido con opciones de tercer nivel',
  render: () => ({
    template: `
      <div class="w-[240px]">
        <liv-nav-item type="subgroup" [expanded]="true">
          Cargue de tablas base por vigencia
          <liv-nav-item type="nested" href="#asistente">Asistente de preparación de vigencia</liv-nav-item>
          <liv-nav-item type="nested" href="#catalogos">Catálogos de avalúos</liv-nav-item>
        </liv-nav-item>
      </div>
    `,
  }),
};

export const TresNiveles: Story = {
  name: 'Tres niveles · módulo, subgrupo y opciones',
  render: () => ({
    template: `
      <div class="w-[240px]">
        <liv-nav-item type="group" icon="settings" [expanded]="true" [active]="true">
          Administración
          <liv-nav-item type="subitem" href="#vigencias">Vigencias fiscales</liv-nav-item>
          <liv-nav-item type="subgroup" [expanded]="true" [active]="true">
            Cargue de tablas base por vigencia
            <liv-nav-item type="nested" href="#asistente">Asistente de preparación de vigencia</liv-nav-item>
            <liv-nav-item type="nested" href="#carga" [active]="true">Cargar archivo de avalúos</liv-nav-item>
            <liv-nav-item type="nested" [disabled]="true">Calendario tributario</liv-nav-item>
          </liv-nav-item>
          <liv-nav-item type="subgroup">
            Consultas y exportación de tablas base
            <liv-nav-item type="nested" href="#lotes">Consulta de lotes de importación</liv-nav-item>
          </liv-nav-item>
          <liv-nav-item type="subitem" [disabled]="true">Parámetros generales</liv-nav-item>
        </liv-nav-item>
      </div>
    `,
  }),
};

export const GroupExpandido: Story = {
  name: 'Group · expandido con subopciones',
  render: () => ({
    template: `
      <div class="w-[240px]">
        <liv-nav-item type="group" icon="settings" [expanded]="true" [active]="true">
          Administración
          <liv-nav-item type="subitem" href="#vigencias" [active]="true">Vigencias fiscales</liv-nav-item>
          <liv-nav-item type="subitem" href="#tablas">Tablas base</liv-nav-item>
        </liv-nav-item>
      </div>
    `,
  }),
};
