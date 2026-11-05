# AGENTS.md — frontend

Reglas para cualquier agente de IA (y para el equipo) que trabaje en el frontend. **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD, TDD, aprobaciones H1–H3, *skills* (en especial `liv-figma`) y `ESTADO.md`. Si este proyecto ya está en su propio repositorio, ese documento está en el repositorio de plataforma.

## Antes de tocar código

1. La tarea debe venir de una feature aprobada (`spec/features/NNN-…/` en el repositorio de plataforma) o ser un cambio del flujo directo, según la evaluación previa del `AGENTS.md` del repositorio de plataforma (§4.3).
2. **Toda pantalla nueva necesita su diseño aprobado** en la página `03 Pantallas` del archivo de Figma LIV‑Diseño, o una declaración de reutilización de estructura en el `plan.md` de la feature.
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
| Accesibilidad | WCAG 2.1 AA | ESLint (reglas de accesibilidad de plantillas) + axe‑core en e2e |
| Localización | `es-CO`: miles con punto, decimales con coma, COP, `dd/mm/aaaa` | Revisión |
| Formato | Prettier | `npm run format:check` |
| TDD | Commit `test:` (prueba en rojo) antes del `feat:`/`fix:` | Revisión del PR |

## Antes de decir "terminado"

- `npm run lint`, `npm test` y `npm run build` en verde, o se reporta qué falló.
- La feature y `ESTADO.md` del repositorio de plataforma están actualizados.
- Un PR toca solo este proyecto.

## Skills

Las *skills* de código del frontend (nueva pantalla) se agregarán en `frontend/.claude/skills/` con el primer componente. Mientras tanto, usa `liv-figma` y las demás *skills* del repositorio de plataforma.
