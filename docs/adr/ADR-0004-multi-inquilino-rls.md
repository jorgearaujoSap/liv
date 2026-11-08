# ADR-0004 · Multi‑inquilino por `administration_id` con RLS

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D8 y D16); `docs/arquitectura/02` §5; `docs/arquitectura/01` §9

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Cada administración tributaria solo puede ver y modificar sus propios datos (impulsor D8), y el producto es multi‑inquilino desde el inicio, dimensionado para departamentos (D16).

## Opciones consideradas

No hay alternativas documentadas.

## Decisión

Aislamiento por `administration_id` con políticas RLS de PostgreSQL (doc. 00 §2, D8; doc. 02 §5.1).

## Consecuencias

- **Positivas:** el aislamiento lo aplica la base de datos, no solo el código de la aplicación.
- **Negativas o costos:** cada tabla con datos de una administración necesita su política, y cada transacción debe fijar el contexto.
- **Qué cambia en el repositorio:**
  - el contexto se fija por transacción, y sin contexto no se ve ninguna fila (doc. 02 §5.1);
  - el rol de la aplicación (`liv_app`) no tiene `BYPASSRLS` (doc. 02 §5.2);
  - las FK son compuestas con `administration_id`, para que no mezclen administraciones (doc. 02 §5.1; doc. 01 §9);
  - una suite de aislamiento con Testcontainers corre en cada PR (doc. 02 §5.3).
