# ADR-0007 · Spring Batch para procesos masivos; worker separado

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D3), §3 y §5; `docs/arquitectura/01` §12; `docs/arquitectura/03` §2; `docs/funcional/03` §2 (avalúos); `docs/funcional/02` §B.13 (LM‑05); `docs/funcional/04` §5.3 (IM‑06)

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El sistema procesa volúmenes grandes que deben poder reiniciarse sin duplicar (impulsor D3):
- 389.530 avalúos;
- la emisión masiva en bloques transaccionales, por ejemplo de 1.000 vehículos (LM‑05);
- exportaciones de más de 50.000 filas;
- la imputación de pagos sin duplicados (IM‑06).

## Opciones consideradas

1. **Spring Batch:** procesamiento por bloques (*chunks*), repositorio de ejecuciones, reinicio y particionamiento nativos (doc. 00 §3).
2. **Lotes sobre BullMQ y Redis,** dentro de la alternativa NestJS: habría que construirlos (doc. 00 §3, criterio «Lotes reiniciables»).

## Decisión

Spring Batch para los procesos masivos, en el rol worker, separado del rol API (doc. 01 §12).

## Consecuencias

- **Positivas:** reinicio desde el último bloque confirmado, con los insumos cargados una sola vez (doc. 03 §2.5).
- **Negativas o costos:** el worker es un rol más que desplegar y vigilar.
- **Qué cambia en el repositorio:**
  - el worker escala horizontalmente con `SKIP LOCKED` (doc. 03 §2.1);
  - el repositorio de ejecuciones vive en PostgreSQL, y el progreso se consulta por la API (doc. 01 §12);
  - las tareas programadas usan ShedLock (doc. 00 §5);
  - agregar Redis exige un ADR (doc. 03 §2.2).
