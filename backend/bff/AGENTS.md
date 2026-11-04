# AGENTS.md — BFF

Reglas para cualquier agente de IA (y para el equipo) que trabaje en el BFF (`liv-bff`). **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD, TDD, aprobaciones H1–H3, *skills* y `ESTADO.md`. Todo el BFF es ruta crítica (`backend/bff/**`): cada cambio lleva la revisión adversarial y las cuatro lentes.

## Antes de tocar código

1. La tarea debe venir de una feature aprobada (`spec/features/NNN-…/` en el repositorio de plataforma) o ser un cambio trivial.
2. Lee la sección del documento funcional y del de arquitectura que cita la feature (seguridad: doc. 02 §3 y §8).
3. Trabaja solo en los archivos que declara la tarea.

## Reglas del BFF

| Regla | Detalle | Verificación |
|---|---|---|
| Independencia | Ningún archivo de este proyecto referencia algo fuera de `backend/bff/`. Con la API se habla solo por HTTP | Revisión (IN‑01) |
| Solo OIDC estándar | Sin librerías ni API propias de Keycloak: Spring Security OAuth2 Client contra cualquier proveedor OIDC (001-20) | `ArchitectureRulesTest` |
| Sin *tokens* en el navegador | Ninguna respuesta lleva un JWT, un *refresh token* ni un `id_token_hint`; los *tokens* viven cifrados en la sesión del servidor | Pruebas de las tareas de sesión |
| Paquetes | Todo bajo `co.com.sapenlinea.liv.bff`, en `login`, `logout`, `session` o `web`. Un paquete nuevo se agrega al catálogo en el mismo PR | `PackageCatalogTest` |
| Inyección | Solo por constructor; nunca `@Autowired` en campos | `ArchitectureRulesTest` |
| Tamaño | Clases de máximo 400 líneas, controladores de 200, y máximo 8 dependencias por constructor. Sin excepciones: si crece, se divide | `CodeSizeGuardTest` |
| Base de datos | Solo el esquema `bff`. La aplicación usa `liv_bff`; las migraciones, `liv_bff_owner`. Una migración aplicada no se edita; credenciales solo por variables de entorno | `BffSchemaContractTest` y revisión |
| Contrato | *Contract‑first*: primero `contracts/openapi/bff.yaml`, luego el código (ADR 0005) | Revisión |
| Idioma | Código en inglés; nombres visibles de las pruebas (`@DisplayName`) en español, con el código de la regla cuando lo hay | Revisión (ADR 0017) |
| Formato | Google Java Format (2 espacios) | `./mvnw verify` (Spotless) |
| TDD | Commit `test:` (prueba en rojo) antes del `feat:`/`fix:` | Revisión del PR |
| Dependencias | Solo las del ADR 0023 para el BFF. Una dependencia nueva en el `pom.xml` exige ADR | Revisión |

## Antes de decir "terminado"

- `./mvnw verify` en verde (formato, compilación y todas las pruebas), o se reporta qué falló.
- La feature y `ESTADO.md` del repositorio de plataforma están actualizados.
- Un PR toca solo este proyecto.
