---
name: liv-figma
description: Trabaja con el archivo de diseño LIV-Diseño en Figma siguiendo las reglas del proyecto LIV - leer o implementar un diseño, diseñar una pantalla nueva, crear o modificar estilos o componentes, o revisar el estado del archivo. Úsala siempre que la tarea toque Figma, una pantalla o un componente de liv-components, aunque no tengas instaladas las skills del plugin de Figma (figma-use, figma-generate-library), a las que complementa.
---

# Figma en el proyecto LIV

Reglas completas en `AGENTS.md` §6 y `docs/arquitectura/01` §11.

## Archivo y organización

- Único archivo oficial: **LIV‑Diseño** — `https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o`, clave `s2r2Kdu31sQW5CmVeQNyZb`. No se diseña fuera de él.
- Páginas (no se crean otras):

| Página | Contenido |
|---|---|
| `00 Guía` | Convenciones y estados de aprobación |
| `01 Fundamentos` | Marco `Fundamentos`: muestras de estilos enlazadas a los estilos |
| `02 Componentes` | Componentes con el **mismo nombre, variantes y propiedades** que en `liv-components` |
| `03 Pantallas` | **Todas** las pantallas, una sección de Figma por módulo (M1, M2.6, M3, M4…); cada pantalla con su número y nombre de menú, p. ej. "1.5.3 Cargar archivo de avalúos" |
| `99 Archivo` | Diseños reemplazados |

- Estilos locales obligatorios (nombres en inglés, iguales a los *tokens* del código):
  - **Color:** `text/*`, `bg/*`, `border/*`, `action/primary*`, `status/*` (cada estado con su variante `-subtle`).
  - **Texto:** `heading/h1`–`h3`, `body/lg`, `body/md`, `body/md-strong`, `body/sm`, `label/md`, `label/sm`, `button/md`, `code/sm`.
  - **Efecto:** `elevation/sm`–`xl`, `focus/ring`.
  - Ningún color, texto o sombra se aplica sin estilo.
- Plan Professional con Dev Mode, **sin Code Connect**: la correspondencia con `liv-components` es por nombre idéntico y cada historia de Storybook enlaza a su componente de Figma.
- No hay diseñador dedicado: diseñan el equipo o los agentes, y **aprueba el responsable funcional** del módulo. Un diseño aprobado se marca como listo para desarrollo.

## Leer o implementar un diseño

1. Crea una sesión del servidor MCP de Figma con la clave del archivo (`create_session`) y reutiliza su identificador en las llamadas siguientes.
2. Usa `get_metadata` para ubicar páginas y nodos, `get_design_context` para el detalle del *frame* y `get_screenshot` para verlo.
3. Traduce cada elemento a un componente de `liv-components` con el mismo nombre. Si no existe, **detente**: primero hay que crear el componente (en Figma y en la librería) en un cambio aparte.
4. Registra el enlace del *frame* (con `node-id`) en la sección "Diseño de interfaz" del `plan.md` de la feature.

## Diseñar o modificar en Figma

1. Si están instaladas, carga antes las *skills* del plugin de Figma: `figma-use` para cualquier escritura y `figma-generate-library` para estilos, variables o componentes. Si no lo están, sigue las reglas de abajo.
2. **Inspecciona primero** con una lectura (páginas, estilos y componentes existentes) y sigue sus convenciones.
3. Escrituras con `use_figma`, **una a la vez, nunca en paralelo**.
4. Reglas de la API de *plugins* que más fallan:
   - Cambiar de página solo con `await figma.setCurrentPageAsync(página)`, una vez por llamada.
   - Antes de escribir texto, `await figma.loadFontAsync({ family, style })` (estilos de Inter: "Regular", "Medium", "Semi Bold").
   - Colores en rango 0–1, no 0–255.
   - Devolver con `return` los identificadores de todo lo creado o modificado.
   - Aplicar estilos con `setFillStyleIdAsync`, `setTextStyleIdAsync`, `setStrokeStyleIdAsync` y `setEffectStyleIdAsync`, nunca con valores sueltos.
   - Contenedores con *auto layout*; los nodos de primer nivel, lejos de (0, 0) si la página ya tiene contenido.
   - Antes de borrar algo, comprueba que es exactamente lo que creaste (por identificador).
5. Al terminar, toma **una** captura del resultado y corrige lo que se vea cortado o superpuesto.
6. Pantallas nuevas: incluye todos los estados (vacío, cargando, con datos, error, sin permiso, éxito) y textos reales en español.

## Al terminar

Actualiza la sección "Figma" de `ESTADO.md` (páginas, estilos, componentes, pantallas y su estado de aprobación) con la *skill* `liv-estado`.
