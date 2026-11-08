# liv-bff (BFF)

*Backend for frontend* del sistema de liquidación y recaudo del impuesto vehicular (LIV), según el ADR 0010. Hace el inicio de sesión OIDC (código con PKCE) y guarda la sesión en el servidor, así que el navegador nunca recibe un *token*. También es la única puerta de entrada del navegador a la API. Usa Java 21 LTS, Spring Boot 4.1 y Spring Cloud Gateway Server MVC.

> Proyecto **autónomo**: vive en `backend/bff/` y no depende de ningún archivo fuera de esta carpeta (doc. 01 §14.1). Con la API (`liv-api`) solo se comunica por HTTP; con el frontend, por su contrato OpenAPI (`contracts/openapi/bff.yaml`), que llegará con las tareas siguientes.

## Requisitos

- JDK 21.
- No hace falta instalar Maven: se usa el *wrapper* (`./mvnw` o `mvnw.cmd`), que descarga Maven 3.10.0.
- Docker, para las pruebas con PostgreSQL (Testcontainers).

## Comandos

| Comando | Qué hace |
|---|---|
| `./mvnw verify` | Compila, ejecuta todas las pruebas y verifica el formato |
| `./mvnw test` | Solo las pruebas |
| `./mvnw spotless:apply` | Formatea el código (Google Java Format, 2 espacios) |
| `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` | Arranca el BFF en `http://localhost:8081` contra la base local y migra el esquema `bff` |

En Windows, `mvnw.cmd` en lugar de `./mvnw`.

## Estructura

```
backend/bff/
├── pom.xml                         ← un artefacto: liv-bff
├── contracts/                      ← bff.yaml (OpenAPI) y la copia de los scripts de Q08
├── mvnw, mvnw.cmd, .mvn/           ← Maven wrapper
├── config/application-dev.yml      ← perfil de desarrollo (fuera del jar)
└── src/
    ├── main/java/co/com/sapenlinea/liv/bff/
    │   ├── LivBffApplication.java
    │   ├── login/                  ← inicio de sesión: /bff/login, returnTo, regreso del proveedor y rechazo
    │   ├── logout/                 ← cierre de sesión: /bff/logout
    │   ├── session/                ← tokens cifrados, llamadas a LIV y al proveedor, comprobación del rol de BD
    │   └── web/                    ← configuración: propiedades, clientes OIDC y seguridad
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/           ← migraciones Flyway del esquema bff
    └── test/java/co/com/sapenlinea/liv/bff/
        ├── LivBffApplicationTest.java
        ├── architecture/           ← guardias: reglas de arquitectura, catálogo de paquetes, tamaño
        ├── login/, logout/         ← flujo completo contra un proveedor OIDC genérico (mock-oauth2-server)
        ├── persistence/            ← PostgreSQL real: contrato del esquema, tablas de sesión y rol de conexión
        ├── session/                ← cifrado de los tokens
        └── support/                ← el proveedor, la API simulada y un "navegador" de prueba
```

Los paquetes de `co.com.sapenlinea.liv.bff` son los que declaran las tareas de la feature 001: `login`, `logout`, `session` y `web`. Un paquete nuevo es un cambio de diseño: se agrega al catálogo de `PackageCatalogTest` en el mismo PR.

## Base de datos

El BFF usa su propio esquema, `bff`, en la base de LIV, con dos roles (doc. 02 §5.2):

| Rol | Uso |
|---|---|
| `liv_bff_owner` | Dueño del esquema. Solo lo usan las migraciones |
| `liv_bff` | Aplicación: DML en `bff`, sin acceso a los esquemas de LIV |

- **Lo que debe entregar la infraestructura** (en local, el entorno de desarrollo del backend; en AWS, la infraestructura como código):
  - los roles `liv_bff_owner` y `liv_bff`, con inicio de sesión y sin atributos de privilegio ni membresías;
  - en la base, `REVOKE ALL ON DATABASE … FROM PUBLIC` y `REVOKE ALL ON SCHEMA public FROM PUBLIC`, más `CONNECT` para los dos roles. `liv_bff_owner` no es dueño de la base y no puede revocarlos por su cuenta;
  - en las demás bases del servidor (en RDS, `postgres`), `REVOKE ALL … FROM PUBLIC`: la migración solo ve la base en la que corre;
  - el esquema: `CREATE SCHEMA bff AUTHORIZATION liv_bff_owner`. Flyway no lo crea: el historial queda dentro de él, en `bff.flyway_schema_history`.
- **La primera migración** (`V202611032100__bff_schema_roles.sql`, PostgreSQL 16 o superior) comprueba ese contrato y se detiene con un mensaje claro si no se cumple. La reversión deja la base como estaba, y la migración se puede repetir al corregir. Comprueba que:
  - corre como `liv_bff_owner` y que ese rol es el dueño del esquema;
  - ninguno de los dos roles es superusuario ni tiene `BYPASSRLS`, `CREATEROLE`, `CREATEDB` o `REPLICATION`;
  - ninguno de los dos es miembro de otros roles. En RDS el poder llega por membresía (`rds_superuser`, `pg_read_all_data`…);
  - ningún rol es miembro de ellos, ni directo ni a través de otro, salvo un administrador ajeno a LIV (con `CREATEROLE` y sin el prefijo `liv_`). Quien los crea en RDS queda como miembro;
  - solo los dos roles del BFF tienen permisos sobre `bff` (`liv_app` no), y los permisos por defecto del dueño solo dan DML en `bff` a `liv_bff`;
  - ninguno de los dos ve otros esquemas (ni los de LIV ni `public`), crea objetos fuera de `bff` ni crea esquemas en la base. `liv_bff` tampoco crea objetos en `bff` ni tiene `TEMP`.

  Además fija los permisos del dueño:
  - `USAGE` sobre el esquema y DML por defecto sobre tablas y secuencias para `liv_bff`;
  - sus funciones nacen sin `EXECUTE` para `PUBLIC`;
  - nada para `liv_bff` sobre el historial de Flyway.
- **Flyway** corre solo en los perfiles `dev` y `test`, como `liv_bff_owner`. En staging y producción migrará la imagen `liv-bff-migrations` como paso del despliegue; esa imagen y el `Dockerfile` todavía no existen.
- **Variables:** `LIV_DB_URL` y `LIV_BFF_PASSWORD`. En `application.yml` no tienen valor por defecto, y el perfil `dev` trae la URL de la base local. Con el perfil `dev` se necesita además `LIV_BFF_OWNER_PASSWORD`. La conexión es perezosa: el BFF arranca aunque la base no esté arriba, y si no responde se falla a los 5 s.
- **En local:** el entorno de desarrollo del backend crea los roles, la base y el esquema en el puerto 5433. El perfil `dev` vive en `config/` y Spring lo lee cuando se arranca desde `backend/bff/`.

## Spring Cloud

Se fija el tren **2025.1.3** (Oakwood, con Spring Cloud Gateway 5.0.3). El tren 2025.1.x admite Spring Boot 4.1.x desde la versión 2025.1.2. Es el resultado del *spike* de T16: con Boot 4.1.2, el Gateway arranca y enruta, y `LivBffApplicationTest` lo verifica.

Al subir Spring Boot de versión menor, hay que confirmar en la tabla de compatibilidad de Spring Cloud qué tren le corresponde.

## Configuración

Todo llega por variables de entorno y los secretos y las URL no tienen valor por defecto: sin ellos el BFF no arranca. El perfil `dev` (`config/application-dev.yml`) trae valores para el entorno local de `backend/dev/`.

| Variable | Qué es |
|---|---|
| `LIV_DB_URL`, `LIV_BFF_PASSWORD` | Base de datos, como `liv_bff` |
| `LIV_OIDC_ISSUER_URI` | Emisor del proveedor OIDC, con `https://` (solo `liv.bff.oidc.allow-http-issuer=true` admite `http://`). Su documento de descubrimiento se lee la primera vez que se necesita, no al arrancar: el BFF arranca aunque el proveedor esté caído |
| `LIV_BFF_CLIENT_SECRET` | Secreto del cliente `liv-bff` (código con PKCE) |
| `LIV_BFF_SERVICE_CLIENT_SECRET` | Secreto del cliente `liv-bff-service` (*client credentials*), para los endpoints internos de la API |
| `LIV_API_URL` | Dirección interna de `liv-api` |
| `LIV_BFF_SESSION_KEY` | Clave que cifra los *tokens* de la sesión: 32 bytes en base64 (AES-256-GCM). En AWS sale de Secrets Manager |
| `LIV_BFF_TRUSTED_PROXIES` | Expresión regular con las direcciones del balanceador, las únicas cuyas `X-Forwarded-For` y `X-Forwarded-Proto` se aceptan (p. ej. `10[.]0[.]1[.][0-9]+`). Por omisión, solo la máquina local: detrás de un balanceador, sin ella todas las personas parecen venir de él y el BFF lo avisa al arrancar |
| `LIV_BFF_REPLICAS` | Cuántas réplicas del BFF hay (por omisión, 1): cada una deja pasar su parte del límite de peticiones |

Propiedades opcionales (`liv.bff.*`): `oidc.revocation-uri` (si el proveedor no publica `revocation_endpoint`), `oidc.redirect-uri` (por omisión `{baseUrl}/login/oauth2/code/{registrationId}`; detrás de un proxy hay que dar la dirección pública) y `api.connect-timeout` / `api.read-timeout` (2 s y 5 s).

## Inicio y cierre de sesión (T17)

El navegador nunca recibe un *token* (001-11): la sesión vive en el servidor y el navegador solo lleva la cookie `LIV_SESSION` (`HttpOnly`, `Secure`, `SameSite=Strict`).

| Ruta | Qué hace |
|---|---|
| `GET /bff/login?returnTo=<ruta>` | Redirige al proveedor con código, PKCE, *nonce* y `prompt=login`. `returnTo` solo se acepta si es una ruta de LIV (`ReturnTo`); si no, se vuelve a `/` |
| `GET /login/oauth2/code/liv` | Regreso del proveedor: cambia el id de sesión, abre la sesión en LIV (`StartSession` con el *token* de servicio y el del usuario; un 200 es un reintento) y redirige a la ruta pedida. Si LIV no acepta la sesión, revoca el *refresh token* (RFC 7009), borra la sesión y redirige a `/bff/login`, sin motivo |
| `POST /bff/logout` | `EndSession(LOGOUT)` en LIV, revocación del *refresh token* en el proveedor y borrado de la sesión. Responde `{"redirectTo":"/bff/login"}`; cada paso sigue si el anterior falla. Sin sesión responde igual y no llama a nadie |
| `GET /bff/session` | Cuánto le queda a la sesión: `{"idleSecondsLeft", "absoluteSecondsLeft"}`, o 401. **No la extiende** (T18) |
| `POST /bff/session/keepalive` | La persona elige seguir conectada: renueva **siempre** el *token* (así el proveedor también reinicia su inactividad) y extiende la sesión; no pasa de la duración máxima (T18) |
| `/api/**` | Se reenvía a `liv-api` con el *token* de acceso de la sesión, renovado si le queda menos de un minuto (T18) |
| `/internal/**` | 404: nunca se enruta (T18) |
| Cualquier otra ruta | 401 sin sesión |

Detalles que conviene conocer:

- **Un nuevo `GET /bff/login` con una sesión abierta la cierra antes** (`EndSession(LOGOUT)` y revocación), pero solo si es una navegación del navegador: una imagen, un `fetch` o una precarga (`Sec-Fetch-*`, `Sec-Purpose`) no hacen nada. Si la cookie no viaja (enlace desde otro sitio, `SameSite=Strict`), la sesión anterior queda abierta hasta que T19 la barra.
- **Si `StartSession` no responde a tiempo**, el BFF no sabe si LIV creó la sesión: la rechaza y la compensa con `EndSession(PROVIDER)`. Si LIV la confirma después de ese `EndSession`, queda abierta en LIV hasta que T19 (o una expiración en LIV) la cierre.
- **`SameSite=Strict` exige que el proveedor y LIV sean del mismo sitio** (mismo dominio registrable, como `liv.test` y `auth.liv.test`): de otro modo el navegador no envía la cookie en el regreso del proveedor.
- Los motivos de `EndSession` que usa T17 son `LOGOUT` (salir y empezar otro inicio de sesión) y `PROVIDER` (compensación); un motivo propio para cada caso sería un cambio del contrato `core-internal.yaml`.

Los *tokens* se guardan cifrados (`TokenCipher`) como un atributo de la sesión de Spring Session JDBC, en las tablas `bff.spring_session*`; el contexto de seguridad solo guarda el emisor, el `sub` y el `sid`.

## Sesión, CSRF y reenvío a la API (T18)

El contrato con el frontend es `contracts/openapi/bff.yaml` (*contract‑first*, ADR 0005): `/bff/login`, `/bff/logout`, `/bff/session` y `/bff/session/keepalive`. `/api/**` es el contrato de la API, no de este.

- **Tiempos** (plan, «Sesión y tiempos»): 15 minutos sin actividad (`spring.session.timeout`) y 8 horas de duración máxima (`liv.bff.session.absolute-timeout`), comprobada en cada petición de la persona: pasada, la sesión termina con `EndSession(ABSOLUTE)`, la revocación y el borrado, y la respuesta es 401 (con `Cache-Control: no-store`). Salir o volver a `/bff/login` pasado el límite también la cierra como `ABSOLUTE` (no como `LOGOUT`), y siguen su curso: salir responde lo de siempre y `/bff/login` empieza otro inicio. Cualquier petición que pase por la cadena (por ejemplo, a `/api`) extiende la inactividad; `GET /bff/session` no, porque responde antes del filtro de Spring Session.
- **CSRF** (001-12): la cookie `XSRF-TOKEN` (`Secure`, `SameSite=Strict`, sin `HttpOnly` para que la lea el frontend) se copia a la cabecera `X-XSRF-TOKEN` en toda petición que modifica datos; sin ella, 403 con `code: 001-12`. El *token* solo se lee de la cabecera, no del parámetro `_csrf` de un formulario. La cookie no es una credencial: la única que autentica es `LIV_SESSION`. Se entrega con la respuesta que deja entrar a la persona y con toda respuesta posterior.
- **Reenvío:** del navegador no pasan a la API ni su `Authorization`, ni la cookie de sesión, ni `X-User-Token`, ni `X-XSRF-TOKEN`. La respuesta de la API no puede poner cookies (`Set-Cookie` se quita) ni permitir caché (`Cache-Control: no-store`). El `scope` de la petición del navegador no llega al proveedor al renovar. Si el proveedor rechaza la renovación para siempre (`invalid_grant` o `invalid_token`: usuario desactivado o sesión cerrada allí) o no hay con qué renovar (sin *refresh token* y con el *token* vencido), la sesión termina con `EndSession(PROVIDER)` y la respuesta es 401, sin reenviar nada. Si el proveedor solo falla un momento, la petición sigue con el *token* que aún vale; si ya venció, 502 y la sesión sigue. Los demás códigos de error del proveedor (`invalid_client`, `invalid_scope`…) se tratan como pasajeros y dan 502. Tiempos de espera: `spring.http.clients.connect-timeout` (2 s) y `read-timeout` (30 s).
- **Contratos (Q08):** `contracts/scripts/` es la copia del comparador de cambios incompatibles de `backend/` (ADR 0027): el BFF sigue siendo autónomo. `contracts/scripts/test/run.sh` lo prueba.

## Estado

Inicio y cierre de sesión, reenvío a la API, CSRF, límites de sesión, `keepalive` y `/bff/session` (feature 001, T17 y T18). Falta, con la tarea siguiente de la feature 001 (`tasks.md`): el endurecimiento antes de producción (T19): el terminador de sesiones vencidas que nadie vuelve a leer, el cierre por canal trasero, la limitación de tasa y las cabeceras de seguridad. Ver `CHANGELOG.md` y el `ESTADO.md` del repositorio de plataforma.

Demostración 003.
