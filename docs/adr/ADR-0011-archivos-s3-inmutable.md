# ADR-0011 · Archivos en almacenamiento S3 compatible e inmutable

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §5 y §7 (P7); `docs/arquitectura/01` §7; `docs/arquitectura/02` §7; `docs/arquitectura/03`

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Los archivos originales que se cargan deben conservarse sin cambios, con su SHA‑256, y validarse por su contenido y con antivirus (ClamAV) antes de usarse (doc. 02 §7).

## Opciones consideradas

No hay alternativas documentadas al almacenamiento S3. MinIO se usa solo en desarrollo local y en pruebas, como otra implementación del mismo puerto (doc. 00 §5; doc. 01 §7).

## Decisión

Almacenamiento compatible con S3, con versionado y Object Lock, dentro de AWS (doc. 00 §5; ADR 0020).

## Consecuencias

- **Positivas:** los originales no se pueden alterar.
- **Negativas o costos:** el costo de conservar todas las versiones (doc. 03).
- **Qué cambia en el repositorio:**
  - acceso por URL firmadas de corta duración, que quedan en la auditoría, y exportaciones con fecha de expiración (doc. 02 §7);
  - **pendiente:** mientras no exista la tabla de retención documental (doc. 00 §7, P7), no se borra nada, y el doc. 00 §7 (P7) lo deja como provisional;
  - MinIO y ClamAV en el entorno local llegan con la primera feature que suba archivos (ADR 0031).
