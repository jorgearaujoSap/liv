# ADR-0014 · Proceso SDD + TDD obligatorio, también para agentes de IA

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §1; `docs/arquitectura/04` §4.1; `docs/arquitectura/05` §1, §3 y §13

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El proyecto quiere evitar el desarrollo improvisado (doc. 00 §1), y los agentes de IA tienden a completar lo que no está especificado (doc. 05 §1).

## Opciones consideradas

No hay alternativas evaluadas. El doc. 05 §13 describe como antipatrón escribir «código primero, pruebas al final (o nunca)».

## Decisión

Especificación antes del código (SDD) y prueba antes de la implementación (TDD), para personas y agentes. Sin especificación aprobada no hay código (G1), primero la prueba (G2) y, ante la duda, detenerse y preguntar (G8) (doc. 05 §1).

## Consecuencias

- **Positivas:** trazabilidad de la norma a la prueba y al código, y decisiones humanas en puntos fijos.
- **Negativas o costos:** más pasos antes de escribir código.
- **Qué cambia en el repositorio:**
  - aprobaciones humanas H1, H2 y H3 (doc. 05 §3.1);
  - el ciclo rojo, verde y refactorizar es obligatorio en `domain` y `application` (doc. 04 §4.1);
  - quien implementa no revisa (G7, doc. 05 §1);
  - el ADR 0028 agregó la evaluación previa y el flujo directo para los cambios que no necesitan el SDD.
