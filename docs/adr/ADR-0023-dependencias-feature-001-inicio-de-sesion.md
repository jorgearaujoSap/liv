# ADR-0023 · Dependencias de la feature 001 (inicio de sesión)

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** arquitecto y responsable funcional (aprobación H2 de la feature 001)
- **Documentos relacionados:** `spec/features/001-inicio-de-sesion/plan.md` (Decisiones); `AGENTS.md` §4.5; `docs/arquitectura/00` §5; ADR 0005, 0008, 0010, 0018

## Contexto

La feature 001 implementa el inicio de sesión con OIDC y BFF (ADR 0010), las primeras migraciones con RLS (ADR 0004), los eventos entre módulos con *outbox* (ADR 0008) y los primeros componentes de `liv-components` (ADR 0018). `AGENTS.md` §4.5 exige un ADR para cada dependencia nueva. Las versiones se fijan exactas en cada tarea y son compatibles con Spring Boot 4.1 y Angular 21.

## Opciones consideradas

1. **Implementar a mano** la validación OIDC, la sesión, los bloqueos de tareas programadas y los iconos. Evita dependencias, pero reimplementa seguridad, que es justo lo que no se debe escribir a mano.
2. **Usar las librerías estándar** de Spring y de Angular para cada necesidad, y no agregar ninguna que no se use en la feature.

## Decisión

Opción 2, con estas dependencias:

| Proyecto | Dependencias | Para qué |
|---|---|---|
| `backend/` | `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `postgresql`, `spring-modulith-starter-jdbc`, `shedlock-spring`, `shedlock-provider-jdbc-template`, `openapi-generator-maven-plugin` | Servidor de recursos, cliente del importador de eventos, persistencia, migraciones, *outbox*, bloqueo de tareas programadas, contrato |
| `backend/` (pruebas) | `spring-boot-testcontainers`, `testcontainers` (`postgresql`, `junit-jupiter`), `testcontainers-keycloak`, imagen `mock-oauth2-server`, `spring-security-test` | Base real, Keycloak real y proveedor OIDC genérico (001-14, 001-20) |
| `backend/bff/` | BOM de Spring Cloud, `spring-cloud-starter-gateway-server-webmvc`, `spring-boot-starter-oauth2-client`, `spring-session-jdbc`, `postgresql`, Flyway, ShedLock, `bucket4j-core` | BFF, sesión en BD, limitación de tasa |
| `backend/identity/` | Imagen de Keycloak, `keycloak-config-cli`, Tailwind CLI, `liv-components` (solo para compilar el tema) | *Realm* como código y tema |
| `frontend/` | `ng-openapi-gen`, `@testing-library/angular`, imagen base de nginx | Cliente generado, pruebas de componentes, servidor de estáticos |
| `ui/` | Storybook, Playwright | Catálogo y capturas de referencia |
| `infra/`, `e2e/` | Caddy, `@playwright/test`, `@axe-core/playwright`, `otplib` | Proxy TLS de e2e, pruebas de sistema, accesibilidad, códigos TOTP |

No se agregan:
- **AspectJ:** `@RequiresPermission` usa la seguridad de métodos de Spring.
- **Librería de iconos:** los SVG de Lucide se copian en `liv-components` con su licencia ISC en `THIRD_PARTY_NOTICES.md`.

`ng-openapi-gen` reemplaza a "openapi‑generator" **solo en el frontend** (doc. 00 §5): genera servicios Angular sin necesitar Java. El backend sigue con `openapi-generator`.

## Consecuencias

- **Positivas:** la seguridad y la sesión usan componentes mantenidos y estándar. Las pruebas usan una base de datos real, Keycloak real y un proveedor OIDC genérico.
- **Negativas o costos:**
  - más superficie de cadena de suministro, controlada con Dependabot, Trivy y SBOM (doc. 02 §11);
  - un *spike* debe confirmar que existe una versión de Spring Cloud Gateway compatible con Spring Boot 4.1. Si no la hay, se usa el plan B del `plan.md`, con un ADR que modifique el ADR 0010.
- **Qué cambia en el repositorio:**
  - doc. 00 §5 (generación del cliente del frontend);
  - los `pom.xml` y `package.json` de cada proyecto, en sus PR;
  - `THIRD_PARTY_NOTICES.md` en `ui/`.
