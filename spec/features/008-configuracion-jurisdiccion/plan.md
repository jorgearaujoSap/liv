# 008 · Configuración de jurisdicción — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** borrador
_(borrador → aprobado (H2))_

## H2

Pendiente. La persona que dirige el trabajo aprueba en la H2, como arquitecto y como responsable funcional (`AGENTS.md` §4.3: hay pantallas, y el plan propone reglas, códigos y una enmienda a la spec):
- el plan;
- las decisiones H2‑1 a H2‑24 de «Decisiones para la H2», cada una con su recomendación;
- los dos altos de la ronda 2 de la revisión, corregidos en el plan sin una tercera ronda (la *skill* admite dos): la persona decide si basta o pide una verificación acotada antes de aprobar, como en la 007 (véase «Revisión»);
- la enmienda 1 a la spec (H2‑5), que firma de nuevo la H1 de 008-18; la escribe el orquestador en `spec.md`;
- el conjunto exacto de hallazgos de las pruebas *golden* (008-31), en «Pruebas *golden*».

## Enfoque

La 1.5.9 es el primer uso del motor de ingesta (`docs/arquitectura/01` §6) y el primer código del módulo `jurisdiction`, que es hexagonal completo (`ArchitectureRulesTest` ya lo trata así).

- **`jurisdiction` es dueño de todo lo específico:** el cargador de YAML de producción, el validador CJ01 a CJ18, el ciclo de vida de la versión y su tabla, en un esquema propio (`jurisdiction`, decisión 23; `docs/arquitectura/01` §3.1, nota 1).
- **`platform` tiene las piezas comunes del motor** (decisión 2), sin tablas propias ni *staging* por filas:
  - la recepción acotada con SHA‑256 y el tope del cuerpo;
  - la máquina de estados con la segregación de funciones;
  - el coordinador de la carga y de las transiciones, que decide qué va dentro de la transacción y qué fuera;
  - el registro de los intentos rechazados, que los audita aunque la operación no cambie nada.

  `platform` define las interfaces (el lector, las reglas, la guarda de la versión y el publicador), y `jurisdiction` las implementa en su capa `application` o `infrastructure`, nunca en `domain`.
- **El validador es dominio puro.** El dominio de `jurisdiction` solo depende de `shared` (`docs/arquitectura/01` §4.1), y una regla nueva de ArchUnit lo comprueba. CJ01 (el esquema JSON) es un adaptador detrás de un puerto, porque usa `json-schema-validator`. CJ02 a CJ18 y la comparación CJ11 recorren un árbol propio del dominio (`ConfigTree`), con `BigDecimal` y sin Jackson. No hay valores tributarios en el código: el validador compara contra lo que dice el propio archivo (decisión 14).
- **Ninguna transacción espera a la red.** El cuerpo se recibe, se mide, se le calcula el SHA‑256 y se carga con el cargador seguro **antes** de abrir la transacción. La transacción solo toma el bloqueo de la vigencia, lee el contexto, valida y guarda.
- **La base de datos hace cumplir lo que no debe depender de la aplicación:**
  - la inmutabilidad, con permisos por columna y un disparador de transiciones (008-41);
  - una sola versión publicada y una sola en curso por vigencia, con índices únicos parciales (008-10 y 008-38);
  - que un reemplazo apunte a la versión que se publica en la misma vigencia;
  - la segregación, con `CHECK` (008-33);
  - el aislamiento, con RLS y FK compuestas (008-44).
- **Las dependencias van en un solo sentido** (decisión 16; ADR 0037):
  - `tax → jurisdiction`: `jurisdiction` define el puerto de la vigencia y `tax` lo implementa; `tax` implementa el punto 9 con la fachada de la versión publicada (008-42 y 008-43);
  - `jurisdiction → core`, `platform` y `shared`;
  - `audit → jurisdiction` y `audit → platform`: `audit` escucha sus eventos (ADR 0025 y 0035).
- **Un PR por proyecto** (`AGENTS.md` §4.6), de unas 400 líneas como máximo:
  - la raíz: los documentos y los ADR;
  - `backend/`: el bloque B (cargador y validador) y el bloque C (motor, ciclo de vida, API, auditoría, punto 9 y BFF);
  - `ui/`: el bloque D (`liv-components` 0.6.0);
  - `frontend/` y `e2e/`: el bloque E.

  El mapa está en «Mapa de los PR».

## Ajustes al modelo de datos

Los aprueba la H2 (decisión 23 de la H1; H2‑1 y H2‑2). Van al ERD 2.5 en el PR de documentos (R1).

**Esquema `jurisdiction`** (nuevo). Reemplaza a `core.jurisdiction_config` del Doc. 03 §5.6, que nunca se migró ni está entre las 80 tablas del ERD 2.4. La tabla conserva su nombre y cambia de esquema, como propone `docs/arquitectura/01` §3.1 (nota 1). El ERD pasa a 81 tablas en 10 esquemas funcionales.

**`jurisdiction.jurisdiction_config`** (una fila por carga; «versión» en la interfaz)

| Columna | Tipo | Regla |
|---|---|---|
| `id` | `UUID` PK `DEFAULT uuidv7()` | — |
| `administration_id` | `UUID NOT NULL` | FK a `core.tax_administration (id)` |
| `fiscal_year` | `SMALLINT NOT NULL` | FK compuesta (`administration_id`, `fiscal_year`) a `tax.administration_fiscal_year`, que ya tiene ese `UNIQUE` |
| `version_number` | `INTEGER NOT NULL` | `CHECK (version_number > 0)`. Es el número que le toca (008-26): el de la última versión publicada más uno, o 1. No es el que declara el archivo: si no coinciden, CJ17 da error y la carga queda *Con errores* con este número |
| `file_sha256` | `CHAR(64) NOT NULL` | `CHECK (file_sha256 ~ '^[0-9a-f]{64}$')`, calculado al recibir (008-05) |
| `original_file` | `BYTEA NOT NULL` | `CHECK (octet_length(original_file) BETWEEN 1 AND 1048576)`. Excepción temporal del ADR 0036 (decisión 10). Reemplaza a `source_storage_key`, que llega con la feature 1.3 |
| `content` | `JSONB NOT NULL` | El árbol que dio el cargador seguro. JSONB guarda los números como `numeric`, con su escala (`0.10` sigue siendo `0.10`; 008-30) |
| `schema_version` | `VARCHAR(10)` | El `esquema_version` declarado. `CHECK`: no puede ser nulo salvo en *Con errores*, cuando CJ01 no pudo leerlo |
| `validator_version` | `VARCHAR(20) NOT NULL` | `MAYOR.MENOR.PARCHE` del validador (decisión 17), `CHECK` de forma |
| `validation_result` | `JSONB NOT NULL` | Hallazgos, lista de revisión y comparación CJ11 («Datos, API y seguridad») |
| `validation_digest` | `CHAR(64) NOT NULL` | SHA‑256 de la forma canónica de los hallazgos y de la lista de revisión. Identifica el resultado (008-34 y 008-39) |
| `compared_with_id` | `UUID` | La versión publicada contra la que se comparó (CJ11); nulo si era la primera. FK compuesta (`compared_with_id`, `administration_id`, `fiscal_year`) a la misma tabla: no puede ser de otra vigencia. `CHECK (compared_with_id <> id)` |
| `status` | `VARCHAR(20) NOT NULL` | `CHECK (status IN ('CON_ERRORES', 'VALIDADA', 'APROBADA', 'PUBLICADA', 'REEMPLAZADA', 'RECHAZADA', 'ANULADA'))`. *Cargada* no se guarda: es solo de paso (decisión 7; 008-05) |
| `uploaded_by` | `UUID NOT NULL` | FK compuesta (`uploaded_by`, `administration_id`) a `core.admin_user (id, administration_id)` |
| `uploaded_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Lo pone la base de datos: `liv_app` no lo escribe |
| `approved_by`, `approved_at` | `UUID`, `TIMESTAMPTZ` | FK compuesta a `core.admin_user` |
| `approval_marks` | `JSONB` | Rutas marcadas como revisadas y aceptación de las advertencias (008-34) |
| `approved_validation_digest` | `CHAR(64)` | El `validation_digest` que vio y envió el aprobador (H2‑21) |
| `rejected_by`, `rejected_at`, `rejection_reason` | `UUID`, `TIMESTAMPTZ`, `VARCHAR(500)` | 008-35; FK compuesta a `core.admin_user` |
| `annulled_by`, `annulled_at`, `annulment_reason` | `UUID`, `TIMESTAMPTZ`, `VARCHAR(500)` | 008-36; `CHECK (annulled_by = uploaded_by)` |
| `published_by`, `published_at` | `UUID`, `TIMESTAMPTZ` | 008-38; FK compuesta a `core.admin_user` |
| `superseded_by`, `superseded_at` | `UUID`, `TIMESTAMPTZ` | FK compuesta (`superseded_by`, `administration_id`, `fiscal_year`) a la misma tabla; `CHECK (superseded_by <> id)` |

- **Restricciones únicas e índices:**
  - `UNIQUE (id, administration_id)`, para la FK de `ledger.tax_obligation`;
  - `UNIQUE (id, administration_id, fiscal_year)`, para las FK de la propia tabla;
  - `UNIQUE (administration_id, fiscal_year) WHERE status = 'PUBLICADA'` (Doc. 03 §5.6; 008-38);
  - `UNIQUE (administration_id, fiscal_year) WHERE status IN ('VALIDADA', 'APROBADA')`: una sola en curso (decisión 8; 008-10, también con dos cargas a la vez);
  - `UNIQUE (administration_id, fiscal_year, version_number) WHERE status IN ('PUBLICADA', 'REEMPLAZADA')` (008-26).
- **`CHECK` por estado.** Los datos de cada transición existen solo cuando el estado los pide:
  - `approved_*`, `approval_marks` y `approved_validation_digest` van siempre juntos, nunca a medias: todos en *Aprobada*, *Publicada* y *Reemplazada*; en *Rechazada*, todos si venía de *Aprobada* (para que el historial diga quién la aprobó; 008-02 y 008-35) y ninguno si venía de *Validada*; ninguno en los demás estados;
  - *Publicada* y *Reemplazada* tienen `published_*`;
  - *Reemplazada* tiene `superseded_*`;
  - *Rechazada* tiene `rejected_*`, con motivo no vacío;
  - *Anulada* tiene `annulled_*`, con motivo no vacío.
- **Segregación en la base de datos** (008-33; `docs/arquitectura/02` §4.3): `CHECK (approved_by <> uploaded_by)`, `CHECK (published_by <> uploaded_by)` y `CHECK (rejected_by <> uploaded_by)`. El dominio y `platform` lo rechazan antes con su código. Es una defensa en profundidad con un límite: la base de datos no conoce al usuario de la sesión, así que confía en que la aplicación escriba el actor real. Lo que la protege es que ningún caso de uso recibe el actor del cliente: sale siempre de `CurrentUser`.
- **Disparador de transiciones** `jurisdiction.jurisdiction_config_guard()`, `BEFORE UPDATE`, del dueño de los objetos:
  - solo admite las transiciones de la decisión 7: *Validada* → *Aprobada*, *Rechazada* o *Anulada*; *Aprobada* → *Publicada* o *Rechazada*; *Publicada* → *Reemplazada*;
  - rechaza cualquier cambio de una columna que la transición no escribe. En particular, *Aprobada* → *Rechazada* conserva los `approved_*`, y nada reescribe los datos de una transición anterior (008-41);
  - *Publicada* → *Reemplazada* exige que `superseded_by` sea otra fila de la misma administración y vigencia, en *Aprobada* en ese momento y con `version_number` mayor.

  Un segundo disparador, de restricción y diferido hasta el *commit* (`CONSTRAINT TRIGGER … DEFERRABLE INITIALLY DEFERRED`), comprueba que la fila de `superseded_by` quedó *Publicada* en la misma transacción. Así `liv_app` no puede «despublicar» una vigencia sin publicar la versión que la reemplaza. Los permisos por columna impiden escribir las columnas inmutables; los disparadores impiden reescribir las de una transición anterior y los reemplazos sin publicación.
- **RLS**, una política por operación, como `tax.administration_fiscal_year`. Todas son `TO liv_app` sobre `NULLIF(current_setting('app.administration_id', true), '')::uuid`:
  - `SELECT`, de la administración;
  - `INSERT`, de la administración y solo con `status IN ('CON_ERRORES', 'VALIDADA')`;
  - `UPDATE`, con `USING` sobre la administración y `status IN ('VALIDADA', 'APROBADA', 'PUBLICADA')`, y `WITH CHECK` sobre la administración;
  - ninguna de `DELETE`.
- **Permisos de `liv_app`**, por columna como en la 007 (`V202611141000`):
  - `SELECT` de la tabla;
  - `INSERT` solo de `administration_id`, `fiscal_year`, `version_number`, `file_sha256`, `original_file`, `content`, `schema_version`, `validator_version`, `validation_result`, `validation_digest`, `compared_with_id`, `status` y `uploaded_by`: `id` y `uploaded_at` los pone la base de datos;
  - `UPDATE` solo de `status` y de las columnas de las transiciones (`approved_*`, `approval_marks`, `approved_validation_digest`, `rejected_*`, `annulled_*`, `published_*` y `superseded_*`);
  - sin `DELETE` (008-41).
- **Esquema:** la migración crea `jurisdiction`, con `GRANT USAGE` a `liv_app`.

**`ledger.tax_obligation`.** El ERD 2.5 cambia su FK a (`jurisdiction_config_id`, `administration_id`) → `jurisdiction.jurisdiction_config (id, administration_id)`. La tabla aún no existe: la migra el Módulo 4.

**Lo que no cambia todavía** (decisión 1): `tax.tax_rate`, `tax.tax_calendar`, `tax.base_adjustment_rule`, `tax.penalty_rule`, `documents.legal_instrument` y `ledger.revenue_distribution` siguen sin migrar. Las llena la feature de cada tabla desde lo publicado. El ERD 2.5 lo anota en cada una.

### Lista de aceptación previa a Flyway (ERD)

La lista sigue abierta. La 001 y la 007 pidieron autorización para migrar sus tablas sin cerrarla; la 008 hace lo mismo con su tabla (H2‑1).

| Punto de la lista | En la 008 |
|---|---|
| FK de la misma entidad territorial | Se cumple: FK compuestas con `administration_id` (y con `fiscal_year` entre versiones) y RLS |
| Catálogos publicados congelados | Se cumple para lo suyo: la versión publicada es inmutable (permisos por columna y disparadores; 008-41) |
| CSV, *staging*, aprobación por otro usuario, publicación transaccional, `UNIQUE` de catálogo y lote | En parte: no hay CSV ni *staging* por filas (decisión 2). Sí hay aprobación por otro usuario (`CHECK` y dominio), publicación en una transacción y `UNIQUE` parciales |
| Índices únicos e intervalos sin solapamientos | Se cumple para lo suyo: una publicada y una en curso por vigencia |
| Reglas de redondeo, facturación y sanciones | No aplica: se validan y se guardan, no se aplican (Fase 2) |
| Controles de auditoría y permisos de funcionarios | Se cumple para lo suyo: auditoría por evento (C7) y permisos de acción con su prueba de matriz (C1a) |
| Validar el ERD con expertos tributarios antes de migrar | Se pide en H2‑1: autorizar sin esa validación, como en la 001 y la 007. La tabla guarda el archivo, no reglas de cálculo |
| Carga histórica, conciliación, *rollback* e idempotencia | En parte: la publicación es atómica y una carga fallida no deja nada (008-05) |
| Cartera histórica, prescripción y cobro coactivo | No aplica |

## Decisiones para la H2

_Lo que la persona aprueba con este plan, además del plan en sí (`AGENTS.md` §4.5). Cada una trae la recomendación. Las que cambian una regla o la spec las decide como responsable funcional._

| # | Decisión | Recomendación |
|---|---|---|
| H2‑1 | Migrar `jurisdiction.jurisdiction_config` con la lista previa a Flyway abierta, sin la validación del ERD por expertos tributarios | **Autorizar**, como en la 001 y la 007: la tabla guarda el archivo y su resultado, no reglas de cálculo |
| H2‑2 | El modelo de «Ajustes al modelo de datos»: una tabla en el esquema nuevo `jurisdiction`, el original en `BYTEA` (ADR 0036), los dos disparadores, los `CHECK` de estado y de segregación, las políticas RLS y los permisos por columna | **Aprobar.** Sin tablas de `platform`: las piezas comunes son código, y la guarda de la versión es de `jurisdiction` (decisión 2) |
| H2‑3 | Permisos nuevos `jurisdiction.config.upload`, `.approve`, `.publish` y `.download`, con los roles de la decisión 6, en la matriz única de `PlatformTablesTest` | **Aprobar.** Es la tabla de la decisión 6 |
| H2‑4 | **Códigos de rechazo nuevos (cambian el Doc. 01, el Doc. 03 y `AGENTS.md` §5.3):** `CG-01` a `CG-06` del motor de ingesta y `CJC-01` a `CJC-04` de la 1.5.9, con sus estados HTTP. El detalle está en «Códigos de rechazo (H2‑4)», abajo | **Aprobar.** Un contrato no publica números de criterios como códigos de error (precedente H2‑8 de la 007). `F001` a `F003` del Doc. 01 §6.5 siguen siendo las reglas de los archivos CSV de catálogos; la feature 1.5.2 decide si `F003` pasa a ser `CG-03`. `AGENTS.md` §5.3 agrega los prefijos `CG` y `CJC` |
| H2‑5 | **Enmienda 1 a la spec: CJ08 sin DANE (cambia 008-18).** La plantilla trae el ajuste `SAN_ANDRES` y su DANE es `PENDIENTE`. Al pie de la letra, CJ08 da un error, y 008-31 espera solo CJ02, CJ12, CJ16 y CJ17 | **CJ08 es *no evaluable* si `jurisdiccion.codigo_dane` está en `PENDIENTE`** (motivo `DANE_PENDING`): sin DANE no se puede comparar, y CJ17 ya bloquea. 008-18 agrega esa frase y la H1 se firma de nuevo para ese criterio. *Alternativa:* error de CJ08, con la enmienda en 008-31 |
| H2‑6 | **CJ02 sin modalidades que liquiden.** En la plantilla ninguna modalidad está activa, y `jurisdiccion`, `calendario` y `redondeo` están en `PENDIENTE`. Lo mismo le pasaría a un municipio solo del 20 % que copie `calendario` de la plantilla | **Son errores de CJ02.** La lista del Doc. 03 §5.4 no los condiciona (solo `intereses_mora` y `prescripcion` lo están), y `calendario` y `redondeo` traen `obligatoria: true` (decisión 11). CJ02 solo mira las reglas presentes: el municipio del 20 % pasa si no trae esas secciones (008-28) y falla si las trae en `PENDIENTE`. B8 prueba los dos casos. *Alternativa:* condicionar `calendario` y `redondeo` a que alguna modalidad liquide, como las fechas de CJ13 |
| H2‑7 | El conjunto exacto de hallazgos de las pruebas *golden* («Pruebas *golden*») | **Aprobar.** Coincide con el §9.2 en conteos (Ipiales: 0 errores, 17 advertencias, 19 por confirmar) y lo amplía con las rutas, los códigos, CJ17, CJ18 y la marca de la `tarifa_maxima`. Lo recalcularon los dos revisores sobre los anexos |
| H2‑8 | **Topes de tamaño** (decisión 10; 008-07), iguales en el BFF y en la API, como dice 008-07, con un aviso del BFF a la API para auditar lo que pasa del tope. El detalle está en «Topes de tamaño (H2‑8)», abajo | **Aprobar.** El BFF nunca reenvía un cuerpo que la API cortaría (lo que daría un 502), y el intento queda auditado salvo si falla el aviso (riesgo residual, aceptado y medido). Si el navegador pierde la conexión al subir un archivo de más de 1 MiB (Tomcat solo descarta hasta 2 MB antes de cerrarla), la pantalla muestra el mensaje de `TAMANO`. *Alternativa:* cortar en el BFF solo con un registro (*log*), sin auditoría por encima del tope, que exige enmendar 008-07 |
| H2‑9 | **La carga es el cuerpo crudo** (`application/octet-stream`) en `POST /api/v1/jurisdiction-configs/{year}/versions`, sin `multipart/form-data` ni nombre de archivo | **Aprobar.** No hay sobre que cambie los bytes ni nombre del cliente que guardar (008-04: el nombre lo genera el servidor). Ningún punto acepta `application/yaml` (008-08) |
| H2‑10 | **Auditoría de un rechazo** (decisión 18; 008-07, 008-33 y 008-47). El coordinador de `platform` no es transaccional: abre la transacción del negocio con `TransactionTemplate`, y si termina con un rechazo auditable (`CG-01`, `CG-06` o el tope), la cierra primero y después registra el intento con el puerto `RejectedAttemptLog`. Este abre su propia transacción, con el contexto de la administración, y publica `IngestionAttemptRejected` por el registro persistente de eventos. Es el mecanismo de `JdbcSessionStore.denied` de la 001, que usa `REQUIRES_NEW`; aquí se llama sin transacción activa (lo comprueba con `TransactionSynchronizationManager.isActualTransactionActive()` y falla si la hay), así que nunca hay dos conexiones a la vez. Si su transacción falla, la respuesta no cambia y el fallo queda en el registro con nivel `ERROR` y una métrica | **Aprobar sin ADR nuevo:** no cambia el patrón del ADR 0025. El ADR 0038 lo describe como parte del motor |
| H2‑11 | **Cinco ADR** (R2a y R2b): 0036, el original en la base de datos (decisión 10); 0037, las dependencias entre módulos (decisión 16), que agrega `jurisdiction → core` porque CJ17 lee el DANE y el tipo de la entidad territorial (la flecha ya está en la figura de `docs/arquitectura/01` §3.2, pero no en la decisión 16 ni en `ModularityTest`); 0038, el motor de ingesta en `platform` (decisión 2), con su interfaz con nombre de Spring Modulith, la primera del proyecto; 0039, el cargador de producción (decisión 3), porque el ADR 0032 dice que `snakeyaml` **no** se declara y la decisión 3 pide declararlo; 0040, el tope de la carga en el BFF y su aviso a la API por `platform.yaml` (H2‑8), sin tocar las cadenas de seguridad | **Aprobar que se redacten** con la *skill* `liv-adr`. Cada ADR se acepta en su PR |
| H2‑12 | **Pantalla añadida por el diseño:** «Versiones de la vigencia» (95:5176 y 95:5516), aparte de la lista. La spec lista cuatro pantallas y pone el historial en 008-02 | **No hace falta un criterio nuevo:** 008-02 ya describe el historial; el diseño solo le da pantalla propia. La prueba de 008-02 cubre la pantalla y su estado vacío. *Alternativa:* un criterio 008-54 para el estado vacío |
| H2‑13 | **`liv-components` 0.6.0 en una serie de PR** (U1 a U4): `ui/AGENTS.md` pide subir la versión en el mismo PR | **Excepción como la H2‑15 de la 007:** los tres primeros van a «Sin publicar», y el U4 sube a 0.6.0. La persona publica en npm después de integrar el U4 |
| H2‑14 | **Documentos que cambian además de la tabla de la spec:** `AGENTS.md` §3.1 (81 tablas en 10 esquemas funcionales, ERD 2.5), §5.2 (la ruta real `base_gravable.catalogo_ministerio.multiplicador_tablas`) y §5.3 (prefijos de H2‑4); los códigos de H2‑4 en el Doc. 01 §2 y en el Doc. 03 §5.3; y el Doc. 01 §6.1 (el punto 9 lee `jurisdiction.jurisdiction_config`) | **Aprobar.** Van en el PR R1 |
| H2‑15 | **Repetir las pruebas de `e2e/`:** las versiones no se pueden borrar y tienen FK a la vigencia. `backend/dev/limpiar-vigencias.sh` vacía también `jurisdiction.jurisdiction_config` antes que las vigencias, solo en la base **local** | **Aprobar.** Es la extensión del comando de la H2‑9 de la 007, con las mismas guardas |
| H2‑16 | **El aprobador marca 20 puntos en Ipiales:** las 19 reglas `POR_CONFIRMAR` y la `tarifa_maxima` de `ELECTRICO` (decisión 14; 008-34). La lista de revisión es una sola, con la clase de cada punto | **Aprobar.** Es lo que dicen 008-16 y 008-34; el §9.2 cuenta 19 porque no tenía la regla de la `tarifa_maxima` |
| H2‑17 | **La versión del validador** empieza en `1.0.0`, escrita en el backend (`JurisdictionConfigValidator.VERSION`), y sube con cualquier cambio de una regla CJ: el menor con una regla nueva o más estricta, el parche con una corrección que no cambia ningún resultado *golden* | **Aprobar** (decisión 17) |
| H2‑18 | **U1 modifica `Icon`**, un componente existente de `liv-components` (`AGENTS.md` §4.5): agrega `check`, `x`, `upload` y `download`, como diseña 88:506 | **Aprobar** la modificación. Es un cambio crítico de `ui/**`, compatible (solo agrega nombres) |
| H2‑19 | **CJ18 y las fechas anuales (pregunta funcional).** El esquema 1.1 tiene fechas `MM-DD` (por ejemplo, `causacion.general: "01-01"`) cuyo patrón acepta `02-30`. 008-27 solo habla de fechas «con la forma correcta». ¿Las comprueba CJ18, y qué pasa con `02-29`? | **CJ18 comprueba también las `MM-DD`, contra el año de `vigencia.anio`:** `02-30` es siempre un error, y `02-29` lo es en un año que no es bisiesto. Cada configuración es de una sola vigencia, así que su año está definido. *Alternativa:* aceptar `02-29` siempre, como fecha anual genérica |
| H2‑20 | **CJ12 y las fechas del convenio (pregunta funcional).** El Doc. 03 §9.5 menciona «fechas invertidas» entre las variantes probadas, sin decir de qué fechas: pueden ser las del convenio (CJ12) o las del calendario (que ya cubre CJ13). CJ12 solo exige que el convenio tenga número y fechas | **Un convenio activo con `fecha_inicio` posterior a `fecha_fin` es un error de CJ12** (motivo `AGREEMENT_DATES_INVERTED`), y el Doc. 03 v0.6 lo agrega al texto de CJ12. Si la persona dice que el §9.5 hablaba del calendario, igual conviene la regla, porque un convenio invertido bloquearía toda liquidación de matriculados. *Alternativa:* no comprobarlo |
| H2‑21 | **Las marcas que faltan y el ADR 0035.** 008-34 pide decir cuáles faltan, y el ADR 0035 solo admite extensiones escalares en el Problem Details | **`CJC-02` lleva `missingCount` y `warningsAccepted`**, y la pantalla dice cuáles faltan comparando sus marcas con `reviewItems`. Aprobar solo se habilita con todo marcado (99:8566 y 99:8817), así que el rechazo solo llega en una carrera o por la API. El cuerpo de la aprobación lleva el `validationDigest` que mostró la pantalla, y el servidor lo compara con el de la fila. *Alternativa:* un ADR que admita listas de texto acotadas en las extensiones |
| H2‑22 | **El catálogo cerrado de motivos** de cada regla («Catálogo de motivos»), que el contrato documenta y con el que la pantalla arma los textos. Un motivo nuevo es una versión menor del contrato y del validador | **Aprobar.** Fijarlo ahora deja publicar el contrato (C2a) antes que el validador, sin cambiarlo después |
| H2‑23 | **Dos versiones del contrato en la serie** (ADR 0027: la CI publica cada versión al integrar, y dos PR no pueden tener la misma). C2a publica `jurisdiction.yaml` 1.0.0 con las consultas, y C2b, 1.1.0 con las acciones (compatible: solo agrega operaciones) | **Aprobar.** El *frontend* (F0) se fija a 1.1.0. *Alternativa:* un solo PR de contrato de unas 650 líneas |
| H2‑24 | **La segregación en el dominio y en el motor.** 008-33 dice que la regla la aplica el dominio. El agregado `JurisdictionConfigVersion` comprueba él mismo el actor y el estado de origen (`CG-01` y `CG-02`, con `shared.RuleViolation`, sin depender de `platform`), y `IngestionLifecycle` de `platform` lo comprueba también, como pieza común del motor | **Aprobar.** Es una comparación de dos UUID en Java puro, así que el dominio protege sus invariantes aunque alguien lo use fuera del coordinador |

### Códigos de rechazo (H2‑4)

| Código | Documento | Cuándo | HTTP |
|---|---|---|---|
| `CG-01` | Doc. 01 §2 (motor de ingesta) | Segregación: el cargador no aprueba, rechaza ni publica; solo el cargador anula | 422 |
| `CG-02` | Doc. 01 §2 | Transición no permitida desde el estado actual | 409 |
| `CG-03` | Doc. 01 §2 | Archivo duplicado: mismo SHA‑256 que la versión publicada o la en curso de la vigencia, con la versión que coincide | 409 |
| `CG-04` | Doc. 01 §2 | Ya hay una carga en curso | 409 |
| `CG-05` | Doc. 01 §2 | Falta el motivo | 422 |
| `CG-06` | Doc. 01 §2 | Archivo rechazado antes de interpretarlo, con el `motivo` de los casos comunes (`CODIFICACION`, `ALIAS`…) | 422; **413 con el motivo `TAMANO`** |
| `CJC-01` | Doc. 03 §5.3 (solo la 1.5.9) | La vigencia existe pero no está *En preparación*. Una *Cerrada* da `V-04`; una que no existe da el 404 `SYS-404` de `shared.NotFound`, como cualquier recurso ajeno o inexistente | 409 |
| `CJC-02` | Doc. 03 §5.3 | Faltan marcas o la aceptación de las advertencias, o el `digest` no coincide | 422 |
| `CJC-03` | Doc. 03 §5.3 | El resultado es de otra versión del validador | 409 |
| `CJC-04` | Doc. 03 §5.3 | Una comprobación de la publicación falló (008-39) | 409 |

- Los estados siguen el ADR 0035: 409 cuando el estado lo impide y 422 cuando la regla rechaza la petición.
- **Única excepción:** el 413 de `CG-06` `TAMANO`, con una excepción propia de `platform` (`PayloadTooLarge`) que traduce `ProblemDetailsHandler`. El ADR 0038 la registra como excepción al ADR 0035.
- En el contrato y en el código, los códigos llevan el guion ASCII (`V-04`, `CG-01`).

### Topes de tamaño (H2‑8)

- **Topes:** archivo de hasta 1 MiB (1.048.576 bytes) y tope duro de 1 MiB + 64 KiB (1.114.112 bytes; el cuerpo es crudo, sin sobre). La ruta de carga exige `Content-Length` en el BFF y en la API (si falta, 411), así que el tope se decide solo por la cabecera y cada rechazo tiene un solo dueño.
- **Hasta el tope:** la API lee, calcula el SHA‑256 y da 413 `CG-06` `TAMANO`, auditado con el SHA‑256.
- **Por encima del tope:** nadie lee el cuerpo.
  - El BFF no lo reenvía. En lugar de la carga, llama con el *token* de la sesión a la operación pública `reportOversizeUpload` de `platform.yaml` 1.1.0.
  - Esa operación pasa por la cadena normal de `/api/**` (usuario, administración y permiso de la ruta, sin cambiar la autenticación) y audita el intento sin SHA‑256.
  - El BFF responde lo que ella decida: 403 sin el permiso de carga, 413 con él.
- **Si el aviso falla:** el BFF responde 413 y deja el fallo en su registro (*log*) con nivel `ERROR`.

## Implementación

_Los pasos están en orden de dependencia. Cada uno dice su proyecto, su *skill* y sus dependencias; «Dependencias, paralelismo y recursos» dice qué corre a la vez. Todo PR del backend corre `dev/check.sh`, y todo PR, la lente de legibilidad antes de cada *commit*. Ninguna clase pasa de 400 líneas ni ningún controlador de 200 (`CodeSizeGuardTest`, CC‑11)._

### 0. Diseño en Figma — ✅ aprobado el 2026-11-16 (raíz, Figma)

Decisión 20. La persona aprobó el 2026-11-16 las pantallas de la sección «1.5.9 · Configuración de jurisdicción» (92:4179) y los componentes nuevos. Los nodos están en «Diseño de interfaz». Cada PR de `ui/` y de `frontend/` lee su nodo con la *skill* `liv-figma` antes de escribir.

### R1. Documentos (raíz, PR propio) — `docs(008)`

Se integra **antes** del C1a y del C2a: la prueba de la matriz de permisos y el documento deben coincidir (`docs/arquitectura/02` §4.4), y el contrato publica los códigos del documento. Es la tabla «Documentos que cambian» de la spec, más H2‑4, H2‑14, H2‑19 y H2‑20:

- **Doc. 03 v0.6:**
  - §5.2: la fila `vigencia` (se valida contra la vigencia de la administración, no se materializa); «se materializa en» pasa a «la llena después la feature de cada tabla»; la nota de la precedencia;
  - §5.3: *Rechazada* y *Anulada*; la publicación guarda la copia inmutable (deja de decir «materialización transaccional»); los códigos `CJC-01` a `CJC-04` (H2‑4);
  - §5.4: CJ17 y CJ18 (con las fechas `MM-DD`, H2‑19); el alcance de CJ02 (decisión 11 y H2‑6), CJ04 y CJ06 (decisión 12), CJ05 con la `tarifa_maxima` (decisión 14), CJ08 *no evaluable* sin DANE (H2‑5) y CJ12 con las fechas del convenio (H2‑20); las tres clases de hallazgo y la regla de la advertencia por ruta (decisión 13); CJ09 y CJ10 *no evaluables* (decisión 5); la versión del validador (decisión 17);
  - §5.5: la precedencia (decisión 21);
  - §5.6: el esquema `jurisdiction` y las columnas de «Ajustes al modelo de datos»;
  - §7: los cambios a otros documentos de la v0.6;
  - §8: el pendiente 9 resuelto y la pregunta de la precedencia;
  - §9.2: CJ09, CJ10, CJ17 y CJ18, la marca de la `tarifa_maxima` (H2‑16) y la remisión a las pruebas *golden*; los SHA‑256 no cambian;
  - §9.4 y §9.5: el administrador no activa las modalidades por fuera del flujo de la 1.5.9 (las declara el archivo de `CARGADOR_TABLAS` y las revisa quien aprueba; decisión 2), y `ADMINISTRACION_DIRECTA` solo para `DEPARTAMENTO` o `DISTRITO_CAPITAL` (decisión 15).
- **Doc. 01 v0.7:**
  - §2: los códigos `CG-01` a `CG-06` del motor de ingesta (H2‑4);
  - §3: la 1.5.9 → `jurisdiction.jurisdiction_config`;
  - §4: las filas de la 1.5.9 en la matriz de acciones y la tabla de sus permisos de acción (decisión 6);
  - §6.1: el punto 9 lee `jurisdiction.jurisdiction_config` con `status = PUBLICADA`, y los puntos 4 a 7 verificarán lo que llene cada feature (decisión 1);
  - §6.7: las opciones 1.5.5 a 1.5.8 solo muestran lo publicado;
  - §8: los eventos de la 1.5.9 (008-46);
  - §11: el pendiente 8 para la 1.5.9;
  - §13: los cambios de la v0.7 a otros documentos.
- **Doc. 05 v0.3:** §5.1, las sanciones se leen de la configuración publicada y la 1.5.8 solo las muestra.
- **ERD 2.5:** el esquema `jurisdiction` y su tabla con RLS y permisos; la FK de `ledger.tax_obligation`; las notas de las tablas que se llenarán desde lo publicado; la autorización H2‑1 en la lista previa a Flyway; 81 tablas.
- **`AGENTS.md`:** §3.1, §5.2 (`ADMINISTRACION_DIRECTA`, decisión 15, y la ruta del multiplicador) y §5.3 (H2‑14). Es un cambio de las reglas del proyecto y lo aprueba la persona en este PR.
- No toca los anexos YAML (fila «Anexos YAML» de la spec: van con la feature aparte) ni `spec.md` (la enmienda 1 la escribe el orquestador).

### R2a y R2b. ADR 0036 a 0040 (raíz, dos PR) — *skill* `liv-adr`

En paralelo con R1. R2a se integra antes del C1a; R2b, antes del B1 y del C3.

- **R2a · ADR 0036 · El original de la configuración en la base de datos (excepción temporal)** (decisión 10): `BYTEA` de hasta 1 MiB, solo texto UTF‑8 que el cargador seguro acepta, inmutable, servido como adjunto por la API. Termina con la feature de la opción 1.3 o antes de producción, lo que llegue primero. Esa feature trae ClamAV y el almacenamiento de objetos, escanea los originales guardados y los migra con una migración que conserva el SHA‑256. Actualiza `docs/arquitectura/02` §7 con la remisión.
- **R2a · ADR 0037 · Dependencias entre módulos de la feature 008** (decisión 16 y H2‑11):
  - `tax → jurisdiction`: `jurisdiction` define en su paquete raíz el puerto `FiscalYearDirectory` (las vigencias de la administración, con su estado y sus fechas) y la fachada `PublishedJurisdictionConfigs` (008-42); `tax` implementa el primero y usa la segunda en el punto 9;
  - `jurisdiction → platform` (permisos, usuario, contexto de la administración y motor de ingesta), `jurisdiction → shared` (`RuleViolation`, `NotFound`) y `jurisdiction → core` (`AdministrationTerritory`: el DANE y el tipo de la entidad territorial, para CJ17);
  - `audit → jurisdiction` (eventos);
  - nadie depende de `audit`, `jurisdiction` no depende de `tax`, y `jurisdiction.domain` no depende de ningún otro módulo salvo `shared`.

  Actualiza la figura de `docs/arquitectura/01` §3.2 y la nota 1 del §3.1.
- **R2b · ADR 0038 · Piezas comunes del motor de ingesta en `platform`** (decisión 2):
  - qué piezas son comunes («Enfoque») y su lugar, `platform.ingestion`, publicado como interfaz con nombre de Spring Modulith (`@NamedInterface("ingestion")`), la primera del proyecto;
  - el coordinador `IngestionEngine` y quién llama a cada interfaz (C3): `IngestionReader` y `IngestionRules` en la carga, `VersionStore` en la carga y las transiciones, `IngestionPublisher` en la publicación;
  - el registro de intentos rechazados (H2‑10);
  - los ocho estados de la decisión 7 y los códigos `CG`;
  - la excepción `PayloadTooLarge` (413), la única fuera de los tipos del ADR 0035 (H2‑4).

  Alinea el diagrama de `docs/arquitectura/01` §6 (RECIBIDO y RECHAZADO dejan de ser estados guardados: un archivo rechazado antes de interpretarlo no se guarda, solo se audita) y su §5.3 (cuándo se construye cada materialización; la validación de los anexos, IN‑11, llega con la feature aparte).
- **R2b · ADR 0039 · Cargador de YAML de producción** (decisión 3): `org.yaml:snakeyaml` declarado en `compile`, con la versión de Spring Boot (cambia lo que decía el ADR 0032), y `json-schema-validator` 3.0.9 a `compile`, con `jackson-dataformat-yaml` excluido; ningún convertidor de `application/yaml`; un solo cargador, que usan también las pruebas del esquema y de los casos comunes (ADR 0034). No es una dependencia nueva: las dos ya están en el árbol.
- **R2b · ADR 0040 · El tope de la carga en el BFF** (H2‑8): el BFF exige `Content-Length` en la ruta de carga y, por encima del tope, no reenvía el cuerpo y llama a la operación pública `reportOversizeUpload` de `platform.yaml` 1.1.0 con el *token* de la sesión, ya renovado. La operación pasa por la cadena normal de `/api/**`: no cambia ninguna cadena de seguridad ni la autenticación (`AGENTS.md` §4.5).

`docs/arquitectura/01` y `02` suben de versión en el PR que los toca.

### Bloque B — Cargador y validador (`backend/`)

Java puro en `jurisdiction/domain`, salvo el cargador y CJ01, que son adaptadores en `jurisdiction/infrastructure`. Cada regla con su prueba primero y el código de la regla en el `@DisplayName` (por ejemplo, «CJ04 · 008-15 · un hueco entre rangos es un error»). Los mensajes son motivos del «Catálogo de motivos»: el texto lo arma la pantalla.

#### B1. Cargador seguro de producción y `.gitattributes`

Después de R2b (ADR 0039).
- **Rojo:**
  - `JurisdictionConfigCommonCasesTest` y `SafeYamlLoaderTest` pasan a usar la clase de producción (no compilan);
  - una prueba nueva, `NoYamlConverterTest`, comprueba que ningún `HttpMessageConverter` del contexto acepta `application/yaml` ni `application/x-yaml` (008-08);
  - una regla nueva en `ArchitectureRulesTest`, como lista blanca: `..liv.jurisdiction.domain..` solo depende de `java..`, de `..liv.jurisdiction.domain..` y de `..liv.shared..` (sin otros módulos, sin Jackson ni ninguna otra biblioteca).
- **Verde:**
  - `SafeYamlLoader` pasa de `src/test` a `jurisdiction/infrastructure/yaml/SafeYamlLoader`, sin cambiar su comportamiento. Recibe bytes y devuelve el árbol o el `motivo` de los casos comunes. Su límite de `TAMANO` es la constante única `IngestionLimits.MAX_FILE_BYTES` (1.048.576), la misma del ADR 0032; no es configurable;
  - `pom.xml`: `snakeyaml` en `compile` sin versión y `json-schema-validator` en `compile` con la exclusión (ADR 0039);
  - `backend/.gitattributes`: `src/test/resources/contracts/jsonschema/config-jurisdiccion/** text eol=lf` (008-32). Hoy el patrón `contracts/jsonschema/**` no cubre esas copias, y en Windows salen con CRLF.
- `Jurisdiction*` y `CommonCases` de `src/test/.../contracts` siguen en las pruebas y usan el cargador de producción: el backend tiene un solo cargador (008-08).
- Tamaño: unas 150 líneas propias (el movimiento no cuenta como código nuevo).

#### B2. CJ01 y el árbol del dominio

- **Dominio:**
  - `ConfigTree`: registros inmutables (objeto con orden de claves, lista, texto, número `BigDecimal` con escala, booleano y nulo);
  - `JsonPointer` (RFC 6901): la forma de las rutas de todo hallazgo, la misma de los casos comunes.
- **Puerto** `SchemaCheck`, y su adaptador `NetworkntSchemaCheck`, que pasa de `JurisdictionConfigSchemas` (pruebas) a `jurisdiction/infrastructure/schema/`:
  - **registro de versiones** `{1.0, 1.1}`, desde `contracts/jsonschema/config-jurisdiccion/`, copiado al *classpath* por el *build*;
  - **enrutado:** valida contra el esquema del `esquema_version` declarado; sin él, o con una versión que no existe, un error de CJ01 en `/esquema_version` (008-11);
  - sin carga remota y sin aceptar un decimal como `integer` (ADR 0032);
  - cada error de esquema da su ruta, el motivo `SCHEMA_VIOLATION` y la palabra clave que falló en `params.keyword`.
- **Conversión** de `JsonNode` a `ConfigTree` en el adaptador, sin pasar por `double` (008-30).
- **Pruebas:**
  - los casos de validación de cada `*.casos.json` con el registro del ADR 0034 (la versión X solo con las ≤ X);
  - el enrutado con el registro completo: un archivo 1.0 no se valida contra 1.1, ni al revés;
  - `0.7 + 0.2 + 0.1` en `BigDecimal` suma exactamente 1 (008-30).
- Tamaño: unas 350 líneas.

#### B3. Núcleo del validador, CJ02 y CJ03

- **`Finding`:** clase (`ERROR`, `WARNING` o `NOT_EVALUABLE`), códigos, ruta, motivo y parámetros de texto.
- **`ValidationResult`:** hallazgos, lista de revisión (`ReviewItem`: ruta, clase `POR_CONFIRMAR` o `TARIFA_MAXIMA` y norma), comparación (B7), `outcome` y `digest`.
- **`JurisdictionConfigValidator`:**
  - corre CJ01 y, si no da errores, todas las demás reglas, sin cortar en el primer hallazgo (008-29);
  - si CJ01 da errores, el resultado lo dice (`skippedAfterSchemaErrors`) y no trae advertencias ni lista de revisión (008-11 y 008-13);
  - lleva la constante `VERSION = "1.0.0"` (H2‑17).
- **Fusión de advertencias** (decisión 13): una por ruta, con todos los códigos que la señalan, ordenados. Un `PENDIENTE` en el `estado` de una regla se informa en la ruta de la regla. Una ruta con un error no da advertencia.
- **Contexto** (`ValidationContext`): la administración (DANE y tipo), la vigencia (año y fechas), la versión publicada (su número y su árbol, para CJ11 y CJ17) y si hay catálogo publicado (hoy, siempre no; decisión 5).
- **Modalidades activas que liquidan:** las que tienen `activa: true` y `liquida: true` en el archivo. No hay una lista en el código (decisión 14).
- **CJ02** (008-12 y 008-13):
  - reglas obligatorias: `jurisdiccion`, las tarifas de las categorías usadas (B4), `redondeo` y `calendario` (H2‑6); las que el archivo marque `obligatoria: true`; y `cartera.intereses_mora` y `cartera.prescripcion` solo si alguna modalidad activa liquida, aunque traigan `obligatoria: true` (decisión 11);
  - una obligatoria con `estado: PENDIENTE` es un error en su ruta;
  - un campo numérico de `cartera` en `PENDIENTE` cuya regla más cercana con `estado` no está en `PENDIENTE` es un error;
  - cada otro `PENDIENTE` es una advertencia, y cada `estado: POR_CONFIRMAR` entra en la lista de revisión.
- **CJ03** (008-14): cada clave `norma` fuera de la sección `normas` debe nombrar el `id` de una norma de `normas`; si no, un error en la ruta de la cita.
- Tamaño: unas 400 líneas. Si pasa, B3a (núcleo) y B3b (CJ02 y CJ03).

#### B4. CJ04, CJ05, CJ06 y CJ08

- **Categoría usada** (decisión 12): alguna regla de `categorias.reglas` la asigna y, para `PUBLICO`, además `sujecion.servicio_publico.gravado: true`.
- **CJ04** (008-15): por cada categoría usada, el primer rango empieza en 0, cada `desde_exclusivo` es el `hasta_inclusivo` anterior, y el último tiene `hasta_inclusivo: null`. Un hueco, un solapamiento, un primero distinto de 0 o un último cerrado son errores en la ruta del rango, y una categoría usada sin rangos también. Una categoría no usada sin rangos es una advertencia de su regla.
- **CJ05** (008-16): se aplica si `tarifas` trae la categoría `ELECTRICO`. Entonces su `tarifa_maxima` es obligatoria (si falta, un error) y cada `tarifa` debe ser ≤ `tarifa_maxima` (comparación de `BigDecimal`). La `tarifa_maxima` entra en la lista de revisión (H2‑16). Sin `ELECTRICO` en `tarifas` (el municipio del 20 % de 008-28), CJ05 no da nada.
- **CJ06** (008-17): por cada modalidad con `liquida: true`, activa o no, la suma de `distribucion[].porcentaje` es exactamente 1 (`compareTo`).
- **CJ08** (008-18): un ajuste `SAN_ANDRES` con `jurisdiccion.codigo_dane` distinto de `"88"` es un error; con el DANE en `PENDIENTE`, *no evaluable* (H2‑5). `"88"` es una constante del texto de la regla (decisión 14).
- Tamaño: unas 350 líneas.

#### B5. CJ12, CJ13 y CJ14

- **CJ12** (008-20): una prueba parametrizada con los casos de la tabla del Doc. 03 §9.5 y sus «otros controles». Las modalidades son A `UEDF_INTERNACION`, B `CONVENIO_DEPTO`, C `PARTICIPACION_20` y D `ADMINISTRACION_DIRECTA`.

  | # | Caso | Resultado |
  |---|---|---|
  | 1 | Solo A, con `zona_frontera: true` | ✅ |
  | 2 | Solo B, con número y fechas del convenio | ✅ |
  | 3 | Solo C | ✅ |
  | 4 | A + B | ✅ |
  | 5 | A + C (Ipiales) | ✅ |
  | 6 | B + C | ❌ `EXCLUSIVE` |
  | 7 | A + B + C | ❌ `EXCLUSIVE` (contiene B + C) |
  | 8 | Ninguna | ❌ `NO_ACTIVE` |
  | 9 | A sin `zona_frontera` o con `false` | ❌ `REQUIRES_BORDER_ZONE` |
  | 10 | B sin número | ❌ `AGREEMENT_INCOMPLETE` |
  | 11 | B sin fecha de inicio | ❌ `AGREEMENT_INCOMPLETE` |
  | 12 | B sin fecha de fin | ❌ `AGREEMENT_INCOMPLETE` |
  | 13 | B con la fecha de inicio posterior a la de fin | ❌ `AGREEMENT_DATES_INVERTED` (H2‑20) |
  | 14 | Solo D en un `DEPARTAMENTO` | ✅ |
  | 15 | Solo D en un `DISTRITO_CAPITAL` | ✅ |
  | 16 | D en un `MUNICIPIO` | ❌ `DIRECT_ADMINISTRATION_TYPE` (decisión 15) |
  | 17 | D en un `DISTRITO` | ❌ `DIRECT_ADMINISTRATION_TYPE` (decisión 15) |
  | 18 | D + B | ❌ `EXCLUSIVE` |
  | 19 | D + C | ❌ `EXCLUSIVE` |
  | 20 | A + D en un `DEPARTAMENTO` con zona de frontera | ✅ (la regla solo excluye B y C) |

  Cada error va en la ruta de la modalidad, o en `/competencia/modalidades` si no hay ninguna activa. La prueba comprueba la clase, el código, la ruta y el motivo.
- **CJ13** (008-22): si alguna modalidad activa liquida, por cada grupo:
  - `fecha_inicio_pago ≤ fecha_limite_descuento ≤ fecha_limite_pago_sin_intereses`; si no, un error;
  - un límite sin intereses posterior a `vigencia.fin` es una advertencia.

  Sin modalidad que liquide, las fechas pueden faltar o estar en `PENDIENTE`.
- **CJ14** (008-23): un plazo `DIAS` sin `dias`, una `FECHA_FIJA` sin `fecha`, o los días del descuento mayores que los del plazo sin intereses son errores.
- Tamaño: unas 400 líneas, la mitad en la tabla de casos.

#### B6. CJ15 y CJ16

Se aplican a toda validación nueva, declare el `esquema_version` que declare (008-24 y 008-25): un archivo 1.0 sin `cartera` y con alguna modalidad activa que liquida da los errores de falta.
- **CJ15:** cada condición del Doc. 03 §5.4 es una prueba:
  - la falta de `intereses_mora`, si alguna modalidad activa liquida;
  - `base` con `TAX` y sin `INTEREST`;
  - `sobre_sanciones` coherente con `PENALTY`;
  - `bloquear_si_falta_tasa: true`;
  - el `multiplo` mayor que 0, salvo con `SIN_REDONDEO`;
  - `inicio` igual a `calendario.inicio_mora`.
- **CJ16:** cada condición es una prueba, también `sistema_determinacion: PENDIENTE` como error. Solo da dos advertencias:
  - `prescripcion.competente` en `PENDIENTE`, que se fusiona con la de CJ02 en la misma ruta;
  - `formato` junto con `formato_preferido`.
- Tamaño: unas 400 líneas.

#### B7. CJ09, CJ10, CJ11, CJ17 y CJ18

- **CJ09 y CJ10** (008-19): sin catálogo publicado, un *no evaluable* cada uno, en `/base_gravable/catalogo_ministerio/multiplicador_tablas` y en `/vigencia/anio`, con motivo `NO_PUBLISHED_CATALOG`. La comprobación real es de la feature del catálogo.
- **CJ11** (008-21):
  - compara el árbol nuevo con el de la versión publicada, hoja por hoja: los objetos por clave y las listas por posición;
  - cada cambio da su sección (la clave de primer nivel), su ruta, `ADDED`, `REMOVED` o `CHANGED`, y el valor anterior y el nuevo como texto JSON canónico, para que los decimales no pierdan su escala (`0.10` frente a `0.1` es un cambio);
  - sin versión publicada, `firstVersion: true`;
  - nunca da hallazgos de error.
- **CJ17** (008-26): un error en cada ruta que no coincide:
  - `/jurisdiccion/codigo_dane` y `/jurisdiccion/tipo`, contra la entidad territorial de la administración;
  - `/vigencia/anio`, `/vigencia/inicio` y `/vigencia/fin`, contra la vigencia;
  - `/vigencia/version_config`, contra la versión publicada más uno.
- **CJ18** (008-27; H2‑19): solo en las rutas que el esquema declara como fecha (las que usan su definición `AAAA-MM-DD` y la de `MM-DD`, como `causacion.general`), no en los textos libres. Una `AAAA-MM-DD` debe ser una fecha real (`ResolverStyle.STRICT`), y una `MM-DD` debe existir en el año de `vigencia.anio`, que CJ01 ya garantiza. `2026-02-30`, `02-30` y `02-29` en 2026 son errores en su ruta. Las rutas salen del esquema de la versión declarada, no de una lista en el código.
- Tamaño: unas 400 líneas.

#### B8. Pruebas *golden*, decimales y el municipio del 20 %

- **`JurisdictionConfigGoldenTest`** (CA‑5.13 · 008-31): valida las copias de `1.1/` con el contexto de 008-31 (DANE 52356, `MUNICIPIO`, vigencia 2026 del 1 de enero al 31 de diciembre, sin versión publicada ni catálogo). Compara el **conjunto completo** de hallazgos (clase, códigos y ruta) y la lista de revisión con «Pruebas *golden*», no solo los conteos.
- **`AnnexCopiesTest`** (008-32): el SHA‑256 de las copias de `1.1/` es el del Doc. 03 §9.2; las de `1.0/` tienen otro.
- **`ParticipationOnlyTest`** (008-28 y H2‑6): un archivo mínimo con solo `PARTICIPACION_20` queda sin errores; el mismo con `calendario` en `PENDIENTE` da el error de CJ02.
- **008-30:** una tarifa `0.015` sale del árbol con escala 3, y `0.7 + 0.2 + 0.1` pasa CJ06. La lectura desde la versión guardada se prueba en C5a.
- Tamaño: unas 300 líneas, casi todas la lista esperada.

### Bloque C — Motor, ciclo de vida, API, auditoría y punto 9 (`backend/`)

#### C1a. Base de datos: tabla, índices, RLS y permisos — *skill* `liv-migracion`

Después de R1 y R2a.
1. **Rojo:**
   - `AdministrationIsolationTest` (CA‑12 · 008-44 y 008-45): con el contexto de A no se ven ni se escriben filas de B; sin contexto no se ve nada; A y B publican cada una su 2026; el mismo SHA‑256 en A y en B no choca (008-09).
   - `LivAppGrantsTest`: las columnas que `liv_app` puede insertar y actualizar, y que no puede borrar.
   - Una prueba nueva, `JurisdictionConfigTableTest`:
     - los `UNIQUE` parciales (una publicada y una en curso; 008-10 y 008-38) y el de `version_number` (008-26);
     - los `CHECK` de estado (también una *Rechazada* con todos los `approved_*`, sin ninguno y con solo una parte, que se rechaza) y de segregación (008-33);
     - las FK de `compared_with_id` y `superseded_by` rechazan una fila de otra vigencia, y las de `rejected_by` y `published_by`, un usuario de otra administración;
     - **al insertar**, `liv_app` no puede fijar `id` ni `uploaded_at` (error 42501);
     - **al actualizar**, `liv_app` no puede cambiar `original_file`, `file_sha256`, `content`, `validation_result`, `version_number` ni `uploaded_*` (error 42501), ni borrar.
   - `PlatformTablesTest.permissionMatrixIsTheApprovedOne`, con los cuatro permisos nuevos, igual al Doc. 01 v0.7 (H2‑3).
2. **Verde:** `V<marca>__jurisdiction_configs.sql` (esquema, tabla, índices, `CHECK`, RLS y permisos de `liv_app`) y `V<marca>__platform_jurisdiction_config_permissions.sql`.

Tamaño: unas 300 líneas.

#### C1b. Base de datos: disparadores

Después de C1a.
- **Rojo:** en `JurisdictionConfigTableTest`:
  - el disparador rechaza *Validada* → *Publicada*, *Reemplazada* → *Publicada* y reescribir `approved_by` de una *Aprobada*, y conserva los `approved_*` en *Aprobada* → *Rechazada* (008-41 y 008-35);
  - *Publicada* → *Reemplazada* apuntando a una *Rechazada* o a una versión menor de la misma vigencia se rechaza con el error del disparador (la prueba comprueba su SQLSTATE y su mensaje: otra vigencia ya la rechaza la FK de C1a, y no probaría el disparador);
  - un reemplazo sin publicar la versión que reemplaza falla al confirmar (disparador diferido).
- **Verde:** `V<marca>__jurisdiction_config_guards.sql`.

Tamaño: unas 300 líneas.

#### C2a. Contrato: consultas y modelo — *skill* `liv-endpoint`, paso 1

Después de R1. Va pronto, para que el *frontend* arranque.
- `backend/contracts/openapi/jurisdiction.yaml` **1.0.0**, nuevo, con su ejecución `contract-jurisdiction` de `openapi-generator` en `pom.xml`:
  - las operaciones de consulta y descarga;
  - el modelo de la versión, del resultado, del hallazgo y de la comparación;
  - el catálogo de motivos (H2‑22) en la descripción de `reason`.
- Primero `OpenApiContractTest` (rojo), luego el contrato. Las extensiones del Problem Details son escalares (ADR 0035).
- La CI lo publica al integrar como `contract-jurisdiction-v1.0.0` (ADR 0027).

Tamaño: unas 350 líneas de YAML.

#### C2b. Contrato: acciones, `tax.yaml` 1.2.0 y `platform.yaml` 1.1.0

Después de C2a.
- `jurisdiction.yaml` **1.1.0** (H2‑23): las operaciones de carga, aprobación, rechazo, anulación y publicación, con sus errores. Es compatible con 1.0.0: solo agrega operaciones. Los motivos (`reason` de rechazo y anulación) son texto de 1 a 500 caracteres sin caracteres de control (patrón), para acotar la auditoría (C7).
- `tax.yaml` **1.2.0**: el motivo `JURISDICTION_CONFIG_NOT_PUBLISHED` en la descripción de `reason` del punto de preparación (008-43). Es compatible: `reason` ya es un texto con patrón.
- `platform.yaml` **1.1.0**: `reportOversizeUpload` (`POST /api/v1/ingestion/oversize-uploads`, con la ruta de carga, de una lista cerrada, el tamaño declarado y un `attemptId`), que llama el BFF (H2‑8). Responde 204, o 403 sin el permiso de la ruta.
- `contracts/scripts/compare.sh` no da cambios incompatibles en ninguno (ADR 0027). La CI publica `contract-jurisdiction-v1.1.0`, `contract-tax-v1.2.0` y `contract-platform-v1.1.0` al integrar.

Tamaño: unas 350 líneas.

#### C3. Piezas comunes del motor de ingesta (`platform`)

Después de R2b (ADR 0038). En `platform/ingestion`, con `@NamedInterface("ingestion")`. Es Java sin dependencia de `jurisdiction`:
- **`IngestionLimits`:** las constantes `MAX_FILE_BYTES` (1.048.576) y `MAX_REQUEST_BYTES` (1.114.112). No son configurables, para no apartarse del cargador ni de los casos comunes (ADR 0032).
- **`IngestionRoute`:** el registro cerrado de las rutas de carga (patrón, permiso y entidad). Lo aporta cada módulo desde su `infrastructure`; la 1.5.9 registra la suya con `jurisdiction.config.upload` y `jurisdiction.jurisdiction_config`. Lo usan el filtro y `reportOversizeUpload` (C9a), así que `platform` no conoce a `jurisdiction`. Una prueba compara por reflexión el permiso de cada ruta con el `@RequiresPermission` de su caso de uso.
- **`FileReceiver`:** lee un `InputStream` de longitud declarada hasta `MAX_REQUEST_BYTES` y calcula el SHA‑256 mientras lee. Devuelve `ReceivedFile` (bytes, tamaño y SHA‑256) u `Oversize` (tamaño y SHA‑256, entre 1 MiB y el tope). Tiene un **plazo total** de lectura: un temporizador cierra el flujo al vencer, porque el `connection-timeout` de Tomcat mide cada lectura y un cliente que envía un byte cada pocos segundos nunca lo dispararía.
- **`RequestBodyLimitFilter`:**
  - va después de la cadena de Spring Security y solo en las rutas de `IngestionRoute`;
  - sin sesión, deja que la cadena dé 401; sin el permiso de la ruta, 403, antes de mirar el tamaño (un `CONSULTA` nunca ve un 413 ni llena la auditoría);
  - sin `Content-Length`, 411. El tope se decide solo por la cabecera, así que el filtro es el único dueño del rechazo por encima del tope, y `FileReceiver`, del que va entre 1 MiB y el tope;
  - con permiso y un `Content-Length` por encima del tope, lanza `PayloadTooLarge` (413 `CG-06` `TAMANO`, con `Connection: close`) sin leer el cuerpo, y registra el intento sin SHA‑256 con `RejectedAttemptLog`.
- **`IngestionStatus` e `IngestionLifecycle`:** los ocho estados y las acciones (`APPROVE`, `REJECT`, `ANNUL`, `PUBLISH` y `SUPERSEDE`). `require(from, action)` da `CG-02` con el estado actual. `requireDifferentActor(uploader, actor)` da `CG-01`, y `requireSameActor` la anulación.
- **`IngestionEngine`**, el coordinador, sin transacción propia:
  - `upload(...)`: recibe y lee el archivo fuera de la transacción (`FileReceiver` e `IngestionReader`). Después abre una transacción (`TransactionTemplate`, `READ_COMMITTED`) que toma el bloqueo de la vigencia, aplica las reglas (`IngestionRules`) y guarda (`VersionStore`);
  - `transition(...)`: las acciones llegan por `{id}`, así que abre la transacción, lee la vigencia de la fila (`fiscal_year` es inmutable), toma el bloqueo de esa vigencia y **vuelve a leer la fila** bajo el bloqueo. Después aplica `IngestionLifecycle` y la parte del módulo, y guarda. Las comprobaciones (marcas, 008-39) se hacen sobre la fila leída bajo el bloqueo;
  - un rechazo auditable (`CG-01`, `CG-06`) cierra la transacción y después se registra con `RejectedAttemptLog` (H2‑10). `PayloadTooLarge` del filtro no pasa por el coordinador: lo registra solo el filtro, así que cada intento da exactamente un evento.
- **`RejectedAttemptLog`**, en `platform/infrastructure`: abre su propia transacción con el `AdministrationContext` de la sesión y publica `IngestionAttemptRejected` (entidad, `id` si lo hay, vigencia, código, motivo, SHA‑256 si lo hay, usuario, administración, instante, `eventId` y `correlationId`). Falla si hay una transacción activa. Si su propia transacción falla, la respuesta no cambia y el fallo queda con nivel `ERROR` y una métrica.
- **Bloqueo de la vigencia:** `pg_advisory_xact_lock(espacio, hashtext(administrationId || ':' || year))`, con una constante de espacio propia de la ingesta, distinta de la de la creación de vigencias de la 007 (`AdministrationFiscalYearRepository`); una prueba comprueba que son distintas. Lo toman la carga y todas las transiciones de la misma vigencia, así que una carga no lee la versión publicada a mitad de una publicación.
- **Interfaces** que implementa el módulo: `IngestionReader` (el cargador), `IngestionRules` (el validador), `VersionStore` (guardar y leer la versión y hacer cada `UPDATE` condicionado) e `IngestionPublisher` (el paso de publicar). Las llama `IngestionEngine`; ninguna queda sin uso.
- **Pruebas:**
  - el SHA‑256 de un archivo conocido;
  - `Oversize` en 1 MiB + 1 byte;
  - el filtro con `Content-Length` grande, sin leer el cuerpo, y con exactamente un evento; sin `Content-Length`, 411; con un usuario sin permiso, 403;
  - un cliente que envía un byte por intervalo corta por el plazo total, sin tomar una conexión a la base;
  - la tabla completa de transiciones permitidas y prohibidas;
  - `RejectedAttemptLog` deja su evento en el registro de eventos de Spring Modulith, y falla si se llama dentro de una transacción. La fila de auditoría se prueba en C7, que trae el *listener*.

Tamaño: unas 400 líneas. Si pasa, C3a (recepción, filtro y registro) y C3b (ciclo de vida y coordinador).

#### C4. Dominio del ciclo de vida (`jurisdiction/domain`)

Después de C3. Java puro: no usa `platform` (regla del B1). El agregado protege sus invariantes él mismo (H2‑24; 008-33: «la regla la aplica el dominio»), y `IngestionLifecycle` lo comprueba también en el coordinador:
- **`JurisdictionConfigVersion`** (el agregado): el estado y los datos de la carga, la aprobación, el rechazo, la anulación, la publicación y el reemplazo.
  - Cada acción recibe el actor y comprueba el estado de origen (`CG-02`) y la segregación: el actor no es el cargador para aprobar, rechazar y publicar, y es el cargador para anular (`CG-01`), con `shared.RuleViolation`.
  - `approve(actor, marks, warningsAccepted, shownDigest, currentValidatorVersion)`:
    - las marcas son exactamente la lista de revisión y las advertencias están aceptadas (`CJC-02` con `missingCount`; H2‑21);
    - `shownDigest` es el `validation_digest` de la versión (`CJC-02`);
    - el resultado es de la versión actual del validador (`CJC-03`).
  - `reject(actor, reason)` y `annul(actor, reason)`: motivo no vacío (`CG-05`).
  - `publicationChecks(...)`: las comprobaciones de 008-39. Dan `CJC-04` con su motivo: `FILE_CHANGED`, `RESULT_CHANGED`, `VALIDATOR_CHANGED`, `PUBLISHED_CHANGED`, `FISCAL_YEAR_NOT_IN_PREPARATION` o `REVALIDATION_DIFFERS`.
- **`UploadPolicy`:** decide la carga, en este orden:
  1. la vigencia existe (si no, `shared.NotFound`, 404 `SYS-404`) y está *En preparación* (`CJC-01`; una *Cerrada* da `V-04`);
  2. el SHA‑256 no coincide con la publicada ni con la en curso (`CG-03`, con la versión que coincide);
  3. no hay otra en curso (`CG-04`).

  Da también el número que le toca: la publicada más uno, o 1.
- **Pruebas:** cada regla con su código y su motivo, también la segregación y el estado de origen sin el coordinador; el orden de `UploadPolicy`; las comprobaciones de 008-39 con datos alterados.
- **Las comprobaciones de 008-39 son una defensa en profundidad.** En la aplicación solo puede ocurrir `VALIDATOR_CHANGED` (un despliegue entre la aprobación y la publicación):
  - la fila es inmutable, así que `FILE_CHANGED`, `RESULT_CHANGED` y `REVALIDATION_DIFFERS` no pueden pasar;
  - el índice de «una en curso» impide que otra versión se publique mientras esta está en curso, así que `PUBLISHED_CHANGED` tampoco;
  - hoy ninguna transición sale de *En preparación*, así que `FISCAL_YEAR_NOT_IN_PREPARATION` tampoco.

  Todas se prueban en el dominio, y en C6b alterando las filas con el dueño de los objetos.

Tamaño: unas 350 líneas.

#### C5a. Carga: persistencia, puertos y caso de uso

Después de B8, C1b, C3 y C4.
- **`UploadJurisdictionConfig`** (`@RequiresPermission("jurisdiction.config.upload")`), sin `@Transactional`: delega en `IngestionEngine.upload`.
  1. Fuera de la transacción: recibe el archivo y lo carga con `SafeYamlLoader`. Un rechazo da `CG-06` con el motivo y el SHA‑256 (`TAMANO` con 413), no guarda nada y se registra con `RejectedAttemptLog` (008-07).
  2. En la transacción, con el bloqueo de la vigencia:
     - lee la vigencia (`FiscalYearDirectory`), el territorio (`AdministrationTerritory`) y la versión publicada;
     - aplica `UploadPolicy`;
     - valida (bloque B) y guarda la fila en *Con errores* o *Validada*;
     - publica `JurisdictionConfigUploaded` (*Cargada*) y `JurisdictionConfigValidated` (*Cargada* → *Con errores* o *Validada*).

  Si algo falla en la transacción, no queda nada (008-05). Si otra carga gana la carrera sin pasar por el bloqueo, el `23505` del índice en curso se traduce a `CG-04` (008-10), nunca a un 500.
- **Persistencia** con JPA en `jurisdiction/infrastructure/persistence`, como la 007:
  - todas las columnas `updatable = false`; `id` y `uploaded_at` con `insertable = false` y `@Generated`;
  - las transiciones, con `UPDATE … WHERE id = ? AND status = ?` explícitos que devuelven las filas afectadas;
  - `content` y `validation_result` como JSONB, leídos con `BigDecimal` (008-30: una `0.015` guardada se lee como `0.015`, con escala 3);
  - las listas (008-01 y 008-02) con consultas de proyección, sin `original_file`, `content` ni `validation_result`.
- **`tax/infrastructure/JurisdictionFiscalYearDirectory`** implementa `FiscalYearDirectory` con el repositorio de vigencias, bajo el RLS de la administración.
- **`core.AdministrationTerritory`**, en el paquete raíz de `core`: el DANE y el tipo de la entidad territorial de la administración.
- **`ModularityTest`:** `jurisdiction` → {`core`, `platform`, `shared`}; `tax` → {`jurisdiction`, `platform`, `shared`}; y la prueba «solo `platform` y `tax` dependen de `shared`» agrega `jurisdiction`.
- **Pruebas del adaptador** con Testcontainers:
  - dos cargas a la vez de la misma vigencia dejan una sola en curso, y la otra da `CG-04`;
  - una carga mientras otra versión se publica espera el bloqueo y compara con la nueva publicada;
  - un fallo de la base después de validar no deja ninguna fila ni evento (008-05);
  - el mismo archivo se carga en A y en B (008-09);
  - un rechazo del cargador publica su `IngestionAttemptRejected` en el registro de eventos (008-07); la fila de `audit.audit_event` se prueba en C7.

Tamaño: unas 400 líneas.

#### C5b. API de consulta, carga y descarga

Después de C2b y C5a. `jurisdiction/api/JurisdictionConfigsController` implementa la interfaz generada, sin lógica (CC‑07). Si pasa de 200 líneas, se parte en dos controladores (consulta y acciones).
- **Casos de uso:**
  - `ListJurisdictionConfigs` y `ListFiscalYearVersions` (008-01 y 008-02);
  - `GetJurisdictionConfigVersion` (008-02), con `availableActions` según el estado, el permiso y si el usuario es el cargador;
  - `DownloadOriginal` (008-04), con una transacción de lectura y escritura (no `readOnly`), porque publica `JurisdictionConfigOriginalDownloaded` por el registro de eventos.
- **Carga:** el controlador recibe el cuerpo como `InputStream` (`application/octet-stream`; H2‑9) y lo entrega a `UploadJurisdictionConfig`.
- **Descarga:** los bytes guardados, con estas cabeceras:
  - `Content-Type: application/octet-stream`;
  - `Content-Disposition: attachment; filename="config_jurisdiccion_<DANE>_<AÑO>_v<N>.yaml"`; el nombre lo arma el servidor;
  - `X-Content-Type-Options: nosniff`;
  - `Cache-Control: no-store`.
- **Pruebas** con `PlatformWebTest`:
  - las respuestas y los errores del contrato, con su estado HTTP (H2‑4): `TAMANO` siempre 413, los demás motivos del cargador 422;
  - 401 y 403 por rol (008-03 y 008-04): `CONSULTA` y `LIQUIDADOR` sin lectura; `ADMIN_SISTEMA` lee pero no descarga;
  - una versión de otra administración da el mismo 404 que una que no existe (008-44);
  - el SHA‑256 de la descarga es el de la carga, y la descarga queda auditada (008-04);
  - `Content-Type: application/yaml` da 415 (008-08).

Tamaño: unas 400 líneas.

#### C6a. Aprobar, rechazar y anular

Después de C5b.
- `ApproveJurisdictionConfig` (`jurisdiction.config.approve`), `RejectJurisdictionConfig` (`.approve`) y `AnnulJurisdictionConfig` (`.upload`), por `IngestionEngine.transition`:
  - leen la vigencia de la fila, toman el bloqueo, vuelven a leer la fila, aplican `IngestionLifecycle` y el agregado, y hacen el `UPDATE` condicionado al estado leído;
  - con 0 filas, vuelven a leer y dan `CG-02` con el estado actual (008-37);
  - publican `JurisdictionConfigApproved`, `JurisdictionConfigRejected` o `JurisdictionConfigAnnulled`.
- Un rechazo por segregación (`CG-01`) se registra con `RejectedAttemptLog` (H2‑10; 008-33 y 008-47).
- **Pruebas:**
  - el cargador con los dos roles no puede aprobar ni rechazar, y su intento queda auditado (008-33);
  - faltan marcas, o el `validationDigest` enviado no es el de la versión (008-34);
  - un resultado de otra versión del validador, con un validador de prueba de otra versión (008-34);
  - una aprobación que pierde la carrera con un rechazo da `CG-02` (008-37);
  - el rechazo de una *Aprobada* conserva quién la aprobó (008-35 y 008-02);
  - la anulación por el cargador, y por otro, que da `CG-01` (008-36).

Tamaño: unas 400 líneas.

#### C6b. Publicar

Después de C6a.
- **`PublishJurisdictionConfig`** (`jurisdiction.config.publish`), por `IngestionEngine.transition` con `IngestionPublisher`, en una sola transacción con el bloqueo de la vigencia:
  1. lee la fila y comprueba que el actor no es el cargador (`CG-01`, registrado con `RejectedAttemptLog`);
  2. comprueba 008-39 (C4): el SHA‑256 de los bytes guardados es `file_sha256`; `approved_validation_digest` es `validation_digest`; la versión del validador es la actual; la publicada sigue siendo `compared_with_id`; la vigencia sigue *En preparación* (sin bloquear su fila); y volver a cargar y validar el original da el mismo `digest`;
  3. pasa la publicada anterior a *Reemplazada*, con `superseded_by`, y la nueva a *Publicada*, en ese orden, para no chocar con el índice único (el disparador diferido lo comprueba al confirmar);
  4. publica `JurisdictionConfigPublished` (administración, vigencia, versión y versión reemplazada; 008-38) y, si hubo reemplazo, `JurisdictionConfigSuperseded`, cada uno con su `eventId`, por el registro persistente de eventos.

  Si algo falla, no cambia nada y la versión sigue *Aprobada* (008-38 y 008-39).
- **`PublishedJurisdictionConfigs`** (fachada del paquete raíz; 008-42): el `id` de la versión publicada por administración y vigencia.
- **Pruebas del adaptador:**
  - dos publicaciones a la vez dejan una sola *Publicada* (008-38);
  - el cargador no puede publicar, y su intento queda auditado (008-33);
  - un resultado de otra versión del validador da `CJC-04` `VALIDATOR_CHANGED`, con un validador de prueba de otra versión;
  - el resto de los motivos, alterando las filas con el dueño de los objetos: otra fila puesta en *Publicada* (`PUBLISHED_CHANGED`), la vigencia puesta en *Lista* (`FISCAL_YEAR_NOT_IN_PREPARATION`), y el original, el `digest` o el resultado cambiados (`FILE_CHANGED`, `RESULT_CHANGED` y `REVALIDATION_DIFFERS`);
  - un fallo después del reemplazo no deja nada ni emite eventos;
  - una prueba de tiempo con el archivo más grande que acepta el cargador.

Tamaño: unas 400 líneas.

#### C7. Auditoría

Después de C6b.
- `audit/infrastructure/JurisdictionConfigEventsListener` (eventos de `jurisdiction`) e `IngestionEventsListener` (`IngestionAttemptRejected` de `platform`), con `audit/application/RecordJurisdictionConfigEvents`, como `FiscalYearEventsListener`: asíncronos e idempotentes por `eventId`.
- **La fila** (008-46):
  - `entity_name`: `jurisdiction.jurisdiction_config`;
  - `entity_id`: el `id` de la versión, nulo en un intento sin versión;
  - `operation`: `JURISDICTION_CONFIG_UPLOADED`, `_VALIDATED`, `_APPROVED`, `_REJECTED`, `_ANNULLED`, `_PUBLISHED`, `_SUPERSEDED`, `_ORIGINAL_DOWNLOADED` o `INGESTION_ATTEMPT_REJECTED`;
  - `previous_values`: `{status}`;
  - `new_values`: una lista blanca acotada. Siempre van la vigencia, la versión y el estado nuevo. Según la operación:
    - la validación, los totales por clase y por código;
    - la aprobación, el número de marcas y el `validation_digest`;
    - el rechazo y la anulación, el motivo;
    - la publicación, la versión reemplazada;
    - un intento rechazado, el código, el motivo y el SHA‑256, si lo hay.
  - `user_id`, `administration_id`, `occurred_at` y `correlation_id`, los del evento.
- **Pruebas:**
  - `JurisdictionConfigAuditTest`, cada operación con sus valores;
  - la publicación con reemplazo da dos filas, una por evento;
  - el peor caso de `new_values` (todos los códigos de CJ01 a CJ18 en los totales y un motivo de 500 caracteres de comillas y barras, que el JSON escapa al doble) cabe en los 4096 caracteres del `CHECK`. Si no cabe, se recorta el motivo en la lista blanca;
  - un intento por segregación, uno del cargador y uno del tope quedan auditados sin que la operación cambie nada (008-07 y 008-47);
  - `ModularityTest`: `audit` → {`core`, `platform`, `tax`, `jurisdiction`}.

Tamaño: unas 350 líneas.

#### C8. Punto 9 de la lista de preparación (`tax`)

Después de C6b y C2b.
- `tax/infrastructure/JurisdictionConfigPreparationCheck` implementa `PreparationItemCheck` para el punto 9 con `PublishedJurisdictionConfigs`: *cumplido* con una versión publicada; *pendiente* con `JURISDICTION_CONFIG_NOT_PUBLISHED` si no.
- Se lee bajo el RLS de la administración, y un fallo de la lectura falla la lista entera, como pide el contrato del puerto.
- **Pruebas:** `GetFiscalYear` con y sin versión publicada; los demás puntos no cambian (007-11); «Habilitar liquidación» sigue inhabilitado (V‑03 · CA‑14); un fallo de la lectura da el error de la lista entera (008-43).

Tamaño: unas 200 líneas.

#### C9a. Aviso del tope (`platform`)

Después de C2b y C3.
- `platform/api` implementa `reportOversizeUpload` de `platform.yaml` 1.1.0 con un caso de uso, `ReportOversizeUpload`, que pasa por la cadena normal de `/api/**` (`CurrentUserFilter`, `AdministrationContext`):
  - la ruta pedida debe estar en `IngestionRoute` (si no, 400), y el usuario debe tener el permiso de esa ruta (si no, 403). El permiso lo comprueba el caso de uso con el autorizador de `platform`, no el controlador;
  - registra el intento sin SHA‑256 con `RejectedAttemptLog`, con el `attemptId` del BFF como `eventId`: un reintento del BFF no duplica la fila.
- **Pruebas:** el aviso publica su evento; sin el permiso de la ruta, 403 y nada; una ruta fuera del registro, 400; un usuario desactivado o de otra administración, el 401 o el 403 de la cadena normal; el mismo `attemptId` dos veces, un solo evento.

Tamaño: unas 200 líneas.

#### C9b. BFF

Después de C9a.
- **`bff.yaml`:** las respuestas propias del BFF en la ruta de carga: 411 y 413 como Problem Details `CG-06` `TAMANO`, idéntico al de la API. Sube su versión menor, y el *frontend* la toma en F0.
- **El filtro:** una `HandlerFilterFunction` del *gateway* para las rutas de carga de `/api/**`, siempre después de `TokenRelayFilter`. Así ya corrieron la sesión, el CSRF, `AbsoluteLimitFilter` y `RateLimitFilter`, y el *token* está renovado (H2‑8):
  - exige `Content-Length`; si falta, 411;
  - por encima del tope, no reenvía el cuerpo y llama a `reportOversizeUpload` con el *token* de la sesión y un `attemptId` nuevo. Responde 403 si la API da 403, y 413 `TAMANO` en los demás casos;
  - si el aviso falla, responde 413 y deja el fallo en el registro (*log*) con nivel `ERROR`.
- **Pruebas** en `BffContractTest`, con una API falsa:
  - el cuerpo `application/octet-stream` llega a la API byte por byte: el SHA‑256 de lo que recibe es el del archivo enviado, también con CRLF y con bytes no ASCII (008-05 y 008-09);
  - cuerpos de 1 MiB + 1 byte (lo reenvía, y la API da 413 con SHA‑256), del tope + 1 byte, de 3 MiB y de 5 MiB (el BFF no reenvía y avisa a la API): siempre 413 `TAMANO`, nunca 502;
  - un `CONSULTA` con 5 MiB recibe 403, y una petición sin sesión, 401, sin aviso;
  - sin `Content-Length`, 411;
  - las cabeceras de la descarga (`Content-Disposition`, `X-Content-Type-Options`) pasan sin cambios, y `Cache-Control: no-store` sigue.
- **Riesgo que se comprueba aquí:** que `HandlerFunctions.http()` de Spring Cloud Gateway Server MVC reenvía el cuerpo en *streaming*, sin convertir la codificación.

Tamaño: unas 300 líneas.

#### C10. Abrir la opción del menú

Se integra al final, después de C7, C8 y C9b y de la validación en local del *frontend*: así la opción no se muestra habilitada antes de que existan la API y su auditoría (001-15).
- `menu-catalog.yaml`: la 1.5.9 pasa a `AVAILABLE`, con su ajuste en `MenuCatalogTest` (008-01).
- `backend/dev/limpiar-vigencias.sh` vacía `jurisdiction.jurisdiction_config` antes que `tax.administration_fiscal_year`, con las mismas guardas, y su prueba de escenarios (`dev/test/limpiar-vigencias-escenarios.sh`) lo cubre (H2‑15).
- Agrega a la *skill* `liv-endpoint` el ejemplo de una carga de archivo (cuerpo crudo, tope y coordinador del motor).

Tamaño: unas 120 líneas.

### Bloque D — `liv-components` 0.6.0 (`ui/`) — *skill* `liv-componente`

Después del paso 0, en paralelo con el backend. Cada componente lleva su prueba por variante y estado, axe en cada combinación, su historia de Storybook enlazada a su nodo y su exportación en `public-api.ts`. Ninguno sabe de rutas ni de HTTP. Estilos solo por *token*.

| PR | Contenido | Nodo | Tipo |
|---|---|---|---|
| U1 | Iconos `check`, `x`, `upload` y `download` en `Icon`; la prueba que cuenta los iconos | 88:506 | Crítico (modifica un componente; H2‑18) |
| U2 | `Checkbox` y `TextArea` | 88:524 y 88:578 | Normal |
| U3 | `FileField` | 89:611 | Normal |
| U4 | `Tab` y sube a 0.6.0 | 90:565 | Normal |

- **`Checkbox` (U2):** un `<input type="checkbox">` nativo con su `<label>`, sus estados (marcado, sin marcar, inhabilitado, con error) e integración con los formularios de Angular (`ControlValueAccessor`). El diálogo de aprobación usa uno por punto de la lista, sin «Marcar todas» (decisión de diseño).
- **`TextArea` (U2):** un `<textarea>` con etiqueta, ayuda, error enlazado con `aria-describedby`, contador y `maxlength` (500 para los motivos).
- **`FileField` (U3):**
  - un `<input type="file">` nativo, con un botón visible que lo abre y el nombre y el tamaño del archivo elegido;
  - `accept` como entrada, sin validar el contenido: lo valida el servidor;
  - emite el `File`;
  - estados sin archivo, con archivo, inhabilitado y con error;
  - Enter y Espacio abren el selector.
- **`Tab` (U4):** el patrón de pestañas de WAI‑ARIA, con `role="tablist"`, `tab` y `tabpanel`, `aria-selected`, `aria-controls` y un solo Tab que entra a la pestaña activa. Las flechas cambian de pestaña, con activación automática. jsdom no implementa el foco real del teclado, así que las flechas se prueban también en `e2e/` (E1).
- **Versión (H2‑13):** U1 a U3 van a «Sin publicar» del `CHANGELOG.md`; U4 sube a 0.6.0 y mueve las entradas. La persona publica en npm después de integrar el U4. 0.6.0 es correcto: los cambios son compatibles, y en 0.x una versión compatible sube el número del medio.
- **Capturas:** `ui/` no tiene regresión visual (Q14). En el U1, crítico, la comparación con Figma es una revisión manual en Storybook, con capturas en la descripción del PR.

### Bloque E — Pantallas (`frontend/`) — *skill* `liv-pantalla`; y pruebas de navegador (`e2e/`)

Después de C2b (`jurisdiction.yaml` 1.1.0, `tax.yaml` 1.2.0 y `platform.yaml` 1.1.0 publicados), de C9b (`bff.yaml`) para F3, y de U4 (0.6.0 publicado en npm). El *frontend* no calcula reglas: los estados, las acciones (`availableActions`, `canUpload`), los hallazgos y los motivos vienen del contrato. Los textos se arman con el código y el motivo del «Catálogo de motivos», desde las hojas de mensajes del diseño (99:8176 y 99:8264); un motivo desconocido muestra el texto genérico de su código. Todas las rutas llevan `capabilityGuard` con `capability: '1.5.9'` y se declaran antes de la genérica. Mientras la opción esté en *Próximamente* (C10), llevan a «Próximamente».

| PR | Contenido | *Frames* |
|---|---|---|
| F0 | `api-spec/versions.json` con `jurisdiction` 1.1.0 y `tax` 1.2.0, `npm run api:update`, `liv-components` 0.6.0 exacto en `package.json`, y el texto de `JURISDICTION_CONFIG_NOT_PUBLISHED` en la lista de preparación de la 1.4 (008-43) | — |
| F1 | `JurisdictionConfigsService` en `src/app/features/jurisdiction-configs/` y la lista en `${OPTION_ROUTE}/1.5.9`, con sus seis estados; «Cargar configuración» solo con `canUpload` | 92:4180, 92:4464, 92:4722, 92:4984, 92:5205, 92:5434 |
| F2 | «Versiones de la vigencia» en `${OPTION_ROUTE}/1.5.9/:year` (H2‑12) | 95:5176, 95:5516 |
| F3 | El diálogo «Cargar configuración»: la vigencia (`Select`, de `uploadableYears`), el archivo (`FileField`), «validando» mientras se envía (Escape no cierra) y los rechazos por código y motivo, también el 411 y el 413 del BFF (con `bff.yaml` de C9b) | 95:5973, 95:6164, 95:6349, 95:6534, 99:8176 |
| F4a | El detalle en `${OPTION_ROUTE}/1.5.9/versiones/:id`: datos, `Badge` del estado y pestañas (`Tab`) de errores, advertencias, no evaluables y por confirmar; cargando, no encontrada y error | 97:6519, 97:6871, 97:7206, 98:7528, 98:7850, 99:7686, 99:7943 |
| F4b | La pestaña de cambios (CJ11, agrupados por sección, con «Primera versión: no hay nada que comparar»), la descarga del original y la hoja de mensajes del detalle | 98:7153, 99:8264 |
| F5 | Los diálogos de aprobar (un `Checkbox` por punto y la aceptación de las advertencias, sin «Marcar todas»), rechazar y anular (motivo con `TextArea`, tono *Warning*) y publicar | 99:8566, 99:8817, 99:8998, 99:9180, 99:9347 |
| E1 | Las pruebas de navegador de `e2e/` | Todos |

**En todos los PR del *frontend***
- Las acciones se muestran según lo que dice el *backend*, no según el rol. Un valor de `availableActions` desconocido se ignora.
- La descarga pide el original como `Blob` y lo guarda con el nombre que da `Content-Disposition`, sin mostrarlo en la página (008-04).
- **La carga:**
  - envía el `File` tal cual, con `Content-Type: application/octet-stream`;
  - no valida el tamaño antes de enviar, para que todo intento quede auditado (H2‑8);
  - si la petición falla sin respuesta (conexión cortada) y el archivo pasa de 1 MiB, muestra el mensaje de `TAMANO`;
  - el `FileField` lleva `accept=".yaml,.yml"`.
- La aprobación envía las marcas, la aceptación y el `validationDigest` que muestra (H2‑21). Si recibe `CJC-02`, dice cuáles faltan comparando sus marcas con `reviewItems`.
- Las fechas van con el formateador de la librería, y los estados con los tonos de `Badge` del diseño.
- **Pruebas primero**, en el `spec.ts` de cada pantalla:
  - cada estado de cada *frame*;
  - la acción ausente sin su valor en `availableActions` (008-33 a 008-36, del lado de la pantalla);
  - cada rechazo con su código y su motivo;
  - el aprobar inhabilitado hasta marcar todos los puntos;
  - el motivo obligatorio;
  - el 403 por URL (008-03);
  - axe con cada diálogo abierto (008-53).

  El contraste no se mide en jsdom: lo cubre `e2e/`.

**E1 · Pruebas de navegador (`e2e/`)** — *skill* `liv-prueba-e2e`. Con el sistema levantado y `backend/dev/limpiar-vigencias.sh` ejecutado (ADR 0030 y H2‑15). Se escriben en rojo mientras se implementan F1 a F5.
- **Datos:**
  - las copias de los YAML en `e2e/datos/`, con `e2e/.gitattributes` en LF (008-53);
  - la prueba crea y prepara una vigencia por la API (`support/vigencias.ts`) y escribe en la copia su año y sus fechas antes de cargarla. No depende de ningún SHA‑256 escrito a mano.
- **Recorridos**, con los usuarios del *realm* de desarrollo:
  - `dev.cargador.tablas` carga Ipiales y ve *Validada* con sus advertencias, y carga un archivo con errores y ve *Con errores*;
  - `dev.aprobador.tablas` aprueba marcando cada punto, y `dev.juridico` la publica;
  - la versión siguiente muestra los cambios (CJ11) y reemplaza a la anterior;
  - el cargador anula la suya, y el aprobador rechaza otra con motivo;
  - el punto 9 de la 1.4 queda cumplido (008-43);
  - `dev.auditor` descarga el original y no ve las acciones;
  - `dev.consulta` recibe 403;
  - `e2e.admin.b` (`ADMIN_SISTEMA` de B) no ve las de Ipiales (008-44).
- **Navegación con teclado de las pestañas** (componente `Tab`) en el navegador real, que jsdom no cubre (U4).
- **Capturas de referencia** de la lista, el historial, el detalle y cada diálogo, con máscara sobre las fechas, los SHA‑256 y los años. Las aprueba la persona. Axe en cada una.
- La captura del menú de la 001 cambia porque la 1.5.9 deja de estar en *Próximamente*; la persona la vuelve a aprobar.

## Datos, API y seguridad

- **Migraciones:** las de C1a y C1b. No modifican ninguna migración aplicada.
- **Contrato OpenAPI** `jurisdiction.yaml` 1.0.0 (C2a) y 1.1.0 (C2b), nuevo; sin cambios incompatibles en `tax.yaml`, que pasa a 1.2.0, ni en `platform.yaml`, que pasa a 1.1.0. `year` es un entero entre 1999 y 2100 (el rango de la tabla nacional); `id`, un UUID. Todos los errores son Problem Details con `code` y extensiones escalares (ADR 0035).

| Operación | Ruta | Permiso | Respuesta | Errores |
|---|---|---|---|---|
| `listJurisdictionConfigs` | `GET /api/v1/jurisdiction-configs` | `read` | `{ items: [{ fiscalYear, fiscalYearStatus, published: { id, versionNumber, publishedAt } \| null, inProgress: { id, versionNumber, status, uploadedAt } \| null }], canUpload, uploadableYears }`, de la más reciente a la más antigua | 401, 403 |
| `listFiscalYearVersions` | `GET /api/v1/jurisdiction-configs/{year}/versions` | `read` | `{ fiscalYear, items: [{ id, versionNumber, status, fileSha256, uploadedBy, uploadedAt, approvedBy, approvedAt, rejectedBy, rejectedAt, annulledAt, closureReason, publishedAt, supersededAt }] }`, de la más reciente a la más antigua | 400, 401, 403, 404 `SYS-404` |
| `uploadJurisdictionConfig` | `POST /api/v1/jurisdiction-configs/{year}/versions`, cuerpo `application/octet-stream` con `Content-Length` | `upload` | 201 y el detalle | 400, 401, 403, 404 `SYS-404` (la vigencia no existe), 409 `CG-03` (`matchesVersionId`, `matchesVersionNumber`), 409 `CG-04`, 409 `CJC-01` (`fiscalYearStatus`), 409 `V-04`, 411, 413 `CG-06` `TAMANO`, 415, 422 `CG-06` (`reason` del cargador, `fileSha256`) |
| `getJurisdictionConfigVersion` | `GET /api/v1/jurisdiction-configs/versions/{id}` | `read` | La versión; `validation: { schemaVersion, validatorVersion, outcome, digest, totals, findings: [{ class, codes, path, reason, params }], reviewItems: [{ path, kind, norma }], skippedAfterSchemaErrors }`; `comparison: { firstVersion, comparedWith, changes: [{ section, path, change, before, after }] }`; `approval`; `availableActions` | 400, 401, 403, 404 |
| `downloadOriginal` | `GET /api/v1/jurisdiction-configs/versions/{id}/original` | `download` | 200 `application/octet-stream`, `attachment` (008-04) | 401, 403, 404 |
| `approveJurisdictionConfig` | `POST …/versions/{id}/approve` con `{ reviewedPaths, warningsAccepted, validationDigest }` | `approve` | La versión | 401, 403, 404, 422 `CG-01`, 409 `CG-02` (`status`), 422 `CJC-02` (`missingCount`, `warningsAccepted`, `digestMatches`), 409 `CJC-03` (`resultValidatorVersion`, `currentValidatorVersion`) |
| `rejectJurisdictionConfig` | `POST …/versions/{id}/reject` con `{ reason }` | `approve` | La versión | 401, 403, 404, 422 `CG-01`, 409 `CG-02`, 422 `CG-05` |
| `annulJurisdictionConfig` | `POST …/versions/{id}/annul` con `{ reason }` | `upload` | La versión | 401, 403, 404, 422 `CG-01` (`ONLY_UPLOADER`), 409 `CG-02`, 422 `CG-05` |
| `publishJurisdictionConfig` | `POST …/versions/{id}/publish` | `publish` | La versión publicada y la reemplazada | 401, 403, 404, 422 `CG-01`, 409 `CG-02`, 409 `CJC-04` (`reason`) |

  - `uploadedBy`, `approvedBy`, `rejectedBy` y `publishedBy` son `{ id, displayName }` de la administración.
  - `before` y `after` son texto JSON canónico, para conservar la escala de los decimales (008-30).
  - `path` es un JSON Pointer.
  - `availableActions`: `APPROVE`, `REJECT`, `ANNUL`, `PUBLISH` y `DOWNLOAD`.
  - `uploadableYears` son las vigencias *En preparación* sin versión en curso.
  - Un 403 dentro de un diálogo lleva a «Sin permiso» (001-16), como en la 007.
- **Catálogo de motivos** (H2‑22), el `reason` de cada hallazgo:

| Regla | Clase | Motivos |
|---|---|---|
| CJ01 | Error | `SCHEMA_VERSION_UNKNOWN`, `SCHEMA_VIOLATION` (con la palabra clave del esquema que falló en `params.keyword`; una prueba de C2a recorre los esquemas 1.0 y 1.1 y falla si la pantalla no tiene texto para alguna de sus palabras clave) |
| CJ02 | Error | `REQUIRED_RULE_PENDING`, `NUMERIC_PENDING_IN_UNPENDING_RULE` |
| CJ02 | Advertencia | `PENDING_VALUE`, `PENDING_RULE` |
| CJ03 | Error | `UNKNOWN_NORMA` |
| CJ04 | Error | `RANGE_GAP`, `RANGE_OVERLAP`, `FIRST_RANGE_NOT_ZERO`, `LAST_RANGE_NOT_OPEN`, `USED_CATEGORY_WITHOUT_RANGES` |
| CJ04 | Advertencia | `UNUSED_CATEGORY_WITHOUT_RANGES` |
| CJ05 | Error | `MAX_RATE_MISSING`, `RATE_ABOVE_MAX` |
| CJ06 | Error | `DISTRIBUTION_NOT_ONE` |
| CJ08 | Error / no evaluable | `SAN_ANDRES_OUTSIDE_88` / `DANE_PENDING` |
| CJ09, CJ10 | No evaluable | `NO_PUBLISHED_CATALOG` |
| CJ12 | Error | `NO_ACTIVE`, `EXCLUSIVE`, `REQUIRES_BORDER_ZONE`, `AGREEMENT_INCOMPLETE`, `AGREEMENT_DATES_INVERTED`, `DIRECT_ADMINISTRATION_TYPE` |
| CJ13 | Error / advertencia | `DATE_MISSING`, `DATE_ORDER` / `AFTER_FISCAL_YEAR_END` |
| CJ14 | Error | `DAYS_MISSING`, `FIXED_DATE_MISSING`, `DISCOUNT_DAYS_EXCEED_TERM` |
| CJ15 | Error | `SECTION_MISSING`, `BASE_WITHOUT_TAX`, `BASE_WITH_INTEREST`, `PENALTY_BASE_INCONSISTENT`, `MUST_BLOCK_WITHOUT_RATE`, `MULTIPLE_NOT_POSITIVE`, `INTEREST_START_MISMATCH` |
| CJ16 | Error | `SECTION_MISSING`, `ACTIVE_PENALTY_WITHOUT_NORMA`, `ACTIVE_PENALTY_WITHOUT_RATE`, `DETERMINATION_PENDING`, `DECLARATION_RULE_ACTIVE`, `UVT_MISSING`, `FINAL_ACT_REQUIRED`, `DECLARATION_MODE`, `YEARS_NOT_POSITIVE`, `TRAFFIC_LIGHT_ORDER`, `AGING_NOT_INCREASING`, `BANK_FORMAT_MISSING`, `DUPLICATE_BANK_CODE`, `UNKNOWN_BANK` |
| CJ16 | Advertencia | `COMPETENT_PENDING` (fusionada con `PENDING_VALUE`), `BOTH_FORMATS` |
| CJ17 | Error | `DANE_MISMATCH`, `TYPE_MISMATCH`, `YEAR_MISMATCH`, `FISCAL_YEAR_START_MISMATCH`, `FISCAL_YEAR_END_MISMATCH`, `VERSION_MISMATCH` |
| CJ18 | Error | `INVALID_DATE` |

  Una advertencia fusionada (decisión 13) lleva un motivo por código. Los `params` son texto: el valor esperado y el encontrado, la norma, el rango o la modalidad. Los `kind` de la lista de revisión son `POR_CONFIRMAR` y `TARIFA_MAXIMA`.
- **Permisos y segregación** (decisión 6; H2‑3):

| Permiso | Roles | Casos de uso |
|---|---|---|
| `jurisdiction.config.read` (ya existe) | `ADMIN_SISTEMA`, `CARGADOR_TABLAS`, `APROBADOR_TABLAS`, `JURIDICO`, `AUDITOR` | `ListJurisdictionConfigs`, `ListFiscalYearVersions`, `GetJurisdictionConfigVersion` |
| `jurisdiction.config.upload` | `CARGADOR_TABLAS` | `UploadJurisdictionConfig`, `AnnulJurisdictionConfig` (solo la propia) |
| `jurisdiction.config.approve` | `JURIDICO`, `APROBADOR_TABLAS` | `ApproveJurisdictionConfig`, `RejectJurisdictionConfig` (nunca el cargador) |
| `jurisdiction.config.publish` | `JURIDICO`, `APROBADOR_TABLAS` | `PublishJurisdictionConfig` (nunca el cargador; puede ser quien aprobó) |
| `jurisdiction.config.download` | `CARGADOR_TABLAS`, `APROBADOR_TABLAS`, `JURIDICO`, `AUDITOR` | `DownloadOriginal` |

  La segregación está en `platform` (`CG-01`) y en la base de datos (`CHECK`). La interfaz solo muestra lo que permite `availableActions`.
- **Amenazas** (`docs/arquitectura/02`):
  - *YAML hostil* (alias, etiquetas, bombas de anidación, claves repetidas, caracteres de control): el cargador seguro lee eventos y rechaza antes de construir nada (008-07 y 008-08). Hay un solo cargador, con los casos comunes.
  - *Archivo grande o cliente lento*: el BFF exige `Content-Length` y corta por encima del tope sin reenviar; el filtro de la API y `FileReceiver` nunca leen más que el tope, tienen un tiempo máximo de lectura y no toman una conexión a la base mientras leen (H2‑8).
  - *Archivo malicioso sin antivirus*: se acepta por la excepción temporal del ADR 0036. Solo se guarda texto UTF‑8 que el cargador aceptó, nunca se interpreta fuera de él y se descarga solo como adjunto con `nosniff`.
  - *Un usuario que aprueba lo suyo* (`docs/arquitectura/02` §4.3): `platform`, el `CHECK` y la auditoría del intento. Queda el riesgo aceptado de un administrador con dos cuentas (decisión 2), que compensa la feature 1.2.
  - *Saltar de administración* (§5): la administración sale de la sesión; RLS y FK compuestas; el mismo 404 para lo ajeno y lo inexistente; un SHA‑256 de otra administración no choca (008-09).
  - *Cambiar o despublicar lo publicado*: permisos por columna, los dos disparadores y sin `DELETE` (008-41).
  - *Publicar con un resultado viejo*: el `digest` que envió el aprobador, la versión del validador, el bloqueo de la vigencia y la revalidación dentro de la transacción (008-39).
  - *Llenar la auditoría*: solo audita el tope un usuario con el permiso de carga; los motivos están acotados y sin caracteres de control.
  - *Auditoría con datos sensibles*: solo totales, referencias y el motivo. El original y los hallazgos no van a `audit_event`.
  - El YAML no trae números de cuenta: `cuenta_recaudo_ref` es una referencia al gestor de secretos (Doc. 03 §5.2).

### Pruebas *golden*

**Contexto de 008-31:**
- administración de prueba con DANE 52356 y tipo `MUNICIPIO`;
- vigencia 2026, del 1 de enero al 31 de diciembre, *En preparación*;
- sin versión publicada ni catálogo publicado;
- las copias de `1.1/`, con los SHA‑256 del Doc. 03 §9.2.

Las rutas son JSON Pointer; «PND» es un valor `PENDIENTE` y «regla» un `estado: PENDIENTE`. La lista se obtuvo recorriendo los dos anexos con un programa, se contrastó con el §9.2 y la recalcularon los dos revisores. La prueba compara el conjunto completo.

**Ipiales (`config_jurisdiccion_52356_2026.yaml`)** — *Validada*.
- **Errores:** ninguno.
- **Advertencias (17):**

| # | Ruta | Códigos | Qué es |
|---|---|---|---|
| 1 | `/jurisdiccion/administracion/nit` | CJ02 | PND |
| 2 | `/normas/7/numero` | CJ02 | PND (`DEFINICION_ADMINISTRACION_2026`) |
| 3 | `/normas/7` | CJ02 | regla |
| 4 | `/sujecion/motos_electricas_criterio_sujecion` | CJ02 | PND |
| 5 | `/tarifas/3` | CJ02, CJ04 | regla (`PUBLICO`, no se usa y no tiene rangos) |
| 6 | `/beneficios/0` | CJ02 | regla (beneficio de ejemplo inactivo) |
| 7 a 10 | `/recaudo/bancos/0/cuenta_recaudo_ref` a `/recaudo/bancos/3/cuenta_recaudo_ref` | CJ02 | PND |
| 11 | `/recaudo/archivo_banco` | CJ02 | regla |
| 12 | `/recaudo/reporte_participacion_20` | CJ02 | regla |
| 13 | `/cartera/sanciones/estatuto_territorial` | CJ02 | PND |
| 14 | `/cartera/prescripcion/competente` | CJ02, CJ16 | PND |
| 15 | `/cartera/cartera_historica/vigencias_desde` | CJ02 | PND |
| 16 | `/cartera/cartera_historica/fecha_corte_migracion` | CJ02 | PND |
| 17 | `/cartera/cartera_historica` | CJ02 | regla |

- **Lista de revisión (20):**
  - las 19 reglas `POR_CONFIRMAR`: `/normas/1`, `/normas/5`, `/normas/6`, `/normas/9`, `/normas/10`, `/sujecion/servicio_publico`, `/sujecion`, `/categorias`, `/tarifas/1`, `/conservacion_tarifa`, `/causacion/proporcionalidad_nuevos`, `/recaudo`, `/cartera/intereses_mora`, `/cartera/sanciones/uvt`, `/cartera/sanciones/reglas/0`, `/cartera/sanciones/reglas/1`, `/cartera/sanciones`, `/cartera/prescripcion` y `/cartera/antiguedad`;
  - la `TARIFA_MAXIMA`: `/tarifas/2/tarifa_maxima` (H2‑16).
- **No evaluables (2):** CJ09 en `/base_gravable/catalogo_ministerio/multiplicador_tablas` y CJ10 en `/vigencia/anio` (`NO_PUBLISHED_CATALOG`).
- **CJ11:** primera versión. CJ17 y CJ18 se cumplen.

**Plantilla (`config_jurisdiccion_plantilla_2026.yaml`)** — *Con errores*.
- **Errores (7):**

| # | Ruta | Código | Motivo |
|---|---|---|---|
| 1 | `/jurisdiccion` | CJ02 | Regla obligatoria en `PENDIENTE` (H2‑6) |
| 2 | `/calendario` | CJ02 | Ídem (`obligatoria: true`) |
| 3 | `/redondeo` | CJ02 | Ídem (`obligatoria: true`) |
| 4 | `/competencia/modalidades` | CJ12 | Ninguna modalidad activa |
| 5 | `/cartera/sanciones/sistema_determinacion` | CJ16 | `PENDIENTE` es error |
| 6 | `/jurisdiccion/codigo_dane` | CJ17 | `PENDIENTE` ≠ 52356 |
| 7 | `/jurisdiccion/tipo` | CJ17 | `DEPARTAMENTO` ≠ `MUNICIPIO` |

- **Advertencias (21), todas con CJ02 salvo donde se indica otro código:**
  - 13 valores PND: `/jurisdiccion/nombre`, `/jurisdiccion/administracion/nit`, `/normas/5/numero`, `/sujecion/motos_electricas_criterio_sujecion`, `/calendario/grupos/0/fecha_limite_descuento`, `/calendario/grupos/0/fecha_limite_pago_sin_intereses`, `/calendario/descuento_pronto_pago/tarifa`, `/recaudo/estado_bancos`, `/recaudo/reporte_participacion_20/remitente`, `/cartera/sanciones/estatuto_territorial`, `/cartera/prescripcion/competente` (CJ02, CJ16), `/cartera/cartera_historica/vigencias_desde` y `/cartera/cartera_historica/fecha_corte_migracion`;
  - 8 reglas: `/competencia` (no es obligatoria), `/normas/5`, `/tarifas/3` (CJ02, CJ04), `/beneficios/0`, `/recaudo/archivo_banco`, `/recaudo/reporte_participacion_20`, `/cartera/intereses_mora` (no es obligatoria: ninguna modalidad activa liquida; decisión 11) y `/cartera/cartera_historica`.
- **Lista de revisión (17):**
  - las 16 reglas `POR_CONFIRMAR`: `/normas/1`, `/normas/7`, `/normas/8`, `/sujecion/servicio_publico`, `/sujecion`, `/categorias`, `/tarifas/1`, `/conservacion_tarifa`, `/causacion/proporcionalidad_nuevos`, `/recaudo`, `/cartera/sanciones/uvt`, `/cartera/sanciones/reglas/0`, `/cartera/sanciones/reglas/1`, `/cartera/sanciones`, `/cartera/prescripcion` y `/cartera/antiguedad`;
  - `/tarifas/2/tarifa_maxima`.
- **No evaluables (3):** CJ08 en `/base_gravable/ajustes/3` (`DANE_PENDING`, H2‑5), CJ09 y CJ10.
- **CJ11:** primera versión.

Son los errores de CJ02, CJ12, CJ16 y CJ17 que espera 008-31.

## Diseño de interfaz

Archivo LIV‑Diseño, sección «1.5.9 · Configuración de jurisdicción» ([92:4179](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-4179)), aprobada por la persona el 2026-11-16. Base de los enlaces: `https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=<id con guion>`.

| Pantalla | Diseño en Figma | Componentes de `liv-components` |
|---|---|---|
| 1.5.9 · Configuraciones (lista) | Cargador [92:4180](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-4180), consulta [92:4464](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-4464), vacía [92:4722](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-4722), cargando [92:4984](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-4984), error [92:5205](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-5205), 403 [92:5434](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=92-5434) | `PageHeader`, `Table`, `TableRow`, `TableCell`, `Badge`, `Button`, `ResultState`, `Skeleton`, `Icon` |
| 1.5.9 · Versiones de la vigencia (**añadida por el diseño aprobado**, H2‑12; 008-02) | [95:5176](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=95-5176), vacía [95:5516](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=95-5516) | `PageHeader`, `Table`, `Badge`, `Button`, `ResultState` |
| 1.5.9 · Cargar configuración (diálogo) | Sin archivo [95:5973](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=95-5973), archivo elegido [95:6164](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=95-6164), validando [95:6349](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=95-6349), rechazado [95:6534](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=95-6534), mensajes [99:8176](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-8176) | `Dialog`, `Select`, `FileField`, `Button`, `Alert`, `Spinner` |
| 1.5.9 · Detalle de la versión | Validada vista por el aprobador [97:6519](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=97-6519), con errores [97:6871](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=97-6871), vista por el cargador [97:7206](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=97-7206), aprobada con cambios [98:7153](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=98-7153), publicada [98:7528](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=98-7528), cargando [98:7850](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=98-7850), no encontrada [99:7686](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-7686), error [99:7943](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-7943), mensajes [99:8264](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-8264) | `PageHeader`, `Panel`, `DescriptionItem`, `Badge`, `Tab`, `Table`, `Alert`, `Button`, `ResultState`, `Skeleton`, `Icon` |
| 1.5.9 · Aprobar, rechazar, anular y publicar (diálogos) | Aprobar con marcas faltantes [99:8566](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-8566) y todo marcado [99:8817](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-8817), rechazar [99:8998](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-8998), anular [99:9180](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-9180), publicar [99:9347](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=99-9347) | `Dialog` (tono *Warning* en rechazar y anular), `Checkbox`, `TextArea`, `Button`, `Alert` |

**Componentes nuevos** (página `02 Componentes`; bloque D): `Icon` con `check`, `x`, `upload` y `download` ([88:506](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=88-506)), `Checkbox` ([88:524](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=88-524)), `TextArea` ([88:578](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=88-578)), `FileField` ([89:611](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=89-611)) y `Tab` ([90:565](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=90-565)).

**Decisiones de diseño de la persona** que el código respeta:
- el historial va en pantalla propia;
- la aprobación va en un diálogo, con una marca por punto y sin «Marcar todas»;
- el detalle usa pestañas;
- rechazar y anular van en tono *Warning*;
- no hay marco aparte para el rol sin descarga: la pantalla oculta «Descargar» sin `DOWNLOAD`;
- los estados se muestran con los tonos de `Badge` del diseño.

Si un PR del *frontend* necesita algo que el diseño no tiene (un estado, un texto), se detiene y lo pregunta; no lo inventa.

## Dependencias, paralelismo y recursos

- **Depende de:**
  - la 007, integrada: `tax.administration_fiscal_year`, `PreparationItemCheck` y `tax.yaml` 1.1.0;
  - el diseño, aprobado el 2026-11-16;
  - la H2 y la enmienda 1 (H2‑5).
- **Recursos compartidos:**
  - `backend/` entero: una sola sesión a la vez. Todos los PR del backend tocan `pom.xml`, `ModularityTest`, las migraciones o los contratos;
  - `jurisdiction.yaml` y `tax.yaml` (C2a y C2b): los lee el *frontend* por su *release*;
  - `liv-components` 0.6.0: lo publica la persona después del U4;
  - el entorno local (`tools/dev/iniciar.sh`), exclusivo: lo usan la validación en local del *frontend* (con C10 aplicado) y E1. Las pruebas del backend usan Testcontainers y no lo necesitan;
  - Figma: solo lectura.
- **Orden entre proyectos:**
  - R1, R2a y R2b primero;
  - el backend en serie: B1 → C2a → C2b → B2 → B3 → B4 → B5 → B6 → B7 → B8 → C1a → C1b → C3 → C9a → C9b → C4 → C5a → C5b → C6a → C6b → C7 → C8 → C10. C2a y C2b van pronto para que el *frontend* empiece, y C9b antes de C4 para que F3 tenga `bff.yaml`;
  - `ui/` (U1 → U2 → U3 → U4) en paralelo con el backend, desde la H2;
  - el *frontend* (F0 → F1 → … → F5), en cuanto están el *release* de C2b y 0.6.0 en npm. Valida en local cuando C10 está listo; E1 va al final;
  - C10 se integra después de validar el *frontend* en local, y el *frontend* después de C10, como en la 007.

**Oleadas** (máximo tres sesiones a la vez, una sola en `backend/`):

| Oleada | Sesión 1 (`backend/`) | Sesión 2 | Sesión 3 |
|---|---|---|---|
| 1 | — | R1 (raíz) | R2a y R2b (raíz) |
| 2 | B1, C2a, C2b | U1, U2 (`ui/`) | — |
| 3 | B2 a B8 | U3, U4 (`ui/`), y la persona publica 0.6.0 | F0, F1 (`frontend/`), en cuanto hay 0.6.0 |
| 4 | C1a, C1b, C3, C9a, C9b, C4 | F2, F4a (`frontend/`) | — |
| 5 | C5a, C5b, C6a, C6b, C7, C8 | F3, F4b, F5 (`frontend/`) | E1 en rojo (`e2e/`), sin el entorno |
| 6 | C10 | Validación en local del *frontend* y E1 (entorno exclusivo) | — |

### Mapa de los PR

| PR | Proyecto | Contenido | Líneas | Depende de | *Skill* |
|---|---|---|---:|---|---|
| R1 | raíz | Doc. 03 v0.6, Doc. 01 v0.7, Doc. 05 v0.3, ERD 2.5, `AGENTS.md` | ~400 | H2 | — |
| R2a | raíz | ADR 0036 y 0037, `docs/arquitectura/01` §3 y `02` §7 | ~300 | H2 | `liv-adr` |
| R2b | raíz | ADR 0038, 0039 y 0040, `docs/arquitectura/01` §5.3 y §6 | ~350 | H2 | `liv-adr` |
| B1 | `backend/` | Cargador de producción, `pom.xml`, `.gitattributes`, regla de ArchUnit | ~150 | R2b | — |
| C2a | `backend/` | `jurisdiction.yaml` 1.0.0: consultas, modelo y motivos | ~350 | R1 | `liv-endpoint` |
| C2b | `backend/` | `jurisdiction.yaml` 1.1.0: acciones; `tax.yaml` 1.2.0; `platform.yaml` 1.1.0 | ~350 | C2a | `liv-endpoint` |
| B2 | `backend/` | CJ01, registro de versiones, `ConfigTree` | ~350 | B1 | — |
| B3 | `backend/` | Núcleo del validador, CJ02, CJ03 | ~400 | B2 | — |
| B4 | `backend/` | CJ04, CJ05, CJ06, CJ08 | ~350 | B3 | — |
| B5 | `backend/` | CJ12, CJ13, CJ14 | ~400 | B3 | — |
| B6 | `backend/` | CJ15, CJ16 | ~400 | B3 | — |
| B7 | `backend/` | CJ09, CJ10, CJ11, CJ17, CJ18 | ~400 | B3 | — |
| B8 | `backend/` | Pruebas *golden*, 008-28, 008-30, 008-32 | ~300 | B4 a B7 | — |
| C1a | `backend/` | Tabla, índices, RLS y permisos | ~300 | R1, R2a | `liv-migracion` |
| C1b | `backend/` | Disparadores | ~300 | C1a | `liv-migracion` |
| C3 | `backend/` | Motor de ingesta en `platform` | ~400 | R2b | — |
| C4 | `backend/` | Dominio del ciclo de vida | ~350 | C3 | — |
| C5a | `backend/` | Carga: persistencia, puertos y caso de uso | ~400 | B8, C1b, C3, C4 | `liv-endpoint` |
| C5b | `backend/` | API de consulta, carga y descarga | ~400 | C2b, C5a | `liv-endpoint` |
| C6a | `backend/` | Aprobar, rechazar, anular | ~400 | C5b | `liv-endpoint` |
| C6b | `backend/` | Publicar y la fachada de la publicada | ~400 | C6a | `liv-endpoint` |
| C7 | `backend/` | Auditoría | ~350 | C6b | — |
| C8 | `backend/` | Punto 9 | ~200 | C6b, C2b | — |
| C9a | `backend/` | Aviso del tope (`reportOversizeUpload`) | ~200 | C2b, C3 | `liv-endpoint` |
| C9b | `backend/` | BFF y `bff.yaml` | ~300 | C9a | — |
| C10 | `backend/` | Abrir la opción, `limpiar-vigencias.sh` | ~120 | C7, C8, C9b, validación del *frontend* | — |
| U1 | `ui/` | Iconos | ~120 | H2 | `liv-componente` |
| U2 | `ui/` | `Checkbox`, `TextArea` | ~400 | U1 | `liv-componente` |
| U3 | `ui/` | `FileField` | ~350 | U1 | `liv-componente` |
| U4 | `ui/` | `Tab`, 0.6.0 | ~350 | U2, U3 | `liv-componente` |
| F0 | `frontend/` | Contratos, 0.6.0, texto del punto 9 | ~150 | C2b (*release*), U4 (npm) | `liv-pantalla` |
| F1 | `frontend/` | Lista | ~400 | F0 | `liv-pantalla` |
| F2 | `frontend/` | Versiones de la vigencia | ~250 | F1 | `liv-pantalla` |
| F3 | `frontend/` | Cargar | ~350 | F1, C9b | `liv-pantalla` |
| F4a | `frontend/` | Detalle y pestañas | ~400 | F2 | `liv-pantalla` |
| F4b | `frontend/` | Cambios y descarga | ~300 | F4a | `liv-pantalla` |
| F5 | `frontend/` | Diálogos de acción | ~400 | F4a | `liv-pantalla` |
| E1 | `e2e/` | Pruebas de navegador | ~400 | F5, C10 | `liv-prueba-e2e` |

Son **38 PR**. Las cifras son techos: suman unas 12.450 líneas sin *lockfiles* ni archivos generados, y unas 11.400 sin los tres PR de la raíz, que la estimación de la spec no contaba (7.300 a 10.300 en los bloques B a E). La diferencia está en el aviso del BFF (H2‑8), los disparadores de la base de datos y el detalle de las pruebas del validador. Un PR que pase de 400 líneas se parte en el momento, sin cambiar el orden.

## Decisiones

_Decisiones del plan que no necesitan la H2 porque no cambian ninguna regla ni ningún documento; las que sí, están en «Decisiones para la H2»._

- **Una sola tabla, sin tablas de `platform`.** El original, el resultado y la comparación son de la versión e inmutables. Separar el resultado en otra tabla solo agregaría una FK y otra política RLS. *Descartado:* una tabla de hallazgos por fila, porque nadie los consulta por separado.
- **El árbol propio del dominio (`ConfigTree`).** El dominio no depende de Jackson ni de otro módulo (`ArchitectureRulesTest` y la regla nueva del B1), y así el validador se prueba con árboles escritos a mano. *Descartado:* `JsonNode` en el dominio.
- **La máquina de estados en `platform`, las reglas de la 1.5.9 en el dominio.** El coordinador de `platform` comprueba la transición y la segregación, y el agregado, las marcas, el validador y la publicación. Así el dominio sigue sin depender de `platform` (`docs/arquitectura/01` §4.1). *Descartado:* llevar la máquina de estados a `shared` (otro ADR para una pieza que solo usa el motor).
- **Las rutas como JSON Pointer.** Son las de los casos comunes y las de CJ01, así que todos los hallazgos se leen igual. *Descartado:* la notación `a.b[0]`.
- **CJ11 compara las listas por posición.** Es determinista y no supone claves. Un elemento insertado en medio de una lista se ve como varios cambios; si eso molesta a quien aprueba, una versión menor del validador puede emparejar por `codigo` o `id`.
- **El `digest` del resultado** identifica lo que vio el aprobador y lo que se vuelve a validar al publicar, sin comparar el JSONB campo por campo.
- **El bloqueo por vigencia** (`pg_advisory_xact_lock`) en la carga y en todas las transiciones. Con `READ_COMMITTED` y lecturas separadas, una carga podía ver la publicada anterior y ya no la en curso. *Descartado:* `SERIALIZABLE`, que obliga a reintentar.
- **La carga valida en la misma transacción que guarda**, pero recibe y lee fuera: una carga nunca se queda en *Cargada* (008-05), y la conexión a la base no espera a la red.
- **El número de versión lo pone el servidor** (el que le toca), no el archivo. CJ17 exige que coincidan, y la fila *Con errores* se ve en el historial con el número que esperaba.

## Riesgos

- **El reenvío del cuerpo por el BFF** (Spring Cloud Gateway Server MVC) podría guardar el cuerpo en memoria o cambiar su codificación. *Mitigación:* C9b lo prueba con una API falsa y un archivo con CRLF y bytes no ASCII (008-05). El tope del BFF acota la memoria.
- **El navegador puede perder la conexión** cuando el BFF responde 413 a un archivo grande antes de recibirlo entero. *Mitigación:* el intento queda auditado igual por el aviso a la API (`reportOversizeUpload`), y la pantalla muestra el mensaje de `TAMANO` (H2‑8). E1 lo prueba con un archivo de 5 MiB.
- **Las pruebas *golden* dependen de interpretaciones** (H2‑5, H2‑6, H2‑19 y la fusión de advertencias). *Mitigación:* la H2 aprueba el conjunto exacto antes de escribir código; un cambio de interpretación después es una versión del validador y un cambio del conjunto.
- **El tamaño:** 38 PR, de ellos 23 en serie en el backend, son el camino crítico. *Mitigación:* C2a y C2b van pronto para desbloquear el *frontend*; `ui/` va en paralelo desde el principio; los PR se cortaron de antemano para no pasar de 400 líneas.
- **Los disparadores y los permisos por columna** pueden chocar con Hibernate si emite un `UPDATE` propio. *Mitigación:* como en la 007, todas las columnas `updatable = false` y las transiciones con `UPDATE` explícitos. Una prueba cuenta las sentencias.
- **La revalidación al publicar** ocurre dentro de la transacción, con el bloqueo de la vigencia. Con un archivo de 1 MiB, cargar y validar toma tiempo. *Mitigación:* la prueba de tiempo de C6b; si pasa de un segundo, se revisa antes de la H3.
- **La primera interfaz con nombre de Spring Modulith** (`platform.ingestion`) puede cambiar lo que `ModularityTest` ve. *Mitigación:* el ADR 0038 la registra, y C3 ajusta la prueba en el mismo PR.
- **Los anexos cambian antes de la feature aparte:** cambiaría el SHA‑256 y las pruebas *golden*. *Mitigación:* la spec ya lo prohíbe hasta esa feature (008-32), y `AnnexCopiesTest` lo detecta en las copias.
- **El `CHECK` de 4096 caracteres de `audit_event`.** *Mitigación:* la lista blanca acotada, los motivos sin caracteres de control y la prueba del peor caso con escapes (C7).
- **La excepción del original en la base de datos** se olvida al llegar a producción. *Mitigación:* el ADR 0036 la ata a la feature 1.3 o a antes de producción, y el orquestador la deja en las decisiones abiertas de `ESTADO.md`.

## Trazabilidad de los criterios

| Criterio | PR | Prueba |
|---|---|---|
| 008-01 | C5b, F1, C10 | `ListJurisdictionConfigsTest`, `jurisdiction-configs.spec.ts`, `MenuCatalogTest`, E1 |
| 008-02 | C5b, C6a, F2, F4a | `ListFiscalYearVersionsTest`, `GetJurisdictionConfigVersionTest`, `RejectJurisdictionConfigTest` (quién aprobó), `fiscal-year-versions.spec.ts`, E1 |
| 008-03 | C5b, F1 | `JurisdictionConfigsApiTest` (403), prueba del 403 por URL, E1 |
| 008-04 | C5b, C9b, F4b | `DownloadOriginalTest`, `BffContractTest`, E1 |
| 008-05 | C3, C5a, C9b | `FileReceiverTest`, `UploadJurisdictionConfigAdapterTest`, `BffContractTest` |
| 008-06 (V-04) | C4, C5b | `UploadPolicyTest`, `JurisdictionConfigsApiTest` |
| 008-07 | B1, C3, C5a, C9a, C9b, C7 | `JurisdictionConfigCommonCasesTest`, `RequestBodyLimitFilterTest`, `ReportOversizeUploadTest`, `BffContractTest`, `JurisdictionConfigAuditTest` |
| 008-08 | B1, C5b | `JurisdictionConfigCommonCasesTest`, `NoYamlConverterTest`, prueba del 415 |
| 008-09 | C1a, C4, C5a, C9b | `AdministrationIsolationTest`, `UploadPolicyTest`, `UploadJurisdictionConfigAdapterTest` (A y B), `BffContractTest` (CRLF) |
| 008-10 | C1a, C3, C4, C5a | `JurisdictionConfigTableTest`, `UploadPolicyTest`, prueba de dos cargas a la vez |
| 008-11 (CJ01) | B2, B3 | `SchemaCheckTest`, `JurisdictionConfigCommonCasesTest`, `JurisdictionConfigValidatorTest` |
| 008-12, 008-13 (CJ02) | B3, B8 | `Cj02Test`, `ParticipationOnlyTest` |
| 008-14 (CJ03) | B3 | `Cj03Test` |
| 008-15 (CJ04) | B4 | `Cj04Test` |
| 008-16 (CJ05) | B4 | `Cj05Test` |
| 008-17 (CJ06) | B4 | `Cj06Test` |
| 008-18 (CJ08) | B4 | `Cj08Test` (con la enmienda 1) |
| 008-19 (CJ09, CJ10) | B7 | `Cj09Cj10Test` |
| 008-20 (CJ12) | B5 | `Cj12Test` (20 casos) |
| 008-21 (CJ11) | B7, F4b | `Cj11Test`, prueba de la pestaña de cambios, E1 |
| 008-22 (CJ13) | B5 | `Cj13Test` |
| 008-23 (CJ14) | B5 | `Cj14Test` |
| 008-24 (CJ15) | B6 | `Cj15Test` |
| 008-25 (CJ16) | B6 | `Cj16Test` |
| 008-26 (CJ17) | B7, C1a | `Cj17Test`, `JurisdictionConfigTableTest` |
| 008-27 (CJ18) | B7 | `Cj18Test` |
| 008-28 | B8 | `ParticipationOnlyTest` |
| 008-29 | B3, C5a | `JurisdictionConfigValidatorTest`, `UploadJurisdictionConfigTest` |
| 008-30 | B2, B8, C5a | `ConfigTreeDecimalsTest`, lectura de vuelta en `UploadJurisdictionConfigAdapterTest` |
| 008-31 (CA‑5.13) | B8 | `JurisdictionConfigGoldenTest` |
| 008-32 | B1, B8 | `AnnexCopiesTest` |
| 008-33 (CA‑05) | C1a, C3, C4, C6a, C6b, C7 | `JurisdictionConfigTableTest`, `IngestionLifecycleTest`, `JurisdictionConfigVersionTest` (sin el coordinador), pruebas de aprobar, rechazar y publicar como el cargador, `JurisdictionConfigAuditTest` |
| 008-34 (CJ02) | C4, C6a, F5 | `JurisdictionConfigVersionTest`, `ApproveJurisdictionConfigTest`, prueba del diálogo |
| 008-35 | C1b, C4, C6a, F5 | `JurisdictionConfigTableTest`, `RejectJurisdictionConfigTest` |
| 008-36 | C1a, C3, C6a, F5 | `JurisdictionConfigTableTest` (`annulled_by`), `AnnulJurisdictionConfigTest` |
| 008-37 | C3, C6a | `IngestionLifecycleTest`, prueba de la carrera |
| 008-38 | C1a, C1b, C6b | `JurisdictionConfigTableTest`, `PublishJurisdictionConfigAdapterTest` |
| 008-39 | C4, C6b | `JurisdictionConfigVersionTest`, `PublishJurisdictionConfigAdapterTest` (cada motivo) |
| 008-40 | C1a, C6b | `PublishJurisdictionConfigTest` |
| 008-41 | C1a, C1b, C5a | `JurisdictionConfigTableTest`, `LivAppGrantsTest` |
| 008-42 | C6b | `PublishedJurisdictionConfigsTest` |
| 008-43 (Doc. 01 §6.1) | C2b, C8, F0 | `JurisdictionConfigPreparationCheckTest`, `GetFiscalYearTest`, prueba de la lista de preparación, E1 |
| 008-44 (CA‑12) | C1a, C5b | `AdministrationIsolationTest`, `JurisdictionConfigsApiTest`, E1 |
| 008-45 | C1a, B7 | `AdministrationIsolationTest`, `Cj17Test` |
| 008-46 | C5b, C6b, C7 | `DownloadOriginalTest` (auditada), `JurisdictionConfigAuditTest` |
| 008-47 | C3, C6a, C6b, C7 | `RejectedAttemptLogTest`, `JurisdictionConfigAuditTest` (intentos rechazados) |
| 008-48 a 008-52 | — | Feature aparte (decisión 19) |
| 008-53 | U1 a U4, F1 a F5, E1 | axe en cada componente y pantalla, E1 |

## Revisión

**Ronda 1 (2026-11-16)**, dos `liv-revisor-critico` ciegos en paralelo (`liv-revision-adversarial`). Ninguno encontró críticos; los dos recalcularon el conjunto *golden* sobre los anexos y coincidió, salvo las dos rutas corregidas abajo.
- **Revisor A:** 3 altos, 9 medios y 8 bajos.
- **Revisor B:** 4 altos, 7 medios y 8 bajos.

La tabla agrupa los hallazgos que vieron los dos, con la severidad mayor; por eso sus filas no suman lo mismo que los conteos de cada revisor.

| Hallazgo | Severidad | Lo vio | Resultado |
|---|---|---|---|
| La carga leía el cuerpo con la transacción abierta (denegación de servicio por clientes lentos) | Alto | A y B | **Corregido:** se recibe y se lee fuera de la transacción, con tiempo máximo (C3, C5a) |
| Los intentos rechazados (filtro, cargador, segregación al publicar) no tenían un mecanismo que sobreviviera al *rollback*; el filtro publicaba fuera de una transacción y sin orden respecto a la seguridad | Alto | A y B | **Corregido:** `IngestionEngine` sin transacción y `RejectedAttemptLog` en su propia transacción, después; el filtro va después de la seguridad y solo audita con permiso (H2‑10, C3) |
| El `CHECK` por estado y el disparador impedían rechazar una *Aprobada* o borraban quién la aprobó | Alto | A | **Corregido:** `approved_*` opcionales en *Rechazada* y conservados por el disparador (modelo, C1b) |
| El agregado del dominio usaba `platform` (`docs/arquitectura/01` §4.1) | Alto | A | **Corregido:** la máquina de estados la aplica el coordinador; regla nueva de ArchUnit (C3, C4, B1) |
| `missingPaths` como lista viola el ADR 0035 | Alto | B | **Corregido:** `missingCount` escalar y H2‑21 |
| Topes de BFF y API distintos contradecían 008-07; un 413 de la API a mitad del reenvío daría 502 | Medio | A y B | **Corregido:** el mismo tope en los dos, `Content-Length` obligatorio y aviso interno para auditar (H2‑8, C9a, C9b, ADR 0040) |
| FK de `compared_with_id` y `superseded_by` sin la vigencia; un reemplazo podía «despublicar» | Medio | A y B | **Corregido:** FK con `fiscal_year`, condiciones del disparador y disparador diferido (modelo, C1b) |
| CJ03 comparaba contra `codigo`; la clave de las normas es `id` | Medio / bajo | A y B | **Corregido** (B3) |
| La descarga en una transacción de solo lectura no puede publicar su evento | Bajo | A y B | **Corregido** (C5b) |
| `ModularityTest` («solo `platform` y `tax` dependen de `shared`») y los prefijos de `AGENTS.md` §5.3 | Bajo | A y B | **Corregido** (C5a, R1, H2‑14) |
| Ruta de CJ09: `/base_gravable/catalogo_ministerio/multiplicador_tablas` | Medio | A | **Corregido** (B7, *golden*, `AGENTS.md` §5.2 en R1) |
| Carga y publicación a la vez con `READ_COMMITTED` | Medio | A | **Corregido:** bloqueo por vigencia (C3) |
| `INSERT` de tabla dejaba fijar `id` y `uploaded_at` | Medio | A | **Corregido:** `INSERT` por columna (modelo) |
| La aprobación no recibía lo que vio el aprobador | Medio | A | **Corregido:** `validationDigest` en el cuerpo; comprobaciones de 008-39 declaradas como defensa en profundidad, con cómo se prueba cada motivo (C4, C6b) |
| Catálogo de motivos inexistente al publicar el contrato | Medio | B | **Corregido:** «Catálogo de motivos» y H2‑22 |
| Estados HTTP de los códigos nuevos frente al ADR 0035, y relación con `F001` a `F003` | Medio | B | **Corregido:** H2‑4 |
| C1, C2 y R2 pasaban de 400 líneas | Medio | B | **Corregido:** C1a y C1b, C2a y C2b, R2a y R2b |
| H2‑5 cambia 008-18: es una enmienda a la spec | Medio | B | **Corregido:** enmienda 1, que escribe el orquestador y firma la H1 (H2‑5) |
| CJ18 no miraba las fechas `MM-DD` | Medio | B | **Pregunta a la persona:** H2‑19 |
| Convenio con fechas invertidas | Bajo | B | **Pregunta a la persona:** H2‑20 |
| Un municipio del 20 % con `calendario` en `PENDIENTE` | Bajo | A | **Corregido:** explícito en H2‑6, con su prueba (B8) |
| CJ05 sin `ELECTRICO` | Bajo | A | **Corregido** (B4) |
| Límites configurables frente al 1 MiB del cargador | Bajo | A | **Corregido:** constantes (C3) |
| `TAMANO` con 413 o 422 | Bajo | A | **Corregido:** siempre 413 (H2‑4, C5b) |
| SHA‑256 de otra administración en la prueba del adaptador | Bajo | A | **Corregido** (C1a, C5a) |
| Listas con `BYTEA` y JSONB | Bajo | A | **Corregido:** proyecciones (C5a) |
| `annulled_by` y el límite de los `CHECK` de segregación | Bajo | B | **Corregido** (modelo) |
| Dos filas de auditoría con un solo `eventId` | Bajo | B | **Corregido:** `JurisdictionConfigSuperseded` propio (C6b, C7) |
| Interfaces del motor sin quien las llame e interfaz con nombre sin ADR | Bajo | B | **Corregido:** `IngestionEngine` las llama; ADR 0038 (C3, R2b) |
| U1 modifica `Icon` sin aprobación explícita; usuarios de `e2e/` mal nombrados | Bajo | B | **Corregido:** H2‑18 y E1 |
| Escapes de JSON en el peor caso de `new_values` | Bajo | B | **Corregido:** motivos sin caracteres de control y prueba con escapes (C2b, C7) |

**Ronda 2 (2026-11-16)**, los mismos dos niveles, solo sobre lo corregido. Los dos dieron por resueltas las correcciones de la ronda 1 salvo las de abajo. Comprobaron también que el modelo es implementable en PostgreSQL 18 (el disparador diferido sobre la misma tabla y el orden de los `UPDATE` de la publicación) y que el mapa no tiene ciclos y respeta las tres sesiones.
- **Revisor A:** 2 altos, 5 medios y 5 bajos.
- **Revisor B:** 1 alto, 6 medios y 5 bajos.

| Hallazgo | Severidad | Lo vio | Resultado |
|---|---|---|---|
| El aviso interno del BFF (`/internal/**`) no podía resolver al usuario, su administración ni su permiso sin cambiar las cadenas de seguridad | Alto | A y B | **Corregido:** el aviso es la operación pública `reportOversizeUpload` de `platform.yaml`, por la cadena normal de `/api/**`, con el permiso de un registro cerrado de rutas (H2‑8, ADR 0040, C2b, C9a) |
| C2a y C2b publicaban dos contratos distintos con el mismo 1.0.0 (ADR 0027) | Alto | A | **Corregido:** C2b sube a 1.1.0 (H2‑23) |
| El BFF respondía 413 a quien no tiene permiso, y no se fijaba su orden respecto a la sesión y el CSRF; faltaba `bff.yaml` | Medio | A y B | **Corregido:** el filtro va después de `TokenRelayFilter`, la respuesta del aviso decide 403 o 413, y `bff.yaml` declara 411 y 413 (C9b) |
| El 413 de `CG-06` `TAMANO` no cabe en los tipos del ADR 0035 | Medio | A y B | **Corregido:** excepción `PayloadTooLarge` de `platform`, declarada en H2‑4 y en el ADR 0038 |
| Cuerpo en trozos: dos dueños posibles del rechazo y auditoría doble o nula; plazo de lectura irrealizable con Tomcat | Medio / bajo | A y B | **Corregido:** `Content-Length` obligatorio en la API también (411), el filtro es el único dueño por encima del tope, y un temporizador da el plazo total (C3) |
| El orden «bloqueo, luego leer» es imposible con rutas por `{id}`; espacio del bloqueo sin fijar | Medio / bajo | A y B | **Corregido:** leer la vigencia, bloquear y volver a leer; espacio propio, distinto del de la 007 (C3, C6a) |
| Caminos en que un rechazo no queda auditado (aviso fallido, *token* vencido, fallo de `RejectedAttemptLog`) | Medio | B | **Corregido en parte:** el *token* se renueva antes del aviso, el `attemptId` hace idempotente el reintento, y un fallo queda con nivel `ERROR` y una métrica. El aviso fallido queda como riesgo residual aceptado, dicho en H2‑8 |
| `platform` necesitaba datos de `jurisdiction` para el filtro | Medio | B | **Corregido:** registro `IngestionRoute` que aporta cada módulo, con una prueba que lo compara con `@RequiresPermission` (C3) |
| `CJC-01` con 409 para una vigencia que no existe contradice el ADR 0035 | Medio | B | **Corregido:** 404 `SYS-404` (H2‑4, C4, contrato) |
| La segregación salió del dominio, y 008-33 dice que la aplica el dominio | Medio | A | **Corregido:** el agregado la comprueba también (H2‑24, C4) |
| `PUBLISHED_CHANGED` y `FISCAL_YEAR_NOT_IN_PREPARATION` no pueden ocurrir en la aplicación; C4 y C6b se contradecían | Medio | A | **Corregido:** solo `VALIDATOR_CHANGED` puede ocurrir; las demás se prueban alterando filas (C4, C6b) |
| FK compuestas de `rejected_by` y `published_by` | Bajo | A | **Corregido** (modelo, C1a) |
| La regla de ArchUnit no era una lista blanca | Bajo | A | **Corregido** (B1) |
| Catálogo de CJ01 abierto; `START_MISMATCH` con dos significados; guion de `V-04` en el contrato | Bajo | A y B | **Corregido:** `SCHEMA_VIOLATION` con `params.keyword`, dos motivos distintos y guion ASCII en el contrato |
| CJ18 por la forma del texto daba falsos errores en textos libres | Bajo | A | **Corregido:** solo en las rutas que el esquema declara como fecha (B7) |
| H2‑20 suponía de qué fechas habla el Doc. 03 §9.5 | Bajo | B | **Corregido:** la ambigüedad va en la pregunta (H2‑20) |
| H2‑10 decía que la 001 no anida transacciones (usa `REQUIRES_NEW`) | Bajo | B | **Corregido** (H2‑10) |
| Pruebas de C1a y C1b (insertar frente a actualizar, `approved_*` a medias, una prueba que la FK hacía pasar sin el disparador) | Bajo | B | **Corregido** (modelo, C1a, C1b) |
| Pruebas de C5a y C9a que dependían del *listener* de C7 | Bajo | B | **Corregido:** prueban el evento en el registro; la fila, en C7 |

**Escalado a la persona.** La ronda 2 dejó dos altos. La *skill* `liv-revision-adversarial` admite dos rondas: con altos en la segunda, se detiene y escala a la persona. Los dos están corregidos en el plan, pero **ninguna ronda ha revisado esas correcciones**. La persona decide en la H2 si basta, o si pide una verificación acotada de C2b, C9a, C9b, H2‑8 y ADR 0040 antes de aprobar, como en la 007.

**Lente de legibilidad (2026-11-16)**, `liv-revisor-rapido` sobre el cambio preparado: tablas y enlaces de Figma bien formados; 12 hallazgos de estilo (1 alto, 8 medios y 3 bajos). Se aplicaron todos menos uno: las celdas largas de H2‑4 y H2‑8 pasaron a sus subsecciones, se aclararon seis frases, se completó una dependencia del mapa, se explicó el conteo de la ronda 1 y se unificó `V-04` con el guion ASCII. Se descartó cambiar la etiqueta «008-34 (CJ02)», que es la de `spec.md`.

- [x] Lente de legibilidad.
- [ ] Aprobación H2: <quién, fecha>.
