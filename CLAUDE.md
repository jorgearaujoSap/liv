# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Las reglas del proyecto son las mismas para todos los agentes y están en `AGENTS.md`, que se importa aquí completo:

@AGENTS.md

---

## Particularidades de Claude Code

Este archivo no repite ni modifica reglas: cualquier regla nueva o cambio va en `AGENTS.md`, para que la sigan todos los agentes. Aquí solo va lo específico de Claude Code.

- **Skills del proyecto:** están en `.claude/skills/` y Claude Code las carga solo al abrir el repositorio, en cualquier equipo. Son `liv-orquestador`, `liv-sdd-feature`, `liv-revision-adversarial`, `liv-revision-4r`, `liv-figma`, `liv-adr` y `liv-estado` (para qué sirve cada una: `AGENTS.md` §9). Las *skills* de código están en el `.claude/skills/` de cada proyecto (`backend/`, `frontend/`, `ui/` y `e2e/`): Claude Code las carga al abrir ese proyecto (desde la raíz, ver `AGENTS.md` §9). Las *skills* del proyecto **prevalecen sobre las *skills* globales** que pueda tener un equipo concreto:
  - `liv-revision-adversarial` reemplaza a `judgment-day`;
  - `liv-revision-4r` reemplaza a `review-risk`, `review-resilience`, `review-readability` y `review-reliability`;
  - `liv-sdd-feature` reemplaza a los comandos SDD de gentle‑ai. Si se usan esos comandos, los artefactos van igual en `spec/features/NNN-nombre-feature/` (propose + spec → `spec.md`, design → `plan.md`, tasks → `tasks.md`), nunca en `openspec/`.
- **Modelos y subagentes (`AGENTS.md` §10):**
  - **Niveles:** el nivel **alto** es Opus, el **estándar** es Sonnet y el **económico** es Haiku. El esfuerzo de razonamiento va en el mismo orden: alto, medio y bajo.
  - **Sesión principal:** el modelo se elige en el selector de la aplicación (o con `/model` en la terminal) según la fase. Opus para spec, plan, ADR y revisión de seguridad; Sonnet para `tasks.md`, implementar tareas, Figma y documentación. Al cambiar de fase, abre una sesión nueva.
  - **Orquestador** (`liv-orquestador`, ADR 0033): corre en una sesión con Opus que solo coordina. Abre cada fase y cada tarea en una sesión nueva con la herramienta de sesiones de la aplicación de escritorio (`start_session`), en su propio *worktree* y con el modelo de su nivel. Tiene como máximo tres sesiones de trabajo a la vez. Nunca implementa en un subagente. Si no tiene esa herramienta (en la terminal) o esta no le deja fijar el modelo o el *worktree*, la persona abre cada sesión con el *brief* del orquestador.
  - **Subagentes del proyecto** (`.claude/agents/`): cada uno fija su modelo, su esfuerzo y sus herramientas, y se lanza por su nombre, sin pasar otro modelo salvo que el usuario lo pida. Son de solo lectura.

    | Subagente | Modelo | Úsalo para |
    |---|---|---|
    | `liv-explorador` | Haiku | Búsquedas, inventarios y resúmenes |
    | `liv-revisor-rapido` | Haiku | Lente de legibilidad antes de cada *commit* y verificación de correcciones puntuales |
    | `liv-revisor` | Sonnet | Revisión adversarial de cambios normales y lentes de resiliencia y confiabilidad |
    | `liv-revisor-critico` | Opus | Revisión adversarial de planes y de cambios críticos y lente de riesgo en cambios críticos |

  - **Tipos genéricos:** no se usan los subagentes genéricos (`general-purpose`, `Explore`, `Plan`) cuando uno del proyecto cubre la tarea.
- **Figma:** usa el servidor MCP oficial de Figma con la *skill* `liv-figma`. Si el plugin de Figma está instalado, sus *skills* `figma-use` y `figma-generate-library` la complementan; si no, basta con `liv-figma`.
- **Memoria (Engram), si está disponible:** guarda decisiones y descubrimientos con `mem_save` y cierra la sesión con `mem_session_summary`. Es opcional y personal de cada equipo: lo que afecte al equipo va al repositorio (ADR, `AGENTS.md`, `ESTADO.md`).
- **Ganchos y permisos:** los ganchos que bloquean rutas protegidas y la lista blanca de comandos se configurarán en `.claude/settings.json` como parte del entregable F0‑03. Hasta entonces, respeta las prohibiciones de `AGENTS.md` §4.5 sin excepción.
