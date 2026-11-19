# AGENTS.md — backend

Reglas para cualquier agente de IA (y para el equipo) que trabaje en el backend. **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD por rebanadas verticales (ADR 0040), pruebas, aprobaciones (H1 de la spec, visto bueno de cada rebanada y H3), *skills* y `ESTADO.md`. Si este proyecto ya está en su propio repositorio, ese documento está en el repositorio de plataforma.

## Antes de tocar código

1. La tarea es un ajuste (sin *issue*) o una rebanada con su *issue* en «Lista», de una feature con su `spec.md` aprobado (`spec/features/NNN-…/` en el repositorio de plataforma) o suelta. Los tamaños de trabajo y las zonas rojas están en el `AGENTS.md` del repositorio de plataforma (§4.3).
2. Lee la sección del documento funcional y del documento de arquitectura que cita la feature.
3. Trabaja solo en lo de la rebanada o del ajuste. Lo demás que encuentres se le propone a la persona como *issue* (*skill* `liv-backlog`).

## Reglas del backend

| Regla | Detalle | Verificación |
|---|---|---|
| Independencia | Ningún archivo de este proyecto referencia algo fuera de `backend/` | Revisión (IN‑01) |
| Catálogo de módulos | Paquetes raíz: `shared`, `platform`, `core`, `documents`, `jurisdiction`, `valuation`, `tax`, `ledger`, `audit`, `reporting`. Un módulo nuevo exige ADR y actualizar el catálogo | `ModuleCatalogTest` |
| Capas | Los módulos de negocio solo usan `api`, `application`, `domain` e `infrastructure` | `ModuleCatalogTest` |
| Fronteras | Un módulo usa a otro solo por su paquete raíz (fachadas y eventos), sin ciclos | `ModularityTest` (Spring Modulith) |
| Controladores | `api` solo llama a `application`; nunca a `infrastructure` | `ArchitectureRulesTest` |
| Hexagonal | En `jurisdiction`, `valuation`, `tax`, `ledger` y `shared`, `domain` es Java puro (sin Spring, JPA ni jOOQ) y `application` usa puertos, nunca `infrastructure`. Las entidades JPA van en `infrastructure/persistence` | `ArchitectureRulesTest` |
| Registros | En `core`, `documents`, `audit` y `reporting` se permite el estilo simplificado: entidades JPA en `domain/entity` y servicios que usan repositorios | Revisión |
| Dinero | Nunca `double` ni `float` en `domain` ni `application` de los módulos hexagonales: tipo `Dinero` sobre `BigDecimal` (ADR 0016) | `ArchitectureRulesTest` |
| Reloj | Prohibido `LocalDate.now()`, `Instant.now()` y similares en `domain` y `application`: se inyecta un `Clock` | `ArchitectureRulesTest` |
| Inyección | Solo por constructor; nunca `@Autowired` en campos | `ArchitectureRulesTest` |
| Tamaño | Clases de máximo 400 líneas, controladores de 200, y máximo 8 dependencias por constructor. Sin excepciones: si crece, se divide | `CodeSizeGuardTest` |
| Reglas tributarias | Ningún valor tributario (tarifa, rango, porcentaje, fecha, redondeo) en el código: vienen de la configuración de jurisdicción | Revisión (CC‑01) |
| Zonas rojas | Rutas: `tax`, `ledger`, `jurisdiction`, `audit`, la seguridad, la autorización y la persistencia de `platform`, las migraciones, los contratos, el `pom.xml`, el BFF y la identidad. Temas, estén donde estén: la autenticación, la autorización, las políticas RLS y los roles (un permiso nuevo en `core`, por ejemplo). Tampoco es un ajuste el código que implementa o corrige una regla con código, esté donde esté. Lista completa: `AGENTS.md` de la raíz, §4.3. Un cambio de código ahí nunca es un ajuste, y cada PR pasa la revisión adversarial | Revisión adversarial |
| Contrato | API *contract‑first*: primero el OpenAPI, luego el código (ADR 0005) | Revisión |
| Migraciones | Flyway en `src/main/resources/db/migration`; una migración aplicada no se edita; credenciales solo por variables de entorno | Revisión |
| Idioma | Código en inglés; nombres visibles de las pruebas (`@DisplayName`) en español con el código de la regla | Revisión (ADR 0017) |
| Formato | Google Java Format (2 espacios) | `./mvnw verify` (Spotless) |
| Pruebas | Toda rebanada entra con sus pruebas en el mismo PR. **Prueba primero** (escrita antes y vista fallar, sin *commit* aparte) en el código que implementa una regla con código (`AGENTS.md` de la raíz, §5.3) y en los casos *golden* (§4.1, principio 5) | Revisión del PR; revisión adversarial si toca una regla o una zona roja |
| Dependencias | Una dependencia nueva en el `pom.xml` exige ADR | Revisión |

## Antes de decir "terminado"

- `./mvnw verify` en verde (formato, compilación y todas las pruebas), o se reporta qué falló.
- El `CHANGELOG.md` de este proyecto tiene su entrada y la tarjeta del *issue* está al día (*skill* `liv-backlog`).
- Un PR toca solo este proyecto: no cambia `spec.md`, el glosario ni `ESTADO.md`, que solo cambian en los PR de la raíz (`AGENTS.md` de la raíz, §4.2, «Quién escribe en la raíz»).

## Skills

Las *skills* de código del backend están en `backend/.claude/skills/`. Siguen el código real de la feature 001. Antes de la tarea que describen, léelas, aunque tu agente no las cargue solo:

| *Skill* | Úsala para |
|---|---|
| `liv-migracion` | Crear o cambiar tablas, columnas, políticas RLS, permisos o funciones de PostgreSQL |
| `liv-endpoint` | Agregar o cambiar un endpoint `/api/**`: contrato, caso de uso con su permiso, controlador y pruebas |

Las de regla de validación y paso del motor se crearán con la primera feature de los Módulos 1 y 3, a partir de su código real. Para el resto, usa las *skills* de proceso del repositorio de plataforma (hoy, `.claude/skills/` en la raíz del monorepo).
