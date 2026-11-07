---
name: liv-pantalla
description: Crea o cambia una pantalla del frontend de LIV (Angular 21) con las convenciones del proyecto - diseño aprobado en Figma, ruta con su capacidad, servicio sobre el cliente generado del OpenAPI, composición solo con liv-components, estilos solo con tokens y pruebas con axe primero. Úsala siempre que una tarea cree una pantalla, cambie su estructura o sus datos, o agregue una opción del menú.
---

# Nueva pantalla

Una pantalla nueva, o un cambio en su estructura o su aspecto, va por el SDD (`AGENTS.md` de la raíz, §4.3). **Sin diseño aprobado en Figma no se implementa** (`frontend/AGENTS.md`). Esta *skill* dice **cómo** se hace aquí; las reglas están en `frontend/AGENTS.md`.

**Conocimiento de Angular:** usa el servidor MCP del CLI de Angular (`angular-cli` en `.mcp.json`). `get_best_practices` da la guía oficial y `search_documentation` la documentación de la versión instalada. No escribas de memoria: Angular 21 cambió mucho desde versiones anteriores. **Si la guía de Angular choca con una regla de LIV, manda LIV:** sin `style` ni `[style]`, plantilla y estilos en archivos propios (`templateUrl`, `styleUrl`) y solo componentes de `liv-components`.

## Ejemplo que se sigue

**Inicio** (001-02, 001-15): `src/app/features/home/` (`home.ts`, `home.html`, `home.css`, `home.spec.ts`).

| Carpeta | Qué va |
|---|---|
| `src/app/features/<pantalla>/` | La pantalla: componente, plantilla, estilos y prueba |
| `src/app/core/` | Sesión, capacidades, HTTP y navegación, compartidos por todas las pantallas |
| `src/app/api/` | Cliente generado desde `api-spec/`: **no se edita** |
| `src/app/features/testing/fixtures.ts` | Datos ficticios y dobles de prueba (`FakeWorkspace`, `leaf()`) |

## Pasos

1. **Diseño.** Lee el *frame* aprobado en `03 Pantallas` con la *skill* `liv-figma` y anota su nodo. Si necesita un componente o variante que no existe en `liv-components`, **detente**: primero se diseña y se implementa en la librería como un cambio aparte, y una persona publica su versión (`AGENTS.md` de la raíz, §6.2 y §4.5).
2. **Datos.** Si la pantalla usa un endpoint nuevo, su contrato ya debe estar publicado como *release* `contract-<nombre>-vX.Y.Z`. En `api-spec/versions.json` cambia su `version` y su `sha256`, que está en el archivo `<nombre>.yaml.sha256` de la *release*. Después actualiza la copia y el cliente con `npm run api:update` y verifica con `npm run api:verify` (`frontend/README.md`). El frontend nunca lee `../backend`.
3. **Prueba primero (rojo).**
   - `src/app/features/<pantalla>/<pantalla>.spec.ts` con Vitest y `TestBed`, como `home.spec.ts`: `describe('<Nombre> (<códigos de criterio>)')`, datos con los dobles de `fixtures.ts` y búsqueda por roles y textos visibles.
   - Agrega la ruta de la pantalla a la lista de `provideRouter` de `src/app/features/accessibility.spec.ts` y un caso que la abra (001-19, axe).
   - Comprueba que fallan por el motivo correcto.
4. **Servicio**, si la pantalla trae datos.
   - `@Injectable({ providedIn: 'root' })` con `inject()`, que llama al cliente generado (`inject(CoreApi).invoke(operación, …)`), como `features/shell/workspace.service.ts`.
   - Estado con *signals* (`signal`, `computed`). Las llamadas devuelven `Promise` con tiempo límite (`withTimeout`).
   - Sin lógica de negocio: el frontend muestra lo que calcula el backend (impuestos, saldos, descuentos).
5. **Componente** en `src/app/features/<pantalla>/`. Las convenciones de este paso y del 7 que la feature 001 todavía no usa (`OnPush`, `input()`/`output()` y carga diferida) se aplican en el código nuevo, como en `liv-components`; el código existente no se cambia solo por eso.
   - Un comentario al inicio con los criterios y el nodo de Figma, como en `home.ts`.
   - `imports` solo con componentes de `liv-components`.
   - `changeDetection: ChangeDetectionStrategy.OnPush`.
   - `input()`/`output()` en lugar de decoradores. Estado derivado con `computed()`. Flujo de control nativo de las plantillas (`@if`, `@for`).
   - Textos en español en la plantilla, con los términos del glosario. Fechas y montos en `es-CO`: miles con punto, decimales con coma, `dd/mm/aaaa`.
6. **Estilos** en `<pantalla>.css`.
   - La primera línea es `@reference '../../../styles.css';`. Solo se usa `@apply` con utilidades de Tailwind y *tokens* de la librería (`text-heading-h3`, `text-text-primary`).
   - Nada de colores, tamaños de letra ni sombras sueltas. Nada de `::ng-deep`, ni de redefinir los estilos de un componente de la librería.
7. **Ruta** en `src/app/app.routes.ts`, como hija del `Shell`, que ya exige sesión.
   - Ruta `${OPTION_ROUTE}/<código>` con `canActivate: [capabilityGuard]` y `data: { capability: '<código>' }`. Declárala **antes** de la ruta genérica `${OPTION_ROUTE}/:code`.
   - Carga diferida: `loadComponent: () => import('./features/<pantalla>/<pantalla>').then((m) => m.<Clase>)`.
8. **Verde y refactorizar**, con las pruebas en verde.

## Verificar

Desde `frontend/`: `npm run lint`, `npm run format:check`, `npm test` y `npm run build`. Después, la pantalla en el navegador con el sistema levantado (`sh tools/dev/iniciar.sh` desde la raíz), comparada con el *frame* de Figma, y su prueba e2e con la *skill* `liv-prueba-e2e` (`e2e/.claude/skills/`).

## Antes del *commit*

- [ ] La pantalla tiene diseño aprobado, o el `plan.md` declara qué estructura reutiliza.
- [ ] Solo se usan componentes de `liv-components`, sin estilos propios para ellos, y solo con *tokens*.
- [ ] La ruta declara su capacidad y la pantalla no decide permisos: los decide el backend.
- [ ] No se editó nada de `src/app/api/`, y el código no lee ni importa nada de `../backend` ni de `../ui`.
- [ ] La prueba de la pantalla y la de accesibilidad se escribieron antes (commit `test:`).
- [ ] `lint`, `format:check`, `test` y `build` están en verde.
- [ ] `CHANGELOG.md` tiene su entrada en «Sin publicar» y la feature y `ESTADO.md` de la raíz están al día.
