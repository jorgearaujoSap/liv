---
name: liv-migracion
description: Crea una migración Flyway del backend de LIV (API o BFF) con las convenciones del proyecto - nombre con marca de tiempo, aislamiento por administración con RLS, permisos mínimos para el rol de la aplicación, expand/contract y su prueba de integración primero. Úsala siempre que una tarea cree o cambie tablas, columnas, índices, restricciones, políticas RLS, permisos o funciones de PostgreSQL.
---

# Nueva migración

Una migración es un **cambio crítico**: va por el SDD con su `plan.md` aprobado (H2), y nunca por el flujo directo (`AGENTS.md` de la raíz, §4.3). Sigue las reglas de `docs/arquitectura/01` §9 (tabla de persistencia) y `docs/arquitectura/02` §5.1 (RLS). Esta *skill* dice **cómo** se aplican aquí.

## 0. Antes de escribir

1. La tabla o columna debe estar en el ERD (`docs/funcional/ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md`) o en los «Ajustes al modelo de datos» del plan aprobado. Si no está, **detente y pregunta**: el ERD es un documento funcional y no se cambia como efecto colateral.
2. Decide dónde va:
   - **API:** `backend/src/main/resources/db/migration/`;
   - **BFF:** `backend/bff/src/main/resources/db/migration/`, solo en el esquema `bff` (`backend/bff/AGENTS.md`).
3. Una migración que ya está en `main` está aplicada: **nunca** se edita. Cualquier corrección es una migración nueva.
4. Para la sintaxis de PostgreSQL 18 (la versión del proyecto) o de Flyway, consulta la documentación oficial de esa versión. No escribas de memoria.

## 1. Prueba primero (rojo)

Las pruebas de base de datos están en `backend/src/test/java/co/com/sapenlinea/liv/isolation/`. Usan `LivDatabase`, que arranca PostgreSQL con Testcontainers y aplica todas las migraciones. Escribe la prueba antes de la migración y comprueba que falla por el motivo correcto.

| Si la migración… | Prueba modelo | Qué agrega tu prueba |
|---|---|---|
| Crea una tabla con `administration_id` | `AdministrationIsolationTest` | Con el contexto de A no se ven ni se escriben filas de B; con el contexto vacío no se ve nada |
| Da permisos a `liv_app` | `LivAppGrantsTest` | Actualiza las columnas y permisos esperados. Un permiso de más debe hacer fallar la prueba |
| Hace una tabla de solo agregar (auditoría, movimientos del libro) | `AuditAppendOnlyTest` | `UPDATE` y `DELETE` fallan para `liv_app` |
| Cambia el esquema del BFF | `backend/bff/.../persistence/BffSchemaContractTest` | La forma esperada del esquema `bff` |

`LivAppGrantsTest` ya exige RLS y una política en **toda** tabla con `administration_id`. Una tabla sin `administration_id` no la cubre ninguna prueba automática: justifica en un comentario de la migración por qué no lleva RLS (modelo: `V202611010910__platform_roles_permissions.sql`).

Cada `@DisplayName` va en español y empieza con el código del criterio: `"001-17 · Con el contexto de A solo se ven las filas de A"`.

## 2. La migración (verde)

1. **Nombre:** `V<AAAAMMDDHHmm>__<modulo>_<descripcion>.sql`, con una marca posterior a la última de la carpeta. Ejemplo: `V202611010905__core_administrations_users.sql`.
2. **Encabezado:** un comentario con la feature, la tarea y el **porqué** (CC‑09). Modelo: `V202611031010__audit_import_cursor_complete.sql`.
3. **Tipos y nombres:** los de la tabla de `docs/arquitectura/01` §9. Son `snake_case` en inglés como el ERD, `NUMERIC` para dinero y tasas, nunca `float`, `DATE` para fechas de negocio, `TIMESTAMPTZ` para instantes y `CHECK` con los valores de cada estado.
4. **Aislamiento.** Modelo: `V202611010905__core_administrations_users.sql`.
   - Las tablas con datos de una administración tienen `administration_id NOT NULL`.
   - Sus FK son compuestas `(…, administration_id)` y se apoyan en un `UNIQUE` compuesto del padre, para que una FK no mezcle administraciones.
   - `ALTER TABLE … ENABLE ROW LEVEL SECURITY` y una política `TO liv_app` con `USING` (y `WITH CHECK` si escribe) sobre `NULLIF(current_setting('app.administration_id', true), '')::uuid`.
   - Si la tabla tiene una máquina de estados, una política por operación. Ejemplo: `core.user_session`, que no se reabre una vez cerrada.
   - La aplicación fija ese valor en cada transacción (`platform/persistence/AdministrationJpaDialect`): la migración no lo toca.
5. **Permisos mínimos.**
   - Solo lo que usa la aplicación: `GRANT SELECT, INSERT` sobre la tabla y `UPDATE` solo sobre las columnas que la aplicación cambia (modelo: `V202611010905`).
   - Nada de `UPDATE` ni `DELETE` sobre auditoría, movimientos del libro ni catálogos publicados (doc. 01 §9, «Inmutabilidad»).
   - Los saldos nunca se guardan como dato editable (`AGENTS.md` de la raíz, §5.1).
   - Los roles los crea la infraestructura (`backend/dev/initdb/`): la migración no crea roles.
6. **Funciones `SECURITY DEFINER`:** solo con el patrón de `core.find_user_by_identity` (`V202611010905`). Llevan un rol propio sin inicio de sesión y sin `BYPASSRLS`, `search_path` fijo con `pg_temp` al final y `REVOKE` de `PUBLIC`. Antes del PR, pide la lente de riesgo a `liv-revisor-critico`.
7. **Expand/contract:** nunca renombres ni borres en un paso algo que usa la versión en ejecución. Son tres pasos:
   1. Una migración **expand** agrega lo nuevo sin romper lo viejo: una columna que acepta nulos o tiene valor por defecto, y un `UPDATE` que copia los datos existentes.
   2. Se despliega la versión de la aplicación que usa lo nuevo.
   3. En una versión posterior, una migración **contract** cierra el cambio: `NOT NULL` y se borra lo viejo.

## 3. Verificar

- Desde `backend/`, con Docker abierto: `./mvnw verify`. Aplica todas las migraciones desde cero y corre las pruebas.
- Si el cambio altera tablas que ya tenían datos, agrega a la prueba un caso con datos anteriores a la migración (`docs/arquitectura/04`, prueba de actualización).

## 4. Antes del *commit*

- [ ] La tabla o columna está en el ERD o en el plan aprobado.
- [ ] El nombre tiene una marca de tiempo nueva y no se editó ninguna migración ya integrada.
- [ ] Toda tabla con `administration_id` tiene RLS, una política `TO liv_app` y FK compuestas.
- [ ] Los permisos de `liv_app` son los mínimos y `LivAppGrantsTest` los refleja.
- [ ] Ningún valor tributario quedó en la migración (tarifas, rangos, fechas): eso es configuración de jurisdicción (CC‑01).
- [ ] Las pruebas se escribieron antes (commit `test:`) y `./mvnw verify` está en verde.
