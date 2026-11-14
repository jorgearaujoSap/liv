# 007 · Vigencias fiscales — Tareas

_Checklist accionable derivada del `plan.md` (aprobado en H2, 2026-11-13). Los códigos `007-NN` son los criterios de `spec.md`. Tareas pequeñas y concretas; cada PR cabe en ~400 líneas, salvo el B6, que el plan deja como un solo PR y que tiene su corte previsto (véase «Mapa de los PR»); B5a, B5b y B7 se miden antes de abrirse. Marca `[x]` al completarlas. Cada tarea indica:_

- _su proyecto, su PR del plan y los archivos que puede tocar;_
- _de qué otras tareas depende;_
- _qué recursos compartidos usa: un contrato, la versión de `liv-components`, las migraciones o los archivos de *build* de su proyecto, el entorno local;_
- _la prueba o verificación que se escribe primero (TDD: rojo → verde → refactorizar). En las tareas de documentación, la «prueba primero» es una lista de comprobación escrita antes del texto, en el *scratchpad* de la sesión (no se sube), y corrida al final._

_Con eso el orquestador decide qué corre en paralelo (`liv-orquestador`)._

Es un cambio crítico (decisión 4 de la H1): antes de cada PR, `liv-revision-adversarial` con dos revisores de nivel alto y las cuatro lentes de `liv-revision-4r`, con el resumen en la descripción del PR; antes de cada *commit*, la lente de legibilidad. Un PR toca un solo proyecto (`AGENTS.md` §4.6). Modelo: nivel estándar para todas las tareas, salvo el ADR (T3 y T4), que es de nivel alto, igual que la revisión adversarial y la lente de riesgo de los cambios críticos (`AGENTS.md` §10). Las sesiones de las tareas **no escriben** `ESTADO.md`, el *roadmap*, el glosario, las marcas de esta lista ni los criterios de `spec.md`: se lo reportan al orquestador (`AGENTS.md` §7, ADR 0033). Las tareas de código no incluyen el glosario: los términos nuevos («vigencia de la administración» y «lista de preparación», plan, paso 1) los entrega la sesión de T2 en su reporte y el orquestador los escribe.

## Mapa de los PR

Los pasos y los números de PR son los del plan: 1 y 2 (raíz), 3 a 7 y 10 (`backend/`), 8a a 8f (`ui/`), F9a y F9b (pasos 9a y 9b, `frontend/`) y E11 (paso 11, `e2e/`).

| PR | Rama | Proyecto | Tareas | Depende para **escribirse** | Depende para **integrarse** |
|---|---|---|---|---|---|
| D1 · Documentos (paso 1) | `feat/007-documentos` | raíz | T1, T2 | nada | nada (va primero) |
| D2 · ADR 0035 (paso 2) | `feat/007-adr-0035` | raíz | T3, T4 | nada | nada (H3: la persona acepta el ADR) |
| B3 · Base de datos (paso 3) | `feat/007-base-de-datos` | `backend/` | T5, T6 | D1 integrado | D1 |
| B4 · Contrato (paso 4) | `feat/007-contrato` | `backend/` | T7 | D1 integrado | D1 |
| B5a · Dominio: estados, agregado y política (paso 5a) | `feat/007-dominio` | `backend/` | T8, T9 | D2 integrado | D2 |
| B5b · Dominio: lista de preparación (paso 5b) | `feat/007-lista-preparacion` | `backend/` | T10 | D2 integrado | D2 |
| B6 · Aplicación, persistencia y API (paso 6) | `feat/007-aplicacion-api` | `backend/` | T11 a T15 | B3, B4, B5a y B5b integrados | B3, B4, B5a y B5b |
| B7 · Auditoría (paso 7) | `feat/007-auditoria` | `backend/` | T16, T17 | B6 integrado | B6 |
| B10 · Menú y limpieza (paso 10) | `feat/007-menu` | `backend/` | T31, T32 | B6 integrado | B6, B7 y **F9b** (después de validar el frontend con su rama aplicada) |
| UI 8a · `Table` | `feat/007-ui-table` | `ui/` | T18 | nada (el diseño ya está aprobado) | nada |
| UI 8b · `ChecklistItem`, `DescriptionItem`, `Panel` y formateador de fechas | `feat/007-ui-panel` | `ui/` | T19, T20 | nada | nada |
| UI 8c · `RadioGroup` y `RadioOption` | `feat/007-ui-radio` | `ui/` | T21 | nada | nada (antes del 8f) |
| UI 8d · Iconos `plus` y `circle` | `feat/007-ui-icon` | `ui/` | T22 | nada | nada |
| UI 8e · `Button` con `disabledReason` | `feat/007-ui-button` | `ui/` | T23 | nada | nada |
| UI 8f · `Dialog` con cuerpo, tamaño y tono, y versión 0.4.0 | `feat/007-ui-dialog` | `ui/` | T24 | 8c integrado | 8a a 8e integrados (sube la versión y mueve las entradas) |
| F9a · Lista y creación | `feat/007-front-lista` | `frontend/` | T25 a T28 | B4 integrado (*release* `contract-tax-v1.0.0`), 8f integrado y **0.4.0 publicada en npm** por la persona (el *frame* 75:4479 ya está aprobado, el 2026-11-13) | las mismas, y B6 integrado (T25 lo declara) |
| F9b · Detalle y preparación | `feat/007-front-detalle` | `frontend/` | T29, T30 | F9a integrado | F9a |
| E11 · Pruebas de navegador | `feat/007-e2e` | `e2e/` | T33 a T35 | nada (se escriben en rojo mientras se hace F9a y F9b; se **corren** con B10 aplicado en local) | B10, F9a y F9b integrados |

**Tamaño.** B6 es un solo PR, como dice el plan, y casi seguro pasa de ~400 líneas (casos de uso, adaptador JPA con sus cuatro pruebas de carreras, controlador, pruebas de la API y `ModularityTest`). Sus tareas están en el orden en que se podría cortar en dos sin cambiar nada: **6a** = T11 a T13 (errores, casos de uso y persistencia) y **6b** = T14 y T15 (API y cierre). La decisión de cortarlo es del orquestador, que lo mide antes de abrir el PR. B5a, B5b, B6 y B7 se miden igual.

### Orden de integración (obligatorio; plan, «Dependencias y recursos»)

1. `D1` y `D2` (en cualquier orden entre ellos); `8a` a `8e`, que no dependen de nada.
2. Con D1 integrado: `B3` y `B4`. Con D2 integrado: `B5a` y `B5b`.
3. `B6`, cuando estén integrados B3, B4, B5a y B5b. Después, `B7`.
4. `8f`, cuando estén integrados 8a a 8e. **La persona publica 0.4.0 en npm.** Con B4 integrado, `backend-main` publica `contract-tax-v1.0.0`.
5. `F9a → F9b`. Se validan en local con la rama de B10 aplicada (plan, paso 10, «Validación del paso 9»); su resultado se cita en cada PR del frontend.
6. `B10`, después de B6, B7 y F9b. Después, `E11`.
7. El cierre del orquestador (*roadmap*, glosario, `ESTADO.md`, criterios de `spec.md`).

### Paralelismo (máximo tres sesiones de trabajo a la vez)

- **Ola 1 (ya):** D1 (T1, T2), D2 (T3, T4) y UI. No comparten archivos. En `ui/` conviene empezar por 8c (el 8f depende de él) y 8a; el resto de la serie 8 puede ocupar el tercer lugar a medida que D1 y D2 terminan.
- **Ola 2, con D1 integrado:** B3 (T5, T6) y B4 (T7) a la vez (no comparten archivos: migraciones y pruebas de base de datos contra `pom.xml`, contrato y su prueba), más una sesión de `ui/`.
- **Ola 3, con D2 integrado:** B5a (T8, T9) y B5b (T10) a la vez (dominio puro, archivos distintos), cuando haya sesiones libres; el camino crítico del *backend* prefiere B3 y B4 primero.
- **Ola 4, con B3, B4, B5a y B5b integrados:** B6 (T11 a T15), **sola en `backend/`**: usa el contrato, `shared`, `ProblemDetailsHandler` y `ModularityTest`, que B5a y B7 también tocan. Pueden correr a la vez sesiones de `ui/` o de `frontend/` que ya estén listas.
- **Ola 5, con B6 integrado:** B7 (T16, T17) y B10 (T31, T32). B10 no toca los archivos de B7 (`menu-catalog.yaml`, `MenuCatalogTest` y `backend/dev/`), pero los dos usan `backend/CHANGELOG.md` (se resuelve al rebasar).
- **Ola 6:** F9a (T25 a T28) → F9b (T29, T30), en serie (comparten `app.routes.ts`, `accessibility.spec.ts` y `features/fiscal-years/`). Cada uno se valida en local con el entorno, que es exclusivo: ninguna otra sesión lo usa mientras.
- **Ola 7:** E11 (T33 a T35). Se escribe en rojo mientras se hace F9; se corre al final, con el entorno local libre.
- **En serie siempre:** T1 → T2; T3 → T4; T5 → T6; T8 → T9; T11 → T12 → T13 → T14 → T15; T16 → T17; T19 → T20; T25 → T26 → T27 → T28; T29 → T30; T31 → T32; T33 → T34 → T35; 8c → 8f.
- **La serie 8a a 8f puede escribirse en paralelo** (cada PR toca el directorio de su componente), pero **se integra en serie**: todos agregan una entrada a `ui/CHANGELOG.md` y varios una exportación a `public-api.ts`; el segundo en integrarse rebasa.
- **El entorno local es exclusivo** (ADR 0030): lo usan las validaciones de F9a y F9b y E11, de una en una. Las pruebas de B3, B6 y B7 usan Testcontainers, no el entorno local.

### Recursos compartidos

| Recurso | Quién lo escribe | Regla |
|---|---|---|
| Migraciones de `backend/` | B3 (T5, T6) | Ninguna otra tarea las toca |
| `backend/pom.xml` y `contracts/openapi/tax.yaml` | B4 (T7) | Solo B4 |
| `shared`, `platform/web/ProblemDetailsHandler`, `ModularityTest` | B5a (`shared`), B6, B7 | En serie: B5a → B6 → B7 |
| `AuditLog` | B7 (T16) | Solo B7 |
| `menu-catalog.yaml`, `MenuCatalogTest` y `backend/dev/` | B10 (T31, T32) | Solo B10 |
| `backend/CHANGELOG.md` | B3 a B7 y B10 | Compartido: cada PR agrega su entrada en «Sin publicar»; el segundo en integrarse rebasa |
| `ui/CHANGELOG.md`, `ui/projects/liv-components/src/public-api.ts` | 8a a 8f | Compartidos: véase arriba |
| Versión de `liv-components` y su publicación en npm | 8f (sube la versión), la persona (publica) | Hasta la publicación, F9a no empieza |
| `frontend/api-spec/` y `frontend/package.json` | F9a (T25) | F9b no los toca |
| Entorno local, usuarios de Keycloak y capturas de `e2e/` | E11, y las validaciones de F9a y F9b | Exclusivo, de una sesión a la vez |
| Figma | — | Todo aprobado; el *frame* 75:4479 (etiqueta de año pasado) lo aprobó la persona el 2026-11-13 |

## PR D1 · Documentos (raíz, rama `feat/007-documentos`) — plan, paso 1

Se integra **antes** de B3 y B4: la prueba de la matriz de permisos y el documento deben coincidir (`docs/arquitectura/02` §4.4). No toca la spec (las enmiendas ya están en ella) ni el Doc. 03.

- [x] **T1** · ERD 2.3.
  - **Proyecto:** raíz · **PR:** D1
  - **Archivos:** `docs/funcional/ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md` (encabezado y esquema `tax`: el diagrama Mermaid, la lista de tablas, las restricciones y la nota de las FK futuras, y la lista previa a Flyway).
  - **Depende de:** ninguna.
  - **Recursos:** el ERD (solo D1 lo edita).
  - **Prueba primero:** lista de comprobación escrita antes del texto. Cada punto debe aparecer en el Mermaid, en la lista de tablas y en las relaciones:
    - `tax.fiscal_year` sin `status` ni `assessments_enabled`, con `CHECK (starts_on = make_date(year, 1, 1))` y `CHECK (ends_on = make_date(year, 12, 31))` (H2‑2) y el rango técnico y provisional 1999–2100;
    - `tax.administration_fiscal_year` con todas sus columnas, los dos `UNIQUE`, las FK compuestas de `created_by` y `updated_by`, el `CHECK (NOT assessments_enabled)`, las cuatro políticas RLS (sin `DELETE`) y los permisos de `liv_app` por columna;
    - la nota de las FK futuras (nacionales hacia `tax.fiscal_year (year)`; de una administración hacia (`administration_id`, `fiscal_year`));
    - la autorización H2‑1 en la lista previa a Flyway, con cada punto de la tabla del plan;
    - el conteo pasa de 79 a 80 tablas, en el encabezado y en la lista, y la versión pasa a 2.3 con su motivo.
- [x] **T2** · Doc. 01 v0.5, Doc. 02 v0.3, registro entre documentos y `AGENTS.md` §3.1.
  - **Proyecto:** raíz · **PR:** D1
  - **Archivos:** `docs/funcional/01_Menu_y_Cargue_Tablas_Base_Liquidacion.md` (encabezado, §1, §3, §5, §6.1, §8 y §11, y el registro de cambios entre documentos al estilo del Doc. 03 §7), `docs/funcional/02_Homologacion_y_Reglas_de_Liquidacion.md` (versión, LQ‑P01 y el diagrama del §B.2) y `AGENTS.md` (solo §3.1: «80 tablas» y «versión 2.3»).
  - **Depende de:** T1 (los nombres de tablas y columnas del ERD).
  - **Recursos:** los Docs. 01 y 02 y `AGENTS.md` (solo D1 los edita).
  - **Prueba primero:** lista de comprobación escrita antes del texto:
    - encabezado del Doc. 01 con 80 tablas y v0.5, y el del Doc. 02 con v0.3;
    - §1: la liquidación exige `assessments_enabled` de la vigencia de **la administración**;
    - §3: el mapa del menú, 1.4 → `tax.fiscal_year` y `tax.administration_fiscal_year`;
    - §5: el modelo de una vigencia por administración sobre un año nacional, las reglas `V‑05` (con `FIRST_YEAR` y `FUTURE_LIMIT`; H2‑6 y H2‑8) y `V‑06` (H2‑8), la tabla de permisos de acción de la opción 1.4 (`tax.fiscal-year.create` y `tax.fiscal-year.prepare`, solo `ADMIN_SISTEMA`; H2‑3) y la nota de que la habilitación, *Lista* y el cierre van en la 1.5.1;
    - §6.1, punto 1: lee `tax.administration_fiscal_year.status`; §8: la entidad es `tax.administration_fiscal_year` (H2‑7); §11: el pendiente 5 remite a la feature 5.1 y se agrega el cierre y la reapertura (V‑04, decisión 1b) como pendiente;
    - Doc. 02: LQ‑P01 y el diagrama del §B.2 leen el estado y `assessments_enabled` de la vigencia de la administración;
    - los códigos de regla y los permisos coinciden con `spec.md` y con la tabla de permisos del plan, y no queda ninguna referencia a `tax.fiscal_year.status`.
  - **Al terminar T1 y T2:** revisión adversarial, las cuatro lentes y el PR (H3). El glosario no se toca (nota de arriba).

## PR D2 · ADR 0035 (raíz, rama `feat/007-adr-0035`) — plan, paso 2

Se integra **antes** de B5a, B5b, B6 y B7, porque el código de `tax` ya usa `platform` y `shared`. *Skill* `liv-adr`.

- [x] **T3** · Redactar el ADR 0035 como «Propuesto», su fila en la tabla de ADR y la figura de `docs/arquitectura/01` §3.2.
  - **Proyecto:** raíz · **PR:** D2 · **Modelo:** nivel alto
  - **Archivos:** `docs/adr/ADR-0035-dependencias-entre-modulos-y-errores-de-negocio.md` (nuevo), `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` (solo la fila 0035 de la tabla de §6) y `docs/arquitectura/01_Arquitectura_de_Software.md` (la figura del §3.2 y su encabezado de versión; **no** el glosario del §15).
  - **Depende de:** ninguna.
  - **Recursos:** `docs/arquitectura/01` (D1 no lo edita; el glosario lo escribe el orquestador).
  - **Prueba primero:** el ADR sigue la plantilla MADR de `liv-adr` y el formato de los ADR 0033 y 0034; dice cada punto del plan (paso 2): `audit → tax`, `tax → platform`, `tax → shared` y `platform → shared` (con `ModularityTest`), ningún módulo depende de `audit`, `shared.RuleViolation` (con código de regla, tipo `CONFLICT` o `UNPROCESSABLE` y detalles) y `shared.NotFound` (404 `SYS-404`) traducidos por `ProblemDetailsHandler` sin ciclos, `shared` como módulo normal de Spring Modulith, la propiedad `liv.business-zone` (`America/Bogota`) con el `Clock` de `platform` en UTC, y los puntos de la lista de preparación que vivirán en `tax/infrastructure` (con `tax → documents` como flecha futura con su propio ADR); estado «Propuesto»; la fila del doc. 00 §6 coincide con el estilo de las filas 0033 y 0034; la figura del §3.2 coincide con el ADR.
- [x] **T4** · Aceptación formal del ADR 0035 en la H3.
  - **Proyecto:** raíz · **PR:** D2 · **Modelo:** nivel alto
  - **Archivos:** `docs/adr/ADR-0035-dependencias-entre-modulos-y-errores-de-negocio.md` y la fila 0035 del doc. 00 §6 (solo el estado y la fecha).
  - **Depende de:** T3, y de que la persona acepte el ADR (solo un humano lo acepta).
  - **Recursos:** ninguno.
  - **Prueba primero:** el estado del ADR y el de la fila dicen lo mismo («Aceptado», con la fecha que da la persona); el PR no se integra con el ADR en «Propuesto». **B5a, B5b, B6 y B7 esperan este PR integrado.**
  - **Al terminar T3 y T4:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B3 · Base de datos (`backend/`, rama `feat/007-base-de-datos`) — plan, paso 3

*Skill* `liv-migracion`. Con D1 integrado. Ninguna migración aplicada se modifica.

- [x] **T5** · Esquema `tax`: `fiscal_year` y `administration_fiscal_year`, con su RLS y sus permisos.
  - **Proyecto:** `backend/` · **PR:** B3
  - **Archivos:** `backend/src/main/resources/db/migration/V<marca>__tax_fiscal_years.sql` (nueva), y en `backend/src/test/java/co/com/sapenlinea/liv/isolation/`: `AdministrationIsolationTest.java` y `LivAppGrantsTest.java` (se amplían) y `FiscalYearSchemaTest.java` (nueva); `backend/CHANGELOG.md`.
  - **Depende de:** D1 integrado.
  - **Recursos:** migraciones de `backend/` (solo B3); `backend/CHANGELOG.md`.
  - **Prueba primero** (rojo, con PostgreSQL 18 en Testcontainers, antes de la migración):
    - `AdministrationIsolationTest`, **CA‑12 · 007-18 y 007-19**: con el contexto de A no se ven ni se escriben filas de B; sin contexto no se ve nada; A y B tienen cada una su 2026;
    - `LivAppGrantsTest`: las columnas y los permisos esperados de las dos tablas (`SELECT` e `INSERT`; `UPDATE` solo de `status`, `updated_by` y `updated_at`; sin `DELETE`);
    - `FiscalYearSchemaTest`: los `CHECK` de V‑01 y de `assessments_enabled` (007-12); el `UNIQUE` (007-05); los años 1999 a 2100; que `liv_app` no puede insertar una vigencia en otro estado que *Planeada*, ni cambiar `assessments_enabled`, ni borrar; que `WITH CHECK` rechaza con 42501 pasar de *Planeada* a *Habilitada*, *Lista* o *Cerrada*; y que `USING` deja intactas las vigencias fuera de *Planeada*: volver de *En preparación* a *Planeada* y tocar solo `updated_at` actualizan **0 filas** (la prueba cuenta filas, PostgreSQL no da error).
  - **Verde:** la migración, con la comprobación de `uuidv7()`, el esquema `tax` con `GRANT USAGE` a `liv_app`, las dos tablas, los años 1999 a 2100, la RLS (una política por operación) y los permisos.
- [x] **T6** · Permisos de acción `tax.fiscal-year.create` y `tax.fiscal-year.prepare`.
  - **Proyecto:** `backend/` · **PR:** B3
  - **Archivos:** `backend/src/main/resources/db/migration/V<marca>__platform_fiscal_year_permissions.sql` (nueva, con una marca posterior a la de T5) y `backend/src/test/java/co/com/sapenlinea/liv/isolation/PlatformTablesTest.java` (`permissionMatrixIsTheApprovedOne`); `backend/CHANGELOG.md`.
  - **Depende de:** T5 (orden de las migraciones).
  - **Recursos:** migraciones de `backend/`.
  - **Prueba primero:** `PlatformTablesTest.permissionMatrixIsTheApprovedOne` se amplía con los dos permisos y sigue siendo la única matriz esperada, igual a la del Doc. 01 v0.5 (D1): asignar `create` a `JURIDICO` o a `AUDITOR` la hace fallar (007-02, 007-09). Falla porque la migración todavía no existe.
  - **Al terminar T5 y T6:** revisión adversarial, las cuatro lentes (la de riesgo, de nivel alto) y el PR (H3).

## PR B4 · Contrato (`backend/`, rama `feat/007-contrato`) — plan, paso 4

*Skill* `liv-endpoint`, paso 1. Con D1 integrado. Al integrarlo, `backend-main` publica `contract-tax-v1.0.0`.

- [x] **T7** · `tax.yaml` 1.0.0 y su generación.
  - **Proyecto:** `backend/` · **PR:** B4
  - **Archivos:** `backend/contracts/openapi/tax.yaml` (nuevo), `backend/pom.xml` (solo una ejecución más de `openapi-generator`, sin dependencias nuevas), `backend/src/test/java/co/com/sapenlinea/liv/contracts/OpenApiContractTest.java`, `backend/README.md` (la tabla de contratos) y `backend/CHANGELOG.md`.
  - **Depende de:** D1 integrado (los códigos `V‑05` y `V‑06` salen del Doc. 01 v0.5).
  - **Recursos:** contrato `tax` (nuevo); `backend/pom.xml`.
  - **Prueba primero:** `OpenApiContractTest` (rojo) comprueba el contrato del plan («Datos, API y seguridad»):
    - las cuatro operaciones (`listFiscalYears`, `getFiscalYear`, `createFiscalYear` y `openFiscalYearPreparation`) con sus rutas por año, no por `id`, sus respuestas y sus errores (400, 401, 403, 404 `SYS-404`, 409 `V‑01` y `V‑06`, 422 `V‑02` y `V‑05`);
    - `year` entero con `minimum: 1999` y `maximum: 2100`;
    - el `status` de la vigencia con los cinco estados, el de un punto con `MET`, `PENDING` y `OBSERVED`, y `availableActions`, `relation`, el motivo de `createBlocked` y el `code` y `reason` de cada punto como cadenas con sus valores documentados;
    - `canCreate`, `creatableYears` (con fechas y `relation`: `PAST`, `CURRENT` o `NEXT`) y `createBlocked` (`FUTURE_LIMIT` y `maxYear`);
    - las extensiones de cada rechazo (`year`, `requiredYear`, `oldestYear`, `currentYear`, `nextYear`, `maxYear`, `status`);
    - seguridad `bearerAuth` bajo `/api/v1/`, sin ninguna operación que habilite la liquidación.
  - **Verde:** el contrato; `sh contracts/scripts/compare.sh origin/main` lo juzga como contrato nuevo.
  - **Al terminar T7:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B5a · Dominio: estados, agregado y política (`backend/`, rama `feat/007-dominio`) — plan, paso 5

Con D2 integrado. Java puro, con pruebas unitarias primero (`@DisplayName` con el código del criterio). No usa los modelos generados.

- [x] **T8** · `shared.RuleViolation`, `shared.NotFound`, `FiscalYearStatus` y `AdministrationFiscalYear`.
  - **Proyecto:** `backend/` · **PR:** B5a
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/`: `shared/RuleViolation.java`, `shared/NotFound.java`, `tax/domain/FiscalYearStatus.java` y `tax/domain/AdministrationFiscalYear.java`; en `backend/src/test/java/co/com/sapenlinea/liv/`: `shared/RuleViolationTest.java` y `tax/domain/AdministrationFiscalYearTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** D2 integrado.
  - **Recursos:** `shared` (B6 y B7 lo usan después).
  - **Prueba primero:** `RuleViolationTest` (código de regla, tipo `CONFLICT` o `UNPROCESSABLE`, detalles, excepción **sin marcar**, `NotFound` aparte) y `AdministrationFiscalYearTest`: **007-10** · `openPreparation()` desde `PLANEADA` da `EN_PREPARACION` y desde cualquier otro estado da `V‑06` con el estado actual; los cinco estados con los códigos del Doc. 01 §5; `assessmentsEnabled` no cambia nunca.
- [x] **T9** · `FiscalYearOpeningPolicy`.
  - **Proyecto:** `backend/` · **PR:** B5a
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/tax/domain/FiscalYearOpeningPolicy.java` (más los tipos pequeños que necesite, p. ej. `CreatableYear`, `YearRelation` y `CreateBlocked`, en `tax/domain/`) y `backend/src/test/java/co/com/sapenlinea/liv/tax/domain/FiscalYearOpeningPolicyTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T8.
  - **Recursos:** ninguno.
  - **Prueba primero:** `FiscalYearOpeningPolicyTest` comprueba el código, el motivo y las extensiones de cada combinación, **en el orden del plan** (el primero que falla es el que se responde): **V‑01 · 007-04 y 007-05** (ya existe), **V‑02 · 007-07** `HISTORICAL` (`oldestYear`), **V‑05 · 007-08** `FIRST_YEAR` (`currentYear`, `nextYear`), **V‑02 · V‑05 · 007-06** `GAP` (`requiredYear`) y `FUTURE_LIMIT` (`maxYear`, H2‑6); un caso por par de reglas que se puede cumplir a la vez (p. ej. 2029 con la más reciente 2026 da `GAP`); `creatableYears` con sus fechas y `relation` (`CURRENT`, `NEXT` y **`PAST`**: la más reciente es 2026, estamos en 2028 y nadie creó 2027, así que el único año que se puede crear es 2027 con V‑02) y `createBlocked` con `FUTURE_LIMIT` cuando no queda ninguno, siempre dentro de la tabla nacional.
  - **Al terminar T8 y T9:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B5b · Dominio: lista de preparación (`backend/`, rama `feat/007-lista-preparacion`) — plan, paso 5

Con D2 integrado. Se puede escribir a la vez que B5a: no comparten archivos. Mismas condiciones que B5a: Java puro, pruebas unitarias primero y `@DisplayName` con el código del criterio.

- [x] **T10** · `PreparationChecklist` y el puerto `PreparationItemCheck`.
  - **Proyecto:** `backend/` · **PR:** B5b
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/tax/domain/PreparationChecklist.java`, `PreparationItemCheck.java` y los tipos pequeños que necesite (p. ej. `PreparationItem`), y `backend/src/test/java/co/com/sapenlinea/liv/tax/domain/PreparationChecklistTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** D2 integrado. **No** depende de T8 ni de T9: recibe el estado de la vigencia como dato.
  - **Recursos:** ninguno.
  - **Prueba primero:** `PreparationChecklistTest`: **007-11** · los nueve puntos del Doc. 01 §6.1, cada uno con su número, `code`, obligatoriedad (1 a 5, 8 y 9 obligatorios; 6 y 7 opcionales) y opción del menú; el punto 1 se cumple desde `EN_PREPARACION` (y en los estados posteriores) y no desde `PLANEADA`; los demás salen del puerto, que en la 007 no tiene implementaciones y da «Opción aún no disponible» (`OPTION_NOT_AVAILABLE`), nunca cumplido; **V‑03 · CA‑14 · 007-12** · `canEnable()` es falso siempre en la 007 y `missing` lista los obligatorios que faltan.
  - **Al terminar T10:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B6 · Aplicación, persistencia y API (`backend/`, rama `feat/007-aplicacion-api`) — plan, paso 6

Con B3, B4, B5a y B5b integrados. Una sola sesión en `backend/` mientras dure. Corte previsto: 6a = T11 a T13, 6b = T14 y T15. La revisión adversarial y las cuatro lentes se hacen una vez por PR (o por mitad, si se corta) y están en T15.

- [ ] **T11** · Errores de negocio en la API, `liv.business-zone` y las flechas de `platform`.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/platform/web/ProblemDetailsHandler.java`, `backend/src/main/resources/application.yml` (la propiedad `liv.business-zone: America/Bogota`), y en `backend/src/test/java/co/com/sapenlinea/liv/`: `platform/ErrorResponsesTest.java` y `architecture/ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T8 (las excepciones de `shared`) y B3, B4, B5a (T8 y T9) y B5b integrados.
  - **Recursos:** `ProblemDetailsHandler` y `ModularityTest` (compartidos con B7: en serie).
  - **Prueba primero:** `ErrorResponsesTest`: `RuleViolation` `CONFLICT` da 409 y `UNPROCESSABLE` da 422, con su `code` y sus extensiones en el Problem Details; `NotFound` da 404 con `SYS-404` y un cuerpo idéntico sea cual sea el motivo (CA‑12); `ModularityTest`: `platform` depende solo de `shared` (`containsExactly("shared")`).
- [ ] **T12** · Casos de uso, `BusinessYear`, el puerto `FiscalYears` y los eventos.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/tax/`: `FiscalYearCreated.java` y `FiscalYearStatusChanged.java` (paquete raíz, como `core.SessionStarted`) y, en `application/`, `BusinessYear.java`, `FiscalYears.java` (puerto, con `lockForCreation`), `ListFiscalYears.java`, `GetFiscalYear.java`, `CreateFiscalYear.java` y `OpenFiscalYearPreparation.java`; en `backend/src/test/java/co/com/sapenlinea/liv/tax/application/`: `CreateFiscalYearTest.java`, `ListFiscalYearsTest.java` y `BusinessYearTest.java` (esta última, en el contexto real); `ModularityTest` (`tax` depende de `platform` y `shared`); `backend/CHANGELOG.md`.
  - **Depende de:** T11, T9 y T10.
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** con un doble del puerto, **007-04 a 007-08** (cada rechazo, con su código y extensiones) y **007-10** en `CreateFiscalYearTest` y `OpenFiscalYearPreparationTest`; **la frontera del año**, en `CreateFiscalYearTest` y `ListFiscalYearsTest` con `Clock.fixed(2027-01-01T04:30Z)` (31 de diciembre de 2026 a las 23:30 en Bogotá): se pueden crear 2026 (`CURRENT`) y 2027 (`NEXT`), y 2028 se rechaza con `V‑05` `FUTURE_LIMIT` y su `maxYear`; la lista nunca ofrece un año que la creación rechace; `canCreate` según `tax.fiscal-year.create`; `BusinessYearTest` comprueba, con el contexto real, que usa la zona `America/Bogota` (falla si fuera UTC); cada caso de uso lleva su `@RequiresPermission` (`AuthorizationRulesTest` ya lo exige) y los eventos llevan `eventId`, `administrationId`, `userId`, el `id` de la fila, el año, el estado anterior (nulo al crear), el nuevo, el instante y el `correlationId`.
- [ ] **T13** · Persistencia JPA: entidades, repositorios y el adaptador de `FiscalYears`.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/tax/infrastructure/persistence/`: la entidad de la fila de la administración, la de solo lectura del año nacional, sus repositorios y el adaptador `JpaFiscalYears`; en `backend/src/test/java/co/com/sapenlinea/liv/tax/infrastructure/persistence/`: `JpaFiscalYearsTest.java` (Testcontainers y el adaptador real); `backend/CHANGELOG.md`.
  - **Depende de:** T12.
  - **Recursos:** ninguno.
  - **Prueba primero:** `JpaFiscalYearsTest`, con las pruebas que demuestran **los dos altos de la verificación acotada** (se comprueban en la revisión adversarial del código de este paso; véase abajo): cada una fuerza su intercalación.
    - **`UNIQUE` real (V‑01 · 007-05):** el dueño de los objetos inserta la vigencia 2026; `save` de 2026 sin pasar por el bloqueo da `RuleViolation` `V‑01`, no `DataIntegrityViolationException` (por SQLState `23505` y el nombre de la restricción);
    - **apertura que pierde la carrera (007-10):** con dos `TransactionTemplate`, T1 lee la fila, T2 la abre y confirma, y el `UPDATE` de T1 afecta 0 filas y da `V‑06` con el estado `EN_PREPARACION` (nunca 503); un reintento después de un éxito también da `V‑06`;
    - **apertura que gana:** la respuesta dice `EN_PREPARACION` y un contador de sentencias de Hibernate comprueba que no emite ningún `UPDATE` propio (entidad solo de lectura e inserción: `id`, `status`, `assessments_enabled` y `created_at` con `insertable = false` y `@Generated`, y todas las columnas `updatable = false`);
    - **el bloqueo (007-05):** dos instancias de `CreateFiscalYear`, cada una con su `Clock.fixed` a un lado de la medianoche del 31 de diciembre en Bogotá, sobre el adaptador real; un espía detiene a A justo después de obtener el bloqueo y B sigue esperando durante un tiempo límite; al liberar a A el resultado es {2026} y B recibe `V‑02` `GAP`; **la misma prueba con un `lockForCreation` vacío da {2026, 2028}**, con lo que se demuestra que la prueba detecta la falta del bloqueo;
    - `CreateFiscalYear` y `OpenFiscalYearPreparation` en `READ_COMMITTED`; una vigencia de otra administración da `NotFound` (CA‑12).
- [ ] **T14** · `FiscalYearsController` y las pruebas de la API.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/tax/api/FiscalYearsController.java` (sin lógica, máximo 200 líneas) y `backend/src/test/java/co/com/sapenlinea/liv/tax/FiscalYearsApiTest.java` (`extends PlatformWebTest`); `backend/CHANGELOG.md`.
  - **Depende de:** T13 y T7 (la interfaz generada).
  - **Recursos:** contrato `tax`.
  - **Prueba primero:** `FiscalYearsApiTest`, antes del controlador y fallando por una aserción: las respuestas y errores del contrato; **007-01 y 007-03** (lista, orden y estado vacío con las opciones de `creatableYears`); **007-02 y 007-09** (401 y 403 por rol); una vigencia de otra administración y un año inexistente dan **el mismo 404**; **CA‑12 · 007-18 y 007-19** por la API; **V‑01 · 007-05** y **007-10** por la API; dos creaciones y dos aperturas a la vez, solo como prueba de humo (nunca 500 ni 503). Cada prueba que necesite una administración sin vigencias la crea con el dueño de los objetos, porque `liv_app` no puede borrar y el contenedor se comparte.
- [ ] **T15** · Cierre del PR: ejemplo hexagonal en la *skill* y registro.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `backend/.claude/skills/liv-endpoint/SKILL.md` (agrega el ejemplo del módulo hexagonal `tax`, como pide la propia *skill*) y `backend/CHANGELOG.md`.
  - **Depende de:** T14.
  - **Recursos:** `backend/CHANGELOG.md`.
  - **Prueba primero:** `./mvnw verify` en verde, con el Spotless y las pruebas guardianas (`ArchitectureRulesTest`, `CodeSizeGuardTest`, `ModularityTest`, `ContractRulesTest`).
  - **Revisión adversarial del código de este PR:** además de lo normal, **comprueba los dos altos de la verificación acotada** (plan, «Revisión»): (1) que las pruebas del adaptador de T13 fuercen cada intercalación (el `UNIQUE` real, la apertura que pierde la carrera y el bloqueo, con su variante sin bloqueo) y (2) que `relation` admita `PAST` con su prueba (T9 y T12). Dos revisores ciegos de nivel alto; si aparece otro hallazgo alto, se escala a la persona.

## PR B7 · Auditoría (`backend/`, rama `feat/007-auditoria`) — plan, paso 7

Con B6 integrado.

- [ ] **T16** · `AuditLog.Entry` con `previousValues`.
  - **Proyecto:** `backend/` · **PR:** B7
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/audit/application/AuditLog.java`, `backend/src/main/java/co/com/sapenlinea/liv/audit/infrastructure/JdbcAuditLog.java` y los siete usos actuales de `Entry` (`audit/application/RecordSessionEvents.java` y los demás que el compilador señale en `audit/`), y las pruebas de `backend/src/test/java/co/com/sapenlinea/liv/audit/` que los usan; `backend/CHANGELOG.md`.
  - **Depende de:** B6 integrado.
  - **Recursos:** `AuditLog` (solo B7).
  - **Prueba primero:** una prueba de `JdbcAuditLog` (p. ej. en `audit/AuditRows.java` y `SessionAuditTest.java`) comprueba que `previous_values` se escribe cuando viene y que las filas de sesión siguen con `previous_values` nulo; `previousValues` admite nulo (el mapa se copia solo si existe); el *javadoc* de `entityName` agrega `tax.administration_fiscal_year`.
- [ ] **T17** · Escucha de los eventos de la vigencia y auditoría.
  - **Proyecto:** `backend/` · **PR:** B7
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/audit/infrastructure/FiscalYearEventsListener.java` y `backend/src/main/java/co/com/sapenlinea/liv/audit/application/RecordFiscalYearEvents.java`; `backend/src/test/java/co/com/sapenlinea/liv/audit/FiscalYearAuditTest.java` y `backend/src/test/java/co/com/sapenlinea/liv/architecture/ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T16.
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** `FiscalYearAuditTest`, **007-20**: `entity_name = tax.administration_fiscal_year`, `entity_id` el `id` de la fila, `operation` `FISCAL_YEAR_CREATED` o `FISCAL_YEAR_STATUS_CHANGED`, `previous_values` `{status}` (nulo al crear), `new_values` `{year, status}` (la lista blanca), y `user_id`, `administration_id`, `occurred_at` y `correlation_id` los del evento; el evento llega aunque el consumidor falle una vez (registro de eventos) y es idempotente por `eventId`; `ModularityTest`: `audit` depende de `core`, `platform` y `tax`, y nadie depende de `audit`.
  - **Al terminar T16 y T17:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR UI 8a a 8f · `liv-components` (`ui/`) — plan, paso 8

*Skill* `liv-componente`. Cada componente lleva su prueba por variante y estado, axe en cada combinación (**007-21**), su historia de Storybook enlazada a su nodo de Figma, su exportación en `public-api.ts` y una entrada en `ui/CHANGELOG.md`; ninguno sabe de rutas ni de HTTP. Verificaciones del proyecto: `npm run lint`, `npm run format:check`, `npm test` y `npm run pack`. Los PR 8d, 8e y 8f son **críticos** (modifican un componente existente) y se revisan con la lente de riesgo de nivel alto. En todos los PR, la comparación con Figma es una revisión manual en Storybook, con capturas en la descripción (`ui/` no tiene regresión visual, Q14). Cada tarea indica su PR. Los cinco primeros van a «Sin publicar» (H2‑15); **el 8f sube la versión a 0.4.0** (`npm run version:minor`) y mueve las entradas a su sección. Nada se publica: lo hace la persona después de integrar el 8f.

- [x] **T18** · **8a** · `Table`, `TableRow` y `TableCell`.
  - **Proyecto:** `ui/` · **PR:** 8a
  - **Archivos:** `ui/projects/liv-components/src/lib/table/` (los componentes, sus `.css`, `.html`, `.spec.ts` y las historias), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** ninguna.
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** una tabla HTML real (`table[livTable]`, `tr[livTableRow]`, `th[livTableCell]` con `scope="col"` y `td[livTableCell]`); nombre accesible obligatorio (`caption` o `aria-label`) y `aria-busy` en *Loading*; la celda de enlace dibuja su propio `<a href>` y emite `navigate`, con una prueba del color del enlace por *token*; solo el enlace recibe el foco, con *Hover* y *Focus* por `:focus-within` y el chevron decorativo (`aria-hidden`); los tipos `Header`, `Text`, `Link`, `Badge` y `Action`; axe por variante y estado. Sin *Empty* (la 007 usa `ResultState`), barra ni paginación. Diseño: nodos 63:283, 63:352 y 64:446.
  - **Al terminar:** revisión adversarial, las cuatro lentes y el PR (H3).
- [x] **T19** · **8b** · `ChecklistItem`, `DescriptionItem` y `Panel`.
  - **Proyecto:** `ui/` · **PR:** 8b
  - **Archivos:** `ui/projects/liv-components/src/lib/checklist-item/`, `description-item/` y `panel/` (componentes, estilos, pruebas e historias), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** ninguna.
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** `ChecklistItem` con `Status` `Met`, `Pending` y `Observed` (se implementa aunque la 007 no lo use), número, título, motivo, enlace y `Optional`; `DescriptionItem` `Text` y `Badge`; `Panel` con título, descripción opcional y contenido proyectado; axe por variante y estado. Diseño: nodos 64:573, 64:583 y 64:631.
- [x] **T20** · **8b** · Formateador de fechas de negocio.
  - **Proyecto:** `ui/` · **PR:** 8b
  - **Archivos:** `ui/projects/liv-components/src/lib/shared/` (el formateador y su `.spec.ts`; la configuración de pruebas de la zona, si hace falta), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** T19 (mismo PR y `public-api.ts`).
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** formatea una fecha de negocio (`AAAA-MM-DD`) **sin convertir de zona**: la larga («1 de enero de 2026», H2‑13) y la corta (`dd/mm/aaaa`). La prueba fija `process.env.TZ = 'America/Bogota'` y comprueba al empezar que la zona está activa (`new Date(2026, 0, 1).getTimezoneOffset() === 300`); si no, falla, porque en UTC el defecto no se vería.
  - **Al terminar T19 y T20:** revisión adversarial, las cuatro lentes y el PR (H3).
- [x] **T21** · **8c** · `RadioGroup` y `RadioOption` (H2‑12, diseño aprobado).
  - **Proyecto:** `ui/` · **PR:** 8c
  - **Archivos:** `ui/projects/liv-components/src/lib/radio-group/` (los dos componentes, estilos, pruebas e historias), `ui/projects/liv-components/src/public-api.ts` y `ui/CHANGELOG.md`.
  - **Depende de:** ninguna. **8f depende de este PR integrado.**
  - **Recursos:** `ui/CHANGELOG.md` y `public-api.ts`.
  - **Prueba primero:** un `<fieldset>` con su `<legend>` (nombre accesible) y radios nativos con un `name` común; el valor y el cambio de selección; `Selected` × `State` (`Default`, `Hover`, `Focus` y `Disabled`, que se implementa porque está diseñada); con y sin descripción; axe por variante y estado. Las flechas y el Tab que solo entra a la opción marcada los da el navegador y jsdom no los implementa: se prueban en `e2e/` (T35). Diseño: nodos 64:629 y 73:514.
  - **Al terminar:** revisión adversarial, las cuatro lentes y el PR (H3).
- [x] **T22** · **8d** · Iconos `plus` y `circle` en `Icon` (crítico).
  - **Proyecto:** `ui/` · **PR:** 8d
  - **Archivos:** `ui/projects/liv-components/src/lib/icon/icons.ts`, `icon.component.spec.ts` y `icon.stories.ts`, y `ui/CHANGELOG.md`.
  - **Depende de:** ninguna.
  - **Recursos:** `ui/CHANGELOG.md`.
  - **Prueba primero:** la prueba de `icon.component.spec.ts` que cuenta los iconos y la descripción de Figma se actualiza (rojo); cada icono toma el color de su contenedor (`currentColor`) y pasa axe. `chevron-left` no entra: es de la paginación (1.6). Diseño: nodo 63:243.
  - **Al terminar:** revisión adversarial y las cuatro lentes (la de riesgo, de nivel alto).
- [ ] **T23** · **8e** · `Button` con `disabledReason` (crítico).
  - **Proyecto:** `ui/` · **PR:** 8e
  - **Archivos:** `ui/projects/liv-components/src/lib/button/` (`button.component.ts`, `.html`, `.css`, `.spec.ts` y `button.stories.ts`) y `ui/CHANGELOG.md`.
  - **Depende de:** ninguna.
  - **Recursos:** `ui/CHANGELOG.md`.
  - **Prueba primero:** `disabledReason` solo cuenta con `disabled`; con motivo, el botón usa `aria-disabled="true"` y sigue recibiendo el foco, con su estado propio en el anfitrión (p. ej. `data-state="disabled"`) para el aspecto inhabilitado; el aspecto es igual con motivo y sin él; no emite clic con el ratón, con Enter, con Espacio ni como `submit` de un formulario; `aria-describedby` apunta al texto visible; `reasonAlign` (`start` o `end`); sin `disabled` el motivo no se muestra; `loading` conserva la precedencia de hoy; las reglas de *hover* y de «cargando» (`.button[aria-disabled='true']`) excluyen ese estado, comprobado por el texto de las reglas porque jsdom no evalúa `:hover` (la revisión manual en Storybook lo confirma); axe. Diseño: nodo 65:456.
  - **Al terminar:** revisión adversarial y las cuatro lentes (la de riesgo, de nivel alto).
- [ ] **T24** · **8f** · `Dialog` con cuerpo, tamaño y tono (H2‑11, crítico) y versión 0.4.0.
  - **Proyecto:** `ui/` · **PR:** 8f
  - **Archivos:** `ui/projects/liv-components/src/lib/dialog/` (`dialog.component.ts`, `.html`, `.css`, `.spec.ts` y `dialog.stories.ts`), `ui/projects/liv-components/package.json` (la versión, con `npm run version:minor`), `ui/package-lock.json` (si el comando lo cambia) y `ui/CHANGELOG.md` (la sección `## [0.4.0] — <fecha>` con las entradas de 8a a 8f; `npm run release:check` la exige).
  - **Depende de:** T21 (`RadioGroup`) y, para integrarse, T18 a T23.
  - **Recursos:** `ui/CHANGELOG.md`, la versión de `liv-components` y su publicación en npm.
  - **Prueba primero:** el cuerpo es una ranura con nombre (`<ng-content select="[livDialogBody]">`) y el contenido sin selector sigue yendo a las acciones; `size` `md` (440 px, por omisión) o `lg` (520 px); `tone` `warning` (por omisión) o `primary`; **una prueba del uso por omisión** (sin cuerpo, sin `size` ni `tone`) fija 440 px, el tono de advertencia y que todo el contenido sin selector va a las acciones (es el uso del aviso de sesión de la 001); la trampa de foco cuenta, de cada grupo de radios, el marcado o, si no hay ninguno, el primero habilitado al avanzar y el último al retroceder, y excluye los radios de un `fieldset` inhabilitado, con la misma regla para el primer foco al abrir; **las pruebas de teclado de un `RadioGroup` dentro de un `Dialog`** (uno y dos años, con y sin opción marcada); axe. Diseño: conjunto 73:482 (65:472 y 73:467).
  - **Al terminar:** revisión adversarial y las cuatro lentes (la de riesgo, de nivel alto); `npm run release:check` en verde. **No se publica**: la persona lo hace después de integrar el PR (`ui/README.md` › «Publicar una versión»). El PR dice que **F9a corre la prueba propia del aviso de sesión del frontend con 0.4.0**.

## PR F9a · Lista y creación (`frontend/`, rama `feat/007-front-lista`) — plan, paso 9

*Skill* `liv-pantalla`. **Requiere** el contrato `tax` publicado como *release* `contract-tax-v1.0.0` (B4 integrado), `liv-components` 0.4.0 publicada en npm (8f integrado y publicada por la persona) y el *frame* 75:4479 (la etiqueta de un año pasado, `relation: PAST`), **aprobado el 2026-11-13**. El *frontend* no calcula reglas: todo lo que muestra sobre años, fechas y motivos viene del contrato, y ninguna pantalla usa `new Date()` para decidir.

- [ ] **T25** · Contrato adoptado, versión nueva de la librería y `FiscalYearsService`.
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/api-spec/versions.json` y `frontend/api-spec/tax.yaml` (copia fijada), el cliente generado (`frontend/src/app/api/tax/`, generado por `npm run api:update`), `frontend/package.json` y `frontend/package-lock.json` (`liv-components` 0.4.0 exacta), `frontend/src/app/features/fiscal-years/fiscal-years.service.ts` y su `.spec.ts`, y `frontend/CHANGELOG.md`.
  - **Depende de:** B4 integrado y *release* publicada, 8f integrado y 0.4.0 publicada; para integrarse el PR, también B6 integrado.
  - **Recursos:** contrato `tax` (`frontend/api-spec/`), versión de `liv-components` y `package.json`.
  - **Prueba primero:** `npm run api:verify` en verde (SHA‑256 de la *release*); `fiscal-years.service.spec.ts` (rojo): llama a los cuatro métodos sobre el cliente generado y expone `canCreate`, `creatableYears` y `createBlocked` tal como vienen. **Antes de seguir**, `npm test` completo con 0.4.0, citando en el PR la prueba propia del aviso de sesión (`features/shell/session-warning-dialog.spec.ts`): `Dialog` por omisión no cambió.
- [ ] **T26** · La lista de vigencias, con sus siete estados.
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/src/app/features/fiscal-years/fiscal-years.ts`, `.html`, `.css` y `fiscal-years.spec.ts`, `frontend/src/app/app.routes.ts` y `frontend/src/app/app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.4` con `capabilityGuard` y `capability: '1.4'`, antes de la genérica), `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** T25.
  - **Recursos:** `app.routes.ts` y `accessibility.spec.ts` (F9b los toca después: en serie).
  - **Prueba primero:** `fiscal-years.spec.ts` (rojo): **007-01** (la lista, del año más reciente al más antiguo, con las fechas con el formateador de la librería); **007-03** (el estado vacío con `ResultState`, y la acción solo con `canCreate`); **007-09** (`canCreate` en falso, sin la acción); `createBlocked` (el botón inhabilitado con su motivo, `disabledReason`); cargando y error (`ResultState` y `Skeleton`); axe de la ruta en `accessibility.spec.ts` (**007-21**), con el botón inhabilitado con motivo; `app.routes.spec.ts`: la ruta, y el 403 por URL (**007-02**) y «Próximamente» mientras la opción siga así. Diseño: *frames* 66:1967 a 66:3493.
- [ ] **T27** · El diálogo de creación y los rechazos.
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/src/app/features/fiscal-years/create-fiscal-year-dialog.ts`, `.html`, `.css` y `.spec.ts` (nombres a decidir por la sesión dentro de `features/fiscal-years/`), `fiscal-years.ts` y `fiscal-years.spec.ts` (para abrir el diálogo y releer la lista), `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** T26 (el *frame* 75:4479 ya está aprobado, el 2026-11-13).
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** las pruebas del diálogo (rojo): las opciones salen de `creatableYears` con su etiqueta (incluida la de un año `PAST`) y sus fechas; lleva la advertencia de H2‑6 (**007-04**); el grupo de años siempre tiene una opción marcada mientras está abierto; mientras se envía, el botón está en `loading` y Escape no cierra; cada rechazo con su código y sus extensiones del Problem Details (**V‑01 · 007-05**, **V‑02 · 007-06 y 007-07**, **V‑05 · 007-08** y `V‑06`), con la alerta creada de nuevo en cada intento para que se anuncie aunque el texto se repita; tras un `V‑01`, la lista se vuelve a leer y queda marcado el primer año que siga disponible, y si no queda ninguno (`createBlocked`) el diálogo se cierra y la lista muestra el botón inhabilitado con su motivo; un 403 lleva a «Sin permiso» (H2‑14, 001-16); axe con el diálogo abierto y con un rechazo (**007-21**). Diseño: *frames* 67:3040, 67:3163 y 67:3284, y el 75:4479 (aprobado el 2026-11-13).
- [ ] **T28** · Fecha larga para fechas sueltas (H2‑13).
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/AGENTS.md` (solo la fila «Localización») y `frontend/.claude/skills/liv-pantalla/SKILL.md` (solo el paso 5) y `frontend/CHANGELOG.md`.
  - **Depende de:** T26 (la primera pantalla que usa el formato).
  - **Recursos:** ninguno.
  - **Prueba primero:** comprobación escrita antes del texto: las dos reglas admiten la fecha larga («1 de enero de 2026») para fechas sueltas y piden `dd/mm/aaaa` en tablas de consulta y exportaciones, y dicen que el formateador sale de `liv-components`; `npm run lint` y `npm run format:check` siguen en verde.
  - **Validación en local, antes del PR (ADR 0030):** con el sistema levantado y la rama de B10 aplicada, se ven las pantallas de la lista y del diálogo, se comparan con Figma y se corre `e2e/`; su resultado se cita en el PR.

**Al terminar T25 a T28:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR F9b · Detalle y preparación (`frontend/`, rama `feat/007-front-detalle`) — plan, paso 9

Con F9a integrado.

- [ ] **T29** · El detalle de la vigencia y su lista de preparación.
  - **Proyecto:** `frontend/` · **PR:** F9b
  - **Archivos:** `frontend/src/app/features/fiscal-years/fiscal-year-detail.ts`, `.html`, `.css` y `fiscal-year-detail.spec.ts`, `frontend/src/app/features/fiscal-years/fiscal-years.service.ts` (solo lo que falte) y su `.spec.ts`, `frontend/src/app/app.routes.ts` y `app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.4/:year` con `capabilityGuard` y `capability: '1.4'`, antes de la genérica), `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** F9a integrado.
  - **Recursos:** `app.routes.ts` y `accessibility.spec.ts`.
  - **Prueba primero:** `fiscal-year-detail.spec.ts` (rojo): **007-10** (la acción «Abrir preparación» solo con `OPEN_PREPARATION`; sin ella en cualquier otro estado, y un valor desconocido de `availableActions` se ignora; un `V‑06` muestra su alerta encima de los datos y vuelve a leer la vigencia); **007-11** (los nueve puntos, con el título sacado de su `code`, la frase de obligatorios y opcionales armada con `required` y «Opción aún no disponible» como pendiente); **V‑03 · CA‑14 · 007-12** («Habilitar liquidación» inhabilitado con `disabledReason` a partir de `missing`); el parámetro `:year` no numérico o fuera de 1999–2100 se muestra «no encontrada» **sin llamar a la API**, igual que el 404; otro error (500 o tiempo agotado) con el `ResultState` de error; cargando; axe de la ruta, con el botón con motivo (**007-21**); `app.routes.spec.ts`: el 403 por URL (**007-02**). Diseño: *frames* 68:3460 a 68:4474.
- [ ] **T30** · `Shell`: la opción del menú activa en sus rutas hijas.
  - **Proyecto:** `frontend/` · **PR:** F9b
  - **Archivos:** `frontend/src/app/features/shell/shell.ts` y `shell.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** ninguna (mismo PR que T29).
  - **Recursos:** ninguno.
  - **Prueba primero:** `shell.spec.ts` (rojo): en `/opcion/1.4/2026` «Vigencias fiscales» queda activa y «Administración» abierta (hoy solo se marca con la ruta exacta de la opción).
  - **Validación en local, antes del PR:** igual que la de T28.

**Al terminar T29 y T30:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B10 · Abrir la opción del menú y limpiar las vigencias (`backend/`, rama `feat/007-menu`) — plan, paso 10

Con B6 integrado (para escribirse); se integra después de B6, B7 y F9b. Su rama se aplica en local para validar F9a y F9b.

- [ ] **T31** · La opción 1.4 pasa a `AVAILABLE`.
  - **Proyecto:** `backend/` · **PR:** B10
  - **Archivos:** `backend/src/main/resources/platform/menu-catalog.yaml` (solo la opción 1.4), `backend/src/test/java/co/com/sapenlinea/liv/platform/navigation/MenuCatalogTest.java` y `backend/CHANGELOG.md`.
  - **Depende de:** B6 integrado.
  - **Recursos:** `menu-catalog.yaml` y `MenuCatalogTest` (solo B10).
  - **Prueba primero:** `MenuCatalogTest` (rojo): **007-01** · la opción 1.4 está `AVAILABLE` con el permiso `tax.fiscal-year.read` y las demás siguen `COMING_SOON`.
- [ ] **T32** · `backend/dev/limpiar-vigencias.sh` (H2‑9).
  - **Proyecto:** `backend/` · **PR:** B10
  - **Archivos:** `backend/dev/limpiar-vigencias.sh` (nuevo, junto a `check.sh`), su prueba de escenarios con un `docker` falso (en `backend/dev/test/`, como `contracts/scripts/test/fake-gh.sh`), `backend/dev/README.md` y `backend/CHANGELOG.md`. `tools/dev/` puede invocarlo, pero ese cambio es de la raíz y queda fuera de esta feature.
  - **Depende de:** T31 (mismo PR).
  - **Recursos:** `backend/dev/`.
  - **Prueba primero:** la prueba de escenarios (rojo), con el `docker` falso: el *script* solo ejecuta `docker compose -f dev/compose.yaml exec postgres psql …`, toca únicamente `tax.administration_fiscal_year`, y **se niega** (código de salida distinto de 0 y sin ejecutar nada) si el contenedor no es el de desarrollo.
  - **Al terminar T31 y T32:** revisión adversarial y las cuatro lentes, y el PR (H3). **Se integra después de B6, B7 y F9b.**

## PR E11 · Pruebas de navegador (`e2e/`, rama `feat/007-e2e`) — plan, paso 11

*Skill* `liv-prueba-e2e`. Las pruebas se escriben en rojo mientras se implementa F9; se corren al final, con el sistema levantado (`README.md` › «Ejecutar LIV en local») y `backend/dev/limpiar-vigencias.sh` ejecutado por la persona. `e2e/` no lo llama (ADR 0030, H2‑9).

- [ ] **T33** · El usuario `e2e.admin.b` y la comprobación de la administración de prueba.
  - **Proyecto:** `e2e/` · **PR:** E11
  - **Archivos:** `e2e/preparar.ts`, `e2e/limpiar.ts`, `e2e/support/keycloak.ts` y `e2e/support/acceso.ts`; `e2e/README.md` (la sección «Usuarios»).
  - **Depende de:** ninguna (la ejecución, de B10 aplicado).
  - **Recursos:** entorno local y usuarios de Keycloak.
  - **Prueba primero:** una corrida de `npm test` en la que `preparar.ts` crea `e2e.admin.b` (`ADMIN_SISTEMA` de la administración de prueba B, con un TOTP conocido, por la API de administración de Keycloak) y `limpiar.ts` lo borra; y la **comprobación previa**: si la administración de Ipiales de prueba ya tiene vigencias, `preparar.ts` lo detecta por la API y se detiene con un mensaje claro que manda a correr `backend/dev/limpiar-vigencias.sh`.
- [ ] **T34** · Pruebas de la 001 que cambian.
  - **Proyecto:** `e2e/` · **PR:** E11
  - **Archivos:** `e2e/pruebas/estructura.spec.ts` y `e2e/pruebas/estructura.spec.ts-snapshots/` (las capturas del menú).
  - **Depende de:** T33.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero:** `estructura.spec.ts` (rojo con la opción 1.4 ya abierta: falla porque la prueba de «Próximamente» ya no la encuentra así): pasa a usar otra opción que siga en *Próximamente* y la muestra en la captura del menú del administrador. **La persona vuelve a aprobar esas capturas.**
- [ ] **T35** · Recorridos de la 1.4, el teclado del grupo de años, axe y capturas.
  - **Proyecto:** `e2e/` · **PR:** E11
  - **Archivos:** `e2e/pruebas/vigencias.spec.ts` y `e2e/pruebas/vigencias.spec.ts-snapshots/` (nuevos), `e2e/support/` (solo lo que falte) y `e2e/README.md` (la tabla «Qué prueban»).
  - **Depende de:** T33 y T34.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero:** `vigencias.spec.ts` (rojo mientras F9 se implementa): el estado vacío (**007-03**); crear la primera vigencia y la siguiente (**007-04 a 007-08**); abrir la preparación (**007-10**); la lista con «Habilitar liquidación» inhabilitado (**V‑03 · CA‑14 · 007-12**); `dev.consulta`, sin las acciones (**007-09**); `dev.liquidador`, con 403 (**007-02**); `e2e.admin.b`, sin ver las de Ipiales y con su propio 2026 (**CA‑12 · 007-18 y 007-19**). Los años salen de `creatableYears`, no del calendario de la prueba. **El teclado del grupo de años en el navegador real**: dentro del diálogo, las flechas cambian la opción y Tab entra solo a la marcada. Axe en cada pantalla, **con el contraste** (**007-21**), y capturas de referencia de las tres pantallas, con máscara sobre los años y las fechas, **que aprueba la persona**.
  - **Antes del PR:** `npm test` en verde, con el sistema local y la revisión de cada pantalla contra su *frame* de Figma. **Al terminar T33 a T35:** revisión adversarial y el PR (H3). Se integra **al final**.

## Cierre de la feature

- [ ] Actualizar documentación si aplica (funcional, arquitectura, ADR, glosario). El glosario («vigencia de la administración» y «lista de preparación») lo escribe el orquestador con el texto que entrega la sesión de T2.
- [ ] Revisión adversarial independiente de lo implementado; hallazgos resueltos o justificados. La del código de B6 comprueba los dos altos de la verificación acotada (T15).
- [ ] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra (la tabla «Trazabilidad de los criterios» del `plan.md`). Lo hace el orquestador.
- [ ] Compuertas de CI en verde y PR aprobado por un humano (H3), en cada PR.
- [ ] La persona publica `liv-components` 0.4.0 en npm, después de integrar 8f, y no se vuelve a abrir F9a hasta que esté publicada.
- [ ] Mover la feature a "Hecho" en `../../constitution/roadmap.md` y actualizar `ESTADO.md` (el orquestador).

## Mantenimiento (checklist recurrente)

- [ ] Si la 1.5.1 amplía la política RLS de `UPDATE` de `tax.administration_fiscal_year`, el `CHECK` de `assessments_enabled`, los permisos por columna de `liv_app` o `PreparationItemCheck`, actualizar a la vez `FiscalYearSchemaTest`, `LivAppGrantsTest`, `PreparationChecklistTest` y el *frame* de la lista de preparación.
- [ ] Si se amplía el rango de años de `tax.fiscal_year` (migración nueva), actualizar el `minimum` y el `maximum` de `tax.yaml`, la validación de `:year` del *frontend* y sus pruebas.
