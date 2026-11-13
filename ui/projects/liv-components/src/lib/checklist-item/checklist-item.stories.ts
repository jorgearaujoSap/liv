import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { ChecklistItemComponent, ChecklistItemStatus } from './checklist-item.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=64-573';

interface ChecklistItemArgs {
  status: ChecklistItemStatus;
  number: string;
  heading: string;
  reason: string;
  optional: boolean;
  link: string;
  linkHref: string;
}

const meta: Meta<ChecklistItemArgs> = {
  title: 'Componentes/ChecklistItem',
  component: ChecklistItemComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [ChecklistItemComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Punto de una lista de verificación, como la lista de preparación de una vigencia. Es un \`<li livChecklistItem>\` dentro de un \`<ol role="list">\`. El estado nunca va solo en el icono: el Badge de la derecha lo dice con texto. Un motivo o un enlace vacíos no se muestran; sin texto o sin destino no hay enlace. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    status: { control: 'inline-radio', options: ['met', 'pending', 'observed'] },
  },
  args: {
    status: 'met',
    number: '1.',
    heading: 'Vigencia en preparación',
    reason: 'La vigencia aún no está en preparación.',
    optional: false,
    link: '',
    linkHref: '',
  },
  render: (args) => ({
    props: args,
    template: `
      <ol role="list" class="m-0 w-[720px] list-none p-0">
        <li
          livChecklistItem
          [status]="status"
          [number]="number"
          [heading]="heading"
          [reason]="reason"
          [optional]="optional"
          [link]="link"
          [linkHref]="linkHref"
        ></li>
      </ol>
    `,
  }),
};

export default meta;
type Story = StoryObj<ChecklistItemArgs>;

/** `Status=Met`: cumplido (`circle-check` en `status/success`). */
export const Cumplido: Story = {};

/** `Status=Pending`: pendiente (círculo vacío en `text/muted`). */
export const Pendiente: Story = { args: { status: 'pending' } };

/** `Status=Observed`: con observación (`triangle-alert` en `status/warning`). La 007 aún no lo usa. */
export const ConObservacion: Story = { args: { status: 'observed' } };

export const Opcional: Story = {
  args: { status: 'pending', heading: 'Beneficios configurados', number: '6.', optional: true },
};

export const ConEnlace: Story = {
  args: {
    status: 'pending',
    reason: 'Opción aún no disponible',
    link: 'Ir a 1.5.2 · Catálogos de avalúos',
    linkHref: '/administracion/avaluos/catalogos',
  },
};

export const SinMotivo: Story = { args: { reason: '' } };

/** Los tres estados en la misma lista, como en el nodo 64:573. */
export const Estados: Story = {
  parameters: { controls: { disable: true } },
  render: () => ({
    template: `
      <ol role="list" class="m-0 w-[720px] list-none p-0">
        <li livChecklistItem status="met" number="1." heading="Vigencia en preparación" reason="La vigencia aún no está en preparación."></li>
        <li livChecklistItem status="pending" number="2." heading="Vigencia en preparación" reason="La vigencia aún no está en preparación."></li>
        <li livChecklistItem status="observed" number="3." heading="Vigencia en preparación" reason="La vigencia aún no está en preparación."></li>
      </ol>
    `,
  }),
};

/** La lista de preparación de una vigencia *Planeada* (68:3846): nueve puntos, el 6 y el 7 opcionales. */
export const ListaDePreparacion: Story = {
  parameters: { controls: { disable: true } },
  render: () => ({
    template: `
      <ol role="list" class="m-0 w-[960px] list-none p-0">
        <li livChecklistItem status="pending" number="1." heading="Vigencia en preparación" reason="La vigencia está Planeada: abre su preparación para empezar a cargar sus tablas."></li>
        <li livChecklistItem status="pending" number="2." heading="Resolución de avalúos registrada con su PDF" reason="Opción aún no disponible"></li>
        <li livChecklistItem status="pending" number="6." heading="Beneficios configurados" reason="Opción aún no disponible" optional></li>
        <li livChecklistItem status="pending" number="7." heading="Reglas de sanción registradas" reason="Opción aún no disponible" optional></li>
      </ol>
    `,
  }),
};
