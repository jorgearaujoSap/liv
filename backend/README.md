# liv-api (backend)

Backend del sistema de liquidación y recaudo del impuesto vehicular (LIV): Java 21 LTS, Spring Boot 4.1 y Spring Modulith, organizado como **monolito modular** con un artefacto Maven.

> Proyecto **autónomo**: hoy vive en `backend/` del monorepo `liv`, y más adelante tendrá su propio repositorio. No depende de ningún archivo fuera de esta carpeta (reglas IN‑01 a IN‑11 del documento de arquitectura 01 §14.1).

## Requisitos

- JDK 21.
- No hace falta instalar Maven: se usa el *wrapper* (`./mvnw` o `mvnw.cmd`), que descarga Maven 3.10.0.

## Comandos

| Comando | Qué hace |
|---|---|
| `./mvnw verify` | Compila, ejecuta todas las pruebas y verifica el formato |
| `./mvnw test` | Solo las pruebas |
| `./mvnw spotless:apply` | Formatea el código (Google Java Format, 2 espacios) |
| `./mvnw spring-boot:run` | Arranca la API en `http://localhost:8080` (salud en `/actuator/health`). Necesita el entorno local y las variables de `dev/.env`: ver `dev/README.md` |

En Windows, `mvnw.cmd` en lugar de `./mvnw`.

## Estructura

```
backend/
├── pom.xml                         ← un solo artefacto: liv-api
├── mvnw, mvnw.cmd, .mvn/           ← Maven wrapper
├── contracts/openapi/              ← contratos OpenAPI, uno por módulo (contract-first)
├── contracts/scripts/              ← comparador de cambios incompatibles y publicación de los contratos
└── src/
    ├── main/java/co/com/sapenlinea/liv/
    │   ├── LivApplication.java
    │   ├── shared/                 ← tipos de valor comunes (Java puro)
    │   ├── platform/               ← transversal técnico: seguridad, inquilino, auditoría, ingesta, exportación
    │   ├── core/ · documents/      ← registros (CRUD simplificado)
    │   ├── jurisdiction/ · valuation/ · tax/ · ledger/   ← reglas de negocio (hexagonal completo)
    │   └── audit/ · reporting/
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/           ← migraciones Flyway (feature 001: esquemas platform, core y audit)
    └── test/java/co/com/sapenlinea/liv/
        ├── LivApplicationTests.java
        └── architecture/           ← guardias: catálogo de módulos, reglas de arquitectura, tamaño, fronteras
```

### Capas de cada módulo de negocio

| Capa | Contenido |
|---|---|
| `api` | Controladores REST y DTO (implementan el contrato OpenAPI) |
| `application` | Casos de uso: orquestan, abren la transacción y autorizan |
| `domain` | Modelo, reglas, enums, excepciones y puertos. En `jurisdiction`, `valuation`, `tax` y `ledger` es Java puro, sin Spring ni JPA |
| `infrastructure` | Adaptadores: persistencia (entidades JPA y repositorios), clientes externos, configuración, tareas programadas |

Lo que otros módulos pueden usar (fachadas y eventos) va en el **paquete raíz** del módulo; las subcarpetas son internas (Spring Modulith lo verifica).

## Contratos de API

La API es *contract-first* (ADR 0005, doc. 01 §10): primero se escribe el OpenAPI en `contracts/openapi/<modulo>.yaml` (y `<modulo>-internal.yaml` para los endpoints entre servicios) y luego se implementa.

- `./mvnw` genera las interfaces de servidor y sus modelos con `openapi-generator-maven-plugin` en `target/generated-sources/openapi/`, dentro del paquete `<modulo>.api.contract`. **No se editan ni se suben al repositorio.**
- La generación valida cada contrato: un OpenAPI inválido detiene el *build*.
- **Versión y compatibilidad (Q08, ADR 0027).** Cada contrato lleva su versión en `info.version` (`X.Y.Z`), y si el contrato cambia, la versión sube. Un cambio incompatible, o quitar un contrato, exige la etiqueta `contrato-incompatible` en el PR y subir la versión mayor. La etiqueta es la aprobación explícita del responsable: la pone una persona, nunca un agente. El *workflow* `backend-contracts-pr` lo comprueba con `openapi-diff`; antes de abrir el PR, también en local (en Windows, desde Git Bash):
  - `sh contracts/scripts/compare.sh origin/main` compara los contratos con `main` (con la rama al día);
  - `sh contracts/scripts/test/run.sh` prueba el comparador y la publicación.
- **Otras reglas de la compuerta.** En `contracts/openapi/` solo van contratos `.yaml`, con nombre en minúsculas, dígitos y guiones, y con los `$ref` internos (`#/...`). Un PR no cambia a la vez el comparador (`contracts/scripts/` o la versión de `openapi-diff`) y un contrato, para que el comparador no se juzgue a sí mismo.
- **Huecos conocidos de `openapi-diff` 2.1.7.** No detecta una petición más estricta por `minLength`, `minimum` o similares, ni un campo obligatorio de la respuesta que pasa a `nullable`. Esos cambios los revisa una persona; dos escenarios de las pruebas fijan el comportamiento actual para que un cambio de versión del plugin se note.
- **Publicación.** Al integrar en `main`, el *workflow* `backend-main` vuelve a comparar contra el commit anterior y publica cada versión nueva como *release* `contract-<nombre>-vX.Y.Z`, con `<nombre>.yaml` y su SHA-256 (`contracts/scripts/publish.sh`). La *release* se crea como borrador y se publica solo con los dos archivos verificados. El frontend la descarga de `https://github.com/jorgearaujoSap/liv/releases/download/contract-<nombre>-vX.Y.Z/<nombre>.yaml` (la URL cambia si el backend pasa a su propio repositorio) y fija la versión y el SHA-256 en su `api-spec/versions.json`. Una versión publicada no cambia: si su contenido es otro, la publicación falla.
- El nombre del archivo identifica al contrato y nombra su *release*, así que es único en todo el repositorio. El BFF (`bff/`) tendrá su propia copia de `contracts/scripts/` (T18, con `bff.yaml`), para seguir siendo un proyecto independiente.
- Todo controlador implementa su interfaz generada y no declara rutas propias; nadie escribe a mano en los paquetes `api.contract`. `ContractRulesTest` lo verifica.
- Los errores se responden con `ProblemDetail` de Spring (RFC 9457) y el código de la regla en `code`.

| Contrato | Rutas | Interfaz generada |
|---|---|---|
| `core.yaml` | `GET /api/v1/me` | `core.api.contract.CurrentUserApi` |
| `platform.yaml` | `GET /api/v1/me/capabilities` | `platform.api.contract.CapabilitiesApi` |
| `core-internal.yaml` | `POST /internal/v1/sessions`, `POST /internal/v1/sessions/{sid}/end` (solo el BFF, ADR 0025) | `core.api.contract.internal.InternalSessionsApi` |

## Seguridad

La API es un servidor de recursos OAuth 2.0 sin estado (doc. 02 §3, plan de la feature 001, paso 7). Valida los tokens solo con OIDC estándar (001-20): con cualquier proveedor que publique su *discovery* y su JWKS.

| Rutas | Quién llama | Qué exige |
|---|---|---|
| `/api/**` | El navegador, a través del BFF | Token de usuario con audiencia `liv-api`. Luego `CurrentUserFilter` busca al usuario en el registro de LIV y responde 401 con `001-14` (sin registro), `001-05` (desactivado) o `001-17` (otra administración) |
| `/internal/**` | Solo el BFF (ADR 0025) | Token de *client credentials* de `liv-bff-service`, con audiencia `liv-internal` y `azp` igual a ese cliente. Para iniciar sesión, además, el token del usuario en `X-User-Token` |
| Resto | — | Solo `/actuator/health` y `/actuator/info`; lo demás se niega |

- Variable obligatoria: `LIV_OIDC_ISSUER_URI`, el emisor. El perfil `dev` usa el Keycloak local. El *discovery* se hace con el primer token, así que la API arranca aunque el proveedor no esté arriba.
- Audiencias, cliente de servicio y nombres de los *claims* se configuran en `liv.security` (`application.yml`).
- La administración de cada petición sale del registro de LIV, nunca del token (`AdministrationContext`). El gestor de transacciones la fija como `app.administration_id`, con alcance local, al empezar **cada** transacción (también `REQUIRES_NEW`), y la RLS limita las filas a esa administración. Sin contexto se fija vacío y las políticas no devuelven filas, aunque la conexión, el rol o la base tuvieran otro valor. Un oyente o un trabajo abre el contexto con la administración de su evento **antes** de su transacción: abrir otra administración dentro de una transacción abierta falla. Por eso un oyente no es transaccional: abre la administración de su evento y luego llama a un servicio `@Transactional` (con `@ApplicationModuleListener` no se puede).
- La aplicación solo se conecta como `liv_app`: cada conexión nueva comprueba el usuario de la sesión y el rol, que no sea superusuario ni tenga `BYPASSRLS`, membresías ni tablas propias, y que el rol o la base no fijen un contexto (`LivAppConnectionCheck`, impuesto en código). Se comprueba al crear cada conexión: un privilegio concedido después llega a las conexiones del *pool* hasta que se renuevan. Si algo de eso falla, no se obtiene ninguna conexión y la sonda de *readiness*, que incluye la base, no deja la instancia lista.
- El token de usuario exige, además de la audiencia `liv-api`, `azp = liv-bff`: los usuarios solo inician sesión por el BFF. El emisor y todas las llamadas al proveedor (descubrimiento y JWKS) deben ser `https://`, salvo en `dev` y en las pruebas (`liv.security.allow-http-issuer`, que deja un aviso en el log).
- Todo token debe tener `exp`. Las llamadas al proveedor tienen un tiempo de espera de 3 s, y la base, de 5 s para obtener conexión.
- Todo error es Problem Details con `code` y sin detalles internos (`ProblemDetailsHandler`). Los rechazos de seguridad llevan el código del criterio y `WWW-Authenticate: Bearer`; los errores técnicos, `SYS-<estado>` (404, 500 y 503 cuando la base o el proveedor no responden). Los fallos de los filtros también pasan por ahí (`ProblemFallbackFilter`). El detalle va al log, en una línea con el id de correlación.
- `X-Correlation-Id` solo se acepta como UUID; si no lo es, se genera uno nuevo. Va en la respuesta y en cada línea de log.
- Las credenciales se enmascaran en los mensajes y en las trazas de los logs (`logback-spring.xml`): JWT, `Bearer`, `X-User-Token`, `Authorization`, `client_secret` y los tokens de OAuth. El log estructurado (JSON) de Spring Boot no pasa por ese filtro, así que sigue apagado: `LogMaskingTest` falla si se enciende.
- Las pruebas usan la configuración de producción contra `mock-oauth2-server` en un contenedor, que sirve el *discovery* y el JWKS (`platform/OidcTestProvider`). `RealKeycloakTest` repite lo esencial contra Keycloak real, con el *realm* como código aplicado por keycloak-config-cli igual que `dev/compose.yaml`: el token de usuario del BFF (código y PKCE) y el de servicio pasan, y ninguno sirve en el lugar del otro. Tarda alrededor de un minuto.

## Sesiones de LIV

El BFF inicia y cierra la sesión de LIV de cada usuario por `core-internal.yaml` (ADR 0025). Lo implementan `core.application.StartSession` y `EndSession`, sobre `JdbcSessionStore`, que trabaja con SQL bajo RLS.

- **Inicio** (`POST /internal/v1/sessions`). Los datos salen del token del usuario ya validado (`VerifiedUserToken`) y el registro de LIV decide:

  | Caso | Respuesta |
  |---|---|
  | Sesión nueva | 201 |
  | Reintento sobre la misma sesión abierta | 200 |
  | `sid` distinto del token, de otro usuario o de una sesión cerrada | 401 `001-14` |
  | Usuario desactivado | 403 `001-05` |
  | Otra administración, o una inexistente o inactiva | 403 `001-17` |

- **Primer ingreso:** el usuario se registra en la administración que dice el token, con identificador propio de LIV vinculado a (emisor, `sub`) (001-21). Si falta el correo, o el nombre o el correo no caben en el registro, la respuesta es 401 `001-14`. `liv.sessions.auto-provision=false` apaga el alta automática, por ejemplo durante un cambio de proveedor.
- **Cierre** (`POST /internal/v1/sessions/{sid}/end`): cierra una sola vez la sesión abierta de ese emisor y `sub`, y siempre responde 204.
- **Transacciones:** cada operación abre la administración y después su transacción. Un inicio que no inicia se revierte entero.
- **Eventos:** `SessionStarted`, `UserProvisioned` y `SessionEnded` se publican en la misma transacción. Todo rechazo publica `SessionDenied` en una transacción aparte. La auditoría los escuchará en T15.
- **`GET /api/v1/me` (001-13):**
  - el usuario y su administración salen del registro de LIV, bajo RLS con la administración registrada;
  - los roles son los del token que están en el catálogo de LIV (`platform.app_role`), y los permisos, la unión de los suyos (`PermissionCatalog`);
  - sin roles conocidos, las listas van vacías: el usuario solo llega a Inicio.
- **Autorización (001-16):** cada método público de un servicio de `application` lleva una de dos marcas:
  - `@RequiresPermission("<modulo>.<recurso>.<accion>")`: antes de ejecutarlo se comprueba que el usuario de la petición tenga ese permiso por alguno de los roles de su token que LIV conoce; si no, o si no hay petición, 403 `001-16` y el método no corre;
  - `@OpenUseCase("motivo")`: corre para cualquiera, también para un oyente o un trabajo. La lista cerrada de estos métodos está en la prueba de arquitectura `AuthorizationRules`; agregar uno es un cambio que se revisa.

  Un método sin marca se niega a todos. Los servicios de `application` son `@Service`, no `final` y sin superclase, y no se llaman a sí mismos con `this` en un método protegido, porque esa llamada no pasaría por la comprobación.
- **Menú (`GET /api/v1/me/capabilities`, 001-15):** `src/main/resources/platform/menu-catalog.yaml` lista las opciones de Doc. 01 §3. Cada hoja lleva su permiso y su estado (`AVAILABLE` o `COMING_SOON`); una rama se ve si se ve alguno de sus hijos. Cuando una feature construye una opción, cambia su estado a `AVAILABLE` en ese archivo.

## Auditoría

- **Eventos de LIV (001-18):** inicio, fin, rechazo y primer ingreso quedan en `audit.audit_event` con usuario, fecha, resultado y correlación. Los escribe un oyente asíncrono después del *commit*; el *outbox* JDBC (`platform.event_publication`) guarda cada entrega hasta que termina.
- **Eventos del proveedor:** el importador los copia cada `liv.audit.importer.interval` con el cliente `liv-audit-importer`, enmascarados (ver `CHANGELOG.md`).
- **Variables:** `LIV_AUDIT_HMAC_KEY` (al menos 32 caracteres) y `LIV_AUDIT_IMPORTER_SECRET`. Sin ellas, la API no arranca mientras `liv.audit.importer.enabled` esté encendido (lo está por omisión).

## Estado

Esqueleto de la Fase 0 (entregable F0‑04): módulos vacíos y guardias de arquitectura activas. Sin funcionalidad de negocio todavía. Ver `CHANGELOG.md` y el `ESTADO.md` del repositorio de plataforma.
