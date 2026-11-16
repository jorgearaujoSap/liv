import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { SelectComponent, SelectOption } from './select.component';

const figma = 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=81-629';

const years: SelectOption[] = [
  { value: '2027', label: '2027 · Año siguiente al año en curso' },
  { value: '2026', label: '2026 · Año en curso' },
  { value: '2025', label: '2025' },
  { value: '2024', label: '2024' },
];

/** Los 26 años de 2002 a 2027, del más reciente al más antiguo, como los entrega el servidor. */
const manyYears: SelectOption[] = [
  years[0],
  years[1],
  ...Array.from({ length: 24 }, (_, index) => {
    const year = String(2025 - index);
    return { value: year, label: year };
  }),
];

interface SelectArgs {
  label: string;
  value: string | null;
  options: SelectOption[];
  placeholder?: string;
  help?: string;
  error?: string;
  disabled: boolean;
}

const meta: Meta<SelectArgs> = {
  title: 'Componentes/Select',
  component: SelectComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [SelectComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Lista desplegable de selección única de 44 px, sobre el \`<select>\` nativo, con la etiqueta siempre visible encima. La lista abierta la dibuja el navegador: no se diseña ni se implementa (el estado Open de Figma se pinta como Focus, el que sale al enfocar el control). Con \`value\` \`null\` muestra el \`placeholder\`, una opción vacía que no se puede volver a elegir. Con \`error\` expone \`aria-invalid\` y el mensaje queda enlazado con \`aria-describedby\`. El teclado lo da el navegador: enfoca el control, abre la lista y cámbiala con las flechas. [Diseño en Figma](${figma})`,
      },
    },
  },
  argTypes: {
    value: { control: 'select', options: [null, ...years.map((year) => year.value)] },
  },
  args: {
    label: 'Año de la vigencia',
    value: '2026',
    options: years,
    placeholder: 'Elige un año',
    disabled: false,
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="w-[320px]">
        <liv-select
          [label]="label"
          [(value)]="value"
          [options]="options"
          [placeholder]="placeholder"
          [help]="help"
          [error]="error"
          [disabled]="disabled"
        />
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<SelectArgs>;

export const Filled: Story = {};

export const Placeholder: Story = { args: { value: null } };

export const ConAyuda: Story = { args: { help: 'Elige el año de la vigencia que vas a crear.' } };

export const Focus: Story = {
  parameters: {
    docs: {
      description: {
        story:
          'El foco lo da el navegador: pulsa Tab hasta el control. Con la lista abierta (Open en Figma) el control conserva este mismo aspecto.',
      },
    },
  },
};

export const StateError: Story = {
  name: 'Error',
  args: { value: null, error: 'Elige un año para crear la vigencia.' },
};

export const ErrorConValor: Story = {
  args: { error: 'Ese año ya no se puede crear.' },
};

export const Disabled: Story = { args: { disabled: true } };

export const DisabledPlaceholder: Story = { args: { disabled: true, value: null } };

export const ListaLarga: Story = {
  args: { options: manyYears, value: null },
  parameters: {
    docs: {
      description: {
        story: 'Los 26 años de 2002 a 2027, en el orden en que los entrega el servidor.',
      },
    },
  },
};
