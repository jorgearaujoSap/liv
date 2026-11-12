# Changelog — liv-api (backend)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `backend-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Esqueleto de la Fase 0 (F0‑04): proyecto Maven (`liv-api`, *wrapper* con Maven 3.10.0) con Java 21 LTS, Spring Boot 4.1.2 y Spring Modulith 2.1.1. La base sigue las convenciones del equipo (Maven, capas `api`/`application`/`domain`/`infrastructure`, pruebas guardianas), con mejoras para este proyecto.
- Módulos `shared`, `platform`, `core`, `documents`, `jurisdiction`, `valuation`, `tax`, `ledger`, `audit` y `reporting` como paquetes de `co.com.sapenlinea.liv`.
- Pruebas guardianas: arranque; fronteras entre módulos (Spring Modulith); catálogo de módulos y capas estándar; reglas de arquitectura (controladores sin infraestructura, dominio hexagonal sin frameworks, aplicación con puertos, dinero sin coma flotante, reloj inyectado, inyección por constructor); tamaño de clases y dependencias, sin excepciones.
- Formato con Spotless y Google Java Format (2 espacios), verificado en `mvn verify`.
- Migraciones Flyway de la feature 001 (T10), parte 1: esquemas `platform`, `core` y `audit`; tabla del *outbox* (`platform.event_publication`); `core.territorial_entity`, `tax_administration`, `admin_user` y `user_session` con FK compuestas y RLS por `app.administration_id`; función `core.find_user_by_identity` (`SECURITY DEFINER`, dueño `liv_identity_lookup`). El outbox queda sin DELETE ni UPDATE general para `liv_app`. Una sesión cerrada no se reabre. Flyway solo corre con los perfiles `dev` (en `config/`, fuera del jar) y `test`, como `liv_owner`; la primera migración comprueba el contrato de roles.
- Pruebas con PostgreSQL 18.6 en Testcontainers y los roles reales del entorno local: aislamiento por administración (001-17), endurecimiento de la función de identidad y permisos exactos de `liv_app`.
- Migraciones Flyway de la feature 001 (T10), parte 2: `platform.app_role`, `app_permission` y `role_permission` con la matriz de permisos aprobada en H2; `audit.audit_event` de solo inserción (con `source`, `source_event_id` y `actor_ref`, y un disparador que rechaza el cambio de datos incluso al dueño; no protege contra su DDL), `audit.import_cursor`, `audit.record_platform_session_denied` (`SECURITY DEFINER`, dueño `liv_audit_platform`, con lista blanca de `new_values`, longitudes y fecha) y `platform.shedlock`. Las filas de plataforma solo las escriben el importador (`app.platform_job = 'on'`) y esa función.
- Contratos OpenAPI de la feature 001 (T11): `contracts/openapi/core.yaml` (`GET /api/v1/me`), `platform.yaml` (`GET /api/v1/me/capabilities`) y `core-internal.yaml` (`POST /internal/v1/sessions` y `/{sid}/end`). `openapi-generator-maven-plugin` 7.26.0 (ADR 0023) genera las interfaces de servidor en `<modulo>.api.contract` con las opciones de Spring Boot 4 (Jackson 3, JSpecify y validación de Spring MVC), y valida cada contrato en el *build*. `ContractRulesTest` exige que todo controlador implemente una interfaz generada sin rutas propias, y `OpenApiContractTest` compara los contratos con el plan y con las decisiones del responsable del 2026-11-02: `StartSession` sin `sessionAuditId`, con el `sid` del token (si no coincide, 401 con `001-14`) e idempotente; `EndSession` siempre 204; `001-14` para el usuario no registrado.
- Seguridad de `platform` (feature 001, T12): servidor de recursos con `spring-boot-starter-oauth2-resource-server` (ADR 0023) y solo OIDC estándar (001-20), configurado con `LIV_OIDC_ISSUER_URI` y `liv.security`. Tiene tres cadenas:
  - `/internal/**`: token de servicio de `liv-bff-service`, con audiencia `liv-internal` y `azp`; al iniciar sesión, además, `X-User-Token` validado antes del controlador;
  - `/api/**`: token de usuario con audiencia `liv-api`, más `CurrentUserFilter`, que responde 401 con `001-14`, `001-05` o `001-17` y abre `AdministrationContext` con la administración registrada;
  - el resto: solo salud e información.

  También agrega:
  - `UserDirectoryAdapter` en `core`, sobre `core.find_user_by_identity`;
  - Problem Details con el código del criterio;
  - `X-Correlation-Id` solo como UUID, en el MDC y en el patrón de log;
  - tokens enmascarados en los mensajes de log;
  - cabeceras de hasta 32 KB;
  - todo token con `exp`, y tiempos de espera de 3 s al proveedor y de 5 s a la base;
  - todo error como Problem Details con `code` y sin detalles internos (`SYS-404`, `SYS-500` y `SYS-503` para los técnicos), también los fallos de los filtros, registrados con el id de correlación;
  - enmascarado de credenciales también en las trazas, entre comillas y en `Bearer`;
  - reglas de ArchUnit: sin `org.keycloak`; los modelos generados solo en la capa `api`, sin *loggers*; y `AdministrationContext` solo lo abren `platform` y la infraestructura.

  Las pruebas corren contra `mock-oauth2-server` 3.0.1 en Testcontainers, con la configuración de producción.
- Contexto RLS por transacción (feature 001, T12 parte 2):
  - `spring-boot-starter-data-jpa` (ADR 0023);
  - un `JpaTransactionManager` extendido fija siempre, con alcance local, `app.administration_id` (la de `AdministrationContext`, o vacío) y vacía `app.platform_job` al empezar cada transacción, también en `REQUIRES_NEW`. Abrir otra administración dentro de una transacción abierta falla. Se prueba con un *pool* de una sola conexión (A y luego B, en la misma conexión física), con un valor de sesión dejado en la conexión, con escritura, con `REQUIRES_NEW` y con un oyente asíncrono;
  - cada conexión nueva comprueba que el usuario de la sesión y el rol sean `liv_app`, sin superusuario, `BYPASSRLS`, membresías ni contexto fijado en el rol o la base. Se impone en código, así que ninguna propiedad lo quita, y la sonda de *readiness* incluye la base;
  - decisiones del responsable: el token de usuario exige `azp = liv-bff`, y el emisor y las llamadas al proveedor son `https://` salvo con `liv.security.allow-http-issuer` (`dev` y pruebas).
- Prueba de la API contra Keycloak real (feature 001, T12 parte 3): Keycloak 26.8.1 con el *realm* de `identity/config/base` y la capa de `dev/keycloak`, aplicados por keycloak-config-cli como en `dev/compose.yaml`, sin dependencias nuevas.
  - Comprueba la forma real de los tokens: `typ`, audiencias, `azp`, roles planos y `administration_id`.
  - El token de usuario del BFF (código con PKCE) entra en `/api` y como `X-User-Token`; el de servicio cierra sesiones en `/internal`.
  - Ninguno sirve en el lugar del otro, un token de otra administración da 401 con `001-17`, y el *realm* exige PKCE.
- Inicio y fin de sesión de LIV (feature 001, T13 parte 1):
  - `StartSession` y `EndSession` en `core`, con el controlador de `core-internal.yaml` y `JdbcSessionStore` (SQL bajo RLS);
  - en el primer ingreso, el usuario se registra con su identificador de LIV (001-21);
  - rechazos con 401 `001-14`, 403 `001-05` o 403 `001-17`, todos publicados como `SessionDenied`;
  - eventos `SessionStarted`, `UserProvisioned` y `SessionEnded`;
  - `liv.sessions.auto-provision`;
  - en `platform`, `VerifiedUserToken` entrega `TokenUser` y `RequestRefusedException` responde 403 con el código del criterio.
- `GET /api/v1/me` (feature 001, T13 parte 2):
  - el usuario y su administración salen del registro de LIV, bajo RLS (puerto `UserProfiles`);
  - roles del token filtrados por el catálogo de LIV y la unión de sus permisos (`PermissionCatalog`, en `platform`);
  - las pruebas comparan con la matriz aprobada, escrita aparte.
- Tema `liv` de Keycloak e imagen propia (feature 001, T08 parte a; ADR 0024):
  - `identity/Dockerfile`: Keycloak 26.8.1 con el tema, optimizado para PostgreSQL; la hoja se compila con Tailwind 4.3.3 y los *tokens* de `liv-components` 0.2.0, con versión exacta;
  - plantilla común con `Keycloak/BrandPanel`, inicio de sesión (escritorio, móvil, Ingresando y credenciales incorrectas) y página de error, desde los *frames* "001 · Acceso" de LIV-Diseño;
  - mensaje único y misma página para todo fallo de inicio de sesión, también con la cuenta bloqueada o desactivada (001-03, 001-04, 001-05);
  - el *realm* activa el tema (`loginTheme: liv`) y `dev/compose.yaml` construye la imagen;
  - `identity/theme/check.sh` lo prueba en un entorno desechable, incluido el arranque con `start --optimized`.
- Autorización y menú (feature 001, T14):
  - cada método público de un servicio de `application` lleva `@RequiresPermission` o `@OpenUseCase`, y se comprueba con `@EnableMethodSecurity` y un `AuthorizationManager` propio, sin AspectJ, antes que cualquier otro *advice* (también la transacción);
  - se niega por defecto: sin el permiso, el caso de uso no se ejecuta y la respuesta es 403 `001-16`. Un método sin marca (también uno heredado) se niega a todos, y uno protegido se niega fuera de una petición. Los permisos se leen una vez por petición; si no se pueden leer, 503 `SYS-503`;
  - `@OpenUseCase` (decisión del responsable) marca los métodos sin permiso de usuario, por ejemplo los que llamen oyentes y trabajos. Hoy son `GetCurrentUser.get`, `StartSession.start` y `EndSession.end`, y una prueba fija la lista cerrada;
  - ArchUnit exige además servicios interceptables (`@Service`, no `final`, sin superclase), métodos marcados que no sean `static` ni `final`, ningún otro método que no sea privado, ninguna llamada ni referencia con `this` a un método protegido, ningún caso de uso creado con `@Bean`, controladores que usan solo casos de uso (no sus puertos), ningún `@RequiresPermission` en métodos asíncronos u oyentes y ningún `@PreAuthorize`, `@Secured` ni `@RolesAllowed`;
  - `GET /api/v1/me/capabilities` (`platform.yaml`) devuelve el árbol del menú que permiten los permisos del usuario (001-15). Las opciones salen de `platform/menu-catalog.yaml`, con el mapa de Doc. 01 §3 hasta las hojas de 1.5 y 1.6, todas en `COMING_SOON`; un catálogo mal escrito impide arrancar;
  - las pruebas comparan cada uno de los 11 roles con la matriz aprobada, escrita aparte.
- Perfil de usuario del *realm* alineado con el registro de LIV (feature 001; plan, ajustes de T13):
  - nombre de hasta 74 caracteres y apellido de hasta 75: con el espacio del *claim* `name`, 150, como `core.admin_user.full_name`;
  - correo de hasta 200;
  - nombre y apellido obligatorios, porque sin ellos LIV tomaría el correo como nombre (decisión del responsable);
  - los espacios cuentan al medir (`trim-disabled`).

  Así un valor que no cabe en el registro lo rechaza Keycloak al crear o editar el usuario, y no LIV en su primer ingreso. Se prueba contra Keycloak real.
- Auditoría (feature 001, T15; 001-18):
  - *outbox* de Spring Modulith en JDBC (`spring-modulith-starter-jdbc`, ADR 0023) sobre `platform.event_publication`: los eventos de `core` llegan a la auditoría después del *commit*. Un trabajo programado de `platform` reentrega cada 5 minutos los que quedaron pendientes (no al arrancar, para que la API siga arrancando sin base), y el tipo de base se fija en PostgreSQL sin consultarla. Cada entrega completada se borra (`completion-mode: delete`; migración `V202611031000` con `DELETE` para `liv_app` sobre esa tabla), y `SessionDenied` ya no lleva el emisor ni el `sub` (decisiones del responsable);
  - trabajos programados de plataforma con ShedLock (`platform.shedlock`): una réplica a la vez;
  - los eventos de `core` llevan `eventId`, su fecha y el `correlationId` de la petición (`platform.Correlation`); `java.time.Clock` se inyecta desde `platform`;
  - oyentes asíncronos en `audit`, con un ejecutor propio de pocos hilos para no quitar conexiones a las peticiones, para `SessionStarted`, `SessionEnded`, `SessionDenied` y `UserProvisioned`. Cada fila va en la administración del evento; un rechazo sin administración válida va como fila de plataforma por `audit.record_platform_session_denied`, sin actor (decisión del responsable). Una sola vez por evento;
  - importador de eventos de Keycloak (`spring-boot-starter-oauth2-client` y ShedLock 7.10.1, ADR 0023), con el cliente `liv-audit-importer`:
    - copia intentos fallidos, bloqueos, errores de canje de código y cambios de roles y de estado hechos por administradores, solo con `result`, `reason`, `truncated_ip` y `masked_attempted_username` (decisión del responsable);
    - enmascara el usuario intentado (HMAC-SHA256 con `LIV_AUDIT_HMAC_KEY`, más su longitud), trunca la IP (/24 y /48) y guarda al autor como referencia opaca;
    - cada minuto, una réplica a la vez, con marca de agua en `audit.import_cursor` y 5 minutos de superposición. Lee desde el instante exacto (`dateFrom` en milisegundos) por páginas y, si el proveedor tiene más eventos de los que lee una ejecución, la marca no los salta. Avisa si se atrasa más de un día y publica las métricas `liv.audit.importer.lag` y `liv.audit.importer.failures`;
    - todas las llamadas a Keycloak, también la del *token*, tienen tiempo de espera; `LIV_IDP_ADMIN_BASE_URI` permite usar la API de administración por la red interna;
    - el puerto `IdentityProviderEvents` está en `platform` y solo su adaptador conoce la API de Keycloak; se prueba contra Keycloak real;
  - `ModularityTest` verifica las flechas del ADR 0025 (`audit → core`, `audit → platform`, nadie depende de `audit`).
- Tema `liv` de Keycloak, contraseña nueva y segundo factor (feature 001, T08 parte b): páginas de contraseña nueva, configurar el segundo factor (pasos, QR y clave) y pedir su código, con CodeInput y los errores en su campo; `check.sh` las prueba (001-06, 001-07, 001-19).
- Comparador de cambios incompatibles de los contratos y su publicación (F0‑07, compuerta Q08; ADR 0027):
  - `contracts/scripts/compare.sh` compara cada contrato de `contracts/openapi/` con su versión base usando `openapi-diff-maven` 2.1.7, que solo se declara en `pluginManagement` y no corre en el *build*. Si el contrato cambia, `info.version` debe subir; un cambio incompatible, o quitar un contrato, exige la etiqueta `contrato-incompatible` en el PR y la versión mayor subida. Además rechaza los `$ref` externos en cualquier forma de YAML, los archivos que no son contratos `.yaml` (también los ocultos y los enlaces simbólicos) y un proyecto sin contratos. Lo corre el *workflow* `backend-contracts-pr` en cada PR con los scripts de la base, para que un PR no cambie la regla con la que se juzga;
  - `contracts/scripts/publish.sh` publica, como *release* `contract-<nombre>-vX.Y.Z` con el YAML y su SHA-256, el contenido del commit integrado. Antes juzga cada versión con `compare.sh` contra la *release* publicada más alta de su contrato, con la etiqueta del PR integrado, que solo aprueba los contratos que ese commit cambia (decisión del responsable). Crea la *release* como borrador, la publica solo con los dos archivos verificados y se puede volver a correr. Una versión publicada no cambia: si su contenido es otro, falla;
  - `contracts/scripts/test/run.sh` lo prueba en 79 casos: 30 escenarios de comparación (entre ellos quitar un campo requerido de la respuesta y los dos huecos conocidos de `openapi-diff`), 14 con una referencia de Git sobre repositorios temporales y 35 de publicación con un `gh` falso.
- *Skills* de código para los agentes en `.claude/skills/`: `liv-migracion` (migraciones Flyway con RLS, permisos mínimos y *expand/contract*) y `liv-endpoint` (endpoints *contract-first* con su permiso y sus pruebas), basadas en el código de la feature 001.
- Esquema 1.0 de la configuración de jurisdicción (feature 004, F0‑10; ADR 0032): `contracts/jsonschema/config-jurisdiccion/1.0.json` (Draft 2020‑12, solo la forma del YAML, CJ01) y sus casos comunes de carga y de validación, `1.0.casos.json`. `com.networknt:json-schema-validator` 3.0.9 en `test`, sin `jackson-dataformat-yaml`. Las pruebas cargan el YAML con un subconjunto portable (`SafeYamlLoader`, sobre los eventos de SnakeYAML), eligen el esquema por `esquema_version`, comprueban cada criterio, cambian cada nodo de copias de los dos anexos y revisan los archivos del esquema con una meta‑prueba (004‑11: sin valores tributarios). La mutación compara cada campo con su clase del plan (`JurisdictionConfigFieldClasses`). `contracts/jsonschema/**` se guarda con LF.
- Inmutabilidad y publicación de los esquemas JSON (feature 004, F0‑10, paso 2; ADR 0032): `contracts/scripts/jsonschema.sh`, con dos órdenes.
  - `check <referencia>` juzga el commit `HEAD`, lo que Git tiene guardado y se publica, no el árbol de trabajo. Falla si una versión (esquema o casos) que existe en la base cambió de contenido o se quitó; si hay algo fuera de la forma `<nombre>/<MAYOR.MENOR>.json` o `<nombre>/<MAYOR.MENOR>.casos.json` (archivos sueltos, subcarpetas, archivos ocultos, enlaces simbólicos, submódulos o nombres raros); si a una versión le falta el esquema o los casos; si el nombre se repite con un contrato de `contracts/openapi/`; si `HEAD` ya no tiene el *script*, `lib.sh` o las pruebas mientras haya esquemas; y si la carpeta del proyecto pasa por un enlace simbólico.
  - `publish [--dry-run]` publica cada versión como *release* `jsonschema-<nombre>-v<MAYOR.MENOR>` con el esquema, sus casos y el `.sha256` de cada uno, desde el commit `GITHUB_SHA`, con el protocolo de `publish.sh` (borrador, subir, verificar lo descargado, publicar sin marcarla como la más reciente; se puede volver a correr; con otro contenido, falla).
  - `contracts/scripts/test/jsonschema-escenarios.sh` los prueba con el `gh` falso sobre repositorios temporales (los enlaces simbólicos, los submódulos y los nombres raros se ponen en el índice de Git, así que corren igual en Windows y en Linux), y `run.sh` los carga y falla si faltan; son comprobaciones aparte de las del comparador y la publicación de contratos. Todavía no corre en CI: eso es el paso 3 de la feature.
- Esquema 1.1 de la configuración de jurisdicción (feature 005, B1; ADR 0034): `contracts/jsonschema/config-jurisdiccion/1.1.json` y sus casos comunes, `1.1.casos.json`. Es compatible con la 1.0, que no cambia: todo lo nuevo es opcional y una configuración válida en la 1.0 lo es en la 1.1 cambiando solo `esquema_version`.
  - `recaudo` suma `bancos` (`codigo`, `nombre` y `cuenta_recaudo_ref`, un alias del gestor de secretos —un identificador: minúsculas, dígitos y `_`, empezando por una letra— o `PENDIENTE`; un número de cuenta pegado tal cual no pasa), `estado_bancos` y `reporte_participacion_20`; `archivo_banco` suma `formato_preferido`, `formato_alterno`, `formato_por_banco` y `estado`, y su `formato` pasa a ser opcional.
  - La sección nueva `cartera` trae `intereses_mora`, `sanciones`, `prescripcion`, `cartera_historica` y `antiguedad`. Cuatro definiciones nuevas: `anio_o_pendiente` (`vigencias_desde`), `norma_o_pendiente` (`estatuto_territorial`), `cantidad_uvt` (`sancion_minima_uvt`) y `referencia_secreto` (`cuenta_recaudo_ref`); cinco listas cerradas nuevas (`inicio_termino`, `intereses_historicos`, `sistema_determinacion`, `redondeo_causacion.metodo` y los componentes de `intereses_mora.base`: `TAX`, `INTEREST` y `PENALTY`). `PENDIENTE` entra en dos campos que no lo admitían y en una lista cerrada (excepciones a 004‑08 aprobadas en la H2).
  - Pruebas: las copias de los anexos 1.1 de la raíz (`src/test/resources/…/1.1/`), la tabla de campos y las clases de la 1.1 (con las excepciones del oráculo de P2), `JurisdictionConfigSchema11Test` (CJ01, que los casos de la 1.0 no cambian con la 1.1 publicada, y que lo válido en la 1.0 es válido en la 1.1: cada mutación y cada caso de la 1.0, con la versión cambiada primero) y los casos de `1.1.casos.json` de `bancos[]`, `formato_por_banco[]` y `cartera`. Las de la 1.0 no cambian.
  - Al integrarse, `backend-main` publica la *release* `jsonschema-config-jurisdiccion-v1.1`.
- Base de las pruebas *golden* (feature 006, F0‑11; PR B1a): solo código de prueba, sin dependencias nuevas y sin cambios en el `pom.xml` ni en los *workflows*.
  - Apoyo común en `co.com.sapenlinea.liv.golden` (no es un módulo, no depende de `tax` ni de `ledger`): carga y validación de los *fixtures* YAML con el cargador seguro y el validador de la feature 004, SHA‑256 de la configuración citada con fin de línea LF, y un comparador de resultados exactos (escala por campo, listas como multiconjunto exacto, sin coma flotante).
  - `GoldenFixturesTest` (los *fixtures* están completos y bien formados; diecisiete variantes dañadas, comunes a todos los módulos, se rechazan solo por el motivo correcto; el esquema de liquidación suma las suyas en B1b) y `GoldenComparatorTest` (una mutación por tipo de diferencia, con valores sintéticos), ambas en verde.
  - `src/test/resources/golden/README.md`: qué son las *golden*, el formato, la frontera del adaptador, cómo ejecutarlas en rojo, cómo ponerlas en verde y qué hacer cuando cambia un ejemplo. Los esquemas, los casos y las pruebas de liquidación (B1b) y de cuenta corriente (B2) llegan en sus PR.

### Cambiado

- `SafeYamlLoader`, `JurisdictionConfigSchemas` y `CommonCases` (pruebas de la feature 004) pasan a `public` para que las use el paquete `golden`: solo cambia la visibilidad de la clase y de `load`, `compile`, `metaSchemaErrors`, `BACKEND` y `EXACT`; el comportamiento y las pruebas no cambian.

- Pruebas del esquema de configuración de jurisdicción por versión (feature 005, B0; ADR 0034), sin cambiar lo que comprueban las de la 1.0:
  - las copias de los anexos de prueba pasan a `src/test/resources/contracts/jsonschema/config-jurisdiccion/1.0/`;
  - los casos comunes (de carga y de validación) y las pruebas que dependían de `1.0` recorren cada archivo de casos;
  - la tabla de campos, las listas cerradas, el oráculo de la mutación y las rutas de `enum` de `JsonSchemaFilesTest` están por versión, y una versión sin ellas falla;
  - los casos de una versión se evalúan con el registro de esquemas hasta esa versión, comparada como número (`1.10` es mayor que `1.9`). Con una sola versión no cambia ningún resultado; el efecto lo demuestra B1, al agregar la 1.1.
- Comentario de `jurisdiction/package-info.java`: el validador de la configuración es CJ01-CJ16 (Doc. 03 v0.5; antes decía CJ01-CJ14). Sin cambio de comportamiento.
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.

