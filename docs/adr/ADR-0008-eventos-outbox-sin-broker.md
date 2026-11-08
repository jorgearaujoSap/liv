# ADR-0008 · Eventos entre módulos con *outbox* en PostgreSQL; sin *broker* de mensajes hasta que se justifique

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D5), §5 y §6; `docs/arquitectura/01` §3.2; `docs/arquitectura/03` §2.3

> Archivo creado el 2026-11-07 (entregable F0‑02, ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Algunos efectos entre módulos son asíncronos o van «hacia atrás» en las dependencias, por ejemplo `LiquidacionEmitida` (doc. 01 §3.2). Además, deben ser consistentes con la transacción que los origina (impulsor D5).

## Opciones consideradas

1. **Registro persistente de eventos de Spring Modulith (*outbox*) en PostgreSQL:** el evento se guarda en la misma transacción que el cambio (doc. 01 §3.2; doc. 00 §5).
2. **Un *broker* de mensajes (RabbitMQ o Kafka):** se reevalúa solo si aparece uno de los disparadores del doc. 03 §2.3.

## Decisión

Eventos con *outbox* en PostgreSQL. No se agrega un *broker* hasta que se justifique (doc. 00 §6).

## Consecuencias

- **Positivas:** sin infraestructura adicional; el evento se guarda en la misma transacción que el cambio y se entrega al menos una vez.
- **Negativas o costos:** la entrega es «al menos una vez», así que los consumidores deben ser idempotentes (doc. 01 §3.2).
- **Qué cambia en el repositorio:** el *broker* se reevalúa si hay integración en tiempo real, más de un despliegue independiente o una latencia sostenida fuera del objetivo (doc. 03 §2.3).
