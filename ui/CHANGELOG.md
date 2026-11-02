# Changelog — liv-components

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `ui-vX.Y.Z`. Cada versión publicada necesita su sección `## [X.Y.Z] — AAAA-MM-DD` antes de publicar (lo verifica `npm run release:check`). Proceso completo en `README.md` › "Publicar una versión".

## [Sin publicar]

### Agregado

- Proceso de publicación documentado y verificaciones automáticas antes de publicar (`scripts/check-release.mjs`): rama `main` sin cambios pendientes, sesión de npm, permiso de publicación, versión nueva y mayor que la última, y sección en este archivo. Comandos `version:patch`, `version:minor` y `version:major`.

## [0.1.0] — 2026-11-01

Primera versión publicada en npm. Se publicó antes de integrar su PR, como excepción para poder instalarla en el frontend; las siguientes siguen el proceso del `README.md`.

### Agregado

- Tokens de diseño en `styles/theme.css`, transcritos de los estilos locales del archivo de Figma LIV‑Diseño: 26 de color, 11 de texto y 5 de efecto. Anulan la paleta, los tamaños de texto y las sombras por defecto de Tailwind.
- Workspace de Angular 21 con la librería, ESLint (sin `HttpClient` ni imports de aplicaciones) y Prettier.
