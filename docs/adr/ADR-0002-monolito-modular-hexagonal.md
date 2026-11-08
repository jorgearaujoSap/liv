# ADR-0002 · Monolito modular (Spring Modulith) con hexagonal por módulo; roles API y worker

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §4; `docs/arquitectura/01` §3; `docs/arquitectura/03` §2.1

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Un monolito sin fronteras termina en una «bola de lodo», y el núcleo contable no admite sagas ni transacciones distribuidas (impulsor D5, doc. 00 §4; D6, doc. 00 §2).

## Opciones consideradas

1. **Monolito en capas tradicional.** Descartado: no impone fronteras entre contextos (doc. 00 §4).
2. **Microservicios.** Descartado: obligaría a sagas en el núcleo contable y a varios despliegues desde el inicio (doc. 00 §4).
3. **Monolito modular con hexagonal por módulo.** Fronteras verificadas en el *build*, una sola base transaccional, un despliegue y la posibilidad de extraer módulos más adelante (doc. 00 §4, veredicto).

## Decisión

Monolito modular con Spring Modulith y arquitectura hexagonal en cada módulo. Un solo artefacto que corre con dos roles: API y worker.

## Consecuencias

- **Positivas:** fronteras comprobadas automáticamente y transacciones locales.
- **Negativas o costos:** la disciplina de módulos se mantiene con pruebas de arquitectura, no por separación física.
- **Qué cambia en el repositorio:**
  - un módulo por contexto (doc. 01 §3.1), con fronteras verificadas por `ModularityTest` (doc. 01 §3.2);
  - roles API y worker del mismo artefacto (doc. 03 §2.1);
  - un módulo nuevo o una dependencia nueva entre módulos exige un ADR (doc. 01 §3.2).
