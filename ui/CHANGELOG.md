# Changelog — liv-components

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `ui-vX.Y.Z`. Cada versión publicada necesita su sección `## [X.Y.Z] — AAAA-MM-DD` antes de publicar (lo verifica `npm run release:check`). Proceso completo en `README.md` › "Publicar una versión".

## [Sin publicar]

### Cambiado

- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.

## [0.3.1] — 2026-11-05

### Corregido

- `ModuleCard` ocupa todo el alto de su celda: en una cuadrícula, todas las tarjetas miden lo mismo, como en el diseño de Inicio (Figma, nodo 26-793). Antes cada tarjeta medía lo que su texto, y la de descripción más larga quedaba más alta (feature 001, encontrado al probar Inicio).

## [0.3.0] — 2026-11-05

Tercer nivel del menú lateral (feature 001, diseño aprobado por el responsable funcional el 2026-11-05: Figma `NavItem`, `Type=Subgroup` y `Type=Nested`, y el marco «Menú · tres niveles»). Un humano la publica desde `main`, después de integrar el PR (`README.md` › "Publicar una versión").

### Agregado

- `NavItem` con dos tipos nuevos, compatibles con lo anterior en tiempo de ejecución (`NavItemType` pasa de `'item' | 'group' | 'subitem'` a `'item' | 'group' | 'subgroup' | 'subitem' | 'nested'`; un `switch` exhaustivo o un `Record<NavItemType, …>` de una aplicación deberá cubrir los valores nuevos al adoptar esta versión):
  - `subgroup`: opción de segundo nivel que agrupa opciones de tercer nivel (1.5 y 1.6 del módulo 1). Se expande como un `group` (botón con `aria-expanded`; expandido, `aria-controls` y la lista de sus opciones como `role="group"`), pero sin icono, alineada con el texto del módulo (44 px) y con énfasis medio más suave (`label/md` en `text-secondary`). En Active (el grupo que contiene la pantalla actual) el texto pasa a `body/md-strong` en `action-primary`, sin relleno y con el chevron en `text-muted`, para no apilar tres barras con el módulo y la opción actual; no tiene Hover mientras está activo. Deshabilitado no se expande ni muestra el chevron;
  - `nested`: opción de tercer nivel (1.5.x y 1.6.x). Es un enlace como un `subitem`, sangrado 16 px más (60 px), con el mismo Hover, Active (`aria-current="page"`), Disabled y foco.
- Historias de Storybook de `Subgroup`, `Nested` y del menú completo de tres niveles, enlazadas al diseño en Figma, y pruebas de Default, Active, Disabled, foco y accesibilidad con axe de los tipos nuevos. Hover no tiene prueba automática y axe no calcula el contraste en jsdom: el contraste del texto de `Subgroup` Active se mide con los valores de `theme.css` en `theme-components.spec.ts` y el aspecto se revisa en el catálogo de Storybook.

## [0.2.0] — 2026-11-02

Primera versión con componentes (feature 001, tareas T03, T04, T04a —contraste de tokens— y T05). Un humano la publica desde `main`, después de integrar el PR (`README.md` › "Publicar una versión").

### Agregado

- Componentes, cada uno con su historia de Storybook y sus pruebas (incluida la accesibilidad con axe):
  - base (T03): `Icon` (38 iconos de Lucide), `Logo`, `Spinner`, `Skeleton`, `Badge` y `Avatar`;
  - formularios y avisos (T04): `Button`, `IconButton`, `TextField`, `CodeInput` y `Alert`;
  - estructura y resultados (T05): `Dialog`, `ResultState`, `NavItem`, `SideNav` (con `NavSection`, la etiqueta de sección del menú), `AppHeader`, `PageHeader` y `ModuleCard`.
- `NavItem` y `ModuleCard` son enlaces: un clic normal emite `navigate` con el destino y cancela la navegación del navegador, para que la aplicación navegue con su enrutador; con Ctrl, Cmd, Mayús, Alt o el botón central el navegador abre el enlace como siempre. Si está deshabilitado (`disabled`), no tiene `href`, no navega y declara `aria-disabled="true"`.
- `NavItem` y `ModuleCard` con `href` ausente, vacío o solo con espacios quedan inertes (sin enlace, sin foco de teclado): una opción o tarjeta habilitada necesita destino. El destino que emiten se recorta, pero no se sanea: la aplicación debe navegar solo a rutas propias.
- El grupo de `NavItem` expone `aria-controls` y `aria-labelledby` entre su botón y la lista de subopciones.
- `Icon` admite 28 px además de 16, 20 y 24 (`IconSize`), para el círculo de `ResultState`. Es compatible con lo anterior: el lienzo sigue siendo de 24 unidades y el trazo escala con el tamaño. Decisión del responsable (2026-11-02); la descripción de los 38 iconos de Figma ya dice «16, 20, 24 o 28 px».
- `Dialog` atrapa el foco, lo devuelve a quien lo tenía al cerrarse y emite `closeRequested` con Escape si `dismissible` es verdadero y ningún control interno lo atendió (ni hay una composición de texto en curso); su fondo se desplaza si el panel no cabe. El foco inicial va al elemento con `data-initial-focus` o, si no hay, al primer enfocable.
- Tokens nuevos en `styles/theme.css`: colores `text/inverse-secondary` y `bg/inverse-subtle`, textos `heading/display` y `code/lg`, y las variables `spacing/*` (0 a 16) y `radius/*` (`sm` a `full`) de Figma. Ahora son 28 de color, 13 de texto y 5 de efecto.
- Proceso de publicación documentado y verificaciones automáticas antes de publicar (`scripts/check-release.mjs`): rama `main` sin cambios pendientes, sesión de npm, permiso de publicación, versión nueva y mayor que la última, y sección en este archivo. Comandos `version:patch`, `version:minor` y `version:major`.

### Cambiado

Estos cambios modifican la apariencia de las aplicaciones que usan `styles/theme.css`. Los tres primeros cambios (borde de los campos, anillo de foco y placeholder de `CodeInput`) se hicieron primero en Figma (LIV‑Diseño) y los aprobó el responsable el 2026-11-02 (tarea T04a); el último viene de T03.

- Token `border/strong` (`--color-border-strong`, `border-border-strong`): `#cbd5e1` → `#64748b`.
  - Motivo: el borde de los campos medía 1,48:1 sobre `bg/surface` y WCAG 1.4.11 exige 3:1; ahora mide 4,76:1 (4,34:1 sobre `bg/subtle`).
  - Afecta a `TextField`, `CodeInput` y a los botones Secondary.
- Token `focus/ring` (`--shadow-focus-ring`, `shadow-focus-ring`): `0 0 0 3px rgb(37 99 235 / 0.4)` → `0 0 0 2px #ffffff, 0 0 0 4px #2563eb` (anillo opaco con una separación blanca).
  - Motivo: el anillo medía 1,81:1 sobre `bg/surface` y WCAG 1.4.11 exige 3:1; ahora mide 5,17:1 (4,94:1 sobre `bg/canvas`, 4,72:1 sobre `bg/subtle` y 4,75:1 sobre `action/primary-subtle`). La separación blanca evita que el anillo quede pegado al relleno de un botón Primary (1,30:1) o al borde de un campo con error.
- Placeholder de `CodeInput`: `text/disabled` (`#94a3b8`, 2,56:1) → `text/muted` (`#64748b`, 4,76:1 sobre `bg/surface`), como ya usaba `TextField`; WCAG 1.4.3 exige 4,5:1. El placeholder de un campo deshabilitado sigue en `text/disabled`.
- **Incompatible:** `styles/theme.css` ahora anula también la escala de espaciado y de radios de Tailwind (`--spacing-*: initial` y `--radius-*: initial`) y emite todos los tokens (`@theme static`). Las clases de Tailwind de una aplicación que dependan de la escala por defecto (p. ej. `p-7`, `gap-1.5` o `rounded-2xl`) dejan de existir: solo quedan los valores de `spacing/*` y `radius/*`.

### Notas

- `SideNav` y `AppHeader` son claros (`bg/surface`) en Figma, así que el anillo de foco y los textos se miden contra fondos claros (superficie, lienzo, `bg/subtle` y `action/primary-subtle`), no contra `bg/inverse-subtle`.
- Todavía no hay pruebas de regresión visual (compuerta Q14 de CI): las capturas de referencia por variante quedan pendientes.
- Con varios `Dialog` abiertos, solo el último abierto atrapa el foco y atiende Escape. El foco inicial por defecto es el primer elemento enfocable: en un aviso con una acción destructiva primero, marca la acción segura con `data-initial-focus`.

## [0.1.0] — 2026-11-01

Primera versión publicada en npm. Se publicó antes de integrar su PR, como excepción para poder instalarla en el frontend; las siguientes siguen el proceso del `README.md`.

### Agregado

- Tokens de diseño en `styles/theme.css`, transcritos de los estilos locales del archivo de Figma LIV‑Diseño: 26 de color, 11 de texto y 5 de efecto. Anulan la paleta, los tamaños de texto y las sombras por defecto de Tailwind.
- Workspace de Angular 21 con la librería, ESLint (sin `HttpClient` ni imports de aplicaciones) y Prettier.
