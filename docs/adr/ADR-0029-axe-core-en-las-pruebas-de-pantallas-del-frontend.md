# ADR-0029 · `axe-core` en las pruebas de pantallas del frontend

- **Estado:** Aceptado
- **Fecha:** 2026-11-05
- **Decisores:** responsable del proyecto, el 2026-11-05, al revisar las decisiones de T22 (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** ADR 0023, ADR 0026; `AGENTS.md` §4.5 y §6; `frontend/AGENTS.md` (regla de accesibilidad); `docs/arquitectura/04` §6 (pruebas) y §7; feature 001, criterio 001-19 y tarea T22 (PR #89)

## Contexto

La tarea T22 de la feature 001 pide, como prueba primero, «axe por pantalla (001-19)»: las pantallas del frontend (Inicio, Aviso, Sin permiso, Próximamente, Cargando y Error) cumplen WCAG 2.1 AA.

Lo aprobado hasta hoy:

- ADR 0023, fila de `frontend/`: `ng-openapi-gen`, `@testing-library/angular` e imagen base de nginx. **No incluye `axe-core`.**
- ADR 0026: `axe-core` solo en `ui/`, con Vitest y jsdom.
- ADR 0023, fila de `infra/` y `e2e/`: `@axe-core/playwright`, para el e2e de T23.
- `frontend/AGENTS.md` fija la verificación de accesibilidad como «axe‑core en e2e».

`AGENTS.md` §4.5 exige un ADR para agregar una dependencia. El PR #89 agregó `axe-core` 4.14.0 como dependencia de desarrollo de `frontend/` para poder escribir esa prueba, y lo declaró pendiente de este ADR.

## Opciones consideradas

1. **`axe-core` en las pruebas de Vitest de `frontend/`** (jsdom), con la misma versión exacta que `ui/` (4.14.0).
   - Ventajas: una prueba por pantalla en cada PR del frontend, sin levantar el Compose completo; es lo que pide la tarea (T22) y coincide con lo que ya hace `ui/`.
   - Desventajas: una dependencia más de desarrollo (licencia MPL-2.0; no entra en la imagen ni en `npm audit --omit=dev`); jsdom no calcula el contraste de color.
2. **Solo `@axe-core/playwright` en el e2e (T23).**
   - Ventajas: navegador real, el contraste sí se verifica; no hay dependencia nueva en `frontend/`.
   - Desventajas: la accesibilidad de las pantallas se prueba tarde y con el Compose completo; T22 quedaría sin su prueba de 001-19.
3. **Ninguna herramienta automática**, solo la regla de ESLint de accesibilidad de plantillas.
   - Ventajas: sin dependencia nueva.
   - Desventajas: no detecta roles, nombres ni foco reales; contradice `AGENTS.md` §6 («WCAG 2.1 AA») y el doc. 04 §7 («axe‑core en cada pantalla visitada»).

## Decisión

**Opción 1**, sin quitar la 2. `axe-core` 4.14.0, solo en `devDependencies` de `frontend/` y con versión exacta, para la prueba `features/accessibility.spec.ts` (una por pantalla, sin la regla `color-contrast`, que jsdom no calcula). El contraste lo cubre el e2e de T23 con `@axe-core/playwright`, ya aprobado.


## Consecuencias

- **Positivas:** cada PR del frontend prueba la accesibilidad de las pantallas; los cambios en `liv-components` que rompan una pantalla se ven antes del e2e.
- **Negativas o costos:**
  - una dependencia de desarrollo más en la cadena de suministro (Dependabot y revisión del *lockfile*, doc. 02 §11);
  - la prueba no cubre el contraste; hasta T23 queda sin verificar en navegador.
- **Qué cambia en el repositorio:**
  - `frontend/package.json` y `package-lock.json` (`axe-core` 4.14.0);
  - `frontend/AGENTS.md`: la verificación de accesibilidad pasa a «axe‑core en las pruebas de pantalla y en e2e»;
  - doc. 00 §6 (esta fila) y `ESTADO.md`.
