# LIV — Impuesto vehicular

Sistema de liquidación y recaudo del impuesto anual de vehículos automotores de Colombia (Ley 488 de 1998), configurable por jurisdicción. Primer caso: municipio de Ipiales (Nariño).

> **Estado:** Fase 0 — Fundaciones. Arquitectura aprobada y esqueletos de backend y frontend creados, todavía sin funcionalidad de negocio. El estado al día está en [`ESTADO.md`](ESTADO.md).

## Mapa del repositorio

```
/
├── README.md                ← este archivo
├── AGENTS.md                ← reglas y metodología para cualquier agente de IA (y para el equipo)
├── CLAUDE.md                ← importa AGENTS.md + detalles de Claude Code
├── ESTADO.md                ← estado del proyecto, decisiones y bitácora
├── .claude/skills/          ← skills del proyecto (procedimientos para personas y agentes)
├── backend/                 ← proyecto autónomo: Java 21 + Spring Boot (ver backend/README.md)
├── frontend/                ← proyecto autónomo: Angular 21; instala liv-components desde npm (ver frontend/README.md)
├── ui/                      ← proyecto autónomo: librería liv-components, publicada en npm (ver ui/README.md)
├── docs/
│   ├── funcional/           ← especificación funcional: documentos 01–04 y ERD
│   │   └── anexos/          ← configuración de jurisdicción (YAML) y prevalidación de tablas 2026 (Excel)
│   └── arquitectura/        ← decisiones, arquitectura, seguridad, operación, calidad y gobierno de agentes (00–05)
└── spec/                    ← desarrollo guiado por especificaciones (SDD)
    ├── constitution/        ← misión, stack y convenciones, roadmap
    └── features/            ← una carpeta por feature (spec, plan, tareas)
```

## Monorepo separable

`backend/` (Java + Spring Boot), `frontend/` (Angular) y `ui/` (librería `liv-components`) son **proyectos autónomos** que después pasarán a repositorios propios. No comparten archivos, herramientas ni rutas: cada uno se construye desde su carpeta, con sus propios comandos. El backend y el frontend se comunican solo por el OpenAPI que publica el backend; el frontend recibe la librería como paquete de npm con versión fijada. Este repositorio conservará la documentación, `spec/`, las pruebas de sistema (`e2e/`, `perf/`), la infraestructura compartida (`infra/`) y las herramientas (`tools/`).

Estructura y reglas: [`docs/arquitectura/01_Arquitectura_de_Software.md`](docs/arquitectura/01_Arquitectura_de_Software.md) §14.

## Por dónde empezar

1. [`ESTADO.md`](ESTADO.md) — dónde está el proyecto.
2. [`AGENTS.md`](AGENTS.md) — cómo se trabaja aquí (también para personas).
3. [`docs/funcional/`](docs/funcional/) — qué hace el sistema.
4. [`docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md`](docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md) — decisiones técnicas y hoja de ruta.
5. [`spec/README.md`](spec/README.md) — cómo se especifica y desarrolla una feature.

## Ejecutar LIV en local

Paso a paso para levantar todo el sistema en tu equipo: entorno (PostgreSQL y Keycloak), API, BFF y frontend. Cada proyecto tiene el detalle en su `README.md`; aquí solo va el orden y lo mínimo para que funcione.

### Requisitos

- Docker Desktop, abierto antes de empezar.
- JDK 21. No hace falta instalar Maven: cada proyecto trae su *wrapper* (`./mvnw`).
- Node 22.12 o superior dentro de la línea 22, o Node 24 o superior.
- En Windows, **Git Bash** para todos los comandos (los *scripts* usan `sh`).

### Qué queda levantado

| Servicio | Dirección | Detalle |
|---|---|---|
| PostgreSQL | `localhost:5433` | [`backend/dev/README.md`](backend/dev/README.md) |
| Keycloak | <http://localhost:8180> (consola en `/admin`) | [`backend/dev/README.md`](backend/dev/README.md) y [`backend/identity/README.md`](backend/identity/README.md) |
| API (`liv-api`) | <http://localhost:8080> | [`backend/README.md`](backend/README.md) |
| BFF (`liv-bff`) | <http://localhost:8081> | [`backend/bff/README.md`](backend/bff/README.md) |
| Frontend | **<http://localhost:4200>**, la única dirección que se abre en el navegador | [`frontend/README.md`](frontend/README.md) |

### Atajo: un solo comando

Desde la raíz del repositorio (en Windows, con Git Bash; también en macOS o Linux), con `main` al día y Docker Desktop abierto:

```bash
sh tools/dev/iniciar.sh
```

Hace los pasos 1 a 4 de abajo, en orden: verifica los requisitos y que los puertos estén libres, levanta cada servicio y espera a que esté listo. Si algo falla, o lo interrumpes con Ctrl+C, muestra el final del registro y detiene lo que ya había arrancado. La primera vez tarda varios minutos. Los servicios siguen corriendo aunque cierres la terminal. Los registros quedan en `tools/dev/registros/`. Al terminar, abre <http://localhost:4200> (paso 5).

Para detener todo, conservando los datos:

```bash
sh tools/dev/detener.sh
```

Con `--borrar-datos` también borra los volúmenes (pide confirmación), y la próxima vez todo empieza de cero. `detener.sh` solo termina lo que levantó `iniciar.sh`: nunca otro programa que use el mismo puerto, ni un entorno que levantaste a mano. Para la prueba de navegador del aviso de inactividad, `sh tools/dev/iniciar.sh --sesion-corta` levanta el BFF con una sesión de 150 s (`e2e/README.md`).

### Paso a paso

Lo mismo, a mano: se necesitan cuatro terminales, una por paso, todas desde la raíz del repositorio y con `main` al día.

### 1. Entorno local (terminal 1)

La primera vez, crea el archivo de variables desde `backend/`:

```bash
cd backend
```

```bash
cp dev/.env.example dev/.env
```

Levanta PostgreSQL y Keycloak, y verifica:

```bash
docker compose -f dev/compose.yaml --env-file dev/.env up -d --build
```

```bash
sh dev/check.sh
```

`--build` construye la imagen de Keycloak con el tema `liv`; la primera vez tarda unos minutos. `check.sh` debe terminar con **«Entorno local listo.»**. Keycloak puede tardar un minuto en estar listo.

### 2. API, puerto 8080 (terminal 2)

```bash
cd backend
```

```bash
set -a; . dev/.env; set +a
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

La primera vez migra la base y carga los datos de prueba. Está lista cuando aparece `Started LivApplication`.

### 3. BFF, puerto 8081 (terminal 3)

Usa las mismas variables de `backend/dev/.env`:

```bash
cd backend/bff
```

```bash
set -a; . ../dev/.env; set +a
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Migra el esquema `bff`. Está listo cuando aparece `Started LivBffApplication`.

### 4. Frontend, puerto 4200 (terminal 4)

```bash
cd frontend
```

```bash
npm install
```

```bash
npm start
```

### 5. Entrar

Abre **<http://localhost:4200>**. La aplicación te lleva a la pantalla de acceso de Keycloak y, al entrar, a Inicio. Si en vez de eso ves un 404 en `/bff/login`, mira «Problemas conocidos».

- Todos los usuarios de prueba tienen la contraseña `Liv-Dev-2026!`. Para empezar, usa **`dev.consulta`**, que no pide segundo factor. Para entrar como administrador, usa **`dev.admin.sistema`**: en su primer ingreso pide configurar el TOTP con una aplicación de autenticación (Google Authenticator, Microsoft Authenticator o similar).
- La lista completa de usuarios, roles y administraciones está en [`backend/dev/README.md`](backend/dev/README.md) › «*Realm* `liv` y usuarios de prueba».

### Crear otro usuario

Los usuarios se crean en Keycloak; LIV los registra en su primer ingreso.

1. Abre <http://localhost:8180/admin> con `KEYCLOAK_ADMIN_USER` y `KEYCLOAK_ADMIN_PASSWORD` de `backend/dev/.env`, y cambia al *realm* **`liv`**.
2. **Users › Add user.** Keycloak exige el nombre y el apellido, y LIV además el correo: sin él, rechaza el primer ingreso. En **Administración (UUID)** va la de Ipiales, `10000000-0000-0000-0000-000000052356`, o la de prueba B, `10000000-0000-0000-0000-0000000b0001`.
3. **Credentials › Set password**, con **Temporary** desactivado.
4. **Role mapping › Assign role**, filtrando por *realm roles*. El administrador es `ADMIN_SISTEMA`. Los roles que piden segundo factor están en la tabla de usuarios de [`backend/dev/README.md`](backend/dev/README.md).

Un usuario creado así se pierde al recrear el entorno (`down -v`). Para que sea permanente y lo tenga todo el equipo, va en `backend/dev/keycloak/liv-realm-dev.yaml`, con un PR.

### Detener

Detén la API, el BFF y el frontend con `Ctrl+C` en su terminal. Después, desde `backend/`:

```bash
docker compose -f dev/compose.yaml --env-file dev/.env down
```

Sin `-v` se conservan los datos. Con `-v` se borran y la próxima vez todo empieza de cero.

### Problemas conocidos

| Síntoma | Causa | Solución |
|---|---|---|
| La API no arranca: «`liv_owner` debe poder asumir `liv_identity_lookup` y `liv_audit_platform`» | El volumen de PostgreSQL es anterior a esos roles. `check.sh` todavía no lo detecta | Recrea el entorno con `docker compose -f dev/compose.yaml --env-file dev/.env down -v` y vuelve al paso 1, o ejecuta como `postgres` el `GRANT` de [`backend/dev/README.md`](backend/dev/README.md) › «Roles de base de datos» |
| Al escribir `localhost:4200` en la barra del navegador aparece un 404 («Whitelabel Error Page») en `/bff/login` | El frontend es anterior al arreglo de la precarga del navegador (PR #97): arrancaba durante la precarga y el BFF no abre un inicio de sesión desde ella | Actualiza `main` y vuelve a ejecutar `npm start`. Mientras tanto, pulsa **F5** en esa pestaña |
| Keycloak responde «Invalid redirect uri», o al volver no hay sesión | Se entró por `127.0.0.1` en lugar de `localhost`. El *realm* solo acepta el regreso a `http://localhost:4200` (`LIV_BFF_BASE_URL`), y la cookie de sesión no viaja entre `127.0.0.1` y `localhost` | Usa siempre `http://localhost:4200` |
| Un puerto ya está en uso | Otro programa usa 5433, 8180, 8080, 8081 o 4200 | Cierra ese programa. Los de PostgreSQL y Keycloak se cambian en `backend/dev/.env`; los de la API, el BFF y el frontend, como dice el `README.md` de cada uno |
| El tema de Keycloak no muestra un cambio reciente | La imagen de Keycloak es la anterior | Vuelve a levantar el entorno con `--build` |

Diseño: archivo de Figma [LIV‑Diseño](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o).
