# Guía del desarrollador

> **Para quién:** la persona que se suma a LIV para desarrollar la lógica y las pantallas. **Fecha:** 2026-11-08.
> Esta guía no agrega reglas: resume dónde está el proyecto y apunta a las fuentes. Si algo choca con `AGENTS.md`, manda `AGENTS.md`.

## 1. Dónde está el proyecto

La arquitectura está aprobada (ADR 0001–0033) y estamos cerrando la **Fase 0 — Fundaciones**. El estado al día siempre está en [`ESTADO.md`](../ESTADO.md).

| Ya está hecho | Dónde verlo |
|---|---|
| **Feature 001 · Inicio de sesión**, de punta a punta: API y BFF con Keycloak (sin *tokens* en el navegador), segundo factor por rol, permisos, sesión, auditoría, menú, *layout* y pantallas de error, con pruebas de navegador en local | [`spec/features/001-inicio-de-sesion/`](../spec/features/001-inicio-de-sesion/spec.md). Es la **feature de referencia**: copia su forma |
| **Librería `liv-components` 0.3.1**, publicada en npm con 18 componentes | [`ui/README.md`](../ui/README.md) y el archivo de Figma [LIV‑Diseño](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o) |
| **CI obligatorio en `main`**: cada proyecto tiene su verificación y ningún PR entra sin ella | [`spec/features/003-ci-obligatorio/`](../spec/features/003-ci-obligatorio/spec.md) |
| **ADR 0001–0033**, uno por archivo | [`docs/adr/`](adr/) |
| **Feature 004, pasos 1 a 3 de 6:** el JSON Schema 1.0 de la configuración de jurisdicción, publicado como *release* `jsonschema-config-jurisdiccion-v1.0` | [`spec/features/004-esquema-configuracion-jurisdiccion/tasks.md`](../spec/features/004-esquema-configuracion-jurisdiccion/tasks.md) |

## 2. Por dónde empezar: tus primeras tareas

Ninguna feature de negocio empieza antes de cerrar la Fase 0 (`spec/constitution/roadmap.md`, ADR 0031). Para cerrarla faltan dos entregables, y son tu entrada al proyecto:

1. **Terminar la feature 004 (F0‑10)**, pasos 4 a 6 de su `tasks.md`:
   - **Paso 4 (T7, T8):** el validador de los anexos YAML contra el esquema publicado, en `tools/config-jurisdiccion/`. T7 la declara ruta crítica, y eso cambia `AGENTS.md`: necesita la aprobación del responsable.
   - **Paso 5 (T9 a T11):** el CI de la raíz que corre ese validador. Cambia `.github/`, así que también necesita su aprobación. Los dos *checks* nuevos del *ruleset* de `main` los agrega el responsable (T10).
   - **Paso 6 (T12):** el cierre de la feature.
2. **F0‑11 · Pruebas *golden* en rojo:** todavía no tiene feature; se abre como una nueva, con su spec y su plan. Convierte los ejemplos numéricos de los documentos funcionales (Doc. 02 §B.9, Doc. 03 §9.3 y Doc. 04 §5.2) en pruebas que fallan, porque el motor de liquidación todavía no existe. Es la mejor forma de aprender la lógica tributaria antes de escribirla.

Cuando las dos estén hechas, la Fase 0 se cierra, y lo siguiente es la Fase 1 del roadmap (vigencias fiscales, cargue de tablas, configuración de jurisdicción…).

## 3. Lecturas, en este orden

1. [`ESTADO.md`](../ESTADO.md): fase, entregables, decisiones abiertas y bitácora.
2. [`AGENTS.md`](../AGENTS.md): **cómo se trabaja aquí**, también para personas. Lo esencial está en §4 (SDD, evaluación previa, rutas críticas, lo prohibido sin aprobación) y §5 (reglas del dominio).
3. [`spec/README.md`](../spec/README.md) y la feature 001: cómo se escribe y se conduce una feature.
4. [`docs/funcional/`](funcional/), en el orden de `AGENTS.md` §3.1: qué hace el sistema.
5. [`docs/arquitectura/01`](arquitectura/01_Arquitectura_de_Software.md) (módulos, capas, glosario en §15) y [`04`](arquitectura/04_Calidad_Testing_y_CI.md) (código limpio, pruebas y CI).
6. El `AGENTS.md` y el `README.md` del proyecto donde vayas a trabajar (`backend/`, `frontend/`, `ui/`), con sus *skills* de código en su `.claude/skills/`.

## 4. Levantar LIV en tu equipo

Todo está en el [`README.md`](../README.md) de la raíz, «Ejecutar LIV en local»: requisitos (Docker Desktop, JDK 21, Node 22.12+ o 24+, y Git Bash en Windows), el atajo de un solo comando (`tools/dev/iniciar.sh`), el paso a paso, los usuarios de prueba y los problemas conocidos.

## 5. Cómo se trabaja, en una página

- **Antes de escribir nada**, la evaluación previa (`AGENTS.md` §4.3): flujo directo (erratas, defectos claros fuera de reglas y rutas críticas) o SDD (todo lo demás: reglas, contratos, datos, pantallas, rutas críticas).
- **SDD:** `spec.md` (H1, la aprueba el responsable) → Figma si hay pantallas → `plan.md` con revisión adversarial (H2) → `tasks.md` → TDD por tarea (`test:` en rojo, `feat:` en verde, `refactor:`) → revisión independiente → PR (H3).
- **Una feature a la vez.** El orquestador (*skill* `liv-orquestador`, ADR 0033) reparte su trabajo en sesiones y lleva el tablero de `ESTADO.md`.
- **Un PR toca un solo proyecto.** `backend/`, `frontend/` y `ui/` no se referencian entre sí: el frontend recibe la API por el OpenAPI fijado y la librería por npm con versión exacta (`AGENTS.md` §4.6).
- **Ningún valor tributario en el código:** tarifas, rangos, fechas y redondeo vienen de la configuración de jurisdicción (`AGENTS.md` §5.1).
- **Pantallas:** solo con componentes de `liv-components` y a partir de un diseño aprobado en Figma (`AGENTS.md` §6). Si falta un componente, primero va a la librería, en su propio PR.
- **Quien implementa no revisa ni integra.** Solo el responsable integra a `main`: tú abres los PR y él hace la revisión H3 y los integra (`AGENTS.md` §4.3).

## 6. Accesos que te tiene que dar el responsable

| Acceso | Para qué |
|---|---|
| Colaborador del repositorio en GitHub | Abrir PR y correr el CI. GitHub no exige aprobaciones, pero **no integres tus PR**: los integra solo el responsable, después de su H3 (`AGENTS.md` §4.3, excepción «un solo responsable integra») |
| Archivo de Figma LIV‑Diseño | Leer los diseños aprobados y diseñar las pantallas nuevas |
| npm (`liv-components`) | Solo si vas a publicar versiones de la librería: `npm owner add` lo ejecuta el dueño actual. Publicar es siempre de una persona, desde `main` |

Nunca uses datos reales de contribuyentes ni credenciales de entornos reales: en local, los usuarios y claves de prueba están en [`backend/dev/README.md`](../backend/dev/README.md).

## 7. Trampas conocidas

- **Windows:** usa Git Bash para todos los comandos.
- **`ESTADO.md`** tiene finales de línea CRLF: no lo reescribas entero. Edítalo en su lugar, prepáralo con `git -c core.autocrlf=false add ESTADO.md` y revisa con `git diff --cached --numstat` que solo cambien tus líneas.
- **gitleaks revisa cada *commit*,** no solo el resultado final: no escribas JWT ni claves en base64 literales en las pruebas, ni siquiera para borrarlas después.
- **Edge y un *service worker* ajeno:** si `localhost:4200` muestra un 404 en `/bff/login` o un ciclo de recargas, revisa en las herramientas del navegador › Application › Service workers que no haya un `sw.js` de otra aplicación registrado en ese puerto, y quítalo.

## 8. Pendientes conocidos (no bloquean el cierre)

- **Antes de producción:** la T24 de la feature 001 (imágenes, TLS, e2e en CI, CSP con *nonce*). No se despliega sin ella (ADR 0030).
- **F0‑03:** la plantilla de PR y los ganchos del agente y de Git.
- **Esquemas JSON** (de la feature 004; detalle en las descripciones de los PR #142 y #144):
  - `jsonschema.sh` y `publish.sh` dejan pasar una *release* con archivos de más, y `jsonschema.sh` no falla cuando no puede escribir sus archivos de trabajo. Va en un PR aparte del *backend*;
  - la *release* 1.0 quedó marcada como «Latest» en GitHub, aunque el paso 3 esperaba que no (visto en la lista de *releases* el 2026-11-08);
  - proteger las etiquetas `jsonschema-*` en un *ruleset*, con la aprobación del responsable.
- **Feature 002** (publicación automática de `liv-components`): su spec es una propuesta sin aprobar, en el PR #113 (borrador); todavía no está en `main`.
- **Decisiones abiertas del equipo** (P1, P6, P7) y de identidad: `ESTADO.md` §5 y §6.
