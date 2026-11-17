# 008 · Configuración de jurisdicción — Tareas

_Checklist accionable derivada del `plan.md` (aprobado en H2, 2026-11-17, #218). Los códigos `008-NN` son los criterios de `spec.md`, con las enmiendas 1 y 2; `H2‑N` son las decisiones del plan. Tareas pequeñas y concretas; cada PR cabe en ~400 líneas y su corte previsto, si lo tiene, está en «Mapa de los PR». Marca `[x]` al completarlas. Cada tarea indica:_

- _su proyecto, su PR del plan y los archivos que puede tocar;_
- _de qué otras tareas depende;_
- _qué recursos compartidos usa: un contrato, la versión de `liv-components`, las migraciones o los archivos de *build* de su proyecto, el entorno local;_
- _la prueba o verificación que se escribe primero (TDD: rojo → verde → refactorizar). En las tareas de documentación, la «prueba primero» es una lista de comprobación escrita antes del texto, en el *scratchpad* de la sesión (no se sube), y corrida al final._

_Con eso el orquestador decide qué corre en paralelo (`liv-orquestador`)._

Es un cambio **crítico** (spec, cabecera). Un PR toca un solo proyecto (`AGENTS.md` §4.6). Las sesiones de las tareas **no escriben** `ESTADO.md`, el *roadmap*, el glosario, las marcas de esta lista ni los criterios de `spec.md`: se lo reportan al orquestador (`AGENTS.md` §7, ADR 0033). Las tareas de código no incluyen el glosario: los términos nuevos (por ejemplo, «versión de la configuración», «motor de ingesta», «hallazgo», «lista de revisión», «versión del validador» y «resultado de validación») los entrega cada sesión en su reporte y el orquestador los escribe. `AGENTS.md` y las reglas de cada proyecto mandan sobre esta lista si alguna vez chocan.

**Ramas.** Los PR de documentos de la raíz usan `docs/008-…` y los demás, `feat/008-…`. La 007 usó `feat/007-documentos` para los suyos; aquí se sigue la forma `docs/` que pidió el orquestador.

**Rutas abreviadas** (solo en este archivo):

| Abreviatura | Ruta |
|---|---|
| `main/` | `backend/src/main/java/co/com/sapenlinea/liv/` |
| `test/` | `backend/src/test/java/co/com/sapenlinea/liv/` |
| `res/` | `backend/src/main/resources/` |
| `bff/` y `bff-test/` | `backend/bff/src/main/java/co/com/sapenlinea/liv/bff/` y `backend/bff/src/test/java/co/com/sapenlinea/liv/bff/` |
| `app/` | `frontend/src/app/` |
| `lib/` | `ui/projects/liv-components/src/lib/` |

Cuando el plan no fija el nombre de una clase, la tarea da la carpeta y la sesión elige el nombre dentro de ella; los nombres que sí fija el plan se respetan.

## Reglas comunes a todas las tareas

- **Modelo** (`AGENTS.md` §10): nivel estándar para todas las tareas, salvo las de ADR (T5 a T10), que son de nivel alto, igual que la revisión adversarial y la lente de riesgo de los cambios críticos.
- **Verificaciones locales:**
  - `backend/`: `./mvnw verify` en verde (Spotless, `ArchitectureRulesTest`, `CodeSizeGuardTest`, `ModularityTest`, `ContractRulesTest`, `AuthorizationRulesTest`). `backend/bff/` es un proyecto Maven aparte: su `./mvnw verify` se corre dentro de `backend/bff/`. `dev/check.sh` revisa el entorno local, como pide el plan; no reemplaza al `verify`.
  - `ui/`: `npm run lint`, `npm run format:check`, `npm test` y `npm run pack`.
  - `frontend/`: `npm run lint`, `npm run format:check`, `npm test` y `npm run build`.
  - `e2e/`: `npm test`, con el sistema local levantado.
- **Tamaño:** ninguna clase pasa de 400 líneas ni ningún controlador de 200 (`CodeSizeGuardTest`, CC‑11). La sesión mide cada PR antes de abrirlo; el corte, si hace falta, lo decide el orquestador y no cambia el orden.
- **Pruebas de regla:** el `@DisplayName` empieza con el código de la regla y el criterio, por ejemplo «CJ04 · 008-15 · un hueco entre rangos es un error», y llevan la etiqueta (`@Tag("008-15")`).
- **`CHANGELOG.md` del proyecto:** cada PR agrega su entrada en «Sin publicar» (`backend/CHANGELOG.md`; `backend/bff/CHANGELOG.md` en C9; `ui/` y `frontend/` los suyos; `e2e/` no tiene `CHANGELOG.md` y no lo lleva). La raíz no tiene `CHANGELOG.md`.
- **Antes de cada *commit*:** la lente de legibilidad de `liv-revision-4r` (`liv-revisor-rapido`).
- **Revisión de cada PR.** La hacen subagentes sin el contexto de quien implementó, lanzados por la sesión de la tarea. Su resumen va en la descripción del PR y el orquestador lo comprueba. Las letras de la columna «Rev.» del mapa son:
  - **Crítica (C):** `liv-revision-adversarial` con dos `liv-revisor-critico` (nivel alto) y las cuatro lentes de `liv-revision-4r`: riesgo con `liv-revisor-critico`, resiliencia y confiabilidad con `liv-revisor`, legibilidad con `liv-revisor-rapido`.
  - **Normal (N):** `liv-revision-adversarial` con dos `liv-revisor` (nivel estándar) y la lente de legibilidad.
  - **Documentos (D):** legibilidad por *commit* y un `liv-revisor` de coherencia entre documentos; la persona aprueba como responsable funcional.
  - **ADR (A):** `liv-revision-adversarial` con dos `liv-revisor-critico` y la lente de legibilidad; la persona acepta.
  - Un hallazgo alto sin resolver se escala a la persona (`liv-revision-adversarial`). La segunda ronda examina solo lo corregido, con el mismo nivel.
- **Reporte final de cada sesión:** qué hizo, qué falló, qué no verificó, las marcas de esta lista y de `spec.md` que corresponden y los términos nuevos del glosario.
- Si una tarea necesita un archivo que no declara, la sesión lo avisa y actualiza esta lista antes de tocarlo. Si necesita algo que el diseño no tiene, se detiene y pregunta (plan, «Diseño de interfaz»).

## Mapa de los PR

Son los 37 PR del plan: R1, R2a y R2b (raíz); B1 a B8 y C1a a C10 (`backend/`; C9 es de `backend/bff/`); U1 a U4 (`ui/`); F0 a F5 (`frontend/`; F4a y F4b son dos) y E1 (`e2e/`). El PR de estas tareas (`docs/008-tareas`) es aparte.

| PR | Rama | Proyecto | Tareas | Depende para **escribirse** | Depende para **integrarse** | Rev. (véase «Reglas comunes») |
|---|---|---|---|---|---|---|
| R1 · Documentos | `docs/008-documentos` | raíz | T1 a T4 | H2 (ya) | nada (va primero) | D |
| R2a · ADR 0036 y 0037 | `docs/008-adr-original-y-dependencias` | raíz | T5 a T7 | H2 (ya) | nada; la persona acepta los ADR | A |
| R2b · ADR 0038 y 0039 | `docs/008-adr-motor-y-cargador` | raíz | T8 a T10 | H2 (ya); se escribe sobre la rama de R2a | R2a integrado (comparten `docs/arquitectura/00`, `01` y `02`); la persona acepta los ADR | A |
| B1 · Cargador de producción | `feat/008-b1-cargador` | `backend/` | T11 | R2b integrado | R2b | C |
| B2 · CJ01 y el árbol | `feat/008-b2-cj01-arbol` | `backend/` | T12, T13 | B1 integrado | B1 | C |
| B3 · Núcleo, CJ02 y CJ03 | `feat/008-b3-validador-cj02-cj03` | `backend/` | T14 a T16 | B2 integrado | B2 | C |
| B4 · CJ04, CJ05, CJ06 y CJ08 | `feat/008-b4-cj04-cj05-cj06-cj08` | `backend/` | T17 a T19 | B3 integrado | B3 | C |
| B5 · CJ12, CJ13 y CJ14 | `feat/008-b5-cj12-cj13-cj14` | `backend/` | T20, T21 | B4 integrado | B4 | C |
| B6 · CJ15 y CJ16 | `feat/008-b6-cj15-cj16` | `backend/` | T22, T23 | B5 integrado | B5 | C |
| B7 · CJ09, CJ10, CJ11, CJ17 y CJ18 | `feat/008-b7-cj09-cj10-cj11-cj17-cj18` | `backend/` | T24 a T26 | B6 integrado | B6 | C |
| B8 · Pruebas *golden* | `feat/008-b8-golden` | `backend/` | T27 a T29 | B7 integrado | B7 | C |
| C1a · Tabla, índices, RLS y permisos | `feat/008-c1a-tabla` | `backend/` | T30, T31 | R1 y R2a integrados | R1, R2a y B8 (orden del backend) | C |
| C1b · Disparadores | `feat/008-c1b-disparadores` | `backend/` | T32 | C1a integrado | C1a | C |
| C2a · Contrato: consultas | `feat/008-c2a-contrato-consultas` | `backend/` | T33 | R1 integrado | R1 y B1 (orden del backend). La CI publica `contract-jurisdiction-v1.0.0` | C |
| C2b · Contrato: acciones y `tax.yaml` 1.2.0 | `feat/008-c2b-contrato-acciones` | `backend/` | T34 | C2a integrado **y la *release* `contract-jurisdiction-v1.0.0` publicada** | C2a y esa *release*. La CI publica `contract-jurisdiction-v1.1.0` y `contract-tax-v1.2.0` | C |
| C3 · Motor de ingesta (`platform`) | `feat/008-c3-motor-ingesta` | `backend/` | T35 a T37 | R2b integrado | C1b (orden del backend) | C |
| C4 · Dominio del ciclo de vida | `feat/008-c4-dominio-ciclo` | `backend/` | T38, T39 | B8 y C3 integrados | C9 (orden del backend) | C |
| C5a · Carga | `feat/008-c5a-carga` | `backend/` | T40 a T42 | B8, C1b, C3 y C4 integrados | C4 | C |
| C5b · API de consulta, carga y descarga | `feat/008-c5b-api-consulta` | `backend/` | T43, T44 | C2b y C5a integrados | C5a | C |
| C6a · Aprobar, rechazar y anular | `feat/008-c6a-aprobar-rechazar-anular` | `backend/` | T45, T46 | C5b integrado | C5b | C |
| C6b · Publicar | `feat/008-c6b-publicar` | `backend/` | T47 | C6a integrado | C6a | C |
| C7 · Auditoría | `feat/008-c7-auditoria` | `backend/` | T48, T49 | C6b integrado | C6b | C |
| C8 · Punto 9 | `feat/008-c8-punto-9` | `backend/` | T50 | C6b y C2b integrados | C7 (orden del backend) | C |
| C9 · BFF: tope del cuerpo y `bff.yaml` 1.5.0 | `feat/008-c9-bff-tope` | `backend/bff/` | T51 a T53 | R2b integrado | C3 (orden del backend). La CI publica `contract-bff-v1.5.0` | C |
| C10 · Abrir la opción y limpieza | `feat/008-c10-menu` | `backend/` | T54, T55 | C6b integrado (se escribe en la ola 6) | C7, C8, C9, F5 y la validación en local (T74) | N |
| U1 · Iconos | `feat/008-ui-iconos` | `ui/` | T56 | H2 (ya) | nada | C |
| U2 · `Checkbox` y `TextArea` | `feat/008-ui-checkbox-textarea` | `ui/` | T57, T58 | U1 integrado | U1 | N |
| U3 · `FileField` | `feat/008-ui-filefield` | `ui/` | T59 | U1 integrado | U2 (comparten `public-api.ts` y `ui/CHANGELOG.md`) | N |
| U4 · `Tab` y 0.6.0 | `feat/008-ui-tab` | `ui/` | T60, T61 | U2 y U3 integrados | U3. **La persona publica 0.6.0 después** | N |
| F0 · Contratos, 0.6.0 y texto del punto 9 | `feat/008-f0-contratos` | `frontend/` | T62, T63 | C2b (*release*) y **0.6.0 publicada en npm** | las mismas | N |
| F1 · Lista | `feat/008-f1-lista` | `frontend/` | T64, T65 | F0 integrado | F0 | N |
| F2 · Versiones de la vigencia | `feat/008-f2-versiones` | `frontend/` | T66 | F1 integrado | F1 | N |
| F3 · Cargar | `feat/008-f3-cargar` | `frontend/` | T67, T68 | F1 integrado y C9 (*release* `contract-bff-v1.5.0`) | F1 y esa *release* | N |
| F4a · Detalle y pestañas | `feat/008-f4a-detalle` | `frontend/` | T69 | F2 integrado | F2 | N |
| F4b · Cambios y descarga | `feat/008-f4b-cambios-descarga` | `frontend/` | T70, T71 | F4a integrado | F4a | N |
| F5 · Diálogos de acción | `feat/008-f5-acciones` | `frontend/` | T72, T73 | F4a integrado | F4a | N |
| E1 · Pruebas de navegador | `feat/008-e2e` | `e2e/` | T75 a T78 | nada (se escriben en rojo mientras se hace F1 a F5; se **corren** con C10 aplicado en local) | F5, C10 y la validación en local (T74), **en verde** contra el entorno local | N |

**Tamaño.** Las cifras del plan son techos. Se miden antes de abrir cada PR; los que el plan marca con corte previsto son:
- **C3** (T35 a T37): si pasa de 400 líneas, C3a = T35 y T37 (recepción y registro) y C3b = T36 (ciclo de vida y coordinador). El corte sigue el plan y no el orden numérico: T37 solo necesita T35 y se escribe antes que T36, que usa su interfaz.
- **B3** (T14 a T16): si pasa de 400, B3a = T14 (núcleo) y B3b = T15 y T16 (CJ02 y CJ03).
- **B5, B6 y B7** se miden igual; si pasan de 400, se cortan por regla (B5: T20 y T21; B6: T22 y T23; B7: T24, T25 y T26).
- **C5a, C5b, C6a, C6b y C7** se miden igual; si alguno pasa de 400, se corta por tareas, sin cambiar el orden.
- **U4** y **F0** a **F5** se miden igual.

### Orden de integración (obligatorio; plan, «Dependencias, paralelismo y recursos»)

1. **R1**, **R2a** y **R2b**. R2a antes que R2b, porque los dos suben de versión `docs/arquitectura/01` y `02`. La persona acepta los cuatro ADR en la H3 de su PR.
2. **`backend/`, en este orden** (plan): B1 → C2a → *(esperar la *release* `contract-jurisdiction-v1.0.0`)* → C2b → B2 → B3 → B4 → B5 → B6 → B7 → B8 → C1a → C1b → C3 → C9 → C4 → C5a → C5b → C6a → C6b → C7 → C8. C2a y C2b van pronto para que el *frontend* empiece; C9 va antes de C4 para que F3 tenga `bff.yaml` 1.5.0.
3. **`ui/`:** U1 → U2 → U3 → U4, en serie (todos tocan `ui/CHANGELOG.md` y `public-api.ts`). **La persona publica `liv-components` 0.6.0 en npm** (`ui/README.md` › «Publicar una versión»). F0 no empieza antes.
4. **`frontend/`:** F0 → F1 → F2 → F4a → F3 → F4b → F5. Cada uno parte de `main` con el anterior integrado. Las rutas de la 1.5.9 llevan a «Próximamente» mientras la opción no esté `AVAILABLE` (C10), así que estos PR no se alcanzan desde el menú.
5. **Validación en local (T74)**, con C10 aplicado en local (no integrado), de todas las pantallas a la vez. Un defecto da un PR de corrección del proyecto dueño, con su prueba primero.
6. **C10**, que puede escribirse en la ola 6 en paralelo con T74 pero se integra después de C7, C8, C9, F5 y T74: así la opción se abre cuando existen la API, su auditoría y las pantallas (001-15).
7. **E1**, en verde contra el entorno local con C10 integrado.
8. El cierre del orquestador (*roadmap*, glosario, `ESTADO.md`, criterios de `spec.md`; véase «Cierre de la feature»).

> El plan dice «el frontend después de C10, como en la 007». Esta lista sigue la regla escrita de la 007 (su B10 se integró después del frontend; plan 007, «Validación del paso 9») y la frase del propio plan «mientras la opción esté en *Próximamente* (C10), llevan a *Próximamente*». Si la persona prefiere el orden literal del plan, solo cambia el orden de integración de C10 y de F0 a F5; no cambia ningún archivo. Véase «Puntos del plan que estas tareas interpretan».

### Paralelismo (máximo tres sesiones de trabajo a la vez, una sola en `backend/`; el entorno local es exclusivo)

Una oleada es un grupo de trabajo que corre a la vez; son las seis del plan. Una sola sesión de `ui/` a la vez y una sola de `frontend/` a la vez (comparten `public-api.ts`, `ui/CHANGELOG.md`, `app.routes.ts` y `accessibility.spec.ts`).

| Oleada | Sesión 1 (`backend/`) | Sesión 2 | Sesión 3 |
|---|---|---|---|
| 1 | — | R1 (T1 a T4, raíz) | R2a y R2b (T5 a T10, raíz, en serie) |
| 2 | B1 (T11), C2a (T33), C2b (T34) | U1 (T56), U2 (T57, T58) (`ui/`) | — |
| 3 | B2 a B8 (T12 a T29) | U3 (T59), U4 (T60, T61) (`ui/`); después la persona publica 0.6.0 | F0 (T62, T63) y F1 (T64, T65) (`frontend/`), **en cuanto** C2b esté integrada y 0.6.0 publicada |
| 4 | C1a, C1b, C3, C9, C4 (T30 a T32, T35 a T39, T51 a T53) | F2 (T66) y F4a (T69) (`frontend/`) | — |
| 5 | C5a, C5b, C6a, C6b, C7, C8 (T40 a T50) | F3 (T67, T68), F4b (T70, T71) y F5 (T72, T73) (`frontend/`) | E1 en rojo (T75 a T78, `e2e/`), sin el entorno |
| 6 | C10 (T54, T55) | Validación en local (T74) y corrida de E1 (entorno exclusivo) | — |

- **Con R1 y R2b integrados (ola 2):** B1 y C2a pueden escribirse a la vez que `ui/`; C2b espera la *release* de C2a.
- **La sesión 3 de la ola 3 (F0 y F1)** no empieza hasta que estén publicadas la *release* `contract-jurisdiction-v1.1.0` y `liv-components` 0.6.0 en npm; por eso no corre a la vez que U4.
- **En serie siempre:** T1 → T2 → T3 → T4; T5 → T6 → T7; T8 → T9 → T10; T12 → T13; T14 → T15 → T16; T17 → T18 → T19; T20 → T21; T22 → T23; T24 → T25 → T26; T27 → T28 → T29; T30 → T31; T35 → T37 → T36; T38 → T39; T40 → T41 → T42; T43 → T44; T45 → T46; T48 → T49; T51 → T52 → T53; T54 → T55; T56 → T57 → T58 → T59 → T60 → T61; T62 → T63; T64 → T65; T67 → T68; T70 → T71; T72 → T73; T75 → T76 → T77 → T78.
- **El entorno local es exclusivo** (ADR 0030): lo usan T74 y la corrida de E1, de una en una. Las pruebas del backend usan Testcontainers y no lo necesitan.

### Recursos compartidos

| Recurso | Quién lo escribe | Regla |
|---|---|---|
| `backend/` entero (una sola sesión) | B1 a B8 y C1a a C8, C10 | Todos tocan `pom.xml`, `ModularityTest`, las migraciones o los contratos: una sola sesión a la vez |
| `backend/pom.xml` | B1 (T11), B2 (T13), C2a (T33) | En serie. Ninguna otra tarea lo toca |
| Migraciones de `backend/` y `FlywayProfilesTest` | C1a (T30, T31) y C1b (T32) | Solo ellas; cada una agrega sus versiones a la lista de `FlywayProfilesTest` |
| `ModularityTest`, `ArchitectureRulesTest` y `AuthorizationRules` (clase auxiliar de `AuthorizationRulesTest`) | T11, T35, T37, T38, T40, T48, T50 | En serie, por el orden del backend |
| `jurisdiction.yaml` y la *release* de cada versión | C2a (1.0.0) y C2b (1.1.0) | C2b espera la *release* de C2a (ADR 0027). Lo lee el *frontend* por su *release* |
| `tax.yaml` 1.2.0 | C2b (T34) | Solo C2b |
| `bff.yaml` 1.5.0 y `contract-bff-v1.5.0` | C9 (T51) | Solo C9. F3 la adopta |
| `platform/web/ProblemDetailsHandler`, `ErrorCodes`, `application.yml` de la API | C3 (T35) | Solo C3 |
| `JurisdictionConfigValidator` (el registro de las reglas) | B3 a B7 | En serie: cada regla agrega su línea |
| `audit/application/AuditLog` y `AuthorizationRules.OPEN_USE_CASES` | C7 (T48) | Solo C7 |
| `menu-catalog.yaml`, `MenuCatalogTest`, `CapabilitiesTest` y `backend/dev/` | C10 (T54, T55) | Solo C10 |
| `backend/CHANGELOG.md` | todos los PR de `backend/` salvo C9 | Compartido: cada PR agrega su entrada; el segundo en integrarse rebasa |
| `backend/bff/CHANGELOG.md` | C9 | Solo C9 |
| `ui/CHANGELOG.md` y `ui/projects/liv-components/src/public-api.ts` | U1 a U4 | Compartidos; integración en serie |
| Versión de `liv-components` y su publicación en npm | U4 (sube a 0.6.0), la persona (publica) | Hasta la publicación, F0 no empieza |
| `frontend/api-spec/`, `package.json` y `package-lock.json` | F0 (T62) y F3 (T68, el salto de `bff`) | En serie |
| `app/app.routes.ts`, `app.routes.spec.ts`, `features/accessibility.spec.ts` y `frontend/CHANGELOG.md` | F1 a F5 | En serie |
| `app/features/jurisdiction-configs/` | F1 a F5 | En serie |
| Entorno local, usuarios de Keycloak y capturas de `e2e/` | T74 y E1 | Exclusivo, de una sesión a la vez |
| `docs/arquitectura/00`, `01` y `02` | R2a y R2b | En serie: R2a → R2b |
| Figma | — | Solo lectura; todo aprobado el 2026-11-16 |
| `ESTADO.md`, el *roadmap*, el glosario, `tasks.md` y los criterios de `spec.md` | Solo el orquestador | — |

### Validación en local y pruebas de navegador (ADR 0030)

- **Quién y cuándo:** T74 (sesión de `frontend/`, nivel estándar) en la ola 6, con la persona. Antes, cada PR del *frontend* se prueba con el servicio simulado y axe en jsdom; **no** se puede validar en local contra la API real, porque la API completa llega en la ola 5. Cada PR de `frontend/` lo dice en su descripción y cita después el resultado de T74.
- **Cómo:** el sistema se levanta como dice `README.md` › «Ejecutar LIV en local». La rama de C10 se aplica **solo en local** (nunca se empuja ni se integra por este camino). La persona corre `backend/dev/limpiar-vigencias.sh` antes de cada corrida de `e2e/`.
- **Qué se comprueba:** cada pantalla y cada diálogo contra sus *frames* de Figma (los nodos están en el plan, «Diseño de interfaz»), con el navegador integrado, y `npm test` de `e2e/` en verde (sus flujos, las capturas y axe).
- **Resultado:** va a la descripción del PR de E1 y al reporte final. Una captura de referencia nueva o cambiada la aprueba la persona.

### Lo que hace la persona (nunca un agente)

- Aprueba la H3 de cada PR e integra. Aprueba R1 como responsable funcional (cambia documentos funcionales y `AGENTS.md`).
- **Acepta los ADR 0036, 0037, 0038 y 0039** en R2a y R2b. El PR no se integra con un ADR en «Propuesto».
- **Integra C2a y espera a que la CI publique `contract-jurisdiction-v1.0.0`** antes de C2b.
- **Publica `liv-components` 0.6.0 en npm** (`ui/README.md` › «Publicar una versión») después de integrar U4. **F0 no se abre hasta que esté publicada.**
- Levanta el entorno local, corre `backend/dev/limpiar-vigencias.sh` antes de cada corrida de `e2e/` y acompaña T74.
- **Aprueba las capturas de `e2e/`:** la lista, el historial, el detalle y cada diálogo (nuevas), y la del menú de la 001 (`inicio-administrador-menu`, vuelta a aprobar porque la 1.5.9 deja de estar en «Próximamente»).
- **Confirma el orden de C10 y del *frontend* (punto 1 de «Puntos del plan que estas tareas interpretan») antes de integrar F0**, y decide los demás puntos.

### Puntos del plan que estas tareas interpretan

Ninguno cambia la spec ni el alcance. Están aquí para que la persona los confirme o los corrija antes de que arranque la ola que los usa:

1. **Orden de C10 y del *frontend*.** El plan dice «C10 se integra después de validar el frontend en local, y el frontend después de C10, como en la 007», y a la vez que C10 se integra «al final» y que, con la opción en *Próximamente*, las rutas llevan a «Próximamente». Esta lista usa la regla de la 007: el *frontend* primero (inerte) y C10 al final. Si se prefiere el orden literal, cambia solo el orden de integración.
2. **Validación en local diferida a T74.** El plan pide validar el *frontend* en local «cuando C10 esté listo» (ola 6), pero `liv-sdd-feature` pide citar esa validación en cada PR del *frontend*. Aquí cada PR del *frontend* se integra con sus pruebas de jsdom y axe, y la validación contra la API real se hace una vez en T74; un defecto da un PR de corrección antes de C10.
3. **`IngestionLimits` antes de C3.** El plan pide que el cargador de B1 use `IngestionLimits.MAX_FILE_BYTES`, pero esa clase nace en C3, después de B1. B1 conserva la constante del cargador (1.048.576, como hoy `MAX_BYTES`) y **T42 la reemplaza por `IngestionLimits.MAX_FILE_BYTES`** con una prueba que las ata, para que quede una sola constante.
4. **Usuarios de `e2e/`.** El plan dice que E1 usa `dev.aprobador.tablas`, `dev.juridico` y `dev.auditor`, pero `APROBADOR_TABLAS`, `JURIDICO` y `AUDITOR` exigen segundo factor (`liv-mfa-requerido` en `backend/identity/config/base/liv-realm.yaml`) y `e2e/support/acceso.ts` solo sabe entrar con TOTP conocido. T75 crea sus equivalentes con TOTP (`e2e.aprobador`, `e2e.juridico` y `e2e.auditor`), como se hizo con `e2e.admin.b` en la 007. `dev.cargador.tablas` y `dev.consulta` no exigen segundo factor y se usan tal cual.
5. **La prueba de las palabras clave de CJ01.** El plan la ubica en C2a y dice que falla «si la pantalla no tiene texto» para alguna palabra clave del esquema, pero en C2a no hay pantalla. T33 comprueba que las palabras clave de los esquemas 1.0 y 1.1 son exactamente las que el contrato documenta; T69 comprueba, en la pantalla, que cada palabra clave documentada tiene su texto.
6. **El número de PR.** La solicitud del orquestador habla de 38 PR; el plan, en su mapa, cuenta 37 (3 de la raíz, 8 del bloque B, 14 del C, 4 de `ui/`, 7 del *frontend* y 1 de `e2e/`). Esta lista cubre los 37 del mapa; no falta ninguno.
7. **C9 es de `backend/bff/`.** Es un proyecto Maven aparte, con su `CHANGELOG.md`, su CI (`bff-pr.yml`) y su `PackageCatalogTest`. Se escribe e integra dentro de la sesión de `backend/`, como dice el plan; como no comparte archivos con la API, la persona puede autorizar que corra en paralelo con la sesión de `backend/`, pero no se prevé.
8. **`UploadSizeLimitsIT` no correría en `./mvnw verify`.** `backend/pom.xml` no tiene `failsafe` y `surefire` solo recoge `*Test`; agregar `failsafe` cambia el *build* y la CI y exige aprobación (`AGENTS.md` §4.5). Estas tareas la llaman `UploadSizeLimitsTest`; su contenido es el del plan (C5b).
9. **`ModularityTest` en C4.** El agregado usa `shared.RuleViolation` desde C4, así que la prueba «solo `platform` y `tax` dependen de `shared`» se ajusta en T38 y no en C5a, como ubica el plan; si no, el `verify` de C4 queda en rojo.
10. **Los plazos de Tomcat.** El plan los fija en dos sitios (C3 y «Topes de tamaño»): la API lleva `connection-timeout` de 20 s y `keep-alive-timeout` de 60 s, y el BFF, `connection-timeout` de 20 s. Las tareas los escriben como `server.tomcat.*`, porque el `connection-timeout: 5000` que ya traen los dos `application.yml` es otra cosa.

---

## PR R1 · Documentos (raíz, rama `docs/008-documentos`)

Se integra **antes** de C1a y de C2a: la prueba de la matriz de permisos y el documento deben coincidir (`docs/arquitectura/02` §4.4), y el contrato publica los códigos del documento. Es la tabla «Documentos que cambian» de la spec más H2‑4, H2‑14, H2‑19 y H2‑20. **No toca** `spec.md`, los anexos YAML, los ADR, `docs/arquitectura/**`, el glosario ni el *roadmap*. Toda regla se escribe como propuesta pendiente de revisión jurídica (`AGENTS.md` §8). Revisión: D. Si el PR pasa de 400 líneas, se corta en R1a (T1 a T3) y R1b (T4).

- [ ] **T1** · ERD 2.5.
  - **Proyecto:** raíz · **PR:** R1
  - **Archivos:** `docs/funcional/ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md` (encabezado, el esquema nuevo `jurisdiction`: diagrama Mermaid, lista de tablas, restricciones y la nota de las FK; la FK de `ledger.tax_obligation`; las notas de las tablas que se llenarán; la lista previa a Flyway).
  - **Depende de:** ninguna.
  - **Recursos:** el ERD (solo R1 lo edita).
  - **Prueba primero:** lista de comprobación escrita antes del texto:
    - la versión pasa a 2.5 con su motivo, y el conteo, de 80 a 81 tablas en 10 esquemas funcionales, en el encabezado y en la lista;
    - `jurisdiction.jurisdiction_config` con **todas** las columnas, tipos y reglas de «Ajustes al modelo de datos» del plan, y la nota de que reemplaza a `core.jurisdiction_config` del Doc. 03 §5.6;
    - los cinco `UNIQUE` (`(id, administration_id)`, `(id, administration_id, fiscal_year)`, una publicada, una en curso y `version_number` de publicadas y reemplazadas);
    - los `CHECK` de forma, de estado y de segregación (`approved_by`, `published_by` y `rejected_by` distintos de `uploaded_by`; `annulled_by = uploaded_by`);
    - las FK compuestas con `administration_id` y, entre versiones, con `fiscal_year`;
    - los dos disparadores (transiciones y el de restricción diferido), las tres políticas RLS (sin `DELETE`) y los permisos de `liv_app` por columna;
    - la FK de `ledger.tax_obligation` a (`jurisdiction_config_id`, `administration_id`);
    - la nota en `tax.tax_rate`, `tax.tax_calendar`, `tax.base_adjustment_rule`, `tax.penalty_rule`, `documents.legal_instrument` y `ledger.revenue_distribution`: las llena la feature de cada tabla desde lo publicado (decisión 1);
    - la autorización H2‑1 en la lista previa a Flyway, con las nueve filas de la tabla del plan.
- [ ] **T2** · Doc. 03 v0.6.
  - **Proyecto:** raíz · **PR:** R1
  - **Archivos:** `docs/funcional/03_Configuracion_Jurisdiccion_y_Tablas_2026.md` (encabezado, §5.2 a §5.6, §7, §8 y §9; los SHA‑256 de §9.2 no cambian).
  - **Depende de:** T1 (los nombres de tablas y columnas).
  - **Recursos:** el Doc. 03 (solo R1 lo edita).
  - **Prueba primero:** lista de comprobación, cada punto contrastado con la spec y el plan («Documentos que cambian» y R1):
    - **§5.2:** la fila `vigencia` (se valida contra la vigencia de la administración, no se materializa); «se materializa en» pasa a «la llena después la feature de cada tabla»; la nota de la precedencia;
    - **§5.3:** *Rechazada* y *Anulada*; la publicación guarda la copia inmutable (deja de decir «materialización transaccional»); los códigos `CJC-01` a `CJC-04` (H2‑4);
    - **§5.4:** CJ17 y CJ18 (con las fechas `MM-DD` y `LEAP_DAY`, H2‑19); el alcance de CJ02 (decisión 11 y H2‑6), CJ04 y CJ06 (decisión 12), CJ05 con la `tarifa_maxima` (decisión 14), CJ08 *no evaluable* sin DANE (H2‑5), CJ12 con las fechas del convenio (H2‑20); las tres clases de hallazgo y la advertencia por ruta (decisión 13); CJ09 y CJ10 *no evaluables* (decisión 5); la versión del validador (decisión 17);
    - **§5.5:** la precedencia (decisión 21); **§5.6:** el esquema `jurisdiction` y sus columnas; **§7:** los cambios a otros documentos de la v0.6; **§8:** el pendiente 9 resuelto y la pregunta de la precedencia;
    - **§9.2:** CJ09, CJ10, CJ17 y CJ18, la marca de la `tarifa_maxima` (H2‑16) y la remisión a las pruebas *golden*;
    - **§9.4 y §9.5:** el administrador no activa las modalidades por fuera del flujo de la 1.5.9 (decisión 2) y `ADMINISTRACION_DIRECTA` solo para `DEPARTAMENTO` o `DISTRITO_CAPITAL` (decisión 15);
    - ningún anexo YAML cambia.
- [ ] **T3** · Doc. 01 v0.7.
  - **Proyecto:** raíz · **PR:** R1
  - **Archivos:** `docs/funcional/01_Menu_y_Cargue_Tablas_Base_Liquidacion.md` (encabezado, §2, §3, §4, §6.1, §6.7, §8, §11 y la entrada de la versión 0.7 en §13, con el registro de los cambios a otros documentos al estilo del Doc. 03 §7).
  - **Depende de:** T1 y T2 (los códigos y los nombres).
  - **Recursos:** el Doc. 01 (solo R1 lo edita).
  - **Prueba primero:** lista de comprobación:
    - **§2:** los códigos `CG-01` a `CG-06` del motor de ingesta, con su estado HTTP (H2‑4), y la nota de que `F001` a `F003` siguen siendo de los CSV de catálogos;
    - **§3:** la 1.5.9 → `jurisdiction.jurisdiction_config`;
    - **§4:** las filas de la 1.5.9 en la matriz de acciones y la tabla de sus cinco permisos de acción (decisión 6), que coincide exacta con la tabla de la spec y con la matriz de `PlatformTablesTest`;
    - **§6.1:** el punto 9 lee `jurisdiction.jurisdiction_config` con `status = PUBLICADA`, y los puntos 4 a 7 verificarán lo que llene cada feature (decisión 1); **§6.7:** las opciones 1.5.5 a 1.5.8 solo muestran lo publicado;
    - **§8:** los eventos de la 1.5.9 (008-46); **§11:** el pendiente 8 para la 1.5.9; **§13:** la entrada de la v0.7.
- [ ] **T4** · Doc. 05 v0.3 y `AGENTS.md`.
  - **Proyecto:** raíz · **PR:** R1
  - **Archivos:** `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` (encabezado y §5.1) y `AGENTS.md` (§3.1, §5.2 y §5.3).
  - **Depende de:** T1 a T3.
  - **Recursos:** el Doc. 05 y `AGENTS.md` (solo R1 los edita).
  - **Prueba primero:** lista de comprobación:
    - **Doc. 05 §5.1:** las sanciones se leen de la configuración publicada y la opción 1.5.8 solo las muestra; versión 0.3;
    - **`AGENTS.md` §3.1:** «81 tablas en 10 esquemas funcionales» y «versión 2.5» del ERD, y el Doc. 05 «borrador (v0.3)»;
    - **§5.2:** `ADMINISTRACION_DIRECTA` solo para departamentos y distritos capitales (decisión 15), y la ruta `base_gravable.catalogo_ministerio.multiplicador_tablas`;
    - **§5.3:** los prefijos `CG` y `CJC` (H2‑14); nada más cambia en `AGENTS.md`.
  - **Al terminar T1 a T4:** legibilidad por *commit*, `liv-revisor` de coherencia entre documentos (códigos, versiones, conteos, permisos) y el PR. La persona aprueba como responsable funcional, también el cambio de `AGENTS.md`. El reporte al orquestador incluye las filas del *roadmap* y de `ESTADO.md` que la spec pide («Documentos que cambian»).

## PR R2a · ADR 0036 y 0037 (raíz, rama `docs/008-adr-original-y-dependencias`)

*Skill* `liv-adr`. Nivel alto. Se integra **antes** de C1a. Revisión: A.

- [ ] **T5** · ADR 0036 · El original de la configuración en la base de datos (excepción temporal).
  - **Proyecto:** raíz · **PR:** R2a · **Modelo:** nivel alto
  - **Archivos:** `docs/adr/ADR-0036-original-de-la-configuracion-en-la-base-de-datos.md` (nuevo), `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` (solo la fila 0036 de la tabla de §6) y `docs/arquitectura/02_Seguridad.md` (§7: la remisión al ADR, y su encabezado de versión).
  - **Depende de:** ninguna.
  - **Recursos:** `docs/arquitectura/00` y `02` (R2a y R2b: en serie).
  - **Prueba primero:** el ADR sigue la plantilla MADR de `liv-adr` y el formato de los ADR 0033 a 0035. Dice cada punto del plan (R2a) y de la decisión 10: el original va en `BYTEA` de hasta 1 MiB, solo texto UTF‑8 que el cargador seguro acepta, inmutable, servido como adjunto por la API; la excepción termina con la feature de la opción 1.3 o antes de producción, lo que llegue primero; esa feature trae ClamAV y el almacenamiento de objetos, escanea los originales ya guardados y los migra con una migración que conserva su SHA‑256; estado «Propuesto»; la fila del doc. 00 §6 coincide con el estilo de las anteriores.
- [ ] **T6** · ADR 0037 · Dependencias entre módulos de la feature 008.
  - **Proyecto:** raíz · **PR:** R2a · **Modelo:** nivel alto
  - **Archivos:** `docs/adr/ADR-0037-dependencias-entre-modulos-de-la-feature-008.md` (nuevo), `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` (solo la fila 0037) y `docs/arquitectura/01_Arquitectura_de_Software.md` (§3.1 nota 1, la figura del §3.2 y el encabezado de versión; **no** el glosario del §15).
  - **Depende de:** T5 (la fila anterior de la tabla).
  - **Recursos:** `docs/arquitectura/00` y `01`.
  - **Prueba primero:** el ADR dice cada flecha del plan: `tax → jurisdiction` (el puerto `FiscalYearDirectory` y la fachada `PublishedJurisdictionConfigs`, en el paquete raíz de `jurisdiction`); `jurisdiction → platform`, `jurisdiction → shared` y **`jurisdiction → core`** (`AdministrationTerritory`, para CJ17); `audit → jurisdiction`; nadie depende de `audit`; `jurisdiction` no depende de `tax`; `jurisdiction.domain` solo depende de `shared`; la figura del §3.2 coincide con el ADR y la nota 1 del §3.1 deja de ser propuesta.
- [ ] **T7** · Aceptación de los ADR 0036 y 0037 (H3).
  - **Proyecto:** raíz · **PR:** R2a · **Modelo:** nivel alto
  - **Archivos:** los dos ADR y sus filas del doc. 00 §6 (solo el estado y la fecha).
  - **Depende de:** T5, T6 y de que la persona los acepte (solo un humano acepta).
  - **Recursos:** ninguno.
  - **Prueba primero:** el estado del ADR y el de su fila dicen lo mismo («Aceptado», con la fecha que da la persona); el PR no se integra con un ADR en «Propuesto». **C1a espera este PR integrado.**
  - **Al terminar T5 a T7:** revisión A y el PR (H3).

## PR R2b · ADR 0038 y 0039 (raíz, rama `docs/008-adr-motor-y-cargador`)

*Skill* `liv-adr`. Nivel alto. Se escribe sobre la rama de R2a (T5 a T7) y se integra después de R2a. Se integra **antes** de B1, C3 y C9. Revisión: A.

- [ ] **T8** · ADR 0038 · Piezas comunes del motor de ingesta en `platform`.
  - **Proyecto:** raíz · **PR:** R2b · **Modelo:** nivel alto
  - **Archivos:** `docs/adr/ADR-0038-motor-de-ingesta-en-platform.md` (nuevo), `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` (solo la fila 0038), `docs/arquitectura/01_Arquitectura_de_Software.md` (§5.3, el diagrama del §6 y su encabezado de versión) y `docs/arquitectura/02_Seguridad.md` (§7: el tope técnico fijo de H2‑8 en lugar del «límite por tipo de carga en `parametros_sistema`»).
  - **Depende de:** T7 (la rama de R2a: R2b se escribe sobre ella y se integra después de R2a).
  - **Recursos:** `docs/arquitectura/00`, `01` y `02`.
  - **Prueba primero:** el ADR dice cada punto del plan (R2b) y de H2‑8, H2‑10 y H2‑24:
    - qué piezas son comunes («Enfoque») y su lugar, `platform.ingestion`, con interfaz con nombre de Spring Modulith (`@NamedInterface("ingestion")`), la primera del proyecto;
    - el coordinador `IngestionEngine` y quién llama a cada interfaz (`IngestionReader`, `IngestionRules`, `VersionStore`, `IngestionPublisher`);
    - el registro de intentos rechazados y que su transacción es aparte (H2‑10);
    - los ocho estados de la decisión 7, los códigos `CG` y la excepción `PayloadTooLarge` (413), la única fuera de los tipos del ADR 0035;
    - los topes de H2‑8 en el BFF y en la API, y que la API no depende del `Content-Length` porque el *gateway* lo quita al reenviar;
    - §5.3: cuándo se construye cada materialización y que la validación de los anexos (IN‑11) llega con la feature aparte; §6: RECIBIDO y RECHAZADO dejan de ser estados guardados.
- [ ] **T9** · ADR 0039 · Cargador de YAML de producción.
  - **Proyecto:** raíz · **PR:** R2b · **Modelo:** nivel alto
  - **Archivos:** `docs/adr/ADR-0039-cargador-de-yaml-de-produccion.md` (nuevo), `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` (solo la fila 0039) y, **solo si el ADR reemplaza en parte al 0032 y la *skill* `liv-adr` lo pide**, el estado de `docs/adr/ADR-0032-validacion-del-esquema-de-configuracion-de-jurisdiccion.md` y su fila (la única edición permitida de un ADR aceptado; la persona la aprueba en este PR).
  - **Depende de:** T8.
  - **Recursos:** `docs/arquitectura/00`.
  - **Prueba primero:** el ADR dice: `org.yaml:snakeyaml` declarado en `compile` con la versión de Spring Boot (cambia lo que decía el ADR 0032); `json-schema-validator` 3.0.9 a `compile`, con `jackson-dataformat-yaml` excluido; ningún convertidor de `application/yaml`; un solo cargador, que usan también las pruebas del esquema y de los casos comunes (ADR 0034); no es una dependencia nueva, las dos ya están en el árbol.
- [ ] **T10** · Aceptación de los ADR 0038 y 0039 (H3).
  - **Proyecto:** raíz · **PR:** R2b · **Modelo:** nivel alto
  - **Archivos:** los dos ADR y sus filas del doc. 00 §6 (solo el estado y la fecha), y el ADR 0032 si T9 lo tocó.
  - **Depende de:** T8, T9 y de que la persona los acepte.
  - **Recursos:** ninguno.
  - **Prueba primero:** igual que T7. **B1, C3 y C9 esperan este PR integrado.**
  - **Al terminar T8 a T10:** revisión A y el PR (H3).

---

## Bloque B — Cargador y validador (`backend/`)

Java puro en `jurisdiction/domain`, salvo el cargador y CJ01, que son adaptadores en `jurisdiction/infrastructure`. Cada regla con su prueba primero y el código de la regla en el `@DisplayName`. Los mensajes son motivos del «Catálogo de motivos» del plan (H2‑22): el texto lo arma la pantalla. Revisión de todo el bloque: C. Nada del bloque necesita la migración: se prueba con árboles escritos a mano y con las copias de los anexos.

## PR B1 · Cargador seguro de producción (`backend/`, rama `feat/008-b1-cargador`)

Con R2b integrado (ADR 0039).

- [ ] **T11** · El cargador pasa a producción, `pom.xml`, `.gitattributes` y la regla de ArchUnit.
  - **Proyecto:** `backend/` · **PR:** B1
  - **Archivos:**
    - `main/jurisdiction/infrastructure/yaml/SafeYamlLoader.java` (movido desde `test/contracts/`, sin cambiar su comportamiento; recibe bytes y devuelve el árbol o el `motivo` de los casos comunes);
    - `test/jurisdiction/infrastructure/yaml/SafeYamlLoaderTest.java` (movido desde `test/contracts/`);
    - `test/contracts/JurisdictionConfigCommonCasesTest.java`, `CommonCases.java`, `JurisdictionConfigSchemas.java`, `TestConfigurations.java` `JurisdictionConfigSchemaTest.java`, `JurisdictionConfigSchema11Test.java`, `JurisdictionConfigSchemaMutationTest.java` y las demás pruebas de `test/contracts/` que importen el cargador (el compilador las señala);
    - `test/jurisdiction/NoYamlConverterTest.java` (nueva) y `test/architecture/ArchitectureRulesTest.java` (la regla nueva);
    - `backend/pom.xml` (solo `snakeyaml` en `compile` sin versión y `json-schema-validator` en `compile` con la exclusión, ADR 0039), `backend/.gitattributes`, `backend/README.md` (si cita el lugar del cargador) y `backend/CHANGELOG.md`.
  - **Depende de:** R2b integrado.
  - **Recursos:** `backend/pom.xml`; `ArchitectureRulesTest`; `backend/.gitattributes`.
  - **Prueba primero** (rojo; 008-07, 008-08 y 008-32):
    - **008-07 y 008-08:** `JurisdictionConfigCommonCasesTest` (los casos de carga de todos los `*.casos.json`) y `SafeYamlLoaderTest` pasan a usar la clase de producción (no compilan); `NoYamlConverterTest` comprueba que ningún `HttpMessageConverter` del contexto acepta `application/yaml` ni `application/x-yaml`;
    - `ArchitectureRulesTest`, como lista blanca: `..liv.jurisdiction.domain..` solo depende de `java..`, de `..liv.jurisdiction.domain..` y de `..liv.shared..` (sin otros módulos, sin Jackson ni otra biblioteca);
    - **008-32:** `.gitattributes` con `src/test/resources/contracts/jsonschema/config-jurisdiccion/** text eol=lf`; `git check-attr eol` sobre las copias de `1.0/` y `1.1/` da `lf` (la prueba del SHA‑256 es T28).
  - **Notas:** el límite de `TAMANO` sigue siendo la constante del cargador (1.048.576); T42 la reemplaza por `IngestionLimits.MAX_FILE_BYTES` («Puntos del plan que estas tareas interpretan», 3). El backend queda con un solo cargador (008-08).

## PR B2 · CJ01 y el árbol del dominio (`backend/`, rama `feat/008-b2-cj01-arbol`)

Con B1 integrado.

- [ ] **T12** · `ConfigTree`, `JsonPointer` y los decimales exactos.
  - **Proyecto:** `backend/` · **PR:** B2
  - **Archivos:** `main/jurisdiction/domain/ConfigTree.java` y `JsonPointer.java` (y los tipos pequeños que necesiten); `test/jurisdiction/domain/ConfigTreeTest.java`, `JsonPointerTest.java` y `ConfigTreeDecimalsTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** B1 integrado.
  - **Recursos:** ninguno.
  - **Prueba primero:** `ConfigTreeDecimalsTest` (**008-30**): `0.7 + 0.2 + 0.1` en `BigDecimal` suma exactamente 1 y una tarifa `0.015` queda con escala 3; `ConfigTreeTest`: objeto con orden de claves, lista, texto, número, booleano y nulo, inmutables; `JsonPointerTest`: el RFC 6901 (escapes `~0` y `~1`), la misma forma de ruta de los casos comunes. Sin Jackson en el dominio.
- [ ] **T13** · CJ01: el puerto `SchemaCheck`, su adaptador y el registro de versiones.
  - **Proyecto:** `backend/` · **PR:** B2
  - **Archivos:** `main/jurisdiction/domain/SchemaCheck.java` (puerto); `main/jurisdiction/infrastructure/schema/NetworkntSchemaCheck.java` (movido desde `JurisdictionConfigSchemas` de las pruebas) y la conversión de `JsonNode` a `ConfigTree`; `test/jurisdiction/infrastructure/schema/SchemaCheckTest.java`; `test/contracts/JurisdictionConfigSchemas.java` y las pruebas del esquema que lo usen; `backend/pom.xml` (solo la copia de `contracts/jsonschema/config-jurisdiccion/` al *classpath*); `backend/CHANGELOG.md`.
  - **Depende de:** T12.
  - **Recursos:** `backend/pom.xml`.
  - **Prueba primero:** `SchemaCheckTest` (**CJ01 · 008-11**): el registro `{1.0, 1.1}`; el enrutado por la versión declarada, de modo que un archivo 1.0 no se valida contra 1.1 ni al revés; sin `esquema_version`, o con una versión que no existe, un error en `/esquema_version` (`SCHEMA_VERSION_UNKNOWN`); cada error de esquema con su ruta, `SCHEMA_VIOLATION` y la palabra clave en `params.keyword`; sin carga remota y sin aceptar un decimal como `integer` (ADR 0032); los casos de validación de cada `*.casos.json` con el registro del ADR 0034 (la versión X solo con las ≤ X); la conversión conserva los decimales sin pasar por `double` (**008-30**).
  - **Al terminar T12 y T13:** revisión C y el PR (H3).

## PR B3 · Núcleo del validador, CJ02 y CJ03 (`backend/`, rama `feat/008-b3-validador-cj02-cj03`)

Con B2 integrado. Carpetas: `main/jurisdiction/domain/validation/` y su espejo en `test/`.

- [ ] **T14** · `Finding`, `ValidationResult`, `ValidationContext` y `JurisdictionConfigValidator`.
  - **Proyecto:** `backend/` · **PR:** B3
  - **Archivos:** `main/jurisdiction/domain/validation/Finding.java`, `ValidationResult.java`, `ReviewItem.java`, `ValidationContext.java` y `JurisdictionConfigValidator.java` (con `VERSION = "1.0.0"`, H2‑17) y la fusión de advertencias; `test/jurisdiction/domain/validation/JurisdictionConfigValidatorTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** B2 integrado.
  - **Recursos:** `JurisdictionConfigValidator` (el registro de reglas: B4 a B7 le agregan).
  - **Prueba primero:** `JurisdictionConfigValidatorTest`:
    - **008-29:** evalúa todas las reglas y da todos sus hallazgos, no solo el primero; con algún error, el resultado es de una carga *Con errores*, y sin errores, de una *Validada*; el resultado lleva la versión del esquema y la del validador;
    - **CJ01 · 008-11:** si CJ01 da errores, el resultado lo dice (`skippedAfterSchemaErrors`) y no evalúa CJ02 en adelante ni trae advertencias ni lista de revisión;
    - **008-13 y decisión 13:** una advertencia por ruta, con todos los códigos que la señalan, ordenados; un `PENDIENTE` en el `estado` de una regla se informa en la ruta de la regla; una ruta con un error no da advertencia;
    - el `digest` (SHA‑256 de la forma canónica de los hallazgos y de la lista de revisión) es estable y cambia si cambia un hallazgo;
    - las modalidades que liquidan salen del archivo (`activa` y `liquida`), no de una lista del código (decisión 14).
- [ ] **T15** · CJ02.
  - **Proyecto:** `backend/` · **PR:** B3
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (la regla CJ02) y su línea en `JurisdictionConfigValidator.java`; `test/jurisdiction/domain/validation/rules/Cj02Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T14.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj02Test` (**CJ02 · 008-12 y 008-13**; decisión 11; H2‑6): una regla obligatoria (`jurisdiccion`, las tarifas de las categorías usadas, `redondeo`, `calendario` y las que el archivo marque `obligatoria: true`) con `estado: PENDIENTE` es un error en su ruta; `cartera.intereses_mora` y `cartera.prescripcion` solo son obligatorias si alguna modalidad activa liquida, aunque traigan `obligatoria: true`; `cartera.prescripcion.competente` no cuenta; un campo numérico de `cartera` en `PENDIENTE` cuya regla más cercana con `estado` no está en `PENDIENTE` es un error; cada otro `PENDIENTE` es una advertencia y cada `estado: POR_CONFIRMAR` entra en la lista de revisión, con su ruta y su norma; CJ02 se evalúa sobre el `estado` de la regla, no sobre cada valor (`AGENTS.md` §5.2); un municipio del 20 % pasa si no trae `calendario` ni `redondeo` y falla si los trae en `PENDIENTE` (la prueba de archivo completo es T29).
- [ ] **T16** · CJ03.
  - **Proyecto:** `backend/` · **PR:** B3
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (la regla CJ03) y su línea en `JurisdictionConfigValidator.java`; `test/jurisdiction/domain/validation/rules/Cj03Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T14.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj03Test` (**CJ03 · 008-14**): cada clave `norma` fuera de la sección `normas` debe nombrar el `id` de una norma de `normas` (no `codigo`); si no, un error `UNKNOWN_NORMA` en la ruta de la cita, en cualquier parte del archivo.
  - **Al terminar T14 a T16:** revisión C y el PR (H3).

## PR B4 · CJ04, CJ05, CJ06 y CJ08 (`backend/`, rama `feat/008-b4-cj04-cj05-cj06-cj08`)

Con B3 integrado. Misma carpeta de reglas que B3.

- [ ] **T17** · CJ04 y la categoría usada.
  - **Proyecto:** `backend/` · **PR:** B4
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ04 y la definición de categoría usada) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj04Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** B3 integrado.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj04Test` (**CJ04 · 008-15**; decisión 12): una categoría se usa si alguna regla de `categorias.reglas` la asigna y, para `PUBLICO`, solo si `sujecion.servicio_publico.gravado` es `true`; por cada categoría usada, el primer rango empieza en 0, cada `desde_exclusivo` es el `hasta_inclusivo` anterior y el último es abierto (`hasta_inclusivo: null`); un hueco, un solapamiento, un primero distinto de 0, un último cerrado o una categoría usada sin rangos son errores en la ruta del rango; una categoría no usada sin rangos (`PUBLICO` en Ipiales) es una advertencia de su regla, fusionada con la de CJ02.
- [ ] **T18** · CJ05 y CJ06.
  - **Proyecto:** `backend/` · **PR:** B4
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ05 y CJ06) y sus líneas en el validador; `test/jurisdiction/domain/validation/rules/Cj05Test.java` y `Cj06Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T17.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj05Test` (**CJ05 · 008-16**; decisión 14): con la categoría `ELECTRICO` en `tarifas`, su `tarifa_maxima` es obligatoria (si falta, `MAX_RATE_MISSING`) y cada tarifa debe ser ≤ la máxima (comparación de `BigDecimal`; `RATE_ABOVE_MAX`); la `tarifa_maxima` entra en la lista de revisión con `kind` `TARIFA_MAXIMA` (H2‑16); sin `ELECTRICO` no da nada (el municipio del 20 %); ningún 1 % escrito en el código. `Cj06Test` (**CJ06 · 008-17**): por cada modalidad con `liquida: true`, activa o no, la suma de `distribucion[].porcentaje` es exactamente 1 por `compareTo` (`0.7 + 0.2 + 0.1` pasa); `PARTICIPACION_20`, que no liquida, no se suma.
- [ ] **T19** · CJ08.
  - **Proyecto:** `backend/` · **PR:** B4
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ08) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj08Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T17.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj08Test` (**CJ08 · 008-18**, con la enmienda 1): un ajuste `SAN_ANDRES` con `jurisdiccion.codigo_dane` distinto de `"88"` es un error `SAN_ANDRES_OUTSIDE_88`; con el DANE en `PENDIENTE` es *no evaluable* `DANE_PENDING` (H2‑5); `"88"` es una constante del texto de la regla (decisión 14).
  - **Al terminar T17 a T19:** revisión C y el PR (H3).

## PR B5 · CJ12, CJ13 y CJ14 (`backend/`, rama `feat/008-b5-cj12-cj13-cj14`)

Con B4 integrado (el orden del backend es en serie; B5 solo necesita B3).

- [ ] **T20** · CJ12.
  - **Proyecto:** `backend/` · **PR:** B5
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ12) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj12Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** B4 integrado.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj12Test` parametrizada (**CJ12 · 008-20**) con **los 20 casos** de la tabla del plan (B5), que salen del Doc. 03 §9.5 y sus «otros controles»; cada caso comprueba la clase, el código, la ruta (la de la modalidad, o `/competencia/modalidades` si no hay ninguna activa) y el motivo (`NO_ACTIVE`, `EXCLUSIVE`, `REQUIRES_BORDER_ZONE`, `AGREEMENT_INCOMPLETE`, `AGREEMENT_DATES_INVERTED` y `DIRECT_ADMINISTRATION_TYPE`); el tipo de la jurisdicción sale del archivo (`DEPARTAMENTO` o `DISTRITO_CAPITAL` para la modalidad D, decisión 15); el convenio con la fecha de inicio posterior a la de fin es error (H2‑20).
- [ ] **T21** · CJ13 y CJ14.
  - **Proyecto:** `backend/` · **PR:** B5
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ13 y CJ14) y sus líneas en el validador; `test/jurisdiction/domain/validation/rules/Cj13Test.java` y `Cj14Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T20.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj13Test` (**CJ13 · 008-22**): si alguna modalidad activa liquida, por cada grupo `fecha_inicio_pago ≤ fecha_limite_descuento ≤ fecha_limite_pago_sin_intereses` (si no, error `DATE_ORDER`; si falta, `DATE_MISSING`) y un límite sin intereses posterior a `vigencia.fin` es una advertencia `AFTER_FISCAL_YEAR_END`; sin modalidad que liquide, las fechas pueden faltar o estar en `PENDIENTE`. `Cj14Test` (**CJ14 · 008-23**): un plazo `DIAS` sin `dias`, una `FECHA_FIJA` sin `fecha` o los días del descuento mayores que los del plazo sin intereses son errores.
  - **Al terminar T20 y T21:** revisión C y el PR (H3).

## PR B6 · CJ15 y CJ16 (`backend/`, rama `feat/008-b6-cj15-cj16`)

Con B5 integrado. Se aplican a toda validación nueva, declare el `esquema_version` que declare (008-24 y 008-25): un archivo 1.0 sin `cartera` y con alguna modalidad activa que liquida da los errores de falta.

- [ ] **T22** · CJ15.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ15) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj15Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** B5 integrado.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj15Test` (**CJ15 · 008-24**): una prueba por condición del Doc. 03 §5.4: falta de `intereses_mora` con alguna modalidad activa que liquida (`SECTION_MISSING`); `base` con `TAX` y sin `INTEREST` (`BASE_WITHOUT_TAX`, `BASE_WITH_INTEREST`); `sobre_sanciones` coherente con `PENALTY` (`PENALTY_BASE_INCONSISTENT`); `bloquear_si_falta_tasa: true` (`MUST_BLOCK_WITHOUT_RATE`); `multiplo` mayor que 0 salvo con `SIN_REDONDEO` (`MULTIPLE_NOT_POSITIVE`); `inicio` igual a `calendario.inicio_mora` (`INTEREST_START_MISMATCH`); cada una también con `esquema_version: "1.0"`. El redondeo a $1.000 y su alcance (pendientes 2 y 12 del Doc. 05) **no** se codifican (`AGENTS.md` §5.1).
- [ ] **T23** · CJ16.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ16) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj16Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T22.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj16Test` (**CJ16 · 008-25**): una prueba por condición del Doc. 03 §5.4 (sanciones, prescripción, antigüedad y formato bancario) con los motivos del catálogo (`ACTIVE_PENALTY_WITHOUT_NORMA`, `DETERMINATION_PENDING`, `UNKNOWN_BANK`…); `sistema_determinacion` en `PENDIENTE` es un **error**; solo dos advertencias: `prescripcion.competente` en `PENDIENTE` (la misma advertencia de ese valor, fusionada con CJ02, que lleva los dos códigos) y `recaudo.archivo_banco.formato` junto con `formato_preferido` (`BOTH_FORMATS`); también con `esquema_version: "1.0"`.
  - **Al terminar T22 y T23:** revisión C y el PR (H3).

## PR B7 · CJ09, CJ10, CJ11, CJ17 y CJ18 (`backend/`, rama `feat/008-b7-cj09-cj10-cj11-cj17-cj18`)

Con B6 integrado.

- [ ] **T24** · CJ09, CJ10 y CJ11.
  - **Proyecto:** `backend/` · **PR:** B7
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ09, CJ10 y CJ11) y el modelo de la comparación (`Change`, `Comparison`) en `main/jurisdiction/domain/validation/`; sus líneas en el validador; `test/jurisdiction/domain/validation/rules/Cj09Cj10Test.java` y `Cj11Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** B6 integrado.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj09Cj10Test` (**CJ09 · CJ10 · 008-19**): sin catálogo publicado, un *no evaluable* cada uno, en `/base_gravable/catalogo_ministerio/multiplicador_tablas` y en `/vigencia/anio`, con motivo `NO_PUBLISHED_CATALOG`, que no bloquea. `Cj11Test` (**CJ11 · 008-21**): compara el árbol nuevo con el de la versión publicada, hoja por hoja (objetos por clave y listas por posición); cada cambio da sección, ruta, `ADDED`, `REMOVED` o `CHANGED` y el valor anterior y el nuevo como texto JSON canónico (`0.10` frente a `0.1` es un cambio); sin versión publicada, `firstVersion: true`; **nunca** da un hallazgo de error.
- [ ] **T25** · CJ17.
  - **Proyecto:** `backend/` · **PR:** B7
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ17) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj17Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T24.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj17Test` (**CJ17 · 008-26**; **008-45**): un error en cada ruta que no coincide: `/jurisdiccion/codigo_dane` y `/jurisdiccion/tipo` contra la entidad territorial del contexto (`DANE_MISMATCH`, `TYPE_MISMATCH`), `/vigencia/anio`, `/vigencia/inicio` y `/vigencia/fin` contra la vigencia (`YEAR_MISMATCH`, `FISCAL_YEAR_START_MISMATCH`, `FISCAL_YEAR_END_MISMATCH`) y `/vigencia/version_config` contra la última publicada más uno, o 1 (`VERSION_MISMATCH`); un DANE de otra administración no se acepta (la configuración de A no se carga en B).
- [ ] **T26** · CJ18.
  - **Proyecto:** `backend/` · **PR:** B7
  - **Archivos:** `main/jurisdiction/domain/validation/rules/` (CJ18) y su línea en el validador; `test/jurisdiction/domain/validation/rules/Cj18Test.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T24.
  - **Recursos:** `JurisdictionConfigValidator`.
  - **Prueba primero:** `Cj18Test` (**CJ18 · 008-27**; H2‑19): solo en las rutas que el esquema declara como fecha (las que usan su definición `AAAA-MM-DD` y la de `MM-DD`; las rutas salen del esquema de la versión declarada, no de una lista en el código), no en los textos libres; una `AAAA-MM-DD` debe ser una fecha real (`2026-02-30` y `2026-02-29` son errores `INVALID_DATE`); una `MM-DD` debe existir en algún año (`04-31` y `02-30` son errores) y `02-29` es una advertencia `LEAP_DAY`.
  - **Al terminar T24 a T26:** revisión C y el PR (H3).

## PR B8 · Pruebas *golden*, decimales y el municipio del 20 % (`backend/`, rama `feat/008-b8-golden`)

Con B7 integrado. Son pruebas: no agregan reglas. Si una prueba descubre una regla que no cumple el plan, se corrige la regla con su prueba, en el PR de su bloque o en este.

- [ ] **T27** · `JurisdictionConfigGoldenTest`.
  - **Proyecto:** `backend/` · **PR:** B8
  - **Archivos:** `test/jurisdiction/domain/validation/JurisdictionConfigGoldenTest.java` y, si hace falta, un *fixture* de apoyo en `test/jurisdiction/domain/validation/`; `backend/CHANGELOG.md`. Las copias de los anexos de `src/test/resources/contracts/jsonschema/config-jurisdiccion/1.1/` **no se editan**.
  - **Depende de:** B7 integrado.
  - **Recursos:** las copias de los anexos de `1.1/` (solo lectura).
  - **Prueba primero:** **CA‑5.13 · 008-31**: valida las dos copias de `1.1/` con el contexto del plan («Pruebas *golden*»: administración con DANE 52356 y tipo `MUNICIPIO`; vigencia 2026 del 1 de enero al 31 de diciembre; sin versión publicada ni catálogo). La prueba compara el **conjunto completo** (clase, códigos y ruta) y la lista de revisión con la lista literal del plan, no solo los conteos. Sus conteos, como control:
    - **Ipiales** (*Validada*): 0 errores; **17** advertencias; lista de revisión de **20** (las 19 reglas `POR_CONFIRMAR` y `/tarifas/2/tarifa_maxima`); 2 *no evaluables* (CJ09 y CJ10); CJ11 como primera versión; CJ17 y CJ18 se cumplen;
    - **Plantilla** (*Con errores*): **7** errores (CJ02 ×3 en `/jurisdiccion`, `/calendario` y `/redondeo`; CJ12 en `/competencia/modalidades`; CJ16 en `/cartera/sanciones/sistema_determinacion`; CJ17 en `/jurisdiccion/codigo_dane` y en `/jurisdiccion/tipo`); **21** advertencias (13 valores `PENDIENTE` y 8 reglas); lista de revisión de **17** (16 reglas y la `tarifa_maxima`); **3** *no evaluables* (CJ08 `DANE_PENDING`, CJ09 y CJ10).
- [ ] **T28** · `AnnexCopiesTest`.
  - **Proyecto:** `backend/` · **PR:** B8
  - **Archivos:** `test/jurisdiction/AnnexCopiesTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T27 y T11 (el `.gitattributes`).
  - **Recursos:** las copias de los anexos (solo lectura).
  - **Prueba primero:** **008-32**: el SHA‑256 de los bytes de las copias de `1.1/` es el del Doc. 03 §9.2 (las dos), y el de las de `1.0/` es otro; la prueba lee los bytes tal cual (con LF), de modo que falla en Windows si se pierde el `.gitattributes`. Nada compara los anexos de `docs/funcional/anexos/` con sus copias hasta la feature aparte (decisión 19).
- [ ] **T29** · `ParticipationOnlyTest` y los decimales de punta a punta.
  - **Proyecto:** `backend/` · **PR:** B8
  - **Archivos:** `test/jurisdiction/domain/validation/ParticipationOnlyTest.java`; `test/jurisdiction/domain/ConfigTreeDecimalsTest.java` (se amplía); `backend/CHANGELOG.md`.
  - **Depende de:** T27.
  - **Recursos:** ninguno.
  - **Prueba primero:** `ParticipationOnlyTest` (**008-28**; H2‑6): un archivo mínimo con las seis secciones del esquema y solo `PARTICIPACION_20` activa queda sin errores (no exige las secciones de liquidación) y puede quedar *Validada*; el mismo con `calendario` en `PENDIENTE` da el error de CJ02. **008-30:** una tarifa `0.015` sale del árbol cargado de las copias con escala 3, y `0.7 + 0.2 + 0.1` pasa CJ06 en el validador completo (la lectura desde la versión guardada es T42).
  - **Al terminar T27 a T29:** revisión C y el PR (H3). **Con B8 integrado, C5a puede escribirse.**

---

## Bloque C — Motor, ciclo de vida, API, auditoría y punto 9 (`backend/`)

Revisión de todo el bloque: C, salvo C10 (N).

## PR C1a · Base de datos: tabla, índices, RLS y permisos (`backend/`, rama `feat/008-c1a-tabla`)

*Skill* `liv-migracion`. Con R1 y R2a integrados. Ninguna migración aplicada se modifica. Las marcas de las migraciones las fija la sesión (posteriores a `V202611151000`).

- [ ] **T30** · Esquema `jurisdiction` y `jurisdiction_config`, con sus índices, RLS y permisos.
  - **Proyecto:** `backend/` · **PR:** C1a
  - **Archivos:** `res/db/migration/V<marca>__jurisdiction_configs.sql` (nueva); en `test/isolation/`: `AdministrationIsolationTest.java` y `LivAppGrantsTest.java` (se amplían), `FlywayProfilesTest.java` (la lista de migraciones aplicadas) y `JurisdictionConfigTableTest.java` (nueva); `test/isolation/LivDatabase.java` (solo si hace falta un auxiliar de datos); `backend/CHANGELOG.md`.
  - **Depende de:** R1 y R2a integrados.
  - **Recursos:** migraciones de `backend/` y `FlywayProfilesTest` (solo C1a y C1b).
  - **Prueba primero** (rojo, con PostgreSQL 18 en Testcontainers, antes de la migración; **008-40:** `schema_version` y `validator_version` existen, con su `CHECK` de forma, y `validator_version` es obligatoria):
    - `AdministrationIsolationTest` (**CA‑12 · 008-44** y **008-45**): con el contexto de A no se ven ni se escriben filas de B; sin contexto no se ve nada; A y B publican cada una su 2026; el mismo SHA‑256 en A y en B no choca (**008-09**);
    - `LivAppGrantsTest`: las columnas que `liv_app` puede insertar y actualizar, y que no puede borrar (**008-41**);
    - `JurisdictionConfigTableTest`: los `UNIQUE` parciales (una publicada y una en curso, **008-10** y **008-38**, también con dos inserciones a la vez) y el de `version_number` (**008-26**); los `CHECK` de forma, de estado (también una *Rechazada* con todos los `approved_*`, sin ninguno y con solo una parte, que se rechaza) y de segregación (**008-33**, y `annulled_by = uploaded_by` de **008-36**); las FK de `compared_with_id` y `superseded_by` rechazan una fila de otra vigencia, y las de `rejected_by` y `published_by`, un usuario de otra administración; al insertar, `liv_app` no puede fijar `id` ni `uploaded_at` (42501); al actualizar, no puede cambiar `original_file`, `file_sha256`, `content`, `validation_result`, `version_number` ni `uploaded_*` (42501), ni borrar; la RLS de `INSERT` y de `UPDATE` por estado;
    - `FlywayProfilesTest` cuenta la migración nueva.
- [ ] **T31** · Permisos de acción de la 1.5.9.
  - **Proyecto:** `backend/` · **PR:** C1a
  - **Archivos:** `res/db/migration/V<marca>__platform_jurisdiction_config_permissions.sql` (nueva, con una marca posterior a la de T30); `test/isolation/PlatformTablesTest.java` (`permissionMatrixIsTheApprovedOne`) y `FlywayProfilesTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T30 (orden de las migraciones).
  - **Recursos:** migraciones de `backend/`.
  - **Prueba primero:** `PlatformTablesTest.permissionMatrixIsTheApprovedOne` se amplía con `jurisdiction.config.upload`, `.approve`, `.publish` y `.download` y sus roles (H2‑3 y decisión 6) y sigue siendo la única matriz esperada, igual a la del Doc. 01 v0.7 (R1): asignar `approve` a `CARGADOR_TABLAS` o `upload` a `JURIDICO` la hace fallar. Falla porque la migración todavía no existe; **008-03** y **008-04** dependen de esta matriz.
  - **Al terminar T30 y T31:** revisión C y el PR (H3).

## PR C1b · Base de datos: disparadores (`backend/`, rama `feat/008-c1b-disparadores`)

*Skill* `liv-migracion`. Con C1a integrado.

- [ ] **T32** · Disparador de transiciones y disparador de restricción diferido.
  - **Proyecto:** `backend/` · **PR:** C1b
  - **Archivos:** `res/db/migration/V<marca>__jurisdiction_config_guards.sql` (nueva); `test/isolation/JurisdictionConfigTableTest.java` (se amplía) y `FlywayProfilesTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C1a integrado.
  - **Recursos:** migraciones de `backend/`.
  - **Prueba primero:** en `JurisdictionConfigTableTest` (rojo), **008-41, 008-35 y 008-38**:
    - el disparador rechaza *Validada* → *Publicada*, *Reemplazada* → *Publicada* y reescribir `approved_by` de una *Aprobada*, y conserva los `approved_*` en *Aprobada* → *Rechazada* (**008-35**);
    - *Publicada* → *Reemplazada* apuntando a una *Rechazada* o a una versión menor de la misma vigencia se rechaza con el error del disparador (la prueba comprueba su SQLSTATE y su mensaje: otra vigencia ya la rechaza la FK de C1a y no probaría el disparador);
    - un reemplazo sin publicar la versión que reemplaza falla al confirmar (disparador diferido): `liv_app` no puede «despublicar» una vigencia;
    - `FlywayProfilesTest` cuenta la migración nueva.
  - **Al terminar T32:** revisión C y el PR (H3).

## PR C2a · Contrato: consultas y modelo (`backend/`, rama `feat/008-c2a-contrato-consultas`)

*Skill* `liv-endpoint`, paso 1. Con R1 integrado. Va pronto, para que el *frontend* arranque. Al integrarse, `backend-main` publica `contract-jurisdiction-v1.0.0` (ADR 0027).

- [ ] **T33** · `jurisdiction.yaml` 1.0.0 y su generación.
  - **Proyecto:** `backend/` · **PR:** C2a
  - **Archivos:** `backend/contracts/openapi/jurisdiction.yaml` (nuevo); `backend/pom.xml` (solo una ejecución más de `openapi-generator`, `contract-jurisdiction`, con `typeMappings` `file=org.springframework.core.io.InputStreamResource`, sin dependencias nuevas); `test/contracts/OpenApiContractTest.java`; `backend/README.md` (la tabla de contratos) y `backend/CHANGELOG.md`.
  - **Depende de:** R1 integrado (los códigos salen de los documentos v0.6 y v0.7).
  - **Recursos:** contrato `jurisdiction` (nuevo); `backend/pom.xml`.
  - **Prueba primero:** `OpenApiContractTest` (rojo) comprueba el contrato del plan («Datos, API y seguridad»):
    - las operaciones de consulta y descarga: `listJurisdictionConfigs` (con `uploadLimits`: `maxFileBytes` y `maxRequestBytes`, `canUpload` y `uploadableYears`), `listFiscalYearVersions`, `getJurisdictionConfigVersion` (con `validation`, `comparison`, `approval` y `availableActions`) y `downloadOriginal` (200 `application/octet-stream`, `attachment`; **008-04**), con sus respuestas y errores (400, 401, 403, 404 `SYS-404`);
    - `year` entero entre 1999 y 2100; `id`, UUID; `before` y `after` como texto; `path` como JSON Pointer;
    - los `enum` de respuesta **completos** (estados, acciones y códigos), para que `openapi-diff` no marque como incompatible la versión 1.1.0;
    - el catálogo de motivos (H2‑22) en la descripción de `reason`, y las palabras clave de CJ01 en `params.keyword`: una prueba recorre los esquemas 1.0 y 1.1 y falla si alguna palabra clave del esquema no está documentada (la parte de la pantalla es T69; véase «Puntos del plan que estas tareas interpretan», 5);
    - las extensiones del Problem Details son escalares (ADR 0035); seguridad `bearerAuth` bajo `/api/v1/`;
    - el modelo generado traduce el binario a `InputStreamResource`.
  - **Verde:** el contrato; `sh contracts/scripts/compare.sh origin/main` lo juzga como contrato nuevo.
  - **Al terminar T33:** revisión C y el PR (H3). **La persona integra y espera la *release* antes de C2b.**

## PR C2b · Contrato: acciones y `tax.yaml` 1.2.0 (`backend/`, rama `feat/008-c2b-contrato-acciones`)

*Skill* `liv-endpoint`, paso 1. **Solo con C2a integrado y la *release* `contract-jurisdiction-v1.0.0` ya publicada**: si las dos corridas se integran seguidas y se cancela la de C2a, 1.0.0 no se publica (ADR 0027, «Concurrencia en `main`»).

- [ ] **T34** · `jurisdiction.yaml` 1.1.0 y `tax.yaml` 1.2.0.
  - **Proyecto:** `backend/` · **PR:** C2b
  - **Archivos:** `backend/contracts/openapi/jurisdiction.yaml`, `backend/contracts/openapi/tax.yaml`, `test/contracts/OpenApiContractTest.java`, `backend/README.md` (la tabla de contratos) y `backend/CHANGELOG.md`.
  - **Depende de:** C2a integrado y su *release* publicada.
  - **Recursos:** contratos `jurisdiction` y `tax`.
  - **Prueba primero:** `OpenApiContractTest` (rojo):
    - **1.1.0 de `jurisdiction`:** las operaciones `uploadJurisdictionConfig` (`POST …/{year}/versions`, cuerpo `application/octet-stream`; 201 con el detalle; errores 400, 401, 403, 404 `SYS-404`, 408 `SYS-408`, 409 `CG-03`, `CG-04`, `CJC-01` y `V-04`, 413 `CG-06` `TAMANO`, 415 y 422 `CG-06`), `approveJurisdictionConfig` (con `reviewedPaths`, `warningsAccepted` y `validationDigest`; 422 `CG-01`, 409 `CG-02`, 422 `CJC-02` con `missingCount`, `warningsAccepted` y `digestMatches`, 409 `CJC-03`), `rejectJurisdictionConfig` y `annulJurisdictionConfig` (`reason` de 1 a 500 caracteres sin caracteres de control, por patrón; 422 `CG-05`) y `publishJurisdictionConfig` (409 `CJC-04` con `reason`); cada una con una etiqueta propia para que C2b compile; es compatible con 1.0.0: solo agrega rutas y esquemas de petición;
    - **`tax.yaml` 1.2.0:** el motivo `JURISDICTION_CONFIG_NOT_PUBLISHED` en la descripción de `reason` del punto de preparación (**008-43**); compatible;
    - `sh contracts/scripts/compare.sh origin/main` no da cambios incompatibles en ninguno.
  - **Al terminar T34:** revisión C y el PR (H3). La CI publica `contract-jurisdiction-v1.1.0` y `contract-tax-v1.2.0`; **F0 espera esas *releases*.**

## PR C3 · Piezas comunes del motor de ingesta (`backend/`, rama `feat/008-c3-motor-ingesta`)

Con R2b integrado (ADR 0038). Código en `main/platform/ingestion/` con `@NamedInterface("ingestion")`, sin dependencia de `jurisdiction`. Corte previsto: C3a = T35 y T37, C3b = T36.

- [ ] **T35** · Recepción acotada: `IngestionLimits`, `FileReceiver` y `PayloadTooLarge`.
  - **Proyecto:** `backend/` · **PR:** C3
  - **Archivos:**
    - `main/platform/ingestion/IngestionLimits.java`, `FileReceiver.java`, `ReceivedFile.java`, `Oversize.java`, `PayloadTooLarge.java` y `package-info.java` (con la interfaz con nombre);
    - `main/platform/ErrorCodes.java` (`SYS-408`, y `SYS-400` si falta) y `main/platform/web/ProblemDetailsHandler.java` (413 con `Connection: close`, 408 y 400 como Problem Details);
    - `res/application.yml` (`server.tomcat.max-swallow-size` de 64 KiB, `server.tomcat.connection-timeout` de 20 s y `server.tomcat.keep-alive-timeout` de 60 s; el `connection-timeout: 5000` que ya existe es de otra sección y no se toca);
    - `test/platform/ingestion/FileReceiverTest.java`, `test/platform/ErrorResponsesTest.java` y `test/platform/PlatformWebTest.java` (una sonda de cuerpo para la prueba por el servidor real);
    - `test/architecture/ModularityTest.java` y `ArchitectureRulesTest.java` (la interfaz con nombre); `backend/CHANGELOG.md`.
  - **Depende de:** R2b integrado.
  - **Recursos:** `ProblemDetailsHandler`, `ErrorCodes`, `application.yml`, `ModularityTest`.
  - **Prueba primero:** `FileReceiverTest` (**008-05, 008-07**, enmienda 2):
    - el SHA‑256 de un archivo conocido, calculado al leer;
    - `Oversize` en 1 MiB + 1 byte y en el tope exacto (1.114.112), con su SHA‑256; `PayloadTooLarge` en el tope + 1 byte, sin leer más allá (cada lectura pide como máximo `min(búfer, tope + 1 − leídos)`);
    - un cliente que envía un byte por intervalo corta por el plazo total de 25 s con 408 `SYS-408`, sin tomar una conexión a la base; un corte a mitad de la lectura da 400 `SYS-400` con `WARN` y sin 500;
    - por el servidor real (`RANDOM_PORT`), el cuerpo Problem Details `CG-06` `TAMANO` (413, `Connection: close`), sin `Content-Length` y con un cuerpo en trozos, y que un usuario sin permiso recibe 403 sin que se lea el cuerpo;
    - `ModularityTest`: la interfaz con nombre `ingestion` es visible para los demás módulos.
- [ ] **T36** · Ciclo de vida y coordinador: `IngestionStatus`, `IngestionLifecycle`, `IngestionEngine` y sus interfaces.
  - **Proyecto:** `backend/` · **PR:** C3
  - **Archivos:** `main/platform/ingestion/IngestionStatus.java`, `IngestionLifecycle.java`, `IngestionEngine.java`, `IngestionReader.java`, `IngestionRules.java`, `VersionStore.java`, `IngestionPublisher.java` y `IngestionLock.java`; `test/platform/ingestion/IngestionLifecycleTest.java`, `IngestionEngineTest.java` y `IngestionLockTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T35 y T37 (la interfaz `RejectedAttemptLog`).
  - **Recursos:** ninguno.
  - **Prueba primero:**
    - `IngestionLifecycleTest` (**008-33, 008-35, 008-36, 008-37**): la tabla completa de transiciones permitidas y prohibidas entre los ocho estados; `require(from, action)` da `CG-02` con el estado actual; `requireDifferentActor` da `CG-01` y `requireSameActor` lo da para la anulación;
    - `IngestionEngineTest`, con dobles de las cuatro interfaces: `upload` recibe y lee **fuera** de la transacción y solo después abre la suya (`TransactionTemplate`, `READ_COMMITTED`); `transition` lee la vigencia de la fila, toma el bloqueo y **vuelve a leer** la fila bajo el bloqueo; un rechazo auditable (`CG-01`, o `CG-06` dentro del tope) cierra la transacción y después se registra; cada intento da exactamente un evento; `PayloadTooLarge`, ninguno; ninguna interfaz queda sin llamada;
    - `IngestionLockTest` (**008-10, 008-38**: una carga y una publicación de la misma vigencia no se cruzan): el bloqueo es `pg_advisory_xact_lock(espacio, hashtext(administrationId || ':' || year))` y su espacio es distinto del de la creación de vigencias de la 007 (`AdministrationFiscalYearRepository`).
- [ ] **T37** · `RejectedAttemptLog` y el evento `IngestionAttemptRejected`.
  - **Proyecto:** `backend/` · **PR:** C3
  - **Archivos:** `main/platform/ingestion/RejectedAttemptLog.java` (interfaz) e `IngestionAttemptRejected.java` (evento); `main/platform/infrastructure/` (la implementación, con su propia transacción); `test/platform/ingestion/RejectedAttemptLogTest.java`; `test/architecture/ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T35.
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** `RejectedAttemptLogTest` (**008-47**; H2‑10): deja su evento en el registro de eventos de Spring Modulith con entidad, `id` si lo hay, vigencia, código, motivo, SHA‑256 si lo hay, usuario, administración, instante, `eventId` y `correlationId`; **falla si se llama dentro de una transacción activa**; si su propia transacción falla, la respuesta no cambia y el fallo queda con nivel `ERROR` y una métrica. La fila de `audit.audit_event` se prueba en C7 (T49).
  - **Al terminar T35 a T37:** revisión C y el PR (H3).

## PR C4 · Dominio del ciclo de vida (`backend/`, rama `feat/008-c4-dominio-ciclo`)

Con B8 y C3 integrados. Java puro en `main/jurisdiction/domain/`: **no usa `platform`** (regla de T11). El agregado protege sus invariantes él mismo (H2‑24).

- [ ] **T38** · `JurisdictionConfigVersion`, el agregado.
  - **Proyecto:** `backend/` · **PR:** C4
  - **Archivos:** `main/jurisdiction/domain/JurisdictionConfigVersion.java`, `JurisdictionConfigStatus.java` y los tipos pequeños que necesite (`ApprovalMarks`, `PublicationChecks`); `test/jurisdiction/domain/JurisdictionConfigVersionTest.java`; `test/architecture/ModularityTest.java` (el agregado usa `shared.RuleViolation`: nace la flecha `jurisdiction → shared`, y la prueba «solo `platform` y `tax` dependen de `shared`» pasa a incluir `jurisdiction` en este PR, no en C5a); `backend/CHANGELOG.md`.
  - **Depende de:** C3 integrado (orden del backend; no usa su código).
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** `JurisdictionConfigVersionTest`, sin el coordinador:
    - **CA‑05 · 008-33** (H2‑24): el actor no es el cargador para aprobar, rechazar y publicar, y es el cargador para anular (`CG-01`, con `shared.RuleViolation`); el estado de origen da `CG-02`;
    - **CJ02 · 008-34:** `approve(actor, marks, warningsAccepted, shownDigest, currentValidatorVersion)` exige que las marcas sean exactamente la lista de revisión y que las advertencias estén aceptadas (`CJC-02` con `missingCount`; H2‑21), que `shownDigest` sea el de la versión (`CJC-02`) y que el resultado sea de la versión actual del validador (`CJC-03`);
    - **008-35 y 008-36:** `reject` y `annul` con motivo no vacío (`CG-05`); el rechazo de una *Aprobada* conserva quién la aprobó;
    - **008-39:** `publicationChecks(...)` da `CJC-04` con su motivo (`FILE_CHANGED`, `RESULT_CHANGED`, `VALIDATOR_CHANGED`, `PUBLISHED_CHANGED`, `FISCAL_YEAR_NOT_IN_PREPARATION` o `REVALIDATION_DIFFERS`), probado con datos alterados: son una defensa en profundidad (en la aplicación solo puede ocurrir `VALIDATOR_CHANGED`).
- [ ] **T39** · `UploadPolicy`.
  - **Proyecto:** `backend/` · **PR:** C4
  - **Archivos:** `main/jurisdiction/domain/UploadPolicy.java` y los tipos pequeños que necesite; `test/jurisdiction/domain/UploadPolicyTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T38.
  - **Recursos:** ninguno.
  - **Prueba primero:** `UploadPolicyTest`, en este orden: **V‑04 · 008-06** la vigencia existe (si no, `shared.NotFound`, 404 `SYS-404`) y está *En preparación* (`CJC-01`; una *Cerrada* da `V-04`); **008-09** el SHA‑256 no coincide con la publicada ni con la en curso (`CG-03`, con la versión que coincide; el mismo archivo en otra administración no choca); **008-10** no hay otra en curso (`CG-04`; una *Con errores* no bloquea); da el número que le toca (la publicada más uno, o 1; **008-26**).
  - **Al terminar T38 y T39:** revisión C y el PR (H3).

## PR C5a · Carga: persistencia, puertos y caso de uso (`backend/`, rama `feat/008-c5a-carga`)

*Skill* `liv-endpoint`. Con B8, C1b, C3 y C4 integrados. Se mide: si pasa de 400 líneas, se corta por tareas.

- [ ] **T40** · Puertos de lectura y el territorio de la administración.
  - **Proyecto:** `backend/` · **PR:** C5a
  - **Archivos:** `main/jurisdiction/FiscalYearDirectory.java` y su tipo de datos (paquete raíz de `jurisdiction`); `main/core/AdministrationTerritory.java` (paquete raíz de `core`) y su implementación en `main/core/`; `main/tax/infrastructure/JurisdictionFiscalYearDirectory.java`; `test/tax/infrastructure/JurisdictionFiscalYearDirectoryTest.java` y `test/core/AdministrationTerritoryTest.java`; `test/architecture/ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C4 integrado.
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** `JurisdictionFiscalYearDirectoryTest` (**008-06, 008-44**): lee la vigencia de la administración, con su estado y sus fechas, bajo el RLS (una vigencia de otra administración no se ve); `AdministrationTerritoryTest`: el DANE y el tipo de la entidad territorial de la administración; `ModularityTest`: `jurisdiction` → {`core`, `platform`, `shared`} y `tax` → {`jurisdiction`, `platform`, `shared`} (la parte de `shared` ya entró con T38).
- [ ] **T41** · Persistencia JPA: la entidad, el repositorio y el adaptador de `VersionStore`.
  - **Proyecto:** `backend/` · **PR:** C5a
  - **Archivos:** `main/jurisdiction/infrastructure/persistence/` (la entidad, los repositorios y el adaptador); `test/jurisdiction/infrastructure/persistence/JurisdictionPersistenceTestBase.java`, `JurisdictionConfigsPersistenceTest.java` y `JurisdictionConfigsBeansTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T40.
  - **Recursos:** ninguno.
  - **Prueba primero:** con Testcontainers y el adaptador real: todas las columnas `updatable = false` y `id` y `uploaded_at` con `insertable = false` y `@Generated`; las transiciones con `UPDATE … WHERE id = ? AND status = ?` explícitos que devuelven las filas afectadas, y un contador de sentencias de Hibernate comprueba que no emite ningún `UPDATE` propio; **008-30:** una `0.015` guardada en `content` y `validation_result` se lee como `0.015` con escala 3; las listas (**008-01, 008-02**) con consultas de proyección, sin `original_file`, `content` ni `validation_result`; **008-41:** el adaptador no puede cambiar el archivo ni su SHA‑256.
- [ ] **T42** · `UploadJurisdictionConfig` y los adaptadores del motor.
  - **Proyecto:** `backend/` · **PR:** C5a
  - **Archivos:** `main/jurisdiction/application/UploadJurisdictionConfig.java` (`@RequiresPermission("jurisdiction.config.upload")`, sin `@Transactional`); `main/jurisdiction/infrastructure/` (los adaptadores de `IngestionReader` con el cargador seguro y de `IngestionRules` con el validador); `main/jurisdiction/JurisdictionConfigUploaded.java` y `JurisdictionConfigValidated.java` (paquete raíz); `main/jurisdiction/infrastructure/yaml/SafeYamlLoader.java` (la constante pasa a `IngestionLimits.MAX_FILE_BYTES`); `test/jurisdiction/application/UploadJurisdictionConfigTest.java` y `UseCasePermissionsTest.java`; `test/jurisdiction/infrastructure/UploadJurisdictionConfigAdapterTest.java`; `test/architecture/AuthorizationRulesTest.java` si lo exige; `backend/CHANGELOG.md`.
  - **Depende de:** T41.
  - **Recursos:** ninguno.
  - **Prueba primero:** `UploadJurisdictionConfigTest` y `UploadJurisdictionConfigAdapterTest` (Testcontainers):
    - **008-05, 008-29:** el archivo se guarda con su SHA‑256 y se valida en una sola operación; queda *Con errores* o *Validada*, nunca *Cargada*; un fallo de la base después de validar no deja ninguna fila ni evento;
    - **008-07:** un rechazo del cargador (`CG-06` con el motivo y el SHA‑256) no guarda nada y publica su `IngestionAttemptRejected` en el registro de eventos; uno entre 1 MiB y el tope también; `PayloadTooLarge` no;
    - **008-09:** el mismo archivo se carga en A y en B; **008-10:** dos cargas a la vez de la misma vigencia dejan una sola en curso y la otra da `CG-04` (el `23505` del índice se traduce, nunca un 500); una carga mientras otra versión se publica espera el bloqueo y compara con la nueva publicada;
    - **008-30:** la versión guardada se lee de vuelta con los decimales exactos; **008-41:** el contenido no cambia;
    - una prueba ata `SafeYamlLoader` a `IngestionLimits.MAX_FILE_BYTES` (una sola constante);
    - la fila de `audit.audit_event` se prueba en C7.
  - **Al terminar T40 a T42:** revisión C y el PR (H3).

## PR C5b · API de consulta, carga y descarga (`backend/`, rama `feat/008-c5b-api-consulta`)

*Skill* `liv-endpoint`. Con C2b y C5a integrados. El controlador implementa la interfaz generada, sin lógica (CC‑07); si pasa de 200 líneas, se parte en dos (consulta y acciones).

- [ ] **T43** · Los casos de uso de consulta y descarga.
  - **Proyecto:** `backend/` · **PR:** C5b
  - **Archivos:** `main/jurisdiction/application/ListJurisdictionConfigs.java`, `ListFiscalYearVersions.java`, `GetJurisdictionConfigVersion.java` y `DownloadOriginal.java`; `main/jurisdiction/JurisdictionConfigOriginalDownloaded.java` (paquete raíz); `test/jurisdiction/application/ListJurisdictionConfigsTest.java`, `ListFiscalYearVersionsTest.java`, `GetJurisdictionConfigVersionTest.java`, `DownloadOriginalTest.java` y `UseCasePermissionsTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C5a integrado.
  - **Recursos:** ninguno.
  - **Prueba primero:** **008-01:** la lista de vigencias con su publicada y su en curso, de la más reciente a la más antigua, `canUpload` y `uploadableYears` (las *En preparación* sin versión en curso); **008-02:** el historial con todos los datos pedidos y el detalle con la validación, la comparación y `availableActions` (según el estado, el permiso y si el usuario es el cargador); **008-04:** `DownloadOriginal` devuelve los bytes guardados con el mismo SHA‑256, en una transacción de lectura y escritura (no `readOnly`) porque publica `JurisdictionConfigOriginalDownloaded` por el registro de eventos (**008-46**); **008-44:** una versión de otra administración da el mismo `shared.NotFound`; cada caso de uso lleva su permiso (`UseCasePermissionsTest`).
- [ ] **T44** · El controlador, la descarga y las pruebas de la API.
  - **Proyecto:** `backend/` · **PR:** C5b
  - **Archivos:** `main/jurisdiction/api/JurisdictionConfigsController.java` (y un segundo controlador si pasa de 200 líneas) y su traductor de modelos; `test/jurisdiction/JurisdictionConfigsApiTest.java` (`extends PlatformWebTest`), `UploadSizeLimitsTest.java` y `JurisdictionConfigsContractParityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T43.
  - **Recursos:** contrato `jurisdiction`.
  - **Prueba primero:** `JurisdictionConfigsApiTest`, antes del controlador y fallando por una aserción:
    - las respuestas y errores del contrato con su estado HTTP (H2‑4): `TAMANO` siempre 413, los demás motivos del cargador 422;
    - **008-03 y 008-04:** 401 y 403 por rol (`CONSULTA` y `LIQUIDADOR` sin lectura; `ADMIN_SISTEMA` lee pero no descarga);
    - **V‑04 · 008-06**, **008-44** (la versión ajena da el 404 de la inexistente);
    - **008-04:** el SHA‑256 de la descarga es el de la carga, con `Content-Type: application/octet-stream`, `Content-Disposition: attachment; filename="config_jurisdiccion_<DANE>_<AÑO>_v<N>.yaml"` armado por el servidor, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` y `Content-Length` explícito; una petición con `Range` da 416 y la respuesta no anuncia `Accept-Ranges`;
    - **008-01 y 008-02:** la lista, el historial y el detalle por la API con sus campos, de la más reciente a la más antigua;
    - **008-08:** `Content-Type: application/yaml` da 415;
    - `UploadSizeLimitsTest` (**008-03, 008-05, 008-07**), por el servidor real (`RANDOM_PORT`) y contra el controlador de `jurisdiction`, con un cliente de *socket* que envía despacio y lee la respuesta mientras envía: un `CONSULTA` recibe 403 antes de terminar de enviar; un cargador recibe 413 `CG-06` `TAMANO` al pasar el tope + 1 byte; la aplicación lee como máximo el tope + 1 byte; el servidor cierra la conexión después del 403, el 408 o el 413 (`max-swallow-size`); la interfaz generada recibe un `InputStreamResource`;
    - `JurisdictionConfigsContractParityTest`: los estados y las acciones del contrato son los del dominio.
  - **Al terminar T43 y T44:** revisión C y el PR (H3).

## PR C6a · Aprobar, rechazar y anular (`backend/`, rama `feat/008-c6a-aprobar-rechazar-anular`)

*Skill* `liv-endpoint`. Con C5b integrado. Cada caso de uso va por `IngestionEngine.transition`: lee la vigencia de la fila, toma el bloqueo, vuelve a leer la fila, aplica `IngestionLifecycle` y el agregado, y hace el `UPDATE` condicionado al estado leído; con 0 filas, vuelve a leer y da `CG-02` (**008-37**).

- [ ] **T45** · Aprobar.
  - **Proyecto:** `backend/` · **PR:** C6a
  - **Archivos:** `main/jurisdiction/application/ApproveJurisdictionConfig.java` (`jurisdiction.config.approve`); `main/jurisdiction/JurisdictionConfigApproved.java`; `main/jurisdiction/api/JurisdictionConfigActionsController.java` (el método de aprobar); `test/jurisdiction/application/ApproveJurisdictionConfigTest.java`, `UseCasePermissionsTest.java` y `JurisdictionConfigActionsApiTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C5b integrado.
  - **Recursos:** contrato `jurisdiction`.
  - **Prueba primero:** **CA‑05 · 008-33:** el cargador, aunque tenga los dos roles, no puede aprobar, y su intento queda auditado (`RejectedAttemptLog`; el evento, no la fila: es C7); **CJ02 · 008-34:** faltan marcas, o falta la aceptación de las advertencias, o el `validationDigest` enviado no es el de la versión (`CJC-02`, con `missingCount`, `warningsAccepted` y `digestMatches`), y un resultado de otra versión del validador (con un validador de prueba de otra versión) da `CJC-03`; al aprobar queda *Aprobada* con el aprobador, el instante, las marcas, el SHA‑256 y el digest que revisó; **008-37:** una aprobación que pierde la carrera con un rechazo da `CG-02`; aprobar una *Con errores* da `CG-02`; **008-47:** el intento del cargador rechazado por segregación publica su `IngestionAttemptRejected`.
- [ ] **T46** · Rechazar y anular.
  - **Proyecto:** `backend/` · **PR:** C6a
  - **Archivos:** `main/jurisdiction/application/RejectJurisdictionConfig.java` (`jurisdiction.config.approve`) y `AnnulJurisdictionConfig.java` (`jurisdiction.config.upload`); `main/jurisdiction/JurisdictionConfigRejected.java` y `JurisdictionConfigAnnulled.java`; `main/jurisdiction/api/JurisdictionConfigActionsController.java`; `test/jurisdiction/application/RejectJurisdictionConfigTest.java`, `AnnulJurisdictionConfigTest.java`, `UseCasePermissionsTest.java` y `JurisdictionConfigActionsApiTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T45.
  - **Recursos:** contrato `jurisdiction`.
  - **Prueba primero:** `RejectJurisdictionConfigTest` (**008-35, 008-02**): un aprobador distinto del cargador rechaza una *Validada* o una *Aprobada* con motivo (sin motivo, `CG-05`); el rechazo de una *Aprobada* conserva quién la aprobó; el cargador no puede rechazar (`CG-01`, auditado: **008-33**). `AnnulJurisdictionConfigTest` (**008-36**): solo el cargador anula la suya *Validada*; otra persona recibe `CG-01` (`ONLY_UPLOADER`); una *Aprobada*, *Publicada* o *Reemplazada* no se anula (`CG-02`); sin motivo, `CG-05`.
  - **Al terminar T45 y T46:** revisión C y el PR (H3).

## PR C6b · Publicar (`backend/`, rama `feat/008-c6b-publicar`)

*Skill* `liv-endpoint`. Con C6a integrado.

- [ ] **T47** · `PublishJurisdictionConfig`, `PublishedJurisdictionConfigs` y los eventos de la publicación.
  - **Proyecto:** `backend/` · **PR:** C6b
  - **Archivos:** `main/jurisdiction/application/PublishJurisdictionConfig.java` (`jurisdiction.config.publish`); el adaptador de `IngestionPublisher` en `main/jurisdiction/infrastructure/`; `main/jurisdiction/PublishedJurisdictionConfigs.java` (la fachada, paquete raíz), `JurisdictionConfigPublished.java` y `JurisdictionConfigSuperseded.java`; `main/jurisdiction/api/JurisdictionConfigActionsController.java`; `test/jurisdiction/application/PublishJurisdictionConfigTest.java`, `PublishedJurisdictionConfigsTest.java`, `UseCasePermissionsTest.java` y `JurisdictionConfigActionsApiTest.java`; `test/jurisdiction/infrastructure/PublishJurisdictionConfigAdapterTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C6a integrado.
  - **Recursos:** contrato `jurisdiction`.
  - **Prueba primero:** `PublishJurisdictionConfigAdapterTest` (Testcontainers), en una sola transacción con el bloqueo de la vigencia:
    - **008-38:** la versión queda *Publicada* y la anterior *Reemplazada* con `superseded_by` (en ese orden, para no chocar con el índice único); dos publicaciones a la vez dejan una sola *Publicada*; se emiten `JurisdictionConfigPublished` (administración, vigencia, versión y versión reemplazada) y, si hubo reemplazo, `JurisdictionConfigSuperseded`, cada uno con su `eventId`; un fallo después del reemplazo no deja nada ni emite eventos y la versión sigue *Aprobada*;
    - **CA‑05 · 008-33:** el cargador no puede publicar, y su intento queda auditado;
    - **008-46, 008-47:** la publicación emite sus eventos y el intento del cargador queda registrado;
    - **008-39:** un resultado de otra versión del validador da `CJC-04` `VALIDATOR_CHANGED`, con un validador de prueba de otra versión; el resto de los motivos, alterando las filas con el dueño de los objetos: otra fila puesta en *Publicada* (`PUBLISHED_CHANGED`), la vigencia puesta en *Lista* (`FISCAL_YEAR_NOT_IN_PREPARATION`), y el original, el `digest` o el resultado cambiados (`FILE_CHANGED`, `RESULT_CHANGED`, `REVALIDATION_DIFFERS`); la versión sigue *Aprobada* y se puede rechazar (008-35); la fila de la vigencia no se bloquea;
    - **008-40:** la publicada guarda `schema_version` y `validator_version`; `PublishedJurisdictionConfigsTest` (**008-42**): el `id` de la publicada por administración y vigencia, y ninguna otra tabla se llena;
    - una prueba de tiempo con el archivo más grande que acepta el cargador (si pasa de un segundo, se avisa antes de la H3).
  - **Al terminar T47:** revisión C y el PR (H3).

## PR C7 · Auditoría (`backend/`, rama `feat/008-c7-auditoria`)

Con C6b integrado.

- [ ] **T48** · Los oyentes de los eventos de `jurisdiction` y la auditoría.
  - **Proyecto:** `backend/` · **PR:** C7
  - **Archivos:** `main/audit/infrastructure/JurisdictionConfigEventsListener.java`; `main/audit/application/RecordJurisdictionConfigEvents.java` (casos de uso abiertos, `@OpenUseCase`) y `main/audit/application/AuditLog.java` (solo el *javadoc* de `entityName`); `test/audit/JurisdictionConfigAuditTest.java` y `AuditRows.java`; `test/architecture/AuthorizationRules.java` (**`OPEN_USE_CASES`**), `OpenUseCaseCallersTest.java` y `ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C6b integrado.
  - **Recursos:** `AuditLog`, `AuthorizationRules` y `ModularityTest` (solo C7 en esta serie).
  - **Prueba primero:** `JurisdictionConfigAuditTest` (**008-46**), cada operación con sus valores: `entity_name` `jurisdiction.jurisdiction_config`, `entity_id` el `id` de la versión, `operation` (`JURISDICTION_CONFIG_UPLOADED`, `_VALIDATED`, `_APPROVED`, `_REJECTED`, `_ANNULLED`, `_PUBLISHED`, `_SUPERSEDED` y `_ORIGINAL_DOWNLOADED`), `previous_values` `{status}`, `new_values` con la vigencia, la versión, el estado nuevo y lo propio de cada operación (totales de hallazgos por clase y por código; número de marcas y `validation_digest`; el motivo; la versión reemplazada), y `user_id`, `administration_id`, `occurred_at` y `correlation_id` los del evento; la publicación con reemplazo da dos filas, una por evento; idempotente por `eventId`; `ModularityTest`: `audit` → {`core`, `platform`, `tax`, `jurisdiction`} y nadie depende de `audit`; `AuthorizationRulesTest` y `OpenUseCaseCallersTest`: los métodos abiertos nuevos entran en la lista cerrada, y solo su oyente depende de ellos.
- [ ] **T49** · Los intentos rechazados y el peor caso de `new_values`.
  - **Proyecto:** `backend/` · **PR:** C7
  - **Archivos:** `main/audit/infrastructure/IngestionEventsListener.java` y el método de `RecordJurisdictionConfigEvents.java`; `test/audit/JurisdictionConfigAuditTest.java` y `IngestionAttemptAuditTest.java`; `test/architecture/AuthorizationRules.java` y `OpenUseCaseCallersTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T48.
  - **Recursos:** `AuthorizationRules`.
  - **Prueba primero:** **008-47** (enmienda 2): un intento por segregación (`CG-01`), uno del cargador seguro y uno con `TAMANO` entre 1 MiB y el tope quedan en `audit.audit_event` como `INGESTION_ATTEMPT_REJECTED` (código, motivo y SHA‑256, sin `entity_id` si no hay versión) sin que la operación cambie nada; **un cuerpo por encima del tope no deja ninguna fila**; **008-46:** el peor caso de `new_values` (todos los códigos de CJ01 a CJ18 en los totales y un motivo de 500 caracteres de comillas y barras, que el JSON escapa al doble) cabe en los 4096 caracteres del `CHECK`; si no cabe, se recorta el motivo en la lista blanca.
  - **Al terminar T48 y T49:** revisión C y el PR (H3).

## PR C8 · Punto 9 de la lista de preparación (`backend/`, rama `feat/008-c8-punto-9`)

Con C6b y C2b integrados.

- [ ] **T50** · `JurisdictionConfigPreparationCheck`.
  - **Proyecto:** `backend/` · **PR:** C8
  - **Archivos:** `main/tax/infrastructure/JurisdictionConfigPreparationCheck.java`; `main/tax/domain/PreparationChecklist.java` y `PreparationCheckResult.java` solo si el compilador o la prueba lo exigen; `test/tax/infrastructure/JurisdictionConfigPreparationCheckTest.java`, `test/tax/application/GetFiscalYearTest.java`, `test/tax/domain/PreparationChecklistTest.java`, `test/tax/FiscalYearsApiTest.java` y `test/contracts/OpenApiContractTest.java` (si alguna aserción de los motivos cambia); `test/architecture/ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C6b y C2b integrados.
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** `JurisdictionConfigPreparationCheckTest` y `GetFiscalYearTest` (**Doc. 01 §6.1 · 008-43**): el punto 9 está *cumplido* con una versión *Publicada* y *pendiente* con `JURISDICTION_CONFIG_NOT_PUBLISHED` si no (ya no `OPTION_NOT_AVAILABLE`); se lee bajo el RLS de la administración y un fallo de la lectura falla la lista entera, como pide el contrato de `PreparationItemCheck`; los demás puntos no cambian (007-11) y «Habilitar liquidación» sigue inhabilitado (V‑03 · CA‑14); `FiscalYearsApiTest`: el motivo llega por la API.
  - **Al terminar T50:** revisión C y el PR (H3).

## PR C9 · BFF: tope del cuerpo y `bff.yaml` 1.5.0 (`backend/bff/`, rama `feat/008-c9-bff-tope`)

Con R2b integrado (ADR 0038). **No depende de `jurisdiction`.** Es un proyecto Maven aparte: sus reglas están en `backend/bff/AGENTS.md`; cada cambio lleva la revisión C (todo el BFF es ruta crítica). Un paquete nuevo se agrega al catálogo del BFF (`PackageCatalogTest`); el filtro va en `bff/web`, que ya existe. Verificación: `./mvnw verify` dentro de `backend/bff/`.

- [ ] **T51** · `bff.yaml` 1.5.0.
  - **Proyecto:** `backend/bff/` · **PR:** C9
  - **Archivos:** `backend/bff/contracts/openapi/bff.yaml` (de 1.4.0 a 1.5.0); `bff-test/contracts/BffContractTest.java`; `backend/bff/CHANGELOG.md`.
  - **Depende de:** R2b integrado.
  - **Recursos:** contrato `bff`.
  - **Prueba primero:** `BffContractTest` (rojo): el 413 de `/api/**` es un Problem Details con `code: CG-06`, `reason: TAMANO` y la extensión `rejectedBy: bff`, con el tope en su descripción; el 408 es un Problem Details con `code: SYS-408`; el 413 de la API lleva además `fileSha256`, opcional; `sh contracts/scripts/compare.sh` no da cambios incompatibles. La *release* `contract-bff-v1.5.0` la publica la CI al integrar.
- [ ] **T52** · El filtro del tope del cuerpo.
  - **Proyecto:** `backend/bff/` · **PR:** C9
  - **Archivos:** `bff/web/` (un filtro de *servlet* nuevo, registrado después de `springSecurityFilterChain`, y su registro en `SecurityConfiguration.java` si hace falta); `bff-test/web/ApiProxyTest.java`, `bff-test/support/FakeBackend.java` y `BffFlowTest.java` (si hace falta ampliar la API falsa); `bff-test/architecture/PackageCatalogTest.java` y `CodeSizeGuardTest.java` (solo si fallan); `backend/bff/CHANGELOG.md`.
  - **Depende de:** T51.
  - **Recursos:** `SecurityConfiguration` y `ApiProxyTest` (solo C9).
  - **Prueba primero:** en `ApiProxyTest`, con la API falsa y `RANDOM_PORT` (**008-03** enmienda 2, **008-05, 008-07, 008-09**): cuerpos de 1 MiB + 1 byte (lo reenvía y la API falsa responde su 413), del tope + 1 byte (el literal 1.114.112 + 1), de 3 MiB y de 5 MiB (el BFF no reenvía): el navegador recibe 413 `TAMANO` con `rejectedBy: bff`, nunca 502, y la API falsa no recibe nada con `Content-Length` por encima del tope; un cuerpo en trozos (sin `Content-Length`) de 3 MiB recibe 413 y la API falsa no recibe nada; uno en trozos de 1 MiB llega entero; el cuerpo `application/octet-stream` llega a la API **byte por byte**, con y sin `Content-Length`: el SHA‑256 de lo que recibe es el del archivo enviado, con CRLF y con bytes no ASCII; una petición sin sesión recibe su 401 antes del filtro; un usuario sin permiso que envía más del tope recibe el 413 y no un 403 (enmienda 2); el tope vale para todo `/api/**`, sin lista de rutas; queda en el registro técnico con nivel `WARN`, sin auditoría.
- [ ] **T53** · Plazo de lectura, `max-swallow-size` y el reenvío de la descarga.
  - **Proyecto:** `backend/bff/` · **PR:** C9
  - **Archivos:** `bff/web/` (el plazo en el filtro de T52); `backend/bff/src/main/resources/application.yml` (`server.tomcat.max-swallow-size` de 64 KiB y `server.tomcat.connection-timeout` de 20 s; el `connection-timeout: 5000` que ya existe es de otra sección y no se toca); `bff-test/web/SlowApiForwardingTest.java` y `ApiProxyTest.java`; `backend/bff/README.md` (si documenta la configuración); `backend/bff/CHANGELOG.md`.
  - **Depende de:** T52.
  - **Recursos:** `application.yml` del BFF.
  - **Prueba primero:** `SlowApiForwardingTest` (**008-07**): un cliente que envía más lento que el plazo de 20 s recibe 408 `SYS-408` y el BFF cierra la conexión aunque siga enviando; `ApiProxyTest` (**008-04**): las cabeceras de la descarga (`Content-Disposition`, `X-Content-Type-Options`) pasan sin cambios y `Cache-Control: no-store` sigue; el *gateway* sigue quitando el `Content-Length` (no se desactiva) y `HandlerFunctions.http()` reenvía el cuerpo en *streaming*, sin convertir la codificación (el riesgo que el plan comprueba aquí).
  - **Al terminar T51 a T53:** revisión C y el PR (H3). La CI publica `contract-bff-v1.5.0`; **F3 la espera.**

## PR C10 · Abrir la opción del menú y limpieza (`backend/`, rama `feat/008-c10-menu`)

Se **escribe** en la ola 6, con C6b integrado, y se aplica **solo en local** para validar (T74). Se integra después de C7, C8, C9, F5 y T74: así la opción no se muestra habilitada antes de que existan la API, su auditoría y las pantallas (001-15). Revisión: N.

- [ ] **T54** · La opción 1.5.9 pasa a `AVAILABLE`.
  - **Proyecto:** `backend/` · **PR:** C10
  - **Archivos:** `res/platform/menu-catalog.yaml` (solo la opción 1.5.9); `test/platform/navigation/MenuCatalogTest.java` y `test/platform/CapabilitiesTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** C6b integrado.
  - **Recursos:** `menu-catalog.yaml`, `MenuCatalogTest` y `CapabilitiesTest` (solo C10).
  - **Prueba primero:** `MenuCatalogTest` (rojo): **008-01** · la opción 1.5.9 está `AVAILABLE` con el permiso `jurisdiction.config.read`, las hojas disponibles pasan a ser la 1.4 y la 1.5.9, y las demás siguen `COMING_SOON`; `CapabilitiesTest` (que fija el mapa completo, ramas incluidas, para cada rol) se ajusta: la rama `1.5` pasa a `AVAILABLE` para quien ve la 1.5.9 y la 1.5.9 deja de ser «Próximamente» para los cinco roles con lectura.
- [ ] **T55** · `backend/dev/limpiar-vigencias.sh` y la *skill* `liv-endpoint`.
  - **Proyecto:** `backend/` · **PR:** C10
  - **Archivos:** `backend/dev/limpiar-vigencias.sh`, `backend/dev/test/limpiar-vigencias-escenarios.sh` (y `fake-docker.sh` si hace falta), `backend/dev/README.md`, `backend/.claude/skills/liv-endpoint/SKILL.md` y `backend/CHANGELOG.md`.
  - **Depende de:** T54 (mismo PR).
  - **Recursos:** `backend/dev/`.
  - **Prueba primero:** la prueba de escenarios (rojo), con el `docker` falso (H2‑15): el *script* vacía `jurisdiction.jurisdiction_config` **antes** que `tax.administration_fiscal_year`, solo por `docker compose … exec postgres psql`, con las mismas guardas (se niega, sin ejecutar nada, si el contenedor no es el de desarrollo, si recibe argumentos o si `DOCKER_HOST` o `DOCKER_CONTEXT` apuntan a otro equipo); la *skill* `liv-endpoint` agrega el ejemplo de una carga de archivo (cuerpo crudo, tope y coordinador del motor): comprobación escrita antes del texto.
  - **Al terminar T54 y T55:** revisión N y el PR (H3). **Se integra después de C7, C8, C9, F5 y T74.**

---

## Bloque D — `liv-components` 0.6.0 (`ui/`)

*Skill* `liv-componente`. Después del paso 0 (Figma, ya aprobado), en paralelo con el backend. Cada componente lleva su prueba por variante y estado, axe en cada combinación (**008-53**), su historia de Storybook enlazada a su nodo de Figma, su exportación en `public-api.ts` y una entrada en `ui/CHANGELOG.md`; ninguno sabe de rutas ni de HTTP; estilos solo por *token*, sin atributo `style`, sin utilidades en la plantilla y sin `::ng-deep`. Verificaciones del proyecto: `npm run lint`, `npm run format:check`, `npm test` y `npm run pack`. La comparación con Figma es una revisión manual en Storybook, con capturas en la descripción del PR (`ui/` no tiene regresión visual, Q14). Cada tarea lee su nodo con la *skill* `liv-figma` antes de escribir. U1 a U3 van a «Sin publicar» (H2‑13); **U4 sube la versión a 0.6.0**. Nada se publica: lo hace la persona.

## PR U1 · Iconos (`ui/`, rama `feat/008-ui-iconos`)

- [ ] **T56** · Iconos `check`, `x`, `upload` y `download` en `Icon` (crítico, H2‑18).
  - **Proyecto:** `ui/` · **PR:** U1
  - **Archivos:** `lib/icon/icons.ts`, `icon.component.spec.ts` y `icon.stories.ts`; `ui/CHANGELOG.md`.
  - **Depende de:** ninguna.
  - **Recursos:** `ui/CHANGELOG.md`.
  - **Prueba primero:** la prueba de `icon.component.spec.ts` que cuenta los iconos y la descripción de Figma se actualiza (rojo); cada icono nuevo toma el color de su contenedor (`currentColor`) y pasa axe; los nombres existentes no cambian (solo se agregan). Diseño: nodo 88:506.
  - **Al terminar:** revisión C (modifica un componente existente; la lente de riesgo, de nivel alto) y el PR (H3), con capturas de Storybook.

## PR U2 · `Checkbox` y `TextArea` (`ui/`, rama `feat/008-ui-checkbox-textarea`)

Con U1 integrado. Revisión: N.

- [ ] **T57** · `Checkbox`.
  - **Proyecto:** `ui/` · **PR:** U2
  - **Archivos:** `lib/checkbox/` (`checkbox.component.ts`, `.html`, `.css`, `.spec.ts` y `checkbox.stories.ts`), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** U1 integrado.
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** un `<input type="checkbox">` nativo con su `<label>`; marcado, sin marcar, inhabilitado y con error; el cambio de valor; integración con los formularios de Angular (`ControlValueAccessor`); el nombre accesible obligatorio; sin «Marcar todas» (decisión de diseño); axe por variante y estado. Diseño: nodo 88:524.
- [ ] **T58** · `TextArea`.
  - **Proyecto:** `ui/` · **PR:** U2
  - **Archivos:** `lib/text-area/` (los cinco archivos del componente), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** T57 (mismo PR, `public-api.ts`).
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** un `<textarea>` con etiqueta, ayuda, error enlazado con `aria-describedby`, contador y `maxlength` (500 para los motivos); el valor y su cambio; estados por omisión, con foco, inhabilitado y con error; axe por variante y estado. Diseño: nodo 88:578.
  - **Al terminar T57 y T58:** revisión N y el PR (H3).

## PR U3 · `FileField` (`ui/`, rama `feat/008-ui-filefield`)

Con U1 integrado; se integra después de U2 (comparten `public-api.ts` y `ui/CHANGELOG.md`). Revisión: N.

- [ ] **T59** · `FileField`.
  - **Proyecto:** `ui/` · **PR:** U3
  - **Archivos:** `lib/file-field/` (los cinco archivos del componente), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** U1 integrado.
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** un `<input type="file">` nativo con un botón visible que lo abre y el nombre y el tamaño del archivo elegido; `accept` como entrada, sin validar el contenido (lo valida el servidor); emite el `File`; estados sin archivo, con archivo, inhabilitado y con error; Enter y Espacio abren el selector; axe por variante y estado. Diseño: nodo 89:611.
  - **Al terminar:** revisión N y el PR (H3).

## PR U4 · `Tab` y versión 0.6.0 (`ui/`, rama `feat/008-ui-tab`)

Con U2 y U3 integrados. Revisión: N.

- [ ] **T60** · `Tab`.
  - **Proyecto:** `ui/` · **PR:** U4
  - **Archivos:** `lib/tab/` (los cinco archivos del componente), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** U2 y U3 integrados.
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** el patrón de pestañas de WAI‑ARIA: `role="tablist"`, `tab` y `tabpanel`, `aria-selected` y `aria-controls`, y un solo Tab que entra a la pestaña activa; las flechas cambian de pestaña con activación automática (jsdom no implementa el foco real del teclado: las flechas se prueban también en el navegador real, en E1, T78); estados y variantes del diseño; axe por variante y estado. Diseño: nodo 90:565.
- [ ] **T61** · Versión 0.6.0.
  - **Proyecto:** `ui/` · **PR:** U4
  - **Archivos:** `ui/projects/liv-components/package.json` (la versión, con `npm run version:minor`), `ui/package-lock.json` (si el comando lo cambia), `ui/CHANGELOG.md` (la sección `## [0.6.0] — <fecha>` con las entradas de U1 a U4; `npm run release:check` la exige) y `ui/projects/liv-components/README.md` (si lista los componentes).
  - **Depende de:** T60 y, para integrarse, U1 a U3.
  - **Recursos:** `ui/CHANGELOG.md`, la versión de `liv-components` y su publicación en npm.
  - **Prueba primero:** la comprobación escrita antes de subir la versión (rojo): `package.json` y `package-lock.json` dicen 0.6.0, `ui/CHANGELOG.md` tiene la sección `## [0.6.0]` con las entradas de U1 a U4 movidas desde «Sin publicar», y `npm run pack` pasa. `npm run release:check` exige estar en `main`, con el árbol limpio y con la sesión de npm de la persona: no corre en la rama del PR, lo corre la persona al publicar. 0.6.0 es correcta porque los cambios son compatibles y en 0.x una versión compatible sube el número del medio.
  - **Al terminar T60 y T61:** revisión N y el PR (H3). **No se publica:** la persona lo hace después de integrar el PR (`ui/README.md` › «Publicar una versión»). El PR dice que **F0 corre la prueba propia del aviso de sesión del *frontend* con 0.6.0** (`Dialog` no cambió).

---

## Bloque E — Pantallas (`frontend/`) y pruebas de navegador (`e2e/`)

*Skill* `liv-pantalla`. Después de C2b (`jurisdiction.yaml` 1.1.0 y `tax.yaml` 1.2.0 publicados), de C9 (`bff.yaml` 1.5.0) para F3 y de U4 (0.6.0 **publicada** en npm). El *frontend* no calcula reglas: los estados, las acciones (`availableActions`, `canUpload`), los hallazgos y los motivos vienen del contrato. Los textos se arman con el código y el motivo del «Catálogo de motivos» (H2‑22), desde las hojas de mensajes del diseño (99:8176 y 99:8264); un motivo desconocido muestra el texto genérico de su código y un valor desconocido de `availableActions` se ignora. Todas las rutas llevan `capabilityGuard` con `capability: '1.5.9'` y se declaran **antes** de la genérica; mientras la opción esté en *Próximamente*, llevan a «Próximamente». Cada PR lee sus *frames* con la *skill* `liv-figma` antes de escribir y, si el diseño no tiene algo (un estado, un texto), se detiene y lo pregunta.

**En todos los PR del *frontend*:**
- Pruebas primero, en el `spec.ts` de cada pantalla: cada estado de cada *frame*; la acción ausente sin su valor en `availableActions`; cada rechazo con su código y su motivo; el aprobar inhabilitado hasta marcar todos los puntos; el motivo obligatorio; el 403 por URL (**008-03**); axe con cada diálogo abierto (**008-53**). El contraste no se mide en jsdom: lo cubre `e2e/`.
- Las fechas van con el formateador de la librería y los estados con los tonos de `Badge` del diseño.
- Cada PR dice en su descripción que la validación en local contra la API real es T74 y no se hizo antes.
- Verificaciones: `npm run lint`, `npm run format:check`, `npm test` y `npm run build`, además de `frontend/scripts/lint-rules.test.mjs` y `eslint.config.js`, que **no cambian** (las pantallas usan `<liv-select>`, `<liv-checkbox>`, `<liv-text-area>`, `<liv-file-field>` y `<liv-tab>`, no los elementos nativos) y siguen en verde.

## PR F0 · Contratos, 0.6.0 y texto del punto 9 (`frontend/`, rama `feat/008-f0-contratos`)

Requiere la *release* `contract-jurisdiction-v1.1.0` y `contract-tax-v1.2.0` (C2b integrado) y **`liv-components` 0.6.0 publicada en npm por la persona**.

- [ ] **T62** · Contratos adoptados y `liv-components` 0.6.0.
  - **Proyecto:** `frontend/` · **PR:** F0
  - **Archivos:** `frontend/api-spec/versions.json`, `frontend/api-spec/jurisdiction.yaml` (copia nueva) y `frontend/api-spec/tax.yaml` (copia fijada); el cliente generado (`app/api/jurisdiction/` y `app/api/tax/`, generados por `npm run api:update`); `frontend/package.json` y `frontend/package-lock.json` (`liv-components` 0.6.0 exacta); `frontend/CHANGELOG.md`.
  - **Depende de:** C2b integrado y *releases* publicadas; U4 integrado y 0.6.0 publicada.
  - **Recursos:** contratos `jurisdiction` y `tax` (`frontend/api-spec/`), versión de `liv-components` y `package.json`.
  - **Prueba primero:** `npm run api:verify` en verde (SHA‑256 de cada *release*); `npm test` completo con 0.6.0, citando en el PR la prueba propia del aviso de sesión (`features/shell/session-warning-dialog.spec.ts`): `Dialog` no cambió. Si `api:update` no acepta un contrato nuevo, la sesión lo avisa antes de tocar `frontend/scripts/`.
- [ ] **T63** · El texto del punto 9 en la lista de preparación de la 1.4.
  - **Proyecto:** `frontend/` · **PR:** F0
  - **Archivos:** `app/features/fiscal-years/fiscal-year-detail.ts` (el texto por motivo) y `fiscal-year-detail.spec.ts`; `app/features/accessibility.spec.ts` (si usa los motivos); `frontend/CHANGELOG.md`.
  - **Depende de:** T62.
  - **Recursos:** ninguno.
  - **Prueba primero:** `fiscal-year-detail.spec.ts` (rojo): **008-43** · el punto 9 con el motivo `JURISDICTION_CONFIG_NOT_PUBLISHED` muestra su texto («Configuración de jurisdicción publicada» pendiente) y, *cumplido*, su marca; ya no muestra «Opción aún no disponible»; un motivo que no conoce no rompe la lista (007-11); los demás puntos no cambian y «Habilitar liquidación» sigue inhabilitado con su motivo.
  - **Al terminar T62 y T63:** revisión N y el PR (H3).

## PR F1 · Lista (`frontend/`, rama `feat/008-f1-lista`)

Con F0 integrado. *Frames* 92:4180, 92:4464, 92:4722, 92:4984, 92:5205 y 92:5434.

- [ ] **T64** · `JurisdictionConfigsService` y las rutas.
  - **Proyecto:** `frontend/` · **PR:** F1
  - **Archivos:** `app/features/jurisdiction-configs/jurisdiction-configs.service.ts` y `.spec.ts`; `app/app.routes.ts` y `app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.5.9` con `capabilityGuard` y `capability: '1.5.9'`, antes de la genérica; y la prueba «una opción visible pero sin construir lleva a Próximamente», que hoy usa `/opcion/1.5.9`, pasa a otra opción que siga sin construir, con su fixture); `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** F0 integrado.
  - **Recursos:** `app.routes.ts`, `app.routes.spec.ts` y `accessibility.spec.ts` (F1 a F5, en serie).
  - **Prueba primero:** `jurisdiction-configs.service.spec.ts` (rojo): llama a la lista, al historial y al detalle sobre el cliente generado y expone `canUpload`, `uploadableYears`, `uploadLimits` y `availableActions` tal como vienen, sin calcular; `app.routes.spec.ts`: la ruta, el 403 por URL (**008-03**) y «Próximamente» mientras la opción siga así.
- [ ] **T65** · La lista de configuraciones.
  - **Proyecto:** `frontend/` · **PR:** F1
  - **Archivos:** `app/features/jurisdiction-configs/jurisdiction-configs.ts`, `.html`, `.css` y `jurisdiction-configs.spec.ts`; `app/features/accessibility.spec.ts`; `app/features/testing/fixtures.ts` (si hace falta); `frontend/CHANGELOG.md`.
  - **Depende de:** T64.
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** `jurisdiction-configs.spec.ts` (rojo), con sus seis estados (cargador, consulta, vacía, cargando, error y 403): **008-01** (las vigencias de la más reciente a la más antigua, cada una con su versión publicada y su versión en curso); «Cargar configuración» solo con `canUpload`; cargando y error (`ResultState` y `Skeleton`); el enlace al historial de cada vigencia; axe de la ruta (**008-53**).
  - **Al terminar T64 y T65:** revisión N y el PR (H3).

## PR F2 · Versiones de la vigencia (`frontend/`, rama `feat/008-f2-versiones`)

Con F1 integrado. *Frames* 95:5176 y 95:5516 (pantalla añadida por el diseño, H2‑12).

- [ ] **T66** · «Versiones de la vigencia».
  - **Proyecto:** `frontend/` · **PR:** F2
  - **Archivos:** `app/features/jurisdiction-configs/fiscal-year-versions.ts`, `.html`, `.css` y `fiscal-year-versions.spec.ts`; `jurisdiction-configs.service.ts` y su `.spec.ts` (solo lo que falte); `app/app.routes.ts` y `app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.5.9/:year`); `app/features/shell/shell.spec.ts` (un caso: en `/opcion/1.5.9/2026` la opción queda activa; `shell.ts` solo si el caso falla); `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** F1 integrado.
  - **Recursos:** `app.routes.ts` y `accessibility.spec.ts`.
  - **Prueba primero:** `fiscal-year-versions.spec.ts` (rojo): **008-02** (todas las cargas con número de versión, estado, SHA‑256, quién cargó y cuándo, quién aprobó o rechazó, el motivo, y cuándo se publicó o se reemplazó; su estado vacío); un `:year` no numérico o fuera de 1999 a 2100 muestra «no encontrada» **sin llamar a la API**, igual que el 404; otro error con el `ResultState` de error; cargando; axe de la ruta (**008-53**); el 403 por URL (**008-03**).
  - **Al terminar:** revisión N y el PR (H3).

## PR F3 · Cargar (`frontend/`, rama `feat/008-f3-cargar`)

Con F1 integrado y la *release* `contract-bff-v1.5.0` (C9). *Frames* 95:5973, 95:6164, 95:6349, 95:6534 y 99:8176.

- [ ] **T67** · El salto de `bff` a 1.5.0 y los rechazos de la carga.
  - **Proyecto:** `frontend/` · **PR:** F3
  - **Archivos:** `frontend/api-spec/versions.json` y `frontend/api-spec/bff.yaml` (de 1.1.0 a 1.5.0: se revisan los cambios de 1.2.0 a 1.4.0 que trae el salto), el cliente generado `app/api/bff/` y los usos que cambien; `app/features/jurisdiction-configs/jurisdiction-config-rejections.ts` y `.spec.ts` (texto por código y motivo, hoja 99:8176); `app/core/http/http-errors.ts` y su `.spec.ts` (solo lo que falte: el 413 del BFF con `rejectedBy: bff` y el 408 `SYS-408`); `frontend/CHANGELOG.md`.
  - **Depende de:** F1 integrado y *release* `contract-bff-v1.5.0` publicada.
  - **Recursos:** `frontend/api-spec/`, `package.json`.
  - **Prueba primero:** `npm run api:verify` en verde y `npm test` completo tras el salto (nada que dependía de `bff` 1.1.0 se rompe); `jurisdiction-config-rejections.spec.ts` (rojo), **008-07**: cada rechazo con su código y su motivo (`CG-03` con la versión que coincide, `CG-04`, `CJC-01`, `V-04`, `CG-06` con el motivo del cargador, el 413 `TAMANO` de la API y el del BFF, el 408 `SYS-408`); un motivo desconocido muestra el texto genérico de su código.
- [ ] **T68** · El diálogo «Cargar configuración».
  - **Proyecto:** `frontend/` · **PR:** F3
  - **Archivos:** `app/features/jurisdiction-configs/upload-jurisdiction-config-dialog.ts`, `.html`, `.css` y `.spec.ts`; `jurisdiction-configs.ts`, `.html` y `jurisdiction-configs.spec.ts` (abrir el diálogo y releer la lista); `jurisdiction-configs.service.ts` y su `.spec.ts` (el método de carga); `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** T67.
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** las pruebas del diálogo (rojo):
    - la vigencia (`Select`, de `uploadableYears`) y el archivo (`FileField` con `accept=".yaml,.yml"`);
    - **008-05, 008-07:** envía el `File` tal cual, con `Content-Type: application/octet-stream`; rechaza **en el navegador**, sin enviarlo, lo que pasa de `uploadLimits.maxRequestBytes` (leído de la API, no escrito en el código), con el mensaje de `TAMANO`; uno entre `maxFileBytes` y ese tope sí lo envía, para que quede auditado;
    - «validando» mientras se envía (Escape no cierra y el botón en `loading`);
    - los rechazos de T67, con la alerta creada de nuevo en cada intento; un 403 lleva a «Sin permiso» (001-16);
    - axe con el diálogo abierto, validando y rechazado (**008-53**).
  - **Al terminar T67 y T68:** revisión N y el PR (H3).

## PR F4a · Detalle y pestañas (`frontend/`, rama `feat/008-f4a-detalle`)

Con F2 integrado. *Frames* 97:6519, 97:6871, 97:7206, 98:7528, 98:7850, 99:7686 y 99:7943.

- [ ] **T69** · El detalle de la versión.
  - **Proyecto:** `frontend/` · **PR:** F4a
  - **Archivos:** `app/features/jurisdiction-configs/jurisdiction-config-detail.ts`, `.html`, `.css` y `jurisdiction-config-detail.spec.ts`; `jurisdiction-configs.service.ts` y su `.spec.ts` (solo lo que falte); `app/app.routes.ts` y `app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.5.9/versiones/:id`); `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** F2 integrado.
  - **Recursos:** `app.routes.ts` y `accessibility.spec.ts`.
  - **Prueba primero:** `jurisdiction-config-detail.spec.ts` (rojo), con sus ocho estados (validada vista por el aprobador, con errores, vista por el cargador, aprobada con cambios, publicada, cargando, no encontrada y error): **008-02** (los datos de la versión, el `Badge` del estado con su tono y la validación completa); las pestañas (`Tab`) de errores, advertencias, no evaluables y por confirmar, con su conteo; cada hallazgo con su código, su ruta y su texto (genérico por código hasta F4b); la lista de revisión con su ruta, clase y norma; las acciones solo según `availableActions`; el `:id` que no es un UUID muestra «no encontrada» sin llamar a la API; **una prueba enumera las palabras clave de CJ01 que documenta el contrato y exige un texto de la pantalla para cada una** (T33); axe de la ruta (**008-53**); el 403 por URL (**008-03**).
  - **Al terminar:** revisión N y el PR (H3).

## PR F4b · Cambios y descarga (`frontend/`, rama `feat/008-f4b-cambios-descarga`)

Con F4a integrado. *Frames* 98:7153 y 99:8264.

- [ ] **T70** · La pestaña de cambios (CJ11).
  - **Proyecto:** `frontend/` · **PR:** F4b
  - **Archivos:** `app/features/jurisdiction-configs/jurisdiction-config-detail.ts`, `.html`, `.css` y su `.spec.ts`; un componente de la pestaña (`jurisdiction-config-changes.ts`, `.html`, `.css` y `.spec.ts`) si el detalle pasa de 400 líneas; `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** F4a integrado.
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** **CJ11 · 008-21**: los cambios agrupados por sección, con la ruta, `ADDED`/`REMOVED`/`CHANGED` y el valor anterior y el nuevo como texto (los decimales conservan su escala); «Primera versión: no hay nada que comparar» cuando `firstVersion`; sin cambios, su estado vacío; axe.
- [ ] **T71** · La descarga del original y la hoja de mensajes del detalle.
  - **Proyecto:** `frontend/` · **PR:** F4b
  - **Archivos:** `app/features/jurisdiction-configs/finding-messages.ts` y `.spec.ts` (la hoja de mensajes del detalle, 99:8264); `jurisdiction-config-detail.ts`, `.html` y su `.spec.ts`; `jurisdiction-configs.service.ts` y su `.spec.ts` (la descarga como `Blob`); `app/core/browser/` (el guardado del archivo, solo si falta); `frontend/CHANGELOG.md`.
  - **Depende de:** T70.
  - **Recursos:** ninguno.
  - **Prueba primero:** **008-04:** «Descargar» solo con `DOWNLOAD` en `availableActions` (no hay marco aparte para el rol sin descarga: la pantalla lo oculta); la descarga pide el original como `Blob` y lo guarda con el nombre de `Content-Disposition`, sin mostrarlo en la página; `finding-messages.spec.ts`: una prueba **enumera el catálogo de motivos de H2‑22 (cada regla y clase) y exige un texto para cada uno**, con los `params` armados en el texto; un motivo desconocido cae al texto genérico de su código.
  - **Al terminar T70 y T71:** revisión N y el PR (H3).

## PR F5 · Diálogos de acción (`frontend/`, rama `feat/008-f5-acciones`)

Con F4a integrado. *Frames* 99:8566, 99:8817, 99:8998, 99:9180 y 99:9347.

- [ ] **T72** · El diálogo de aprobar.
  - **Proyecto:** `frontend/` · **PR:** F5
  - **Archivos:** `app/features/jurisdiction-configs/approve-jurisdiction-config-dialog.ts`, `.html`, `.css` y `.spec.ts`; `jurisdiction-config-detail.ts`, `.html` y su `.spec.ts`; `jurisdiction-configs.service.ts` y su `.spec.ts`; `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** F4a integrado.
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** **CJ02 · 008-34:** un `Checkbox` por punto de `reviewItems` y la aceptación de las advertencias, **sin «Marcar todas»**; «Aprobar» inhabilitado hasta marcar todo (99:8566 con marcas faltantes y 99:8817 con todo marcado); envía `reviewedPaths`, `warningsAccepted` y el `validationDigest` que muestra (H2‑21); si recibe `CJC-02` dice cuáles faltan comparando sus marcas con `reviewItems`; `CJC-03`, `CG-01` y `CG-02` con su texto; el botón en `loading` y Escape no cierra mientras envía; axe con el diálogo abierto (**008-53**).
- [ ] **T73** · Los diálogos de rechazar, anular y publicar.
  - **Proyecto:** `frontend/` · **PR:** F5
  - **Archivos:** `app/features/jurisdiction-configs/reject-jurisdiction-config-dialog.ts`, `annul-jurisdiction-config-dialog.ts` y `publish-jurisdiction-config-dialog.ts` (cada uno con su `.html`, `.css` y `.spec.ts`; la sesión puede unir rechazar y anular en un solo componente si comparten todo); `jurisdiction-config-detail.ts`, `.html` y su `.spec.ts`; `jurisdiction-configs.service.ts` y su `.spec.ts`; `app/features/accessibility.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** T72.
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** **008-35, 008-36:** rechazar y anular, en tono *Warning*, con el motivo obligatorio en un `TextArea` de hasta 500 caracteres (sin motivo, no se envía; `CG-05`); **008-38, 008-39:** publicar con su confirmación, y `CJC-04` con el texto de su motivo, tras lo cual la versión sigue *Aprobada* y se puede rechazar; **008-33:** cada acción solo aparece con su valor en `availableActions`; `CG-01` y `CG-02` con su texto; un 403 dentro de un diálogo lleva a «Sin permiso» (001-16); axe con cada diálogo abierto (**008-53**).
  - **Al terminar T72 y T73:** revisión N y el PR (H3).

## Validación en local

- [ ] **T74** · Validación en local de las pantallas de la 1.5.9 (ADR 0030).
  - **Proyecto:** `frontend/` (sin PR propio) · **Quién:** una sesión de nivel estándar con la persona, en la ola 6.
  - **Archivos:** ninguno. Si encuentra un defecto, abre un PR de corrección en el proyecto dueño (`frontend/` o `backend/`), con la prueba que lo reproduce primero (flujo del SDD o directo, según `AGENTS.md` §4.3), antes de que C10 se integre.
  - **Depende de:** F5 y C7, C8 y C9 integrados, y C10 (T54, T55) escrito y aplicado **solo en local**.
  - **Recursos:** **el entorno local, exclusivo**, y los usuarios de Keycloak; la persona levanta el sistema (`README.md` › «Ejecutar LIV en local») y corre `backend/dev/limpiar-vigencias.sh` antes de cada corrida.
  - **Prueba primero:** una lista de comprobación escrita antes de empezar, con cada pantalla y cada diálogo del plan («Diseño de interfaz») con su *frame*; después, con el navegador integrado: cada pantalla y diálogo contra su *frame* de Figma, el flujo completo con un cargador, un aprobador y un jurídico (carga, validación, aprobación, publicación, versión siguiente con sus cambios, rechazo y anulación) y el punto 9 de la 1.4. El resultado va al reporte, a la descripción del PR de E1 y a cada PR del *frontend* que lo cite.

## PR E1 · Pruebas de navegador (`e2e/`, rama `feat/008-e2e`)

*Skill* `liv-prueba-e2e`. Las pruebas se escriben en rojo mientras se implementan F1 a F5 y se **corren** al final (T74), con el sistema levantado y `backend/dev/limpiar-vigencias.sh` ejecutado por la persona (`e2e/` no lo llama: ADR 0030, H2‑15). Con los datos propios de `e2e/`: las copias de los YAML van en `e2e/datos/` con `e2e/.gitattributes` en LF; la prueba crea y prepara una vigencia por la API (`support/vigencias.ts`) y escribe en su copia el año, las fechas y la versión antes de cargarla, **por sustitución de texto**, sin dependencias nuevas y sin ningún SHA‑256 escrito a mano (**008-53**). Revisión: N.

- [ ] **T75** · Usuarios con segundo factor para los roles que lo exigen.
  - **Proyecto:** `e2e/` · **PR:** E1
  - **Archivos:** `e2e/preparar.ts`, `e2e/limpiar.ts`, `e2e/support/keycloak.ts`, `e2e/support/entorno.ts` (`USUARIOS_E2E` y los secretos `TOTP_SECRETO*`), `e2e/support/acceso.ts` (la sesión guardada con segundo factor) y `e2e/support/totp.ts` (solo si hace falta); `e2e/README.md` (la sección «Usuarios»).
  - **Depende de:** ninguna (la ejecución, de C10 aplicado).
  - **Recursos:** entorno local y usuarios de Keycloak.
  - **Prueba primero:** una comprobación escrita en la ola 5 (rojo: los usuarios no existen en `USUARIOS_E2E` ni en `preparar.ts`) y, en la ola 6, con el entorno local, una corrida de `npm test` en la que `preparar.ts` crea tres usuarios de la administración de Ipiales, con un TOTP ficticio y conocido cada uno, por la API de administración de Keycloak, y `limpiar.ts` los borra: `e2e.aprobador` (`APROBADOR_TABLAS`), `e2e.juridico` (`JURIDICO`) y `e2e.auditor` (`AUDITOR`), porque esos roles exigen segundo factor y los `dev.*` correspondientes no pueden entrar sin configurarlo («Puntos del plan que estas tareas interpretan», 4); `dev.cargador.tablas` y `dev.consulta` entran sin TOTP y no se crean; `e2e.admin.b` sigue igual; la sesión de cada uno con TOTP se guarda una sola vez por archivo (`guardarSesionConSegundoFactor`).
- [ ] **T76** · Recorridos de la carga, la aprobación y la publicación.
  - **Proyecto:** `e2e/` · **PR:** E1
  - **Archivos:** `e2e/pruebas/configuracion-jurisdiccion.spec.ts` y `e2e/pruebas/configuracion-jurisdiccion.spec.ts-snapshots/` (nuevos); `e2e/datos/` (las copias de los dos YAML) y `e2e/.gitattributes`; `e2e/support/configuraciones.ts` (nuevo) y `e2e/support/vigencias.ts` (solo lo que falte); `e2e/README.md` (la tabla «Qué prueban» y los datos).
  - **Depende de:** T75.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero:** `configuracion-jurisdiccion.spec.ts` (rojo mientras F1 a F5 se implementan):
    - **008-01 y 008-02:** la lista muestra la vigencia con su versión publicada y su versión en curso, y el historial, todas sus cargas con su estado;
    - `dev.cargador.tablas` carga Ipiales y ve *Validada* con sus advertencias, y carga un archivo con errores y ve *Con errores* (**008-05, 008-11 a 008-13**); el mismo archivo otra vez se rechaza con la versión que coincide (**008-09**); con una versión en curso, otra carga se rechaza (**008-10**);
    - `e2e.aprobador` aprueba marcando cada punto, y `e2e.juridico` la publica; el punto 9 de la 1.4 queda cumplido (**008-43**);
    - la versión siguiente muestra los cambios (**008-21**) y reemplaza a la anterior (**008-38**);
    - el cargador anula la suya (**008-36**) y el aprobador rechaza otra con motivo (**008-35**); ningún aprobador es el cargador (**008-33**).
- [ ] **T77** · Recorridos de consulta, aislamiento y el tope del cuerpo.
  - **Proyecto:** `e2e/` · **PR:** E1
  - **Archivos:** `e2e/pruebas/configuracion-jurisdiccion.spec.ts` y sus capturas; `e2e/support/configuraciones.ts`; `e2e/README.md`.
  - **Depende de:** T76.
  - **Recursos:** entorno local.
  - **Prueba primero:**
    - `e2e.auditor` descarga el original (con el SHA‑256 de lo cargado) y no ve las acciones (**008-04**); `dev.consulta` recibe 403 por URL (**008-03**); `e2e.admin.b` (`ADMIN_SISTEMA` de B) no ve las de Ipiales y publica su propio 2026 (**CA‑12 · 008-44, 008-45**);
    - **el tope, de punta a punta** (**008-07**, H2‑8), con peticiones directas por el BFF (`APIRequestContext`, con la sesión y el `X-XSRF-TOKEN`, porque la pantalla no envía nada por encima del tope): el tope exacto (1.114.112) da el 413 de la API con `fileSha256`, y el tope + 1 byte, el 413 del BFF con `rejectedBy: bff`; un tope distinto en el BFF y en la API, mayor o menor, hace fallar la prueba; la pantalla rechaza en el navegador lo que pasa del tope, sin enviarlo.
- [ ] **T78** · El teclado de las pestañas, el menú de la 001, axe y capturas.
  - **Proyecto:** `e2e/` · **PR:** E1
  - **Archivos:** `e2e/pruebas/configuracion-jurisdiccion.spec.ts` y sus capturas; `e2e/pruebas/estructura.spec.ts` y `e2e/pruebas/estructura.spec.ts-snapshots/` (la captura `inicio-administrador-menu`); `e2e/README.md`.
  - **Depende de:** T77.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero:** **navegación con teclado** de las pestañas (`Tab`) en el navegador real (las flechas cambian de pestaña y Tab entra solo a la activa), que jsdom no cubre (T60); axe en cada pantalla y diálogo, **con el contraste** (**008-53**); capturas de referencia de la lista, el historial, el detalle y cada diálogo, con máscara sobre las fechas, los SHA‑256 y los años, **que aprueba la persona**; `estructura.spec.ts`: el menú del administrador muestra la 1.5.9 como disponible, así que su captura cambia y **la persona la vuelve a aprobar**; la prueba de «Próximamente» (que usa la 1.7) no cambia.
  - **Antes del PR:** `npm test` en verde con el sistema local y la revisión de cada pantalla contra su *frame* de Figma (T74). **Al terminar T75 a T78:** revisión N y el PR (H3). Se integra **al final**, con C10 integrado.

---

## Cobertura de los criterios

Cada criterio de `spec.md` y la tarea que escribe su prueba primero (del plan, «Trazabilidad de los criterios»). Los criterios 008-48 a 008-52 salen de esta feature (decisión 19): no tienen tarea.

| Criterio | Tareas |
|---|---|
| 008-01 | T43, T44, T54, T64, T65, E1 (T76) |
| 008-02 | T43, T44, T46, T66, T69, E1 (T76) |
| 008-03 (enmienda 2) | T31, T44, T52, T64, T66, T69, E1 (T77) |
| 008-04 | T31, T43, T44, T53, T71, E1 (T77) |
| 008-05 | T35, T42, T44, T52, T68 |
| 008-06 (V‑04) | T39, T40, T44 |
| 008-07 (enmienda 2) | T11, T35, T42, T44, T49, T52, T53, T67, T68, E1 (T77) |
| 008-08 | T11, T44 |
| 008-09 | T30, T39, T42, T52, E1 (T76) |
| 008-10 | T30, T36, T39, T42, E1 (T76) |
| 008-11 (CJ01) | T13, T14 |
| 008-12, 008-13 (CJ02) | T14, T15, T29 |
| 008-14 (CJ03) | T16 |
| 008-15 (CJ04) | T17 |
| 008-16, 008-17 (CJ05, CJ06) | T18 |
| 008-18 (CJ08, enmienda 1) | T19 |
| 008-19 (CJ09, CJ10) | T24 |
| 008-20 (CJ12) | T20 |
| 008-21 (CJ11) | T24, T70, E1 (T76) |
| 008-22, 008-23 (CJ13, CJ14) | T21 |
| 008-24 (CJ15) | T22 |
| 008-25 (CJ16) | T23 |
| 008-26 (CJ17) | T25, T30, T39 |
| 008-27 (CJ18) | T26 |
| 008-28 | T29 |
| 008-29 | T14, T42 |
| 008-30 | T12, T13, T29, T41, T42 |
| 008-31 (CA‑5.13) | T27 |
| 008-32 | T11, T28 |
| 008-33 (CA‑05) | T30, T36, T38, T45, T46, T47, T48, T49, T73, E1 (T76) |
| 008-34 (CJ02) | T38, T45, T72 |
| 008-35 | T32, T38, T46, T73, E1 (T76) |
| 008-36 | T30, T36, T46, T73, E1 (T76) |
| 008-37 | T36, T45 |
| 008-38 | T30, T32, T47, T73, E1 (T76) |
| 008-39 | T38, T47, T73 |
| 008-40 | T30, T47 |
| 008-41 | T30, T32, T41, T42 |
| 008-42 | T47 |
| 008-43 (Doc. 01 §6.1) | T34, T50, T63, E1 (T76) |
| 008-44 (CA‑12) | T30, T40, T43, T44, E1 (T77) |
| 008-45 | T25, T30, E1 (T77) |
| 008-46 | T43, T47, T48, T49 |
| 008-47 (enmienda 2) | T37, T45, T47, T49 |
| 008-48 a 008-52 | — (feature aparte, decisión 19) |
| 008-53 | T56 a T61 (axe de cada componente), T65, T66, T68, T69, T70, T72, T73, E1 (T78) |

## Cierre de la feature

- [ ] Actualizar documentación si aplica (funcional, arquitectura, ADR, glosario). Los documentos van en R1, R2a y R2b; el glosario («versión de la configuración», «motor de ingesta», «hallazgo», «lista de revisión», «versión del validador», «resultado de validación» y los que entreguen las sesiones) lo escribe el orquestador.
- [ ] Revisión adversarial independiente de lo implementado en cada PR; hallazgos resueltos o justificados. La del PR de C3 revisa que `FileReceiver` cuenta los bytes; la prueba que lo demuestra por el servidor real es `UploadSizeLimitsTest`, de C5b, y la revisión de C5b la comprueba y la de C6b, las comprobaciones de 008-39 con cada motivo.
- [ ] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra (la tabla de arriba y «Trazabilidad de los criterios» del `plan.md`). Lo hace el orquestador.
- [ ] Compuertas de CI en verde y PR aprobado por un humano (H3), en cada PR.
- [ ] **La persona publica `liv-components` 0.6.0 en npm** después de integrar U4, y no se abre F0 hasta que esté publicada.
- [ ] Validación en local (T74) hecha y E1 en verde contra el entorno local, con las capturas aprobadas por la persona.
- [ ] Mover la feature a «Hecho» en `../../constitution/roadmap.md` y actualizar `ESTADO.md` (el orquestador), con: la excepción del original en la base de datos (ADR 0036) en las decisiones abiertas, atada a la feature de la opción 1.3 o a antes de producción; la feature aparte «validador de anexos y CI» (criterios 004‑13 y 008-48 a 008-52) en el *backlog* de la Fase 1, y la nota de la feature 1.2 sobre alertar las asignaciones de rol que crucen cargar y aprobar (decisión 2); las filas de «Anexos YAML» de la spec (cambian el SHA‑256 de las copias: esperan esa feature).

## Mantenimiento (checklist recurrente)

- [ ] Si cambia una regla CJ o una interpretación de las pruebas *golden* (H2‑5, H2‑6, H2‑19, la fusión de advertencias), sube `JurisdictionConfigValidator.VERSION` (H2‑17: el menor con una regla nueva o más estricta, el parche con una corrección que no cambia ningún resultado *golden*), actualiza a la vez `JurisdictionConfigGoldenTest`, el catálogo de motivos del contrato y `finding-messages.ts`, y recalcula el resultado del Doc. 03 §9.2.
- [ ] Si un anexo YAML cambia (feature aparte), actualizar a la vez las copias de `1.1/` del backend, `AnnexCopiesTest` (008-32), los datos de `e2e/datos/` y el resultado del Doc. 03 §9.2 con su SHA‑256 nuevo.
- [ ] Si cambia el tope del cuerpo (H2‑8), actualizar a la vez `IngestionLimits`, `uploadLimits` de `jurisdiction.yaml`, `bff.yaml`, el filtro del BFF, `UploadSizeLimitsTest` y la prueba del tope de E1 (los tres proyectos comparten el mismo número).
- [ ] Si la feature de la opción 1.3 (o antes de producción) llega, retirar la excepción del ADR 0036: escanear y migrar los originales ya guardados, conservando su SHA‑256 (008-41).
- [ ] Si la 1.5.1 agrega la vuelta a *En preparación* y la habilitación, ampliar la regla de 008-05 y 008-39 (hoy ninguna transición sale de *En preparación*), la política RLS de `UPDATE` de `tax.administration_fiscal_year` frente a `FOR SHARE`, `FiscalYearSchemaTest`, `LivAppGrantsTest` y `PreparationChecklistTest`, y el texto del punto 9.
