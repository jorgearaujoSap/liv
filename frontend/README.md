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

**`HttpClient` solo en el cliente generado.** Una regla de ESLint prohíbe importar `HttpClient` y `HttpBackend` (un tipo con `import type` sí se permite), usar `fetch` o `XMLHttpRequest` y hacer `import()` de `@angular/common/http` fuera de las carpetas de los contratos de `src/app/api/` (también `HttpHandler`, `window.fetch` y `globalThis.fetch`): el resto de la aplicación llama a la API con el cliente, por ejemplo `inject(CoreApi).invoke(getCurrentUser)`, y así pasa por el interceptor. La regla evita el uso accidental; una elusión deliberada (importaciones calculadas, alias de `require`) la detecta la revisión del PR. Registrar `provideHttpClient()` en la configuración de la aplicación, que el cliente necesita, y escribir *interceptores* no requieren importarlo.

`npm run api:update` necesita red hacia GitHub (30 s de tiempo límite por contrato, 2 MiB como máximo). Cuando `frontend/` tenga su propio repositorio, el `repository` de `versions.json` sigue siendo el de las *releases* de los contratos, que publica el repositorio del backend.

## Núcleo de sesión (`src/app/core/`)

El navegador nunca recibe *tokens* (001-11): la sesión es la cookie `HttpOnly` del BFF, y todo lo que sigue solo pregunta y reacciona.

| Pieza | Qué hace |
|---|---|
| `SessionService` | Sobre `BffApi`: lo que le queda a la sesión (`/bff/session`, no la extiende), seguir conectado (`keepalive`), cerrar sesión e ir a `/bff/login?returnTo=…`. Cerrar y entrar son **navegación completa** (`location.replace`), no una ruta de Angular |
| `authGuard` | Sin sesión navega a `/bff/login` con la ruta pedida como `returnTo` (001-01); con sesión empieza a vigilar la inactividad |
| `capabilityGuard` | La ruta declara `data: { capability: '1.5.9' }`. Disponible navega; `COMING_SOON` lleva a `/proximamente`; ausente, a `/sin-permiso` (001-16). Una ruta sin código se niega |
| `provideLivHttp()` | `HttpClient` con el interceptor y el token anti-CSRF. El interceptor agrega `X-Correlation-Id`; ante un 401 de `/api/**` consulta `/bff/session` y **reintenta una vez, solo lecturas o peticiones con `Idempotency-Key`**; si no, va a iniciar sesión; ante un 403 recarga las capacidades y lleva a `/sin-permiso` (no si es el rechazo anti-CSRF, `001-12`) |
| `IdleService` | Avisa 2 minutos antes de que venza la sesión (a los 13 de 15) y 5 minutos antes de las 8 horas (`warning`, `extend()`, `logout()`). **Sin sondeo:** una sola consulta a `/bff/session`, programada para cuando toque avisar, y otra al volver a ver la pestaña; nunca toca `/api`. Las pestañas se sincronizan con `BroadcastChannel` |

Las pantallas que atienden `/sin-permiso`, `/proximamente` y `/error`, y el aviso de sesión, son de la tarea T22. Las rutas están en `app-paths.ts`.

## Estructura

```
frontend/
├── src/
│   ├── app/          ← raíz y rutas; más adelante features/
│   │   ├── api/      ← cliente generado de los contratos (no se edita)
│   │   └── core/     ← sesión, guardas, interceptor e inactividad (feature 001, T21)
│   └── styles.css    ← importa Tailwind y los tokens de liv-components; solo estilos reutilizados en toda la interfaz
├── api-spec/         ← copias fijadas de los contratos y versions.json
├── scripts/          ← api-update.mjs, pruebas de las reglas de ESLint y de nginx.conf
├── proxy.conf.json   ← proxy del servidor de desarrollo hacia el BFF
├── nginx.conf        ← servidor de estáticos de la imagen: cabeceras de seguridad y caché
├── Dockerfile        ← imagen en dos etapas (compila con Node, sirve con nginx sin privilegios)
└── eslint.config.js  ← reglas de ESLint, incluidas las fronteras con liv-components y con los otros proyectos
```

## Imagen

```bash
docker build -t liv-frontend .
docker run --rm -p 8080:8080 liv-frontend
```

`nginx.conf` sirve solo la aplicación en el puerto 8080 con un usuario sin privilegios: la API y el BFF los enruta quien está delante (Caddy en e2e, el balanceador en AWS), que también termina TLS. Las cabeceras de seguridad (doc. 02 §8) se declaran **solo en el bloque `server`**: nginx descarta las del `server` si un `location` declara una propia. `npm test` lo comprueba (`scripts/nginx-conf.test.mjs`).

Verificación en un contenedor real, al cambiar `nginx.conf`, el `Dockerfile` o la versión de Angular (el hash de `script-src-attr` es el del manejador `onload="this.media='all'"` que Angular pone a la hoja de estilos; si cambia, la página queda sin estilos):

1. `docker exec <contenedor> nginx -t` responde que la configuración es correcta, y `id` muestra el usuario `nginx` (101).
2. `curl -sD - -o /dev/null http://localhost:8080/ruta/cualquiera` responde 200 con `Content-Security-Policy`, `Strict-Transport-Security`, `X-Content-Type-Options`, `Referrer-Policy` y `Cache-Control: no-store`.
3. Un archivo con hash (`main-XXXXXXXX.js`) responde `Cache-Control: public, max-age=31536000, immutable`, y uno con hash que no existe, 404 con `no-store` (nunca se guarda un 404); `/api/…`, `/bff/…`, `/login/oauth2/…` y un `.js` que no existe responden 404; un `POST`, 405.
4. El contenedor arranca y responde en `/healthz` con `docker run --read-only --tmpfs /tmp …` y corre con el usuario 101. El registro de accesos (`docker logs`) muestra la ruta sin la consulta de la URL.
5. En un navegador, la página carga con sus estilos (la hoja pasa a `media="all"`) y la consola no muestra violaciones de la CSP.

## Estado

Esqueleto de la Fase 0 (entregable F0‑05): Tailwind, *tokens* desde `liv-components`, ESLint con reglas de frontera, Prettier y Vitest. El núcleo de sesión está hecho (T21); todavía no hay pantallas: cada una necesita antes su diseño aprobado en Figma.
