# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Las reglas del proyecto son las mismas para todos los agentes y están en `AGENTS.md`, que se importa aquí completo:

@AGENTS.md

---

## Particularidades de Claude Code

Este archivo no repite ni modifica reglas: cualquier regla nueva o cambio va en `AGENTS.md`, para que la sigan todos los agentes. Aquí solo va lo específico de Claude Code.

- **Figma:** usa el servidor MCP oficial de Figma. Antes de cualquier lectura o escritura, crea una sesión con la clave del archivo (`s2r2Kdu31sQW5CmVeQNyZb`). Carga la *skill* `figma-use` antes de cada escritura, y `figma-generate-library` para estilos, variables o componentes. Las escrituras se hacen de una en una, nunca en paralelo.
- **Revisiones (gentle‑ai):** la revisión adversarial independiente de `AGENTS.md` §4.2 es `judgment-day`; la revisión de riesgo, resiliencia, legibilidad y confiabilidad de los cambios críticos son `review-risk`, `review-resilience`, `review-readability` y `review-reliability`. Las rutas críticas de `AGENTS.md` §4.3 equivalen, para la regla global, a las rutas `auth/update/security/payments`.
- **Comandos SDD de gentle‑ai:** si los usas, los artefactos se guardan en `spec/features/NNN-nombre-feature/` con los nombres de la plantilla del proyecto (propose + spec → `spec.md`, design → `plan.md`, tasks → `tasks.md`), según la tabla de `spec/README.md`. No uses `openspec/` ni guardes los artefactos solo en Engram.
- **Memoria (Engram):** guarda decisiones y descubrimientos con `mem_save` y cierra la sesión con `mem_session_summary`. Lo que afecte al equipo va además al repositorio (ADR, `AGENTS.md`, `ESTADO.md`): la memoria del agente no es fuente de verdad.
- **Ganchos y permisos:** los ganchos que bloquean rutas protegidas y la lista blanca de comandos se configurarán en `.claude/settings.json` como parte del entregable F0‑03. Hasta entonces, respeta las prohibiciones de `AGENTS.md` §4.5 sin excepción.
