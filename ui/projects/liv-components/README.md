# liv-components

Librería de componentes Angular y *tokens* de diseño del sistema LIV (liquidación del impuesto vehicular).

## Instalación

```bash
npm install liv-components
```

Requiere Angular 21 y Tailwind CSS 4 en la aplicación.

## Tokens de diseño

Importa el tema después de Tailwind en la hoja global de la aplicación:

```css
@import 'tailwindcss';
@import 'liv-components/styles/theme.css';
```

El tema define colores (`--color-text-primary`, `--color-status-success`…), tipografía (`--text-body-md`, `--text-heading-h1`…) y sombras (`--shadow-elevation-md`, `--shadow-focus-ring`…), con los mismos nombres que los estilos de Figma. Anula la paleta, los tamaños de texto y las sombras por defecto de Tailwind, así que solo existen los tokens del sistema.

## Componentes

Todavía no hay componentes publicados. Cada uno tendrá el mismo nombre, variantes y propiedades que en el archivo de diseño de Figma.
