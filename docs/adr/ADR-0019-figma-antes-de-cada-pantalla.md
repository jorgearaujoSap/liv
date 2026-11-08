# ADR-0019 · Diseño en Figma aprobado antes de implementar toda pantalla nueva, con la excepción de las pantallas que copian la estructura de otra existente

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §7 (P10); `docs/arquitectura/01` §11.2; `docs/arquitectura/05` §3.1 y §4.2

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El responsable funcional aprueba cómo se ve cada pantalla (doc. 00 §7, P10), y el diseño es una compuerta del SDD para las features con interfaz (doc. 05 §3.1).

## Opciones consideradas

No hay alternativas documentadas.

## Decisión

Ninguna pantalla nueva se implementa sin su diseño aprobado en el archivo LIV‑Diseño de Figma. La única excepción es una pantalla que copia la estructura de otra ya diseñada y solo cambia el contenido; el `plan.md` debe declararlo (doc. 01 §11.2).

## Consecuencias

- **Positivas:** lo que se implementa es lo que se aprobó.
- **Negativas o costos:** el diseño es un paso previo a la implementación.
- **Qué cambia en el repositorio:**
  - un solo archivo de Figma, con todas las pantallas en la página `03 Pantallas` (doc. 01 §11.2);
  - cada diseño incluye todos sus estados y usa solo componentes de la librería (doc. 01 §11.2);
  - la excepción tiene sus condiciones en el doc. 01 §11.2, y no aplica si hace falta un componente o una variante nueva;
  - el doc. 05 §4.2 prohíbe crear una pantalla sin diseño aprobado.
