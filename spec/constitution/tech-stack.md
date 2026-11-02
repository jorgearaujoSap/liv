# Tech stack y convenciones

_Cómo está construido el proyecto y las reglas que todo el código debe respetar. Es la referencia técnica que ningún plan de feature debería contradecir._

> Resumen de la serie `docs/arquitectura/00`–`05` (versión 1.0, **aprobada**) y de `AGENTS.md`. El detalle y la justificación de cada punto están allí; si hay diferencia, se corrige este archivo.

## Tecnologías

- **Lenguaje:** Java 21 LTS (backend) · TypeScript estricto (frontend).
- **Framework / runtime:** Spring Boot 4.x (Maven, un artefacto) con Spring Modulith, Spring Security y Spring Batch · Angular ≥ 21 (*standalone*, *signals*) con Tailwind CSS y la librería propia `liv-components` (paquete npm público, proyecto `ui/`).
- **Base de datos:** PostgreSQL 18 con Flyway; JPA para escritura de agregados y jOOQ para consultas, reportes y procesos masivos; RLS por administración.
- **Tests:** JUnit 5, AssertJ, jqwik, Testcontainers, PIT (mutación) · Vitest + Angular Testing Library · Playwright + axe‑core · k6.
- **Despliegue:** AWS (ECS sobre Fargate, RDS for PostgreSQL, S3, Secrets Manager, KMS); Docker Compose en desarrollo local. Identidad con Keycloak propio. CI/CD en GitHub Actions, portable a Bitbucket.

## Archivos / módulos clave

_Estado actual: no hay código. La estructura objetivo está en `docs/arquitectura/01` §14._

- `docs/funcional/` — documentos `01_…md` a `04_…md` y `ERD_…md`: especificación funcional y modelo de datos (fuente de verdad de las reglas).
- `docs/funcional/anexos/` — configuración de jurisdicción (`config_jurisdiccion_*.yaml`: plantilla e Ipiales, DANE 52356) y prevalidación de las tablas 2026.
- `docs/arquitectura/` — decisiones, arquitectura, seguridad, operación, calidad y gobierno de agentes.
- `spec/` — esta carpeta: constitución y features SDD.
- `AGENTS.md` — reglas de trabajo para cualquier agente; `ESTADO.md` — estado del proyecto y bitácora.
- `backend/`, `frontend/` y `ui/`: **proyectos autónomos y separables** (futuros repositorios propios). Backend y frontend se comunican solo por el OpenAPI de `backend/contracts/`; el frontend recibe la librería como paquete npm con versión exacta. Más adelante, en la raíz: `e2e/`, `perf/`, `infra/` y `tools/` en la raíz. Estructura y reglas IN‑01 a IN‑11 en `docs/arquitectura/01` §14.

## Comandos

_Cada proyecto se construye desde su propia carpeta; el detalle está en su `README.md`._

| Para | Backend (`cd backend`) | Frontend (`cd frontend`) | Librería (`cd ui`) |
|---|---|---|---|
| Construir y probar todo | `./mvnw verify` | `npm run lint`, `npm test`, `npm run build` | `npm run lint`, `npm run pack` |
| Solo pruebas | `./mvnw test` | `npm test` | — (con el primer componente) |
| Formato | `./mvnw spotless:apply` / `spotless:check` | `npm run format` / `npm run format:check` | `npm run format` / `npm run format:check` |
| Arrancar en local / publicar | `./mvnw spring-boot:run` | `npm start` | `npm run release` (solo un humano) |

El entorno local completo con Docker Compose llega con F0‑06.

## Modelo de datos / dominio

- **Esquemas** — `core`, `documents`, `valuation`, `tax`, `ledger`, `portfolio`, `collection`, `enforcement`, `audit` (ERD); un módulo de backend por esquema y un esquema por módulo.
- **`administration_id`** — está en toda tabla con datos de una administración; activa la RLS y las FK compuestas impiden mezclar administraciones.
- **Configuración de jurisdicción** — montos en pesos, tasas como fracción (`0.015` = 1,5 %), tablas del Ministerio en miles (`multiplicador_tablas: 1000`); validada por CJ01–CJ14 (Doc. 03 §5.4). Modalidades de competencia A, B, C; Ipiales empieza con A + C.
- **Liquidación (`tax_assessment`)** — guarda `jurisdiction_config_id`, `rule_version`, la versión del catálogo y la traza de cálculo; puede terminar en `EXCEPCION` con un código `LQ-Exx`.
- **Saldo de una obligación** — nunca se guarda: es `Σ signed_amount` de sus movimientos por componente (TAX / INTEREST / PENALTY).
- **Códigos de regla** — `R0xx`, `F0xx`, `LQ-*`, `LM-xx`, `CJxx`, `CA-x.xx`: identifican cada regla en documentos, pruebas, errores de la API y comentarios del código.

## Convenciones

- Código, tablas y *tokens* en inglés (como el ERD); documentación, interfaz y nombres visibles de las pruebas en español; glosario en `docs/arquitectura/01` §15.
- Arquitectura hexagonal en los módulos con reglas de negocio; los módulos se comunican por su paquete raíz (fachadas y eventos); las capas `api`, `application`, `domain` e `infrastructure` son internas.
- Dinero con `BigDecimal` (tipo `Dinero`); prohibidos `float` y `double` en dominio; el reloj se inyecta.
- Errores de negocio con su código de regla; respuestas de la API en formato Problem Details (RFC 9457).
- API *contract‑first* con OpenAPI; código generado no se edita.
- Migraciones Flyway con nombre `V<AAAAMMDDHHmm>__<modulo>_<descripcion>.sql`; una migración aplicada no se edita.
- TDD obligatorio; cada prueba de regla lleva el código de la regla.
- Conventional Commits, *trunk‑based development*, PR pequeños (≤ ~400 líneas).

## Estilo visual

- **Componentes:** solo los de `liv-components`; las pantallas no crean componentes visuales propios ni redefinen sus estilos.
- **Tokens:** los estilos locales del archivo de Figma LIV‑Diseño (28 de color, 13 de texto, 5 de efecto), con nombres iguales a los del código (`text/primary`, `body/md`, `elevation/md`…). Ningún color, texto o sombra sin *token*.
- **Tipografías:** Inter para la interfaz; JetBrains Mono para códigos de regla, referencias y hashes.
- **Estilos:** Tailwind CSS en el `.component.css` de cada componente; sin atributo `style`, sin utilidades en la plantilla, sin `::ng-deep`.
- **Diseño previo:** toda pantalla nueva tiene un diseño aprobado en la página `03 Pantallas` de Figma, salvo la excepción de reutilización de estructura (`AGENTS.md` §6.2).
- **Accesibilidad y localización:** WCAG 2.1 AA; formato `es-CO` (moneda COP, `dd/mm/aaaa`).

## Límites duros

La lista completa es `AGENTS.md` §4.5. Los más importantes:

- No escribir valores tributarios en el código.
- No agregar dependencias sin ADR ni aprobación humana.
- No editar una migración aplicada, ni cambiar seguridad, RLS, CI o umbrales de calidad sin aprobación.
- No usar datos reales de contribuyentes ni credenciales de entornos reales.
- No crear pantallas sin diseño aprobado ni componentes visuales fuera de `liv-components`.
- No integrar a `main`, publicar ni desplegar: solo un humano.
