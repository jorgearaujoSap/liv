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
- Todo controlador implementa su interfaz generada y no declara rutas propias; nadie escribe a mano en los paquetes `api.contract`. `ContractRulesTest` lo verifica.
- Los errores se responden con `ProblemDetail` de Spring (RFC 9457) y el código de la regla en `code`.

| Contrato | Rutas | Interfaz generada |
|---|---|---|
| `core.yaml` | `GET /api/v1/me` | `core.api.contract.CurrentUserApi` |
| `platform.yaml` | `GET /api/v1/me/capabilities` | `platform.api.contract.CapabilitiesApi` |
| `core-internal.yaml` | `POST /internal/v1/sessions`, `POST /internal/v1/sessions/{sid}/end` (solo el BFF, ADR 0025) | `core.api.contract.internal.InternalSessionsApi` |

## Estado

Esqueleto de la Fase 0 (entregable F0‑04): módulos vacíos y guardias de arquitectura activas. Sin funcionalidad de negocio todavía. Ver `CHANGELOG.md` y el `ESTADO.md` del repositorio de plataforma.
