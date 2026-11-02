// @ts-check
const { defineConfig } = require('eslint/config');
const rootConfig = require('../../eslint.config.js');

module.exports = defineConfig([
  ...rootConfig,
  {
    files: ['**/*.ts'],
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'liv',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'liv',
          style: 'kebab-case',
        },
      ],
      // docs/arquitectura/01 §11.1: la librería es solo de presentación y no conoce ninguna aplicación.
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@angular/common/http',
              message:
                'liv-components no hace llamadas HTTP: recibe datos por input y emite eventos por output.',
            },
          ],
          patterns: [
            {
              group: ['**/src/app/**', '**/api/**'],
              message: 'liv-components no importa nada de una aplicación ni de un cliente de API.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    rules: {},
  },
]);
