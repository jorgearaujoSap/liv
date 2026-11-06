# Changelog — liv-frontend

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `frontend-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Pantallas de la feature 001 (T22), con sus diseños de Figma LIV-Diseño y solo con `liv-components` 0.2.0:
  - estructura general (`SideNav`, `AppHeader`) con el menú que arma `core/navigation/menu-presentation.ts` a partir de las capacidades: solo lo permitido, las opciones `COMING_SOON` deshabilitadas (001-02, 001-15);
  - Inicio, Sin permiso (403), Próximamente, Cargando y Error (rutas `/`, `/sin-permiso`, `/proximamente` y `/error`);
  - aviso de sesión por expirar sobre `IdleService.warning` (001-22): «Seguir conectado» o «Cerrar sesión»; el aviso del límite de 8 horas solo deja cerrar sesión (su texto no está en el diseño);
  - `WorkspaceService`: carga la persona y las capacidades con un tiempo límite de 10 s y vuelve a pedir la persona cuando cambian las capacidades (p. ej. tras un 403); una rama del menú lleva a su primera opción disponible y los códigos que no son un número de opción se descartan;
  - ruta `opcion/:code`, con `capabilityGuard`, para abrir cualquier opción del menú por URL (001-16);
  - regla de ESLint: las plantillas de `src/app/features/` solo usan `liv-*`, `app-*`, `router-outlet` y contenedores sin estilo, sin `style`, `class` ni controles nativos.
- `axe-core` 4.14.0 (solo desarrollo): una prueba de accesibilidad por pantalla (001-19). ADR 0029, aceptado por el responsable el 2026-11-05.
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

- Durante la precarga del navegador (p. ej. mientras se escribe la dirección en la barra), `BrowserNavigation` no navega a iniciar sesión: espera a que la persona abra la página (`prerenderingchange`). Antes se veía un 404 en `/bff/login`, porque el BFF no abre un inicio de sesión desde una precarga (feature 001, 001-01).
- `liv-components` 0.3.1, desde la 0.2.0; trae también la 0.3.0. Las tarjetas de módulo se estiran con su celda, y la cuadrícula de Inicio tiene filas iguales (`auto-rows-fr`): todas las tarjetas miden lo que la más alta, como en Figma (feature 001, T22a). El menú sigue con dos niveles; el tercero (1.5.x y 1.6.x) va en otro PR.
- `capabilityGuard`: la ruta puede declarar `data: { capabilityParam: 'code' }` para tomar el número de la opción de la URL; declarar a la vez `capability` y `capabilityParam` se niega.
- Contrato `bff` 1.1.0 (antes 1.0.0): declara el 403 anti-CSRF con `code` `001-12` (`CsrfProblem`). Compatible: el cliente generado cambia `Problem` por `CsrfProblem` en el BFF y nada de la aplicación lo usaba.
- Aviso del cierre de sesión entre pestañas (decisión del responsable sobre T21): al ir a iniciar sesión sin sesión (vencida o tras un 401), `SessionService.redirectToLogin` avisa a las demás pestañas, igual que al salir. Si la sesión sigue abierta, no avisa. Cada pestaña que recibe el aviso pregunta al BFF cuando está a la vista, para no pisar el inicio de sesión de la pestaña que la persona usa. Sin sesión, va a iniciar sesión conservando su ruta; con una sesión abierta, se recarga. Si el BFF no responde, vuelve a preguntar en 30 segundos. El canal no falla si el navegador no deja enviar, y `restart()` no deshace una redirección en curso.
- `liv-components` sube a la versión exacta 0.2.0 (18 componentes).
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.

