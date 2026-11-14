# Changelog — liv-components

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `ui-vX.Y.Z`. Cada versión publicada necesita su sección `## [X.Y.Z] — AAAA-MM-DD` antes de publicar (lo verifica `npm run release:check`). Proceso completo en `README.md` › "Publicar una versión".

## [Sin publicar]

### Agregado

- `Button` con `disabledReason` (feature 007, PR 8e; Figma 65:456): cambio compatible en un componente existente. Se versionará con 0.4.0 (PR 8f).
  - Entradas nuevas: `disabledReason` (texto) y `reasonAlign` (`start`, por omisión, o `end`). Sin ellas, el botón se comporta y se ve como siempre.
  - `disabledReason` solo cuenta con `disabled` y con texto no vacío. El motivo se muestra como texto visible debajo del botón (`body/sm`, `text/secondary`), enlazado con `aria-describedby`.
  - Con motivo, el botón usa `aria-disabled="true"` en lugar de `disabled`, para seguir recibiendo el foco del teclado y que el lector de pantalla anuncie el motivo. No emite clic con el ratón, con Enter ni con Espacio, y no envía el formulario (el mismo guarda de `loading`). `disabled` sigue ganándole a `loading`.
  - El aspecto no cambia: el estado Disabled pasa al anfitrión (`data-state="disabled"`, que ahora también lleva el botón deshabilitado sin motivo) y las reglas de hover y de cargando lo excluyen. El anillo de foco se ve también en el botón deshabilitado con motivo, que es enfocable.
  - Con motivo, el botón se ajusta a su contenido (no ocupa todo el ancho del anfitrión) y se alinea con el motivo, a la izquierda o a la derecha según `reasonAlign`. Sin motivo no cambia nada, incluido el ancho.
- Iconos `plus` y `circle` en `Icon` (feature 007, PR 8d; Figma 63:247 y 63:250): `Icon` pasa de 38 a 40 iconos de Lucide. Se versionarán con 0.4.0 (PR 8f).
  - `plus` es para las acciones de alta, como «Crear vigencia»; `circle` es el estado pendiente de la lista de preparación y nunca es la única señal: va siempre con el texto del estado.
  - Toman el color de su contenedor (`currentColor`), como el resto. `IconName` crece con los dos nombres y no rompe el uso existente.
  - `chevron-left`, que Figma también propone, no entra: solo lo usa la paginación y llega con la 1.6.
- `RadioGroup` y `RadioOption` (feature 007, PR 8c; Figma 73:514 y 64:629): grupo de selección única, aprobado por el responsable funcional. Se versionará con 0.4.0 (PR 8f).
  - `liv-radio-group` es un `fieldset` con su `legend` visible (`legend`, el nombre accesible), `[(value)]` (`string | null`; `null` es sin elección), `name` opcional y `disabled`.
  - `liv-radio-option` (`value`, `label`, `description` y `disabled`) es un radio nativo dentro de una tarjeta y solo existe dentro de un grupo.
  - Estados: Default, Hover, Focus y Disabled, elegida o no. La elegida no depende solo del color: borde, fondo y punto interior.
  - Los radios comparten un `name`, así que las flechas y el Tab que entra solo a la opción marcada los da el navegador.
  - El valor es un texto: una pantalla con valores numéricos (un año) lo convierte en los dos sentidos. El grupo no corrige su valor si la opción elegida desaparece; los `value` deben ser únicos y el `name`, si se da, único en la página.
  - En alto contraste (forced-colors) la elegida se distingue por su borde de 2 px y su punto, dibujado con borde, y la deshabilitada usa `GrayText` (único color que no sale de un token).
- `ChecklistItem`, `DescriptionItem`, `Panel` y el formateador de fechas de negocio (feature 007, PR 8b; Figma 64:573, 64:583 y 64:631). Se versionarán con 0.4.0 (PR 8f).
  - `li[livChecklistItem]` es un punto de una lista de verificación, para usarse dentro de un `<ol role="list">`. Es un componente de **selector de atributo** (con una excepción de ESLint justificada en el código): un elemento `liv-*` entre el `ol` y sus `li` rompería la estructura de la lista.
    - Entradas: `status` (`met`, `pending` u `observed`), `number`, `heading` (el `Title` de Figma, que no se llama `title` para no chocar con el atributo del navegador), `reason`, `optional`, `link` y `linkHref`. Un estado desconocido se muestra como `pending`.
    - El estado se dice con texto en un `Badge` («Cumplido», «Pendiente», «Con observación»); el icono es decorativo.
    - El enlace es un `<a href>` que emite `navigate` con un clic normal, como `NavItem`; sin texto o sin destino no se dibuja. El destino no se sanea: debe ser una ruta propia de la aplicación.
    - `Pending` dibuja su círculo vacío en el propio componente porque `Icon` no tenía `circle` (lo agrega el PR 8d; pasarlo a `liv-icon` es un cambio aparte).
    - La pantalla quita al `ol` el relleno y el margen del navegador (`m-0 list-none p-0`).
  - `div[livDescriptionItem]` es un par etiqueta–valor (`dt` y `dd`) para una lista de descripción (`<dl>`), también de selector de atributo (la regla `definition-list` de axe no admite un elemento propio entre el `dl` y sus `dt` y `dd`).
    - Entradas: `type` (`text` o `badge`), `label` y `value`.
    - Se agrega `tone` (por omisión `primary`, el tono del diseño), que Figma no trae: el estado de una vigencia cambia de tono (en el diseño del detalle, «Planeada» es neutra). No afecta a `Type=Text`.
    - La pantalla quita al `dl` el margen del navegador.
  - `liv-panel` es una región (`section`) con borde, nombrada por su título, con su descripción enlazada con `aria-describedby`.
    - Entradas: `heading`, `description` opcional y el contenido proyectado.
    - `headingLevel` elige `h2` (por omisión) o `h3`; el aspecto es el mismo (en el diseño es `heading/h3`). Los títulos de una página deben ser distintos: cada panel es una región.
  - `formatBusinessDate(value, format)` formatea una fecha de negocio (`AAAA-MM-DD`) **sin convertir de zona**: `long` (por omisión) da «1 de enero de 2026» y `short`, `01/01/2026` (`dd/mm/aaaa`).
    - Es la fecha larga de la decisión H2‑13; `new Date('2026-01-01')` mostraría el 31 de diciembre de 2025 en Bogotá.
    - Escribe los meses en español, sin depender de `Intl` ni de los datos de idioma del equipo.
    - Devuelve `null` si el valor está ausente, no es un texto o no es una fecha que exista.
- `Table`, `TableRow` y `TableCell` (feature 007, PR 8a; Figma 64:446, 63:352 y 63:283): tabla de datos de solo lectura, una tabla HTML real. Se versionarán con 0.4.0 (PR 8f).
  - `<table livTable>`: nombre accesible obligatorio, por `caption` (dibujado como `<caption>` oculto a la vista) o por `aria-label`, como atributo o como `[aria-label]` (**`[attr.aria-label]` no vale**; `aria-labelledby` tampoco cuenta). Con los dos, solo cuenta el `aria-label` y no se dibuja el `<caption>`. Si faltan los dos o quedan vacíos, lanza un error solo en desarrollo, también si ocurre después. `loading` declara `aria-busy="true"`. Superficie `bg/surface`, borde `border/default` de 1 px y `radius/xl`, con las columnas repartidas por igual (layout fijo).
  - `tr[livTableRow]` (`type` `header` o `body`; sin `type`, la fila de un `<thead>` es el encabezado): Hover pinta `bg/subtle` y Focus dibuja un contorno `border/focus` de 2 px dentro de la fila mientras su enlace tiene el foco del teclado (`:has(:focus-visible)`: un clic con el ratón no lo pinta); un contorno, no una sombra, para que siga visible en alto contraste. La fila no es clicable: solo navega el enlace de la primera celda.
  - `th[livTableCell]` (siempre un encabezado de columna con `scope="col"`) y `td[livTableCell]` con `type` `text` (por omisión), `link`, `badge` o `action`. El `link` dibuja su propio `<a href>` y emite `navigate` con el destino, como `NavItem`: la pantalla navega con su enrutador. Es el único elemento de la fila que recibe el foco. `action` dibuja un chevron decorativo (`aria-hidden`, 20 px) en una columna de 56 px; su encabezado es un `th` con `type="action"`, cuyo texto, obligatorio, solo leen los lectores de pantalla. Un enlace sin destino queda inerte y se pinta como texto. La pantalla valida el destino que emite `navigate`.
  - Sin estado vacío, barra de herramientas ni paginación (la 1.4 usa `ResultState`; entran con la primera consulta 1.6). Las filas de «cargando» las compone la pantalla con `liv-skeleton` dentro de sus celdas. Los selectores solo valen sobre las etiquetas nativas (`table`, `tr`, `th`, `td`), la pantalla importa los tres componentes, todas las filas tienen las mismas columnas y, en un ancho estrecho, envuelve la tabla en un contenedor con desplazamiento horizontal.
- *Skill* `liv-componente` para los agentes (`.claude/skills/`), basada en los componentes de la feature 001, y el servidor MCP del CLI de Angular en modo de solo lectura (`.mcp.json`). No cambia el paquete publicado.

### Cambiado

- `.vscode/mcp.json` usa el CLI del proyecto, en lugar de descargar la última versión con `npx -y`.
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.

## [0.3.1] — 2026-11-06

Corrección de la feature 001. Un humano la publica desde `main`, después de integrar el PR.

### Corregido

- `ModuleCard` se estira con su celda: en una cuadrícula, las tarjetas de una misma fila miden lo mismo, como en el diseño de Inicio (Figma, nodo 26-793). Antes cada tarjeta medía lo que su texto. El anfitrión pasa de `block` a `flex` y no fija una altura: quien la usa decide (con `align-items: start` mide lo que su contenido). Para igualar también las filas entre sí, la cuadrícula de la pantalla usa filas iguales (`grid-auto-rows: 1fr`). Nueva historia «Cuadrícula de Inicio».

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
