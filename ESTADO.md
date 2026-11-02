# Estado del proyecto

> **Última actualización:** 2026-11-01
> **Regla:** este archivo se actualiza en el mismo cambio que completa, inicia o bloquea una tarea (humano o agente de IA). Si no está al día, el cambio no está terminado (`docs/arquitectura/04_Calidad_Testing_y_CI.md` §7, `docs/arquitectura/05_Gobierno_de_Agentes_IA_y_SDD.md` §4.1).
> **Estados usados:** ✅ Hecho · 🔄 En curso · ⏳ Pendiente · ⛔ Bloqueado

---

## 1. Resumen

| Tema | Estado |
|---|---|
| Fase actual | **Fase 0 — Fundaciones**: arquitectura aprobada; esqueletos de `backend/`, `frontend/` y `ui/` (librería `liv-components`) creados y en verde |
| Código | 🔄 Esqueletos sin funcionalidad de negocio: backend (Maven, Java 21, Spring Boot 4.1.2, 12 pruebas), frontend (Angular 21.2, Tailwind 4, ESLint, 2 pruebas) y librería `liv-components` **0.1.0 publicada en npm** (*tokens*) |
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
| F0‑02 | ADR 0001–0021 aceptados | 🔄 | Los 25 aceptados el 2026-11-01 (tabla del doc. 00 §6). Creados `docs/adr/` y los archivos de los ADR 0023–0025 (feature 001, aceptados con su H2). Faltan los archivos de los ADR 0001–0022 con la *skill* `liv-adr` |
| F0‑03 | `AGENTS.md`/`CLAUDE.md`, *skills*, plantillas SDD, ganchos | 🔄 | `AGENTS.md`, `CLAUDE.md`, `ESTADO.md`, plantillas SDD (`spec/`), *skills* de proceso (`.claude/skills/`) y política de modelos con 4 subagentes por nivel (`AGENTS.md` §10, `.claude/agents/`) creados. Faltan plantilla de PR y ganchos del agente y de Git; las *skills* de código se crean con F0‑04 y F0‑05 |
| F0‑04 | Esqueleto de backend con reglas de arquitectura | ✅ | Maven (*wrapper* 3.10.0, un artefacto `liv-api` en la raíz de `backend/`), Java 21, Spring Boot 4.1.2, Spring Modulith 2.1.1; base del equipo mejorada (ADR 0021): módulos `shared`, `platform` y 8 de negocio como paquetes con capas `api`/`application`/`domain`/`infrastructure`. 12 pruebas: arranque, fronteras (Modulith), catálogo de módulos y capas, 6 reglas de arquitectura y tamaño de clases, comprobadas también con violaciones a propósito; Google Java Format con Spotless. Paquete `co.com.sapenlinea.liv`. Pendiente para otros entregables: Flyway, PostgreSQL y Testcontainers (F0‑08), OpenAPI en `contracts/`, BFF, Dockerfile y `dev/` (F0‑06), Error Prone y NullAway (F0‑07) |
| F0‑05 | Esqueleto de frontend | 🔄 | Workspace Angular 21.2 (Node 22), Tailwind 4, *tokens* desde el paquete `liv-components@0.1.0` instalado desde npm (versión exacta), ESLint con reglas de frontera (comprobadas), Prettier, Vitest; raíz sin pantallas. Faltan autenticación, *layout* y menú (necesitan F0‑05b y el BFF) y el cliente generado desde `api-spec/` |
| F0‑05a | Sistema de diseño en Figma + librería + Storybook | 🔄 | Hecho: estilos y hoja de fundamentos en Figma; proyecto `ui/` con `liv-components` **0.1.0 publicada en npm** (*tokens*), proceso de publicación documentado en `ui/README.md` con verificaciones automáticas (`npm run release:check`). Faltan componentes (primero en Figma), *script* que regenere los tokens desde Figma, Storybook y regresión visual |
| F0‑05b | Diseños de *layout*, inicio de sesión y menú | ✅ | Rediseñados en Figma (versión 2) dentro de la feature [001 · Inicio de sesión](spec/features/001-inicio-de-sesion/spec.md): acceso de Keycloak en pantalla dividida con panel de marca, estructura general con menú lateral con iconos, inicio con tarjetas de módulos, aviso de sesión, sin permiso, carga y error. Aprobados por el responsable funcional el 2026-11-01, con sus ajustes |
| F0‑06 | Docker Compose local | ⏳ | — |
| F0‑07 | GitHub Actions con compuertas de calidad | 🔄 | CI de PR mínimo (T02 de la feature 001): `backend-pr` y `frontend-pr` (Q01–Q03) y `ui-pr` (Q01 hasta T03), con filtro de ruta, `sistema-pr` (título Conventional Commits y gitleaks), Dependabot y CODEOWNERS. Faltan publicación de imágenes y contratos, cobertura, mutación, Sonar, guardián SDD y la protección de la rama `main` (configuración del repositorio, la hace el dueño) |
| F0‑08 | Roles de BD, RLS y primera migración | ⏳ | — |
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
| `02 Componentes` | `2:3` | Una sección por componente, con variantes, propiedades y descripción de uso: [Icon (38, Lucide)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-210), [Logo](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=19-228), [Button](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-298), [IconButton](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-364), [Badge](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-384), [Avatar](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-392), [Spinner](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-8), [Skeleton](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=20-397), [TextField](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-182), [CodeInput](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-244), [Alert](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=21-277), [NavItem](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-147), [SideNav](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-157), [AppHeader](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-319), [PageHeader](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=22-355), [ModuleCard](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-219), [ResultState](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-317), [Dialog](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-325); y, solo para el tema de Keycloak, [Keycloak/BrandPanel](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-361) y [Keycloak/Step](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=23-355) | ✅ Aprobados (2026-11-01). Aún no existen en `liv-components` |
| `03 Pantallas` | `2:5` | Todas las pantallas, una sección por módulo. Hoy: [001 · Acceso (Keycloak con tema LIV)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=24-1163) (10 pantallas, una móvil) y [001 · Aplicación (estructura general)](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o?node-id=26-1717) (5 pantallas) | ✅ Aprobadas (2026-11-01); secciones marcadas "✅ Aprobado" |
| `99 Archivo` | `2:9` | Diseños reemplazados | ⏳ Vacía |

| Estilos locales | Cantidad | Estado |
|---|---|---|
| Color (`text/`, `bg/`, `border/`, `action/`, `status/`) | 28 | ✅ Paleta inicial propuesta; pendiente de validación del responsable funcional o de colores institucionales. 2026-11-01: se agregan `text/inverse-secondary` y `bg/inverse-subtle` para el panel de marca |
| Texto (Inter, JetBrains Mono) | 13 | ✅ 2026-11-01: se agregan `heading/display` (panel de marca) y `code/lg` (códigos de verificación). Falta llevar los 4 estilos nuevos a `theme.css` de `liv-components` (tarea T03 de la feature 001) |
| Efecto (`elevation/sm`–`xl`, `focus/ring`) | 5 | ✅ |
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

---

## 7. Bitácora

Registro cronológico de lo realizado. Lo más reciente va primero.

### 2026-11-01

- Integrado el PR #7 (documentación de la feature 001, ADR 0023–0025 y política de modelos).
- Preparado T02 (F0‑07, CI de PR mínimo), en la rama `chore/f0-07-ci-pr`:
  - cuatro *workflows* con acciones fijadas por SHA, Dependabot y CODEOWNERS;
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
