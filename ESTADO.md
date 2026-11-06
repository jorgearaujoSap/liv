# Estado del proyecto

> **Última actualización:** 2026-11-05
> **Regla:** este archivo se actualiza en el mismo cambio que completa, inicia o bloquea una tarea (humano o agente de IA). Si no está al día, el cambio no está terminado (`docs/arquitectura/04_Calidad_Testing_y_CI.md` §7, `docs/arquitectura/05_Gobierno_de_Agentes_IA_y_SDD.md` §4.1).
> **Estados usados:** ✅ Hecho · 🔄 En curso · ⏳ Pendiente · ⛔ Bloqueado

---

## 1. Resumen

| Tema | Estado |
|---|---|
| Fase actual | **Fase 0 — Fundaciones**: arquitectura aprobada; esqueletos de `backend/`, `frontend/` y `ui/` (librería `liv-components`) creados y en verde |
| Código | 🔄 Esqueletos sin funcionalidad de negocio: backend (Maven, Java 21, Spring Boot 4.1.2, 12 pruebas), frontend (Angular 21.2, Tailwind 4, ESLint, 2 pruebas) y librería `liv-components` **0.1.0 publicada en npm** (*tokens*); la 0.2.0, con 18 componentes (T03, T04 y T05), preparada en el PR #43 y por publicar |
| Documentación funcional (01–04, ERD) | ✅ Existente (insumo del proyecto) |
| Arquitectura (00–05) | ✅ **Aprobada** el 2026-11-01: ADR 0001–0020 aceptados sin ajustes (Java 21 LTS en lugar de 25); ADR 0021 (backend con Maven y la base del equipo) y ADR 0022 (librería como proyecto `ui/` publicado en npm) aceptados el mismo día |
| Diseño en Figma | 🔄 Estilos, hoja de fundamentos, variables de espaciado y radios, 38 iconos y logotipo hechos; 20 componentes y 15 pantallas de la feature 001 (versión 2, rediseñada) aprobados por el responsable funcional (2026-11-01) |
| Decisiones del equipo | ✅ 8 de 10 decididas; P6 espera a la entidad y P7 es provisional (§5) |

---

## 2. Documentación

| Documento | Estado | Versión |
|---|---|---|
| `docs/funcional/`: `01_Menu_y_Cargue_Tablas_Base_Liquidacion.md` · `02_…` · `03_…` · `04_…` · `ERD_…` | ✅ Existente | 0.1–0.4 |
| `docs/funcional/anexos/`: `config_jurisdiccion_plantilla_2026.yaml` · `config_jurisdiccion_52356_2026.yaml` · `Prevalidacion_Tablas_Base_Gravable_2026.xlsx` | ✅ Existente | Esquema 1.0 |
| `README.md` | ✅ Creado: mapa del repositorio | — |
| `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` | ✅ Aprobado | 1.0 |
| `docs/arquitectura/01_Arquitectura_de_Software.md` | ✅ Aprobado | 1.0 |
| `docs/arquitectura/02_Seguridad.md` | ✅ Aprobado | 1.0 |
| `docs/arquitectura/03_Escalabilidad_y_Operacion.md` | ✅ Aprobado | 1.0 |
| `docs/arquitectura/04_Calidad_Testing_y_CI.md` | ✅ Aprobado | 1.0 |
| `docs/arquitectura/05_Gobierno_de_Agentes_IA_y_SDD.md` | ✅ Aprobado | 1.0 |
| `AGENTS.md` | ✅ Creado: reglas y metodología para cualquier agente de IA; fuente única de reglas | — |
| `CLAUDE.md` | ✅ Importa `AGENTS.md` y agrega solo lo específico de Claude Code | — |
| `ESTADO.md` (este archivo) | ✅ Creado | — |
| `spec/` (README, constitución y plantilla de feature) | ✅ Creado, alineado con la plantilla SDD del curso | — |
| `.claude/skills/` (6 *skills* de proceso del proyecto) | ✅ Creado: `liv-sdd-feature`, `liv-revision-adversarial`, `liv-revision-4r`, `liv-figma`, `liv-adr`, `liv-estado` | — |
| `backend/`, `frontend/` y `ui/`: `README.md`, `AGENTS.md`, `CLAUDE.md`, `CHANGELOG.md` | ✅ Creados: cada proyecto es autosuficiente para cuando tenga su propio repositorio | — |

---

## 3. Entregables de la Fase 0

Definidos en `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` §8.1.

| # | Entregable | Estado | Detalle |
|---|---|---|---|
| F0‑01 | Monorepo y traslado de documentos a `docs/` | ✅ | Documentación en `docs/`; `backend/`, `frontend/` y `ui/` creados como proyectos autónomos, sin referencias entre ellos ni herramientas compartidas en la raíz (verificado) |
| F0‑02 | ADR 0001–0021 aceptados | 🔄 | Los 25 aceptados el 2026-11-01 (tabla del doc. 00 §6). Creados `docs/adr/` y los archivos de los ADR 0023–0025 (feature 001, aceptados con su H2) y 0026 (dependencias de desarrollo de `ui/`, aceptado el 2026-11-02). El ADR 0028 (SDD adaptativo) se aceptó el 2026-11-05. Faltan los archivos de los ADR 0001–0022 con la *skill* `liv-adr` |
| F0‑03 | `AGENTS.md`/`CLAUDE.md`, *skills*, plantillas SDD, ganchos | 🔄 | `AGENTS.md`, `CLAUDE.md`, `ESTADO.md`, plantillas SDD (`spec/`), *skills* de proceso (`.claude/skills/`) y política de modelos con 4 subagentes por nivel (`AGENTS.md` §10, `.claude/agents/`) creados. Faltan plantilla de PR y ganchos del agente y de Git; las *skills* de código se crean con F0‑04 y F0‑05 |
| F0‑04 | Esqueleto de backend con reglas de arquitectura | ✅ | Maven (*wrapper* 3.10.0, un artefacto `liv-api` en la raíz de `backend/`), Java 21, Spring Boot 4.1.2, Spring Modulith 2.1.1; base del equipo mejorada (ADR 0021): módulos `shared`, `platform` y 8 de negocio como paquetes con capas `api`/`application`/`domain`/`infrastructure`. 12 pruebas: arranque, fronteras (Modulith), catálogo de módulos y capas, 6 reglas de arquitectura y tamaño de clases, comprobadas también con violaciones a propósito; Google Java Format con Spotless. Paquete `co.com.sapenlinea.liv`. Contratos OpenAPI en `contracts/openapi/` con interfaces generadas (T11, PR #40). Seguridad de `platform` con OIDC estándar: servidor de recursos, usuario resuelto en el registro de LIV, Problem Details y enmascarado de credenciales (T12 parte 1, PR #45). Esqueleto del BFF en `backend/bff/` (`liv-bff`, Spring Cloud Gateway Server MVC; T16, PR #66) con inicio y cierre de sesión OIDC, sesión en el servidor y *tokens* cifrados (T17, PR #79), reenvío a la API, CSRF, límites de sesión, `keepalive` y `bff.yaml` (T18, PR #80). Pendiente para otros entregables: Flyway, PostgreSQL y Testcontainers (F0‑08), Dockerfile y `dev/` (F0‑06), Error Prone y NullAway (F0‑07) |
| F0‑05 | Esqueleto de frontend | 🔄 | Workspace Angular 21.2 (Node 22), Tailwind 4, *tokens* y componentes desde el paquete `liv-components@0.2.0` instalado desde npm (versión exacta), ESLint con reglas de frontera (comprobadas), Prettier, Vitest; raíz sin pantallas. Contratos `core`, `platform` y `bff` 1.0.0 fijados en `api-spec/` con su SHA-256, cliente generado en `src/app/api/`, proxy hacia el BFF y regla de ESLint para `HttpClient` (feature 001, T20, PR #82). Núcleo de sesión (T21, PR #84): `SessionService`, guardas, interceptor, `IdleService` e imagen con nginx. Faltan las pantallas, *layout* y menú (T22) |
| F0‑05a | Sistema de diseño en Figma + librería + Storybook | 🔄 | Hecho: estilos y hoja de fundamentos en Figma; proyecto `ui/` con `liv-components` **0.1.0 publicada en npm** (*tokens*), proceso de publicación documentado en `ui/README.md` con verificaciones automáticas (`npm run release:check`). Storybook con 18 componentes: `Icon`, `Logo`, `Spinner`, `Skeleton`, `Badge` y `Avatar` (T03); `Button`, `IconButton`, `TextField`, `CodeInput` y `Alert` (T04, PR #36). *Tokens* `border/strong` y `focus/ring` y placeholder de `CodeInput` corregidos para WCAG 2.1 AA (T04a, PR #38). `Dialog`, `ResultState`, `NavItem`, `SideNav`, `AppHeader`, `PageHeader` y `ModuleCard` (T05, PR #43; 0.2.0 preparada, la publica un humano). Faltan *script* que regenere los tokens desde Figma y regresión visual |
| F0‑05b | Diseños de *layout*, inicio de sesión y menú | ✅ | Rediseñados en Figma (versión 2) dentro de la feature [001 · Inicio de sesión](spec/features/001-inicio-de-sesion/spec.md): acceso de Keycloak en pantalla dividida con panel de marca, estructura general con menú lateral con iconos, inicio con tarjetas de módulos, aviso de sesión, sin permiso, carga y error. Aprobados por el responsable funcional el 2026-11-01, con sus ajustes |
| F0‑06 | Docker Compose local | 🔄 | `backend/dev/` con PostgreSQL 18.6 (bases `liv` y `keycloak`, roles de doc. 02 §5.2 y ADR 0025) y Keycloak 26.8.1, verificado con `check.sh` (T09 de la feature 001, PR #20). El *realm* `liv` se aplica como código con keycloak-config-cli y `check.sh` verifica el *realm* aplicado (T07, PR #26). Keycloak se construye desde `backend/identity/Dockerfile` con el tema `liv` (T08, PR #57 y #58). Faltan MinIO, ClamAV y observabilidad |
| F0‑07 | GitHub Actions con compuertas de calidad | 🔄 | CI de PR mínimo (T02 de la feature 001): `backend-pr` y `frontend-pr` (Q01–Q03) y `ui-pr` (Q01; desde el 2026-11-02 también las pruebas de componentes con axe y la compilación de Storybook, Q02 y Q14), con filtro de ruta, `sistema-pr` (título Conventional Commits y gitleaks) y CODEOWNERS. Desde el 2026-11-04, `bff-pr` (Q01–Q03) para `backend/bff/`, aprobado por el responsable. Contratos del backend (tarea aparte antes de T20, ADR 0027): comparador de cambios incompatibles Q08 (`backend-contracts-pr`) y publicación como *releases* `contract-<nombre>-vX.Y.Z` (`backend-main`), en revisión en los PR #69 (`backend/`) y #70 (`.github/`). Faltan publicación de imágenes (la de la imagen de identidad, con `backend/identity/theme/check.sh` y Trivy, tarea aparte que requiere aprobación del responsable), cobertura, mutación, Sonar, guardián SDD y exigir los *checks* de cada proyecto (al pasar los *workflows* a "correr siempre y omitir trabajos"). Protección de `main` creada |
| F0‑08 | Roles de BD, RLS y primera migración | 🔄 | Migraciones Flyway `V202611010900`–`0920` (T10 de la feature 001, PR #25 y #28): esquemas `platform`, `core` y `audit`; entidades, administraciones, usuarios y sesiones con FK compuestas y RLS por `app.administration_id`; `core.find_user_by_identity` y `audit.record_platform_session_denied` (`SECURITY DEFINER`); roles y permisos de la aplicación; auditoría de solo inserción; ShedLock; Flyway solo en `dev` y `test`. 87 pruebas con Testcontainers como `liv_app`. Faltan las tablas tributarias y el *checklist* previo a Flyway del ERD, y las decisiones humanas listadas en la bitácora (2026-11-02) |
| F0‑09 | Auditoría, correlación y observabilidad | 🔄 | Correlación: `X-Correlation-Id` solo como UUID, en la respuesta, el MDC y cada línea de log; credenciales enmascaradas en los logs (T12 parte 1, PR #45). Pendientes: auditoría (T15), log estructurado en JSON con enmascarado y datos personales |
| F0‑10 | JSON Schema de la configuración de jurisdicción | ⏳ | — |
| F0‑11 | Pruebas *golden* en rojo | ⏳ | — |
| F0‑12 | Prueba Playwright de humo en CI | ⏳ | — |

---

## 4. Figma — LIV‑Diseño

Archivo: <https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o> (clave `s2r2Kdu31sQW5CmVeQNyZb`). Organización definida en `docs/arquitectura/01_Arquitectura_de_Software.md` §11.2.

| Página | ID | Contenido | Estado |
|---|---|---|---|
| `00 Guía` | `0:1` | — | ⏳ Vacía |
| `01 Fundamentos` | `2:2` | Marco `Fundamentos` (`7:2`): encabezado, muestras de color, tipografía y efectos, todas enlazadas a los estilos | ✅ |
| `02 Componentes` | `2:3` | Una sección por componente, con variantes, propiedades y descripción de uso: [Icon (38, Lucide)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-210), [Logo](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-228), [Button](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-298), [IconButton](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-364), [Badge](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-384), [Avatar](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-392), [Spinner](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-8), [Skeleton](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-397), [TextField](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-182), [CodeInput](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-244), [Alert](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-277), [NavItem](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-147), [SideNav](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-157), [AppHeader](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-319), [PageHeader](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-355), [ModuleCard](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-219), [ResultState](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-317), [Dialog](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-325); y, solo para el tema de Keycloak, [Keycloak/BrandPanel](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-361) y [Keycloak/Step](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-355) | ✅ Aprobados (2026-11-01). En `liv-components`: los de T03 y T04 (11); faltan los 7 de T05. 2026-11-02 (T04a): icono de ayuda de `TextField` y `CodeInput` a 16 px en las 14 variantes y texto de ejemplo de `CodeInput` en `text/muted` · 2026-11-05: [NavItem](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-147) gana los tipos `Subgroup` y `Nested` (tercer nivel del menú, 1.5.x y 1.6.x), ⏳ pendientes de aprobación del responsable funcional |
| `03 Pantallas` | `2:5` | Todas las pantallas, una sección por módulo. Hoy: [001 · Acceso (Keycloak con tema LIV)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=24-1163) (10 pantallas, una móvil) y [001 · Aplicación (estructura general)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1717) (5 pantallas) | ✅ Aprobadas (2026-11-01); secciones marcadas "✅ Aprobado". 2026-11-03 (T08): subtítulo de "Acceso · Actualizar contraseña" (`24:737`) cambiado a "Antes de continuar, crea una contraseña nueva para tu cuenta.", por decisión del responsable (las contraseñas no vencen, D-001-7). Las 8 pantallas de acceso vigentes (las otras 2 pasaron a `99 Archivo` el 2026-11-01) ya están en el tema de Keycloak · 2026-11-05: sección [001 · Menú de tres niveles (propuesta)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=51-1518) (1 marco), ⏳ pendiente de aprobación |
| `99 Archivo` | `2:9` | Diseños reemplazados | ⏳ Vacía |

| Estilos locales | Cantidad | Estado |
|---|---|---|
| Color (`text/`, `bg/`, `border/`, `action/`, `status/`) | 28 | ✅ Paleta inicial propuesta; pendiente de validación del responsable funcional o de colores institucionales. 2026-11-01: se agregan `text/inverse-secondary` y `bg/inverse-subtle` para el panel de marca. 2026-11-02 (T04a): `border/strong` pasa a `#64748b` (4,76:1 sobre `bg/surface`) |
| Texto (Inter, JetBrains Mono) | 13 | ✅ 2026-11-01: se agregan `heading/display` (panel de marca) y `code/lg` (códigos de verificación). Los 4 estilos nuevos ya están en `theme.css` de `liv-components` (T03, PR #30) |
| Efecto (`elevation/sm`–`xl`, `focus/ring`) | 5 | ✅ 2026-11-02 (T04a): `focus/ring` pasa a dos sombras: 2 px de separación en `bg/surface` y anillo `#2563eb` opaco hasta 4 px (5,17:1) |
| Espaciado y radios (variables) | 16 | ✅ Colección `Layout`: `spacing/0`–`spacing/16` y `radius/sm`–`radius/full`, con la sintaxis de Tailwind como código |

---

## 5. Decisiones del equipo

Revisadas el 2026-11-01. Detalle en `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` §7.

| # | Decisión | Resultado | Estado |
|---|---|---|---|
| P1 | Alojamiento | Nube pública **AWS** (ADR 0020) | ✅ · región por validar con jurídica (datos personales fuera de Colombia) |
| P2 | Identidad | **Keycloak propio** en AWS | ✅ |
| P3 | Experiencia en Java / Spring | El equipo la tiene; se confirma Spring Boot | ✅ |
| P4 | Plataforma Git | **GitHub**; posible migración a Bitbucket (CI portable) | ✅ |
| P5 | Volumen | Producto multi‑inquilino para **departamentos** | ✅ |
| P6 | Disponibilidad, RPO y RTO | Los define la entidad; mientras tanto, 99,5 % / 15 min / 4 h | ⏳ Esperando a la entidad |
| P7 | Retención | No se borra nada hasta la tabla de retención de jurídica | 🔄 Provisional |
| P8 | Firma digital | Después del piloto | ✅ |
| P9 | Portal del contribuyente | Después del piloto | ✅ |
| P10 | Diseño y Figma | Sin diseñador dedicado; diseñan el equipo o los agentes; aprueba el **responsable funcional** | ✅ · plan Professional con Dev Mode, sin Code Connect: correspondencia por nombre y en Storybook |

---

## 6. Próximos pasos

1. Dar permiso de publicación de `liv-components` a los desarrolladores que publicarán versiones (`npm owner add <usuario> liv-components`, lo ejecuta el dueño actual). Hoy solo hay un responsable con cuenta personal; más adelante conviene una organización de npm del equipo.
2. Completar F0‑02 (un archivo por ADR en `docs/adr/`) y F0‑03 (plantilla de PR y ganchos).
3. Siguientes entregables: F0‑07 (GitHub Actions por proyecto), F0‑06 (Docker Compose), F0‑08 (base de datos, RLS y primera migración), F0‑10 y F0‑11; en la feature 001 (plan aprobado H2, `tasks.md` con 23 tareas), integrar el PR de documentación (T01) y hacer F0‑07 (T02), que es requisito previo; luego `tasks.md` y la implementación (primero F0‑07, porque las pruebas de seguridad del backend deben correr en CI).
4. Pendientes de las decisiones: región de AWS con jurídica (P1), disponibilidad, RPO y RTO de la entidad (P6) y tabla de retención documental (P7); validar la paleta de color con el responsable funcional.
5. `liv-components`, en este orden:
   - **T05:** integrada (PR #43) y la 0.2.0 publicada el 2026-11-03; `frontend/` la adopta en el PR #82 (T20). Antes, decidir lo abierto de T05 (bitácora del 2026-11-02): estilo de texto de la etiqueta de sección, recorte de textos y opciones deshabilitadas del menú.
   - **Backlog de componentes:** `Tooltip`, primero en Figma; mientras tanto, `IconButton` usa `title` nativo.
6. Identidad (tras T08), decisiones pendientes del responsable:
   - un trabajo de CI que construya la imagen de Keycloak, ejecute `backend/identity/theme/check.sh`, la revise con Trivy y la publique;
   - si la imagen `node` que compila el tema queda cubierta por "Tailwind CLI" en el ADR 0023;
   - la CSP del *realm* (`browserSecurityHeaders`, doc. 02 §8);
   - que `RealKeycloak` use la imagen de LIV.
7. BFF (tras T18): integrar el PR #80 con `bff · verify` en verde; luego T20 (hecha, PR #82), T21 (hecha, PR #84; decisiones pendientes en el PR: la CSP con `style-src 'unsafe-inline'`, el 403 de una mutación, `returnTo` con `%2F`, el cierre entre pestañas por expiración y el CI de la imagen), T22 (hecha, PR #89 y #90; decisiones del responsable del 2026-11-05 en la bitácora; el diseño del tercer nivel del menú está propuesto en Figma, pendiente de aprobación) y, antes de producción, T19. Decisiones pendientes del responsable: las de T18 (el prefijo `__Host-` de la cookie CSRF, los códigos de error del proveedor que terminan la sesión y la revocación con `invalid_grant`; plan › «Ajustes de T18»), las de T16 (la publicación de la imagen del BFF, Spring Boot 4.1.2 con menos de dos semanas de publicado, las dependencias de prueba del BFF y la etiqueta `bff-vX.Y.Z`; plan › Decisiones, «Ajustes de T16») y las de T17 (la equivalencia de `spring-boot-starter-session-jdbc` con el ADR 0023, los motivos de `EndSession`, las sesiones que pueden quedar abiertas en LIV hasta T19 y el sitio común del proveedor; «Ajustes de T17»).
8. Contratos (F0‑07, ADR 0027): integrar el PR #69 y después el #70, volviendo a correr sus *checks*. Comprobar que la primera ejecución de `backend-main` publica `contract-core-v1.0.0`, `contract-core-internal-v1.0.0` y `contract-platform-v1.0.0`. Luego, T20 (hecha, PR #82). Quedan por decidir:
   - activar las *releases* inmutables y un *ruleset* para las etiquetas `contract-*`;
   - exigir Q08 como *check*;
   - fijar `distributionSha256Sum` en el *wrapper* de Maven.

---

## 7. Bitácora

Registro cronológico de lo realizado. Lo más reciente va primero.

### 2026-11-05

- Feature 001, diseño del tercer nivel del menú (1.5.x y 1.6.x) en Figma, pendiente de aprobación del responsable funcional (decisión del 2026-11-05 sobre T22).
  - **Qué se diseñó:**
    - [NavItem](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-147) (`02 Componentes`) gana `Type=Subgroup` (opción de segundo nivel que se expande, con chevron: `chevron-right` cerrado y `chevron-down` abierto) y `Type=Nested` (tercer nivel, sangrado 16 px más que `Subitem`), cada uno con Default, Hover, Active y Disabled; el estado Active de `Subgroup` solo cambia el color del texto para no apilar tres barras azules;
    - marco de referencia [Menú · tres niveles](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=51-1519) (`03 Pantallas`, sección propia marcada «pendiente de aprobación»): módulo Administración abierto, 1.5 expandido con 1.5.3 como pantalla actual, 1.6 cerrado, opciones sin construir deshabilitadas y el menú que se desplaza cuando no cabe; los textos largos pasan a dos y tres líneas;
    - las descripciones de `NavItem` y `SideNav` documentan el nivel 3.
  - **Todo con estilos y componentes existentes:** sin colores, textos ni sombras nuevos; las variantes nuevas se hicieron a partir de las de `Group` y `Subitem`.
  - **Falta:**
    - la aprobación del responsable funcional;
    - la variante en `liv-components` (`ui/`, otro PR, con versión nueva y regresión visual: modificar un componente existente es un cambio crítico) y su uso en el frontend (otro PR);
    - decidir si el menú del módulo 1 se abre solo con la opción actual o se recuerda lo abierto.

- Feature 001, decisiones del responsable sobre T22 (PR #89 y #90):
  - **Aceptadas:**
    - el ADR 0029: `axe-core` 4.14.0 como dependencia de desarrollo de `frontend/`, para la prueba de accesibilidad de cada pantalla (el contraste, en T23);
    - el texto del aviso del límite de 8 horas, que no estaba en Figma;
    - el grupo de módulo del menú lateral habilitado aunque todo lo suyo esté sin construir, como en Figma.
  - **Pasa a una tarea aparte:** diseñar el tercer nivel del menú (1.5.x y 1.6.x), primero en Figma y, si hace falta, en `ui/`. Mientras tanto se pintan dos niveles.
  - **Sigue sin hacerse:** la pantalla local de «sesión terminada» (no está en los diseños aprobados).

- Feature 001, T22 (PR [#89](https://github.com/jorgearaujoSap/liv/pull/89), `frontend/`): estructura general y pantallas del frontend.
  - **Qué hace:**
    - estructura general (`SideNav`, `AppHeader`) con el menú que arma `core/navigation/menu-presentation.ts` a partir de las capacidades: solo lo permitido y las opciones `COMING_SOON` deshabilitadas (001-02, 001-15);
    - Inicio, Aviso de sesión por expirar, Sin permiso (403), Próximamente, Cargando y Error, según sus diseños de Figma (nodos `26:793`, `26:1157`, `26:1454`, `29:1689`, `26:1640` y `26:1672`), comparadas a mano en el navegador;
    - ruta `opcion/:code` con `capabilityGuard` para abrir cualquier opción por URL (001-16);
    - contrato `bff` 1.1.0 adoptado;
    - regla de ESLint: las pantallas solo usan `liv-components`, sin estilos propios.
  - **Pruebas:** 253 de Vitest (con axe por pantalla) y 89 de `node --test`; lint, formato y build en verde; `frontend · verify` en verde.
  - **Revisión:** adversarial con dos `liv-revisor-critico` ciegos y las cuatro lentes, dos rondas. La segunda dejó 1 alto (callar el 401 dejaba «Cargando» colgado, una regresión de la primera corrección), corregido con prueba primero **sin una tercera ronda**. Resumen en `tasks.md` (T22) y en el PR.
  - **Ajustes:** ver el plan, «Ajustes de T22».
  - **Decisiones para el responsable (PR de la feature):**
    - `axe-core` 4.14.0 en `frontend/` sin ADR (`AGENTS.md` §4.5): **ADR 0029 propuesto**; el PR no debe integrarse sin decidirlo;
    - el texto del aviso del límite de 8 horas, que no está en Figma;
    - el tercer nivel del menú (1.5.x y 1.6.x), sin diseño;
    - el grupo de módulo habilitado en el menú lateral aunque todo lo suyo esté sin construir, frente a la tarjeta deshabilitada de Inicio;
    - la pantalla local de «sesión terminada»: no se inventó.
  - **Falta:** T23 (infra y e2e), que cubre el contraste de color, la prueba visual y los flujos de 001-01 a 001-22 en un navegador real.

- Feature 001, decisiones del responsable sobre los 8 pendientes de T21 (PR #84):
  - **Aceptadas:**
    - la CSP con `style-src 'unsafe-inline'` para validar con usuarios;
    - el aviso a 5 minutos del límite absoluto;
    - las rutas de T22;
    - que todo 403 de `/api/**` que no sea anti-CSRF lleve a «sin permiso».
  - **Corregidas:**
    - `bff.yaml` 1.1.0 declara el 403 anti-CSRF con `code` 001-12, también en `/api/**` (PR #86);
    - `redirectToLogin` avisa el cierre a las demás pestañas (PR #87).
  - **Pasan a T19:** el *nonce* de la CSP y `returnTo` con `%2F`.
  - **Pasa a T23:** el CI de la imagen del frontend, que necesita aprobación.

- Feature 001, T21 (PR #84, `frontend/`): núcleo de sesión del frontend e imagen con nginx.
  - **Qué hace:**
    - `SessionService` sobre `BffApi`, `authGuard` con `returnTo` y `capabilityGuard` (403 y «Próximamente») sobre `PlatformApi`;
    - interceptor con `X-Correlation-Id`, token anti-CSRF, reintento tras un 401 una vez y solo en lecturas o con `Idempotency-Key`, y recarga de capacidades tras un 403;
    - `IdleService`: aviso a los 13 de 15 minutos con una sola consulta a `/bff/session` (sin sondeo ni `/api`), aviso aparte del límite de 8 horas y `BroadcastChannel` entre pestañas;
    - cierre de sesión con navegación completa;
    - `nginx.conf` (CSP, HSTS, `Referrer-Policy`, `nosniff`, `no-store` salvo archivos con hash) y `Dockerfile` en dos etapas con `nginx-unprivileged` fijado por digest y usuario 101, verificado en un contenedor de solo lectura.
  - **Pruebas:** 135 de Vitest y 27 de `node --test`; lint y build en verde.
  - **Regla de ESLint de T20:** no hizo falta ampliarla.
  - **Revisión:** adversarial con dos `liv-revisor-critico` ciegos y las cuatro lentes, dos rondas; la segunda dejó 2 altos (regresiones de `IdleService`) que se corrigieron con prueba primero **sin una tercera ronda**. Resumen en `tasks.md` (T21) y en el PR.
  - **Falta:** T22 (pantallas) y T23 (e2e). Decisiones para el responsable (PR #84): la CSP con `style-src 'unsafe-inline'`, el 403 de una mutación, `returnTo` con `%2F`, el 403 anti-CSRF fuera de `bff.yaml`, el cierre entre pestañas por expiración y el trabajo de CI de la imagen.

- Feature 001, T20 (PR #82, `frontend/`): el frontend adopta `liv-components` 0.2.0 y los contratos.
  - **Qué hace:**
    - `liv-components` 0.2.0 con versión exacta y `ng-openapi-gen` 1.1.0;
    - `api-spec/versions.json` fija versión y SHA-256 de `core`, `platform` y `bff` 1.0.0 (los SHA-256 coinciden con los publicados en las *releases*); `npm run api:update` descarga sin *token*, verifica y no cambia nada si uno falla;
    - cliente generado en `src/app/api/{core,platform,bff}/`;
    - `proxy.conf.json` hacia el BFF (`localhost:8081`), comprobado arrancando `ng serve`;
    - regla de ESLint: `HttpClient` y las demás vías de llamada HTTP solo en las carpetas de los contratos de `src/app/api/`.
  - **Pruebas:** 6 de Vitest y 41 de `node --test` (descarga y verificación, cliente guardado igual al generado, reglas de ESLint); lint y build en verde.
  - **Revisión:** adversarial con dos revisores ciegos y la lente de legibilidad; resumen en `tasks.md` (T20) y en el PR.
  - **Falta:** T21 (núcleo) y T22 (pantallas). Decisiones para el responsable (PR #82): «servicios de datos» = el cliente generado, un cliente por contrato y `npm test` con las pruebas de los *scripts*.

- Feature 001, T18 (PR #80): reenvío a la API y sesión mínima del BFF, con el alcance del 2026-11-03.
  - **Qué hace:**
    - `/api/**` va a `liv-api` con el *token* de la sesión y `/internal/**` no se enruta;
    - CSRF por cookie y cabecera;
    - 15 minutos de inactividad y 8 horas de duración máxima, comprobada en cada petición;
    - `GET /bff/session` sin extender la sesión y `POST /bff/session/keepalive` que renueva siempre el *token*;
    - `invalid_grant` termina la sesión con 401;
    - el contrato `bff.yaml`, que necesita T20, y la copia de los *scripts* de Q08 en el BFF.
  - **Revisión:** adversarial con dos revisores críticos ciegos y las cuatro lentes: ningún crítico ni alto en las dos rondas; los medios coincidentes (el `scope` del navegador, el *token* CSRF como parámetro, `LOGOUT` en lugar de `ABSOLUTE`, la API con cookies, el 502 sin contrato) se corrigieron con prueba en rojo primero.
  - **Pruebas:** 219 en `./mvnw verify`, contra PostgreSQL y un proveedor OIDC genérico reales.
  - **Falta:** T19 (endurecimiento antes de producción) y, para el frontend, T20 a T22. Las decisiones del responsable están en el plan, «Ajustes de T18».
- Feature 001, T17 (PR #79): inicio y cierre de sesión del BFF, con el alcance mínimo del 2026-11-03.
  - **Qué hace:**
    - `/bff/login` con código, PKCE, `prompt=login` y `returnTo` validado;
    - al volver, cambia el id de sesión y llama a `StartSession` con el *token* de servicio y el del usuario;
    - si LIV rechaza, o falla cualquier paso, revoca el *refresh token*, borra la sesión y vuelve a `/bff/login`;
    - `POST /bff/logout`: `EndSession`, revocación y borrado, y responde la ruta de inicio de sesión;
    - Spring Session JDBC con los *tokens* cifrados, y la comprobación del rol `liv_bff` en cada conexión (pendiente de T16).
  - **Revisión:** adversarial con dos revisores críticos ciegos y las cuatro lentes. Los dos encontraron los mismos 2 altos (el token de servicio caído dejaba una sesión a medias y salir no cerraba nada), corregidos con prueba en rojo primero; segunda ronda sin críticos ni altos.
  - **Pruebas:** 167 en `./mvnw verify`, contra PostgreSQL y un proveedor OIDC genérico reales.
  - **Falta:** T18 (reenvío a la API, CSRF, inactividad, límite absoluto, `keepalive`, `/bff/session`) y T19. Las decisiones del responsable están en el plan, «Ajustes de T17».
  - **CI:** `gitleaks` marcó dos literales de cabecera de JWT y una clave de relleno en las pruebas, y se reescribieron los commits de la rama para quitarlos.
- SDD adaptativo (ADR 0028, aceptado por el responsable el mismo día), a su pedido: antes de escribir una spec, el agente evalúa la solicitud. El SDD sigue siendo la metodología principal; no se instala ODD ni otra metodología.
  - **Criterios de SDD** (`AGENTS.md` §4.3): reglas, contratos y datos, riesgo y rutas críticas, arquitectura, interfaz, y alcance ambiguo o grande. Con uno basta para ir por el SDD; ante la duda, también.
  - **Flujo directo:** reemplaza al tipo «trivial». Va sin carpeta de feature ni documentos de planificación, pero lleva:
    - prueba primero si cambia el comportamiento;
    - verificaciones y CI en verde;
    - si cambia el comportamiento, un revisor independiente (`liv-revisor`) antes del PR; si no, la lente de legibilidad;
    - «Flujo: directo» en el PR y la revisión H3;
    - su registro en el `CHANGELOG.md` del proyecto y en esta bitácora (la raíz, solo aquí).
  - **Revisión:** dos revisores adversariales ciegos (nivel estándar). Sus hallazgos altos se corrigieron:
    - un cambio directo sin revisión de su corrección;
    - la declaración del flujo sin ninguna comprobación (queda para `sdd-guard`);
    - las ambigüedades con rutas críticas, parches de dependencias y textos de pantallas.
  - **Reevaluación:** si durante un cambio directo aparece un criterio, el agente se detiene y el cambio sigue por el SDD desde `spec.md`.
  - **Dónde:** `AGENTS.md` §2, §4.1, §4.3, §10 y §11; doc. 05 §1, §3.3 y §12; doc. 04 (Q11 y ramas); doc. 00 §6; `spec/README.md`; *skill* `liv-sdd-feature`. Los `AGENTS.md` de cada proyecto, en un PR por proyecto.

### 2026-11-04

- F0‑07, tarea aparte antes de T20 (PR #69 de `backend/` y #70 de `.github/`, por integrar en ese orden; ADR 0027): comparador de cambios incompatibles de los contratos (Q08) y su publicación.
  - **Diseño aprobado por el responsable:** una *release* `contract-<nombre>-vX.Y.Z` por contrato y versión, con el YAML y su SHA-256; `openapi-diff-maven` 2.1.7 como comparador; la etiqueta `contrato-incompatible` más la versión mayor para aprobar un cambio incompatible.
  - **Decisiones posteriores, tras la revisión:**
    - el BFF tendrá su propia copia de los *scripts* (T18);
    - dos PR, uno por proyecto;
    - por ahora no se activan las *releases* inmutables;
    - los huecos de `openapi-diff` se documentan;
    - `main` juzga cada versión contra la última *release* publicada antes de publicarla.
  - **Juez:** en CI juzgan los *scripts* de la base, no los del PR. Las pruebas del PR corren en otro trabajo, sin compartir caché.
  - **Pruebas:** 79 casos en `contracts/scripts/test/run.sh`. Entre ellos, quitar un campo requerido de la respuesta falla con `[INCOMPATIBLE]`; los demás cubren el modo Git y la publicación con un `gh` falso.
  - **Revisión:** tres rondas adversariales con `liv-revisor-critico` (la tercera, autorizada por el responsable), las cuatro lentes y una verificación puntual. Al final no queda ningún hallazgo crítico ni alto.
  - Se creó en el repositorio la etiqueta `contrato-incompatible`.
- F0-07, CI del BFF: nuevo *workflow* `bff-pr` con el trabajo `bff · verify` (Q01–Q03), aprobado por el responsable.
  - **Qué hace:** en los PR que tocan `backend/bff/`, corre `./mvnw verify` del BFF: compilación, formato, arquitectura y pruebas con Testcontainers.
  - **Por qué:** `backend/bff/` es un proyecto Maven aparte y el `verify` de `backend/` no lo compila. Va antes de T17 para que el código del BFF que agregan T17 y T18 ya tenga CI.
  - **Cómo se usa:** el *check* informa pero todavía no bloquea. Si fuera obligatorio, los PR que no tocan el BFF lo esperarían para siempre, porque el filtro de ruta no lo corre. Hasta que los *workflows* corran siempre y omitan los trabajos que no aplican (F0‑07), cada PR de `backend/bff/` se integra solo con `bff · verify` en verde.
  - **Documentación:** doc. 04 §9, `tasks.md` (T02 y T16) y el plan, en «Ajustes de T16».
  - **Revisión:** dos revisores adversariales de nivel alto y las lentes de resiliencia, confiabilidad y legibilidad; sin críticos ni altos.
  - **Sigue pendiente:**
    - la publicación de la imagen del BFF;
    - exigir el *check* en la protección de `main`;
    - que `backend-pr` no corra en los PR que solo tocan el BFF (`!backend/bff/**`);
    - una verificación automática que compare los permisos de `backend/dev/initdb` con los que repiten las pruebas del BFF;
    - fijar con su suma el Maven del *wrapper* y la imagen Ryuk de Testcontainers, en `backend/` y en el BFF.

### 2026-11-03

- Feature 001, T16 (PR #66): *spike* de Spring Cloud Gateway y esqueleto del BFF en `backend/bff/` (`liv-bff`).
  - ***Spike*:** Spring Cloud 2025.1.3 (Gateway Server MVC 5.0.3) es compatible con Spring Boot 4.1.2, así que no hace falta el plan B.
  - **Esqueleto:** proyecto Maven autónomo con Spotless, ArchUnit (sin `org.keycloak`, 001-20), catálogo de paquetes y tamaño de clases. Flyway del esquema `bff` como `liv_bff_owner`; la aplicación usa `liv_bff`. Sin endpoints ni tablas.
  - **Migración:** comprueba el contrato de los roles que entrega la infraestructura y fija los permisos del dueño. 50 pruebas con Testcontainers.
  - **Revisión:** dos rondas de revisión adversarial con revisores de nivel alto y las cuatro lentes. Se corrigieron los hallazgos altos, entre ellos las membresías de los roles, quién migra y pruebas que no detectaban permisos de más. La segunda ronda no dejó críticos ni altos.
  - **Pendiente del responsable:**
    - el trabajo de CI del BFF, porque hoy el CI del backend no lo compila;
    - Spring Boot 4.1.2, con menos de dos semanas de publicado;
    - las dependencias de prueba del BFF;
    - la etiqueta `bff-vX.Y.Z`;
    - la rotación de credenciales en AWS;
    - quitar del *initdb* local los permisos por defecto sobre `bff`.

    Todo está en el plan, en «Ajustes de T16».

- Feature 001, alcance del BFF, por decisión del responsable: primero un BFF mínimo para validar con usuarios, y el endurecimiento antes de producción.
  - **T17:** inicio y cierre de sesión.
  - **T18:** reenvío a la API, CSRF, inactividad, límite absoluto, `keepalive` y `bff.yaml`.
  - **T19, antes de producción:** terminador y barrido de sesiones vencidas, canal trasero, límite de tasa y cabeceras.
  - Se mantiene el BFF porque lo piden 001-11 y el ADR 0010.

- Feature 001, límites del perfil del *realm* (PR #62): cierra el pendiente de los ajustes de T13.
  - **Qué cambia:** nombre de hasta 74 caracteres, apellido de hasta 75 y correo de hasta 200. Nombre y apellido son obligatorios y los espacios cuentan al medir.
  - **Efecto:** Keycloak rechaza al crear o editar un usuario lo que no cabe en el registro de LIV, en lugar de que el primer ingreso falle con 401 `001-14`.
  - **Pruebas:** se comprueba contra Keycloak real.
  - **Revisión:** dos revisores adversariales de nivel alto, que aprobaron en la segunda ronda.

- Feature 001, T15 (PR #60): auditoría en el backend. 001-18 queda marcado.
  - **Eventos de LIV:** inicio, fin, rechazo y primer ingreso llegan por el *outbox* JDBC de Spring Modulith a oyentes asíncronos, cada uno en su administración. Los rechazos sin administración válida quedan como fila de plataforma, sin actor.
  - **Eventos de Keycloak:**
    - un importador programado, con ShedLock y el cliente `liv-audit-importer`, copia intentos fallidos, bloqueos y cambios de administradores;
    - enmascara el usuario intentado (HMAC), la IP y el autor, y solo guarda las claves del plan;
    - lee desde el instante exacto y no salta eventos;
    - se prueba contra Keycloak real.
  - **Decisiones del responsable**, registradas en el plan (ajustes de T15): sin actor, un solo PR, `SessionDenied` sin emisor ni `sub`, purga del *outbox* y lista blanca del plan.
  - **Verificación:** `./mvnw verify` termina con 342 pruebas en verde. Hubo tres rondas de revisión adversarial de nivel alto y las cuatro lentes.
  - **Variables obligatorias nuevas:** `LIV_AUDIT_HMAC_KEY` y `LIV_AUDIT_IMPORTER_SECRET`.

- Feature 001, T08 hecha en dos PR: tema `liv` de Keycloak e imagen propia (`backend/identity/`). Usa `liv-components` 0.2.0 con versión exacta y solo sus *tokens*.
  - **PR #57:** imagen de Keycloak 26.8.1 con el tema, optimizada para PostgreSQL; plantilla común con `Keycloak/BrandPanel`; inicio de sesión en escritorio y móvil, con "Ingresando…" y credenciales incorrectas; página de error. El *realm* activa el tema y `backend/dev/compose.yaml` construye la imagen.
  - **PR #58:** contraseña nueva, configurar el segundo factor (pasos, QR y clave) y pedir su código, con los errores en su campo.
  - **Prueba:** `backend/identity/theme/check.sh` en un entorno desechable, 45 verificaciones.
    - Mensaje único y **misma página** para contraseña incorrecta, usuario inexistente, cuenta bloqueada y usuario desactivado.
    - Campos de Keycloak, entrada con datos correctos (TOTP calculado) y bloqueo tras 5 códigos inválidos.
    - Arranque con `start --optimized`.
    - `./mvnw verify` en verde.
  - **Decisiones del responsable:**
    - prueba visual y axe a T23 (Playwright y axe no están aprobados en `backend/identity/`);
    - fuente del sistema;
    - cerrar las demás sesiones al cambiar la contraseña o activar el segundo factor;
    - subtítulo neutro en "Actualizar contraseña" (también en Figma);
    - sin aviso de bloqueo al configurar el segundo factor;
    - la latencia del bloqueo de Keycloak, como riesgo aceptado en el plan.
  - **Revisión:** adversarial y las cuatro lentes, dos rondas en cada PR. Altos corregidos:
    - la cuenta desactivada se distinguía por el marcado;
    - la prueba no verificaba los nombres de campo ni los caminos de éxito;
    - no se cerraban las demás sesiones.
  - **Spec:** 001-03 a 001-07 quedan marcados con `check.sh`, que se ejecuta a mano hasta que exista su trabajo de CI (pendiente, con aprobación del responsable). 001-19 sigue pendiente de T22 y T23.
- Feature 001, T14 (PR #55): autorización de los casos de uso y menú de capacidades en el backend.
  - **Autorización:** se niega por defecto.
    - Cada método público de un servicio de `application` lleva `@RequiresPermission` o `@OpenUseCase`; sin marca se niega a todos.
    - Sin el permiso, 403 `001-16` y el caso de uso no corre. Si no se pueden leer los permisos, 503.
    - ArchUnit cierra las vías alrededor del *proxy*.
  - **Menú:** `GET /api/v1/me/capabilities` con el catálogo `platform/menu-catalog.yaml` (Doc. 01 §3 hasta las hojas de 1.5 y 1.6, todo en `COMING_SOON`), comparado con la matriz aprobada rol por rol y permiso por permiso.
  - **Decisiones del responsable**, registradas en el plan (ajustes de T14): profundidad del catálogo, estados, reparto de 1.5, `@OpenUseCase` por método y negar por defecto.
  - `./mvnw verify` termina con 294 pruebas en verde. Dos rondas de revisión adversarial de nivel alto (en la segunda, un revisor aprobó y el hallazgo medio del otro quedó corregido y verificado) y las cuatro lentes.
  - 001-15 y 001-16 quedan probados en el backend; faltan el frontend y el e2e.
- `liv-components` 0.2.0 publicada en npm por el responsable. Destraba T08 y la adopción en el frontend (T20).

### 2026-11-02

- Feature 001, T13 terminada con la parte 2 (PR #53): `GET /api/v1/me`.
  - Usuario y administración del registro de LIV bajo RLS; roles del token que están en el catálogo de LIV y la unión de sus permisos (`PermissionCatalog`); sin roles conocidos, listas vacías.
  - Pruebas contra la matriz aprobada en H2, escrita aparte. 001-13 queda marcado en la spec.
  - `./mvnw verify` termina con 225 pruebas en verde. Dos rondas de revisión adversarial de nivel estándar, sin altos.

- Feature 001, T13 parte 1 (PR #51): inicio y fin de sesión de LIV en `core`.
  - **Qué hace:**
    - `StartSession` registra al usuario en su primer ingreso, con su identificador de LIV (001-21), y crea la sesión;
    - rechaza con 401 `001-14`, 403 `001-05` o 403 `001-17`, y publica `SessionDenied` en todos los casos;
    - `EndSession` cierra una sola vez;
    - un inicio rechazado no deja cambios;
    - eventos `SessionStarted`, `UserProvisioned` y `SessionEnded`.
  - **Pruebas:** 220 en verde, con concurrencia determinista y Keycloak real.
  - **Revisión, dos rondas:** dos adversariales de nivel alto y las cuatro lentes. En la segunda ronda, 0 críticos y 0 altos.
  - **Decisiones del responsable**, registradas en `plan.md`:
    - rechazar y auditar los datos de identidad que no caben;
    - una sesión cerrada no se reabre;
    - auditar los 401;
    - el interruptor `liv.sessions.auto-provision`.

- Feature 001, T12 terminada con la parte 3 (PR #49): la API contra Keycloak real.
  - Keycloak 26.8.1 con el *realm* como código, aplicado por keycloak-config-cli como en `dev/compose.yaml`, sin dependencias nuevas.
  - Verifica la forma real de los tokens (`typ: JWT`, `liv-api`/`liv-internal`, `azp`, roles planos) y que cada token sirva solo en su lugar, con todas las cuentas registradas para que el rechazo solo venga del token.
  - También verifica 001-17 con un usuario real de otra administración, y que el *realm* exige PKCE.
  - `./mvnw verify` termina con 199 pruebas en verde. Dos rondas de revisión adversarial de nivel estándar; la segunda, sin altos.

- Feature 001, T12 parte 2 (PR #47): contexto RLS por transacción y rol `liv_app` en el backend.
  - **Qué hace:**
    - un `JpaTransactionManager` extendido fija siempre, con alcance local, `app.administration_id` (o vacío) y vacía `app.platform_job` al empezar cada transacción;
    - abrir otra administración dentro de una transacción abierta falla;
    - cada conexión nueva exige `liv_app` sin superusuario, `BYPASSRLS`, membresías, tablas propias ni contexto fijado, y la *readiness* incluye la base;
    - el token de usuario exige `azp = liv-bff`, y el emisor y el JWKS van por `https` salvo en `dev` y pruebas.
  - **Pruebas:** con un *pool* de una conexión (A y luego B, misma conexión física), con escritura, con `REQUIRES_NEW` y con un oyente asíncrono, y con cada forma de saltarse la RLS. `./mvnw verify` termina con 191 pruebas en verde.
  - **Revisión, dos rondas:** dos adversariales de nivel alto y las cuatro lentes. En la segunda ronda, 0 críticos y 0 altos.
  - **Decisiones del responsable**, registradas en `plan.md`:
    - fijar siempre el contexto;
    - comprobar el rol por conexión, más *readiness*, en lugar de al arrancar;
    - la prueba contra Keycloak real, en un PR aparte (T12 parte 3).

- Feature 001, T12 parte 1 (PR #45): seguridad de `platform` en el backend.
  - **Qué hace:**
    - servidor de recursos con solo OIDC estándar (001-20) y tres cadenas: `/api` (token de usuario y `CurrentUserFilter`, con 401 `001-14`, `001-05` o `001-17`), `/internal` (token de servicio de `liv-bff-service` y `X-User-Token`) y el resto (solo salud);
    - `AdministrationContext` con la administración registrada, y `LeakedContextFilter`, que rechaza y limpia un contexto que alguien dejó abierto en el hilo;
    - todo error como Problem Details con `code` (`SYS-<estado>` para los técnicos y 503 solo para caídas), también desde los filtros;
    - correlación solo como UUID;
    - credenciales enmascaradas en mensajes y trazas.
  - **Pruebas:** con la configuración de producción contra PostgreSQL real y `mock-oauth2-server`. `./mvnw verify` termina con 169 pruebas en verde.
  - **Revisión, dos rondas:** dos adversariales de nivel alto y las cuatro lentes. En la segunda ronda, 0 críticos y 0 altos.
  - **Pendientes:**
    - el alto de la lente de riesgo (comprobar al arrancar el rol `liv_app`) queda para la parte 2, con el gestor de transacciones;
    - **decisiones del responsable** sobre lo que salió de la revisión, registradas en `plan.md`:
      - van a la parte 2: exigir `azp = liv-bff` en el token de usuario, `https://` para el emisor fuera de `dev` y `test`, y la prueba contra Keycloak real;
      - se queda como está: solo `typ: JWT` y los códigos `SYS-<estado>`;
      - pasan a F0‑09: el enmascarado de datos personales y el log en JSON.
  - En paralelo, en otra sesión, T05 (PR #43 y #44).

- Decisión del responsable: en un cambio crítico, solo la revisión adversarial y la lente de riesgo usan el nivel alto. Resiliencia y confiabilidad usan el estándar, y legibilidad el económico. `AGENTS.md` §10.1, regla 2, se alinea con su tabla y con `liv-revision-4r`, que ya lo decían. La contradicción la encontró la revisión del registro de T11.

- Feature 001, T04a en revisión (PR #38): contraste de *tokens* de `liv-components`, primero en Figma (LIV‑Diseño) y después en `theme.css`. Sin cambio de versión: la 0.2.0 y su `CHANGELOG.md` son T05, que debe registrar este cambio.
  - **Valores aprobados por el responsable:**
    - `border/strong`: `#cbd5e1` → `#64748b` (1,48 → 4,76:1 sobre `bg/surface`);
    - `focus/ring`: de `#2563eb` al 40 % a un anillo opaco con 2 px de separación en `bg/surface` (1,81 → 5,17:1);
    - placeholder de `CodeInput`: `text/disabled` → `text/muted` (2,56 → 4,76:1), y el de `TextField` deshabilitado en `text/disabled`, como en Figma;
    - icono de ayuda a 16 px en Figma; se quitó la nota de «decisión pendiente».
  - **Revisión:** adversarial con dos revisores ciegos y las cuatro lentes, todos de nivel alto.
    - Primera ronda: 0 críticos, 0 altos, 14 medios y 39 bajos. El anillo opaco se fundía con el relleno de `Button` Primary (1,30:1) y con el borde de Error. El responsable eligió el anillo con separación (**segunda decisión**), que se aplicó primero en Figma. Faltaban además pruebas del foco por componente.
    - Segunda ronda, solo sobre lo corregido: 0 críticos, 0 altos y 2 medios coincidentes (geometría del anillo y guardas de foco que se podían engañar). Se corrigió endureciendo las pruebas, comprobadas con mutaciones, sin tercera ronda.
  - 225 pruebas en verde (27 nuevas, con contraste WCAG calculado sobre `theme.css`). Además, `lint`, `format:check`, `build-storybook` y `pack`.
  - **Pendientes para T05:** registrar el cambio de *tokens* en `CHANGELOG.md` y medir el anillo sobre los fondos oscuros de `SideNav` y `AppHeader` (≈2,8:1 sobre `bg/inverse-subtle`).

- Feature 001, T04 en revisión (PR #36): `Button`, `IconButton`, `TextField`, `CodeInput` y `Alert` en `liv-components`, con 95 pruebas nuevas (198 en total, con axe) y una historia de Storybook por variante enlazada a su nodo de Figma. Sin cambio de versión: la 0.2.0 y su publicación son T05.
  - **Revisión:** adversarial con dos revisores ciegos y las cuatro lentes, todos de nivel alto.
    - Primera ronda: 2 altos únicos, corregidos. Pegar un código con espacios perdía dígitos por `maxlength`; el commit verde había quitado las pruebas del borde.
    - Segunda ronda, solo sobre lo corregido: 1 alto nuevo. Al mostrar la contraseña volvía el corrector ortográfico. Se corrigió sin tercera ronda y se pide verificarlo en H3.
    - Medios y bajos corregidos: foco visible en alto contraste, pegado sobre un campo lleno, composición de IME, `change` del autocompletado y bloqueo del clic en fase de captura.
  - **Decisiones del responsable (2026-11-02)** sobre lo que dejó abierto la revisión:
    - **Contraste**, «Corregir antes de T05». En Figma y luego en `theme.css`:
      - `border/strong` y `focus/ring` se oscurecen hasta al menos 3:1; hoy dan ≈1,5:1 y ≈1,8:1;
      - el texto de ejemplo deja `text/disabled` (≈2,6:1) por un *token* de texto con 4,5:1.

      Es un cambio de *tokens*, por tanto crítico. Queda como tarea **T04a**, y T05 depende de ella.
    - **Formularios de Angular**, «Decidir con la primera pantalla». Se mantiene `[(value)]`; el ADR para `@angular/forms` se escribe cuando la primera pantalla de Angular con formulario lo necesite.
    - **Icono de ayuda**, «Ajustar Figma a 16 px». Figma pasa a 16 px en `TextField` y `CodeInput` (en T04a); `Icon` mantiene 16, 20 y 24.
    - **Tooltip**, «Provisional y al backlog». `IconButton` sigue con `title` nativo, y un componente `Tooltip` queda en el backlog (primero en Figma).
    - **Sin decisión todavía:** `Disabled` en `CodeInput` y `fullWidth` en `Button`, que no existen en Figma.
  - **Desviaciones declaradas en `tasks.md`:** el ayudante `src/testing/css.ts`, un PR de unas 2.140 líneas y el anuncio de Loading con `loadingLabel`.

- Decisiones del responsable sobre los pendientes de T03:
  - **`Skeleton`** mantiene `width` y `height` como estilo del anfitrión, solo medidas. Queda como única excepción explícita a «sin atributo `style`» en `ui/AGENTS.md` (PR #34).
  - **CI de `ui/`:** `ui-pr` ejecuta ahora `npm test` (103 pruebas con axe) y `npm run build-storybook`. Es un cambio de CI aprobado por el responsable (`AGENTS.md` §4.5).

- Feature 001, T03 integrada (PR #30): *tokens* nuevos de Figma y los componentes `Icon` (38 iconos de Lucide), `Logo`, `Spinner`, `Skeleton`, `Badge` y `Avatar` en `liv-components`, con 103 pruebas (incluida accesibilidad con axe) y su catálogo de Storybook. La versión 0.2.0 no se publica hasta T05.
  - Revisión adversarial con dos revisores de nivel alto: 3 altos, corregidos.
  - **ADR 0026 aceptado** por el responsable el 2026-11-02, tras revisar el catálogo (propuesto en el PR #31 y aceptado en el PR de registro de T03): dependencias de desarrollo de `ui/` (Tailwind, axe-core, complementos de Storybook) y excepción para las 3 vulnerabilidades altas y 39 moderadas que trae Storybook en desarrollo. El paquete publicado tiene 0. La excepción vence el **2027-01-31**.
  - Al probar el catálogo apareció que `npm run storybook` fallaba (Storybook 10 exige `browserTarget`) y que `format:check` marcaba 33 archivos en Windows por el fin de línea. Corregido en el PR #32.
  - **Pendientes:** decidir si `Skeleton` puede recibir ancho y alto como estilo del anfitrión, lo que roza la regla "sin atributo `style`"; y agregar `npm test` y `build-storybook` al CI de `ui/`, que es un cambio de CI y requiere aprobación.
  - **Desviaciones declaradas en `tasks.md`:** archivos de configuración fuera de la lista y un PR de unas 1.650 líneas.

- Feature 001, T10 integrada (PR #25 y #28): migraciones Flyway del backend con roles, funciones y RLS.
  - **Qué hay:** esquemas `platform`, `core` y `audit`; el *outbox* de Spring Modulith; `core.territorial_entity`, `tax_administration`, `admin_user` y `user_session`; `platform.app_role`, `app_permission` y `role_permission` con la matriz de H2; `audit.audit_event` de solo inserción, `import_cursor` y ShedLock; las funciones `core.find_user_by_identity` y `audit.record_platform_session_denied`. La aplicación se conecta siempre como `liv_app`; Flyway (`liv_owner`) corre solo en los perfiles `dev` y `test`.
  - **Pruebas:** 87 con PostgreSQL 18.6 en Testcontainers y los roles reales de `dev/initdb`: aislamiento (001-17), la función de identidad (incluido un operador falso en el `search_path`), permisos exactos de `liv_app`, inmutabilidad de la auditoría, los dos caminos de filas de plataforma, el contrato de la primera migración y los perfiles de Flyway sobre bases vacías.
  - **Revisión:** adversarial con dos `liv-revisor-critico` y las cuatro lentes en cada PR; 0 críticos y 0 altos tras corregir. Lo principal que salió: el *outbox* con `DELETE` y sin RLS, una sesión que se podía reabrir, la política del importador sin limitar el origen, la función de rechazos sin lista blanca ni fecha, y un contrato de roles que no se comprobaba. Hubo segunda ronda en #25; #28 no la tuvo.
  - **Tamaño:** no cupo en ~400 líneas; se dividió en dos PR (1500 y 980, con unas 1200 y 700 de pruebas).
  - **Desviaciones declaradas en `tasks.md`:** archivos fuera de la lista (`application.yml`, `config/application-dev.yml`, `dev/initdb`, `dev/README.md`, `dev/seed`, `README.md`, `CHANGELOG.md`) y los puntos que pasan a T12, T13 y T15.
  - **Decisiones humanas pendientes** (detalle en `plan.md` › Decisiones, ajustes de T10):
    - registrar en doc. 02 §5.2 y en el ADR 0025 la membresía de `liv_owner` en los dueños de las funciones, que la infraestructura de AWS debe repetir; los volúmenes locales anteriores deben recrearse;
    - confirmar y llevar al ERD los valores de `entity_type` y de `admin_user.status`;
    - aceptar la excepción de RLS del *outbox* en doc. 02 §5.1 y definir quién lo purga;
    - decidir si el riesgo residual de `app.platform_job` se acepta o se pasa a un rol propio del importador;
    - llevar la matriz de permisos a Doc. 01 §4 (doc. 02 §4.4);
    - retención y particionado de la bitácora (requiere un ADR).
- Feature 001, T07 integrada (PR #26): *realm* `liv` de Keycloak como código en `backend/identity/config/base/liv-realm.yaml`, aplicado con keycloak-config-cli.
  - Roles con MFA por rol compuesto, flujo con TOTP condicional, bloqueo de 15 min tras 5 intentos, política de contraseñas, tiempos de 001-10, eventos, `administration_id` solo para administradores y clientes `liv-bff`, `liv-bff-service` y `liv-audit-importer`.
  - `RealmConfigTest` con 17 pruebas; verificado contra Keycloak 26.8.1 desde cero y al reaplicar.
  - La revisión adversarial y las cuatro lentes encontraron 7 altos y 0 críticos, todos corregidos; en la segunda ronda, 0 altos. Los principales: `admin-cli` daba *token* solo con contraseña, la prueba del flujo MFA pasaba con el flujo roto, la capa dev podía publicarse y `jboss-logging` escribía datos personales.
  - **Dos decisiones del responsable:** sin rotación del *refresh token* y HTTPS siempre (`sslRequired: all`). Están en `plan.md` › Decisiones, junto con el resto de los ajustes de T07.
  - **Desviaciones declaradas en `tasks.md`:**
    - la capa de desarrollo vive en `backend/dev/keycloak/` y no trae TOTP; los usuarios con TOTP conocido pasan a T23;
    - la prueba está en `src/test/java`;
    - se tocaron archivos de `backend/dev/` (T09).
  - **Hallazgo:** keycloak-config-cli no puede reaplicar credenciales TOTP sin duplicarlas.

### 2026-11-01

- Feature 001, T06 hecha (PR #23): *spike* de Keycloak 26.8.1 en el entorno local.
  - Se confirman la revocación del *refresh token* (cierra la sesión del usuario) y los eventos.
  - El tema no distingue la cuenta bloqueada ni recibe parámetros propios.
  - El responsable decidió: **mensaje único** para todo fallo, con aviso del bloqueo (enmienda 2 de la spec: 001-04 y 001-19; 001-03 sin excepción), y **sin mensaje de motivo** al salir o expirar.
  - En Figma, "Credenciales incorrectas" lleva el nuevo texto y "Cuenta bloqueada" y "Sesión cerrada o expirada" pasan a `99 Archivo` (quedan 8 pantallas de acceso).
  - Plan y tareas ajustados; no hace falta ninguna extensión de Keycloak.
- Feature 001, T09 integrada (PR #20): entorno local del backend en `backend/dev/`.
  - PostgreSQL 18.6 y Keycloak 26.8.1 fijados por *digest*, puerto 5433 para no chocar con un PostgreSQL local.
  - Roles sin privilegios de administración, nada abierto a `PUBLIC` y funciones nuevas sin `EXECUTE` para `PUBLIC`.
  - Semilla ficticia para T10 y `check.sh` con 28 verificaciones.
  - **Desviación declarada:** la verificación del *realm* importado queda pendiente hasta T07.
  - **Hallazgo:** la imagen de PostgreSQL confía (`trust`) en `127.0.0.1`, así que las pruebas de contraseña deben conectar por el nombre del servicio.
  - Casillas de T01, T02 y T09 marcadas en `tasks.md`.
- Decisión del responsable: no se usa un servicio automático de actualización de dependencias. Se retiró Dependabot (`.github/dependabot.yml`) y se cerraron sus 11 PR. Las actualizaciones son manuales, con los comandos de cada proyecto, al iniciar cada fase y ante una vulnerabilidad; una versión mayor sigue exigiendo ADR. Actualizado doc. 02 §11 (antes preveía Renovate). La mención a Dependabot en las consecuencias del ADR 0023 queda reemplazada por esta decisión.
- Creada la regla de protección de `main` (*ruleset* "main"), sin excepciones para nadie: bloquea borrado y *force push*; exige PR, historial lineal, conversaciones resueltas y los *checks* `sistema · título Conventional Commits` y `sistema · secretos`; 0 aprobaciones (equipo de una persona). El repositorio integra solo con *squash* (título del PR como *commit*) y borra la rama al integrar.
- Decisión del responsable: mientras el equipo sea de una sola persona, la rama `main` no exige aprobaciones y quien abre el PR lo revisa (H3) e integra. Las dos aprobaciones de los cambios críticos se suplen con las revisiones automáticas de nivel alto. Registrada como excepción temporal en `AGENTS.md` §4.3, que se elimina al sumar una segunda persona.
- Integrado el PR #7 (documentación de la feature 001, ADR 0023–0025 y política de modelos).
- Preparado T02 (F0‑07, CI de PR mínimo), en la rama `chore/f0-07-ci-pr`:
  - cuatro *workflows* con acciones fijadas por SHA y CODEOWNERS (Dependabot se retiró después, ver la entrada más reciente);
  - los comandos de cada proyecto pasan en local;
  - en Windows, `format:check` falla en local por los finales de línea CRLF de la copia de trabajo (en Git están en LF y pasan).
- Abierto el PR #7 (raíz, tarea T01 de la feature 001): spec, diseño, plan (H2) y tareas del inicio de sesión, ADR 0023–0025 con sus cambios en la arquitectura, y política de modelos con los subagentes del proyecto. Antes del *commit* se aplicó la lente de legibilidad (`liv-revisor-rapido`): 7 hallazgos corregidos y 3 descartados. Siguiente: T02 (F0‑07, CI), que necesita aprobación humana.
- Feature 001 · Inicio de sesión:
  - **Plan aprobado (H2)** por el arquitecto y el responsable funcional (versión 4). La aprobación incluye la matriz de permisos, los ajustes al modelo, los roles de BD nuevos, la política de contraseñas, la pantalla "Próximamente" y la autorización de migrar antes del *checklist* del ERD.
  - Creados y aceptados los ADR 0023 (dependencias), 0024 (identidad como `backend/identity/` y excepción del tema de Keycloak al ADR 0018) y 0025 (dependencias `audit → core` y `audit → platform`, `/internal/v1`, filas de plataforma).
  - Actualizados doc. 00 §5 y §6, doc. 01 §3.2, §10, §11.1 y §14, doc. 02 §5.1 y §5.2, las rutas críticas y los conteos de estilos en `AGENTS.md` y `tech-stack.md`.
  - Escrito `tasks.md`: 23 tareas, una por PR. Orden: raíz, F0‑07, librería, identidad, entorno local, API, BFF, frontend y e2e. Incluye la trazabilidad de cada criterio a sus tareas.
- Feature 001, plan versión 4:
  - El responsable autorizó una tercera ronda de revisión adversarial, como excepción al máximo de dos. La hicieron dos subagentes `liv-revisor-critico` (Opus).
  - Resultado: 0 críticos; 4 y 2 altos según el revisor.
  - Altos corregidos: cierre único de sesión (`SessionTerminator`) también cuando Spring Session borra al leer, contexto RLS por transacción, auditoría de rechazos sin administración y cliente/audiencia exclusivos para el *token* de servicio. También se corrigieron 14 medios y bajos.
  - Se agregó en Figma la pantalla "Próximamente", pendiente de aprobación en H2.
  - Lo corregido en la versión 4 pasa a H2 sin una cuarta ronda; lo vuelven a cubrir las revisiones de cada PR.
- Definida la política de modelos de IA y consumo de tokens (`AGENTS.md` §10), válida para cualquier herramienta:
  - tres niveles: alto para spec, plan, ADR y revisión de cambios críticos; estándar para construir y revisar cambios normales; económico para búsquedas, legibilidad y cambios triviales;
  - prácticas para no gastar de más.
  - En Claude Code (`CLAUDE.md`): alto = Opus, estándar = Sonnet, económico = Haiku, y 4 subagentes del proyecto en `.claude/agents/` que fijan su modelo: `liv-explorador` (Haiku), `liv-revisor-rapido` (Haiku), `liv-revisor` (Sonnet) y `liv-revisor-critico` (Opus).
  - Las *skills* `liv-revision-adversarial`, `liv-revision-4r` y `liv-sdd-feature` indican el nivel de cada paso.
- Feature 001 · Inicio de sesión:
  - **Diseño aprobado** por el responsable funcional (versión 2, con sus ajustes en Figma); secciones de `03 Pantallas` marcadas "✅ Aprobado".
  - Se agregó a la spec el criterio 001-22 (aviso de sesión por expirar, WCAG 2.2.1).
  - Escrito `plan.md`. La revisión adversarial (dos revisores ciegos) encontró 1 hallazgo crítico y 17 altos entre los dos, entre ellos un ciclo de dependencias `platform`–`core`, alta de usuarios incompatible con RLS, un endpoint de auditoría falsificable y criterios de H1 cambiados sin enmienda.
  - El responsable funcional decidió la **enmienda 1** de la spec: 001-04 mantiene el aviso de bloqueo aunque revele que el usuario existe; el segundo factor voluntario pasa a una feature "Mi cuenta"; los eventos de Keycloak se copian a la auditoría de LIV; se aprueba la excepción del tema de Keycloak a la regla de componentes.
  - Plan corregido a la versión 2 (ADR 0023 a 0025 propuestos). La segunda ronda de revisión encontró 7 hallazgos altos por revisor (6 comunes): el endpoint interno sin la identidad del usuario, el dueño de los eventos de sesión, los tiempos BFF/Keycloak, el ID *token* en la URL de cierre, las expiraciones sin registrar y el e2e sin semilla. **Revisión escalada al responsable** (máximo dos rondas).
- Rediseñada en Figma la feature 001 · Inicio de sesión (versión 2, a pedido del responsable funcional: más profesional y moderna, con iconos y logotipo). Se reemplazó la versión 1 (no aprobada). Nuevo: 38 iconos de Lucide (licencia ISC) como componentes, logotipo de LIV (monograma y nombre), 4 estilos (`text/inverse-secondary`, `bg/inverse-subtle`, `heading/display`, `code/lg`) y 20 componentes (Button con iconos, tamaños y estado de carga, IconButton, Badge, Avatar, Spinner, Skeleton, TextField con icono y acción, CodeInput, Alert con icono, NavItem, SideNav, AppHeader, PageHeader, ModuleCard, ResultState, Dialog y, solo para Keycloak, BrandPanel y Step). 15 pantallas: 10 de acceso en pantalla dividida con panel de marca (incluye "Ingresando…" y versión móvil) y 5 de la aplicación (inicio con tarjetas de módulos, aviso de sesión por expirar, sin permiso, carga con skeleton y error). Esperan la aprobación del responsable funcional.
- Iniciada la feature **001 · Inicio de sesión** (`spec/features/001-inicio-de-sesion/`), primera feature y guía de referencia del flujo SDD para el equipo. Alcance acordado: página de inicio de sesión de Keycloak con el tema de LIV, BFF, `/api/v1/me`, guarda de rutas, cierre de sesión y expiración, segundo factor obligatorio para administradores y aprobadores, y recurso de capacidades para el menú. Escrita la `spec.md` con 21 criterios (`001-01` a `001-21`) y 6 decisiones propuestas (D-001-1 a D-001-6), entre ellas la independencia del proveedor de identidad para poder reemplazar Keycloak sin cambiar el dominio ni el frontend. **Spec aprobada (H1)** por el responsable funcional; sigue el diseño en Figma.
- Integrados en `main` los PR #2 (raíz: aprobación de los ADR 0001–0022, *skills*, estructura de proyectos), #3 (`backend/`), #4 (`ui/`) y #5 (`frontend/`), uno por proyecto según la regla IN‑07. Creada la etiqueta `ui-v0.1.0` sobre el *merge* del PR #4, que corresponde a `liv-components@0.1.0` publicada en npm.

- Publicada `liv-components@0.1.0` en npm (cuenta del responsable del proyecto); el frontend ya la instala desde npm y su `package-lock.json` la registra. Documentado el proceso de publicación para cualquier desarrollador en `ui/README.md` (permiso con `npm owner add`, versión nueva en un PR con `npm run version:patch|minor|major` y `CHANGELOG.md`, publicación desde `main` después de integrar, etiqueta `ui-vX.Y.Z`, adopción en el frontend y tabla de errores). Nuevo `npm run release:check` (`ui/scripts/check-release.mjs`), que corre solo antes de `npm run release` y detiene la publicación si la rama no es `main`, hay cambios pendientes, falta la sesión o el permiso, la versión ya existe o no es mayor, o falta su sección en el `CHANGELOG.md`. Actualizados `ui/AGENTS.md`, `ui/CHANGELOG.md`, `frontend/README.md`, `AGENTS.md` §4.6 y doc. 01 §11.1.

- Separada la librería de componentes del frontend (ADR 0022): ahora es el proyecto autónomo `ui/` en la raíz, un workspace de Angular con la librería **`liv-components`** (el *scope* `@liv` es de otra organización en npm), que se publicará en npm como paquete **público**. El frontend la declara con versión exacta (`0.1.0`), importa sus *tokens* desde `liv-components/styles/theme.css` y no lee `../ui`. Verificado con el paquete generado (`npm pack`): el frontend pasa *lint*, pruebas y build, y las nuevas reglas de ESLint bloquean importar archivos internos del paquete o de otros proyectos. Pendiente: la primera publicación en npm, que hace un humano. Actualizados docs. 00, 01, 04 y 05, `AGENTS.md`, `README.md`, *skills* y `tech-stack.md`.

- Rehecho el backend con **Maven** a partir del proyecto que usa el equipo. Se tomó su base (Maven con *wrapper*, un artefacto, módulos como paquetes, capas `api`/`application`/`domain`/`infrastructure`, pruebas guardianas) y se mejoró: proyecto en la raíz de `backend/` sin anidar en `apps/api`, migraciones en la ruta estándar de Flyway y sin credenciales en el `pom.xml`, dominio puro en los módulos con reglas de negocio, catálogo de módulos en inglés y límites de tamaño sin excepciones, fronteras verificadas con Spring Modulith y formato aplicado con Google Java Format. Registrado como ADR 0021; contratos movidos a `backend/contracts/`; actualizados docs. 00, 01, 04 y 05, `AGENTS.md` y `tech-stack.md`. `./mvnw verify` en verde con 12 pruebas.
- Creados los esqueletos de los proyectos (F0‑01, F0‑04, F0‑05 y F0‑05a), con decisiones del equipo: **Angular 21 LTS** (Angular 22 exige Node 22.22.3 y el equipo tiene 22.21.1) y paquete Java **`co.com.sapenlinea.liv`**. `backend/`: build y 7 pruebas en verde. `frontend/` con la librería `@liv/ui`: *lint*, 2 pruebas, build de la aplicación y de la librería en verde; los tokens salen de los estilos de Figma. Cada proyecto tiene su `README.md`, `AGENTS.md`, `CLAUDE.md` y `CHANGELOG.md`, y no referencia nada fuera de su carpeta. Storybook queda para cuando exista el primer componente.
- Revisados con el equipo los ADR 0001–0020: **los 20 aceptados** sin ajustes. Los documentos de arquitectura 00–05 pasan a la versión 1.0, aprobada, y se levanta en `AGENTS.md` la restricción de no crear código. Cambio previo: el backend usará **Java 21 LTS** (ya instalado en los equipos) en lugar de Java 25 (ADR 0001, docs. 00 y 03, `tech-stack.md`).
- Creadas 6 *skills* de proceso dentro del repositorio (`.claude/skills/`), para que cualquier equipo y cualquier agente tenga los mismos procedimientos: `liv-sdd-feature` (reemplaza a los comandos SDD de gentle‑ai), `liv-revision-adversarial` (reemplaza a `judgment-day`), `liv-revision-4r` (reemplaza a las cuatro `review-*`), `liv-figma`, `liv-adr` y `liv-estado`. Engram queda como opcional. Actualizados `CLAUDE.md`, `AGENTS.md` (nueva §9), doc. 05 §9–§11 y `spec/README.md`.
- Integrado a `main` el PR #1 (fundaciones de la Fase 0).
- Reorganizada la documentación: funcionales en `docs/funcional/` (anexos YAML y Excel en `anexos/`) y arquitectura en `docs/arquitectura/`; creado `README.md`. Definido el monorepo separable: `backend/` y `frontend/` serán proyectos autónomos sin archivos ni herramientas compartidas, con el OpenAPI publicado como único contrato, PR, CI y versiones por proyecto (doc. 01 §14.1, reglas IN‑01 a IN‑11; ADR 0013 actualizado). Actualizados `AGENTS.md` (§1.1 y §4.6), docs. 00, 03, 04 y 05 y `spec/`.
- Confirmado el plan de Figma: Professional con Dev Mode. Como no incluye Code Connect, la correspondencia Figma ↔ `@liv/ui` es por nombre idéntico y se documenta en Storybook (docs. 00, 01 y 05).
- Revisadas con el equipo las decisiones P1–P10: AWS, Keycloak propio, Java/Spring confirmado, GitHub con CI portable a Bitbucket, producto para departamentos, firma digital y portal después del piloto, y diseño sin diseñador dedicado aprobado por el responsable funcional. P6 queda a la espera de la entidad y P7 provisional. Nuevo ADR 0020 (AWS). Actualizados los docs. 00 a 05, `AGENTS.md` y `spec/`.
- Alineada la metodología SDD con la plantilla del curso (`Curso Desarrollo con IA/spec`): nueva carpeta `spec/` con `README.md`, constitución (`mission.md`, `tech-stack.md`, `roadmap.md`) y plantilla de feature `features/NNN-nombre-feature/` (`spec.md`, `plan.md`, `tasks.md`). Reemplaza la estructura anterior `specs/changes/<id>/` con `proposal`, `design` y `verify`. Actualizados `AGENTS.md`, `CLAUDE.md` y los docs. 01, 04 y 05.
- Creado `AGENTS.md` como fuente única de reglas y metodología para cualquier agente de IA. `CLAUDE.md` ahora lo importa y solo agrega lo específico de Claude Code (Figma MCP, revisiones de gentle‑ai, Engram, ganchos). Actualizado el doc. 05 §4.
- Creado `ESTADO.md` y establecida la regla de actualizarlo en el mismo cambio que inicia, completa o bloquea una tarea (`CLAUDE.md`, docs. 04 §7 y 05 §4.1, §7.2 y §11). Documentada la hoja de fundamentos de Figma en el doc. 01 §11.2.
- Creada la hoja de muestras en `01 Fundamentos` de Figma, con color, tipografía y efectos enlazados a los estilos.
- Creados en Figma 42 estilos locales: 26 de color, 11 de texto y 5 de efecto, con descripción de uso en español.
- Reducida la estructura de páginas de Figma a cinco: una sola página `03 Pantallas` para todos los diseños y sin página de plantillas.
- Registrado LIV‑Diseño como único archivo de Figma oficial.
- Agregadas a la arquitectura la librería de componentes `@liv/ui` dentro del monorepo y la regla de diseño en Figma antes de cada pantalla, con su excepción para pantallas que copian estructura (ADR 0018 y 0019).
- Ajuste del equipo en el frontend: Tailwind CSS con componentes propios, sin librería de terceros (ADR 0012).
- Escrita la serie de arquitectura 00–05: Spring Boot (frente a NestJS, 187 contra 153 puntos), monolito modular hexagonal, seguridad, escalabilidad, calidad y CI, y gobierno de agentes de IA con SDD y TDD.
- Creado `CLAUDE.md` con la guía para agentes.
