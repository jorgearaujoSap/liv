# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Las reglas del proyecto son las mismas para todos los agentes y están en `AGENTS.md`, que se importa aquí completo:

@AGENTS.md

---

## Particularidades de Claude Code

Este archivo no repite ni modifica reglas: cualquier regla nueva o cambio va en `AGENTS.md`, para que la sigan todos los agentes. Aquí solo va lo específico de Claude Code.

- **Skills del proyecto:** están en `.claude/skills/` y Claude Code las carga solo al abrir el repositorio, en cualquier equipo. Son `liv-sdd-feature`, `liv-revision-adversarial`, `liv-revision-4r`, `liv-figma`, `liv-adr` y `liv-estado` (para qué sirve cada una: `AGENTS.md` §9). **Prevalecen sobre las *skills* globales** que pueda tener un equipo concreto:
  - `liv-revision-adversarial` reemplaza a `judgment-day`;
  - `liv-revision-4r` reemplaza a `review-risk`, `review-resilience`, `review-readability` y `review-reliability`;
  - `liv-sdd-feature` reemplaza a los comandos SDD de gentle‑ai. Si se usan esos comandos, los artefactos van igual en `spec/features/NNN-nombre-feature/` (propose + spec → `spec.md`, design → `plan.md`, tasks → `tasks.md`), nunca en `openspec/`.
- **Figma:** usa el servidor MCP oficial de Figma con la *skill* `liv-figma`. Si el plugin de Figma está instalado, sus *skills* `figma-use` y `figma-generate-library` la complementan; si no, basta con `liv-figma`.
- **Memoria (Engram), si está disponible:** guarda decisiones y descubrimientos con `mem_save` y cierra la sesión con `mem_session_summary`. Es opcional y personal de cada equipo: lo que afecte al equipo va al repositorio (ADR, `AGENTS.md`, `ESTADO.md`).
- **Ganchos y permisos:** los ganchos que bloquean rutas protegidas y la lista blanca de comandos se configurarán en `.claude/settings.json` como parte del entregable F0‑03. Hasta entonces, respeta las prohibiciones de `AGENTS.md` §4.5 sin excepción.
