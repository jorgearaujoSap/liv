---
name: liv-endpoint
description: Crea o cambia un endpoint REST de la API de LIV con el flujo contract-first del proyecto - primero el OpenAPI y su versión, luego la interfaz generada, el controlador sin lógica, el caso de uso con su permiso, el adaptador y las pruebas en orden TDD. Úsala siempre que una tarea agregue, cambie o quite una ruta /api/**, un modelo de petición o respuesta, o un código de error de la API.
---

# Nuevo endpoint

Cambiar un contrato es un **cambio crítico** (`backend/contracts/**`, `AGENTS.md` de la raíz, §4.3): va por el SDD con su `plan.md` aprobado. Las reglas están en `backend/AGENTS.md`, `backend/README.md` › «Contratos de API» y `docs/arquitectura/04` §1 (CC‑06, CC‑07 y CC‑12). Esta *skill* dice **cómo** se aplican aquí.

Para las API de Spring Boot 4, Spring Security 7 o `openapi-generator` consulta la documentación oficial de la versión del `pom.xml`. No escribas de memoria: muchas API cambiaron desde Spring Boot 3.

## Ejemplo completo que se sigue

`GET /api/v1/me` (001-13), en el estilo simplificado de `core` y `platform`:

| Pieza | Archivo |
|---|---|
| Contrato | `contracts/openapi/core.yaml` (`operationId: getCurrentUser`) |
| Interfaz generada | `core.api.contract.CurrentUserApi`, en `target/generated-sources/openapi/`. No se edita ni se sube |
| Controlador | `src/main/java/co/com/sapenlinea/liv/core/api/CurrentUserController.java` |
| Caso de uso | `core/application/GetCurrentUser.java` |
| Puerto y adaptador | `core/application/UserProfiles.java` y `core/infrastructure/JdbcUserProfiles.java` |
| Pruebas | `src/test/java/.../contracts/OpenApiContractTest.java` (contrato), `core/CurrentUserTest.java` (respuesta), `platform/ApiAuthenticationTest.java` (401) y `platform/RequiresPermissionTest.java` (403) |

En los módulos hexagonales (`jurisdiction`, `valuation`, `tax`, `ledger`) se respeta además la fila «Hexagonal» de `backend/AGENTS.md`: `domain` en Java puro y `application` solo con puertos. Todavía no hay un ejemplo real de esos módulos. El primero lo define su feature, que también amplía esta *skill*.

## Pasos

1. **Contrato.**
   - Edita `contracts/openapi/<modulo>.yaml`. Si el endpoint es solo entre servicios, va en `<modulo>-internal.yaml`.
   - Rutas bajo `/api/v1`. Errores con el esquema `Problem` y su `code`.
   - Sube `info.version` (`X.Y.Z`): una corrección sube `Z`; un endpoint o campo nuevo compatible sube `Y`.
   - Un cambio incompatible sube `X` y necesita la etiqueta `contrato-incompatible` en el PR, que pone un humano. Compruébalo con `sh contracts/scripts/compare.sh origin/main`.
   - El contrato va en su propio PR del backend, antes que el frontend que lo usa (`AGENTS.md` de la raíz, §4.6).
2. **Prueba primero (rojo).**
   - En `OpenApiContractTest`, lo que el contrato debe declarar.
   - En una prueba como `CurrentUserTest` (`extends PlatformWebTest`), la respuesta y cada error del contrato. Sin permiso da 403 y sin sesión da 401, como en `RequiresPermissionTest` y `ApiAuthenticationTest`.
   - Cada `@DisplayName` en español con el código del criterio: `"001-13 · Responde identificador…"`.
   - Compila (`./mvnw -q compile`) para generar la interfaz del contrato nuevo. Después, la prueba debe compilar y **fallar por una aserción**, no por un error de compilación.
3. **Caso de uso** en `<modulo>/application/`.
   - Un `@Service` por acción, con nombre de verbo (`GetCurrentUser`). Solo constructor, sin `@Autowired` en campos.
   - **Permiso obligatorio:** cada método público lleva `@RequiresPermission("<código>")` o, si no necesita permiso, `@OpenUseCase("<motivo>")`. Sin ninguno de los dos, la autorización lo rechaza (`RequiresPermissionAuthorizationManager`). Los códigos de permiso son los de la matriz de roles (`platform.role_permission`, Doc. 01 §4).
   - El usuario y su administración se leen con `CurrentUser.current()`, que devuelve un `Optional`, nunca de la petición.
   - Fechas con el `Clock` inyectado. Dinero con `BigDecimal`, o con el tipo `Dinero` cuando una feature lo introduzca (ADR 0016). Los valores tributarios vienen de la configuración de jurisdicción (CC‑01).
4. **Persistencia.**
   - El caso de uso usa un puerto (interfaz en `application`). El adaptador va en `infrastructure` (`JdbcUserProfiles`: `JdbcClient`, SQL explícito y `@Transactional(readOnly = true)` en las lecturas).
   - Si hace falta una tabla o columna, primero la *skill* `liv-migracion`.
   - No generes claves primarias con `UUID.randomUUID()`, que da la versión 4: la tabla las genera con `DEFAULT uuidv7()` y el adaptador las lee con `RETURNING id`. El código de la feature 001 que todavía usa `UUID.randomUUID()` se corrige en una tarea aparte.
5. **Controlador** en `<modulo>/api/`.
   - Clase sin `public` que implementa la interfaz generada (`implements CurrentUserApi`), con `@RestController` y sin rutas propias: `ContractRulesTest` lo verifica.
   - Sin lógica (CC‑07): traduce la petición, llama al caso de uso y traduce la respuesta a los modelos generados. Máximo 200 líneas.
6. **Errores de negocio** (CC‑06): una excepción propia con el código de la regla (`R014`, `LQ-P04`), registrada en `platform/ErrorCodes` y traducida a Problem Details por `platform/web/ProblemDetailsHandler`. Nunca `RuntimeException` ni mensajes sin código.
7. **Verde y refactorizar:** `./mvnw verify` desde `backend/`, con Docker abierto.

## Después de integrar

`backend-main` publica la versión nueva como *release* `contract-<nombre>-vX.Y.Z`. El frontend la adopta en su propio PR: sube la versión en `frontend/api-spec/versions.json` y ejecuta `npm run api:update` (`frontend/scripts/api-update.mjs`).

## Antes del *commit*

- [ ] El contrato cambió primero, con su `info.version` subida, y `compare.sh` está en verde, o el PR lleva la etiqueta `contrato-incompatible`.
- [ ] No se editó nada generado (CC‑12) y el controlador solo implementa la interfaz.
- [ ] Cada método público del caso de uso tiene `@RequiresPermission` o `@OpenUseCase`.
- [ ] Hay pruebas del caso feliz, de cada error del contrato y de 401 y 403, escritas antes (commit `test:`).
- [ ] Ningún valor tributario ni `double`/`float`, y ningún `Instant.now()` en `application`.
- [ ] `./mvnw verify` está en verde.
