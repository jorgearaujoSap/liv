# ADR-0006 · JPA para escritura de agregados; jOOQ para consultas, reportes y masivos

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §3 y §5; `docs/arquitectura/01` §9; `docs/arquitectura/03` §2.5

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

La escritura de agregados necesita concurrencia optimista, y las consultas, los reportes y las escrituras masivas necesitan SQL tipado y eficiente (doc. 00 §5, filas de persistencia).

## Opciones consideradas

No hay alternativas documentadas para esta decisión. Dentro de la evaluación de NestJS, el doc. 00 §3 solo señala que los ORM de Node tienen soporte limitado de RLS.

## Decisión

JPA para cargar y guardar agregados, y jOOQ para consultas con filtros, reportes, exportaciones y escrituras masivas (doc. 01 §9). jOOQ es nativo del ecosistema Java (doc. 00 §3).

## Consecuencias

- **Positivas:** cada herramienta se usa donde es más fuerte.
- **Negativas o costos:** dos formas de acceder a la base que el equipo debe dominar.
- **Qué cambia en el repositorio:**
  - jOOQ genera su código desde el esquema ya migrado en CI, así que un SQL incompatible falla al compilar (doc. 01 §9);
  - las escrituras masivas van en lotes (doc. 03 §2.5).
