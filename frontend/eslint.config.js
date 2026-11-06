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
  importNames: ['HttpClient', 'HttpBackend', 'HttpHandler'],
  // Un tipo no hace ninguna llamada: solo se prohíbe el uso en tiempo de ejecución.
  allowTypeImports: true,
  message:
    'HttpClient, HttpBackend y HttpHandler solo se usan en el cliente generado (src/app/api/). Llama a la API con ese cliente, p. ej. CoreApi.invoke(getCurrentUser).',
};

// Otras formas de llamar a la API sin pasar por el cliente generado y su interceptor.
const bypassMessage = 'Llama a la API con el cliente generado de src/app/api/, no por esta vía.';
const bypassRules = {
  'no-restricted-globals': [
    'error',
    { name: 'fetch', message: bypassMessage },
    { name: 'XMLHttpRequest', message: bypassMessage },
  ],
  'no-restricted-properties': [
    'error',
    ...['window', 'globalThis', 'self'].flatMap((object) =>
      ['fetch', 'XMLHttpRequest'].map((property) => ({ object, property, message: bypassMessage })),
    ),
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
    // contratos; que no haya código a mano dentro de ellas lo comprueba la prueba que compara el
    // cliente con lo que genera ng-openapi-gen (scripts/api-update.test.mjs).
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
  {
    // AGENTS.md §6.1 y doc. 01 §11.1: las pantallas solo componen componentes de liv-components. La
    // disposición (flex, rejilla) va en el `.component.css` de la pantalla, con los tokens del tema, nunca
    // en la plantilla; los controles (botón, enlace, campo, tabla…) son de la librería.
    files: ['src/app/features/**/*.html'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: "TextAttribute[name='style']",
          message: 'Sin atributo style en las pantallas: la disposición va en el .component.css.',
        },
        {
          selector: "BoundAttribute[keySpan.details=/^(attr\\.)?style(\\.|$)|^ngStyle$/]",
          message: 'Sin [style] ni [ngStyle] en las pantallas: la disposición va en el .component.css.',
        },
        {
          selector: "TextAttribute[name='class']",
          message:
            'Sin atributo class (ni utilidades de Tailwind) en la plantilla de una pantalla: los estilos van en el .component.css.',
        },
        {
          selector: "BoundAttribute[keySpan.details=/^(attr\\.)?class(\\.|$)|^ngClass$/]",
          message:
            'Sin [class] ni [ngClass] en la plantilla de una pantalla: los estilos van en el .component.css.',
        },
        {
          // Angular saca las etiquetas <style> del árbol de la plantilla: se busca en el texto.
          selector: 'Program[value=/<style[\s>]/i]',
          message: 'Sin etiqueta <style> en las pantallas: los estilos van en el .component.css.',
        },
        {
          selector:
            'Element[name=/^(button|a|input|select|textarea|table|img|svg|dialog|form)$/]',
          message:
            'Los controles vienen de liv-components (liv-button, liv-text-field, liv-nav-item…); si falta uno, primero va en la librería.',
        },
        {
          selector:
            "Element[name=/^[a-z0-9]+-[a-z0-9-]+$/]:not([name=/^(liv|app|ng)-/]):not([name='router-outlet'])",
          message:
            'Las pantallas solo usan componentes de liv-components (liv-*), secciones propias (app-*) y router-outlet.',
        },
      ],
    },
  },
]);
