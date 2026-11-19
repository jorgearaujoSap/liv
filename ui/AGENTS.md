# AGENTS.md — liv-components

Reglas para cualquier agente de IA (y para el equipo) que trabaje en la librería de componentes. **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD por rebanadas verticales (ADR 0040), pruebas, aprobaciones (H1 de la spec, visto bueno de cada rebanada y H3), *skills* (en especial `liv-figma`) y `ESTADO.md`.

## Antes de tocar código

1. Todo componente nuevo nace primero en la página `02 Componentes` del archivo de Figma LIV‑Diseño, con el **mismo nombre, variantes y propiedades** que tendrá aquí.
2. La tarea es un ajuste (sin *issue*) o una rebanada con su *issue* en «Lista», de una feature con su `spec.md` aprobado (`spec/features/NNN-…/` en el repositorio de plataforma) o suelta. Los tamaños de trabajo y las zonas rojas están en el `AGENTS.md` del repositorio de plataforma (§4.3). Modificar un componente existente o sus capturas de referencia, los *tokens* y la configuración de *build* y de publicación son zona roja: como ajuste solo van los comentarios, el `README.md`, el `CHANGELOG.md` y los parches de dependencias, y cada PR pasa la revisión adversarial.
3. Modificar un componente existente es **zona roja**: afecta todas las pantallas que lo usan y exige la aprobación del líder de frontend y de un responsable funcional.

## Reglas de la librería

| Regla | Detalle | Verificación |
|---|---|---|
| Independencia | Ningún archivo de este proyecto referencia algo fuera de `ui/`. Las aplicaciones la usan solo como paquete npm | Revisión (IN‑01) |
| Solo presentación | Sin `HttpClient`; no importa ninguna aplicación ni cliente de API; no calcula impuestos ni decide permisos. Recibe datos por `input` y emite eventos por `output` | ESLint (`no-restricted-imports`) |
| API pública | Todo lo que se publica sale por `src/public-api.ts`; los tokens, por `styles/theme.css` | Revisión |
| Tokens | `src/styles/theme.css` refleja los estilos locales de Figma; un cambio se hace primero en Figma | Revisión |
| Estilos | Tailwind en el `.component.css` de cada componente; sin atributo `style`, sin `::ng-deep`; solo tokens. **Única excepción:** `Skeleton` aplica sus entradas `width` y `height` como estilo del anfitrión, porque se ajusta al contenido que reemplaza; solo medidas, nunca color, tipografía ni sombra (decisión del responsable, 2026-11-02). Otro componente no la usa sin una decisión igual | Revisión (Stylelint en F0‑07) |
| Accesibilidad | Cada componente cumple WCAG 2.1 AA por sí mismo | ESLint (accesibilidad de plantillas) + axe‑core |
| Versiones | Todo cambio que deba llegar a las aplicaciones sube la versión en el mismo PR (`npm run version:patch`/`minor`/`major`, según la tabla de `README.md`) y agrega su sección en `CHANGELOG.md` | `npm run release:check` |
| Publicación | Solo un humano publica, desde `main` y después de integrar el PR, siguiendo `README.md` › "Publicar una versión". Un agente puede preparar la versión y el `CHANGELOG.md`, pero nunca ejecuta `npm publish` ni `npm run release` | `npm run release:check` + revisión |
| Formato | Prettier | `npm run format:check` |
| Pruebas | Toda rebanada entra con sus pruebas en el mismo PR. **Prueba primero** (escrita antes y vista fallar, sin *commit* aparte) en el código que implementa una regla con código (`AGENTS.md` de la raíz, §5.3) y en los casos *golden* (§4.1, principio 5) | Revisión del PR; revisión adversarial si toca una regla o una zona roja |

## Skills

Las *skills* de código de la librería están en `ui/.claude/skills/`. Siguen el código real de la feature 001. Antes de la tarea que describen, léelas, aunque tu agente no las cargue solo:

| *Skill* | Úsala para |
|---|---|
| `liv-componente` | Crear o cambiar un componente, una variante o un *token*: Figma, componente, estilos, pruebas con axe, historia, API pública y versión |

Para Figma usa `liv-figma`, y para el resto las *skills* de proceso del repositorio de plataforma (hoy, `.claude/skills/` en la raíz del monorepo).

**Conocimiento de Angular:** el servidor MCP del CLI de Angular, en modo de solo lectura, entrega la guía oficial de buenas prácticas y la documentación de la versión instalada. Está configurado en `.mcp.json` (Claude Code) y `.vscode/mcp.json` (VS Code). Usa el CLI del proyecto (`node node_modules/@angular/cli/bin/ng.js mcp --read-only`, desde `ui/`), así que necesita `npm install`. Otros agentes deben configurar ese mismo comando, ejecutado desde `ui/`. Si la guía de Angular choca con una regla de este archivo, manda la regla.

## Antes de decir "terminado"

- `npm run lint`, `npm run format:check` y `npm run pack` en verde, o se reporta qué falló.
- Con el primer componente: pruebas, historia de Storybook y captura de referencia de cada variante y estado.
- El `CHANGELOG.md` de este proyecto tiene su entrada y la tarjeta del *issue* está al día (*skill* `liv-backlog`).
- Un PR toca solo este proyecto: no cambia `spec.md`, el glosario ni `ESTADO.md`, que solo cambian en los PR de la raíz (`AGENTS.md` de la raíz, §4.2, «Quién escribe en la raíz»).
