# ADR-0016 · Tipo `Dinero` sobre `BigDecimal`; redondeo solo al final y según la configuración

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §2 (D1) y §3; `docs/arquitectura/01` §3.1, §5.1, §7 y §9; `docs/arquitectura/04` §1 y §3.1

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Los montos deben ser exactos, sin `float` ni `double`, y se redondean solo al final (impulsor D1; doc. 01 §5.1, M6).

## Opciones consideradas

No hay alternativas documentadas para el tipo. El doc. 00 §3 compara `BigDecimal` con decimal.js o big.js, pero como criterio para elegir el lenguaje del *backend* (ADR 0001).

## Decisión

Un tipo de valor `Dinero` en el módulo `shared`, sobre `BigDecimal` (doc. 01 §3.1 y §7). El redondeo se hace una sola vez, con el método que dice la configuración de jurisdicción (doc. 01 §7; doc. 01 §5.1, M6).

## Consecuencias

- **Positivas:** aritmética exacta en todo el cálculo.
- **Negativas o costos:** todo el código de dinero pasa por el tipo, no por números sueltos.
- **Qué cambia en el repositorio:**
  - en la base se guarda `NUMERIC(18,2)`, y los intermedios con escala de 6 o más solo existen en memoria y en la traza (doc. 01 §9);
  - una prueba de arquitectura prohíbe `float` y `double` en `domain` y `application` de `tax`, `ledger`, `valuation`, `portfolio` y `jurisdiction` (doc. 04 §1, CC‑02; doc. 04 §3.1);
  - el tipo se crea con la primera feature que maneje dinero: todavía no existe en el código.
