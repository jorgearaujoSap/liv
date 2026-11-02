# Estado del proyecto

> **Última actualización:** 2026-11-02
> **Regla:** este archivo se actualiza en el mismo cambio que completa, inicia o bloquea una tarea (humano o agente de IA). Si no está al día, el cambio no está terminado (`docs/arquitectura/04_Calidad_Testing_y_CI.md` §7, `docs/arquitectura/05_Gobierno_de_Agentes_IA_y_SDD.md` §4.1).
> **Estados usados:** ✅ Hecho · 🔄 En curso · ⏳ Pendiente · ⛔ Bloqueado

---

## 1. Resumen

| Tema | Estado |
|---|---|
| Fase actual | **Fase 0 — Fundaciones**: arquitectura aprobada; esqueletos de `backend/`, `frontend/` y `ui/` (librería `liv-components`) creados y en verde |
| Código | 🔄 Esqueletos sin funcionalidad de negocio: backend (Maven, Java 21, Spring Boot 4.1.2, 12 pruebas), frontend (Angular 21.2, Tailwind 4, ESLint, 2 pruebas) y librería `liv-components` **0.1.0 publicada en npm** (*tokens*); en desarrollo la 0.2.0, con 11 componentes (T03 y T04) |
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
| F0‑02 | ADR 0001–0021 aceptados | 🔄 | Los 25 aceptados el 2026-11-01 (tabla del doc. 00 §6). Creados `docs/adr/` y los archivos de los ADR 0023–0025 (feature 001, aceptados con su H2) y 0026 (dependencias de desarrollo de `ui/`, aceptado el 2026-11-02). Faltan los archivos de los ADR 0001–0022 con la *skill* `liv-adr` |
| F0‑03 | `AGENTS.md`/`CLAUDE.md`, *skills*, plantillas SDD, ganchos | 🔄 | `AGENTS.md`, `CLAUDE.md`, `ESTADO.md`, plantillas SDD (`spec/`), *skills* de proceso (`.claude/skills/`) y política de modelos con 4 subagentes por nivel (`AGENTS.md` §10, `.claude/agents/`) creados. Faltan plantilla de PR y ganchos del agente y de Git; las *skills* de código se crean con F0‑04 y F0‑05 |
| F0‑04 | Esqueleto de backend con reglas de arquitectura | ✅ | Maven (*wrapper* 3.10.0, un artefacto `liv-api` en la raíz de `backend/`), Java 21, Spring Boot 4.1.2, Spring Modulith 2.1.1; base del equipo mejorada (ADR 0021): módulos `shared`, `platform` y 8 de negocio como paquetes con capas `api`/`application`/`domain`/`infrastructure`. 12 pruebas: arranque, fronteras (Modulith), catálogo de módulos y capas, 6 reglas de arquitectura y tamaño de clases, comprobadas también con violaciones a propósito; Google Java Format con Spotless. Paquete `co.com.sapenlinea.liv`. Contratos OpenAPI en `contracts/openapi/` con interfaces generadas (T11, PR #40). Pendiente para otros entregables: Flyway, PostgreSQL y Testcontainers (F0‑08), BFF, Dockerfile y `dev/` (F0‑06), Error Prone y NullAway (F0‑07) |
| F0‑05 | Esqueleto de frontend | 🔄 | Workspace Angular 21.2 (Node 22), Tailwind 4, *tokens* desde el paquete `liv-components@0.1.0` instalado desde npm (versión exacta), ESLint con reglas de frontera (comprobadas), Prettier, Vitest; raíz sin pantallas. Faltan autenticación, *layout* y menú (necesitan F0‑05b y el BFF) y el cliente generado desde `api-spec/` |
| F0‑05a | Sistema de diseño en Figma + librería + Storybook | 🔄 | Hecho: estilos y hoja de fundamentos en Figma; proyecto `ui/` con `liv-components` **0.1.0 publicada en npm** (*tokens*), proceso de publicación documentado en `ui/README.md` con verificaciones automáticas (`npm run release:check`). Storybook con 11 componentes: `Icon`, `Logo`, `Spinner`, `Skeleton`, `Badge` y `Avatar` (T03); `Button`, `IconButton`, `TextField`, `CodeInput` y `Alert` (T04, PR #36). *Tokens* `border/strong` y `focus/ring` y placeholder de `CodeInput` corregidos para WCAG 2.1 AA (T04a, PR #38). Faltan los de T05, *script* que regenere los tokens desde Figma y regresión visual |
| F0‑05b | Diseños de *layout*, inicio de sesión y menú | ✅ | Rediseñados en Figma (versión 2) dentro de la feature [001 · Inicio de sesión](spec/features/001-inicio-de-sesion/spec.md): acceso de Keycloak en pantalla dividida con panel de marca, estructura general con menú lateral con iconos, inicio con tarjetas de módulos, aviso de sesión, sin permiso, carga y error. Aprobados por el responsable funcional el 2026-11-01, con sus ajustes |
| F0‑06 | Docker Compose local | 🔄 | `backend/dev/` con PostgreSQL 18.6 (bases `liv` y `keycloak`, roles de doc. 02 §5.2 y ADR 0025) y Keycloak 26.8.1, verificado con `check.sh` (T09 de la feature 001, PR #20). El *realm* `liv` se aplica como código con keycloak-config-cli y `check.sh` verifica el *realm* aplicado (T07, PR #26). Faltan MinIO, ClamAV y observabilidad |
| F0‑07 | GitHub Actions con compuertas de calidad | 🔄 | CI de PR mínimo (T02 de la feature 001): `backend-pr` y `frontend-pr` (Q01–Q03) y `ui-pr` (Q01; desde el 2026-11-02 también las pruebas de componentes con axe y la compilación de Storybook, Q02 y Q14), con filtro de ruta, `sistema-pr` (título Conventional Commits y gitleaks) y CODEOWNERS. Faltan publicación de imágenes y contratos (la de los contratos del backend, tarea aparte antes de T20, con su comparador de cambios incompatibles), cobertura, mutación, Sonar, guardián SDD y exigir los *checks* de cada proyecto (al pasar los *workflows* a "correr siempre y omitir trabajos"). Protección de `main` creada |
| F0‑08 | Roles de BD, RLS y primera migración | 🔄 | Migraciones Flyway `V202611010900`–`0920` (T10 de la feature 001, PR #25 y #28): esquemas `platform`, `core` y `audit`; entidades, administraciones, usuarios y sesiones con FK compuestas y RLS por `app.administration_id`; `core.find_user_by_identity` y `audit.record_platform_session_denied` (`SECURITY DEFINER`); roles y permisos de la aplicación; auditoría de solo inserción; ShedLock; Flyway solo en `dev` y `test`. 87 pruebas con Testcontainers como `liv_app`. Faltan las tablas tributarias y el *checklist* previo a Flyway del ERD, y las decisiones humanas listadas en la bitácora (2026-11-02) |
| F0‑09 | Auditoría, correlación y observabilidad | ⏳ | — |
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
| `02 Componentes` | `2:3` | Una sección por componente, con variantes, propiedades y descripción de uso: [Icon (38, Lucide)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-210), [Logo](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-228), [Button](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-298), [IconButton](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-364), [Badge](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-384), [Avatar](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-392), [Spinner](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-8), [Skeleton](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-397), [TextField](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-182), [CodeInput](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-244), [Alert](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-277), [NavItem](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-147), [SideNav](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-157), [AppHeader](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-319), [PageHeader](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-355), [ModuleCard](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-219), [ResultState](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-317), [Dialog](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-325); y, solo para el tema de Keycloak, [Keycloak/BrandPanel](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-361) y [Keycloak/Step](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-355) | ✅ Aprobados (2026-11-01). En `liv-components`: los de T03 y T04 (11); faltan los 7 de T05. 2026-11-02 (T04a): icono de ayuda de `TextField` y `CodeInput` a 16 px en las 14 variantes y texto de ejemplo de `CodeInput` en `text/muted` |
| `03 Pantallas` | `2:5` | Todas las pantallas, una sección por módulo. Hoy: [001 · Acceso (Keycloak con tema LIV)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=24-1163) (10 pantallas, una móvil) y [001 · Aplicación (estructura general)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1717) (5 pantallas) | ✅ Aprobadas (2026-11-01); secciones marcadas "✅ Aprobado" |
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
   - **T04a:** integrar el PR #38 (contraste de *tokens* e icono de ayuda; ya aplicado en Figma).
   - **T05:** los 7 componentes restantes y la versión 0.2.0, que publica un humano.
   - **Backlog de componentes:** `Tooltip`, primero en Figma; mientras tanto, `IconButton` usa `title` nativo.

---

## 7. Bitácora

Registro cronológico de lo realizado. Lo más reciente va primero.

### 2026-11-02

- Feature 001, T11 (PR #40): contratos OpenAPI del backend y generación de interfaces.
  - `core.yaml` (`GET /api/v1/me`), `platform.yaml` (`GET /api/v1/me/capabilities`) y `core-internal.yaml` (inicio y fin de sesión, solo para el BFF), con `openapi-generator-maven-plugin` 7.26.0 para Spring Boot 4.
  - Una regla de ArchUnit exige que todo controlador implemente su interfaz generada y no declare rutas propias, ni directas ni heredadas. El *build* valida cada contrato.
  - 110 pruebas en verde.
  - **Revisión:** dos adversariales y cuatro lentes. En la primera ronda, 0 críticos y 9 altos reportados (varios repetidos entre revisores), todos corregidos. En la segunda, 1 alto, introducido por una corrección (`issuer` como URI daba 500 en cada cierre), corregido con TDD. Por decisión del responsable, sin tercera ronda.
  - **Decisiones del responsable**, registradas en `plan.md`:
    - sin `sessionAuditId`;
    - `sid` igual al del *token* (401 con `001-14` si no) e idempotente;
    - `EndSession` siempre 204;
    - 401 con `001-14` para el usuario no registrado;
    - la publicación de contratos en CI, como tarea aparte de F0‑07 antes de T20.
  - Pendientes repartidos en `tasks.md`: a T12 (cabecera `X-User-Token`, tamaño de cabeceras y *logs*), a T13 y a T17.

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
