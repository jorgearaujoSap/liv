# Estado del proyecto

> **Última actualización:** 2026-11-01
> **Regla:** este archivo se actualiza en el mismo cambio que completa, inicia o bloquea una tarea (humano o agente de IA). Si no está al día, el cambio no está terminado (`docs/arquitectura/04_Calidad_Testing_y_CI.md` §7, `docs/arquitectura/05_Gobierno_de_Agentes_IA_y_SDD.md` §4.1).
> **Estados usados:** ✅ Hecho · 🔄 En curso · ⏳ Pendiente · ⛔ Bloqueado

---

## 1. Resumen

| Tema | Estado |
|---|---|
| Fase actual | **Fase 0 — Fundaciones**, en definición. Todavía no hay código |
| Documentación funcional (01–04, ERD) | ✅ Existente (insumo del proyecto) |
| Arquitectura (00–05) | 🔄 Escrita, en estado **Propuesto**: pendiente de aprobación del equipo |
| Diseño en Figma | 🔄 Estructura de páginas, estilos locales y hoja de fundamentos hechos; sin componentes ni pantallas |
| Decisiones del equipo | ✅ 8 de 10 decididas; P6 espera a la entidad y P7 es provisional (§5) |

---

## 2. Documentación

| Documento | Estado | Versión |
|---|---|---|
| `docs/funcional/`: `01_Menu_y_Cargue_Tablas_Base_Liquidacion.md` · `02_…` · `03_…` · `04_…` · `ERD_…` | ✅ Existente | 0.1–0.4 |
| `docs/funcional/anexos/`: `config_jurisdiccion_plantilla_2026.yaml` · `config_jurisdiccion_52356_2026.yaml` · `Prevalidacion_Tablas_Base_Gravable_2026.xlsx` | ✅ Existente | Esquema 1.0 |
| `README.md` | ✅ Creado: mapa del repositorio | — |
| `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` | 🔄 Propuesto | 0.1 |
| `docs/arquitectura/01_Arquitectura_de_Software.md` | 🔄 Propuesto | 0.1 |
| `docs/arquitectura/02_Seguridad.md` | 🔄 Propuesto | 0.1 |
| `docs/arquitectura/03_Escalabilidad_y_Operacion.md` | 🔄 Propuesto | 0.1 |
| `docs/arquitectura/04_Calidad_Testing_y_CI.md` | 🔄 Propuesto | 0.1 |
| `docs/arquitectura/05_Gobierno_de_Agentes_IA_y_SDD.md` | 🔄 Propuesto | 0.1 |
| `AGENTS.md` | ✅ Creado: reglas y metodología para cualquier agente de IA; fuente única de reglas | — |
| `CLAUDE.md` | ✅ Importa `AGENTS.md` y agrega solo lo específico de Claude Code | — |
| `ESTADO.md` (este archivo) | ✅ Creado | — |
| `spec/` (README, constitución y plantilla de feature) | ✅ Creado, alineado con la plantilla SDD del curso | — |

---

## 3. Entregables de la Fase 0

Definidos en `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` §8.1.

| # | Entregable | Estado | Detalle |
|---|---|---|---|
| F0‑01 | Monorepo y traslado de documentos a `docs/` | 🔄 | Documentación reorganizada en `docs/funcional/` y `docs/arquitectura/`; reglas de independencia backend/frontend definidas (doc. 01 §14.1). `backend/` y `frontend/` se crean con F0‑04 y F0‑05 |
| F0‑02 | ADR 0001–0020 aceptados | ⏳ | Propuestos en doc. 00 §6; ninguno aceptado aún |
| F0‑03 | `AGENTS.md`/`CLAUDE.md`, *skills*, plantillas SDD, ganchos | 🔄 | `AGENTS.md`, `CLAUDE.md`, `ESTADO.md` y plantillas SDD (`spec/`) creados. Faltan *skills* del proyecto, plantilla de PR y ganchos del agente y de Git |
| F0‑04 | Esqueleto de backend con reglas de arquitectura | ⏳ | — |
| F0‑05 | Esqueleto de frontend con `@liv/ui` | ⏳ | — |
| F0‑05a | Sistema de diseño en Figma + librería `@liv/ui` + Storybook | 🔄 | Hecho en Figma: estilos locales y hoja de fundamentos (§4). Faltan componentes en Figma, librería en código, Storybook y regresión visual |
| F0‑05b | Diseños de *layout*, inicio de sesión y menú | ⏳ | — |
| F0‑06 | Docker Compose local | ⏳ | — |
| F0‑07 | GitHub Actions con compuertas de calidad | ⏳ | — |
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
| `02 Componentes` | `2:3` | — | ⏳ Vacía |
| `03 Pantallas` | `2:5` | Todas las pantallas, una sección por módulo | ⏳ Vacía |
| `99 Archivo` | `2:9` | Diseños reemplazados | ⏳ Vacía |

| Estilos locales | Cantidad | Estado |
|---|---|---|
| Color (`text/`, `bg/`, `border/`, `action/`, `status/`) | 26 | ✅ Paleta inicial propuesta; pendiente de validación del responsable funcional o de colores institucionales |
| Texto (Inter, JetBrains Mono) | 11 | ✅ |
| Efecto (`elevation/sm`–`xl`, `focus/ring`) | 5 | ✅ |
| Espaciado y radios (variables) | 0 | ⏳ Se definen con los primeros componentes |

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

1. Revisar y aprobar (o ajustar) la serie de arquitectura 00–05 y los ADR 0001–0020.
2. Pendientes de las decisiones: elegir la región de AWS con jurídica (P1), recibir de la entidad la disponibilidad, el RPO y el RTO (P6), y la tabla de retención documental (P7).
3. Validar con el responsable funcional la paleta de color propuesta o reemplazarla por los colores institucionales.
4. Iniciar la Fase 0 por F0‑01 (reorganización del repositorio) y completar F0‑03 (*skills*, plantilla de PR y ganchos).

---

## 7. Bitácora

Registro cronológico de lo realizado. Lo más reciente va primero.

### 2026-11-01

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
