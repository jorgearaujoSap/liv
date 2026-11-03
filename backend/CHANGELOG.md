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
