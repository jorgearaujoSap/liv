# Changelog — liv-bff (BFF)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `bff-vX.Y.Z`.

## [Sin publicar]

### Agregado

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

- Contrato `bff.yaml` 1.1.0, compatible: el rechazo anti-CSRF (`CsrfRejected`) usa el esquema `CsrfProblem`, que fija `code` en `001-12`, y la descripción dice que en `/api/**` el BFF responde ese mismo 403. El frontend distingue así este rechazo de cualquier otro 403 de la API (decisión del responsable sobre T21).
- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.
- Las pruebas de T16 se ajustan a la configuración nueva: el arranque sin web usa el entorno `MOCK`, el contrato de migraciones ya no cuenta una sola migración, y la prueba del Gateway abre su ruta de prueba porque todas las demás piden sesión.

### Pendiente (T19)

- Tiempos de espera de las llamadas que hace Spring Security por su cuenta (descubrimiento del proveedor e intercambio del código), terminador de sesiones vencidas, canal trasero, límite de tasa y cabeceras (T19).
