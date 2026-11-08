# ADR-0012 · Tailwind CSS en el `.component.css` de cada componente; hoja global solo para estilos reutilizados

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §5; `docs/arquitectura/01` §11 y §11.1; `docs/arquitectura/04` §2

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Cada componente declara sus propios estilos, y la hoja global solo guarda lo que se reutiliza (doc. 00 §5; doc. 01 §11). La librería busca que un ajuste se refleje en todas las pantallas (doc. 01 §11.1).

## Opciones consideradas

No hay alternativas documentadas. El doc. 01 §11 solo excluye las librerías de componentes de terceros.

## Decisión

Tailwind CSS dentro del `.component.css` de cada componente, con los *tokens* de `liv-components` (doc. 01 §11).

## Consecuencias

- **Positivas:** los estilos de cada componente quedan junto a él, y solo con *tokens*.
- **Negativas o costos:** hay que vigilar que nadie use utilidades en la plantilla ni estilos sueltos.
- **Qué cambia en el repositorio:**
  - los *tokens* salen de `liv-components/styles/theme.css` (doc. 01 §11);
  - Stylelint debe permitir solo *tokens* y prohibir `::ng-deep` (doc. 04 §2); todavía no está activo (ADR 0031);
  - el tema de Keycloak es una excepción (doc. 01 §11.1; ADR 0024).
