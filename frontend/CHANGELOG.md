# Changelog — liv-frontend

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `frontend-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Núcleo de sesión (`src/app/core/`, feature 001, T21):
  - `SessionService` sobre `BffApi` (estado de la sesión sin extenderla, `keepalive` y cierre de sesión con navegación completa), `authGuard` con `returnTo` (001-01) y `capabilityGuard` sobre `PlatformApi` con 403 y «Próximamente» (001-16). Las rutas a las que llevan (`/sin-permiso`, `/proximamente`, `/error`) están en `app-paths.ts`; sus pantallas son de T22.
  - Interceptor de la API (`provideLivHttp()`): `X-Correlation-Id`, token anti-CSRF (cookie `XSRF-TOKEN` a `X-XSRF-TOKEN`, 001-12), reintento tras un 401 **una vez y solo en lecturas o con `Idempotency-Key`**, y recarga de capacidades tras un 403.
  - `IdleService` (001-22): avisa 2 minutos antes de que venza la sesión (a los 13 de 15) con una sola consulta a `/bff/session`, sin sondeo y sin tocar `/api`; aviso aparte a 5 minutos de las 8 horas; sincroniza las pestañas con `BroadcastChannel`.
- `nginx.conf` (CSP, HSTS, `Referrer-Policy`, `nosniff`, `Cache-Control: no-store` salvo los archivos con hash; la API y el BFF responden 404) y `Dockerfile` en dos etapas con `nginx-unprivileged` fijado por digest. `.dockerignore`.
- Contratos `core`, `platform` y `bff` 1.0.0, publicados como *releases* de GitHub (ADR 0027), fijados con su SHA-256 en `api-spec/versions.json`. `npm run api:update` los descarga y verifica, y genera con `ng-openapi-gen` 1.1.0 el cliente de `src/app/api/{core,platform,bff}/`. El detalle está en el `README.md`.
- `proxy.conf.json` para `npm start`: `/bff`, `/api` y `/login/oauth2` van al BFF en `localhost:8081`.
- Regla de ESLint: `HttpClient`, `HttpBackend`, `fetch`, `XMLHttpRequest` y el `import()` de `@angular/common/http` solo en las carpetas de los contratos de `src/app/api/`.
- `npm test` también ejecuta las pruebas del *script* y de las reglas de ESLint, y comprueba que el cliente guardado es el que se genera de `api-spec/`.
- Esqueleto de la Fase 0 (F0‑05): workspace de Angular 21.2 con la aplicación `liv-frontend`. La librería de componentes es el paquete npm `liv-components` (versión exacta 0.1.0), que vive en su propio proyecto.
- Tailwind CSS 4 con los tokens de diseño importados de `liv-components/styles/theme.css`. La paleta, los tamaños de texto y las sombras por defecto de Tailwind están anulados.
- ESLint (angular-eslint 21) con reglas de frontera: la aplicación usa solo la API pública de `liv-components` y no importa archivos de otros proyectos del monorepo.
- Prettier y Vitest; raíz de la aplicación sin pantallas (solo `router-outlet`) a la espera de los diseños aprobados.

### Cambiado

- Al ir a iniciar sesión (sesión vencida, 401 o sin sesión), `SessionService.redirectToLogin` avisa a las demás pestañas igual que al salir, para que no sigan mostrando datos de la sesión que terminó hasta su próxima consulta. Cada pestaña pregunta al BFF antes de irse, así que el aviso no cierra una sesión abierta (decisión del responsable sobre T21).
- `liv-components` sube a la versión exacta 0.2.0 (18 componentes).
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.

