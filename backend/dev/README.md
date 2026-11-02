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

Verifica las bases y sus dueños, que cada rol entre con su contraseña, los permisos (nada abierto a `PUBLIC`, `liv_app` sin `TEMP` ni acceso a `bff`, funciones nuevas sin `EXECUTE` para `PUBLIC`), que Keycloak esté listo y que el *realm* `liv` esté aplicado: el servicio `keycloak-config` terminó bien, el *realm* publica su configuración OpenID, `liv-bff-service` obtiene un *token* con audiencia `liv-internal`, `liv-audit-importer` lee los eventos, el rol por defecto no da `offline_access` y nadie entra con usuario y contraseña por API.

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

## *Realm* `liv` y usuarios de prueba

Al levantar el entorno, el servicio `keycloak-config` aplica el *realm* y termina: primero la configuración base de `identity/config/base/` y luego la capa de desarrollo de `dev/keycloak/` (ver `identity/README.md`). Después de cambiar esos archivos, o si `check.sh` dice que la aplicación falló, reaplícalo:

```bash
docker compose -f dev/compose.yaml --env-file dev/.env up -d --force-recreate keycloak-config
```

Todos los usuarios tienen la contraseña `Liv-Dev-2026!` y la administración de Ipiales, salvo `dev.consulta.b`. Los que tienen un rol con segundo factor deben configurar TOTP en su primer ingreso con una aplicación de autenticación.

| Usuario | Rol | TOTP |
|---|---|---|
| `dev.admin.sistema` | `ADMIN_SISTEMA` | Sí |
| `dev.cargador.tablas` | `CARGADOR_TABLAS` | No |
| `dev.aprobador.tablas` | `APROBADOR_TABLAS` | Sí |
| `dev.liquidador` | `LIQUIDADOR` | No |
| `dev.aprobador.liquidacion` | `APROBADOR_LIQUIDACION` | Sí |
| `dev.recaudo` | `RECAUDO` | No |
| `dev.tesoreria` | `TESORERIA` | Sí |
| `dev.aprobador.recaudo` | `APROBADOR_RECAUDO` | Sí |
| `dev.juridico` | `JURIDICO` | Sí |
| `dev.consulta` | `CONSULTA` | No |
| `dev.auditor` | `AUDITOR` | Sí |
| `dev.consulta.b` | `CONSULTA`, administración de prueba B | No |
| `dev.desactivado` | `CONSULTA`, usuario desactivado | — |

Para volver a pedir el TOTP a un usuario, borra su credencial OTP en la consola de Keycloak (*Users* › usuario › *Credentials*) o empieza de cero.

## Datos de prueba

`dev/seed/R__core_dev_seed.sql` tiene datos ficticios: Nariño, Ipiales y una "Administración de prueba B" (código `PRUEBA-B`, claramente falso) para las pruebas de aislamiento. Las tablas las crean las migraciones de la tarea T10, y Flyway aplica esta semilla solo con el perfil `dev`. Los UUID de las administraciones son fijos porque los usuarios de prueba de Keycloak (T07) los usan.

## Empezar de cero

Para borrar los datos y volver a crear roles y bases:

```bash
docker compose -f dev/compose.yaml --env-file dev/.env down -v
```
