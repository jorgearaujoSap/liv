# Changelog — liv-bff (BFF)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `bff-vX.Y.Z`.

## [Sin publicar]

### Agregado

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

- `AGENTS.md`: una tarea sin feature aprobada puede ser un cambio del flujo directo, según la evaluación previa del repositorio de plataforma (ADR 0028). Antes solo podía ser un cambio trivial.
- Las pruebas de T16 se ajustan a la configuración nueva: el arranque sin web usa el entorno `MOCK`, el contrato de migraciones ya no cuenta una sola migración, y la prueba del Gateway abre su ruta de prueba porque todas las demás piden sesión.

### Pendiente (T18 y T19)

- CSRF, inactividad (15 min), límite absoluto (8 h), `keepalive`, `/bff/session` y el reenvío a `/api` (T18).
- Tiempos de espera de las llamadas que hace Spring Security por su cuenta (descubrimiento del proveedor e intercambio del código), terminador de sesiones vencidas, canal trasero, límite de tasa y cabeceras (T19).
