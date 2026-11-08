# spec/ — Spec Driven Development

> Metodología SDD: primero se escribe la spec, luego el plan, luego las tareas, y solo entonces se toca el código. El código se escribe con TDD: primero la prueba.

## Estructura

```
spec/
├── constitution/            ← reglas estables del proyecto (cambian poco)
│   ├── mission.md           ← qué construimos y para quién
│   ├── tech-stack.md        ← tecnologías, convenciones y límites
│   └── roadmap.md           ← orden y estado de las features
└── features/                ← una carpeta por feature
    ├── NNN-nombre-feature/  ← plantilla: se copia, no se edita
    └── 001-nombre-feature/
        ├── spec.md          ← qué hace + criterios de aceptación
        ├── plan.md          ← cómo se implementa
        └── tasks.md         ← checklist de tareas
```

## Evaluación previa

Antes de escribir una spec, el agente evalúa la solicitud con los criterios de `AGENTS.md` §4.3 (ADR 0028). Si cumple alguno, sigue el flujo de abajo. Si no, va por el flujo directo, sin carpeta de feature.

## Flujo para una feature nueva

> Procedimiento detallado para personas y agentes: *skill* `liv-sdd-feature` (`.claude/skills/liv-sdd-feature/SKILL.md`). Las revisiones usan `liv-revision-adversarial` y `liv-revision-4r`.

1. Copiar la plantilla `features/NNN-nombre-feature/` a una carpeta nueva con el siguiente número libre (`001`, `002`, …) y un nombre corto en minúsculas con guiones, p. ej. `features/001-vigencias-fiscales/`. Ese identificador es también el de la rama (`feat/001-nombre-feature`) y el del PR.
2. Escribir `spec.md`: qué hace, por qué y criterios de aceptación medibles, cada uno con su código de regla o de criterio funcional.
   - **Aprobación H1:** el responsable funcional aprueba la spec. Sin H1 no hay plan.
3. Si la feature tiene pantallas: diseño en Figma aprobado, o declaración de reutilización de estructura (`AGENTS.md` §6.2).
4. Escribir `plan.md`: enfoque técnico y decisiones, respetando `constitution/tech-stack.md` y `docs/arquitectura/`.
   - Revisión adversarial independiente del plan.
   - **Aprobación H2:** arquitecto (más el responsable funcional si cambia una regla o hay pantallas; no hay diseñador dedicado). Sin H2 no hay tareas.
5. Desglosar en `tasks.md`: tareas pequeñas, cada una con los archivos que puede tocar y la prueba que se escribe primero.
6. Implementar tarea por tarea con TDD (rojo → verde → refactorizar) y marcar el progreso.
7. Validar: revisión adversarial independiente de lo implementado, cada criterio de `spec.md` marcado `[x]` con la prueba que lo demuestra, compuertas de CI en verde.
   - **Aprobación H3:** revisión humana del PR. Solo un humano integra a `main`.
8. Cerrar: mover la feature a "Hecho" en `constitution/roadmap.md` y actualizar `ESTADO.md` en el mismo cambio.

Si la feature la dirige un orquestador (*skill* `liv-orquestador`, ADR 0033), solo él marca el progreso de `tasks.md`, los criterios de `spec.md`, el roadmap y `ESTADO.md`; las sesiones de las tareas se lo reportan.

> La constitución manda: si una feature choca con `mission.md` o `tech-stack.md`, se replantea la feature, no la constitución. Cambiar la constitución exige un ADR aprobado.

## Equivalencia con las fases SDD de gentle‑ai

Para herramientas que usan los nombres de fase de gentle‑ai, los artefactos van en la carpeta de la feature con estos nombres:

| Fase gentle‑ai | Dónde queda |
|---|---|
| explore | No produce archivo; sus hallazgos alimentan `spec.md` |
| propose + spec | `spec.md` |
| design | `plan.md` |
| tasks | `tasks.md` |
| apply | Código y pruebas; progreso marcado en `tasks.md` |
| verify | Criterios marcados en `spec.md` y cierre de `tasks.md` |
| archive | `constitution/roadmap.md` + `ESTADO.md` |

## Flujo directo

Los cambios sencillos, bien definidos y de impacto acotado, que no cumplen ningún criterio de SDD, no crean carpeta de feature ni documentos de planificación. Llevan las pruebas, verificaciones, revisión y registro que pide `AGENTS.md` §4.3. Si al implementarlos aparece complejidad o riesgo, siguen este flujo desde `spec.md`.
