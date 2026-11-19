# AGENTS.md — frontend

Reglas para cualquier agente de IA (y para el equipo) que trabaje en el frontend. **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD por rebanadas verticales (ADR 0040), pruebas, aprobaciones (H1 de la spec, visto bueno de cada rebanada y H3), *skills* (en especial `liv-figma`) y `ESTADO.md`. Si este proyecto ya está en su propio repositorio, ese documento está en el repositorio de plataforma.

## Antes de tocar código

1. La tarea es un ajuste o una rebanada: un *issue* en «Lista», de una feature con su `spec.md` aprobado (`spec/features/NNN-…/` en el repositorio de plataforma) o suelta. Los tamaños de trabajo y las zonas rojas están en el `AGENTS.md` del repositorio de plataforma (§4.3).
2. **Toda pantalla nueva necesita su diseño aprobado** en la página `03 Pantallas` del archivo de Figma LIV‑Diseño, o una declaración de reutilización de estructura en el diseño técnico de la `spec.md` o en el *issue* de la rebanada.
3. **Todo componente nuevo nace primero** en la página `02 Componentes` de Figma, con el mismo nombre que tendrá en `liv-components` (se implementa en el proyecto `ui/`, no aquí).

## Reglas del frontend

| Regla | Detalle | Verificación |
|---|---|---|
| Independencia | Ningún archivo de este proyecto referencia algo fuera de `frontend/`; el contrato con el backend es la copia fijada del OpenAPI en `api-spec/` | Revisión (IN‑01, IN‑04) |
| Librería única | Las pantallas solo combinan componentes de `liv-components`; no crean componentes visuales propios ni redefinen los estilos de la librería | Revisión + ESLint |
| API pública | La librería se instala desde npm con versión exacta y se importa solo como `liv-components` (y su tema `liv-components/styles/theme.css`); nunca rutas internas ni archivos de `../ui` | ESLint (`no-restricted-imports`) |
| Estilos | Tailwind en el `.component.css` de cada componente; sin atributo `style`, sin utilidades en la plantilla, sin `::ng-deep` | Revisión (Stylelint en F0‑07) |
| Tokens | Colores, tipografías y sombras solo con los tokens de `liv-components/styles/theme.css`. La paleta por defecto de Tailwind está anulada. Un token nuevo o distinto se pide a la librería (y antes a Figma), nunca se define aquí | Compilación |
| Lógica de negocio | El frontend no calcula impuestos, descuentos ni saldos: muestra lo que calcula el backend | Revisión |
| Accesibilidad | WCAG 2.1 AA | ESLint (reglas de accesibilidad de plantillas) + axe‑core en las pruebas de pantalla (`features/accessibility.spec.ts`) y en e2e |
| Localización | `es-CO`: miles con punto, decimales con coma, COP. Fechas: `dd/mm/aaaa` en las tablas de consulta y en las exportaciones; en una fecha suelta (un dato dentro de un texto, un diálogo, un detalle) se admite la fecha larga, «1 de enero de 2026». Las fechas de negocio (`AAAA-MM-DD`) siempre con `formatBusinessDate` de `liv-components` (`'long'` o `'short'`), que no convierte de zona. | Revisión |
| Formato | Prettier | `npm run format:check` |
| Pruebas | Toda rebanada entra con sus pruebas en el mismo PR. **Prueba primero** (escrita antes y vista fallar, sin *commit* aparte) en el código que implementa una regla con código (`AGENTS.md` de la raíz, §5.3) y en los casos *golden* (§4.1, principio 5) | Revisión del PR; revisión adversarial si toca una regla o una zona roja |

## Antes de decir "terminado"

- `npm run lint`, `npm test` y `npm run build` en verde, o se reporta qué falló.
- El `CHANGELOG.md` de este proyecto tiene su entrada y la tarjeta del *issue* está al día (*skill* `liv-backlog`).
- Un PR toca solo este proyecto: no cambia `spec.md`, el glosario ni `ESTADO.md`, que solo cambian en los PR de la raíz (`AGENTS.md` de la raíz, §4.2, «Quién escribe en la raíz»).

## Skills

Las *skills* de código del frontend están en `frontend/.claude/skills/`. Siguen el código real de la feature 001. Antes de la tarea que describen, léelas, aunque tu agente no las cargue solo:

| *Skill* | Úsala para |
|---|---|
| `liv-pantalla` | Crear o cambiar una pantalla: diseño, ruta con su capacidad, servicio, composición con `liv-components`, estilos y pruebas |

Para Figma usa `liv-figma`, y para el resto las *skills* de proceso del repositorio de plataforma (hoy, `.claude/skills/` en la raíz del monorepo).

**Conocimiento de Angular:** el servidor MCP del CLI de Angular, en modo de solo lectura, entrega la guía oficial de buenas prácticas y la documentación de la versión instalada. Está configurado en `.mcp.json` (Claude Code) y `.vscode/mcp.json` (VS Code). Usa el CLI del proyecto (`node node_modules/@angular/cli/bin/ng.js mcp --read-only`, desde `frontend/`), así que necesita `npm install`. Otros agentes lo configuran con el mismo comando. Si la guía de Angular choca con una regla de este archivo, manda la regla.
