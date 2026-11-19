# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Las reglas del proyecto son las mismas para todos los agentes y están en `AGENTS.md`, que se importa aquí completo:

@AGENTS.md

---

## Particularidades de Claude Code

Este archivo no repite ni modifica reglas: cualquier regla nueva o cambio va en `AGENTS.md`, para que la sigan todos los agentes. Aquí solo va lo específico de Claude Code.

- **Skills del proyecto:** están en `.claude/skills/` y Claude Code las carga solo al abrir el repositorio, en cualquier equipo. Son `liv-orquestador`, `liv-sdd-feature`, `liv-backlog`, `liv-revision-adversarial`, `liv-figma`, `liv-adr` y `liv-estado` (para qué sirve cada una: `AGENTS.md` §9). Las *skills* de código están en el `.claude/skills/` de cada proyecto (`backend/`, `frontend/`, `ui/` y `e2e/`): Claude Code las carga al abrir ese proyecto (desde la raíz, ver `AGENTS.md` §9). Las *skills* del proyecto **prevalecen sobre las *skills* globales** que pueda tener un equipo concreto:
  - `liv-revision-adversarial` reemplaza a `judgment-day`;
  - `liv-revision-adversarial` también reemplaza a `review-risk`, `review-resilience`, `review-readability` y `review-reliability`: el proyecto no usa las cuatro lentes (ADR 0040);
  - `liv-sdd-feature` reemplaza a los comandos SDD de gentle‑ai. Si se usan esos comandos, la spec y su diseño técnico van en `spec/features/NNN-nombre-feature/spec.md` y las tareas, como rebanadas en *issues* de GitHub (ADR 0040), nunca en `openspec/`.
- **Modelos y subagentes (`AGENTS.md` §10):**
  - **Niveles:** el nivel **alto** es Opus, el **estándar** es Sonnet y el **económico** es Haiku. El esfuerzo de razonamiento va en el mismo orden: alto, medio y bajo.
  - **Sesión principal:** el modelo se elige en el selector de la aplicación (o con `/model` en la terminal) según el trabajo. Opus para la spec de arranque, los ADR y la coordinación del backlog; Sonnet para construir rebanadas y ajustes, Figma y documentación. Una sesión por rebanada.
  - **Coordinador del backlog** (`liv-orquestador`, ADR 0033 y 0040): opcional, para avanzar varias rebanadas a la vez. Corre en una sesión con Opus que solo coordina. Abre cada rebanada (o cada PR de una rebanada) en una sesión nueva con la herramienta de sesiones de la aplicación de escritorio (`start_session`), en su propio *worktree* y con el modelo de su nivel. Tiene como máximo tres sesiones de trabajo a la vez. Nunca implementa en un subagente. Si no tiene esa herramienta (en la terminal) o esta no le deja fijar el modelo o el *worktree*, la persona abre cada sesión con el *brief* del coordinador.
  - **Subagentes del proyecto** (`.claude/agents/`): cada uno fija su modelo, su esfuerzo y sus herramientas, y se lanza por su nombre, sin pasar otro modelo salvo que el usuario lo pida. Son de solo lectura.

    | Subagente | Modelo | Úsalo para |
    |---|---|---|
    | `liv-explorador` | Haiku | Búsquedas, inventarios y resúmenes |
    | `liv-revisor` | Sonnet | Revisión adversarial que la persona pide fuera de las zonas rojas, las reglas con código y los documentos funcionales |
    | `liv-revisor-critico` | Opus | Revisión adversarial (dos en paralelo) de cada PR que toca una zona roja, una regla con código o un documento funcional, y del diseño técnico de una feature o de una rebanada suelta con zona roja |

  - **Tipos genéricos:** no se usan los subagentes genéricos (`general-purpose`, `Explore`, `Plan`) cuando uno del proyecto cubre la tarea.
- **Figma:** usa el servidor MCP oficial de Figma con la *skill* `liv-figma`. Si el plugin de Figma está instalado, sus *skills* `figma-use` y `figma-generate-library` la complementan; si no, basta con `liv-figma`.
- **Memoria (Engram), si está disponible:** guarda decisiones y descubrimientos con `mem_save` y cierra la sesión con `mem_session_summary`. Es opcional y personal de cada equipo: lo que afecte al equipo va al repositorio (ADR, `AGENTS.md`, `ESTADO.md`).
- **Ganchos y permisos:** los ganchos que bloquean rutas protegidas y la lista blanca de comandos se configurarán en `.claude/settings.json` como parte del entregable F0‑03. Hasta entonces, respeta las prohibiciones de `AGENTS.md` §4.5 sin excepción.
