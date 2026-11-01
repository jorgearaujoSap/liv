# Arquitectura 00 — Decisiones técnicas, stack y hoja de ruta

> **Proyecto:** Liquidación del impuesto anual de vehículos automotores
> **Documentos base:** `ERD_impuesto_vehicular_definitivo_v2_CSV_cartera.md` · `01` a `04` · `config_jurisdiccion_*.yaml`
> **Serie de arquitectura:** `00` Decisiones (este) · `01` Arquitectura de software · `02` Seguridad · `03` Escalabilidad y operación · `04` Calidad, testing y CI · `05` Gobierno de agentes de IA y SDD
> **Versión:** 0.1 — **Propuesto**, pendiente de aprobación del equipo
> **Alcance:** solo definición. No incluye implementación ni código.

---

## 1. Propósito

Sentar las bases técnicas del sistema antes de escribir la primera línea de código: estilo de arquitectura, stack, seguridad, escalabilidad, mantenibilidad, prácticas de calidad y el modo controlado en que trabajarán los agentes de IA. El objetivo explícito es **evitar el *vibe coding***: código generado sin especificación, sin pruebas y sin límites, que produce deuda técnica.

Cada decisión de este documento se justifica con evidencia de la documentación funcional. Las decisiones quedan como ADR en estado **Propuesto** (§6) hasta que el equipo las apruebe.

---

## 2. Impulsores de arquitectura extraídos de la documentación

| # | Impulsor | Evidencia en la documentación | Consecuencia técnica |
|---|---|---|---|
| D1 | **Exactitud monetaria** | LQ‑C01 (NUMERIC sin pérdida, ≥ 6 decimales, redondeo solo al final); CA‑4.04 (imputación $191.597 / $8.403); descuento efectivo tras redondeo (Doc. 03 §9.3) | Aritmética decimal exacta nativa; tipo de valor `Dinero`; prohibido `float`/`double` en dominio |
| D2 | **Ninguna regla tributaria en el código** | Doc. 03 §5.1: "El motor de liquidación no tiene ninguna regla escrita en el código"; Doc. 02 encabezado | Motor de reglas dirigido por una configuración versionada, validada con esquema (JSON Schema) y reglas CJ |
| D3 | **Procesos masivos reiniciables** | 389.530 avalúos (Doc. 03 §2); emisión masiva en bloques de 1.000 idempotentes con reinicio seguro (LM‑05); exportación asíncrona > 50.000 filas; imputación diaria sin duplicados (IM‑06) | Framework de lotes con *checkpoint*, reinicio e idempotencia; separación de procesos API / *worker* |
| D4 | **Patrón de ingesta repetido** | Staging → validación → aprobación → publicación en avalúos (1.5.3), configuración YAML (1.5.9), archivo del banco (4.2.2), reporte del departamento (4.6.1) y cartera histórica (ERD) | Un componente común de ingesta (Template Method) con piezas específicas por tipo de archivo |
| D5 | **Consistencia transaccional fuerte** | Publicación atómica de catálogos; liquidación → obligación → movimientos; imputación que no exceda el pago (ERD hallazgo 4) | Una sola base de datos transaccional; sin transacciones distribuidas |
| D6 | **Inmutabilidad y libro mayor** | "Lo publicado nunca se modifica" (Doc. 01 §2); saldo = Σ movimientos, sin borrados, con reversos (Doc. 04 §2) | Tablas *append‑only* con permisos de BD que impiden UPDATE/DELETE; versionamiento explícito |
| D7 | **Segregación de funciones** | Cargador ≠ aprobador; doble firma para habilitar vigencia (Doc. 01 §4); aprobador de corrida ≠ creador (LM‑04) | Reglas de segregación como invariantes de dominio, no solo de interfaz |
| D8 | **Aislamiento territorial** | RLS por `administration_id` (Doc. 01 §2.7); FK que no mezclen administraciones (ERD hallazgo 2) | Multi‑inquilino con *Row Level Security* de PostgreSQL |
| D9 | **Reproducibilidad y trazabilidad** | Cada liquidación guarda `jurisdiction_config_id`, `rule_version` y la traza de cálculo (Doc. 03 §5.6, LM‑07) | Motor determinista (función pura); traza JSONB; versión semántica del motor |
| D10 | **Auditoría total** | Toda acción, incluida la exportación, en `audit.audit_event` (Doc. 01 §2.9) | Auditoría transversal automática, correlación de extremo a extremo |
| D11 | **Excel intensivo** | Tablas del Ministerio en XLSX nativo; exportaciones con hoja *Parámetros* + *Datos* (Doc. 01 §7.1) | Lectura y escritura de XLSX en *streaming* |
| D12 | **Integraciones por archivo** | Bancos (formato Asobancaria o propio), extractos, reporte del departamento, SIBGA, RUNT (ERD AUDIT) | Puertos y adaptadores; un adaptador por formato; trazabilidad en `audit.external_integration_event` |
| D13 | **Máquinas de estado explícitas** | Lote, configuración, liquidación, corrida masiva, pago, saldo a favor | Patrón State con transiciones permitidas en el dominio y CHECK en BD |
| D14 | **Datos personales** | Contribuyentes, direcciones, documentos, pagos | Cumplimiento de la Ley 1581/2012 (habeas data); datos sintéticos en desarrollo |
| D15 | **Normativa cambiante y pendientes jurídicos** | Secciones "Pendientes por confirmar" en todos los documentos; estados `VIGENTE` / `POR_CONFIRMAR` / `PENDIENTE` | Especificaciones y decisiones versionadas (SDD + ADR); nada se implementa sobre un pendiente sin decisión |
| D16 | **Escala variable** | Primer caso: un municipio (Ipiales); el ERD pide validar con Bogotá; modalidad `ADMINISTRACION_DIRECTA` para departamentos | Un mismo producto para municipio o departamento: escalado horizontal y multi‑inquilino desde el inicio |

---

## 3. Evaluación del backend: Spring Boot vs. NestJS

Puntuación de 1 a 5 por criterio, ponderada por su peso para **este** proyecto.

| Criterio | Peso | Spring Boot | NestJS | Motivo |
|---|:-:|:-:|:-:|---|
| Aritmética decimal exacta (D1) | 5 | 5 | 3 | `BigDecimal` es nativo en Java. En TypeScript se necesita una librería (decimal.js, big.js) y existe el riesgo permanente de mezclar con `number` |
| Lotes reiniciables (D3) | 5 | 5 | 3 | Spring Batch trae *chunks*, repositorio de ejecuciones, reinicio y particionamiento. En Nest hay que construirlo sobre BullMQ y Redis |
| Transacciones y consistencia (D5, D6) | 4 | 5 | 4 | Ambos las soportan; el modelo transaccional de Spring es más maduro |
| PostgreSQL avanzado: RLS, EXCLUDE, particiones; Flyway (ya citado en el ERD) | 4 | 5 | 4 | Flyway y jOOQ son nativos del ecosistema Java; los ORM de Node tienen soporte limitado de RLS |
| Excel en *streaming* (D11) | 3 | 5 | 4 | Apache POI (SXSSF) y lectores en *streaming* frente a ExcelJS |
| Modularidad verificable (anti *vibe coding*) | 4 | 5 | 4 | Spring Modulith + ArchUnit verifican fronteras en el build; en Nest, dependency‑cruiser |
| Seguridad (D7, D8) | 4 | 5 | 4 | Spring Security con seguridad por método y servidor de recursos OAuth2 |
| Mismo lenguaje que Angular | 3 | 3 | 5 | Ventaja de Nest. Se mitiga generando el cliente Angular desde OpenAPI |
| Mantenibilidad a largo plazo en sector público | 3 | 5 | 4 | Ecosistema estable, LTS largos, amplia oferta de talento en Colombia |
| Curva de entrada del equipo | 2 | 3 | 4 | **A confirmar** con la experiencia real del equipo |
| Calidad del código generado por agentes de IA | 3 | 4 | 4 | Ambos tipados; los tipos explícitos de Java reducen la ambigüedad |
| **Total ponderado (máx. 200)** | **40** | **187** | **153** | |

**Decisión: Spring Boot.** El núcleo del sistema es cálculo monetario exacto, procesos masivos reiniciables y consistencia transaccional; ahí Spring Boot tiene herramientas maduras y NestJS obligaría a construirlas. La única ventaja relevante de NestJS (un solo lenguaje) se compensa con un contrato OpenAPI del que se genera el cliente Angular.

> El equipo confirmó que tiene experiencia en Java y Spring Boot (P3, §7), así que el criterio "curva de entrada del equipo" ya no es una incertidumbre.

---

## 4. Evaluación del estilo de arquitectura

| Opción | Ventajas | Desventajas para este proyecto | Veredicto |
|---|---|---|---|
| Monolito en capas tradicional | Simple, rápido de iniciar | Sin fronteras entre dominios; con agentes de IA degenera rápido en "bola de lodo" | ❌ |
| Microservicios | Despliegue y escalado independientes | La liquidación, la obligación y los movimientos necesitan una transacción atómica (D5): obligaría a sagas y consistencia eventual en el núcleo contable. Sobrecarga operativa desproporcionada para el equipo y para un municipio | ❌ |
| **Monolito modular con arquitectura hexagonal por módulo** | Fronteras explícitas y verificadas en el build; una BD transaccional; un solo despliegue; los módulos se pueden extraer después si se justifica | Requiere disciplina en las fronteras, que se automatiza con Spring Modulith y ArchUnit | ✅ |

**Decisión:** monolito modular (Spring Modulith) con un módulo por contexto de negocio, alineado con los esquemas del ERD, y arquitectura hexagonal (puertos y adaptadores) dentro de cada módulo con reglas de negocio. El mismo artefacto se despliega con dos roles, **API** y **worker**, para escalar por separado la atención de usuarios y los procesos masivos. El detalle está en `01_Arquitectura_de_Software.md`.

---

## 5. Stack definido

Las versiones exactas se fijan en la Fase 0 con la última versión estable de cada línea y quedan centralizadas (catálogo de versiones de Gradle, `package.json`, imágenes por *digest*). Las versiones indicadas son la **línea base mínima**.

| Capa | Tecnología | Línea base | Justificación |
|---|---|---|---|
| Lenguaje backend | Java | 25 LTS | LTS, *records*, *pattern matching*, hilos virtuales |
| Framework backend | Spring Boot | 4.x | Ecosistema de §3 |
| Modularidad | Spring Modulith | Alineado con Spring Boot | Verificación de fronteras, eventos entre módulos con registro persistente (*outbox*) |
| Build | Gradle (Kotlin DSL) con catálogo de versiones y *convention plugins* | 9.x | Reglas de calidad iguales en todos los módulos; *build cache* para CI |
| Persistencia de escritura | Spring Data JPA (Hibernate) | — | Agregados y concurrencia optimista |
| Consultas, reportes y masivos | jOOQ | — | SQL tipado generado desde el esquema real; consultas con filtros y exportaciones |
| Migraciones | Flyway | — | Ya previsto en el ERD ("Checklist previo a Flyway") |
| Base de datos | PostgreSQL | 18 | RLS, `EXCLUDE USING gist`, particionamiento, JSONB |
| Procesos masivos | Spring Batch + ShedLock | — | D3; bloqueo distribuido de tareas programadas en BD |
| Excel | Apache POI (SXSSF) + lector XLSX en *streaming* | — | D11 |
| Configuración de jurisdicción | Jackson YAML (carga segura) + validador JSON Schema | — | D2; esquema versionado (`esquema_version`) |
| Mapeo | MapStruct | — | Mapeos explícitos y verificados en compilación |
| Contrato de API | OpenAPI 3.1 *contract‑first* + openapi‑generator | — | Interfaces Spring y cliente Angular generados del mismo contrato |
| Errores de API | Problem Details (RFC 9457) | — | Código de error = código de regla (p. ej. `LQ-P04`) |
| Frontend | Angular (*standalone*, *signals*, TypeScript estricto) | ≥ 21 | Requisito del proyecto |
| Estilos de interfaz | Tailwind CSS, un archivo por componente | — | Cada componente declara sus clases en su `.component.css`. `frontend/src/styles.css` solo guarda lo reutilizado en toda la interfaz. Prohibido el atributo `style` y las utilidades de Tailwind escritas en la plantilla |
| Librería de componentes | Librería Angular propia `@liv/ui` dentro del monorepo, externa a la aplicación | — | Una sola línea de componentes para todas las pantallas; un cambio se refleja en todas (01 §11.1) |
| Catálogo de componentes | Storybook + regresión visual con Playwright sobre las historias | — | Referencia viva de la librería y detección de cambios visuales no deseados |
| Diseño | Figma: sistema de diseño (estilos locales de color, texto y efecto, y componentes) + pantallas por módulo; plan Professional con Dev Mode; servidor MCP de Figma para los agentes | — | Toda pantalla nueva se diseña y aprueba antes de implementarse (01 §11.2) |
| Identidad | Keycloak propio (OIDC), desplegado en AWS | — | MFA, roles; federable con un directorio de la entidad (P2) |
| Puerta de entrada | BFF con Spring Cloud Gateway | — | *Tokens* fuera del navegador (cookie HttpOnly) |
| Archivos | Amazon S3 con versionado y bloqueo de objetos (Object Lock); MinIO solo en desarrollo local y pruebas | — | Archivos originales inmutables con SHA‑256 |
| Nube | AWS: ECS sobre Fargate, RDS for PostgreSQL, S3, ECR, Secrets Manager, KMS | — | P1 y ADR 0020 (03 §5) |
| Antivirus de cargas | ClamAV | — | Archivos externos (bancos, ministerio, departamento) |
| Observabilidad | OpenTelemetry, Micrometer, Prometheus, Grafana, Loki, Tempo | — | Trazas, métricas técnicas y de negocio, logs correlacionados |
| Contenedores | Docker, Docker Compose (local), imágenes OCI firmadas (cosign) | — | Paridad de entornos |
| CI/CD | GitHub Actions, con la lógica en tareas de Gradle, npm y `tools/` para poder migrar a Bitbucket Pipelines | — | Requisito del proyecto (P4) |
| Calidad estática | Spotless, Error Prone + NullAway, ArchUnit, SonarQube, ESLint, Prettier, sqlfluff, yamllint, markdownlint | — | Ver doc. 04 |
| Pruebas | JUnit 5, AssertJ, jqwik, Testcontainers, PIT (mutación), Vitest + Angular Testing Library, Playwright + axe‑core, k6 | — | Ver doc. 04 |
| Seguridad en CI | CodeQL, Semgrep, gitleaks, Trivy, *dependency review*, OWASP ZAP, Renovate, SBOM CycloneDX | — | Ver doc. 02 |
| Documentación | Markdown + Mermaid, ADR formato MADR, modelo C4 en Mermaid | — | Documentación como código, legible por humanos y agentes |
| Proceso | SDD con la plantilla `spec/` (spec → plan → tareas → código) + TDD | — | Ver doc. 05 y `spec/README.md` |

---

## 6. Registro de decisiones de arquitectura (ADR)

En la Fase 0 cada fila se convierte en un archivo `docs/adr/ADR-NNNN-titulo.md` (formato MADR: contexto, opciones, decisión, consecuencias). Un ADR aceptado solo se cambia con otro ADR que lo reemplace.

| ADR | Decisión | Estado | Documento |
|---|---|---|---|
| 0001 | Backend en Java + Spring Boot | Propuesto | 00 §3 |
| 0002 | Monolito modular (Spring Modulith) con hexagonal por módulo; roles API y worker | Propuesto | 00 §4, 01 §3 |
| 0003 | Una base PostgreSQL; un esquema por módulo; lectura cruzada solo por vistas publicadas | Propuesto | 01 §9 |
| 0004 | Multi‑inquilino por `administration_id` con RLS | Propuesto | 02 §5 |
| 0005 | API REST *contract‑first* con OpenAPI; cliente Angular generado | Propuesto | 01 §10 |
| 0006 | JPA para escritura de agregados; jOOQ para consultas, reportes y masivos | Propuesto | 01 §9 |
| 0007 | Spring Batch para procesos masivos; worker separado | Propuesto | 01 §12, 03 §2 |
| 0008 | Eventos entre módulos con *outbox* en PostgreSQL; sin *broker* de mensajes hasta que se justifique | Propuesto | 01 §3, 03 §2 |
| 0009 | Motor de reglas propio, determinista y dirigido por configuración (sin Drools ni BPM) | Propuesto | 01 §5 |
| 0010 | Autenticación OIDC (Keycloak) con patrón BFF | Propuesto | 02 §3 |
| 0011 | Archivos en almacenamiento S3 compatible e inmutable | Propuesto | 02 §7 |
| 0012 | Tailwind CSS en el `.component.css` de cada componente; hoja global solo para estilos reutilizados | Propuesto | 01 §11 |
| 0013 | Monorepo temporal y separable: *backend* y *frontend* son proyectos autónomos sin archivos, herramientas ni rutas compartidas, que se separarán en repositorios propios; el único contrato entre ellos es el OpenAPI publicado por el backend. Documentación, SDD, e2e e infraestructura compartida se quedan en este repositorio | Propuesto | 01 §14 |
| 0014 | Proceso SDD + TDD obligatorio, también para agentes de IA | Propuesto | 05 |
| 0015 | *Trunk‑based development*, Conventional Commits, versionado semántico | Propuesto | 04 §8 |
| 0016 | Tipo `Dinero` sobre `BigDecimal`; redondeo solo al final y según la configuración | Propuesto | 01 §7, 04 §3 |
| 0017 | Código en inglés (alineado con el ERD); interfaz y documentación en español; glosario obligatorio | Propuesto | 01 §15 |
| 0018 | Librería de componentes propia `@liv/ui` en el monorepo; las pantallas solo componen sus componentes; *tokens* generados desde Figma | Propuesto | 01 §11.1 |
| 0019 | Diseño en Figma aprobado antes de implementar toda pantalla nueva, con la excepción de las pantallas que copian la estructura de otra existente | Propuesto | 01 §11.2, 05 §3 |
| 0020 | Alojamiento en AWS: ECS sobre Fargate, RDS for PostgreSQL, S3, ECR, Secrets Manager y KMS; sin servicios que impidan llevar la aplicación a otro proveedor | Propuesto | 03 §5 |

---

## 7. Decisiones del equipo

Insumos que la documentación funcional no resolvía y que cambian el despliegue, la seguridad o el proceso. Revisadas con el equipo el **2026-11-01**.

| # | Decisión | Resultado | Estado | Afecta |
|---|---|---|---|---|
| P1 | Dónde se aloja el sistema | **Nube pública: AWS** (ADR 0020). La región se elige con jurídica, porque alojar fuera de Colombia es transferencia internacional de datos personales (Ley 1581 de 2012) | ✅ Decidida · región pendiente | 03 §3–§5, 02 §6 y §10 |
| P2 | Proveedor de identidad | **Keycloak propio**, desplegado en AWS; se puede federar después con un directorio de la entidad | ✅ Decidida | 02 §3 |
| P3 | Experiencia del equipo en Java / Spring | **El equipo tiene experiencia**: se confirma el ADR 0001 | ✅ Decidida | 00 §3 |
| P4 | Plataforma Git | **GitHub**; posible migración futura a Bitbucket, por lo que los *pipelines* se mantienen portables | ✅ Decidida | 04 §8–§9 |
| P5 | Volumen objetivo | **Producto multi‑inquilino para departamentos** (hasta ~1.000.000 de vehículos y 500 usuarios); Ipiales es el primer inquilino | ✅ Decidida | 03 §1 |
| P6 | Disponibilidad, RPO y RTO | **Los define la entidad.** Mientras llega el requisito formal se diseña con el supuesto: 99,5 % en horario laboral, RPO 15 min, RTO 4 h | ⏳ Esperando a la entidad | 03 §1, §5, §7 |
| P7 | Retención documental y de auditoría | **No se borra nada** hasta que jurídica entregue la tabla de retención documental (Ley 594 de 2000) | 🔄 Provisional | 02 §6, 03 §7 |
| P8 | Firma digital de actos | **Después del piloto**; queda el puerto previsto | ✅ Decidida | 01 §13 |
| P9 | Portal del contribuyente | **Después del piloto**; la API y `@liv/ui` ya lo permiten | ✅ Decidida | 01 §1 |
| P10 | Responsable de diseño y plan de Figma | **No hay diseñador dedicado**: diseñan el equipo o los agentes con el servidor MCP de Figma, y **aprueba el responsable funcional** del módulo. Plan de Figma: **Professional con Dev Mode**. No incluye Code Connect (exige Organization o Enterprise), así que la correspondencia Figma ↔ `@liv/ui` se hace por nombres iguales y se documenta en Storybook | ✅ Decidida | 01 §11, 05 §3 y §6 |

---

## 8. Hoja de ruta

Ninguna fase empieza sin su documento funcional aprobado y sin sus pendientes críticos resueltos. Cada fase se ejecuta como una serie de cambios SDD (doc. 05).

| Fase | Contenido | Documento funcional | Criterio de salida |
|---|---|---|---|
| **0 — Fundaciones** | Sin funcionalidad de negocio (detalle abajo) | Esta serie 00–05 | *Walking skeleton* desplegado en *staging* con CI completo en verde |
| 1 — Tablas base y configuración | Vigencias (1.4), normas (1.3), cargue de avalúos (1.5.1–1.5.4), tarifas, calendario, beneficios, sanciones (1.5.5–1.5.8), configuración de jurisdicción (1.5.9), consultas 1.6 | 01, 03 | Tablas 2026 y configuración de Ipiales publicadas en *staging* con las mismas cifras de la prevalidación (Doc. 03 §2 y §9.2) |
| 2 — Homologación y liquidación individual | Homologación 2.6, motor de liquidación, liquidación individual (3.1) y simulador (3.3) | 02, 03 | Los ejemplos de Doc. 02 §B.9 y Doc. 03 §9.3 pasan como pruebas *golden* |
| 3 — Liquidación masiva | Corridas 3.2, excepciones, consultas 3.6 | 02 | Corrida con volumen objetivo dentro del tiempo de 03 §1, con reinicio probado |
| 4 — Cuenta corriente y recaudo | Módulo 4 completo | 04 | CA‑4.01 a CA‑4.13 automatizados en verde |
| 5 — Cartera | Intereses, sanciones, prescripción, apertura de cartera histórica | **Pendiente: Doc. 05 funcional** | Por definir |
| 6 — Cobro y medidas cautelares | Esquemas `collection` y `enforcement` | **Pendiente** | Por definir |

### 8.1 Entregables de la Fase 0

| # | Entregable | Documento |
|---|---|---|
| F0‑01 | Reorganización del repositorio: documentación en `docs/` (hecho); creación de `backend/` y `frontend/` como proyectos autónomos y separables al iniciar sus esqueletos (F0‑04, F0‑05) | 01 §14 |
| F0‑02 | ADR 0001–0020 revisados y aceptados (o reemplazados) | 00 §6 |
| F0‑03 | `AGENTS.md` / `CLAUDE.md` jerárquicos, *skills* del proyecto, plantillas SDD, ganchos del agente y de Git | 05 |
| F0‑04 | Esqueleto de backend con los módulos vacíos, verificación de fronteras y reglas de arquitectura activas | 01, 04 |
| F0‑05 | Esqueleto de frontend con autenticación, *layout*, menú dirigido por permisos y cliente API generado, construido solo con componentes de `@liv/ui` | 01 §11 |
| F0‑05a | Páginas de guía, fundamentos y componentes en el archivo de Figma LIV‑Diseño (las páginas ya existen, sin contenido), con los estilos locales (ya creados: 26 de color, 11 de texto, 5 de efecto) y los primeros componentes; librería `@liv/ui` con los *tokens* generados, los primitivos y la plantilla de consulta; Storybook y regresión visual en CI | 01 §11.1 |
| F0‑05b | Diseños en Figma aprobados del *layout* general, inicio de sesión y menú | 01 §11.2 |
| F0‑06 | Docker Compose local: PostgreSQL, Keycloak con usuarios de prueba, MinIO, ClamAV y observabilidad opcional | 03 §4 |
| F0‑07 | Flujos de GitHub Actions con todas las compuertas de calidad, aunque haya poco código | 04 §9 |
| F0‑08 | Base de datos: roles, RLS de ejemplo y primera migración con los catálogos de dominio | 01 §9, 02 §5 |
| F0‑09 | Infraestructura de auditoría, correlación y observabilidad funcionando de extremo a extremo | 02 §9, 03 §6 |
| F0‑10 | Esquema JSON de la configuración de jurisdicción, validado en CI contra los dos YAML existentes | 01 §5 |
| F0‑11 | Pruebas *golden* escritas (en rojo) con los ejemplos de los documentos funcionales | 04 §5 |
| F0‑12 | Una prueba Playwright de extremo a extremo (inicio de sesión → menú) en CI | 04 §5 |
