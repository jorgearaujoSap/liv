# ADR-0025 · Dependencias entre módulos de la feature 001, endpoints internos y filas de plataforma

- **Estado:** Aceptado
- **Fecha:** 2026-11-01
- **Decisores:** arquitecto y responsable funcional (aprobación H2 de la feature 001)
- **Documentos relacionados:** `spec/features/001-inicio-de-sesion/plan.md` (pasos 7 a 9, "Datos, API y seguridad"); `docs/arquitectura/01` §3.2 y §10; `docs/arquitectura/02` §5.1, §5.2 y §9; ADR 0002, 0004, 0008

## Contexto

El inicio de sesión necesita que tres módulos colaboren sin formar ciclos (doc. 01 §3.2: "una dependencia nueva entre módulos exige un ADR"):

- `platform` valida el *token* y fija el contexto;
- `core` es dueño de los usuarios y de las sesiones;
- `audit` guarda la bitácora inmutable.

Además:

- el BFF debe notificar el inicio y el fin de cada sesión por endpoints que no se publican hacia el navegador;
- algunos eventos no pertenecen a ninguna administración (ingresos rechazados por una administración inexistente, eventos del proveedor de usuarios desconocidos), y la RLS de la auditoría debe poder guardarlos sin abrir una puerta general.

## Opciones consideradas

1. **`platform` llama a `core`** para resolver usuarios. Forma un ciclo con `core → platform`.
2. **Puertos en `platform`, implementados por `core`, y eventos de `core` escuchados por `audit`.** No hay ciclos.
3. **Un puerto `AuditRecorder` en `platform` que implementa `audit`.** También es válido, pero obliga a cada caso de uso a llamar a la auditoría a mano, contra doc. 02 §9 ("automático en la capa de aplicación").

## Decisión

Opción 2:

| Flecha | Motivo | Estado |
|---|---|---|
| `core → platform` | Implementa los puertos `UserDirectory` y usa la seguridad | Ya permitida |
| `audit → core` | Escucha `SessionStarted`, `SessionDenied`, `SessionEnded` y `UserProvisioned`. También la FK de base de datos `audit.audit_event.user_id → core.admin_user` | **Nueva** |
| `audit → platform` | Usa el puerto `IdentityProviderEvents` (copia de los eventos del proveedor) | **Nueva** |

- Ningún módulo depende de `audit`. El filtro por modalidades del recurso de capacidades será un puerto de `platform` implementado por `jurisdiction`. `ModularityTest` verifica todas las flechas.
- **Endpoints entre servicios** con el prefijo `/internal/v1`, que se aparta de `/api/v1` (doc. 01 §10):
  - no los enruta el BFF ni el balanceador;
  - exigen un *token* de *client credentials* del cliente `liv-bff-service` con la audiencia exclusiva `liv-internal`.
- **Filas de auditoría de plataforma** (`administration_id` NULL). Solo dos caminos pueden escribirlas:
  1. el importador de eventos del proveedor, un trabajo de plataforma marcado con `app.platform_job`;
  2. la función `audit.record_platform_session_denied(...)`. Es `SECURITY DEFINER`, pertenece a un rol sin inicio de sesión y solo inserta `SESSION_DENIED` de origen `LIV`.
- **Lectura de las filas de plataforma:** `liv_app` no las lee hasta el menú 9 (Auditoría e integraciones, Doc. 01 §3), que traerá su propio rol con otro ADR.
- **Roles de base de datos nuevos** (doc. 02 §5.2), todos sin `BYPASSRLS`:
  - `liv_identity_lookup`, sin inicio de sesión, dueño de `core.find_user_by_identity`, con `SELECT` solo sobre las columnas de identidad;
  - el dueño de `audit.record_platform_session_denied`, sin inicio de sesión;
  - `liv_bff_owner` (migraciones del BFF);
  - `liv_bff` (aplicación BFF).

## Consecuencias

- **Positivas:**
  - grafo de módulos acíclico y verificado;
  - la auditoría es automática;
  - los endpoints internos no se exponen;
  - las excepciones a la RLS son mínimas y se pueden probar.
- **Negativas o costos:**
  - dos flechas nuevas en el grafo;
  - dos funciones `SECURITY DEFINER` que hay que mantener endurecidas (`search_path` con `pg_temp` al final, `REVOKE` a `PUBLIC`).
- **Qué cambia en el repositorio:**
  - figura de doc. 01 §3.2;
  - tabla de roles de doc. 02 §5.2 y nota de excepción en §5.1;
  - convención de prefijos de doc. 01 §10.
