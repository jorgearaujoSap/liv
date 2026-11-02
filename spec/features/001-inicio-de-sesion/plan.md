# 001 · Inicio de sesión — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** aprobado (H2) — 2026-11-01, arquitecto y responsable funcional (versión 4, con los ajustes del *spike* de Keycloak: ver `backend/identity/spike/RESULTADOS.md`)

_(borrador → aprobado (H2))_

**Ajustes posteriores:** T07, aprobados por el responsable el 2026-11-02 (ver «Decisiones»).

## Enfoque

Se implementa el patrón del ADR 0010:

- **Keycloak** autentica.
- Un **BFF** hace OIDC *Authorization Code* + PKCE y guarda la sesión en el servidor.
- La **API** (`liv-api`) valida el *token* y, **en cada petición**, resuelve en LIV quién es el usuario, cuál es su administración registrada y qué permisos tiene.
- El navegador solo tiene una cookie de sesión.

Principios:

1. **Independencia del proveedor (D-001-6, 001-20).**
   - El BFF y la API usan solo OIDC/OAuth 2.0 estándar con Spring Security.
   - El *realm* emite *claims* planos (`roles`, `administration_id`), con nombres configurables.
   - Lo propio de Keycloak (*realm*, tema, lectura de sus eventos) vive en `backend/identity/` y en adaptadores de infraestructura detrás de puertos.
   - Ninguna respuesta al navegador contiene un JWT.
2. **El backend es la única autoridad.** Los permisos (`<modulo>.<recurso>.<accion>`) se resuelven en LIV y cada caso de uso declara el que exige.
3. **Sin ciclos entre módulos** (doc. 01 §3.2).
   - `platform` define puertos.
   - `core` es dueño de los usuarios y del ciclo de sesión, y publica sus eventos.
   - `audit` escucha.
   - Cada flecha nueva se registra en el ADR 0025.
4. **La feature es la guía del equipo:** TDD y un PR por proyecto.

Entrega partes de la Fase 0: F0‑05, F0‑06, F0‑08 y F0‑09.

**Requisitos previos:**
- F0‑07 (CI por proyecto, con publicación de imágenes y contratos) antes de integrar los PR del backend;
- F0‑12 para el e2e.

## Implementación

Entre corchetes, el proyecto y su PR (IN‑07).

1. **[raíz] Documentación.**
   - Esta carpeta y los ADR 0023, 0024 y 0025 (el 0024 modifica el 0018 con `liv-adr`).
   - Doc. 00 §5: se actualiza la herramienta de generación del frontend en el mismo cambio que el ADR 0023.
   - Cambios en `AGENTS.md`, con aprobación:
     - rutas críticas `backend/bff/**` y `backend/identity/**`;
     - conteos de estilos (28 de color, 13 de texto), también en `tech-stack.md`.
   - Doc. 02 §5.2 (roles de BD nuevos) y `ESTADO.md`.
2. **[ui] `liv-components` 0.2.0.**
   - Los 18 componentes de `02 Componentes`, leídos nodo por nodo con el MCP de Figma.
   - *Tokens* transcritos de nuevo desde Figma (4 estilos nuevos y la colección `Layout`).
   - Lucide como SVG, con `THIRD_PARTY_NOTICES.md`.
   - Por componente: pruebas, historia de Storybook, axe y capturas de referencia por variante (Q14).
   - Tres PR de máximo ~400 líneas. Un humano publica la versión.
3. **[backend · identity] `backend/identity/` (ADR 0024).**
   - **`config/base/liv-realm.yaml`:** configuración de todos los entornos, aplicada con `keycloak-config-cli`. Las URL y los secretos van por variables de sustitución, distintas en cada entorno. Contiene:
     - **roles** de doc. 02 §4.2;
     - **cliente `liv-bff`:** confidencial con PKCE y audiencia `liv-api`; cierre por canal trasero hacia el BFF; sin `offline_access`;
     - **cliente `liv-bff-service`:** distinto del anterior, **solo *client credentials***, sin flujo con código. Un *mapper* asignado solo a su cuenta de servicio agrega la audiencia exclusiva `liv-internal`, que ningún *token* de usuario puede traer;
     - **cliente `liv-audit-importer`:** solo *client credentials*, con los roles mínimos para leer eventos y usuarios;
     - **mappers** de *claims* planos;
     - **atributo `administration_id`**, que solo puede ver y editar un administrador;
     - **bloqueo por intentos** (D-001-1), que también cuenta los fallos de TOTP;
     - **política de contraseñas** (D-001-7);
     - **tiempos** de la tabla "Sesión y tiempos", como variables de sustitución con los valores de producción por defecto; el e2e solo los acorta en su capa;
     - **HTTPS siempre** (`sslRequired: all`) y **bloqueo** como valores fijos, que ningún entorno puede cambiar con una variable; solo la capa de desarrollo relaja HTTPS a `external` para usar `http://localhost`;
     - **flujo de contraseña directa** que siempre niega el acceso, y `admin-cli` del *realm* desactivado: nadie obtiene un *token* solo con usuario y contraseña;
     - **TOTP obligatorio** por rol, con un flujo condicional;
     - **eventos de usuario y de administración** activados, con la representación de los recursos (hace falta para saber qué rol o atributo cambió) y caducidad de 30 días. Se guardan en la base de Keycloak, sin el *listener* `jboss-logging`, que escribiría el usuario intentado y la IP sin enmascarar. Solo se copian a LIV los campos de una lista blanca (ver paso 9);
     - **consola de cuenta** desactivada e **idioma** `es`.
   - **Capa de desarrollo** en `backend/dev/keycloak/`, fuera de `identity/config/` para que ninguna imagen la copie: usuarios ficticios (uno por rol en Ipiales, uno en la administración B y uno desactivado), **sin TOTP**. Los usuarios con rol de MFA lo configuran en su primer ingreso, porque keycloak-config-cli duplica las credenciales TOTP al reaplicar; los usuarios con TOTP conocido para el e2e van en `infra/keycloak/` (T23). `RealmConfigTest`, que corre en CI, falla si `identity/config/` contiene algo más que la base.
   - **`theme/liv/`:** FreeMarker con el CSS de los *tokens* del paquete `liv-components` (excepción aprobada, ADR 0024). Prueba visual contra los *frames*. En `messages_es`, la cuenta desactivada usa el mensaje genérico.
   - **Imagen de Keycloak con el tema:** se publica en CI, junto con la imagen `liv-identity-config` (`keycloak-config-cli` + `config/base`), que también usa `infra/`.
   - **Primera tarea: un *spike*** (T06, hecho; `backend/identity/spike/RESULTADOS.md`). Resultados con Keycloak 26.8.1:
     1. el tema **no** distingue la cuenta bloqueada: se usa un **mensaje único** para todo fallo, que avisa del bloqueo (enmienda 2 de la spec);
     2. el tema **no** recibe parámetros propios: no hay mensaje de motivo; tras salir, expirar o ser rechazado, se muestra la página de inicio de sesión (o la de error genérico);
     3. la revocación del *refresh token* (RFC 7009) **sí** cierra la sesión del usuario;
     4. no hay evento de expiración; los fallos traen su causa y los eventos de administración, la representación del recurso.
4. **[backend · dev] `backend/dev/`.**
   - PostgreSQL 18, con una base para LIV y otra para Keycloak.
   - Script de inicio que crea los roles de BD (`liv_owner`, `liv_app`, `liv_bff_owner`, `liv_bff`); en AWS los crea la infraestructura como código.
   - Keycloak con la base y la capa de desarrollo.
   - `.env.example`.
   - Semilla en `backend/dev/seed/`, que no se empaqueta.
5. **[backend] Migraciones** (ver "Datos, API y seguridad").
   - Flyway con `liv_owner` solo en `dev` y en pruebas.
   - En *staging* y producción, la imagen de migraciones `liv-migrations`, como paso del despliegue.
   - La aplicación usa `liv_app`.
6. **[backend] Contratos** (uno por módulo dueño, publicados por CI como artefactos versionados):
   - **`core.yaml`:** `GET /api/v1/me`. Lo implementa `core`, que es dueño de los datos del usuario.
   - **`platform.yaml`:** `GET /api/v1/me/capabilities`.
   - **`core-internal.yaml`:** `POST /internal/v1/sessions` y `POST /internal/v1/sessions/{sid}/end`. El prefijo `/internal/v1` se aparta de doc. 01 §10 (`/api/v1`) y se declara en el ADR 0025: marca que la ruta no se publica hacia el navegador.
   - **`bff/contracts/openapi/bff.yaml`:**
     - `/bff/login`, `/bff/logout`, `/bff/session`, `/bff/session/keepalive`;
     - `/bff/backchannel-logout`: solo lo llama Keycloak, sin CSRF y enrutado solo desde su red.
7. **[backend] `platform` (técnico).**
   - **Validación del *token*:**
     - servidor de recursos configurado solo por propiedades (`issuer-uri`, `audiences`);
     - `ClaimsAdapter`, con nombres de *claims* configurables.
   - **Puerto `UserDirectory`**, en el paquete raíz de `platform`, como el puerto `IdentityProviderEvents` (doc. 04 §3.1); lo implementa `core`: `resolve(issuer, subject)` devuelve id, administración registrada y estado.
   - **Filtro `CurrentUserFilter`.** En cada petición a `/api/**`:
     1. resuelve al usuario con el puerto;
     2. responde **401** si no existe (no hay sesión iniciada), si está inactivo (`001-05`) o si el `administration_id` del *token* difiere del registrado (`001-17`);
     3. guarda la administración **registrada** (no la del *claim*) en un `AdministrationContext` de la petición. El filtro **no toca la base de datos**.
   - **Contexto RLS por transacción** (doc. 02 §5.1). El gestor de transacciones de `platform` (`JpaTransactionManager` extendido) ejecuta `set_config('app.administration_id', ?, true)` al iniciar **cada** transacción, también las `REQUIRES_NEW`, con el valor del `AdministrationContext`. Como el alcance es local, al terminar la transacción no queda nada en la conexión del *pool*.
     - Los oyentes asíncronos y el importador fijan el `AdministrationContext` con la administración del evento antes de abrir su transacción.
     - Sin contexto no se fija nada, así que las políticas no devuelven filas.
     - **Prueba con un *pool* de una sola conexión:** la petición de A y luego la de B, comprobando que B no ve el contexto de A. Otra prueba cubre `REQUIRES_NEW` y los oyentes.
   - **Autorización.** `@RequiresPermission` va en los **métodos públicos de los servicios de `application`**, y se aplica con `@EnableMethodSecurity` y un `AuthorizationManager` propio (sin AspectJ).
     - ArchUnit exige la anotación en esa capa.
     - Excepciones explícitas: `GetCurrentUser`, `GetCapabilities` y los casos internos de sesión, que se protegen por alcance.
   - **Puerto `IdentityProviderEvents`.** El adaptador en `platform/infrastructure/identity` es lo único que conoce la API de eventos de Keycloak.
   - **Correlación:** `X-Correlation-Id`, aceptado solo como UUID.
   - **Errores:** Problem Details con el código del criterio.
   - **ArchUnit**, en `liv-api` y en `bff`, sobre el código principal: ningún import de `org.keycloak..`.
8. **[backend] `core` (CRUD simplificado; dueño de usuarios y sesiones).**
   - **`UserDirectoryAdapter`:** usa `core.find_user_by_identity`, una función endurecida (ver migraciones).
   - **`POST /internal/v1/sessions` (`StartSession`).**
     - **Autenticación doble:**
       - `Authorization`: *token* de *client credentials* del cliente `liv-bff-service`. Se exige la audiencia `liv-internal` y que `azp` sea `liv-bff-service`. Un *token* de usuario nunca cumple las dos condiciones, aunque el usuario agregue alcances en la URL de autorización. La prueba usa un *token* de usuario que pidió todos los alcances posibles y comprueba que recibe 401.
       - `X-User-Token`: *token* de acceso del usuario, validado con el mismo decodificador.
     - **De dónde salen los datos:** emisor, `sub`, `administration_id`, nombre y correo salen del *token* del usuario.
     - **Validaciones:**
       - la administración existe y está activa (`001-17`);
       - alta con `INSERT … ON CONFLICT DO NOTHING` y relectura **con la función**;
       - el usuario está activo (`001-05`) y su administración coincide con la registrada (`001-17`).
     - **Si pasa:** actualiza `last_login_at`, crea el registro `core.user_session` (`sid`, usuario, emisor, `sub`, inicio) y publica `SessionStarted` (y `UserProvisioned` si es nuevo), en la misma transacción.
     - **Si se rechaza:** publica `SessionDenied` (motivo y código) en una transacción aparte (`REQUIRES_NEW`), para que el rechazo quede auditado, y responde 403.
   - **`POST /internal/v1/sessions/{sid}/end` (`EndSession`).** Solo con el *token* de servicio. El cuerpo trae emisor, `sub` y motivo (`LOGOUT`, `IDLE`, `ABSOLUTE`, `PROVIDER`).
     - Verifica que el `sid` exista en `core.user_session` y pertenezca a ese emisor y ese `sub`.
     - **Es idempotente:** si la sesión ya terminó, no hace nada. Así, un cierre del usuario seguido del cierre por canal trasero produce un solo `SessionEnded`.
     - Guarda el fin y el motivo, y publica `SessionEnded`.
   - **Eventos `SessionStarted`, `SessionDenied`, `SessionEnded` y `UserProvisioned`:** son *records* del paquete raíz de `core`. Usan el *outbox* de Spring Modulith (JDBC).
   - **`GET /me`** es solo lectura.
9. **[backend] `audit` (técnico; ADR 0025: `audit → core` y `audit → platform`).**
   - **Escucha los eventos de `core`.** El consumidor es idempotente: UNIQUE `(source, source_event_id)`, con el id del evento como `source_event_id`.
   - **Rechazos sin administración válida** (no existe o falta el *claim*): el oyente no los puede guardar con el contexto de una administración. Los escribe con la función `audit.record_platform_session_denied(...)`, que:
     - es `SECURITY DEFINER` y pertenece a un rol propio sin inicio de sesión;
     - **solo** inserta filas con `administration_id` NULL, `source = 'LIV'` y `operation = 'SESSION_DENIED'`.

     `app.platform_job` sigue reservado al importador. Esta excepción se declara en el ADR 0025 y en doc. 02 §5.1, con aprobación en H2. **Prueba:** un ingreso con una administración inexistente deja su fila en la auditoría.
   - **Lote `IdentityProviderEventsImporter`.** Es un trabajo de plataforma marcado (doc. 02 §5.1).
     - **Concurrencia:** usa ShedLock.
     - **Qué importa:**
       - **sí:** intentos fallidos, bloqueos y errores de canje de código; eventos de administración (cambios de rol, de atributo y de estado del usuario), con una lista blanca de campos;
       - **no:** `LOGIN` exitoso ni `LOGOUT`, porque los registra LIV y así no hay duplicados.
     - **Datos que guarda:**
       - usuario intentado como `HMAC‑SHA256` con una clave secreta, más su longitud;
       - IP truncada (/24 en IPv4, /48 en IPv6);
       - en eventos de administración, el autor como referencia opaca.
     - **Contexto:** fija el contexto por evento, con la administración del usuario si se conoce; si no, es una fila de plataforma.
     - **Marca de agua:** `audit.import_cursor`, con el instante y los ids vistos, y una superposición de 5 minutos.
     - **Alertas:** se alerta si el importador se atrasa más de 1 día. La caducidad de 30 días en Keycloak deja margen.
   - **Credencial:** el importador usa su propio cliente `liv-audit-importer`, con `spring-boot-starter-oauth2-client` en `liv-api`.
10. **[backend · bff] BFF en `backend/bff/`** (Spring Cloud Gateway Server MVC, OAuth2 Client, Spring Session JDBC, ShedLock).
    - **Base de datos:**
      - esquema `bff`, con el historial de Flyway en `bff.flyway_schema_history`;
      - migraciones con `liv_bff_owner` (imagen `liv-bff-migrations` en el despliegue);
      - la aplicación usa `liv_bff`.
    - ***Tokens* en sesión:** cifrados con AES‑GCM y clave de Secrets Manager.
    - **Un único `SessionTerminator`.** Todo final de sesión pasa por él, en este orden: `EndSession(motivo)`, que es idempotente; revocación del *refresh token* (RFC 7009); borrado. Los finales son:
      - cierre por el usuario y cierre por canal trasero;
      - expiración por inactividad y límite absoluto;
      - fallo de renovación;
      - barrido.

      El `SessionRepository` de Spring Session se **decora**: si `findById` encuentra una sesión vencida, la entrega al terminador antes de borrarla, porque el repositorio JDBC la borra al leerla. Hay una prueba de integración para cada caso: el usuario vuelve a los 16 minutos, o después de 8 h, y la sesión queda con `SessionEnded` auditado y el *token* revocado.
    - **Fallo de renovación** (`invalid_grant`: usuario desactivado o sesión cerrada en Keycloak): `SessionTerminator(PROVIDER)` y 401. Nunca se reenvía una petición sin *token*.
    - **Registro de sesiones OIDC:** persistente en `bff.oidc_session` (`sid` → id de sesión), así que el cierre por canal trasero funciona con varias réplicas.
    - **`/bff/login`:**
      - `returnTo` se valida y se guarda en la sesión;
      - `prompt=login`;
      - al volver, cambia el id de sesión y llama a `StartSession` con los dos *tokens*;
      - si `StartSession` falla, revoca el *refresh token* (RFC 7009), invalida la sesión y redirige a `/bff/login`, donde el usuario ve la página de inicio de sesión. El rechazo queda auditado (`SessionDenied`); la página no muestra un motivo, porque el tema no recibe parámetros propios (*spike* T06).
    - **`/bff/logout` (CSRF):**
      1. llama a `EndSession(LOGOUT)`;
      2. **revoca el *refresh token* desde el servidor** (RFC 7009), lo que cierra la sesión de Keycloak;
      3. invalida la sesión;
      4. responde la ruta de LIV `/bff/login`, sin parámetro de motivo.

      El navegador nunca recibe un `id_token_hint` ni un JWT.
    - **`/bff/session`:** está fuera de la cadena que carga la sesión. Lee el repositorio por el id de la cookie y devuelve los segundos que faltan para la expiración por inactividad y para la absoluta. **No extiende la sesión**, y hay una prueba de integración que lo verifica.
    - **`/bff/session/keepalive` (CSRF):** **siempre** renueva el *token* (así reinicia también la inactividad de Keycloak) y extiende la sesión. No pasa del límite absoluto.
    - **Límite absoluto:** se guarda la hora de creación en la sesión, y un filtro la entrega al `SessionTerminator(ABSOLUTE)` a las 8 h.
    - **Barrido programado (ShedLock):** sustituye la limpieza por defecto de Spring Session y entrega al terminador las sesiones vencidas que nadie volvió a leer.
    - **`/bff/backchannel-logout`:** `SessionTerminator(PROVIDER)` sobre la sesión indicada.
    - **Enrutamiento:** `/api/**` va a la API con *TokenRelay*; `/internal/**` nunca se enruta.
    - **Seguridad:**
      - cookie `LIV_SESSION` (`HttpOnly`, `Secure`, `SameSite=Strict`);
      - CSRF (`XSRF-TOKEN`);
      - cabeceras y `Cache-Control: no-store`;
      - **limitación de tasa por usuario (`sub`) y por IP** en `/bff/**` y `/api/**`, como pide doc. 02 §8. Bucket4j en memoria por réplica: el límite configurado se divide entre el número de réplicas. `X-Forwarded-For` solo se acepta del balanceador. Además, WAF por IP delante de Keycloak.
    - **Prueba:** ninguna respuesta del BFF contiene un JWT.
11. **[frontend] Contratos y cliente.**
    - Copias fijadas de `platform.yaml` y `bff.yaml` en `api-spec/`, con `api-spec/versions.json`.
    - `npm run api:update` descarga los artefactos publicados.
    - Cliente generado con `ng-openapi-gen`.
12. **[frontend] Núcleo.**
    - `SessionService` y `authGuard`.
    - **`capabilityGuard`:**
      - una opción visible y disponible navega normalmente;
      - una visible pero `COMING_SOON` lleva a "Próximamente";
      - si no está en las capacidades, lleva a 403.
    - **Interceptor:**
      - agrega `X-Correlation-Id`;
      - ante un 401, consulta `/bff/session` y reintenta **una vez y solo en métodos seguros** (o con `Idempotency-Key`); si no, va al inicio de sesión;
      - ante un 403, recarga las capacidades y muestra 403.
    - **`IdleService`:**
      - usa los segundos que informa el BFF;
      - sincroniza pestañas con `BroadcastChannel`;
      - vuelve a consultar antes de avisar o de cerrar;
      - avisa 2 minutos antes, y avisa del límite absoluto;
      - **no hace peticiones de fondo** a `/api`.
    - **Cierre de sesión:** navegación completa a la ruta que responde `/bff/logout`.
    - **nginx:** CSP, HSTS, `frame-ancestors 'none'` y `Cache-Control: no-store` en `index.html`.
13. **[frontend] Pantallas:** estructura general, Inicio, aviso de sesión, Sin permiso (403), **Próximamente**, cargando y error, según los diseños.
    - En el menú, las opciones `COMING_SOON` aparecen **deshabilitadas**, como pide 001-15: no navegan.
    - La ruta de cada una existe solo para entrar por URL, que es como se prueba 001-16.
14. **[raíz · infra y e2e] Pruebas de sistema (F0‑12).**
    - **`infra/compose.yaml`:** solo imágenes publicadas (IN‑09):
      - `liv-api`, `liv-bff`, `liv-migrations`, `liv-bff-migrations` y el frontend;
      - Keycloak con tema y `liv-identity-config` (configuración base real).
    - **`infra/keycloak/e2e-users.yaml`:** solo agrega usuarios y secretos TOTP de prueba sobre la base.
    - **`infra/e2e/seed.sql`:** administraciones ficticias de e2e.
    - **Proxy TLS (Caddy):** con dos hosts del mismo sitio, `liv.test` y `auth.liv.test`, para que las cookies y `SameSite` se prueben como en producción. La API y el BFF resuelven `auth.liv.test` hacia Caddy y confían en su autoridad certificadora mediante un almacén de confianza montado solo en el *compose* de e2e (*SSL bundle* de Spring por variables de entorno).
    - **`e2e/`:** los flujos de 001-01 a 001-22, incluidos:
      - expiración y aviso con tiempos acortados (el aviso, "Seguir conectado" y la sesión continúa);
      - volver a entrar tras la expiración;
      - usuario desactivado y cambio de rol o de administración con la sesión abierta;
      - URL de cierre sin *tokens*;
      - axe‑core.

## Sesión y tiempos (001-09, 001-10, 001-22)

| Parámetro | Valor | Dónde |
|---|---|---|
| Inactividad (autoridad) | 15 min | BFF |
| Inactividad del proveedor | 25 min (≥ inactividad del BFF + vida del *token* + 5 min de margen) | Keycloak (*SSO Session Idle*). Con `prompt=login` nunca reabre una sesión vencida |
| Máximo absoluto | 8 h en el BFF (filtro). 8 h 10 min en Keycloak (*SSO Session Max*): empieza unos segundos antes, así que el margen evita que la renovación falle al final | BFF y Keycloak, sin "Recordarme" ni `offline_access` |
| *Token* de acceso | 5 min. El BFF lo renueva si le queda menos de 1 min, y siempre en `keepalive` | Keycloak, BFF |
| Rotación del *refresh token* | Desactivada (`revokeRefreshToken: false`). Dos renovaciones simultáneas (varias peticiones o réplicas del BFF) harían fallar la segunda y cerrarían la sesión sin motivo; el *refresh token* nunca sale del BFF | Keycloak |
| Revocación | Todo final de sesión pasa por el `SessionTerminator`, que revoca el *refresh token*. Los cambios hechos en Keycloak (desactivar al usuario, cambiar su rol o su administración) aplican **al renovar el *token*** (≤ 5 min): el usuario desactivado hace fallar la renovación, y el rol o la administración nuevos llegan en el *token* renovado, donde `CurrentUserFilter` los compara con el registro de LIV y responde 401. Los cambios en el registro de LIV aplican en la siguiente petición | BFF, API |
| Aviso | 2 min antes de la expiración por inactividad; aviso aparte cerca del máximo | Frontend |

Cada proyecto prueba sus propios valores contra esta tabla:
- el BFF, en sus pruebas de configuración;
- `backend/identity/`, en las del *realm*, incluida la desigualdad de la inactividad.

El e2e prueba la combinación.

## Datos, API y seguridad

- **Migraciones del backend.** Todas son nuevas. Las políticas usan `NULLIF(current_setting('app.administration_id', true), '')::uuid`.
  - **Nombres:** siguen `V<AAAAMMDDHHmm>__<modulo>_<descripcion>` (doc. 01 §9).
  - **`V202611010900__platform_schemas_outbox.sql`:**
    - esquemas;
    - permisos de `liv_app`: sin `BYPASSRLS`, sin `TEMP` y sin acceso al esquema `bff`;
    - tabla `event_publication` del *outbox* de Spring Modulith.
  - **`V202611010905__core_administrations_users.sql`:**
    - **Tablas:**
      - `territorial_entity`, `tax_administration` y `admin_user`, con FK compuestas y UNIQUE `(identity_issuer, identity_subject)`;
      - `core.user_session` (`sid` PK, `user_id`, `identity_issuer`, `identity_subject`, `administration_id`, `started_at`, `ended_at`, `end_reason`), con RLS.
    - **RLS:** `ENABLE`, no `FORCE`.
    - **Función `core.find_user_by_identity`:**
      - `SECURITY DEFINER`, con `SET search_path = pg_catalog, core, pg_temp` (`pg_temp` al final, como pide la documentación de PostgreSQL);
      - pertenece a un **rol propio sin inicio de sesión**, `liv_identity_lookup`, que solo tiene `SELECT` sobre esas columnas. No pertenece a `liv_owner`, porque doc. 02 §5.2 dice que la aplicación nunca usa ese rol. **Se aprueba en H2**: agrega un rol a doc. 02 §5.2;
      - `REVOKE ALL … FROM PUBLIC` y `GRANT EXECUTE` solo a `liv_app`;
      - devuelve solo id, administración y estado.
  - **`V202611010910__platform_roles_permissions.sql`:** `platform.app_role`, `app_permission` y `role_permission`.
  - **`V202611010915__audit_events.sql`:** incluye la función `audit.record_platform_session_denied` (paso 9), con su propio rol sin inicio de sesión.
    - **`audit.audit_event`:** solo inserción para `liv_app`. Agrega `source`, `source_event_id` (UNIQUE con `source`) y `actor_ref` (referencia opaca cuando no hay usuario de LIV).
    - **`audit.import_cursor`.**
    - **Políticas:**
      - lectura solo de la administración del contexto. Las filas de plataforma (`administration_id` NULL) **no las lee `liv_app`** hasta el menú 9 (Auditoría e integraciones, Doc. 01 §3), que traerá su rol con un ADR;
      - inserción con `WITH CHECK`: la administración del contexto, o NULL solo si la transacción fijó `app.platform_job = 'on'`, que fijan únicamente los trabajos de plataforma (prueba que lo verifica).
  - **`V202611010920__platform_shedlock.sql`.**
- **Migraciones del BFF:** `bff/src/main/resources/db/migration/`, con las tablas de Spring Session, `bff.oidc_session` y ShedLock.
- **Ajustes al modelo de datos.** Requieren el visto bueno del responsable funcional en H2 y se registran en el ERD en un cambio aparte:
  - `core.admin_user`: se agregan `identity_issuer`, `identity_subject` y `last_login_at`.
  - `core.user_session`, tabla nueva: registro de las sesiones, para que `EndSession` sea verificable e idempotente.
  - Roles de BD sin inicio de sesión `liv_identity_lookup` y el dueño de `audit.record_platform_session_denied`. Se agregan a doc. 02 §5.2.
  - Esquema `platform` (doc. 01 §3.1) con `app_role`, `app_permission` y `role_permission`.
  - `audit.audit_event`: se agregan `administration_id`, `source`, `source_event_id` y `actor_ref`. Cada evento se guarda con `entity_name = 'session' | 'identity_provider'`, `operation` = tipo, y `new_values` = `{result, reason, truncated_ip, masked_attempted_username}`.
  - `audit.import_cursor`, la tabla `event_publication` y las tablas de ShedLock.
  - Esquema `bff` y roles de BD `liv_bff_owner` y `liv_bff`. Se agregan a doc. 02 §5.2 con aprobación.
  - **Pendiente del ERD.** Su "Checklist previo a Flyway" está abierto. Esta feature no migra tablas tributarias. **Se pide autorizar en H2 que estas migraciones avancen antes de cerrar esa lista.**
- **Contratos:**

  | Operación | Respuesta | Errores |
  |---|---|---|
  | `GET /api/v1/me` (`core.yaml`) | `id`, `fullName`, `email`, `administration {id, name}`, `roles[]`, `permissions[]` | 401 (`001-14`, `001-05`, `001-17`) |
  | `GET /api/v1/me/capabilities` | Árbol de `code`, `children[]`, `status` (`AVAILABLE` \| `COMING_SOON`) | 401 |
  | `POST /internal/v1/sessions` (*token* de servicio + `X-User-Token`) | 201 con `userId` y `sessionAuditId`. Cuerpo: `sid` | 401, 403 (`001-17`, `001-05`) |
  | `POST /internal/v1/sessions/{sid}/end` (*token* de servicio) | 204. Cuerpo: `issuer`, `subject`, `reason` | 401, 403 |
  | `bff.yaml` | Ver el paso 10 | 401, 403 (CSRF), 429 |

- **Permisos.** La matriz inicial (D-001-5) se somete completa al responsable funcional en H2. Una vez aprobada, se lleva a Doc. 01 §4 (o a un anexo funcional) en un cambio aparte, como exige doc. 02 §4.4.

  | Opción | Permiso | Roles | Origen |
  |---|---|---|---|
  | 1.1 Entidades y administraciones | `core.administration.read` | ADMIN_SISTEMA, AUDITOR | Propuesta |
  | 1.2 Usuarios, roles y permisos | `core.user.read` | ADMIN_SISTEMA, AUDITOR | Propuesta |
  | 1.3 Normas y soportes | `documents.legal-instrument.read` | ADMIN_SISTEMA, CARGADOR_TABLAS, APROBADOR_TABLAS, CONSULTA, AUDITOR | Doc. 01 §4 |
  | 1.4 Vigencias fiscales | `tax.fiscal-year.read` | ADMIN_SISTEMA, CARGADOR_TABLAS, APROBADOR_TABLAS, CONSULTA, AUDITOR | Doc. 01 §4 |
  | 1.5 Cargue de tablas base (1.5.1–1.5.8) | `valuation.batch.read` · `tax.rate.read` | ADMIN_SISTEMA, CARGADOR_TABLAS, APROBADOR_TABLAS, CONSULTA, AUDITOR | Doc. 01 §4 |
  | 1.5.9 Configuración de jurisdicción | `jurisdiction.config.read` | ADMIN_SISTEMA, CARGADOR_TABLAS, APROBADOR_TABLAS, JURIDICO, AUDITOR | Doc. 01 §4 + propuesta (JURIDICO por Doc. 03 §5.3) |
  | 1.6 Consultas y exportación | `valuation.catalog.read` | ADMIN_SISTEMA, CARGADOR_TABLAS, APROBADOR_TABLAS, CONSULTA, AUDITOR | Doc. 01 §4 |
  | 1.7 Referencias vehiculares | `valuation.vehicle-reference.read` | ADMIN_SISTEMA, CARGADOR_TABLAS, APROBADOR_TABLAS, CONSULTA, AUDITOR | Doc. 01 §4 |
  | 1.8 Parámetros generales | `core.parameter.read` | ADMIN_SISTEMA, AUDITOR | Propuesta |
  | 2. Registro automotor y contribuyentes | `core.vehicle.read` | LIQUIDADOR, APROBADOR_LIQUIDACION, RECAUDO, CONSULTA, AUDITOR | Propuesta |
  | 3. Liquidación | `tax.assessment.read` | LIQUIDADOR, APROBADOR_LIQUIDACION, CONSULTA, AUDITOR | Propuesta |
  | 4. Cuenta corriente y recaudo | `ledger.obligation.read` | RECAUDO, TESORERIA, APROBADOR_RECAUDO, CONSULTA, AUDITOR | Doc. 04 §10 + propuesta (AUDITOR por doc. 02 §4.2) |
  | 5. Cartera | `portfolio.balance.read` | RECAUDO, TESORERIA, APROBADOR_RECAUDO, JURIDICO, CONSULTA, AUDITOR | Provisional |
  | 6. Cobro · 7. Medidas cautelares | `collection.case.read` · `enforcement.measure.read` | JURIDICO, AUDITOR | Provisional |
  | 8. Documentos y notificaciones | `documents.document.read` | Todos los roles | Provisional |
  | 9. Auditoría e integraciones | `audit.event.read` | AUDITOR, ADMIN_SISTEMA | Doc. 02 §9 (AUDITOR) + propuesta (ADMIN_SISTEMA) |

  - Un módulo se muestra si el usuario puede ver al menos una de sus opciones. "Inicio" no exige permiso.
  - La prueba de la matriz compara contra una tabla esperada escrita aparte.
  - 001-16 se prueba en tres niveles:
    - en el backend, con un caso de uso y un controlador que existen solo en las pruebas (200 con el permiso, 403 sin él);
    - en el frontend, por URL;
    - en el e2e.
- **Pruebas de 001-14 y 001-20.**
  - La API y el BFF se prueban con su **configuración de producción, sin reemplazar *beans***, contra un proveedor OIDC genérico en contenedor (`mock-oauth2-server`, que sirve el *discovery* y el JWKS).
  - **Casos de 001-14:** sin *token*, vencido, otra audiencia, otro emisor, firma inválida y `alg: none`. Todos usan el `sub` de un usuario registrado y activo, y verifican que el código del error sea `001-14`. Así, la prueba no pasa por el 401 que da el filtro cuando el usuario no está registrado.
  - **001-20:** el BFF completa el flujo OIDC (código, PKCE, renovación, revocación) contra ese mismo proveedor genérico.
  - Además, una prueba contra Keycloak (Testcontainers) verifica la integración real.
- **Amenazas** (doc. 02 §2 y §8):
  - **XSS:** los *tokens* nunca llegan al navegador, ni siquiera en el cierre de sesión, y se cifran en reposo; CSP.
  - **CSRF:** *token* anti‑CSRF y `SameSite=Strict`.
  - **Redirección abierta:** `returnTo` validado.
  - **Fijación de sesión:** el identificador cambia al autenticar.
  - **Fuerza bruta:** bloqueo en Keycloak (que también cuenta TOTP), WAF por IP delante de Keycloak y limitación por sesión e IP en el BFF.
  - **Enumeración:** mensaje genérico, salvo la excepción de 001-04 (enmienda 1).
  - **Suplantación de administración:**
    - la administración se valida en **cada petición** contra el registro de LIV;
    - el atributo solo lo edita un administrador;
    - RLS con la administración registrada.
  - **Eventos falsos:**
    - el endpoint interno exige el *token* de servicio con su alcance y el *token* firmado del usuario, y no se enruta;
    - un *token* de usuario no sirve como *token* de servicio (prueba).
  - **Repudio:** auditoría inmutable, con los ingresos rechazados, las expiraciones y los eventos del proveedor.
  - **Topología:** Keycloak y LIV comparten dominio registrable. La consola de Keycloak y `master` no se exponen y exigen segundo factor con cuentas nominales.
  - **Inyección en *logs*:** correlación solo como UUID; datos personales enmascarados.
  - **Secretos:** los de prueba solo en `backend/dev/keycloak/` e `infra/keycloak/`; `RealmConfigTest` verifica en CI que no estén en `identity/config/`. El cierre por canal trasero usa la URL interna del BFF (`LIV_BFF_BACKCHANNEL_URL`), no la pública.

## Diseño de interfaz

Diseños aprobados por el responsable funcional el 2026-11-01 (versión 2, con sus ajustes) en `03 Pantallas`. **Nuevo para H2:** la pantalla "Próximamente".

| Pantalla | Diseño en Figma | Componentes |
|---|---|---|
| Las 8 pantallas de acceso | [001 · Acceso](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=24-1163) | Tema de Keycloak (excepción del ADR 0024). Tras el *spike* quedan 8: "Cuenta bloqueada" se fusionó con "Credenciales incorrectas" (mensaje único) y "Sesión cerrada o expirada" se archivó en `99 Archivo` |
| Inicio | [Inicio](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-793) | SideNav, NavItem, Logo, AppHeader, Avatar, Button, PageHeader, ModuleCard, Badge, Icon |
| Aviso de sesión por expirar | [Aviso](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1157) | Dialog, Button, Icon |
| Sin permiso (403) | [Sin permiso](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1454) | ResultState, Button, Icon y la estructura general |
| Próximamente (**pendiente de aprobación en H2**) | [Próximamente](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=29-1689) | ResultState (Info), Button, Icon y la estructura general |
| Cargando sesión | [Cargando](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1640) | Skeleton, Logo |
| Error al cargar la sesión | [Error](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1672) | ResultState, Logo, Button, Icon |

## Decisiones

- **ADR 0023 — Dependencias de la feature 001.** Versiones exactas en cada tarea.
  - **`backend/`:**
    - *starters* de `oauth2-resource-server`, `oauth2-client` (para el importador) y `data-jpa`;
    - `spring-boot-starter-flyway`, `flyway-database-postgresql` y `postgresql`;
    - `spring-modulith-starter-jdbc` (el *outbox*);
    - ShedLock (`shedlock-spring`, `shedlock-provider-jdbc-template`);
    - `openapi-generator-maven-plugin`;
    - pruebas: `spring-boot-testcontainers`, `testcontainers` (`postgresql`, `junit-jupiter`), `testcontainers-keycloak`, la imagen `mock-oauth2-server` y `spring-security-test`.
  - **`backend/bff/`:** BOM de Spring Cloud, `spring-cloud-starter-gateway-server-webmvc`, `spring-boot-starter-oauth2-client`, `spring-session-jdbc`, `postgresql`, Flyway, ShedLock y `bucket4j-core`; en pruebas, las mismas imágenes.
  - **`backend/identity/`:** imagen de Keycloak, `keycloak-config-cli`, Tailwind CLI y `liv-components`.
  - **`frontend/`:** `ng-openapi-gen` (desviación de doc. 00 §5, que se actualiza), `@testing-library/angular` y la imagen de nginx.
  - **`ui/`:** Storybook y Playwright.
  - **`infra/` y `e2e/`:** Caddy, `@playwright/test`, `@axe-core/playwright` y `otplib`.
  - **Sin dependencia:**
    - AspectJ, porque se usa la seguridad de métodos;
    - iconos Lucide (SVG copiados).
- **ADR 0024 — Identidad como proyecto `backend/identity/` y excepción del tema.**
  - Configuración base del *realm* para todos los entornos, capa solo de desarrollo, tema e imágenes publicadas.
  - **Excepción aprobada (2026-11-01):** el tema reproduce componentes con los *tokens* de `liv-components`, con prueba visual. Modifica el ADR 0018 solo para el tema.
  - **Descartados:** Keycloakify, e `infra/` como dueño.
- **ADR 0025 — Dependencias entre módulos de la feature.**
  - `core → platform` implementa `UserDirectory`; ya estaba permitida.
  - **`audit → core`:** escucha los eventos de sesión y usuario. Es una flecha nueva, y declara también la FK `audit.audit_event.user_id → core.admin_user`.
  - **`audit → platform`:** usa el puerto `IdentityProviderEvents`. Es una flecha nueva.
  - **Prefijo `/internal/v1`** para los endpoints entre servicios, que no se publican hacia el navegador.
  - **Filas de plataforma:** solo dos caminos pueden escribirlas, el importador (con `app.platform_job`) y la función `audit.record_platform_session_denied`.
  - Nadie depende de `audit`.
  - El filtro por modalidades será un puerto de `platform` que implementará `jurisdiction`.
  - `ModularityTest` verifica todas las flechas.
- **Roles en Keycloak, permisos y administración en LIV.**
  - El registro de LIV manda sobre el *claim*. Un cambio de administración en Keycloak deja al usuario sin acceso (401) hasta que se resuelva con el menú 1.2.
  - Si se cambia de proveedor, el par (emisor, `sub`) se vuelve a vincular de forma explícita, sin dar de alta usuarios nuevos.
- **Cierre de sesión sin *tokens* en el navegador.** Revocación del *refresh token* (RFC 7009) desde el BFF.
  - El *spike* T06 confirmó que la revocación cierra la sesión del usuario en Keycloak, así que no hace falta la variante con la API de administración.
  - Se descartó navegar a `end_session` con `id_token_hint`.
- **Menú: el backend decide qué, el frontend cómo se ve.**
- **Plan B del BFF.** Si no hay una versión de Spring Cloud Gateway compatible, se usa Spring MVC con `RestClient`, registrado en un ADR que modifique el ADR 0010 y doc. 00 §5.
- **Ajustes de T07 (aprobados por el responsable el 2026-11-02).** Salieron de la revisión adversarial de T07 (PR #26):
  - **sin rotación del *refresh token***, que el plan no pedía y cerraba sesiones con renovaciones simultáneas;
  - **HTTPS siempre** (`sslRequired: all` en lugar de `external`), porque detrás de un balanceador mal configurado `external` dejaría pasar HTTP;
  - HTTPS y bloqueo como valores fijos; la capa de desarrollo fuera de `identity/config/` y sin TOTP; flujo de contraseña directa denegado; eventos sin `jboss-logging`; canal trasero por la URL interna.
- **Valores propuestos (D-001-7, H2):** contraseñas de 12 caracteres o más, sin reutilizar las últimas 5, sin vencimiento forzado y distintas del usuario.

## Riesgos

- **Resultados del *spike* de identidad.** Resuelto con T06: el responsable eligió el mensaje único y no mostrar el motivo, así que no se escribe ninguna extensión de Keycloak. La revocación funciona como prevé el plan.
- **Compatibilidad de Spring Cloud Gateway** con Spring Boot 4.1 (ver plan B).
- **Tamaño.** Unos 17 PR. `tasks.md` los ordena; librería, identidad y base de datos van en paralelo. F0‑07 va antes.
- **RLS.** Pruebas de aislamiento con el rol `liv_app` real:
  - sin contexto y con otra administración;
  - la función de identidad, llamada desde otro rol y en un conflicto con otra administración;
  - inserción de filas de plataforma sin `app.platform_job`.
- **Retraso del importador.** Si se atrasa más de 30 días se pierden eventos del proveedor. La alerta salta con 1 día de retraso.
- **Secretos de prueba y deriva visual.** Ver los pasos 3 y 2.
- **Semilla de *staging*.** La administración real se carga con un *script* controlado y auditado en el despliegue, hasta que exista el menú 1.1. Se decide en F0‑08.

## Revisión

- [x] Revisión adversarial independiente del plan, ronda 1 (2026-11-01). Dos revisores ciegos.
  - **Hallazgos:**
    - revisor A: 1 crítico, 7 altos, 15 medios y 11 bajos;
    - revisor B: 0 críticos, 10 altos, 13 medios y 7 bajos.
  - **Corregidos en la versión 2:** todos los confirmados. Los que tocaban criterios aprobados en H1 se llevaron al responsable funcional, que los decidió en la enmienda 1 de la spec.
  - **Descartados:** ninguno.
- [x] Revisión adversarial, ronda 2 (2026-11-01), sobre lo corregido.
  - **Hallazgos:** revisor A con 0 críticos, 7 altos, 13 medios y 7 bajos; revisor B con 0 críticos, 7 altos, 11 medios y 7 bajos.
  - **Escalada:** la ronda no pasó y se escaló al responsable. El responsable autorizó corregir y hacer una tercera ronda como excepción al máximo de dos.
  - **Altos corregidos en esta versión:**
    - doble *token* del endpoint interno;
    - dueño `core` de las sesiones y ADR 0025;
    - inactividad del proveedor mayor que la del BFF y `keepalive` que siempre renueva;
    - cierre por revocación, sin JWT en el navegador;
    - barrido de expiraciones;
    - e2e con la configuración base publicada y semilla propia;
    - `SessionDenied` en una transacción aparte;
    - BFF y API probados contra un proveedor OIDC genérico;
    - validación de la administración en cada petición.
  - **Medios corregidos:**
    - función endurecida;
    - registro de sesiones OIDC persistente;
    - *outbox* y ShedLock con sus tablas;
    - importador idempotente con lista blanca y enmascarado;
    - políticas de auditoría;
    - limitación de tasa en el BFF;
    - roles y migraciones propios del BFF;
    - `/bff/session` sin extender, con prueba;
    - lugar único de `@RequiresPermission`;
    - reintento solo en métodos seguros;
    - TLS de e2e con dos hosts;
    - pantalla "Próximamente" diseñada.
  - **Bajos corregidos:**
    - claves en inglés;
    - sin `offline_access`;
    - filtro del límite absoluto;
    - `actor_ref`;
    - sin `LOGIN` ni `LOGOUT` duplicados;
    - pruebas de tiempos por proyecto;
    - publicación de contratos.
- [x] Revisión adversarial, ronda 3 (2026-11-01), excepción autorizada por el responsable. Dos subagentes `liv-revisor-critico` (nivel alto) revisaron solo lo corregido.
  - **Hallazgos:** revisor A con 0 críticos, 4 altos, 6 medios y 2 bajos; revisor B con 0 críticos, 2 altos, 6 medios y 2 bajos.
  - **Resueltos según los dos revisores:** el ADR 0025 sin ciclos, las pruebas con un proveedor genérico y el e2e.
  - **Altos corregidos en la versión 4:**
    - expiraciones que Spring Session borra al leer: `SessionTerminator` único con repositorio decorado (los dos revisores);
    - contexto RLS por transacción con un gestor de transacciones propio y una prueba con *pool* de una conexión (los dos);
    - rechazos sin administración, auditados con una función de plataforma acotada (A alto, B medio);
    - separación entre los *tokens* de servicio y de usuario con un cliente y una audiencia exclusivos (A alto, B medio).
  - **Medios y bajos corregidos:**
    - `core.user_session` y `EndSession` idempotente;
    - tabla de revocación precisa;
    - fallo de renovación;
    - `pg_temp` al final del `search_path`;
    - rol propio de la función de identidad;
    - representación de los eventos con lista blanca;
    - tiempos como variables de sustitución;
    - 001-14 con un `sub` registrado;
    - `GET /me` en `core.yaml`;
    - prefijo `/internal`;
    - limitación de tasa por usuario;
    - confianza TLS del e2e;
    - nombres de migración;
    - puertos en el paquete raíz.
  - **Estado:** estos cambios no tuvieron una cuarta ronda. Pasan a H2 marcados como "corregidos sin verificar", y la revisión adversarial y las cuatro lentes de cada PR los vuelven a cubrir sobre el código.
- [ ] Revisión de las cuatro lentes (`liv-revision-4r`) antes de cada PR: es un cambio crítico.
- [x] Aprobación H2 del arquitecto y del responsable funcional: 2026-11-01. Incluye:
  - la matriz de permisos;
  - los ajustes al modelo (incluida `core.user_session`);
  - los roles de BD nuevos (`liv_identity_lookup`, el dueño de la función de rechazos, `liv_bff_owner`, `liv_bff`);
  - la excepción de las filas de plataforma y el prefijo `/internal/v1` (ADR 0025);
  - D-001-7;
  - la pantalla "Próximamente";
  - la autorización de las migraciones antes del *checklist* del ERD. En los PR se exigen dos aprobaciones humanas.
