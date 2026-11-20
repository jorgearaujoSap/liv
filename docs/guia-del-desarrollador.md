# Guía del desarrollador

> **Para quién:** la persona que se suma a LIV para desarrollar la lógica y las pantallas. **Fecha:** 2026-11-08; método de trabajo al día el 2026-11-18.
> Esta guía no agrega reglas: resume dónde está el proyecto y apunta a las fuentes. Si algo choca con `AGENTS.md`, manda `AGENTS.md`.

## 1. Dónde está el proyecto

La arquitectura está aprobada: ADR 0001–0035, 0040 y 0041 (los 0036 a 0039 los reserva la feature 008). La **Fase 0 — Fundaciones** se cerró el 2026-11-12 y estamos en la **Fase 1 — Tablas base y configuración**. El estado al día siempre está en [`ESTADO.md`](../ESTADO.md).

| Ya está hecho | Dónde verlo |
|---|---|
| **Feature 001 · Inicio de sesión**, de punta a punta: API y BFF con Keycloak (sin *tokens* en el navegador), segundo factor por rol, permisos, sesión, auditoría, menú, *layout* y pantallas de error, con pruebas de navegador en local | [`spec/features/001-inicio-de-sesion/`](../spec/features/001-inicio-de-sesion/spec.md). Es la **feature de referencia**: copia su forma |
| **Librería `liv-components` 0.5.0**, publicada en npm | [`ui/README.md`](../ui/README.md) y el archivo de Figma [LIV‑Diseño](https://www.figma.com/design/s2r2Kdu31sQW5CmVeQNyZb/LIV-Dise%C3%B1o) |
| **CI obligatorio en `main`**: cada proyecto tiene su verificación y ningún PR entra sin ella | [`spec/features/003-ci-obligatorio/`](../spec/features/003-ci-obligatorio/spec.md) |
| **ADR 0001–0035, 0040 y 0041**, uno por archivo | [`docs/adr/`](adr/) |
| **Features 005, 006 y 007:** cartera y recaudo (documentación y contrato), pruebas *golden* en rojo (cierre de la Fase 0) y **vigencias fiscales (1.4)**, la primera feature de negocio de punta a punta | [`spec/features/`](../spec/features/) y [`spec/constitution/roadmap.md`](../spec/constitution/roadmap.md) |
| **Método de trabajo:** SDD por rebanadas verticales, con el backlog en GitHub Issues y en el *Project* `liv-project` (ADR 0040 y 0041) | [`AGENTS.md`](../AGENTS.md) §4 y [`spec/README.md`](../spec/README.md) |
| **Feature 004 · Esquema de la configuración de jurisdicción (F0‑10):** el JSON Schema 1.0, inmutable en cada PR y publicado como *release* `jsonschema-config-jurisdiccion-v1.0`. El validador de los anexos en la raíz y su CI pasaron a la feature 1.5.9 | [`spec/features/004-esquema-configuracion-jurisdiccion/`](../spec/features/004-esquema-configuracion-jurisdiccion/spec.md) |

## 2. Por dónde empezar

La Fase 0 se cerró el 2026-11-12 (ADR 0031) y estamos en la **Fase 1 — Tablas base y configuración**. Las features 005, 006 y 007 están hechas, y la 007 · Vigencias fiscales (1.4) es la mejor referencia de una opción del menú de punta a punta.

Desde el 2026-11-18 se trabaja con **SDD por rebanadas verticales** (ADR 0040). La feature en construcción y lo siguiente están en [`ESTADO.md`](../ESTADO.md) §1 y en los *issues* de GitHub con la etiqueta `feature:NNN`.

## 3. Lecturas, en este orden

1. [`ESTADO.md`](../ESTADO.md): fase, entregables, decisiones abiertas y bitácora.
2. [`AGENTS.md`](../AGENTS.md): **cómo se trabaja aquí**, también para personas. Lo esencial está en §4 (SDD por rebanadas, tamaños de trabajo, zonas rojas, lo prohibido sin aprobación) y §5 (reglas del dominio).
3. [`spec/README.md`](../spec/README.md): cómo se arranca una feature, el formato del *issue* de una rebanada y cómo se cierra.
4. [`docs/funcional/`](funcional/), en el orden de `AGENTS.md` §3.1: qué hace el sistema.
5. [`docs/arquitectura/01`](arquitectura/01_Arquitectura_de_Software.md) (módulos, capas, glosario en §15) y [`04`](arquitectura/04_Calidad_Testing_y_CI.md) (código limpio, pruebas y CI).
6. El `AGENTS.md` y el `README.md` del proyecto donde vayas a trabajar (`backend/`, `frontend/`, `ui/`), con sus *skills* de código en su `.claude/skills/`.

## 4. Levantar LIV en tu equipo

Todo está en el [`README.md`](../README.md) de la raíz, «Ejecutar LIV en local»: requisitos (Docker Desktop, JDK 21, Node 22.12+ o 24+, y Git Bash en Windows), el atajo de un solo comando (`tools/dev/iniciar.sh`), el paso a paso, los usuarios de prueba y los problemas conocidos.

## 5. Cómo se trabaja, en una página

- **Esfuerzo según lo que cuesta cambiar** (ADR 0040): con cuidado en los datos, los contratos, las reglas tributarias y la seguridad; rápido en las pantallas, los textos y el código interno.
- **Antes de escribir nada, el tamaño** (`AGENTS.md` §4.3):
  - **ajuste** (errata, defecto claro, refactorización local, prueba nueva): un PR directo;
  - **rebanada:** un *issue* del backlog;
  - **feature:** un `spec.md` corto y su backlog.
- **Feature:** `spec.md` con reglas, alcance del MVP, criterios, diseño técnico y rebanadas → H1 → un *issue* por rebanada → cada rebanada de punta a punta → cierre con pruebas de navegador, deuda revisada y la opción abierta en el menú.
- **Rebanada:** contrato primero, después backend y frontend (en paralelo si el contrato ya está). Se ve en local, se ajusta y entra con sus pruebas y el CI en verde, tras «Próximamente». La prueba primero es obligatoria solo en las reglas y los cálculos tributarios.
- **Zonas rojas y reglas** (lista completa en `AGENTS.md` §4.3): nada es un ajuste y cada PR pasa la revisión adversarial, con dos revisores ciegos. Es la única revisión de agentes: el resto entra con el CI y tu H3.
- **Backlog y deuda en GitHub Issues** (*skill* `liv-backlog`): el agente propone los *issues* nuevos que encuentra (defectos, mejoras, deuda) y los crea solo con tu aprobación. Con más de cinco *issues* `deuda` abiertos, la siguiente rebanada es de limpieza.
- **Un PR toca un solo proyecto.** `backend/`, `frontend/` y `ui/` no se referencian entre sí: el frontend recibe la API por el OpenAPI fijado y la librería por npm con versión exacta (`AGENTS.md` §4.6).
- **Ningún valor tributario en el código:** tarifas, rangos, fechas y redondeo vienen de la configuración de jurisdicción (`AGENTS.md` §5.1).
- **Pantallas:** solo con componentes de `liv-components` y a partir de un diseño aprobado en Figma (`AGENTS.md` §6). Si falta un componente, primero va a la librería, en su propio PR.
- **La revisión de agentes es solo la adversarial**, en las zonas rojas y las reglas; integrar a `main` es siempre de una persona. Tus propios PR los revisas (H3) y los integras tú (`AGENTS.md` §4.3, excepción «quien dirige el trabajo decide»).

## 6. Quién decide y qué accesos necesitas

**Decides tú.** Quien dirige el trabajo —la persona que interactúa con el agente y le da las instrucciones— da todas las aprobaciones humanas: la H1, el visto bueno de cada rebanada, la H3 y las de `AGENTS.md` §4.5 (dependencias, CI, reglas, protección de `main`). Revisas e integras tus propios PR. Un agente nunca aprueba ni integra (`AGENTS.md` §4.3, excepción «quien dirige el trabajo decide»); solo ejecuta por ti, con tu confirmación en el chat, las acciones de la publicación de `liv-components` que lista `AGENTS.md` §4.5 (ADR 0043).

Los accesos los da el dueño actual del repositorio, una sola vez:

| Acceso | Para qué |
|---|---|
| Colaborador del repositorio en GitHub, con rol de administrador | Abrir PR, hacer la H3 e integrarlos, y cambiar la protección de `main` cuando una tarea lo pida (como T10) |
| Archivo de Figma LIV‑Diseño | Leer los diseños aprobados y diseñar las pantallas nuevas |
| Entorno `npm` de GitHub (`liv-components`) | Solo si vas a aprobar la publicación de versiones de la librería: el responsable te agrega como revisor del entorno `npm`. No hace falta cuenta ni permiso en npm: publica el *workflow* (ADR 0042). Puedes aprobar desde la página de la ejecución o confirmándole al agente en el chat, con los datos a la vista, sin ir a GitHub (ADR 0043; `ui/README.md` › «Publicar una versión» › «Aprobar desde el chat») |

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
  - proteger las etiquetas `jsonschema-*` en un *ruleset*, con tu aprobación explícita.
- **Feature 002** (publicación automática de `liv-components`): en construcción desde la H1 del 2026-11-19, con el ADR 0042 aceptado; sus rebanadas son los *issues* #231 a #233. La 008 queda en pausa hasta cerrarla.
- **Decisiones abiertas del equipo** (P1, P6, P7) y de identidad: `ESTADO.md` §5 y §6.
