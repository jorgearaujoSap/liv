# ADR-0018 · Librería de componentes propia `liv-components` en el monorepo; las pantallas solo componen sus componentes; *tokens* generados desde Figma

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/00` §7 (P10); `docs/arquitectura/01` §11.1; ADR 0022; ADR 0024

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

Todas las pantallas deben verse y comportarse igual, y un ajuste a un componente debe llegar a todas (doc. 01 §11.1). El diseño se aprueba en Figma (doc. 00 §7, P10).

## Opciones consideradas

No hay alternativas documentadas para la librería en general. El doc. 01 §11 excluye las librerías de componentes de terceros.

## Decisión

Una librería de componentes propia, `liv-components`, de solo presentación: sin `HttpClient` ni reglas de negocio. Las pantallas solo componen sus componentes, y los *tokens* salen de los estilos de Figma (doc. 01 §11.1).

## Consecuencias

- **Positivas:** una sola línea visual, y un cambio se refleja en todas las pantallas.
- **Negativas o costos:** todo componente nuevo pasa primero por Figma y por la librería.
- **Qué cambia en el repositorio:**
  - los *tokens* de color, tipografía y sombra vienen de los estilos de Figma, y el espaciado y los radios de su colección `Layout` (doc. 01 §11.1);
  - una pantalla no redefine los estilos de un componente (doc. 01 §11.1);
  - cada componente cumple WCAG 2.1 AA por sí mismo (doc. 01 §11.1).
- **Modificado en parte:**
  - **ADR 0022:** la librería pasa a ser el proyecto autónomo `ui/` y se publica en npm, en lugar de vivir dentro del *frontend* y consumirse por alias de código fuente;
  - **ADR 0024:** el tema de Keycloak es una excepción a que todo se construya con la librería.
