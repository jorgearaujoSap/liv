---
name: liv-revision-adversarial
description: Revisión adversarial independiente del proyecto LIV con dos revisores ciegos que buscan fallos en un plan o en código ya implementado. Úsala después de escribir el plan.md de una feature (antes de la aprobación H2), después de implementar (antes de abrir el PR) y cuando el usuario pida "revisión adversarial", "judgment-day", "juzgar" o "buscar fallos". Reemplaza a judgment-day de gentle-ai.
---

# Revisión adversarial independiente

Objetivo: que alguien distinto de quien escribió el trabajo intente demostrar que está mal. Quien implementó **no** se revisa a sí mismo (`AGENTS.md` §4.1, principio 7).

## 1. Definir el objetivo

- **Plan:** `spec.md` + `plan.md` de la feature, y los documentos funcionales citados.
- **Implementación:** el diff de la rama frente a `main`, más `spec.md`, `plan.md` y `tasks.md`.

## 2. Lanzar dos revisores ciegos

- Si tu herramienta permite subagentes, lanza **dos en paralelo**, con contexto nuevo. Ninguno ve el resultado del otro.
- Si no los permite, haz **dos pasadas separadas**, idealmente en sesiones nuevas, y no relees la primera hasta terminar la segunda.
- Cada revisor recibe solo el objetivo y esta lista, nunca la opinión de quien implementó.

Cada revisor busca, como mínimo:

| Tema | Pregunta |
|---|---|
| Fidelidad funcional | ¿Cada criterio de `spec.md` se cumple tal como dice el documento funcional y su código de regla? ¿Hay reglas del documento que faltan? |
| Dinero y cálculo | ¿Aritmética exacta (`BigDecimal`), redondeo solo al final, ningún valor tributario en el código? ¿Los ejemplos *golden* dan al peso? |
| Invariantes del dominio | ¿Se respeta que lo publicado no se modifique, que los saldos no se guarden, la segregación cargador ≠ aprobador y el aislamiento por `administration_id`? |
| Casos límite | Bordes de rangos ("hasta" / "más de"), vigencias, fechas de causación, vehículos nuevos, archivos vacíos o duplicados, reintentos |
| Arquitectura | ¿Respeta las capas, las fronteras entre módulos y la independencia backend/frontend (`AGENTS.md` §4.6)? |
| Pruebas | ¿Las pruebas fallarían si el código estuviera mal? ¿Hay pruebas que no verifican nada? |
| Alcance | ¿Se tocó algo fuera de los archivos de la tarea? ¿Se resolvió un pendiente funcional con un supuesto? |

Formato de cada hallazgo: **severidad** (crítico / alto / medio / bajo), **ubicación** (`archivo:línea` o sección), **regla o criterio afectado**, **por qué está mal** y **corrección propuesta**.

## 3. Consolidar

| Caso | Tratamiento |
|---|---|
| Lo encontraron los dos | **Confirmado**: se corrige |
| Lo encontró uno solo | **Sospechoso**: se verifica contra la fuente (documento, prueba, código). Si se confirma, se corrige; si no, se descarta explicando por qué |
| Contradice un documento funcional | Se detiene y se pregunta al humano; no se "corrige" la norma |

## 4. Corregir y volver a juzgar

1. Corrige los hallazgos confirmados (con TDD si es código).
2. Repite la revisión solo sobre lo corregido. **Máximo dos rondas**; si en la segunda siguen apareciendo críticos o altos, detente y escala al humano.

## 5. Registrar

- Para un **plan**: en la sección **Revisión** de `plan.md`, anota la fecha, el número de hallazgos por severidad, qué se corrigió y qué se descartó con su motivo.
- Para una **implementación**: el mismo resumen en la descripción del PR.
- No digas que la revisión pasó si quedaron hallazgos críticos o altos sin resolver.
