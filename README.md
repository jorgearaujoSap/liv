# LIV — Impuesto vehicular

Sistema de liquidación y recaudo del impuesto anual de vehículos automotores de Colombia (Ley 488 de 1998), configurable por jurisdicción. Primer caso: municipio de Ipiales (Nariño).

> **Estado:** Fase 0 — Fundaciones, en definición. Todavía no hay código. El estado al día está en [`ESTADO.md`](ESTADO.md).

## Mapa del repositorio

```
/
├── README.md                ← este archivo
├── AGENTS.md                ← reglas y metodología para cualquier agente de IA (y para el equipo)
├── CLAUDE.md                ← importa AGENTS.md + detalles de Claude Code
├── ESTADO.md                ← estado del proyecto, decisiones y bitácora
├── docs/
│   ├── funcional/           ← especificación funcional: documentos 01–04 y ERD
│   │   └── anexos/          ← configuración de jurisdicción (YAML) y prevalidación de tablas 2026 (Excel)
│   └── arquitectura/        ← decisiones, arquitectura, seguridad, operación, calidad y gobierno de agentes (00–05)
└── spec/                    ← desarrollo guiado por especificaciones (SDD)
    ├── constitution/        ← misión, stack y convenciones, roadmap
    └── features/            ← una carpeta por feature (spec, plan, tareas)
```

## Lo que viene: monorepo separable

`backend/` (Java + Spring Boot) y `frontend/` (Angular + `@liv/ui`) se crearán aquí como **proyectos autónomos** que después pasarán a repositorios propios. No comparten archivos, herramientas ni rutas; su único contrato es el OpenAPI que publica el backend. Este repositorio conservará la documentación, `spec/`, las pruebas de sistema (`e2e/`, `perf/`), la infraestructura compartida (`infra/`) y las herramientas (`tools/`).

Estructura y reglas: [`docs/arquitectura/01_Arquitectura_de_Software.md`](docs/arquitectura/01_Arquitectura_de_Software.md) §14.

## Por dónde empezar

1. [`ESTADO.md`](ESTADO.md) — dónde está el proyecto.
2. [`AGENTS.md`](AGENTS.md) — cómo se trabaja aquí (también para personas).
3. [`docs/funcional/`](docs/funcional/) — qué hace el sistema.
4. [`docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md`](docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md) — decisiones técnicas y hoja de ruta.
5. [`spec/README.md`](spec/README.md) — cómo se especifica y desarrolla una feature.

Diseño: archivo de Figma [LIV‑Diseño](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o).
