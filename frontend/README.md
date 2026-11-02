# liv-frontend

Frontend del sistema de liquidación y recaudo del impuesto vehicular (LIV): Angular 21 (componentes *standalone*, TypeScript estricto), Tailwind CSS 4 y la librería de componentes **[`liv-components`](https://www.npmjs.com/package/liv-components)**, que se instala desde npm.

> Proyecto **autónomo**: hoy vive en `frontend/` del monorepo `liv`, y más adelante tendrá su propio repositorio. No depende de ningún archivo fuera de esta carpeta. Con el backend solo comparte el contrato OpenAPI (copia fijada en `api-spec/`); con la librería, el paquete npm de versión fijada (reglas IN‑01 a IN‑11 del documento de arquitectura 01 §14.1).

## Requisitos

- Node 22.12 o superior dentro de la línea 22 (`.nvmrc`), o Node 24+.

## Comandos

| Comando | Qué hace |
|---|---|
| `npm install` | Instala las dependencias, incluida `liv-components` desde npm |
| `npm start` | Servidor de desarrollo en `http://localhost:4200` |
| `npm run build` | Compila la aplicación |
| `npm test` | Pruebas (Vitest), una sola ejecución |
| `npm run lint` | ESLint |
| `npm run format` / `npm run format:check` | Prettier |

## Actualizar la librería de componentes

`liv-components` se declara con versión exacta en `package.json` (sin `^`). Cuando se publique una versión nueva (proceso en `ui/README.md` del monorepo o del repositorio de la librería), en una rama y un PR propios: cambia el número, ejecuta `npm install`, verifica (`npm run lint`, `npm test`, `npm run build`) y sube `package.json` y `package-lock.json`. Los demás la reciben con su próximo `npm install`. Las versiones disponibles se consultan con `npm view liv-components versions`.

## Estructura

```
frontend/
├── src/
│   ├── app/          ← raíz y rutas; más adelante core/ y features/
│   └── styles.css    ← importa Tailwind y los tokens de liv-components; solo estilos reutilizados en toda la interfaz
└── eslint.config.js  ← reglas de ESLint, incluidas las fronteras con liv-components y con los otros proyectos
```

## Estado

Esqueleto de la Fase 0 (entregable F0‑05): Tailwind, *tokens* desde `liv-components`, ESLint con reglas de frontera, Prettier y Vitest. Todavía no hay pantallas: cada una necesita antes su diseño aprobado en Figma.
