# ADR-0009 · Motor de reglas propio, determinista y dirigido por configuración (sin Drools ni BPM)

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D2) y §6; `docs/arquitectura/01` §5

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Ninguna regla tributaria puede estar en el código: el cálculo lee una configuración de jurisdicción versionada (doc. 01 §5.1, M1 y M2; impulsores D2 y D9).

## Opciones consideradas

1. **Motor propio, determinista y dirigido por configuración.**
2. **Drools o un motor BPM.** La fila del ADR en el doc. 00 §6 los excluye («sin Drools ni BPM»). No hay una evaluación documentada.

## Decisión

Un motor propio: una función pura `liquidar(entrada, reglas)` sobre una instantánea inmutable de la configuración publicada (doc. 01 §5.1, M1).

## Consecuencias

- **Positivas:** el mismo resultado para la misma entrada y la misma configuración, con traza de cada paso.
- **Negativas o costos:** el motor y su traza se construyen y se mantienen en el proyecto.
- **Qué cambia en el repositorio:**
  - el mismo esquema de pasos con traza se usa en la homologación, la imputación, la distribución y los validadores (doc. 01 §5.2);
  - el motor usa la instantánea publicada, no el archivo de configuración (doc. 01 §5.3).
