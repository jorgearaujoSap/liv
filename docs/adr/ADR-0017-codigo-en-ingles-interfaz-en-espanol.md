# ADR-0017 · Código en inglés (alineado con el ERD); interfaz y documentación en español; glosario obligatorio

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/01` §15; ERD

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Las personas y los agentes deben hablar del negocio con las mismas palabras, y el código debe usar los mismos nombres que el modelo de datos (doc. 01 §15).

## Opciones consideradas

No hay alternativas documentadas.

## Decisión

El código usa los nombres en inglés del ERD. La interfaz, la documentación y los nombres visibles de las pruebas van en español. El glosario es obligatorio y se amplía con cada término nuevo (doc. 01 §15).

## Consecuencias

- **Positivas:** el código coincide con las tablas, y lo que ve la persona coincide con los documentos funcionales.
- **Negativas o costos:** dos idiomas que mantener alineados.
- **Qué cambia en el repositorio:**
  - el glosario es la tabla español‑inglés del doc. 01 §15 (`AGENTS.md` §4.4);
  - **pendiente:** el doc. 01 §15 nombra un archivo `docs/glosario.md` que no existe. Hoy el glosario es la tabla del propio §15.
