# Entorno local del backend

Dependencias del backend para desarrollar en tu equipo: **PostgreSQL 18** (bases `liv` y `keycloak`) y **Keycloak 26**. Lo define la tarea T09 de la feature 001 (doc. 03 §4.2). MinIO, ClamAV y observabilidad se agregan con sus entregables de la Fase 0.

## Primer uso

Desde `backend/`:

```bash
cp dev/.env.example dev/.env
```

```bash
docker compose -f dev/compose.yaml --env-file dev/.env up -d
```

```bash
sh dev/check.sh
```

`check.sh` termina con "Entorno local listo." cuando todo está bien. En Windows ejecútalo desde Git Bash (necesita `sh` y `curl`). Keycloak puede tardar un minuto en el primer arranque.

Verifica las bases y sus dueños, que cada rol entre con su contraseña, los permisos (nada abierto a `PUBLIC`, `liv_app` sin `TEMP` ni acceso a `bff`, funciones nuevas sin `EXECUTE` para `PUBLIC`) y que Keycloak esté listo. La verificación del *realm* `liv` aparece como "pendiente" hasta que la tarea T07 lo agregue en `dev/keycloak/realm/`.

## Qué queda levantado

| Servicio | Dirección | Credenciales (ficticias, de `dev/.env`) |
|---|---|---|
| PostgreSQL | `localhost:5433` (5433 para no chocar con un PostgreSQL ya instalado) | Usuario `postgres` / `POSTGRES_PASSWORD`. La aplicación usa `liv_app` |
| Keycloak | <http://localhost:8180> (consola en `/admin`) | `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` |
| Salud de Keycloak | <http://localhost:9100/health/ready> | — |

Los puertos solo escuchan en `127.0.0.1` y se cambian en `dev/.env`. Las contraseñas de `.env.example` son solo para tu equipo: no las reutilices en ningún otro entorno.

## Roles de base de datos

Los crea `dev/initdb/01-roles-y-bases.sh` la primera vez (doc. 02 §5.2, ADR 0025). Ninguno tiene `BYPASSRLS` ni privilegios de administración.

| Rol | Inicio de sesión | Uso |
|---|---|---|
| `liv_owner` | Sí | Dueño de la base `liv`; solo lo usa Flyway |
| `liv_app` | Sí | Aplicación (API y worker); sin `TEMP` ni acceso al esquema `bff` |
| `liv_bff_owner` | Sí | Dueño del esquema `bff`; migraciones del BFF |
| `liv_bff` | Sí | Aplicación BFF |
| `liv_identity_lookup` | No | Dueño de la función de búsqueda de identidad (T10) |
| `liv_audit_platform` | No | Dueño de la función de rechazos de plataforma (T10) |
| `keycloak` | Sí | Base propia de Keycloak |

## Datos de prueba

`dev/seed/R__core_dev_seed.sql` tiene datos ficticios: Nariño, Ipiales y una "Administración de prueba B" (código `PRUEBA-B`, claramente falso) para las pruebas de aislamiento. Las tablas las crean las migraciones de la tarea T10, y Flyway aplica esta semilla solo con el perfil `dev`. Los UUID de las administraciones son fijos porque los usuarios de prueba de Keycloak (T07) los usan.

## Empezar de cero

Para borrar los datos y volver a crear roles y bases:

```bash
docker compose -f dev/compose.yaml --env-file dev/.env down -v
```
