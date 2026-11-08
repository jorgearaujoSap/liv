# ADR-0015 · *Trunk‑based development*, Conventional Commits y versionado semántico

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/03`; `docs/arquitectura/04` §2 y §8

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

`main` debe estar siempre lista para desplegar, con ramas cortas (doc. 04 §8). El documento no detalla más motivos.

## Opciones consideradas

No hay alternativas documentadas.

## Decisión

*Trunk‑based development*, con ramas de dos días de vida ideal, mensajes Conventional Commits y versionado semántico por proyecto (doc. 04 §8).

## Consecuencias

- **Positivas:** historia lineal y versiones que dicen qué tipo de cambio traen.
- **Negativas o costos:** el trabajo largo se parte en cambios pequeños, o se esconde detrás de una *feature flag* con fecha de retiro (doc. 03).
- **Qué cambia en el repositorio:**
  - integración con *squash*, historia lineal y un PR por proyecto (doc. 04 §8);
  - commitlint para los mensajes (doc. 04 §2); el CI ya comprueba que el título del PR siga Conventional Commits (`sistema-pr`);
  - versiones independientes por proyecto (doc. 04 §8);
  - **pendiente:** el doc. 04 §8 deja el idioma de los mensajes «a decidir en la Fase 0». En la práctica, el historial está en español.
