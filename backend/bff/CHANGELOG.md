# Changelog — liv-bff (BFF)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `bff-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Esqueleto del BFF (feature 001, T16): proyecto Maven autónomo `liv-bff` (*wrapper* con Maven 3.10.0) con Java 21 LTS, Spring Boot 4.1.2 y Spring Cloud 2025.1.3 (Gateway Server MVC 5.0.3). El tren de Spring Cloud sale del *spike* de T16 (ADR 0023).
- Esquema `bff` con Flyway (`flyway-database-postgresql`, `postgresql`): la primera migración comprueba el contrato de los roles `liv_bff_owner` y `liv_bff` y fija sus permisos, con el historial en `bff.flyway_schema_history`. Flyway solo corre con los perfiles `dev` (en `config/`, fuera del jar) y `test`.
- Pruebas guardianas: reglas de arquitectura (sin `org.keycloak`, 001-20; inyección por constructor), catálogo de paquetes, tamaño de clases y dependencias, y arranque sin base con una ruta del Gateway.
- Pruebas con PostgreSQL 18.6 en Testcontainers: migración al arrancar como `liv_bff_owner` y conexión como `liv_bff`, permisos de `liv_bff` y `liv_app`, y una prueba por cada incumplimiento del contrato que detiene la migración.
- Formato con Spotless y Google Java Format (2 espacios), verificado en `mvn verify`.
