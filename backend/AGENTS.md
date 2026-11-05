# AGENTS.md — backend

Reglas para cualquier agente de IA (y para el equipo) que trabaje en el backend. **Complementan** al `AGENTS.md` del repositorio de plataforma y documentación (hoy, la raíz del monorepo `liv`), que define la metodología completa: SDD, TDD, aprobaciones H1–H3, *skills* y `ESTADO.md`. Si este proyecto ya está en su propio repositorio, ese documento está en el repositorio de plataforma.

## Antes de tocar código

1. La tarea debe venir de una feature aprobada (`spec/features/NNN-…/` en el repositorio de plataforma) o ser un cambio del flujo directo, según la evaluación previa del `AGENTS.md` del repositorio de plataforma (§4.3).
2. Lee la sección del documento funcional y del documento de arquitectura que cita la feature.
3. Trabaja solo en los archivos que declara la tarea.

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
| Contrato | API *contract‑first*: primero el OpenAPI, luego el código (ADR 0005) | Revisión |
| Migraciones | Flyway en `src/main/resources/db/migration`; una migración aplicada no se edita; credenciales solo por variables de entorno | Revisión |
| Idioma | Código en inglés; nombres visibles de las pruebas (`@DisplayName`) en español con el código de la regla | Revisión (ADR 0017) |
| Formato | Google Java Format (2 espacios) | `./mvnw verify` (Spotless) |
| TDD | Commit `test:` (prueba en rojo) antes del `feat:`/`fix:` | Revisión del PR |
| Dependencias | Una dependencia nueva en el `pom.xml` exige ADR | Revisión |

## Antes de decir "terminado"

- `./mvnw verify` en verde (formato, compilación y todas las pruebas), o se reporta qué falló.
- La feature y `ESTADO.md` del repositorio de plataforma están actualizados.
- Un PR toca solo este proyecto.

## Skills

Las *skills* de código del backend (caso de uso, regla de validación, paso del motor, migración, endpoint) se agregarán en `backend/.claude/skills/` cuando existan los primeros casos reales. Mientras tanto, usa las *skills* de proceso del repositorio de plataforma.
