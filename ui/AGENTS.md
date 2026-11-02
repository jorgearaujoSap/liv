# AGENTS.md — liv-components

Reglas para cualquier agente de IA (y para el equipo) que trabaje en la librería de componentes. **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD, TDD, aprobaciones H1–H3, *skills* (en especial `liv-figma`) y `ESTADO.md`.

## Antes de tocar código

1. Todo componente nuevo nace primero en la página `02 Componentes` del archivo de Figma LIV‑Diseño, con el **mismo nombre, variantes y propiedades** que tendrá aquí.
2. La tarea debe venir de una feature aprobada (`spec/features/NNN-…/` en el repositorio de plataforma) o ser un cambio trivial.
3. Modificar un componente existente es un **cambio crítico**: afecta todas las pantallas que lo usan y exige la aprobación del líder de frontend y de un responsable funcional.

## Reglas de la librería

| Regla | Detalle | Verificación |
|---|---|---|
| Independencia | Ningún archivo de este proyecto referencia algo fuera de `ui/`. Las aplicaciones la usan solo como paquete npm | Revisión (IN‑01) |
| Solo presentación | Sin `HttpClient`; no importa ninguna aplicación ni cliente de API; no calcula impuestos ni decide permisos. Recibe datos por `input` y emite eventos por `output` | ESLint (`no-restricted-imports`) |
| API pública | Todo lo que se publica sale por `src/public-api.ts`; los tokens, por `styles/theme.css` | Revisión |
| Tokens | `src/styles/theme.css` refleja los estilos locales de Figma; un cambio se hace primero en Figma | Revisión |
| Estilos | Tailwind en el `.component.css` de cada componente; sin atributo `style`, sin `::ng-deep`; solo tokens | Revisión (Stylelint en F0‑07) |
| Accesibilidad | Cada componente cumple WCAG 2.1 AA por sí mismo | ESLint (accesibilidad de plantillas) + axe‑core |
| Versiones | Todo cambio que deba llegar a las aplicaciones sube la versión en el mismo PR (`npm run version:patch`/`minor`/`major`, según la tabla de `README.md`) y agrega su sección en `CHANGELOG.md` | `npm run release:check` |
| Publicación | Solo un humano publica, desde `main` y después de integrar el PR, siguiendo `README.md` › "Publicar una versión". Un agente puede preparar la versión y el `CHANGELOG.md`, pero nunca ejecuta `npm publish` ni `npm run release` | `npm run release:check` + revisión |
| Formato | Prettier | `npm run format:check` |
| TDD | Commit `test:` (prueba en rojo) antes del `feat:`/`fix:` | Revisión del PR |

## Antes de decir "terminado"

- `npm run lint`, `npm run format:check` y `npm run pack` en verde, o se reporta qué falló.
- Con el primer componente: pruebas, historia de Storybook y captura de referencia de cada variante y estado.
- La feature y `ESTADO.md` del repositorio de plataforma están actualizados.
- Un PR toca solo este proyecto.
