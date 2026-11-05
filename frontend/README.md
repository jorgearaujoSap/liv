# liv-frontend

Frontend del sistema de liquidación y recaudo del impuesto vehicular (LIV): Angular 21 (componentes *standalone*, TypeScript estricto), Tailwind CSS 4 y la librería de componentes **[`liv-components`](https://www.npmjs.com/package/liv-components)**, que se instala desde npm.

> Proyecto **autónomo**: hoy vive en `frontend/` del monorepo `liv`, y más adelante tendrá su propio repositorio. No depende de ningún archivo fuera de esta carpeta. Con el backend solo comparte el contrato OpenAPI (copia fijada en `api-spec/`); con la librería, el paquete npm de versión fijada (reglas IN‑01 a IN‑11 del documento de arquitectura 01 §14.1).

## Requisitos

- Node 22.12 o superior dentro de la línea 22 (`.nvmrc`), o Node 24+.

## Comandos

| Comando | Qué hace |
|---|---|
| `npm install` | Instala las dependencias, incluida `liv-components` desde npm |
| `npm start` | Servidor de desarrollo en `http://localhost:4200`; `proxy.conf.json` envía `/bff`, `/api` y `/login/oauth2` al BFF en `http://localhost:8081` |
| `npm run build` | Compila la aplicación |
| `npm test` | Pruebas de la aplicación (Vitest) y de los *scripts* y las reglas de ESLint (`node --test`), una sola ejecución |
| `npm run api:update` | Descarga los contratos fijados en `api-spec/versions.json`, verifica su SHA-256 y regenera el cliente de `src/app/api/` |
| `npm run api:generate` | Regenera el cliente desde las copias de `api-spec/`, sin red |
| `npm run api:verify` | Comprueba, sin red, que `api-spec/` coincide con `versions.json` |
| `npm run lint` | ESLint |
| `npm run format` / `npm run format:check` | Prettier |

## Actualizar la librería de componentes

`liv-components` se declara con versión exacta en `package.json` (sin `^`). Cuando se publique una versión nueva (proceso en `ui/README.md` del monorepo o del repositorio de la librería), en una rama y un PR propios: cambia el número, ejecuta `npm install`, verifica (`npm run lint`, `npm test`, `npm run build`) y sube `package.json` y `package-lock.json`. Los demás la reciben con su próximo `npm install`. Las versiones disponibles se consultan con `npm view liv-components versions`.

## Contratos de la API

El único contrato con el backend es el OpenAPI. El backend publica cada contrato como una *release* pública de GitHub, `contract-<nombre>-vX.Y.Z` (ADR 0027), y el frontend guarda una copia fijada:

| Contrato | Qué cubre | Cliente generado |
|---|---|---|
| `core` | `/api/v1/me` | `src/app/api/core/` (`CoreApi`) |
| `platform` | `/api/v1/me/capabilities` | `src/app/api/platform/` (`PlatformApi`) |
| `bff` | `/bff/session` y el resto del BFF | `src/app/api/bff/` (`BffApi`) |

`core-internal` no se adopta: es interno entre el BFF y la API (ADR 0025).

Para adoptar una versión nueva de un contrato, en un PR propio: cambia `version` y `sha256` en `api-spec/versions.json` (el SHA-256 está en el archivo `<nombre>.yaml.sha256` de la *release*), ejecuta `npm run api:update`, verifica y sube `api-spec/` y `src/app/api/`. Si el SHA-256 de lo descargado no es el fijado, el *script* falla y no cambia nada. `api-spec/*.yaml` y `src/app/api/**` no se editan a mano ni se formatean (`.gitattributes` y `.prettierignore`).

**`HttpClient` solo en el cliente generado.** Una regla de ESLint prohíbe importar `HttpClient` y `HttpBackend` (un tipo con `import type` sí se permite), usar `fetch` o `XMLHttpRequest` y hacer `import()` de `@angular/common/http` fuera de las carpetas de los contratos de `src/app/api/`: el resto de la aplicación llama a la API con el cliente, por ejemplo `inject(CoreApi).invoke(getCurrentUser)`, y así pasa por el interceptor. Registrar `provideHttpClient()` en la configuración de la aplicación, que el cliente necesita, y escribir *interceptores* no requieren importarlo.

`npm run api:update` necesita red hacia GitHub (30 s de tiempo límite por contrato, 2 MiB como máximo). Cuando `frontend/` tenga su propio repositorio, el `repository` de `versions.json` sigue siendo el de las *releases* de los contratos, que publica el repositorio del backend.

## Estructura

```
frontend/
├── src/
│   ├── app/          ← raíz y rutas; más adelante core/ y features/
│   │   └── api/      ← cliente generado de los contratos (no se edita)
│   └── styles.css    ← importa Tailwind y los tokens de liv-components; solo estilos reutilizados en toda la interfaz
├── api-spec/         ← copias fijadas de los contratos y versions.json
├── scripts/          ← api-update.mjs y sus pruebas
├── proxy.conf.json   ← proxy del servidor de desarrollo hacia el BFF
└── eslint.config.js  ← reglas de ESLint, incluidas las fronteras con liv-components y con los otros proyectos
```

## Estado

Esqueleto de la Fase 0 (entregable F0‑05): Tailwind, *tokens* desde `liv-components`, ESLint con reglas de frontera, Prettier y Vitest. Todavía no hay pantallas: cada una necesita antes su diseño aprobado en Figma.
