# ADR-0022 · La librería `liv-components` es un proyecto autónomo en la raíz del monorepo (`ui/`), publicado en npm e instalado con versión exacta

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** el equipo del proyecto, en la revisión del 2026-11-01 en que se aceptaron los ADR 0001–0025 (doc. 00 §6 y bitácora de `ESTADO.md`)
- **Documentos relacionados:** `docs/arquitectura/01` §11.1, §14 y §14.1 (IN‑02, IN‑04b, IN‑07); ADR 0013; ADR 0018

> Archivo creado el 2026-11-07 (entregable F0‑02; el criterio de cierre de la Fase 0 está en el ADR 0031) a partir de lo que ya estaba documentado en la serie de arquitectura. No cambia la decisión: la resume y cita dónde está su detalle.

## Contexto

El monorepo debe poder separarse moviendo carpetas (ADR 0013; doc. 01 §14). Con la librería dentro del *frontend*, consumida por alias de código fuente, no se podía separar.

## Opciones consideradas

1. **La librería dentro del *frontend*, consumida por alias de código fuente.** Es lo que este ADR reemplaza del ADR 0018 (doc. 00 §6).
2. **Un proyecto autónomo `ui/`, publicado en npm.** Separable como `backend/` y `frontend/`.

El doc. 01 §14.1 (IN‑02) prohíbe además las herramientas que unirían los proyectos, como Nx o Turborepo.

## Decisión

`liv-components` es un proyecto autónomo en la raíz (`ui/`). Se publica en npm como paquete público, porque el *scope* `@liv` es de otra organización, con versionado semántico y etiquetas `ui-vX.Y.Z`. El *frontend* la instala con versión exacta (doc. 01 §11.1).

## Consecuencias

- **Positivas:** la librería se puede separar y se versiona como cualquier dependencia.
- **Negativas o costos:** adoptar una versión nueva exige publicarla y subirla en el *frontend* en un PR aparte, porque un PR toca un solo proyecto (IN‑07).
- **Qué cambia en el repositorio:**
  - un humano publica con `npm run release`, después de integrar el PR que sube la versión (doc. 01 §11.1; `ui/README.md`);
  - el *frontend* fija la versión exacta y nunca lee `../ui` (IN‑04b);
  - un cambio en un componente existente es un cambio crítico (doc. 01 §11.1).
