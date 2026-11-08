# ADR-0021 · Backend con la base que el equipo ya usa, mejorada: Maven con *wrapper*, un artefacto y módulos como paquetes

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §3, §5, §6 y §7 (P3); `docs/arquitectura/01` §4 y §14; `docs/arquitectura/04` §2

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El equipo ya tiene una base de proyectos Java con Spring (doc. 00 §5; doc. 00 §7, P3). Se toma esa base y se mejora, en lugar de partir de cero (doc. 01 §14).

## Opciones consideradas

1. **Gradle con un subproyecto por módulo.** Es lo que este ADR reemplaza (doc. 00 §6). La serie de arquitectura no documenta una evaluación entre las dos.
2. **Maven con *wrapper* y un solo artefacto,** con los módulos como paquetes: la base del equipo.

## Decisión

Maven con *wrapper* y un artefacto (`liv-api`) en la raíz de `backend/`. Los módulos son paquetes con capas `api`, `application`, `domain` e `infrastructure`, y la API pública de cada módulo está en su paquete raíz. Las migraciones van en `src/main/resources/db/migration`, las pruebas guardianas de catálogo, capas y tamaño no tienen excepciones, y el formato es Google Java Format (doc. 00 §6; doc. 01 §4 y §14).

## Consecuencias

- **Positivas:** la estructura que el equipo ya conoce, con las fronteras comprobadas por pruebas.
- **Negativas o costos:** las fronteras entre módulos dependen de las pruebas de arquitectura, no de subproyectos separados.
- **Qué cambia en el repositorio:**
  - `ModuleCatalogTest` comprueba los módulos y sus capas (doc. 01 §4);
  - dos plantillas de módulo: hexagonal, o simplificada para los registros (doc. 01 §4.1 y §4.2);
  - Google Java Format, aplicado con Spotless (doc. 04 §2).
