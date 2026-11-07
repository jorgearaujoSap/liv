# Changelog — liv-bff (BFF)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `bff-vX.Y.Z`.

## [Sin publicar]

### Cambiado

- `/bff/login` que no es una navegación (001-01, enmienda 4). Una precarga del navegador (`Sec-Purpose`), una imagen o una petición del frontend recibían un 404, y Chrome lo mostraba al abrir la página precargada. Ahora reciben una página que, cuando la persona la abre, vuelve a pedir `/bff/login` como navegación: se inicia la sesión y, si había otra abierta, se cierra antes, como al navegar. No inicia ni cierra una sesión por sí misma, y una precarga no gasta el límite del inicio de sesión. Contrato `bff` 1.4.0.
- `returnTo` con `%2F` o `%5C` en la ruta (001-01, decisión del responsable en T21): antes se cambiaba por el inicio; ahora se acepta si, decodificada, sigue siendo una ruta de LIV, y vuelve tal como se pidió.

### Agregado

- Límite de peticiones (feature 001, T19; doc. 02 §8): una persona con sesión cuenta por persona (300 por minuto); sin sesión, por dirección (600, y las IPv6 por su red /64); `/bff/login` además por dirección (20). Pasado el límite responde 429 con `Retry-After`: una página en `/bff/login`, que es una navegación, y Problem Details en el resto, también en `/api/**` (la petición no llega a la API) y en `/bff/session`. Salir y el canal trasero no tienen límite. Cada réplica deja pasar su parte (`liv.bff.rate-limits.replicas`, `LIV_BFF_REPLICAS`). Usa bucket4j (`bucket4j_jdk17-core` 8.21.0, el nombre actual de `bucket4j-core` del ADR 0023), en memoria y con un tope de direcciones que nunca olvida los límites de las personas.
- La dirección del cliente es la que reenvía el balanceador (`server.forward-headers-strategy: native`), aceptada solo de `LIV_BFF_TRUSTED_PROXIES`; por omisión, solo la máquina local.
- Cabeceras de seguridad en toda respuesta (T19; doc. 02 §8): `Strict-Transport-Security`, `Content-Security-Policy` con `default-src 'none'` y `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y `Referrer-Policy: no-referrer`.
- `bff.yaml` 1.3.0: la respuesta 429.
- Cierre por canal trasero (feature 001, T19; 001-09, 001-18): cuando el proveedor cierra una sesión (un administrador la cierra o desactiva a la persona), llama a `POST /bff/backchannel-logout` (OpenID Connect Back-Channel Logout 1.0) y la sesión del BFF se borra al instante. Después se avisa a LIV (`EndSession` con `PROVIDER`) y se revoca el *refresh token*; si LIV no acepta el fin, el barrido lo reintenta.
- El *logout token* se valida sin API propia de ningún proveedor: firma RS256 con las claves del `jwks_uri` (leídas con tiempos cortos, en caché y como máximo cada 30 s), `typ` `logout+jwt` o `JWT`, `iss`, `aud` del cliente, `iat` de los últimos 2 minutos, el evento del estándar, `jti`, `sid` y sin `nonce`. Responde 200, 400 con `invalid_request` o 503 para que el proveedor reintente. Atiende 8 a la vez. Está en `bff.yaml` (versión 1.2.0) y es solo para la red interna.
- `bff.oidc_session` (migración `V202611061100`) guarda qué sesión del BFF es de qué `sid`, para encontrarla desde cualquier réplica, y los avisos a LIV pendientes.
- Fin de las sesiones vencidas aunque la persona no vuelva (feature 001, T19; 001-10, 001-18):
  - un barrido cada `liv.bff.session.sweep-interval` (1 minuto) termina las sesiones vencidas por inactividad o por la duración máxima: `EndSession` con `IDLE` o `ABSOLUTE`, revocación del *refresh token* y borrado. Si LIV o el proveedor fallan, la sesión queda y se reintenta en el barrido siguiente; pasado `liv.bff.session.sweep-give-up-after` (1 día) se borra igual, con un error en el registro que lleva una huella del `sid` para conciliar. Solo un rechazo definitivo (400, 404, 409, 410 o 422) no se reintenta: queda como error; un 401, 403, 408 o 429 se reintenta. Los inicios de sesión que nadie terminó se borran sin avisar a LIV;
  - cada sesión se termina con su fila bloqueada (`FOR UPDATE SKIP LOCKED`), así que dos réplicas no la terminan a la vez; «Salir» y el límite absoluto de una petición no toman ese bloqueo, y si coinciden con el barrido LIV recibe el fin dos veces (`EndSession` es idempotente). Con varias réplicas, una sola barre a la vez (ShedLock 7.10.1, tabla `bff.shedlock`, migración `V202611061000`, con un índice nuevo en `creation_time`). Un barrido dura como máximo la mitad de su bloqueo;
  - una sesión vencida que no se puede leer (por ejemplo, otra versión de una clase) se borra igual. Una sesión vieja con el mismo `sid` que otra abierta de la persona se borra sin terminar en LIV ni en el proveedor la que está en uso;
  - la limpieza propia de Spring Session queda apagada (`spring.session.jdbc.cleanup-cron: "-"`; el BFF no arranca si no) y una sesión que se lee ya vencida no se lee: la petición queda sin sesión y la termina el barrido, en lugar de borrarse en silencio (`ClosingSessionRepository`). La petición nunca espera a LIV ni al proveedor, salvo `/bff/login`, que termina primero la sesión vencida del navegador para que el fin quede en LIV antes del nuevo inicio;
  - `EndSession` y la revocación informan si salieron bien. El cierre de la persona («Salir», el límite absoluto) no cambia: no espera a LIV.
- Reenvío a la API, CSRF, límites de sesión y `keepalive` (feature 001, T18; 001-10, 001-12, 001-22):
  - `/api/**` va a `liv-api` con el *token* de acceso de la sesión (`TokenRelayFilter`, renovado si le queda menos de un minuto); no pasan la cookie, `X-User-Token`, el `Authorization` ni `X-XSRF-TOKEN` del navegador. `/internal/**` no se enruta (404). Con `invalid_grant` la sesión termina con `PROVIDER` y la respuesta es 401; si el proveedor no responde, 502;
  - CSRF con la cookie `XSRF-TOKEN` y la cabecera `X-XSRF-TOKEN` (solo la cabecera: no el parámetro `_csrf`), entregada también al iniciar sesión; sin el *token*, 403 con `code: 001-12`;
  - inactividad de 15 minutos (`spring.session.timeout`) y duración máxima de 8 horas (`liv.bff.session.absolute-timeout`), comprobada en cada petición: pasada, `EndSession(ABSOLUTE)`, revocación, borrado y 401; salir o volver a `/bff/login` pasado el límite también se audita como `ABSOLUTE`;
  - la renovación del *token* (`TokenRenewal`): el `scope` del navegador no llega al proveedor; si el proveedor falla un momento y al *token* aún le queda vida, la petición sigue con él; sin *refresh token*, o con `invalid_grant` o `invalid_token`, la sesión termina;
  - la respuesta de la API pierde `Set-Cookie` y lleva `Cache-Control: no-store`; `/bff/session` lee todas las cookies de sesión;
  - `GET /bff/session` (segundos que quedan, sin extender la sesión: responde antes del filtro de Spring Session) y `POST /bff/session/keepalive` (renueva siempre el *token* y extiende la sesión);
  - tiempos de espera del reenvío (`spring.http.clients.*`);
  - `contracts/openapi/bff.yaml` (versión 1.0.0) y la copia de los *scripts* de Q08 con `openapi-diff-maven` en `pluginManagement` (ADR 0027).
- Pruebas: reenvío, CSRF, estado y `keepalive`, inactividad y límite absoluto con tiempos cortos, renovación del *token* y `invalid_grant` con el *token endpoint* reemplazado, y el contrato; el servidor de pruebas de PostgreSQL ya no agota sus conexiones (pool de 3 por contexto y 6 contextos en caché).
- Inicio y cierre de sesión (feature 001, T17; 001-01, 001-09, 001-11, 001-20):
  - `GET /bff/login`: cliente OIDC con código, PKCE, *nonce* y `prompt=login`, con `returnTo` validado contra la redirección abierta. Al volver del proveedor cambia el id de sesión y llama a `StartSession` en la API con el *token* de servicio (`liv-bff-service`) y el del usuario; un 200 es un reintento;
  - si LIV rechaza la sesión, revoca el *refresh token* (RFC 7009), borra la sesión y vuelve a `/bff/login`, sin motivo en la URL;
  - `POST /bff/logout`: `EndSession(LOGOUT)`, revocación y borrado de la sesión, y responde la ruta `/bff/login`;
  - Spring Session JDBC en `bff.spring_session` y `bff.spring_session_attributes` (migración `V202611051000`), con la cookie `LIV_SESSION` (`HttpOnly`, `Secure`, `SameSite=Strict`);
  - los *tokens* se guardan cifrados con AES-256-GCM (`LIV_BFF_SESSION_KEY`); el contexto de seguridad guarda solo emisor, `sub` y `sid`;
  - los clientes OIDC se resuelven al primer uso: el BFF arranca sin el proveedor;
  - cada conexión de la base comprueba que el rol sea `liv_bff` sin privilegios, membresías ni objetos propios (pendiente de T16);
  - `spring-boot-starter-oauth2-client` y `spring-boot-starter-session-jdbc` (ADR 0023).
- Pruebas del flujo completo contra `mock-oauth2-server`, una API y una revocación simuladas y PostgreSQL real; reglas de ArchUnit sobre la dirección de las dependencias entre paquetes.
- Esqueleto del BFF (feature 001, T16): proyecto Maven autónomo `liv-bff` (*wrapper* con Maven 3.10.0) con Java 21 LTS, Spring Boot 4.1.2 y Spring Cloud 2025.1.3 (Gateway Server MVC 5.0.3). El tren de Spring Cloud sale del *spike* de T16 (ADR 0023).
- Esquema `bff` con Flyway (`flyway-database-postgresql`, `postgresql`). La primera migración:
  - comprueba el contrato de los roles `liv_bff_owner` y `liv_bff`: quién migra, dueño del esquema, atributos, membresías en los dos sentidos, permisos sobre el esquema, permisos por defecto, otros esquemas, `CREATE` y `TEMP` sobre la base;
  - fija los permisos del dueño, con el historial en `bff.flyway_schema_history` y sin acceso para `liv_bff`.

  Flyway solo corre con los perfiles `dev` (en `config/`, fuera del jar) y `test`.
- Pruebas guardianas: reglas de arquitectura (sin `org.keycloak` en el código ni en el *classpath*, 001-20; inyección por constructor), catálogo de paquetes, tamaño de clases y dependencias, y arranque sin base con una ruta del Gateway.
- Pruebas con PostgreSQL 18.6 en Testcontainers:
  - migración al arrancar con la configuración real, como `liv_bff_owner`, y conexión como `liv_bff`;
  - permisos de `liv_bff` y `liv_app` con lo mínimo de la infraestructura y con el entorno local;
  - reversión limpia de una migración detenida;
  - una prueba por cada incumplimiento del contrato.
- Formato con Spotless y Google Java Format (2 espacios), verificado en `mvn verify`.

### Cambiado

- Contrato `bff.yaml` 1.1.0, compatible (decisión del responsable sobre T21). El rechazo anti-CSRF (`CsrfRejected`) usa el esquema `CsrfProblem`, que documenta `code` 001-12 con su ejemplo, y la descripción dice que en `/api/**` el BFF responde ese mismo 403, antes que el 401. No va como `enum`, porque `openapi-diff` 2.1.7 toma como incompatible agregarlo a una respuesta; `BffContractTest` comprueba que el ejemplo es el código que escribe el BFF. Se quita el esquema `Problem`, que ya no usaba nada.
- El 403 con `code` 001-12, en `application/problem+json;charset=UTF-8`, solo responde a un rechazo anti-CSRF (`CsrfRejection`). Cualquier otra denegación del BFF da un 403 sin ese código, para que el frontend no la confunda con la falta del token.
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.
- Las pruebas de T16 se ajustan a la configuración nueva: el arranque sin web usa el entorno `MOCK`, el contrato de migraciones ya no cuenta una sola migración, y la prueba del Gateway abre su ruta de prueba porque todas las demás piden sesión.

### Pendiente (T19)

- Tiempos de espera de las llamadas que hace Spring Security por su cuenta (descubrimiento del proveedor e intercambio del código), terminador de sesiones vencidas, canal trasero, límite de tasa y cabeceras (T19).
