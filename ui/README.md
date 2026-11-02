# liv-components (workspace)

Workspace de la librería de componentes del sistema LIV, que se publica en npm como **[`liv-components`](https://www.npmjs.com/package/liv-components)**: una sola línea de componentes y *tokens* de diseño para todas las pantallas (documento de arquitectura 01 §11.1).

> Proyecto **autónomo**: hoy vive en `ui/` del monorepo `liv`, y más adelante tendrá su propio repositorio. No depende de ningún archivo fuera de esta carpeta. El frontend la usa **solo como paquete npm** con versión fijada, nunca leyendo `../ui` (reglas IN‑01 a IN‑11).

## Requisitos

- Node 22.12 o superior dentro de la línea 22 (`.nvmrc`), o Node 24+.

## Comandos

| Comando | Qué hace |
|---|---|
| `npm install` | Instala las dependencias |
| `npm run build` | Compila la librería en `dist/liv-components` |
| `npm run pack` | Compila y genera `dist/liv-components-<versión>.tgz`, para revisar el contenido antes de publicar |
| `npm run version:patch` / `version:minor` / `version:major` | Sube la versión de la librería (`projects/liv-components/package.json`) |
| `npm run release:check` | Verifica que la versión se puede publicar, sin publicar nada |
| `npm run release` | Verifica, revisa el *lint*, compila y publica en npm |
| `npm test` | Pruebas de los componentes (Vitest con jsdom), incluida la accesibilidad con axe |
| `npm run storybook` | Catálogo de componentes en <http://localhost:6006> (Storybook) |
| `npm run build-storybook` | Compila el catálogo en `dist/storybook` |
| `npm run lint` | ESLint |
| `npm run format` / `npm run format:check` | Prettier |

## Publicar una versión

Una versión publicada en npm **no se puede cambiar ni volver a publicar** con el mismo número. Por eso la publicación tiene un orden fijo: la versión nueva se prepara en un PR, se integra en `main` y solo después se publica desde `main`.

### Antes de la primera vez (una sola vez por persona)

1. Ten una cuenta en [npmjs.com](https://www.npmjs.com) con verificación en dos pasos (2FA) activa.
2. Pide a un responsable actual del paquete que te dé permiso de publicación:

   ```bash
   npm owner add <tu-usuario-npm> liv-components
   ```

   Para ver quién puede publicar: `npm owner ls liv-components`.

### 1. Preparar la versión (en una rama, por PR)

1. Crea la rama desde `main` actualizado, p. ej. `feat/NNN-boton-ui` o `chore/ui-v0.2.0`.
2. Haz y prueba los cambios de la librería.
3. Elige el número según el [versionado semántico](https://semver.org/lang/es/):

   | Cambio | Comando | Ejemplo |
   |---|---|---|
   | Corrección que no cambia cómo se usa un componente | `npm run version:patch` | 0.1.0 → 0.1.1 |
   | Componente, variante o *token* nuevo, compatible con lo anterior | `npm run version:minor` | 0.1.0 → 0.2.0 |
   | Cambio incompatible: se quita o renombra un componente, *input*, *token* o variante | `npm run version:major` | 0.1.0 → 1.0.0 |

   Mientras la versión empiece por `0.`, un cambio incompatible sube el número del medio (`minor`) y se anuncia en el `CHANGELOG.md`.
4. Agrega en `CHANGELOG.md` la sección `## [X.Y.Z] — AAAA-MM-DD` con lo que cambia. La publicación falla si falta.
5. Verifica el paquete: `npm run lint` y `npm run pack`. Revisa que el `.tgz` de `dist/` tenga lo esperado.
6. Abre el PR **solo con `ui/`**. El PR se revisa e integra como cualquier otro.

### 2. Publicar (después de integrar el PR)

```bash
git checkout main
git pull
cd ui
npm install
npm login
npm run release
```

`npm run release` primero ejecuta `npm run release:check`, que **detiene la publicación** con un mensaje que explica qué hacer si:

- no estás en `main` o hay cambios sin commit en `ui/`;
- no has iniciado sesión en npm (`npm login`);
- tu usuario no tiene permiso de publicación (te dice el `npm owner add` que debe ejecutar un responsable);
- la versión ya existe en npm o no es mayor que la última publicada;
- falta la sección de la versión en `CHANGELOG.md`.

Después revisa el *lint*, compila y publica. Si tienes 2FA, npm abre el navegador o pide el código de 6 dígitos. Si no lo pide, publica con el código directamente:

```bash
npm publish ./dist/liv-components --otp=<código>
```

### 3. Después de publicar

1. Comprueba la publicación: `npm view liv-components version` debe responder la versión nueva.
2. Etiqueta el commit publicado y súbela:

   ```bash
   git tag ui-vX.Y.Z
   git push origin ui-vX.Y.Z
   ```

3. En el frontend, en otra rama y otro PR: cambia la versión de `liv-components` en `frontend/package.json` (versión exacta, sin `^`), ejecuta `npm install`, verifica (`npm run lint`, `npm test`, `npm run build`) y sube `package.json` y `package-lock.json`. El resto del equipo recibe la versión nueva con su próximo `npm install`.

### Si algo sale mal

| Mensaje | Causa | Solución |
|---|---|---|
| `You cannot publish over the previously published versions` | La versión ya existe | Sube la versión (`npm run version:patch`/`minor`/`major`), actualiza el `CHANGELOG.md` e intégralo por PR |
| `E403 … You do not have permission to publish` | Tu usuario no es responsable del paquete | Un responsable ejecuta `npm owner add <tu-usuario> liv-components` |
| `ENEEDAUTH` / `E401` | No has iniciado sesión | `npm login` |
| `EOTP` | Falta el código de verificación en dos pasos | `npm publish ./dist/liv-components --otp=<código>` |
| Se publicó algo incorrecto | — | No intentes reemplazarlo: publica una versión `patch` con la corrección. Retirar una versión (`npm unpublish`) solo es posible en las primeras 72 horas y deja el número inutilizable |

Cuando exista el CI (F0‑07), la publicación la hará el *pipeline* de este proyecto al subir la etiqueta `ui-vX.Y.Z`, con un *token* de npm guardado como secreto, y las mismas verificaciones.

## Estructura

```
ui/
├── projects/liv-components/      ← la librería (lo que se publica)
│   ├── package.json              ← nombre, versión y exportaciones del paquete
│   └── src/
│       ├── public-api.ts         ← única superficie pública
│       ├── lib/<componente>/     ← componente, plantilla, estilos, pruebas e historia de Storybook
│       └── styles/theme.css      ← tokens de diseño (se publica como liv-components/styles/theme.css)
├── .storybook/                   ← configuración del catálogo
├── scripts/                      ← verificaciones previas a la publicación y avisos de terceros
├── THIRD_PARTY_NOTICES.md        ← licencias del material copiado (iconos de Lucide); se publica con el paquete
├── angular.json · tsconfig.json  ← workspace de Angular (solo la librería)
└── eslint.config.js · .prettierrc
```

## Estado

Versión 0.1.0 publicada en npm: contiene solo los *tokens* de diseño. En desarrollo para 0.2.0 (feature 001): *tokens* nuevos y los componentes `Icon`, `Logo`, `Spinner`, `Skeleton`, `Badge` y `Avatar`, con pruebas, accesibilidad con axe y su historia de Storybook por variante. Las capturas de referencia (regresión visual, Q14) llegan con T05. Ver `CHANGELOG.md`.
