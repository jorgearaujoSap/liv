# Changelog — liv-frontend

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `frontend-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Contratos `core`, `platform` y `bff` 1.0.0, publicados como *releases* de GitHub (ADR 0027), fijados con su SHA-256 en `api-spec/versions.json`. `npm run api:update` los descarga y verifica, y genera con `ng-openapi-gen` 1.1.0 el cliente de `src/app/api/{core,platform,bff}/`. El detalle está en el `README.md`.
- `proxy.conf.json` para `npm start`: `/bff`, `/api` y `/login/oauth2` van al BFF en `localhost:8081`.
- Regla de ESLint: `HttpClient` solo se importa en `src/app/api/`.
- `npm test` también ejecuta las pruebas del *script* y de las reglas de ESLint, y comprueba que el cliente guardado es el que se genera de `api-spec/`.
- Esqueleto de la Fase 0 (F0‑05): workspace de Angular 21.2 con la aplicación `liv-frontend`. La librería de componentes es el paquete npm `liv-components` (versión exacta 0.1.0), que vive en su propio proyecto.
- Tailwind CSS 4 con los tokens de diseño importados de `liv-components/styles/theme.css`. La paleta, los tamaños de texto y las sombras por defecto de Tailwind están anulados.
- ESLint (angular-eslint 21) con reglas de frontera: la aplicación usa solo la API pública de `liv-components` y no importa archivos de otros proyectos del monorepo.
- Prettier y Vitest; raíz de la aplicación sin pantallas (solo `router-outlet`) a la espera de los diseños aprobados.

### Cambiado

- `liv-components` sube a la versión exacta 0.2.0 (18 componentes).
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.

