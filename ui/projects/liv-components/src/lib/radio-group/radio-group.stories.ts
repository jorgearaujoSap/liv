import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { RadioGroupComponent } from './radio-group.component';
import { RadioOptionComponent } from './radio-option.component';

const figmaGroup =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=73-514';
const figmaOption =
  'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=64-629';

const longDescription = 'Del 1 de enero al 31 de diciembre de 2027';

interface RadioGroupArgs {
  legend: string;
  value: string | null;
  disabled: boolean;
  /** Habilita la opción 2027, para ver una opción deshabilitada dentro de un grupo activo. */
  nextDisabled: boolean;
  showDescription: boolean;
  /** Cuántas opciones muestra el grupo (`Options` de Figma). */
  options: 1 | 2;
}

const meta: Meta<RadioGroupArgs> = {
  title: 'Componentes/RadioGroup',
  component: RadioGroupComponent,
  tags: ['autodocs'],
  decorators: [moduleMetadata({ imports: [RadioGroupComponent, RadioOptionComponent] })],
  parameters: {
    docs: {
      description: {
        component: `Grupo de selección única: un \`fieldset\` con su \`legend\` visible y radios nativos (\`liv-radio-option\`) con un \`name\` común, así que las flechas cambian de opción y el Tab entra al grupo una sola vez. El valor se enlaza con \`[(value)]\`. Hover y Focus son los del navegador: pasa el ratón y usa el teclado (Tab y flechas) para verlos. [RadioGroup en Figma](${figmaGroup}) · [RadioOption en Figma](${figmaOption})`,
      },
    },
  },
  argTypes: {
    options: { control: 'inline-radio', options: [1, 2] },
  },
  args: {
    legend: 'Año de la vigencia',
    value: '2026',
    disabled: false,
    nextDisabled: false,
    showDescription: true,
    options: 2,
  },
  render: (args) => ({
    props: args,
    template: `
      <div class="w-[416px]">
        <liv-radio-group [legend]="legend" [(value)]="value" [disabled]="disabled">
          <liv-radio-option
            value="2026"
            label="2026"
            [description]="showDescription ? 'Año en curso' : undefined"
          />
          @if (options === 2) {
            <liv-radio-option
              value="2027"
              label="2027"
              [description]="showDescription ? 'Año siguiente al año en curso' : undefined"
              [disabled]="nextDisabled"
            />
          }
        </liv-radio-group>
      </div>
    `,
  }),
};

export default meta;
type Story = StoryObj<RadioGroupArgs>;

/** Dos opciones con la primera elegida, como en el diálogo de creación (Figma 73:514, `Options=2`). */
export const DosOpciones: Story = {};

export const UnaOpcion: Story = { args: { options: 1 } };

export const SinElegir: Story = { args: { value: null } };

export const SinDescripcion: Story = { args: { showDescription: false } };

export const OpcionDeshabilitada: Story = { args: { nextDisabled: true } };

export const GrupoDeshabilitado: Story = { args: { disabled: true } };

/**
 * `RadioOption`: `Selected` × `State` (Figma 64:629). Hover y Focus no se pueden fijar desde la historia:
 * son del navegador.
 */
export const EstadosDeLaOpcion: Story = {
  parameters: { controls: { disable: true } },
  render: () => ({
    template: `
      <div class="grid w-[760px] grid-cols-2 gap-x-6 gap-y-4">
        <liv-radio-group legend="Default" value="b">
          <liv-radio-option value="a" label="2027" description="${longDescription}" />
          <liv-radio-option value="b" label="2027" description="${longDescription}" />
        </liv-radio-group>
        <liv-radio-group legend="Disabled" value="b" disabled>
          <liv-radio-option value="a" label="2027" description="${longDescription}" />
          <liv-radio-option value="b" label="2027" description="${longDescription}" />
        </liv-radio-group>
      </div>
    `,
  }),
};
