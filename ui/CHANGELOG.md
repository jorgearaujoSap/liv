# Changelog — liv-components

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `ui-vX.Y.Z`. Cada versión publicada necesita su sección `## [X.Y.Z] — AAAA-MM-DD` antes de publicar (lo verifica `npm run release:check`). Proceso completo en `README.md` › "Publicar una versión".

## [Sin publicar]

## [0.2.0] — 2026-11-02

Primera versión con componentes (feature 001, tareas T03, T04, T04a y T05). Un humano la publica desde `main`, después de integrar el PR (`README.md` › "Publicar una versión").

### Agregado

- Componentes, cada uno con su historia de Storybook y sus pruebas (incluida la accesibilidad con axe):
  - base (T03): `Icon` (38 iconos de Lucide), `Logo`, `Spinner`, `Skeleton`, `Badge` y `Avatar`;
  - formularios y avisos (T04): `Button`, `IconButton`, `TextField`, `CodeInput` y `Alert`;
  - estructura y resultados (T05): `Dialog`, `ResultState`, `NavItem`, `SideNav` (con `NavSection`, la etiqueta de sección del menú), `AppHeader`, `PageHeader` y `ModuleCard`.
- `NavItem` y `ModuleCard` son enlaces: un clic normal emite `navigate` con el destino y cancela la navegación del navegador, para que la aplicación navegue con su enrutador; con Ctrl, Cmd, Mayús, Alt o el botón central el navegador abre el enlace como siempre. Si está deshabilitado (`disabled`), no tiene `href`, no navega y declara `aria-disabled="true"`.
- `Dialog` atrapa el foco, lo devuelve a quien lo tenía al cerrarse y emite `closed` con Escape si `dismissible` es verdadero. El foco inicial va al elemento con `data-initial-focus` o, si no hay, al primer enfocable.
- Tokens nuevos en `styles/theme.css`: colores `text/inverse-secondary` y `bg/inverse-subtle`, textos `heading/display` y `code/lg`, y las variables `spacing/*` (0 a 16) y `radius/*` (`sm` a `full`) de Figma. Ahora son 28 de color, 13 de texto y 5 de efecto.
- Proceso de publicación documentado y verificaciones automáticas antes de publicar (`scripts/check-release.mjs`): rama `main` sin cambios pendientes, sesión de npm, permiso de publicación, versión nueva y mayor que la última, y sección en este archivo. Comandos `version:patch`, `version:minor` y `version:major`.

### Cambiado

Estos cambios modifican la apariencia de las aplicaciones que usan `styles/theme.css`. Los tres primeros se hicieron primero en Figma (LIV‑Diseño) y los aprobó el responsable el 2026-11-02 (T04a); el último viene de T03.

- Token `border/strong` (`--color-border-strong`, `border-border-strong`): `#cbd5e1` → `#64748b`.
  - Motivo: el borde de los campos medía 1,48:1 sobre `bg/surface` y WCAG 1.4.11 exige 3:1; ahora mide 4,76:1 (4,34:1 sobre `bg/subtle`).
  - Afecta a `TextField`, `CodeInput` y a los botones Secondary.
- Token `focus/ring` (`--shadow-focus-ring`, `shadow-focus-ring`): `0 0 0 3px rgb(37 99 235 / 0.4)` → `0 0 0 2px #ffffff, 0 0 0 4px #2563eb` (anillo opaco con una separación blanca).
  - Motivo: el anillo medía 1,81:1 sobre `bg/surface` y WCAG 1.4.11 exige 3:1; ahora mide 5,17:1 (4,94:1 sobre `bg/canvas`, 4,72:1 sobre `bg/subtle` y 4,75:1 sobre `action/primary-subtle`). La separación blanca evita que el anillo quede pegado al relleno de un botón Primary (1,30:1) o al borde de un campo con error.
- Placeholder de `CodeInput`: `text/disabled` (`#94a3b8`, 2,56:1) → `text/muted` (`#64748b`, 4,76:1 sobre `bg/surface`), como ya usaba `TextField`; WCAG 1.4.3 exige 4,5:1. El placeholder de un campo deshabilitado sigue en `text/disabled`.
- `styles/theme.css` ahora anula también la escala de espaciado y de radios de Tailwind (`--spacing-*: initial` y `--radius-*: initial`) y emite todos los tokens (`@theme static`). Las clases de Tailwind de una aplicación que dependan de la escala por defecto (p. ej. `p-7`, `gap-1.5` o `rounded-2xl`) dejan de existir: solo quedan los valores de `spacing/*` y `radius/*`.

### Notas

- `ResultState` dibuja el icono a 24 px y no a los 28 px de Figma, porque `Icon` solo admite 16, 20 y 24 px.
- `SideNav` y `AppHeader` son claros (`bg/surface`) en Figma, así que el anillo de foco se mide solo contra fondos claros.
- Todavía no hay pruebas de regresión visual (Q14): las capturas de referencia quedan pendientes.

## [0.1.0] — 2026-11-01

Primera versión publicada en npm. Se publicó antes de integrar su PR, como excepción para poder instalarla en el frontend; las siguientes siguen el proceso del `README.md`.

### Agregado

- Tokens de diseño en `styles/theme.css`, transcritos de los estilos locales del archivo de Figma LIV‑Diseño: 26 de color, 11 de texto y 5 de efecto. Anulan la paleta, los tamaños de texto y las sombras por defecto de Tailwind.
- Workspace de Angular 21 con la librería, ESLint (sin `HttpClient` ni imports de aplicaciones) y Prettier.
