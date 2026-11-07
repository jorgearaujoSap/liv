---
name: liv-componente
description: Crea o cambia un componente de liv-components (proyecto ui/) con las convenciones del proyecto - mismo nombre, variantes y propiedades que en Figma, solo presentación, signals con input() y OnPush, estilos solo con tokens, pruebas con axe por variante y estado, historia de Storybook enlazada a Figma, API pública y versión con su CHANGELOG. Úsala siempre que una tarea cree un componente, una variante o un token, o cambie uno existente.
---

# Nuevo componente de `liv-components`

Crear un componente es un cambio **normal** y modificar uno existente es **crítico**: los dos van por el SDD, porque toda la librería es ruta crítica (`AGENTS.md` de la raíz, §4.3). **El componente nace primero en Figma**, en la página `02 Componentes`, con el mismo nombre, variantes y propiedades (`ui/AGENTS.md`). Esta *skill* dice **cómo** se implementa aquí; las reglas están en `ui/AGENTS.md`.

Modificar un componente existente exige además lo de todo cambio crítico: la revisión adversarial (`liv-revision-adversarial`), las cuatro lentes de `liv-revision-4r` y las aprobaciones de `ui/AGENTS.md` (líder de frontend y responsable funcional).

**Conocimiento de Angular:** usa el servidor MCP del CLI de Angular (`angular-cli` en `.mcp.json`). `get_best_practices` da la guía oficial y `search_documentation` la documentación de la versión instalada. No escribas de memoria. **Si la guía de Angular choca con una regla de LIV, manda LIV:** sin `style` ni `[style]`, plantilla y estilos en archivos propios y solo *tokens*.

## Ejemplo que se sigue

`Button`, en `projects/liv-components/src/lib/button/`. En esta *skill*, toda ruta que empieza por `src/` es de `projects/liv-components/src/`:

| Archivo | Qué tiene |
|---|---|
| `button.component.ts` | Selector `liv-button`, `OnPush`, `input()` con `booleanAttribute`, `computed()`, atributos del anfitrión en `host` y un comentario con el nombre de Figma y el comportamiento |
| `button.component.html` y `.css` | La plantilla y los estilos. La primera línea del CSS es `@reference '../tailwind-reference.css';` |
| `button.component.spec.ts` | Una prueba por variante, tamaño y estado, y axe con `expectNoAxeViolations` (`src/testing/axe.ts`) |
| `button.stories.ts` | Título `Componentes/Button`, el enlace al nodo de Figma en la descripción y un control por propiedad |
| `src/public-api.ts` | La exportación del componente y de sus tipos |

## Pasos

1. **Figma.** Lee el componente en `02 Componentes` con la *skill* `liv-figma` y anota:
   - el nodo y el nombre;
   - cada variante y propiedad, con sus valores;
   - los estados (Hover, Focus, Disabled, Loading…);
   - los estilos que usa.

   Si falta un estilo o un *token*, primero se crea en Figma y después en `src/styles/theme.css`.
2. **Prueba primero (rojo)** en `<nombre>.component.spec.ts`.
   - Una prueba por cada variante, propiedad y estado de Figma.
   - Una prueba de axe que recorra todas las variantes y estados. Para el contraste usa `contrastRatio` y `solidColor` de `src/testing/contrast.ts`, y para los estilos declarados, `src/testing/css.ts`.
   - Las pruebas de un criterio llevan su código (`'cumple axe (001-19) en cada variante y estado'`).
   - Comprueba que fallan por el motivo correcto.
3. **Componente** en `src/lib/<nombre>/<nombre>.component.ts`.
   - Selector `liv-<nombre>`, nombre de clase `<Nombre>Component` igual al de Figma, y `changeDetection: ChangeDetectionStrategy.OnPush`.
   - Una entrada `input()` por propiedad de Figma, con los mismos valores como tipo exportado (`ButtonVariant`). Los booleanos con `booleanAttribute`.
   - Las salidas con `output()`, solo si el evento no es uno nativo, como el clic.
   - Atributos del anfitrión en `host`, nunca `@HostBinding`.
   - **Solo presentación:** sin `HttpClient`, sin importar aplicaciones, sin calcular impuestos ni decidir permisos.
   - El texto visible llega por contenido proyectado o por entradas. Lo que se anuncia a lectores de pantalla va en español.
4. **Estilos** en `<nombre>.component.css`.
   - Solo `@apply` con utilidades de Tailwind y *tokens* de `theme.css`.
   - Las variantes, con selectores sobre los atributos del anfitrión (`:host([data-variant='ghost'])`).
   - Sin `style`, sin `::ng-deep` y sin colores, tamaños ni sombras sueltas. La única excepción documentada es `Skeleton`.
5. **Historia** en `<nombre>.stories.ts`, como `button.stories.ts`:
   - el título `Componentes/<Nombre>`;
   - la constante `figma` con el enlace al nodo, citada en `parameters.docs.description.component`;
   - un control por propiedad y una historia por variante.
6. **API pública.** Exporta el componente y sus tipos en `src/public-api.ts`. Lo que no se exporta ahí no existe para las aplicaciones.
7. **Versión.** Sube la versión según la tabla de `ui/README.md` (`npm run version:minor` para un componente o una variante nueva compatible) (el comando cambia la versión en `projects/liv-components/package.json`). En `CHANGELOG.md`, agrega la sección `## [X.Y.Z] — AAAA-MM-DD`, mueve a ella las entradas de `[Sin publicar]` y deja ese encabezado vacío. **Nunca publiques**: la publicación la hace una persona después de integrar el PR.
8. **Verde y refactorizar**, con las pruebas en verde.

## Verificar

- Desde `ui/`: `npm run lint`, `npm run format:check`, `npm test`, `npm run build-storybook` y `npm run pack`.
- `npm run storybook`, para comparar cada variante y estado con Figma.

## Antes del *commit*

- [ ] El nombre, las variantes, las propiedades y los estados son los de Figma, y la historia enlaza su nodo.
- [ ] El componente no tiene lógica de negocio, ni llamadas HTTP, ni decide permisos.
- [ ] Solo hay *tokens*, sin `style` ni `::ng-deep`.
- [ ] Hay una prueba por variante y estado y axe en todos, escritas antes (commit `test:`).
- [ ] Está exportado en `public-api.ts`, con la versión subida y su sección en `CHANGELOG.md`.
- [ ] `lint`, `format:check`, `test`, `build-storybook` y `pack` están en verde, y `ESTADO.md` de la raíz está al día.
