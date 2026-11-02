import { moduleMetadata, type Meta, type StoryObj } from '@storybook/angular';
import { IconComponent } from './icon.component';
import { ICON_NAMES } from './icons';

const meta: Meta<IconComponent> = {
  title: 'Componentes/Icon',
  component: IconComponent,
  tags: ['autodocs'],
  parameters: {
    design: {
      type: 'figma',
      url: 'https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-210',
    },
    docs: {
      description: {
        component:
          'Iconos de Lucide (Figma: sección `Icon` de la página 02 Componentes). Heredan el color del texto. ' +
          'Sin `label` son decorativos (`aria-hidden`); con `label` se anuncian como imagen. ' +
          '[Diseño en Figma](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-210)',
      },
    },
  },
  argTypes: {
    name: { control: 'select', options: ICON_NAMES },
    size: { control: 'inline-radio', options: [16, 20, 24] },
  },
  args: { name: 'arrow-right', size: 24 },
};

export default meta;
type Story = StoryObj<IconComponent>;

export const Decorativo: Story = {};

export const ConTextoAlternativo: Story = {
  args: { name: 'circle-alert', label: 'Error' },
};

export const Tamano16: Story = { args: { size: 16 } };

export const Tamano20: Story = { args: { size: 20 } };

export const Tamano24: Story = { args: { size: 24 } };

export const Catalogo: Story = {
  decorators: [moduleMetadata({ imports: [IconComponent] })],
  parameters: { layout: 'padded' },
  render: () => ({
    props: { names: ICON_NAMES },
    template: `
      <div class="grid grid-cols-[repeat(auto-fill,minmax(8rem,1fr))] gap-4 text-text-primary">
        @for (name of names; track name) {
          <figure class="m-0 flex flex-col items-center gap-2">
            <liv-icon [name]="name" />
            <figcaption class="text-code-sm font-mono text-text-secondary">{{ name }}</figcaption>
          </figure>
        }
      </div>
    `,
  }),
};
