# 007 · Vigencias fiscales — Tareas

_Checklist accionable derivada del `plan.md` (aprobado en H2, 2026-11-13). Los códigos `007-NN` son los criterios de `spec.md`. Tareas pequeñas y concretas; cada PR cabe en ~400 líneas, salvo el B6, que el plan deja como un solo PR y que tiene su corte previsto (véase «Mapa de los PR»); B5a, B5b y B7 se miden antes de abrirse. Marca `[x]` al completarlas. Cada tarea indica:_

- _su proyecto, su PR del plan y los archivos que puede tocar;_
- _de qué otras tareas depende;_
- _qué recursos compartidos usa: un contrato, la versión de `liv-components`, las migraciones o los archivos de *build* de su proyecto, el entorno local;_
- _la prueba o verificación que se escribe primero (TDD: rojo → verde → refactorizar). En las tareas de documentación, la «prueba primero» es una lista de comprobación escrita antes del texto, en el *scratchpad* de la sesión (no se sube), y corrida al final._

_Con eso el orquestador decide qué corre en paralelo (`liv-orquestador`)._

Es un cambio crítico (decisión 4 de la H1): antes de cada PR, `liv-revision-adversarial` con dos revisores de nivel alto y las cuatro lentes de `liv-revision-4r`, con el resumen en la descripción del PR; antes de cada *commit*, la lente de legibilidad. Un PR toca un solo proyecto (`AGENTS.md` §4.6). Modelo: nivel estándar para todas las tareas, salvo el ADR (T3 y T4), que es de nivel alto, igual que la revisión adversarial y la lente de riesgo de los cambios críticos (`AGENTS.md` §10). Las sesiones de las tareas **no escriben** `ESTADO.md`, el *roadmap*, el glosario, las marcas de esta lista ni los criterios de `spec.md`: se lo reportan al orquestador (`AGENTS.md` §7, ADR 0033). Las tareas de código no incluyen el glosario: los términos nuevos («vigencia de la administración» y «lista de preparación», plan, paso 1) los entrega la sesión de T2 en su reporte y el orquestador los escribe.

_La **enmienda 4** (T36 en adelante) está al final de este archivo, en su propia sección._

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
  - **Revisada por la enmienda 4:** T36. La descripción de `tax.fiscal_year` y una nota sobre el comentario de la migración `V202611131000` cambian en el ERD 2.4. La lista de comprobación de esta tarea no se reabre.
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
  - **Revisada por la enmienda 4:** T37 y T38. V‑02 y V‑05 del Doc. 01 cambian en la v0.6, el Doc. 02 pasa a la v0.4 y `AGENTS.md` §3.1 cambia dos números de versión.
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
  - **Revisada por la enmienda 4:** T41. `tax.yaml` pasa a 1.1.0 y `OpenApiContractTest` cambia los motivos, las extensiones y el orden de `creatableYears`.
  - **Al terminar T7:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B5a · Dominio: estados, agregado y política (`backend/`, rama `feat/007-dominio`) — plan, paso 5

Con D2 integrado. Java puro, con pruebas unitarias primero (`@DisplayName` con el código del criterio). No usa los modelos generados.

- [x] **T8** · `shared.RuleViolation`, `shared.NotFound`, `FiscalYearStatus` y `AdministrationFiscalYear`.
  - **Proyecto:** `backend/` · **PR:** B5a
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/`: `shared/RuleViolation.java`, `shared/NotFound.java`, `tax/domain/FiscalYearStatus.java` y `tax/domain/AdministrationFiscalYear.java`; en `backend/src/test/java/co/com/sapenlinea/liv/`: `shared/RuleViolationTest.java` y `tax/domain/AdministrationFiscalYearTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** D2 integrado.
  - **Recursos:** `shared` (B6 y B7 lo usan después).
  - **Prueba primero:** `RuleViolationTest` (código de regla, tipo `CONFLICT` o `UNPROCESSABLE`, detalles, excepción **sin marcar**, `NotFound` aparte) y `AdministrationFiscalYearTest`: **007-10** · `openPreparation()` desde `PLANEADA` da `EN_PREPARACION` y desde cualquier otro estado da `V‑06` con el estado actual; los cinco estados con los códigos del Doc. 01 §5; `assessmentsEnabled` no cambia nunca.
  - **Revisada por la enmienda 4:** T45. `RuleViolationTest` usa `GAP` como ejemplo y pasa a `PAST_LIMIT`. `AdministrationFiscalYearTest` no cambia.
- [x] **T9** · `FiscalYearOpeningPolicy`.
  - **Proyecto:** `backend/` · **PR:** B5a
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/tax/domain/FiscalYearOpeningPolicy.java` (más los tipos pequeños que necesite, p. ej. `CreatableYear`, `YearRelation` y `CreateBlocked`, en `tax/domain/`) y `backend/src/test/java/co/com/sapenlinea/liv/tax/domain/FiscalYearOpeningPolicyTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T8.
  - **Recursos:** ninguno.
  - **Prueba primero:** `FiscalYearOpeningPolicyTest` comprueba el código, el motivo y las extensiones de cada combinación, **en el orden del plan** (el primero que falla es el que se responde): **V‑01 · 007-04 y 007-05** (ya existe), **V‑02 · 007-07** `HISTORICAL` (`oldestYear`), **V‑05 · 007-08** `FIRST_YEAR` (`currentYear`, `nextYear`), **V‑02 · V‑05 · 007-06** `GAP` (`requiredYear`) y `FUTURE_LIMIT` (`maxYear`, H2‑6); un caso por par de reglas que se puede cumplir a la vez (p. ej. 2029 con la más reciente 2026 da `GAP`); `creatableYears` con sus fechas y `relation` (`CURRENT`, `NEXT` y **`PAST`**: la más reciente es 2026, estamos en 2028 y nadie creó 2027, así que el único año que se puede crear es 2027 con V‑02) y `createBlocked` con `FUTURE_LIMIT` cuando no queda ninguno, siempre dentro de la tabla nacional.
  - **Revisada por la enmienda 4:** T42. `FiscalYearOpeningPolicyTest` se reescribe: V‑02 ya no rechaza, V‑05 es un rango desde 2002, `creatableYears` va de mayor a menor y aparece `ALL_CREATED`. Salen `HISTORICAL`, `FIRST_YEAR` y `GAP`.
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

- [x] **T11** · Errores de negocio en la API, `liv.business-zone` y las flechas de `platform`.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/platform/web/ProblemDetailsHandler.java`, `backend/src/main/resources/application.yml` (la propiedad `liv.business-zone: America/Bogota`), y en `backend/src/test/java/co/com/sapenlinea/liv/`: `platform/ErrorResponsesTest.java` y `architecture/ModularityTest.java`; `backend/CHANGELOG.md`.
  - **Depende de:** T8 (las excepciones de `shared`) y B3, B4, B5a (T8 y T9) y B5b integrados.
  - **Recursos:** `ProblemDetailsHandler` y `ModularityTest` (compartidos con B7: en serie).
  - **Prueba primero:** `ErrorResponsesTest`: `RuleViolation` `CONFLICT` da 409 y `UNPROCESSABLE` da 422, con su `code` y sus extensiones en el Problem Details; `NotFound` da 404 con `SYS-404` y un cuerpo idéntico sea cual sea el motivo (CA‑12); `ModularityTest`: `platform` depende solo de `shared` (`containsExactly("shared")`).
  - **Revisada por la enmienda 4:** T45. `ErrorResponsesTest` usa `GAP` como ejemplo y pasa a `PAST_LIMIT`.
- [x] **T12** · Casos de uso, `BusinessYear`, el puerto `FiscalYears` y los eventos.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/tax/`: `FiscalYearCreated.java` y `FiscalYearStatusChanged.java` (paquete raíz, como `core.SessionStarted`) y, en `application/`, `BusinessYear.java`, `FiscalYears.java` (puerto, con `lockForCreation`), `ListFiscalYears.java`, `GetFiscalYear.java`, `CreateFiscalYear.java` y `OpenFiscalYearPreparation.java`; en `backend/src/test/java/co/com/sapenlinea/liv/tax/application/`: `CreateFiscalYearTest.java`, `ListFiscalYearsTest.java` y `BusinessYearTest.java` (esta última, en el contexto real); `ModularityTest` (`tax` depende de `platform` y `shared`); `backend/CHANGELOG.md`.
  - **Depende de:** T11, T9 y T10.
  - **Recursos:** `ModularityTest`.
  - **Prueba primero:** con un doble del puerto, **007-04 a 007-08** (cada rechazo, con su código y extensiones) y **007-10** en `CreateFiscalYearTest` y `OpenFiscalYearPreparationTest`; **la frontera del año**, en `CreateFiscalYearTest` y `ListFiscalYearsTest` con `Clock.fixed(2027-01-01T04:30Z)` (31 de diciembre de 2026 a las 23:30 en Bogotá): se pueden crear 2026 (`CURRENT`) y 2027 (`NEXT`), y 2028 se rechaza con `V‑05` `FUTURE_LIMIT` y su `maxYear`; la lista nunca ofrece un año que la creación rechace; `canCreate` según `tax.fiscal-year.create`; `BusinessYearTest` comprueba, con el contexto real, que usa la zona `America/Bogota` (falla si fuera UTC); cada caso de uso lleva su `@RequiresPermission` (`AuthorizationRulesTest` ya lo exige) y los eventos llevan `eventId`, `administrationId`, `userId`, el `id` de la fila, el año, el estado anterior (nulo al crear), el nuevo, el instante y el `correlationId`.
  - **Revisada por la enmienda 4:** T43. `CreateFiscalYearTest` y `ListFiscalYearsTest` cambian la frontera del año (ahora 2027 es `NEXT` y la lista va de 2027 a 2002) y quitan los casos de V‑02 y de `FIRST_YEAR`. `BusinessYearTest` no cambia.
- [x] **T13** · Persistencia JPA: entidades, repositorios y el adaptador de `FiscalYears`.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/tax/infrastructure/persistence/`: la entidad de la fila de la administración, la de solo lectura del año nacional, sus repositorios y el adaptador `JpaFiscalYears`; en `backend/src/test/java/co/com/sapenlinea/liv/tax/infrastructure/persistence/`: `JpaFiscalYearsTest.java` (Testcontainers y el adaptador real); `backend/CHANGELOG.md`.
  - **Depende de:** T12.
  - **Recursos:** ninguno.
  - **Prueba primero:** `JpaFiscalYearsTest`, con las pruebas que demuestran **los dos altos de la verificación acotada** (se comprueban en la revisión adversarial del código de este paso; véase abajo): cada una fuerza su intercalación.
    - **`UNIQUE` real (V‑01 · 007-05):** el dueño de los objetos inserta la vigencia 2026; `save` de 2026 sin pasar por el bloqueo da `RuleViolation` `V‑01`, no `DataIntegrityViolationException` (por el SQLState `23505`; ajuste de la H3 en `plan.md`);
    - **apertura que pierde la carrera (007-10):** con dos `TransactionTemplate`, T1 lee la fila, T2 la abre y confirma, y el `UPDATE` de T1 afecta 0 filas y da `V‑06` con el estado `EN_PREPARACION` (nunca 503); un reintento después de un éxito también da `V‑06`;
    - **apertura que gana:** la respuesta dice `EN_PREPARACION` y un contador de sentencias de Hibernate comprueba que no emite ningún `UPDATE` propio (entidad solo de lectura e inserción: `id`, `status`, `assessments_enabled` y `created_at` con `insertable = false` y `@Generated`, y todas las columnas `updatable = false`);
    - **el bloqueo (007-05):** dos instancias de `CreateFiscalYear`, cada una con su `Clock.fixed` a un lado de la medianoche del 31 de diciembre en Bogotá, sobre el adaptador real; un espía detiene a A justo después de obtener el bloqueo y B sigue esperando durante un tiempo límite; al liberar a A el resultado es {2026} y B recibe `V‑02` `GAP`; **la misma prueba con un `lockForCreation` vacío da {2026, 2028}**, con lo que se demuestra que la prueba detecta la falta del bloqueo;
    - `CreateFiscalYear` y `OpenFiscalYearPreparation` en `READ_COMMITTED`; una vigencia de otra administración da `NotFound` (CA‑12).
  - **Revisada por la enmienda 4:** T44. Las dos pruebas de la medianoche de `JpaFiscalYearsRacesTest` (`theLockSerializesTheCreationsAcrossMidnight` y `withoutTheLockTheTestSeesTheGap`) pierden su premisa (`V‑02` `GAP`) y las reemplazan dos pruebas de caracterización del bloqueo con el mismo año. `theRealUniqueBecomesV01`, `theLockIsOfTheAdministration` y las pruebas de la apertura no cambian.
- [x] **T14** · `FiscalYearsController` y las pruebas de la API.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/tax/api/FiscalYearsController.java` (sin lógica, máximo 200 líneas) y `backend/src/test/java/co/com/sapenlinea/liv/tax/FiscalYearsApiTest.java` (`extends PlatformWebTest`); `backend/CHANGELOG.md`.
  - **Depende de:** T13 y T7 (la interfaz generada).
  - **Recursos:** contrato `tax`.
  - **Prueba primero:** `FiscalYearsApiTest`, antes del controlador y fallando por una aserción: las respuestas y errores del contrato; **007-01 y 007-03** (lista, orden y estado vacío con las opciones de `creatableYears`); **007-02 y 007-09** (401 y 403 por rol); una vigencia de otra administración y un año inexistente dan **el mismo 404**; **CA‑12 · 007-18 y 007-19** por la API; **V‑01 · 007-05** y **007-10** por la API; dos creaciones y dos aperturas a la vez, solo como prueba de humo (nunca 500 ni 503). Cada prueba que necesite una administración sin vigencias la crea con el dueño de los objetos, porque `liv_app` no puede borrar y el contenedor se comparte.
  - **Revisada por la enmienda 4:** T42 (el controlador pasa `minYear`) y T45. `FiscalYearsApiTest` cambia los rechazos, el orden de `creatableYears` y el estado `ALL_CREATED`, y todos sus años pasan a calcularse con `currentYear()`.
- [x] **T15** · Cierre del PR: ejemplo hexagonal en la *skill* y registro.
  - **Proyecto:** `backend/` · **PR:** B6
  - **Archivos:** `backend/.claude/skills/liv-endpoint/SKILL.md` (agrega el ejemplo del módulo hexagonal `tax`, como pide la propia *skill*) y `backend/CHANGELOG.md`.
  - **Depende de:** T14.
  - **Recursos:** `backend/CHANGELOG.md`.
  - **Prueba primero:** `./mvnw verify` en verde, con el Spotless y las pruebas guardianas (`ArchitectureRulesTest`, `CodeSizeGuardTest`, `ModularityTest`, `ContractRulesTest`).
  - **Revisión adversarial del código de este PR:** además de lo normal, **comprueba los dos altos de la verificación acotada** (plan, «Revisión»): (1) que las pruebas del adaptador de T13 fuercen cada intercalación (el `UNIQUE` real, la apertura que pierde la carrera y el bloqueo, con su variante sin bloqueo) y (2) que `relation` admita `PAST` con su prueba (T9 y T12). Dos revisores ciegos de nivel alto; si aparece otro hallazgo alto, se escala a la persona.
  - **Revisada por la enmienda 4:** T46. La tabla de la *skill* `liv-endpoint` dice que `FiscalYearOpeningPolicy` aplica V‑01, V‑02 y V‑05, y pasa a V‑01 y V‑05.

## PR B7 · Auditoría (`backend/`, rama `feat/007-auditoria`) — plan, paso 7

Con B6 integrado.

- [x] **T16** · `AuditLog.Entry` con `previousValues`.
  - **Proyecto:** `backend/` · **PR:** B7
  - **Archivos:** `backend/src/main/java/co/com/sapenlinea/liv/audit/application/AuditLog.java`, `backend/src/main/java/co/com/sapenlinea/liv/audit/infrastructure/JdbcAuditLog.java` y los siete usos actuales de `Entry` (`audit/application/RecordSessionEvents.java` y los demás que el compilador señale en `audit/`), y las pruebas de `backend/src/test/java/co/com/sapenlinea/liv/audit/` que los usan; `backend/CHANGELOG.md`.
  - **Depende de:** B6 integrado.
  - **Recursos:** `AuditLog` (solo B7).
  - **Prueba primero:** una prueba de `JdbcAuditLog` (p. ej. en `audit/AuditRows.java` y `SessionAuditTest.java`) comprueba que `previous_values` se escribe cuando viene y que las filas de sesión siguen con `previous_values` nulo; `previousValues` admite nulo (el mapa se copia solo si existe); el *javadoc* de `entityName` agrega `tax.administration_fiscal_year`.
- [x] **T17** · Escucha de los eventos de la vigencia y auditoría.
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
- [x] **T23** · **8e** · `Button` con `disabledReason` (crítico).
  - **Proyecto:** `ui/` · **PR:** 8e
  - **Archivos:** `ui/projects/liv-components/src/lib/button/` (`button.component.ts`, `.html`, `.css`, `.spec.ts` y `button.stories.ts`) y `ui/CHANGELOG.md`.
  - **Depende de:** ninguna.
  - **Recursos:** `ui/CHANGELOG.md`.
  - **Prueba primero:** `disabledReason` solo cuenta con `disabled`; con motivo, el botón usa `aria-disabled="true"` y sigue recibiendo el foco, con su estado propio en el anfitrión (p. ej. `data-state="disabled"`) para el aspecto inhabilitado; el aspecto es igual con motivo y sin él; no emite clic con el ratón, con Enter, con Espacio ni como `submit` de un formulario; `aria-describedby` apunta al texto visible; `reasonAlign` (`start` o `end`); sin `disabled` el motivo no se muestra; `loading` conserva la precedencia de hoy; las reglas de *hover* y de «cargando» (`.button[aria-disabled='true']`) excluyen ese estado, comprobado por el texto de las reglas porque jsdom no evalúa `:hover` (la revisión manual en Storybook lo confirma); axe. Diseño: nodo 65:456.
  - **Al terminar:** revisión adversarial y las cuatro lentes (la de riesgo, de nivel alto).
- [x] **T24** · **8f** · `Dialog` con cuerpo, tamaño y tono (H2‑11, crítico) y versión 0.4.0.
  - **Proyecto:** `ui/` · **PR:** 8f
  - **Archivos:** `ui/projects/liv-components/src/lib/dialog/` (`dialog.component.ts`, `.html`, `.css`, `.spec.ts` y `dialog.stories.ts`), `ui/projects/liv-components/package.json` (la versión, con `npm run version:minor`), `ui/package-lock.json` (si el comando lo cambia) y `ui/CHANGELOG.md` (la sección `## [0.4.0] — <fecha>` con las entradas de 8a a 8f; `npm run release:check` la exige).
  - **Depende de:** T21 (`RadioGroup`) y, para integrarse, T18 a T23.
  - **Recursos:** `ui/CHANGELOG.md`, la versión de `liv-components` y su publicación en npm.
  - **Prueba primero:** el cuerpo es una ranura con nombre (`<ng-content select="[livDialogBody]">`) y el contenido sin selector sigue yendo a las acciones; `size` `md` (440 px, por omisión) o `lg` (520 px); `tone` `warning` (por omisión) o `primary`; **una prueba del uso por omisión** (sin cuerpo, sin `size` ni `tone`) fija 440 px, el tono de advertencia y que todo el contenido sin selector va a las acciones (es el uso del aviso de sesión de la 001); la trampa de foco cuenta, de cada grupo de radios, el marcado o, si no hay ninguno, el primero habilitado al avanzar y el último al retroceder, y excluye los radios de un `fieldset` inhabilitado, con la misma regla para el primer foco al abrir; **las pruebas de teclado de un `RadioGroup` dentro de un `Dialog`** (uno y dos años, con y sin opción marcada); axe. Diseño: conjunto 73:482 (65:472 y 73:467).
  - **Al terminar:** revisión adversarial y las cuatro lentes (la de riesgo, de nivel alto); `npm run release:check` en verde. **No se publica**: la persona lo hace después de integrar el PR (`ui/README.md` › «Publicar una versión»). El PR dice que **F9a corre la prueba propia del aviso de sesión del frontend con 0.4.0**.

## PR F9a · Lista y creación (`frontend/`, rama `feat/007-front-lista`) — plan, paso 9

*Skill* `liv-pantalla`. **Requiere** el contrato `tax` publicado como *release* `contract-tax-v1.0.0` (B4 integrado), `liv-components` 0.4.0 publicada en npm (8f integrado y publicada por la persona) y el *frame* 75:4479 (la etiqueta de un año pasado, `relation: PAST`), **aprobado el 2026-11-13**. El *frontend* no calcula reglas: todo lo que muestra sobre años, fechas y motivos viene del contrato, y ninguna pantalla usa `new Date()` para decidir.

- [x] **T25** · Contrato adoptado, versión nueva de la librería y `FiscalYearsService`.
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/api-spec/versions.json` y `frontend/api-spec/tax.yaml` (copia fijada), el cliente generado (`frontend/src/app/api/tax/`, generado por `npm run api:update`), `frontend/package.json` y `frontend/package-lock.json` (`liv-components` 0.4.0 exacta), `frontend/src/app/features/fiscal-years/fiscal-years.service.ts` y su `.spec.ts`, y `frontend/CHANGELOG.md`.
  - **Depende de:** B4 integrado y *release* publicada, 8f integrado y 0.4.0 publicada; para integrarse el PR, también B6 integrado.
  - **Recursos:** contrato `tax` (`frontend/api-spec/`), versión de `liv-components` y `package.json`.
  - **Prueba primero:** `npm run api:verify` en verde (SHA‑256 de la *release*); `fiscal-years.service.spec.ts` (rojo): llama a los cuatro métodos sobre el cliente generado y expone `canCreate`, `creatableYears` y `createBlocked` tal como vienen. **Antes de seguir**, `npm test` completo con 0.4.0, citando en el PR la prueba propia del aviso de sesión (`features/shell/session-warning-dialog.spec.ts`): `Dialog` por omisión no cambió.
  - **Revisada por la enmienda 4:** T49 y T54. El contrato pasa a 1.1.0 y `liv-components` a 0.5.0; `fiscal-years.service.spec.ts` solo cambia si el tipo nuevo lo exige.
- [x] **T26** · La lista de vigencias, con sus siete estados.
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/src/app/features/fiscal-years/fiscal-years.ts`, `.html`, `.css` y `fiscal-years.spec.ts`, `frontend/src/app/app.routes.ts` y `frontend/src/app/app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.4` con `capabilityGuard` y `capability: '1.4'`, antes de la genérica), `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** T25.
  - **Recursos:** `app.routes.ts` y `accessibility.spec.ts` (F9b los toca después: en serie).
  - **Prueba primero:** `fiscal-years.spec.ts` (rojo): **007-01** (la lista, del año más reciente al más antiguo, con las fechas con el formateador de la librería); **007-03** (el estado vacío con `ResultState`, y la acción solo con `canCreate`); **007-09** (`canCreate` en falso, sin la acción); `createBlocked` (el botón inhabilitado con su motivo, `disabledReason`); cargando y error (`ResultState` y `Skeleton`); axe de la ruta en `accessibility.spec.ts` (**007-21**), con el botón inhabilitado con motivo; `app.routes.spec.ts`: la ruta, y el 403 por URL (**007-02**) y «Próximamente» mientras la opción siga así. Diseño: *frames* 66:1967 a 66:3493.
  - **Revisada por la enmienda 4:** T52 y T53. El estado vacío dice el rango de años (007-03) y el botón inhabilitado usa el motivo `ALL_CREATED` (007-23); `accessibility.spec.ts` cambia el `createBlocked` de sus pruebas de axe.
- [x] **T27** · El diálogo de creación y los rechazos.
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/src/app/features/fiscal-years/create-fiscal-year-dialog.ts`, `.html`, `.css` y `.spec.ts` (nombres a decidir por la sesión dentro de `features/fiscal-years/`), `fiscal-years.ts` y `fiscal-years.spec.ts` (para abrir el diálogo y releer la lista), `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** T26 (el *frame* 75:4479 ya está aprobado, el 2026-11-13).
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero:** las pruebas del diálogo (rojo): las opciones salen de `creatableYears` con su etiqueta (incluida la de un año `PAST`) y sus fechas; lleva la advertencia de H2‑6 (**007-04**); el grupo de años siempre tiene una opción marcada mientras está abierto; mientras se envía, el botón está en `loading` y Escape no cierra; cada rechazo con su código y sus extensiones del Problem Details (**V‑01 · 007-05**, **V‑02 · 007-06 y 007-07**, **V‑05 · 007-08** y `V‑06`), con la alerta creada de nuevo en cada intento para que se anuncie aunque el texto se repita; tras un `V‑01`, la lista se vuelve a leer y queda marcado el primer año que siga disponible, y si no queda ninguno (`createBlocked`) el diálogo se cierra y la lista muestra el botón inhabilitado con su motivo; un 403 lleva a «Sin permiso» (H2‑14, 001-16); axe con el diálogo abierto y con un rechazo (**007-21**). Diseño: *frames* 67:3040, 67:3163 y 67:3284, y el 75:4479 (aprobado el 2026-11-13).
  - **Revisada por la enmienda 4:** T50, T51 y T53. El `RadioGroup` pasa a un `Select`; ya no hay siempre una opción marcada (007-22 la reemplaza); cambian las opciones, la advertencia y los mensajes de rechazo, y salen `GAP`, `HISTORICAL` y `FIRST_YEAR`. El frame 75:4479 está retirado (E4‑4).
- [x] **T28** · Fecha larga para fechas sueltas (H2‑13).
  - **Proyecto:** `frontend/` · **PR:** F9a
  - **Archivos:** `frontend/AGENTS.md` (solo la fila «Localización») y `frontend/.claude/skills/liv-pantalla/SKILL.md` (solo el paso 5) y `frontend/CHANGELOG.md`.
  - **Depende de:** T26 (la primera pantalla que usa el formato).
  - **Recursos:** ninguno.
  - **Prueba primero:** comprobación escrita antes del texto: las dos reglas admiten la fecha larga («1 de enero de 2026») para fechas sueltas y piden `dd/mm/aaaa` en tablas de consulta y exportaciones, y dicen que el formateador sale de `liv-components`; `npm run lint` y `npm run format:check` siguen en verde.
  - **Validación en local, antes del PR (ADR 0030):** con el sistema levantado y la rama de B10 aplicada, se ven las pantallas de la lista y del diálogo, se comparan con Figma y se corre `e2e/`; su resultado se cita en el PR.

**Al terminar T25 a T28:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR F9b · Detalle y preparación (`frontend/`, rama `feat/007-front-detalle`) — plan, paso 9

Con F9a integrado.

- [x] **T29** · El detalle de la vigencia y su lista de preparación.
  - **Proyecto:** `frontend/` · **PR:** F9b
  - **Archivos:** `frontend/src/app/features/fiscal-years/fiscal-year-detail.ts`, `.html`, `.css` y `fiscal-year-detail.spec.ts`, `frontend/src/app/features/fiscal-years/fiscal-years.service.ts` (solo lo que falte) y su `.spec.ts`, `frontend/src/app/app.routes.ts` y `app.routes.spec.ts` (la ruta `${OPTION_ROUTE}/1.4/:year` con `capabilityGuard` y `capability: '1.4'`, antes de la genérica), `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** F9a integrado.
  - **Recursos:** `app.routes.ts` y `accessibility.spec.ts`.
  - **Prueba primero:** `fiscal-year-detail.spec.ts` (rojo): **007-10** (la acción «Abrir preparación» solo con `OPEN_PREPARATION`; sin ella en cualquier otro estado, y un valor desconocido de `availableActions` se ignora; un `V‑06` muestra su alerta encima de los datos y vuelve a leer la vigencia); **007-11** (los nueve puntos, con el título sacado de su `code`, la frase de obligatorios y opcionales armada con `required` y «Opción aún no disponible» como pendiente); **V‑03 · CA‑14 · 007-12** («Habilitar liquidación» inhabilitado con `disabledReason` a partir de `missing`); el parámetro `:year` no numérico o fuera de 1999–2100 se muestra «no encontrada» **sin llamar a la API**, igual que el 404; otro error (500 o tiempo agotado) con el `ResultState` de error; cargando; axe de la ruta, con el botón con motivo (**007-21**); `app.routes.spec.ts`: el 403 por URL (**007-02**). Diseño: *frames* 68:3460 a 68:4474.
- [x] **T30** · `Shell`: la opción del menú activa en sus rutas hijas.
  - **Proyecto:** `frontend/` · **PR:** F9b
  - **Archivos:** `frontend/src/app/features/shell/shell.ts` y `shell.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** ninguna (mismo PR que T29).
  - **Recursos:** ninguno.
  - **Prueba primero:** `shell.spec.ts` (rojo): en `/opcion/1.4/2026` «Vigencias fiscales» queda activa y «Administración» abierta (hoy solo se marca con la ruta exacta de la opción).
  - **Validación en local, antes del PR:** igual que la de T28.

**Al terminar T29 y T30:** revisión adversarial, las cuatro lentes y el PR (H3).

## PR B10 · Abrir la opción del menú y limpiar las vigencias (`backend/`, rama `feat/007-menu`) — plan, paso 10

Con B6 integrado (para escribirse); se integra después de B6, B7 y F9b. Su rama se aplica en local para validar F9a y F9b.

- [x] **T31** · La opción 1.4 pasa a `AVAILABLE`.
  - **Proyecto:** `backend/` · **PR:** B10
  - **Archivos:** `backend/src/main/resources/platform/menu-catalog.yaml` (solo la opción 1.4), `backend/src/test/java/co/com/sapenlinea/liv/platform/navigation/MenuCatalogTest.java` y `backend/CHANGELOG.md`.
  - **Depende de:** B6 integrado.
  - **Recursos:** `menu-catalog.yaml` y `MenuCatalogTest` (solo B10).
  - **Prueba primero:** `MenuCatalogTest` (rojo): **007-01** · la opción 1.4 está `AVAILABLE` con el permiso `tax.fiscal-year.read` y las demás siguen `COMING_SOON`.
- [x] **T32** · `backend/dev/limpiar-vigencias.sh` (H2‑9).
  - **Proyecto:** `backend/` · **PR:** B10
  - **Archivos:** `backend/dev/limpiar-vigencias.sh` (nuevo, junto a `check.sh`), su prueba de escenarios con un `docker` falso (en `backend/dev/test/`, como `contracts/scripts/test/fake-gh.sh`), `backend/dev/README.md` y `backend/CHANGELOG.md`. `tools/dev/` puede invocarlo, pero ese cambio es de la raíz y queda fuera de esta feature.
  - **Depende de:** T31 (mismo PR).
  - **Recursos:** `backend/dev/`.
  - **Prueba primero:** la prueba de escenarios (rojo), con el `docker` falso: el *script* solo ejecuta `docker compose -f dev/compose.yaml exec postgres psql …`, toca únicamente `tax.administration_fiscal_year`, y **se niega** (código de salida distinto de 0 y sin ejecutar nada) si el contenedor no es el de desarrollo.
  - **Al terminar T31 y T32:** revisión adversarial y las cuatro lentes, y el PR (H3). **Se integra después de B6, B7 y F9b.**

## PR E11 · Pruebas de navegador (`e2e/`, rama `feat/007-e2e`) — plan, paso 11

*Skill* `liv-prueba-e2e`. Las pruebas se escriben en rojo mientras se implementa F9; se corren al final, con el sistema levantado (`README.md` › «Ejecutar LIV en local») y `backend/dev/limpiar-vigencias.sh` ejecutado por la persona. `e2e/` no lo llama (ADR 0030, H2‑9).

- [x] **T33** · El usuario `e2e.admin.b` y la comprobación de la administración de prueba.
  - **Proyecto:** `e2e/` · **PR:** E11
  - **Archivos:** `e2e/preparar.ts`, `e2e/limpiar.ts`, `e2e/support/keycloak.ts` y `e2e/support/acceso.ts`; `e2e/README.md` (la sección «Usuarios»).
  - **Depende de:** ninguna (la ejecución, de B10 aplicado).
  - **Recursos:** entorno local y usuarios de Keycloak.
  - **Prueba primero:** una corrida de `npm test` en la que `preparar.ts` crea `e2e.admin.b` (`ADMIN_SISTEMA` de la administración de prueba B, con un TOTP conocido, por la API de administración de Keycloak) y `limpiar.ts` lo borra; y la **comprobación previa**: si la administración de Ipiales de prueba ya tiene vigencias, `preparar.ts` lo detecta por la API y se detiene con un mensaje claro que manda a correr `backend/dev/limpiar-vigencias.sh`.
- [x] **T34** · Pruebas de la 001 que cambian.
  - **Proyecto:** `e2e/` · **PR:** E11
  - **Archivos:** `e2e/pruebas/estructura.spec.ts` y `e2e/pruebas/estructura.spec.ts-snapshots/` (las capturas del menú).
  - **Depende de:** T33.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero:** `estructura.spec.ts` (rojo con la opción 1.4 ya abierta: falla porque la prueba de «Próximamente» ya no la encuentra así): pasa a usar otra opción que siga en *Próximamente* y la muestra en la captura del menú del administrador. **La persona vuelve a aprobar esas capturas.**
- [x] **T35** · Recorridos de la 1.4, el teclado del grupo de años, axe y capturas.
  - **Proyecto:** `e2e/` · **PR:** E11
  - **Archivos:** `e2e/pruebas/vigencias.spec.ts` y `e2e/pruebas/vigencias.spec.ts-snapshots/` (nuevos), `e2e/support/` (solo lo que falte) y `e2e/README.md` (la tabla «Qué prueban»).
  - **Depende de:** T33 y T34.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero:** `vigencias.spec.ts` (rojo mientras F9 se implementa): el estado vacío (**007-03**); crear la primera vigencia y la siguiente (**007-04 a 007-08**); abrir la preparación (**007-10**); la lista con «Habilitar liquidación» inhabilitado (**V‑03 · CA‑14 · 007-12**); `dev.consulta`, sin las acciones (**007-09**); `dev.liquidador`, con 403 (**007-02**); `e2e.admin.b`, sin ver las de Ipiales y con su propio 2026 (**CA‑12 · 007-18 y 007-19**). Los años salen de `creatableYears`, no del calendario de la prueba. **El teclado del grupo de años en el navegador real**: dentro del diálogo, las flechas cambian la opción y Tab entra solo a la marcada. Axe en cada pantalla, **con el contraste** (**007-21**), y capturas de referencia de las tres pantallas, con máscara sobre los años y las fechas, **que aprueba la persona**.
  - **Revisada por la enmienda 4:** T55 a T57. `vigencias.spec.ts` cambia: los años salen de `relation` y de su posición en la lista, el grupo de años pasa a un `Select`, y tres capturas se vuelven a aprobar y una se agrega. T33 y T34 no cambian.
  - **Antes del PR:** `npm test` en verde, con el sistema local y la revisión de cada pantalla contra su *frame* de Figma. **Al terminar T33 a T35:** revisión adversarial y el PR (H3). Se integra **al final**.

## Cierre de la feature

- [x] Actualizar documentación si aplica (funcional, arquitectura, ADR, glosario). El glosario («vigencia de la administración» y «lista de preparación») lo escribe el orquestador con el texto que entrega la sesión de T2.
- [x] Revisión adversarial independiente de lo implementado; hallazgos resueltos o justificados. La del código de B6 comprueba los dos altos de la verificación acotada (T15).
- [x] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra (la tabla «Trazabilidad de los criterios» del `plan.md`). Lo hace el orquestador.
- [x] Compuertas de CI en verde y PR aprobado por un humano (H3), en cada PR.
- [x] La persona publica `liv-components` 0.4.0 en npm, después de integrar 8f, y no se vuelve a abrir F9a hasta que esté publicada.
- [x] Mover la feature a "Hecho" en `../../constitution/roadmap.md` y actualizar `ESTADO.md` (el orquestador).

## Mantenimiento (checklist recurrente)

- [ ] Si la 1.5.1 amplía la política RLS de `UPDATE` de `tax.administration_fiscal_year`, el `CHECK` de `assessments_enabled`, los permisos por columna de `liv_app` o `PreparationItemCheck`, actualizar a la vez `FiscalYearSchemaTest`, `LivAppGrantsTest`, `PreparationChecklistTest` y el *frame* de la lista de preparación.
- [ ] Si se amplía el rango de años de `tax.fiscal_year` (migración nueva), actualizar el `minimum` y el `maximum` de `tax.yaml`, la validación de `:year` del *frontend* y sus pruebas.

---

## Enmienda 4 · Años de 2002 al año siguiente al año en curso (T36 a T57)

_Tareas de la enmienda 4, derivadas de «Enmienda 4» de `plan.md` (pasos 12 a 20, aprobada en H2 el 2026-11-16, sobre #204; el paso 17 salió del plan, H2‑16) y de los criterios «(enmienda 4)» de `spec.md`: 007-03, 007-04, 007-06 a 007-08, 007-21, 007-22 y 007-23. Mismo formato y mismas reglas que T1 a T35: cada tarea dice su proyecto, su PR, sus archivos, de qué depende, qué recursos usa y qué prueba se escribe primero. Nivel estándar para todas; la revisión de los cambios críticos, de nivel alto (`AGENTS.md` §10). **La revisión de cada PR de la enmienda es la que fija su plan, según lo que toca** (`AGENTS.md` §4.3): crítico (4U, 4B1 y 4B2) o normal (4F0, 4F y 4E); para esta sección, eso precede a la regla general de la cabecera de este archivo, que pide las cuatro lentes en cada PR. Las sesiones de las tareas **no escriben** `ESTADO.md`, el *roadmap*, el glosario, las marcas de esta lista ni los criterios de `spec.md` (`AGENTS.md` §7, ADR 0033): se lo reportan al orquestador._

**El paso 12 (Figma) ya está hecho y aprobado el 2026-11-16** (`Select` 81:490 y 81:629; pantallas 67:3040, 82:5802, 67:3163, 67:3284, 66:2793 y 66:2513; 75:4479 retirado): no tiene tarea. El paso 17 salió del plan, así que tampoco. Las tareas del backend de la enmienda cumplen `./mvnw verify` por PR; las del *frontend*, `npm run lint`, `npm run format:check`, `npm test` y `npm run build`; las de `ui/`, `npm run lint`, `npm run format:check`, `npm test` y `npm run pack`.

**Lo que no se toca:** no hay migraciones, así que no aplican `FlywayProfilesTest`, `FiscalYearSchemaTest` ni `LivAppGrantsTest`; no hay casos de uso ni permisos nuevos, así que no cambian `AuthorizationRules` ni `UseCasePermissionsTest`; y tampoco cambian `BusinessYearTest`, `JpaFiscalYearsTest`, `FiscalYearsBeansTest`, `TaxJsonConfigurationTest` ni `FiscalYearsContractParityTest` (este compara estados, no motivos). Siguen en verde y se corren en el `verify`. En el *frontend*, `scripts/lint-rules.test.mjs` y `eslint.config.js` no cambian: la regla que prohíbe `<select>` en las pantallas sigue igual, porque la pantalla usa `<liv-select>`. `RadioGroup` y `RadioOption` se quedan en `liv-components` y sus pruebas no cambian.

### Mapa de los PR (enmienda 4)

| PR | Paso | Rama | Proyecto | Tareas | Depende para **escribirse** | Depende para **integrarse** | Tipo y revisión |
|---|---|---|---|---|---|---|---|
| 4D · Documentos | 13 | `docs/007-e4-documentos` | raíz | T36 a T38 | la H2 de la enmienda (ya) | nada: va primero | Documentos funcionales (proceso de `AGENTS.md` §3.1, nunca flujo directo). Legibilidad por *commit* (`liv-revisor-rapido`), `liv-revisor` de coherencia entre documentos y aprobación de la persona como responsable funcional |
| 4F0 · Año elegido sin años pasados | 14 | `feat/007-e4-front-previo` | `frontend/` | T39, T40 | la H2 (ya); compatible con los contratos 1.0.0 y 1.1.0 | nada, pero **antes** de 4B2 (riesgo 6 de la spec) | Normal, de una feature crítica: adversarial con dos `liv-revisor` y legibilidad |
| 4U · `Select` y 0.5.0 | 18 | `feat/007-e4-ui-select` | `ui/` | T47, T48 | el diseño aprobado (paso 12, ya) | nada. **La persona publica 0.5.0** después de integrar | Crítico (`ui/**`): adversarial con dos `liv-revisor-critico` y las cuatro lentes |
| 4B1 · Contrato 1.1.0 | 15 | `feat/007-e4-contrato` | `backend/` | T41 | 4D integrado (el motivo `PAST_LIMIT` sale del Doc. 01 v0.6) | **solo con 4B2 aprobado** sobre su rama. Al integrarse, `backend-main` publica `contract-tax-v1.1.0` | Crítico (`backend/contracts/**`): adversarial con dos `liv-revisor-critico` y las cuatro lentes |
| 4B2 · La regla nueva | 16 | `feat/007-e4-regla`, **apilada sobre la rama de 4B1** | `backend/` | T42 a T46 | 4B1 (su rama) | 4F0 y 4B1 integrados, y 4F y 4E **aprobados**; se integra primero de los tres (H2‑19) | Crítico (`backend/**/liv/tax/**`): adversarial con dos `liv-revisor-critico` y las cuatro lentes |
| 4F · El diálogo con `Select` | 19 | `feat/007-e4-front-dialogo` | `frontend/` | T49 a T54 | 4F0 integrado (mismos archivos), 0.5.0 publicada en npm y 007-22 como quedó en H2‑18 (ya). T54 espera la *release* `contract-tax-v1.1.0` | **justo después de 4B2**, nunca antes (H2‑19) | Normal: adversarial con dos `liv-revisor` y legibilidad |
| 4E · Pruebas de navegador | 20 | `feat/007-e4-e2e` | `e2e/` | T55 a T57 | nada (se escriben en rojo mientras se hace 4F); se **corren** con 4B2 aplicado en local | **justo después de 4F** (H2‑19), en verde contra el entorno local | Normal: adversarial con dos `liv-revisor` y legibilidad |

**Tamaño.**
- **4B2** se mide antes de abrir el PR. La regla, el controlador y **todas** las pruebas que su cambio rompe van juntos, porque si se separaran el `./mvnw verify` del primer PR fallaría. Si pasa de unas 400 líneas, el único corte admitido es un PR previo **sin cambio de comportamiento**, con solo los ejemplos de T45 en `ErrorResponsesTest` y `RuleViolationTest`. Los Javadoc van con la regla. Si aun así pasa, se justifica en el PR.
- **4F** se mide igual. Si pasa de unas 400 líneas, se corta en dos PR seguidos: **19a** (la adopción y `fiscal-year-rejections`) y **19b** (el diálogo y la lista), apilado sobre el 19a. La decisión es del orquestador. Como T54 necesita todo lo anterior, se divide: la adopción del contrato (`versions.json`, la copia, los modelos generados y quitar el tipo local de T50) va en el 19a, con T49 y T50, y la validación en local, con quitar los tipos locales de T51 a T53, en el 19b, con T51 a T53. En los dos casos 4F se aprueba y se integra completo (19a y 19b) justo después de 4B2.

### Orden de integración (obligatorio; plan, «Dependencias, paralelismo y recursos (E4)»)

1. **4D**; y **4F0** en cuanto esté listo.
2. **4U**. **La persona publica `liv-components` 0.5.0 en npm** (`ui/README.md` › «Publicar una versión»). 4F no se aprueba antes.
3. **4B1**, con **4B2 aprobado** sobre su rama. Al integrarlo sale la *release* `contract-tax-v1.1.0`. 4B2 se rebasa sobre `main` y se reapunta su PR.
4. **4F adopta la *release*** con su propio *script* (T54, `npm run api:update`), nunca con una copia sacada de `backend/` (`AGENTS.md` §4.6), y se aprueba con el CI en verde. 4E se aprueba, en verde contra el entorno local con 4B2 y 4F aplicados.
5. **Seguidos y el mismo día, en este orden: 4B2, 4F, 4E** (H2‑19). No se versiona ni se despliega el *backend* entre ellos. Entre los puntos 3 y 5, `main` tiene el contrato 1.1.0 y el *backend* viejo, sin ningún cliente que lo adopte todavía; entre el 5 y el final, las pruebas de navegador de `main` están en rojo hasta que entra 4E.
6. El cierre del orquestador (rama `docs/007-cierre`; véase abajo).

### Paralelismo (máximo tres sesiones de trabajo a la vez; el entorno local es exclusivo)

- **Ola 1 (ya):** 4D (T36 a T38), 4F0 (T39, T40) y 4U (T47, T48). No comparten archivos.
- **Ola 2, con 4D integrado:** **4B1 → 4B2 en una sola sesión de `backend/`** (T41, y después T42 a T46, en serie). Mientras corre, pueden correr otras dos sesiones (tres en total como máximo):
  - 4F (T49 a T53), desde que 4F0 esté integrado y 0.5.0 esté publicada; usa el servicio simulado en sus pruebas hasta que exista la *release*;
  - 4E (T55 a T57), que se escribe en rojo a la vez que 4F;
  - lo que quede de la ola 1.
- **Ola 3, las validaciones con la rama de 4B2 aplicada, de una en una** (entorno exclusivo): primero la de 4F (T54), después la corrida de 4E (T57). Ninguna otra sesión usa el entorno mientras tanto. Las pruebas de 4B1 y 4B2 usan Testcontainers, no el entorno local.
- **Ola 4:** aprobaciones de la persona e integración seguida de 4B2, 4F y 4E.
- **En serie siempre:** T36 → T37 → T38; T39 → T40; T42 → T43 → T44 → T45 → T46; T47 → T48; T49 → T50 → T51 → T52 → T53 → T54; T55 → T56 → T57.

### Recursos compartidos (enmienda 4)

| Recurso | Quién lo escribe | Regla |
|---|---|---|
| `backend/contracts/openapi/tax.yaml` y la *release* `contract-tax-v1.1.0` | 4B1 (T41) | Solo 4B1; 4B2 no lo toca |
| `FiscalYearOpeningPolicy`, `CreateBlocked`, `YearRelation`, `FiscalYearsController` y sus pruebas; `FiscalYears`, `JpaFiscalYears`, `JpaFiscalYearsRacesTest` y la *skill* `liv-endpoint` | 4B2 (T42 a T46) | Solo 4B2, una sola sesión en `backend/` |
| `backend/CHANGELOG.md` | 4B1 y 4B2 | En serie; 4B2 está apilada y rebasa al integrarse 4B1 |
| `frontend/src/app/features/fiscal-years/` y `frontend/CHANGELOG.md` | 4F0 y 4F | En serie: 4F parte de 4F0 integrado |
| `frontend/api-spec/`, `frontend/package.json` y `package-lock.json` | 4F (T49, T54) | Solo 4F |
| `ui/` (`select/`, `public-api.ts`, `CHANGELOG.md`, la versión) y la publicación en npm, que hace la persona | 4U (T47, T48) | Solo 4U; la persona publica |
| `e2e/pruebas/vigencias.spec.ts`, `e2e/support/vigencias.ts`, `e2e/README.md` y las capturas | 4E (T55 a T57) | Solo 4E |
| Documentos funcionales y `AGENTS.md` §3.1 | 4D (T36 a T38) | Solo 4D |
| El entorno local y su base de vigencias (`backend/dev/limpiar-vigencias.sh`, que la persona corre antes de cada corrida de `e2e/`) | T40, T54 y T55 a T57 | Exclusivo, de una sesión a la vez: cada vez que una de esas tareas corre la prueba de navegador, en rojo o en verde |
| Figma (LIV‑Diseño) | — | Todo aprobado (paso 12) |
| La rama `docs/007-cierre`, `ESTADO.md`, `tasks.md`, el *roadmap* y los criterios de `spec.md` | Solo el orquestador | — |

### Lo que hace la persona (nunca un agente)

- Aprueba la H3 de cada PR e integra, con su revisión de los documentos de 4D como responsable funcional.
- **Publica `liv-components` 0.5.0 en npm** después de integrar 4U.
- Corre `backend/dev/limpiar-vigencias.sh` antes de cada corrida de `e2e/`.
- **Aprueba las capturas de `e2e/`:** `vigencias-vacia`, `vigencias-crear` y `vigencias-lista`, vueltas a aprobar, y `vigencias-sin-anios`, nueva.
- Integra 4B1 (publica la *release*) y, el mismo día y seguidos, 4B2, 4F y 4E.

---

### PR 4D · Documentos (raíz, rama `docs/007-e4-documentos`) — plan, paso 13

Propuestas pendientes de revisión jurídica (`AGENTS.md` §8); ningún texto las presenta como reglas aprobadas. Los cambios de la spec («Cambios propuestos a los documentos funcionales») y lo que el plan fija en el paso 13. **No toca:** el Doc. 03, el Doc. 04, los YAML de `anexos/`, el glosario, los ADR, la spec, el plan ni la constitución (`spec/constitution/mission.md` y `roadmap.md` citan «Doc. 05 v0.1»; cambiarlos exige un ADR, así que se anota para el orquestador).

- [x] **T36** · ERD 2.4.
  - **Proyecto:** raíz · **PR:** 4D
  - **Archivos:** `docs/funcional/ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md` (el encabezado y la descripción de `tax.fiscal_year`, más una nota).
  - **Depende de:** ninguna.
  - **Recursos:** el ERD (solo 4D lo edita).
  - **Prueba primero:** lista de comprobación escrita antes del texto:
    - la versión pasa a 2.4, con su motivo: la enmienda 4 de la feature 007;
    - la descripción de `tax.fiscal_year` dice que **V‑05 fija el límite inferior** y ya no dice que su rango técnico «no decide qué vigencias históricas se cargarán (pendiente 5)»;
    - la tabla, sus `CHECK` y sus años (1999 a 2100) **no cambian**, y el conteo sigue en 80 tablas;
    - una nota dice que el comentario de la migración `V202611131000` quedó desactualizado y que **no se edita** porque ya está aplicada (`AGENTS.md` §4.5).
- [x] **T37** · Doc. 01 v0.6.
  - **Proyecto:** raíz · **PR:** 4D
  - **Archivos:** `docs/funcional/01_Menu_y_Cargue_Tablas_Base_Liquidacion.md` (el encabezado, §5 con V‑02, V‑05 y el orden de evaluación, §11 y la entrada de la versión 0.6 en §13, con el registro de los cambios a otros documentos al estilo del Doc. 03 §7).
  - **Depende de:** T36 (la versión del ERD que cita).
  - **Recursos:** el Doc. 01 (solo 4D lo edita).
  - **Prueba primero:** lista de comprobación, con cada punto contrastado con `spec.md` (007-06 a 007-08, E4‑1, E4‑6 y E4‑7) antes del texto:
    - **V‑02:** «El orden de creación es libre»: dentro del rango de V‑05 se puede crear cualquier año que la administración no tenga, antes de la más antigua, después de la más reciente o entre dos, con huecos; V‑02 ya no rechaza nada y **conserva su número**; salen `GAP`, `HISTORICAL` y la frase «Estos dos motivos suponen…»;
    - **V‑05:** el rango va de 2002 al año siguiente al año en curso, igual para la primera vigencia y las demás; un año anterior a 2002 da `PAST_LIMIT` y dice el primer año, y uno posterior al siguiente da `FUTURE_LIMIT` y dice el último; **registra 2002** como límite operativo, por la decisión de producto del responsable funcional del 2026-11-16 (E4‑1), con la excepción explícita a `AGENTS.md` §4.5; salen `FIRST_YEAR` y «Quien crea como primera vigencia el año siguiente…»; se quedan la zona `America/Bogota` y el motivo del «+1»;
    - **orden de evaluación:** V‑01, V‑05 `PAST_LIMIT` y V‑05 `FUTURE_LIMIT`, con el ejemplo del plan: año en curso 2026, vigencia 2026 creada, y tres solicitudes (2001 da V‑05 `PAST_LIMIT`; 2026 da V‑01; 2028 da V‑05 `FUTURE_LIMIT`);
    - **§11:** el pendiente 5 queda resuelto para la creación y sigue abierto para la cartera (5.1); el pendiente nuevo es el **9** (hoy hay 8: se comprueba antes de numerarlo), «cómo se retira una vigencia que no se puede liquidar, sin borrarla» (E4‑7), y remite a la feature 1.5.1 junto con el cierre (pendiente 7);
    - `ALL_CREATED` **no** aparece como regla del Doc. 01: es el motivo de la API para 007-23;
    - §13: la entrada de la v0.6 con los cambios a los Docs. 02 y 05 y al ERD.
- [x] **T38** · Doc. 02 v0.4, Doc. 05 v0.2 y `AGENTS.md` §3.1.
  - **Proyecto:** raíz · **PR:** 4D
  - **Archivos:** `docs/funcional/02_Homologacion_y_Reglas_de_Liquidacion.md` (el encabezado y el pendiente 7 del §12), `docs/funcional/05_Cartera_Intereses_Sanciones_Prescripcion.md` (el encabezado y un pendiente nuevo en el §11) y `AGENTS.md` (solo §3.1: los dos números de versión).
  - **Depende de:** T36 y T37 (los números de versión y de pendiente).
  - **Recursos:** los Docs. 02 y 05 y `AGENTS.md` (solo 4D los edita).
  - **Prueba primero:** lista de comprobación, con cada punto contrastado con el plan (paso 13) antes del texto:
    - **no hay prerrequisito nuevo** (H2‑20): no aparece `LQ‑P05`, el §B.3 no cambia y un año pasado se liquida con `LQ‑P01` a `LQ‑P04`; sus tablas las exige la habilitación de la 1.5.1 (V‑03);
    - **el pendiente 7 del §12 no se reduce:** su pregunta («si se liquidan vigencias anteriores con este motor») queda respondida (sí, sin concepto jurídico previo) y siguen abiertas, sin bloquear nada en esta feature, la prescripción, la firmeza y la competencia ese año (con los criterios que aportó la persona el 2026-11-16, como **guía y propuesta pendiente de revisión jurídica**: plazo de 5 años por el art. 817 ET y el art. 59 de la Ley 788 de 2002, inicio del término según el supuesto, interrupción y suspensión por el art. 818 ET, aforo cuando no hay declaración, quién la declara y evaluación por vigencia y por obligación), los intereses y las sanciones desde su fecha límite, las tasas de usura desde esa fecha, qué hace el motor con un año que ya tiene una obligación migrada y el efecto de imputar a la más antigua primero (Doc. 04 §5);
    - las **dos diferencias con el Doc. 05 §6.1** quedan como preguntas para el Módulo 5 con jurídica, sin decidirlas; los datos que sugirió la persona **no se agregan al ERD**;
    - **Doc. 05 v0.2:** el pendiente nuevo (un año con saldos migrados en la 5.1 y vigencia en LIV: cuál de los dos orígenes prevalece; CH‑02 y OB‑01 ya impiden la obligación doble) va con el siguiente número libre del §11;
    - **`AGENTS.md` §3.1:** el ERD pasa de «versión 2.3» a «versión 2.4», y el Doc. 05, de «borrador (v0.1)» a «borrador (v0.2)»; nada más;
    - los Docs. 03 y 04, los YAML de `anexos/`, el glosario y la constitución no cambian.
  - **Al terminar T36 a T38:** la lente de legibilidad por *commit*; `liv-revisor` (nivel estándar) comprueba la coherencia entre documentos (V‑02, V‑05, el orden de evaluación, los pendientes y el §13); y la persona aprueba como responsable funcional. El reporte al orquestador incluye la referencia desactualizada de la constitución, para `ESTADO.md`.

### PR 4F0 · El año elegido sin años pasados (`frontend/`, rama `feat/007-e4-front-previo`) — plan, paso 14

*Skill* `liv-pantalla`. Mitiga el riesgo 6 de la spec: con el *backend* nuevo, el diálogo no puede elegir por omisión un año pasado. Compatible con los contratos 1.0.0 y 1.1.0; **no cambia** la copia del contrato, la versión de la librería, los textos, el diseño, el orden en que se muestran los años ni la advertencia. Sin año marcado, el diálogo muestra vacíos el inicio y el fin: es un estado sin diseño, y solo dura la ventana de H2‑19. No es evidencia de 007-22: es una regla transitoria que T51 reemplaza.

- [x] **T39** · `pickYear`: nunca se marca un año `PAST`.
  - **Proyecto:** `frontend/` · **PR:** 4F0
  - **Archivos:** `frontend/src/app/features/fiscal-years/create-fiscal-year-dialog.ts`, `frontend/src/app/features/fiscal-years/create-fiscal-year-dialog.spec.ts` y `frontend/CHANGELOG.md` («Sin publicar»).
  - **Depende de:** la H2 de la enmienda (ya).
  - **Recursos:** `features/fiscal-years/` (T49 y siguientes parten de este PR integrado) y `frontend/CHANGELOG.md`.
  - **Prueba primero** (rojo, en `create-fiscal-year-dialog.spec.ts`; mitigación del riesgo 6 de la spec, no un criterio): si el año marcado sigue disponible, se queda; si no, se marca el `CURRENT`; si no viene, el `NEXT`; si tampoco, ninguno; nunca un `PAST`, ni por su posición en la lista:
    - con el orden de 1.1.0 (`NEXT`, `CURRENT`, `PAST`…) queda marcado `CURRENT`;
    - con `NEXT` y años `PAST`, queda marcado `NEXT`;
    - con solo años `PAST`, ninguno: «Crear vigencia» queda inhabilitado y el foco inicial va al grupo, no a «Crear vigencia»;
    - con el orden de 1.0.0 (`CURRENT`, `NEXT`), `CURRENT`;
    - **V‑01 · 007-05:** al releer la lista después de un rechazo de V‑01, solo con `NEXT`, queda marcado `NEXT`.
- [x] **T40** · El foco al releer sin ningún año marcado.
  - **Proyecto:** `frontend/` · **PR:** 4F0
  - **Archivos:** `frontend/src/app/features/fiscal-years/create-fiscal-year-dialog.ts` (`focusSelectionIfPending`), `create-fiscal-year-dialog.html` (solo si el foco al primer radio lo exige), `create-fiscal-year-dialog.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** T39.
  - **Recursos:** los de T39.
  - **Prueba primero** (rojo, riesgo 6 y **V‑01 · 007-05**): al releer la lista sin ningún año que marcar, el foco va al primer radio del grupo y no se queda en «Crear vigencia» (hoy `focusSelectionIfPending` solo busca el radio marcado, `:checked`, y el foco se perdería).
  - **Validación en local, antes del PR (ADR 0030; entorno exclusivo):** con el sistema levantado, sin que ninguna otra sesión use el entorno, y con el *backend* actual (contrato 1.0.0), se corre `npm test` en `e2e/`: las pruebas de navegador siguen en verde (todas esperan marcado el primer año, que es `CURRENT`, o el `NEXT` cuando es el único). El PR cita la corrida. No hay cambios en `e2e/`.
  - **Al terminar T39 y T40:** adversarial con dos `liv-revisor` y la lente de legibilidad (`liv-revisor-rapido`); la H3.

### PR 4U · `Select` y 0.5.0 (`ui/`, rama `feat/007-e4-ui-select`) — plan, paso 18

*Skill* `liv-componente`. Crítico (`ui/**`). Diseño aprobado en Figma (paso 12): sección 81:490 y conjunto 81:629, diez variantes (`State`: `Default`, `Focus`, `Open`, `Disabled` y `Error`, por `Content`: `Filled` o `Placeholder`), con las propiedades `Label`, `Value`, `Placeholder`, `Help` y `Show help`. Alto de 44 px y radio de 8 px; la lista abierta la dibuja el sistema operativo (no se diseña ni se implementa). No hay *tokens* ni estilos nuevos, y `Icon` ya tiene `chevron-down`: no se modifica ningún componente existente.

- [x] **T47** · `Select`.
  - **Proyecto:** `ui/` · **PR:** 4U
  - **Archivos:** `ui/projects/liv-components/src/lib/select/` (el componente, su `.css`, su `.html`, su `.spec.ts` y su historia de Storybook enlazada al nodo 81:629) y `ui/projects/liv-components/src/public-api.ts` (la exportación).
  - **Depende de:** el diseño aprobado (ya).
  - **Recursos:** `public-api.ts`.
  - **Prueba primero** (rojo, `select.component.spec.ts`; **V‑05 · 007-22** para el control y **007-21** para axe en cada combinación):
    - envuelve un `<select>` nativo con su `<label>` visible, enlazados por un `id` propio de cada instancia; **nunca** una lista con `role="listbox"`; el nombre accesible sale de la etiqueta;
    - entradas: `label` (obligatoria), `value` (`string | null`, con `valueChange`), `options` (`{ value, label }`, en el orden recibido), `placeholder`, `disabled`, `help` (enlazado con `aria-describedby`) y una entrada para el estado `Error` (el nombre lo define el PR);
    - el valor de entrada y de salida; la opción vacía con `null`, que muestra el `placeholder` y no se puede volver a elegir; `disabled`;
    - **los cinco estados**, cada uno con `Filled` y `Placeholder`: `Error` pone `aria-invalid="true"` y el texto de `Help` como mensaje de error enlazado con `aria-describedby`; `Open` se pinta como `Focus`, porque CSS no puede saber si un `<select>` nativo está abierto;
    - el texto y el orden de las opciones; axe en cada variante y estado (007-21);
    - **dentro de un `Dialog` real** (0.4.0, sin cambios), una prueba sintética: el `<select>` es una parada más de la trampa de foco y, con `data-initial-focus`, recibe el primer foco; la prueba vive en `select.component.spec.ts` y **no** toca los archivos del `Dialog`;
    - estilos con Tailwind en el `.component.css`, solo con *tokens*, `appearance: none` y el icono `chevron-down`; sin atributo `style`, sin utilidades en la plantilla y sin `::ng-deep`.
    - El teclado del `<select>` lo da el navegador y jsdom no lo implementa: se prueba en `e2e/` (T56).
- [x] **T48** · Versión 0.5.0, `CHANGELOG.md` y `README.md`.
  - **Proyecto:** `ui/` · **PR:** 4U
  - **Archivos:** `ui/projects/liv-components/package.json` (la versión, con `npm run version:minor`), `ui/package-lock.json` (si el comando lo cambia), `ui/CHANGELOG.md` (la sección `## [0.5.0] — <fecha>` con la entrada de `Select`; `RadioGroup` se queda en la librería) y `ui/README.md` (el párrafo de estado de las versiones y la mención del componente nuevo).
  - **Depende de:** T47.
  - **Recursos:** `ui/CHANGELOG.md`, la versión de `liv-components` y su publicación en npm.
  - **Prueba primero:** se escribe la sección `[0.5.0]` antes de subir la versión y se corre `npm run release:check` (sus controles de versión y de `CHANGELOG.md`; los de rama `main` y de sesión de npm son de la publicación) y se reporta su salida. `npm run lint`, `npm run format:check`, `npm test` y `npm run pack` en verde.
  - **Al terminar:** la comparación con Figma es **a mano en Storybook**, con capturas de cada estado en la descripción del PR (`ui/` no tiene regresión visual, Q14); adversarial con dos `liv-revisor-critico` y las cuatro lentes. **No se publica:** la persona lo hace después de integrar. El PR dice que **4F corre la prueba propia del aviso de sesión del *frontend* con 0.5.0** (T49).

### PR 4B1 · Contrato `tax.yaml` 1.1.0 (`backend/`, rama `feat/007-e4-contrato`) — plan, paso 15

*Skill* `liv-endpoint`, paso 1. Con 4D integrado. Se integra solo con 4B2 aprobado sobre su rama: así la *release* no describe una regla que la revisión de 4B2 pudiera corregir.

- [x] **T41** · `tax.yaml` 1.1.0, compatible.
  - **Proyecto:** `backend/` · **PR:** 4B1
  - **Archivos:** `backend/contracts/openapi/tax.yaml`, `backend/src/test/java/co/com/sapenlinea/liv/contracts/OpenApiContractTest.java` y `backend/CHANGELOG.md` («Sin publicar»). No cambia `backend/pom.xml` ni `backend/README.md` (la tabla de contratos no lleva versión).
  - **Depende de:** 4D integrado (T37: los motivos vienen del Doc. 01 v0.6).
  - **Recursos:** contrato `tax` y la *release* `contract-tax-v1.1.0` (solo 4B1); `backend/CHANGELOG.md`.
  - **Prueba primero** (rojo, `OpenApiContractTest`; **V‑05 · 007-07**, **V‑05 · 007-23** y **007-03**): `info.version` 1.1.0; `listFiscalYears` dice que `creatableYears` son los años que la creación acepta hoy (V‑01 y V‑05); `createFiscalYear` evalúa `V-01`, `V-05` `PAST_LIMIT` y `V-05` `FUTURE_LIMIT`; `CreationRejected` con `PAST_LIMIT` (extensión `minYear`) y `FUTURE_LIMIT` (`maxYear`) y la nota de que `V-02`, `GAP`, `HISTORICAL` y `FIRST_YEAR` ya no se emiten; `FiscalYearList.creatableYears` documenta el orden **del más reciente al más antiguo**, de 2002 al año siguiente al año en curso, sin los que existen (se quita el texto «de cero a dos» años de la 1.0.0); `CreatableYear.relation`: `PAST` es un año anterior al año en curso (sale el ejemplo de 2027 en 2028); `CreateBlocked.reason` documenta `ALL_CREATED` (con la propiedad `minYear`, opcional en el esquema y que el servidor envía siempre con `ALL_CREATED`; `required` sigue siendo `[reason, maxYear]`) y deja `FUTURE_LIMIT` documentado y no emitido; `CreationRejectionExtensions` agrega `minYear` opcional y marca como obsoletas `requiredYear`, `oldestYear`, `currentYear` y `nextYear`. **Las obsoletas se quedan como `$ref: Year`** con la nota en la descripción del objeto: no se usa `deprecated: true` y **no se relaja** la comprobación que exige `$ref: Year` en toda extensión de año (`AGENTS.md` §4.5).
  - **Verde:** el contrato. La compatibilidad (Q08) se comprueba de dos maneras: `sh contracts/scripts/compare.sh origin/main` en local y el *check* `backend · contratos` en el PR deben dar **compatible con versión menor**, y el PR cita la salida; y la **tabla de significado** del plan (paso 15), que el PR incluye: `PAST_LIMIT` y `ALL_CREATED`, valores nuevos en cadenas documentadas; los motivos que dejan de emitirse; `minYear` opcional; el orden de `creatableYears`, ahora documentado y contrario al que daba el servidor; y la cantidad de años de `creatableYears`, que pasa de entre cero y dos en la 1.0.0 a hasta 26 en 2026 (uno más cada año). Sin la etiqueta `contrato-incompatible` (H2‑17). Si `compare.sh` no da compatible, se detiene y se escala a la persona.
  - **Entre 4B1 y 4B2:** el modelo generado `CreateBlocked` gana `minYear` y el constructor con los campos obligatorios no cambia, así que `FiscalYearsController` sigue compilando; el `./mvnw verify` de este PR lo confirma.
  - **Al terminar T41:** adversarial con dos `liv-revisor-critico` y las cuatro lentes (`AGENTS.md` §4.3, contrato); no se integra hasta que 4B2 esté aprobado.

### PR 4B2 · La regla nueva (`backend/`, rama `feat/007-e4-regla`, apilada sobre 4B1) — plan, paso 16

*Skill* `liv-endpoint`. Sin migraciones, sin permisos nuevos, sin cambios en RLS, auditoría ni aislamiento. **Los `test:` de T42, T43 y T45 se escriben primero y en rojo** (el rojo puede ser de compilación, por los símbolos nuevos: `FIRST_CREATABLE_YEAR`, `ALL_CREATED`, `minYear`); el verde llega después. **T44 es distinta:** sus dos pruebas son de caracterización y nacen en verde, porque el bloqueo ya existe; van en su propio *commit* `test:` (con el mensaje que lo dice), después del rojo y antes del verde, y no cuentan como rojo. El `./mvnw verify` completo solo queda en verde al cerrar T46, porque las pruebas que la regla rompe van en el mismo PR. Cada prueba lleva el código del criterio en `@DisplayName` y en `@Tag`. Una sola sesión en `backend/`.

- [x] **T42** · La regla en el dominio y el límite 2002.
  - **Proyecto:** `backend/` · **PR:** 4B2
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/tax/`: `domain/FiscalYearOpeningPolicy.java` (con la constante `FIRST_CREATABLE_YEAR = 2002` y su Javadoc), `domain/CreateBlocked.java`, `domain/YearRelation.java`, `domain/CreatableYear.java` (solo si hace falta) y `api/FiscalYearsController.java` (solo lo que pide la compilación y pasar `minYear` al `CreateBlocked` generado); en `backend/src/test/java/co/com/sapenlinea/liv/tax/domain/`: `FiscalYearOpeningPolicyTest.java`.
  - **Depende de:** T41 (la rama de 4B1: el modelo generado trae `minYear`).
  - **Recursos:** `FiscalYearOpeningPolicy`, `CreateBlocked` y el controlador (solo 4B2).
  - **Prueba primero** (rojo, `FiscalYearOpeningPolicyTest`, reescrita):
    - **V‑02 · 007-06:** con {2026} y en 2026, se pueden crear 2002, 2015 y 2027, en cualquier orden; con {2010, 2020}, se aceptan un año anterior, uno intermedio y uno posterior; **ningún** año de 1999 a 2100 da un rechazo con `V-02`;
    - **V‑05 · 007-07:** 2001 da `PAST_LIMIT` con `minYear` 2002, con vigencias y sin ellas; el año en curso + 2 da `FUTURE_LIMIT` con `maxYear`; **V‑01 · 007-05:** un año que ya existe da `V‑01` **antes** que cualquier `V‑05` (con `@Tag("007-05")`); un año fuera de la tabla nacional sigue siendo `IllegalArgumentException` (el contrato lo rechaza antes con 400);
    - **V‑05 · 007-08:** sin vigencias, en 2026, `creatableYears` son los 26 años de 2027 a 2002, en ese orden, y cualquiera se acepta;
    - **orden (007-03):** `creatableYears` va estrictamente de mayor a menor; `relation` es `NEXT`, `CURRENT` y `PAST` en el resto; un año que falta en la tabla nacional no se ofrece;
    - **V‑05 · 007-23:** con 2002 a 2027 creados, `creatableYears` queda vacío y `createBlocked` es `ALL_CREATED` con `minYear` 2002 y `maxYear` 2027; si falta un solo año, no hay `createBlocked`; si la tabla nacional no tiene algún año del rango (un fallo técnico), tampoco hay `createBlocked`;
    - **V‑05 · E4‑1 · «El primer año que se puede crear es 2002»:** 2002 se acepta y 2001 da `PAST_LIMIT` con `minYear` 2002; falla si alguien mueve la constante. El Javadoc de la constante dice cuatro cosas: la regla (V‑05, Doc. 01 v0.6), el motivo (un límite operativo que decidió el responsable funcional el 2026-11-16, no un valor tributario), la excepción explícita a `AGENTS.md` §4.5 que aprobó en la H1 y que cambiarlo exige una versión del Doc. 01.
  - **Verde:** `refusalOf` evalúa `V‑01`, luego `V‑05` `PAST_LIMIT` y luego `V‑05` `FUTURE_LIMIT`; `creatableYears()` recorre la tabla nacional de mayor a menor; `createBlocked()` da `ALL_CREATED` solo si la administración tiene todos los años del rango; `ALL_CREATED` reemplaza a `FUTURE_LIMIT` en el `enum` del dominio y `CreateBlocked` gana `minYear`; `PAST` es un año anterior al año en curso; su Javadoc cambia.
- [x] **T43** · Los casos de uso: la frontera del año.
  - **Proyecto:** `backend/` · **PR:** 4B2
  - **Archivos:** en `backend/src/main/java/co/com/sapenlinea/liv/tax/application/`: `CreateFiscalYear.java` y `ListFiscalYears.java` (solo su Javadoc: el de `CreateFiscalYear` justifica hoy el bloqueo con una medianoche que deja un hueco de V‑02 y cita «V-01, V-02 or V-05» en su `@throws`; debe decir que el bloqueo se queda por la carrera del mismo año, V‑01 (H2‑16), y citar solo V‑01 y V‑05); en `backend/src/test/java/co/com/sapenlinea/liv/tax/application/`: `CreateFiscalYearTest.java`, `ListFiscalYearsTest.java` y `FakeFiscalYears.java` (solo si guarda algo de la regla vieja).
  - **Depende de:** T42.
  - **Recursos:** ninguno compartido.
  - **Prueba primero** (rojo; **V‑05 · 007-07**, **V‑05 · 007-08** y **V‑02 · 007-06**): con `Clock.fixed(2027-01-01T04:30Z)` (el 31 de diciembre de 2026 a las 23:30 en Bogotá), 2027 es `NEXT`, 2028 da `FUTURE_LIMIT` con su `maxYear` y la lista va de 2027 a 2002; la lista nunca ofrece un año que la creación rechace; salen los casos de V‑02 y de `FIRST_YEAR`. Siguen igual y en verde: `canCreate` según `tax.fiscal-year.create` y lo que `CreateFiscalYearTest` comprueba del bloqueo (`lockForCreation`).
- [x] **T44** · El bloqueo se queda: dos pruebas de caracterización con el mismo año (H2‑16).
  - **Proyecto:** `backend/` · **PR:** 4B2
  - **Archivos:** `backend/src/test/java/co/com/sapenlinea/liv/tax/infrastructure/persistence/JpaFiscalYearsRacesTest.java` y `FiscalYearsPersistenceTestBase.java` (solo si el publicador espía va ahí). No cambian `LivDatabase` (se usa su `awaitAnApplicationSessionWaitingOnALock`) ni el código de producción.
  - **Depende de:** T42.
  - **Recursos:** `JpaFiscalYearsRacesTest` (solo 4B2).
  - **Prueba primero — nota:** **estas dos pruebas no empiezan en rojo**, porque el bloqueo ya existe: son pruebas de **caracterización**, y por eso van aparte de las pruebas en rojo. Reemplazan a `theLockSerializesTheCreationsAcrossMidnight` y `withoutTheLockTheTestSeesTheGap`, que pierden su premisa (`V‑02` `GAP`); con esto no se desactiva ni se debilita ninguna prueba (`AGENTS.md` §4.5). Etiquetas `V-01` y `007-05`:
    - **con el bloqueo** (`withLock` verdadero): A toma el bloqueo y se detiene; la prueba espera a que B esté detenida en `pg_advisory_xact_lock` y **solo entonces** comprueba, con un espía en `findAll`, que B no leyó los años; al soltar A, A crea y confirma, y B lee 2026 ya creado y recibe el `V‑01` de la política, sin llegar al `INSERT`; queda una sola fila y se publica un solo `FiscalYearCreated`, contado con un publicador espía en lugar del vacío del arnés;
    - **el control sin el bloqueo** (`withLock` falso), una **prueba permanente**: mientras A está detenida, B no espera nada; la prueba espera a que B termine (`creationB.get`, con su tiempo límite): B lee la administración vacía, crea 2026 y confirma, y el espía registra su lectura; al soltar A, **A** intenta crear, recibe el `23505` y termina en `V‑01`; queda una sola fila y un solo `FiscalYearCreated`. Demuestra en el CI que la prueba con el bloqueo detecta su falta.
    - **Comprobación local, que no se sube:** se quita a mano la llamada al bloqueo en `CreateFiscalYear`, se ve fallar la prueba con el bloqueo y se vuelve a poner.
    - **No cambian:** `theLockIsOfTheAdministration`, `theRealUniqueBecomesV01` y las pruebas de la apertura.
- [x] **T45** · La API: rechazos, orden y `ALL_CREATED`.
  - **Proyecto:** `backend/` · **PR:** 4B2
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/`: `tax/FiscalYearsApiTest.java`, `platform/ErrorResponsesTest.java` y `shared/RuleViolationTest.java`.
  - **Depende de:** T42 y T43.
  - **Recursos:** ninguno compartido.
  - **Prueba primero** (rojo; `FiscalYearsApiTest` usa el reloj real, así que **ningún año va fijo**: todos se calculan con `currentYear()`): **V‑01 · 007-04**, crear el año en curso y uno pasado los deja en *Planeada*, sin liquidación habilitada y con inicio el 1 de enero y fin el 31 de diciembre; **V‑05 · 007-07**, 2001 da 422 `V-05` `PAST_LIMIT` con `minYear` y `currentYear() + 2` da `FUTURE_LIMIT` con `maxYear`, con su estado, su código, su motivo y sus extensiones; **007-03**, el orden de `creatableYears` en el JSON; **V‑05 · 007-23**, una administración nueva con los años de 2002 a `currentYear() + 1`, que inserta el dueño de los objetos (`liv_app` no puede borrar y el contenedor se comparte), da `createBlocked` con `ALL_CREATED`, `minYear` y `maxYear`. `ErrorResponsesTest` y `RuleViolationTest` usan `GAP` como ejemplo y pasan a `PAST_LIMIT`, para no dejar de ejemplo un motivo que ya no existe (**es el corte previo** admitido si el PR pasa de ~400 líneas).
- [x] **T46** · La *skill* `liv-endpoint`, el registro y el cierre del PR.
  - **Proyecto:** `backend/` · **PR:** 4B2
  - **Archivos:** `backend/.claude/skills/liv-endpoint/SKILL.md` (la tabla del ejemplo hexagonal: `FiscalYearOpeningPolicy` aplica V‑01 y V‑05) y `backend/CHANGELOG.md` («Sin publicar»).
  - **Depende de:** T42 a T45.
  - **Recursos:** `backend/CHANGELOG.md`.
  - **Prueba primero:** `./mvnw verify` en verde, con Spotless y las pruebas guardianas (`ArchitectureRulesTest`, `CodeSizeGuardTest`, `ModularityTest`, `ContractRulesTest`), incluidas las que no cambian (véase arriba). Se mide el tamaño del PR antes de abrirlo (corte en «Tamaño»).
  - **Condiciones de integración, que comprueba el orquestador:** 4F0 y 4B1 integrados, y 4F y 4E ya aprobados, para integrarlos justo después.
  - **Al terminar T42 a T46:** adversarial con dos `liv-revisor-critico` y las cuatro lentes (riesgo de nivel alto; resiliencia y confiabilidad, `liv-revisor`; legibilidad, `liv-revisor-rapido`), con el resumen en la descripción del PR. La revisión comprueba además que las dos pruebas de T44 detectan la falta del bloqueo y que 2002 está en una sola constante.

### PR 4F · El diálogo con `Select` (`frontend/`, rama `feat/007-e4-front-dialogo`) — plan, paso 19

*Skill* `liv-pantalla`. **Requiere** el diseño aprobado, 4F0 integrado, `liv-components` 0.5.0 publicada en npm y 007-22 como quedó en H2‑18. Se desarrolla en paralelo con 4B1 y 4B2, con el servicio simulado en sus pruebas; **adopta la *release* `contract-tax-v1.1.0` al final (T54)**, cuando sale al integrar 4B1, y entonces se aprueba. Se integra **justo después de 4B2** y nunca antes (H2‑19). El *frontend* no calcula años: la lista, su orden, su relación con el año en curso, el rango del estado vacío y el motivo del bloqueo salen del servidor.

- [x] **T49** · `liv-components` 0.5.0.
  - **Proyecto:** `frontend/` · **PR:** 4F
  - **Archivos:** `frontend/package.json` y `frontend/package-lock.json` (`liv-components` `0.5.0`, exacta) y `frontend/CHANGELOG.md` («Sin publicar»).
  - **Depende de:** 4F0 integrado y **0.5.0 publicada en npm** por la persona.
  - **Recursos:** `package.json` y `package-lock.json` (solo 4F).
  - **Prueba primero:** `npm test` completo con 0.5.0, y el PR cita la prueba propia del aviso de sesión (`features/shell/session-warning-dialog.spec.ts`), que sigue en verde: el `Dialog` no cambió.
- [x] **T50** · Los mensajes de rechazo.
  - **Proyecto:** `frontend/` · **PR:** 4F
  - **Archivos:** `frontend/src/app/features/fiscal-years/fiscal-year-rejections.ts` y `fiscal-year-rejections.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** T49.
  - **Recursos:** `features/fiscal-years/`.
  - **Prueba primero** (rojo; **V‑05 · 007-07**): el mensaje de `V‑05` `PAST_LIMIT` cita `minYear`; `FUTURE_LIMIT` cita `maxYear`; se quedan `V‑01` y `V‑06`; `GAP`, `HISTORICAL` y `FIRST_YEAR` dan el mensaje general. Textos del frame 67:3284. Mientras la *release* no exista, las pruebas pasan `minYear` con un tipo local de la prueba, que T54 elimina cuando el modelo generado lo trae.
- [x] **T51** · El diálogo con el `Select`.
  - **Proyecto:** `frontend/` · **PR:** 4F
  - **Archivos:** `frontend/src/app/features/fiscal-years/create-fiscal-year-dialog.ts`, `.html`, `.css` y `.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** T49 y T50.
  - **Recursos:** `features/fiscal-years/`.
  - **Prueba primero** (rojo, `create-fiscal-year-dialog.spec.ts`; **V‑05 · 007-22**, **V‑01 · 007-04**, **V‑01 · 007-05** y **007-21**). Diseño: 67:3040, 82:5802 y 67:3163.
    - la etiqueta «Año de la vigencia», la opción vacía «Elige un año», y el orden y el texto de las opciones tal como vienen en `creatableYears` («2026 · Año en curso», «2027 · Año siguiente al año en curso»; un año `PAST` lleva solo su número; se quita la etiqueta de `PAST` de `DESCRIPCION_DE_RELACION`);
    - **la elección del año:** `CURRENT` si viene en la lista y si no, ninguno, al abrir y al releer después de un V‑01; después de un resultado que no se conoce (`isOutcomeUnknown`: **`TimeoutError`, `status` 0 y 503**), el año intentado sigue elegido si sigue en la lista y si no, ninguno (H2‑18); el caso «si ya no está» es una defensa del diálogo, porque la página cierra el diálogo cuando el año intentado aparece entre las vigencias (`onStaleList`), y esa prueba de `fiscal-years.spec.ts` se queda;
    - **el `Select` inhabilitado mientras se envía y mientras se relee la lista** (la variante `Disabled`, sin componente ni estado nuevo), así que «el año intentado» es siempre el que se envió; el foco no se pierde y vuelve al `Select` al terminar la relectura; si la relectura falla, se conserva el año intentado y el `Select` vuelve a estar habilitado;
    - las fechas solo con un año elegido (los dos `DescriptionItem` no se muestran sin él); «Crear vigencia» inhabilitado sin año;
    - el foco inicial en el `Select` (`data-initial-focus`), nunca en «Crear vigencia», y Enter al abrir no crea nada;
    - la advertencia nueva de 67:3040 y, solo con un año `PAST` elegido (lo decide `relation`, no un cálculo de fechas), la de 82:5802, «Es un año pasado», que **reemplaza** a la general;
    - axe con el diálogo abierto, con un año elegido y sin él, y con un rechazo; el resto del comportamiento (envío, rechazos, 403 a «Sin permiso», alerta nueva en cada intento) no cambia.
- [x] **T52** · La lista de vigencias: el estado vacío y el botón inhabilitado.
  - **Proyecto:** `frontend/` · **PR:** 4F
  - **Archivos:** `frontend/src/app/features/fiscal-years/fiscal-years.ts`, `.html` y `fiscal-years.spec.ts`; `frontend/CHANGELOG.md`.
  - **Depende de:** T51.
  - **Recursos:** `features/fiscal-years/`.
  - **Prueba primero** (rojo, `fiscal-years.spec.ts`): **007-03**, el estado vacío del administrador dice el rango («de 2002 a 2027») con los años **del último elemento al primero** de `creatableYears` (en una administración sin vigencias es el rango completo, del más reciente al más antiguo); se quita `describeCreatableYear`; **V‑05 · 007-23**, con `ALL_CREATED` y con `minYear` y `maxYear` enteros, el botón inhabilitado con el motivo del diseño («Ya existen todas las vigencias que se pueden crear: de 2002 a 2027.»), y con otro motivo (por ejemplo, un valor desconocido), el texto general; sale la rama de `FUTURE_LIMIT`. La lista de vigencias no cambia: va completa, sin paginación, aunque tenga 26 filas o más. Diseño: 66:2793 y 66:2513.
- [x] **T53** · Las pruebas de accesibilidad.
  - **Proyecto:** `frontend/` · **PR:** 4F
  - **Archivos:** `frontend/src/app/features/accessibility.spec.ts` y `frontend/CHANGELOG.md`.
  - **Depende de:** T51 y T52.
  - **Recursos:** `accessibility.spec.ts`.
  - **Prueba primero** (rojo; **007-21**): las pruebas de axe de la 1.4 hoy buscan el `fieldset` del grupo de radios dentro del diálogo y usan `createBlocked` con `FUTURE_LIMIT`; pasan al `Select` y a `ALL_CREATED`, y se agregan el diálogo con un año elegido, sin él y con un año pasado elegido.
- [x] **T54** · El contrato 1.1.0 adoptado y la validación en local.
  - **Proyecto:** `frontend/` · **PR:** 4F
  - **Archivos:** `frontend/api-spec/versions.json` (`tax` 1.1.0 y su `sha256`) y `frontend/api-spec/tax.yaml` (la copia fijada), el cliente generado en `frontend/src/app/api/tax/` (`creation-rejection-extensions.ts`, `fiscal-year-list.ts`, `create-blocked.ts` y `creatable-year.ts`, por `npm run api:update`), los *specs* que usaban el tipo local de T50, `fiscal-years.service.spec.ts` (su dato de prueba usa `createBlocked` con `FUTURE_LIMIT`, que ya no se emite: pasa a `ALL_CREATED` con `minYear`) y `frontend/CHANGELOG.md`.
  - **Depende de:** T49 a T53 y la *release* `contract-tax-v1.1.0` (sale al integrar 4B1, con 4B2 aprobado); para su validación, también de T55 y T56 (las pruebas de 4E, que viven en otra rama, se aplican en el entorno sin integrarse). Si 4F se corta, T54 se divide como dice «Tamaño».
  - **Recursos:** `frontend/api-spec/` (solo 4F); el entorno local, exclusivo.
  - **Prueba primero:** `npm run api:verify` en verde (SHA‑256 de la *release*); `npm test` completo; los tipos locales de T50 salen.
  - **Validación en local, antes de aprobar (ADR 0030; entorno exclusivo):** con el *backend* de la rama de 4B2 aplicada y `backend/dev/limpiar-vigencias.sh` corrido por la persona: se abre la 1.4 y se compara con Figma (67:3040, 82:5802, 67:3163, 67:3284, 66:2793 y 66:2513); y se corre `npm test` en `e2e/` con las pruebas de 4E en su estado de ese momento (la rama de 4E aplicada en la copia de trabajo del entorno, sin integrarla). La corrida definitiva de 4E es la de T57. El resultado se cita en el PR.
  - **Al terminar T49 a T54:** adversarial con dos `liv-revisor` (cambio normal) y la lente de legibilidad (`liv-revisor-rapido`); la H3.

### PR 4E · Pruebas de navegador (`e2e/`, rama `feat/007-e4-e2e`) — plan, paso 20

*Skill* `liv-prueba-e2e`. Se escriben en rojo mientras se implementa 4F y se integran **justo después de 4F**, en verde contra el entorno local, con `backend/dev/limpiar-vigencias.sh` ejecutado por la persona antes de cada corrida (H2‑9; `e2e/` no lo llama). **Cómo se eligen los años:** por `relation` y por su posición en la lista del servidor, nunca por el calendario de la prueba, y **una sola vez**, de la lista de la administración vacía, sin volver a leerla: `actual` es el año `CURRENT`; `siguiente`, el `NEXT`; `minimo`, el último de `creatableYears` (2002); e `intermedio`, un año `PAST` del medio de la lista, para dejar huecos. Se quita la etiqueta de `PAST` de `DESCRIPCION_DE_RELACION`. **El rojo de T55 a T57** se comprueba corriendo la prueba de navegador contra el *backend* de la rama de 4B2 aplicada (entorno exclusivo, de una sesión a la vez); mientras esa rama no exista, la sesión escribe las pruebas y comprueba por lectura y con `npm run lint` que compilan, y el primer rojo real lo ve cuando 4B2 está listo.

- [x] **T55** · Los años por `relation` y las pruebas que no cambian de significado.
  - **Proyecto:** `e2e/` · **PR:** 4E
  - **Archivos:** `e2e/support/vigencias.ts` (la toma de los cuatro años de la lista de la administración vacía; su tipo `ListaDeVigencias` ya trae `relation`), `e2e/pruebas/vigencias.spec.ts` y `e2e/README.md` (la tabla «Qué prueban» y la línea de los rechazos: V‑05 (007-07) se prueba por la API y V‑02 ya no rechaza).
  - **Depende de:** ninguna (la ejecución, de 4B2 aplicado); las pruebas de la 1.4 de `main` son la base.
  - **Recursos:** entorno local; `vigencias.spec.ts` y sus capturas (solo 4E).
  - **Prueba primero** (rojo con el *backend* nuevo, ejecutando antes de tocar): **007-09 a 007-12** usan `actual` (007-09 compara las vigencias con las de la API, no con un número fijo); la **administración B** (**CA‑12 · 007-18 y 007-19**) usa `anioDeB`, el `CURRENT`, y `anioSiguiente`, el `NEXT`; **V‑01 · 007-05**, después del V‑01 la lista se relee y **no hay ningún año elegido**: se ve «Elige un año», «Crear vigencia» está inhabilitado, `anioDeB` ya no está entre las opciones y el foco está en el `Select` (el nombre de la prueba cambia).
- [x] **T56** · El diálogo, el teclado y los rechazos.
  - **Proyecto:** `e2e/` · **PR:** 4E
  - **Archivos:** `e2e/pruebas/vigencias.spec.ts`, `e2e/pruebas/vigencias.spec.ts-snapshots/` (`vigencias-vacia`, `vigencias-crear` y `vigencias-lista`, vueltas a aprobar) y `e2e/support/vigencias.ts` (solo lo que falte).
  - **Depende de:** T55.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero** (rojo):
    - **007-03:** el estado vacío dice el rango (`minimo` a `siguiente`); como `minimo` viene de la misma lista, la prueba de 007-07 comprueba además que coincide con el `minYear` del rechazo (la captura `vigencias-vacia`);
    - **V‑05 · 007-22** (la antigua 007-08): un `combobox` llamado «Año de la vigencia» y `creatableYears.length` opciones más la vacía, en el orden y con el texto del servidor; `actual` viene elegido, con sus fechas (la captura `vigencias-crear`);
    - **007-21, teclado:** el grupo de radios pasa al `Select`; el foco inicial está en el `Select`; Enter al abrir no crea nada (el diálogo sigue abierto y no hay ningún `POST`); Tab da la vuelta dentro del diálogo. Elegir con el teclado depende de la plataforma: el valor se cambia con `selectOption` y el teclado se usa solo para el foco y Enter;
    - **V‑01 · 007-04:** crear `actual` la deja *Planeada*; con `minimo` elegido, aparece la advertencia de un año pasado;
    - **V‑02 · 007-06:** crear `siguiente`, `minimo` e `intermedio`, en ese orden, sin ningún rechazo de V‑02; la lista los muestra del más reciente al más antiguo (la captura `vigencias-lista` pasa a la lista de Ipiales con esas cuatro vigencias);
    - **V‑05 · 007-07, por la API, solo HTTP y sin crear nada:** crear `minimo − 1` da 422 `V-05` `PAST_LIMIT` con `minYear` igual a `minimo`, y `siguiente + 1` da `FUTURE_LIMIT` con `maxYear` igual a `siguiente`.
- [x] **T57** · 007-23 al final de la serie de B, y la corrida.
  - **Proyecto:** `e2e/` · **PR:** 4E
  - **Archivos:** `e2e/pruebas/vigencias.spec.ts`, `e2e/pruebas/vigencias.spec.ts-snapshots/` (`vigencias-sin-anios`, nueva) y `e2e/README.md`.
  - **Depende de:** T56.
  - **Recursos:** entorno local y capturas de referencia.
  - **Prueba primero** (rojo): **V‑05 · 007-23**, la última prueba de la serie de la administración B: crea por la API los años que le faltan a B, de `minimo` a `anioSiguiente`, comprueba que «Crear vigencia» queda inhabilitado con el motivo de `ALL_CREATED`, que no se activa con el teclado y que pasa axe (**007-21**), y lleva la captura nueva `vigencias-sin-anios`. Va al final porque deja a B sin años: las pruebas anteriores de B no cambian, y `limpiar-vigencias.sh` borra todo antes de la corrida siguiente. Se elimina de Ipiales la prueba «Sin años que crear», cuya premisa ya no se cumple.
  - **Antes del PR (ADR 0030; entorno exclusivo):** `npm test` en verde, con el sistema local, la rama de 4B2 aplicada y 4F aplicado, después de que la persona corra `backend/dev/limpiar-vigencias.sh`; la revisión de cada pantalla contra su *frame* de Figma queda hecha en T54. Las capturas de detalle no cambian (tienen máscara sobre los años). **La persona aprueba las cuatro capturas** (tres vueltas a aprobar y una nueva). El resultado se cita en el PR.
  - **Al terminar T55 a T57:** adversarial con dos `liv-revisor` y la lente de legibilidad (`liv-revisor-rapido`); la H3. Se integra **justo después de 4F**, en verde.

### Cierre de la enmienda 4 (orquestador)

Lo hizo el orquestador en el PR de cierre (2026-11-16), sobre `main` con los siete PR integrados. La validación de 4F fue la prueba de la persona en el entorno local con 4B2 y 4F aplicados, y las capturas las aprobó al integrar #211.

- [x] Los siete PR están integrados en el orden de arriba (4D, 4F0 y 4U; 4B1; y, seguidos y el mismo día, 4B2, 4F y 4E), con el CI en verde y la H3 de cada uno.
- [x] La persona publicó `liv-components` 0.5.0 en npm y `contract-tax-v1.1.0` está publicada; `frontend/api-spec/versions.json` y `package.json` las adoptan (`api:verify` en verde).
- [x] La revisión adversarial y las cuatro lentes de 4B1, 4B2 y 4U, y la adversarial de 4D (coherencia), 4F0, 4F y 4E, tienen sus hallazgos resueltos o justificados, con el resumen en cada PR.
- [x] La validación en local (ADR 0030) está citada en los PR de 4F0, 4F y 4E: `npm test` de `e2e/` en verde con 4B2, 4F y 4E aplicados, las pantallas comparadas con Figma y las cuatro capturas aprobadas por la persona.
- [x] En `spec.md`: vuelven a `[ ]` los criterios que cambian (007-03, 007-04, 007-06, 007-07, 007-08 y 007-21), se agregan 007-22 y 007-23, y se marcan `[x]` con las pruebas de los pasos 14 a 20 (la tabla «Trazabilidad de los criterios (E4)» del plan); 007-05, 007-09 a 007-12, 007-18 y 007-19 se vuelven a marcar con las pruebas nuevas (las de navegador y la del bloqueo); 007-01, 007-02 y 007-20 no cambian.
- [x] «Ajustes de la H3» de la rama de cierre: el bloqueo de creación se queda (H2‑16), con las dos pruebas de caracterización de T44.
- [x] `ESTADO.md` (tablero, bitácora y el *roadmap*) al día, con la referencia desactualizada de la constitución («Doc. 05 v0.1» en `mission.md` y `roadmap.md`), que cambiarla exige un ADR y decide la persona. Los términos nuevos del glosario, si los hay, los escribe el orquestador.
- [x] Las marcas `[x]` de T36 a T57 y las «Revisada por la enmienda 4» de T1 a T35 se concilian con las de la rama de cierre; las marcas de T1 a T35 no se quitan.

### Mantenimiento (enmienda 4)

- [ ] Si cambia el límite 2002 (`FiscalYearOpeningPolicy.FIRST_CREATABLE_YEAR`): versión nueva del Doc. 01 (V‑05), y a la vez `FiscalYearOpeningPolicyTest`, `FiscalYearsApiTest`, los ejemplos de `tax.yaml` y los textos del *frontend* de `PAST_LIMIT` y del estado vacío.
- [ ] Si se publica una versión de `liv-components` que cambia `Select`, el `Dialog` o `Button`: correr en el *frontend* la prueba del aviso de sesión, las del diálogo de vigencias y `accessibility.spec.ts`.
