# ADR-0003 · Una base PostgreSQL; un esquema por módulo; lectura cruzada solo por vistas publicadas

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D5); `docs/arquitectura/01` §3.2 y §9; `docs/arquitectura/02` §5.2

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Las operaciones del núcleo deben ser transaccionales sin coordinación distribuida (impulsor D5), y cada módulo debe ser dueño de sus datos (doc. 01 §9).

## Opciones consideradas

No hay alternativas documentadas. El doc. 00 §4 descarta los microservicios por las sagas, pero no evalúa una base separada por módulo.

## Decisión

Una sola base PostgreSQL con un esquema por módulo. Un módulo solo escribe en su esquema (doc. 01 §9), y otro módulo lo lee solo por su fachada o por vistas publicadas `v_*` (doc. 01 §3.2).

## Consecuencias

- **Positivas:** transacciones locales entre módulos sin perder la propiedad de los datos.
- **Negativas o costos:** la lectura cruzada exige publicar y mantener vistas.
- **Qué cambia en el repositorio:**
  - el módulo `reporting` solo lee vistas `v_*`, y su rol de base de datos (`liv_reporting`) solo tiene `SELECT` sobre ellas (doc. 01 §3.2; doc. 02 §5.2);
  - **pendiente:** el doc. 01 §3.1, nota 1, propone un esquema `jurisdiction`, pero el documento funcional 03 (`docs/funcional/03`) §5.6 ubica la configuración en `core`. Queda por validar.
