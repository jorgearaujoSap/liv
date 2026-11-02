// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
      // docs/arquitectura/04 §3.2: la librería es un paquete npm y se usa solo por su API pública;
      // el frontend nunca importa archivos de otro proyecto del monorepo (IN-01).
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['liv-components/*', '!liv-components/styles/*'],
              message:
                'Importa liv-components solo desde su API pública: import { … } from "liv-components".',
            },
            {
              group: ['../../ui/**', '../../backend/**', '**/../ui/**', '**/../backend/**'],
              message:
                'El frontend no importa archivos de otros proyectos del monorepo (regla IN-01).',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
