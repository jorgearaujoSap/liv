# Changelog — liv-frontend

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `frontend-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Esqueleto de la Fase 0 (F0‑05): workspace de Angular 21.2 con la aplicación `liv-frontend`. La librería de componentes es el paquete npm `liv-components` (versión exacta 0.1.0), que vive en su propio proyecto.
- Tailwind CSS 4 con los tokens de diseño importados de `liv-components/styles/theme.css`. La paleta, los tamaños de texto y las sombras por defecto de Tailwind están anulados.
- ESLint (angular-eslint 21) con reglas de frontera: la aplicación usa solo la API pública de `liv-components` y no importa archivos de otros proyectos del monorepo.
- Prettier y Vitest; raíz de la aplicación sin pantallas (solo `router-outlet`) a la espera de los diseños aprobados.
