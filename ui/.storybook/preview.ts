import type { Preview } from '@storybook/angular';

const preview: Preview = {
  parameters: {
    layout: 'padded',
    a11y: { test: 'error' },
  },
};

export default preview;
