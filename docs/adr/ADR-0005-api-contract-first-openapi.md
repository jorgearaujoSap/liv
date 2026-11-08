# ADR-0005 · API REST *contract‑first* con OpenAPI; cliente Angular generado

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §3 y §5; `docs/arquitectura/01` §10

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El *backend* y el *frontend* necesitan un único contrato entre ellos (doc. 01 §10). Además, el doc. 00 §3 propone el cliente generado como mitigación de que los dos usen lenguajes distintos (ADR 0001).

## Opciones consideradas

No hay alternativas documentadas.

## Decisión

API REST *contract‑first*: primero se escribe y se aprueba el OpenAPI, y luego se genera el código (doc. 01 §10). Las interfaces de Spring y el cliente de Angular salen del mismo contrato (doc. 00 §5).

## Consecuencias

- **Positivas:** el contrato es la fuente de verdad, y los dos lados se generan desde él.
- **Negativas o costos:** un cambio de API pasa siempre primero por el contrato.
- **Qué cambia en el repositorio:**
  - los contratos viven en `backend/contracts/openapi/`, y el *frontend* usa una copia fijada en `frontend/api-spec/` (doc. 01 §10);
  - prefijos `/api/v1` y `/internal/v1` (doc. 01 §10);
  - el código generado no se edita a mano, y un comparador de cambios incompatibles corre en CI (doc. 01 §10; ADR 0027).
