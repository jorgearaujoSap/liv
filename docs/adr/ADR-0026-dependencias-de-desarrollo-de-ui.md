# ADR-0026 · Dependencias de desarrollo de `ui/` (pruebas, estilos y catálogo) y excepción de vulnerabilidades

- **Estado:** Propuesto
- **Fecha:** 2026-11-02
- **Decisores:** arquitecto y líder de frontend (pendiente de aprobación)
- **Documentos relacionados:** ADR 0018, ADR 0023; `AGENTS.md` §4.5; `docs/arquitectura/02` §11; `docs/arquitectura/04` §6 (Q02, Q14); PR [#30](https://github.com/jorgearaujoSap/liv/pull/30) (tarea T03 de la feature 001)

## Contexto

El ADR 0023 (aceptado) lista para `ui/` solo "Storybook, Playwright". La tarea T03 de la feature 001 (PR #30) agregó los primeros componentes de `liv-components` y necesitó más herramientas de desarrollo, que `AGENTS.md` §4.5 exige aprobar por ADR. Un ADR aceptado no se edita, así que este lo **complementa** sin reemplazarlo.

Ninguna de estas dependencias entra en el paquete publicado en npm: `npm audit --omit=dev` da 0 vulnerabilidades. Las versiones se fijan exactas en `ui/package.json`.

La revisión adversarial de T03 también halló que `npm audit` reporta 3 vulnerabilidades altas y 39 moderadas, todas en dependencias de desarrollo que trae Storybook (entre ellas `webpack-dev-middleware` 6.1.3, GHSA‑g84c‑rxfj‑3j2c, recorrido de rutas, que solo afecta al servidor de desarrollo local). `docs/arquitectura/02` §11 permite una excepción documentada.

## Opciones consideradas

1. **Reducir al mínimo** y escribir a mano lo que se pueda (sin `@apply` de Tailwind, sin axe, otro catálogo). Menos superficie de cadena de suministro, pero reimplementa accesibilidad y contradice "estilos con Tailwind en el `.component.css`" (ADR 0018, doc. 01 §11.1).
2. **Aceptar las herramientas estándar** de Angular, Storybook y Tailwind, con versiones exactas y una excepción acotada para las vulnerabilidades de desarrollo.
3. **Cambiar de catálogo** a uno sin webpack. Evitaría la cadena de `@angular-devkit/build-angular`, pero se aparta del ADR 0023, que ya eligió Storybook.

## Decisión

Opción 2. Para `ui/`, además de lo que ya autoriza el ADR 0023:

| Dependencia (solo desarrollo) | Para qué |
|---|---|
| `tailwindcss`, `@tailwindcss/postcss` | `@apply` y tokens en los `.component.css`; ng-packagr y las pruebas lo compilan |
| `axe-core` | Pruebas de accesibilidad de cada componente (WCAG 2.1 AA) con Vitest y jsdom |
| `@storybook/addon-a11y`, `@storybook/addon-docs` | Panel de accesibilidad y descripción de cada historia con el enlace a Figma |
| `@angular-devkit/build-angular`, `@angular/platform-browser-dynamic` | Pares obligatorios de `@storybook/angular`; traen webpack |

No se agregan `zone.js` ni `@angular/animations` (pares opcionales de Storybook que no se usan).

**Excepción de vulnerabilidades (doc. 02 §11):** se acepta, solo para dependencias de desarrollo de `ui/`, las vulnerabilidades altas que llegan por `@storybook/angular`, mientras no exista una versión de Storybook que las corrija dentro del rango compatible con Angular 21. Condiciones:

- no se ejecuta el servidor de Storybook en redes no confiables;
- el catálogo compilado (`build-storybook`) no se despliega con datos reales ni credenciales;
- `npm audit --omit=dev` debe seguir en 0;
- se revisa en cada versión de Storybook y vence el **2027-01-31** (fecha propuesta, a confirmar por el aprobador), cuando se corrige o se renueva con motivo.

## Consecuencias

- **Positivas:** componentes con accesibilidad probada de forma automática, estilos por tokens y catálogo vivo, con las herramientas estándar del ecosistema.
- **Negativas o costos:** unos 600 paquetes más en el *lockfile* de `ui/`, con alertas de `npm audit` que Dependabot seguirá mostrando; el contraste de color no se prueba en jsdom y queda para las pruebas de navegador (Q14).
- **Qué cambia en el repositorio:** la tabla de ADR del doc. 00 §6 (fila 0026) y, al aceptarlo, la lista de dependencias de `ui/` en `ui/README.md` si se desea.
