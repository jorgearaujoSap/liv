# Changelog — liv-api (backend)

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con etiquetas `backend-vX.Y.Z`.

## [Sin publicar]

### Agregado

- Esqueleto de la Fase 0 (F0‑04): proyecto Maven (`liv-api`, *wrapper* con Maven 3.10.0) con Java 21 LTS, Spring Boot 4.1.2 y Spring Modulith 2.1.1. La base sigue las convenciones del equipo (Maven, capas `api`/`application`/`domain`/`infrastructure`, pruebas guardianas), con mejoras para este proyecto.
- Módulos `shared`, `platform`, `core`, `documents`, `jurisdiction`, `valuation`, `tax`, `ledger`, `audit` y `reporting` como paquetes de `co.com.sapenlinea.liv`.
- Pruebas guardianas: arranque; fronteras entre módulos (Spring Modulith); catálogo de módulos y capas estándar; reglas de arquitectura (controladores sin infraestructura, dominio hexagonal sin frameworks, aplicación con puertos, dinero sin coma flotante, reloj inyectado, inyección por constructor); tamaño de clases y dependencias, sin excepciones.
- Formato con Spotless y Google Java Format (2 espacios), verificado en `mvn verify`.
- Migraciones Flyway de la feature 001 (T10), parte 1: esquemas `platform`, `core` y `audit`; tabla del *outbox* (`platform.event_publication`); `core.territorial_entity`, `tax_administration`, `admin_user` y `user_session` con FK compuestas y RLS por `app.administration_id`; función `core.find_user_by_identity` (`SECURITY DEFINER`, dueño `liv_identity_lookup`). El outbox queda sin DELETE ni UPDATE general para `liv_app`. Una sesión cerrada no se reabre. Flyway solo corre con los perfiles `dev` (en `config/`, fuera del jar) y `test`, como `liv_owner`; la primera migración comprueba el contrato de roles.
- Pruebas con PostgreSQL 18.6 en Testcontainers y los roles reales del entorno local: aislamiento por administración (001-17), endurecimiento de la función de identidad y permisos exactos de `liv_app`.
