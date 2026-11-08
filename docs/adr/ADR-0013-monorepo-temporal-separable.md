# ADR-0013 · Monorepo temporal y separable: *backend* y *frontend* son proyectos autónomos; el único contrato entre ellos es el OpenAPI

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/01` §14 y §14.1 (IN‑01 a IN‑11)

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El *backend* y el *frontend* conviven en un repositorio por ahora, pero cada uno tendrá el suyo. Separarlos no debe exigir cambios de código (doc. 01 §14).

## Opciones consideradas

No hay alternativas evaluadas. El doc. 01 §14.1 (IN‑02) prohíbe las herramientas que unirían los proyectos: *workspaces* de npm o pnpm, un `pom.xml` agregador en la raíz, Nx y Turborepo.

## Decisión

Monorepo temporal y separable: cada proyecto es autónomo, sin archivos, herramientas ni rutas compartidas, y el único contrato entre el *backend* y el *frontend* es el OpenAPI que publica el *backend*. La documentación, el SDD, las pruebas de sistema y la infraestructura compartida se quedan en este repositorio. Separar un proyecto debe ser «mover la carpeta» (doc. 01 §14.1).

## Consecuencias

- **Positivas:** se puede separar en cualquier momento.
- **Negativas o costos:** cada proyecto duplica su propia configuración de calidad, ganchos y versión.
- **Qué cambia en el repositorio:**
  - el *frontend* nunca lee `../backend` (IN‑04);
  - un PR toca un solo proyecto (IN‑07);
  - `e2e/` usa los proyectos solo por HTTP (IN‑09; ADR 0030);
  - la librería `ui/` se sumó como tercer proyecto autónomo, y se comparte solo como paquete npm (IN‑04b; ADR 0022);
  - la separación se hará cuando se decida (doc. 01 §14.1).
