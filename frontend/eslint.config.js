// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

// docs/arquitectura/04 §3.2: la librería es un paquete npm y se usa solo por su API pública;
// el frontend nunca importa archivos de otro proyecto del monorepo (IN-01).
const boundaryPatterns = [
  {
    group: ['liv-components/*', '!liv-components/styles/*'],
    message:
      'Importa liv-components solo desde su API pública: import { … } from "liv-components".',
  },
  {
    group: ['../../ui/**', '../../backend/**', '**/../ui/**', '**/../backend/**'],
    message: 'El frontend no importa archivos de otros proyectos del monorepo (regla IN-01).',
  },
];

// doc. 01 §11 y doc. 04 §3.2: HttpClient solo en los servicios de datos. Los servicios de datos
// son el cliente que ng-openapi-gen genera en src/app/api/; el código a mano no importa HttpClient:
// llama a la API a través de ese cliente.
const httpClientRestriction = {
  name: '@angular/common/http',
  importNames: ['HttpClient', 'HttpBackend'],
  // Un tipo no hace ninguna llamada: solo se prohíbe el uso en tiempo de ejecución.
  allowTypeImports: true,
  message:
    'HttpClient y HttpBackend solo se usan en el cliente generado (src/app/api/). Llama a la API con ese cliente, p. ej. CoreApi.invoke(getCurrentUser).',
};

// Otras formas de llamar a la API sin pasar por el cliente generado y su interceptor.
const bypassMessage = 'Llama a la API con el cliente generado de src/app/api/, no por esta vía.';
const bypassRules = {
  'no-restricted-globals': [
    'error',
    { name: 'fetch', message: bypassMessage },
    { name: 'XMLHttpRequest', message: bypassMessage },
  ],
  'no-restricted-syntax': [
    'error',
    {
      selector: "ImportExpression[source.value='@angular/common/http']",
      message: bypassMessage,
    },
  ],
};

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
      'no-restricted-imports': [
        'error',
        { patterns: boundaryPatterns, paths: [httpClientRestriction] },
      ],
      ...bypassRules,
    },
  },
  {
    // El cliente generado es el servicio de datos: ahí sí va HttpClient. Solo las carpetas de los
    // contratos: un archivo escrito a mano en src/app/api/ no está exento.
    files: ['src/app/api/*/**/*.ts'],
    // Cada archivo generado lleva su propio «eslint-disable» y no se puede editar para quitarlo.
    linterOptions: { reportUnusedDisableDirectives: 'off' },
    rules: { 'no-restricted-imports': ['error', { patterns: boundaryPatterns }] },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);
