# liv-bff (BFF)

*Backend for frontend* del sistema de liquidación y recaudo del impuesto vehicular (LIV), según el ADR 0010. Hace el inicio de sesión OIDC (código con PKCE) y guarda la sesión en el servidor, así que el navegador nunca recibe un *token*. También es la única puerta de entrada del navegador a la API. Usa Java 21 LTS, Spring Boot 4.1 y Spring Cloud Gateway Server MVC.

> Proyecto **autónomo**: vive en `backend/bff/` y no depende de ningún archivo fuera de esta carpeta (doc. 01 §14.1). Con la API (`liv-api`) solo se comunica por HTTP; con el frontend, por su contrato OpenAPI, que llegará con T19.

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
├── mvnw, mvnw.cmd, .mvn/           ← Maven wrapper
├── config/application-dev.yml      ← perfil de desarrollo (fuera del jar)
└── src/
    ├── main/java/co/com/sapenlinea/liv/bff/
    │   └── LivBffApplication.java
    ├── main/resources/
    │   ├── application.yml
    │   └── db/migration/           ← migraciones Flyway del esquema bff
    └── test/java/co/com/sapenlinea/liv/bff/
        ├── LivBffApplicationTest.java
        ├── architecture/           ← guardias: reglas de arquitectura, catálogo de paquetes, tamaño
        └── persistence/            ← PostgreSQL real: contrato del esquema y de los roles
```

Los paquetes de `co.com.sapenlinea.liv.bff` son los que declaran las tareas de la feature 001: `login`, `logout`, `session` y `web`. Un paquete nuevo es un cambio de diseño: se agrega al catálogo de `PackageCatalogTest` en el mismo PR.

## Base de datos

El BFF usa su propio esquema, `bff`, en la base de LIV, con dos roles (doc. 02 §5.2):

| Rol | Uso |
|---|---|
| `liv_bff_owner` | Dueño del esquema. Solo lo usan las migraciones |
| `liv_bff` | Aplicación: DML en `bff`, sin acceso a los esquemas de LIV |

- **La infraestructura crea los roles y el esquema** (`CREATE SCHEMA bff AUTHORIZATION liv_bff_owner`). Flyway no crea el esquema: el historial queda dentro de él, en `bff.flyway_schema_history`.
- **La primera migración** (`V202611032100__bff_schema_roles.sql`) comprueba ese contrato y se detiene con un mensaje claro si no se cumple:
  - `liv_bff_owner` es el dueño del esquema;
  - ninguno de los dos roles es superusuario ni tiene `BYPASSRLS`, `CREATEROLE` o `CREATEDB`;
  - `liv_bff` no es miembro de otros roles;
  - solo los dos roles del BFF tienen permisos sobre `bff` (`liv_app` no);
  - `liv_bff` no crea objetos, no tiene `TEMP` y no ve otros esquemas.

  Además fija los permisos del dueño: `USAGE` y DML por defecto para `liv_bff`, y nada sobre el historial de Flyway.
- **Flyway** corre solo en los perfiles `dev` y `test`, como `liv_bff_owner`. En staging y producción migra la imagen `liv-bff-migrations` como paso del despliegue.
- **Variables:** `LIV_DB_URL` y `LIV_BFF_PASSWORD`, sin valor por defecto; con el perfil `dev`, además, `LIV_BFF_OWNER_PASSWORD`. La conexión es perezosa: el BFF arranca aunque la base no esté arriba.
- **En local:** el entorno de desarrollo del backend crea los roles, la base y el esquema en el puerto 5433. El perfil `dev` vive en `config/` y Spring lo lee cuando se arranca desde `backend/bff/`.

## Spring Cloud

Se fija el tren **2025.1.3** (Oakwood, con Spring Cloud Gateway 5.0.3). El tren 2025.1.x admite Spring Boot 4.1.x desde la versión 2025.1.2. Es el resultado del *spike* de T16: con Boot 4.1.2, el Gateway arranca y enruta, y `LivBffApplicationTest` lo verifica.

Al subir Spring Boot de versión menor, hay que confirmar en la tabla de compatibilidad de Spring Cloud qué tren le corresponde.

## Estado

Esqueleto (feature 001, T16): proyecto, guardias y esquema `bff` sin tablas, todavía sin endpoints. Siguen el inicio y el cierre de sesión (T17), el ciclo de vida de la sesión (T18) y la seguridad de borde con el contrato `bff.yaml` (T19). Ver `CHANGELOG.md` y el `ESTADO.md` del repositorio de plataforma.
