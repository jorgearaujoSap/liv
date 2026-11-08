# ADR-0001 · Backend en Java 21 LTS + Spring Boot

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2, §3, §5 y §7 (P3)

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El núcleo del sistema calcula dinero con exactitud (impulsor D1), procesa lotes grandes que deben poder reiniciarse (D3), necesita consistencia transaccional en la cuenta corriente (D5 y D6) y exporta Excel grande sin agotar la memoria (D11). El doc. 00 §3 evalúa el *backend* con criterios ponderados a partir de esos impulsores.

## Opciones consideradas

1. **Java 21 LTS y Spring Boot.** 187 de 200 puntos en la evaluación del doc. 00 §3: `BigDecimal`, Spring Batch, Flyway y Apache POI son herramientas maduras para cada impulsor.
2. **NestJS (Node.js).** 153 de 200 (doc. 00 §3). A favor, el mismo lenguaje que el *frontend*. En contra: los lotes reiniciables habría que construirlos sobre BullMQ y Redis, y los ORM de Node tienen soporte limitado de RLS.

## Decisión

Java 21 LTS con Spring Boot. Encaja mejor con los impulsores del núcleo, y el equipo ya tiene experiencia en Java y Spring (doc. 00 §3 y §7, P3).

## Consecuencias

- **Positivas:** herramientas maduras para dinero exacto, lotes, migraciones y Excel en *streaming*.
- **Negativas o costos:** el *backend* y el *frontend* usan lenguajes distintos. Se mitiga con el cliente del *frontend* generado desde el OpenAPI (ADR 0005, doc. 00 §3).
- **Qué cambia en el repositorio:** Java 21 y Spring Boot 4.x (doc. 00 §5). Pasar a Java 25 exige un ADR (doc. 00 §5).
