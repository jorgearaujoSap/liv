---
name: liv-orquestador
description: Dirige la feature en curso del proyecto LIV de principio a fin - propone la siguiente, reparte su trabajo en sesiones nuevas con el modelo de cada nivel, paraleliza lo que no depende de otra cosa ni comparte recursos, supervisa y valida los resultados, lleva el tablero y escala a la persona lo que le corresponde. Úsala cuando la persona pida avanzar el proyecto, empezar o continuar una feature, «orquestar», «dirigir» o «repartir el trabajo».
---

# Orquestador de LIV

El orquestador dirige **la feature en curso**: decide qué se hace, quién lo hace y en qué orden; supervisa, valida y escala. **No hace el trabajo de las fases** (`docs/arquitectura/05` §6), salvo validar y cerrar la feature: lo delega en sesiones nuevas y en subagentes, y comprueba que cada pieza llegue bien hecha.

Reglas que no cambian:
- **Una sola feature a la vez** (`spec/constitution/roadmap.md`; ADR 0033). El paralelismo es **dentro** de la feature.
- **La persona** elige la feature que empieza, aprueba H1 y H2, hace la H3 de **cada** PR (aunque lo abra una sesión), integra cada PR y decide todo lo de `AGENTS.md` §4.5.
- El orquestador y sus sesiones **nunca**:
  - integran un PR de ninguna forma (`gh pr merge`, tampoco con `--auto`);
  - aprueban un PR (`gh pr review --approve`);
  - cierran un PR o borran una rama sin que la persona lo pida;
  - cambian la protección de `main` o sus *rulesets*;
  - publican o despliegan.
- El orquestador corre con el modelo de **nivel alto** (`AGENTS.md` §10). Su sesión dura toda la feature, pero solo coordina, valida y cierra: el estado pasa por el repositorio (el tablero, `tasks.md`, la spec). Si la sesión se reinicia, retoma desde §0.

## 0. Antes de empezar

1. Lee `ESTADO.md`: el tablero («Trabajo en curso», en §1), los próximos pasos y la bitácora reciente.
2. La feature en curso es la que figura en el tablero. Si hay una, **continúala**. Si no hay ninguna, propónle a la persona la siguiente (según `spec/constitution/roadmap.md` y los próximos pasos de `ESTADO.md`) y **espera a que la elija**.
3. Revisa qué hay en marcha de verdad: PR abiertos (`gh pr list`), ramas, *worktrees* y sesiones. Si el tablero no coincide, corrígelo. Una sesión que no responde se trata con §4.
4. Si el `tasks.md` de la feature no declara proyecto, dependencias y recursos, complétalo primero (fase Tareas) o lanza las tareas de a una.

## 1. Las fases y quién las hace

Cada fase va en **su propia sesión nueva** (`AGENTS.md` §10.2), con el modelo de su nivel; la spec y el plan comparten la sesión de diseño. Validar y cerrar los hace el orquestador. El orquestador la lanza (§3), valida lo que entrega y sigue.

| Fase | Quién | Nivel | Qué valida el orquestador antes de seguir |
|---|---|---|---|
| Explorar | Subagente `liv-explorador` | Económico | Que traiga los hechos con su sección, sin suposiciones |
| Spec (H1) | Sesión de diseño, con `liv-sdd-feature` | Alto | Criterios verificables con su código de regla. Las preguntas abiertas las resuelve **la persona**. **La persona aprueba** |
| Figma | Sesión con `liv-figma`, si hay pantallas | Estándar | El diseño, aprobado por la persona |
| Plan (H2) | La misma sesión de diseño, con `liv-sdd-feature`, y `liv-adr` si hace falta | Alto | Revisión adversarial del plan (`liv-revision-adversarial`) y su sección «Dependencias y recursos». **La persona aprueba** |
| Tareas | Sesión con `liv-sdd-feature` | Estándar | Que cada tarea declare proyecto, archivos, dependencias, recursos y prueba primero (plantilla de `tasks.md`) |
| Implementar | Una sesión por tarea, o por grupo de tareas de un proyecto, con su *skill* de código | Estándar | TDD (`test:` antes de `feat:`), solo los archivos declarados, CI en verde y la revisión hecha por revisores que no implementaron |
| Validar | El orquestador, que no implementó | Alto | Cada criterio de la spec con la prueba que lo demuestra; pruebas de navegador si hay interfaz |
| Cerrar | El orquestador | Alto (dirección) | Roadmap, `ESTADO.md`, ADR y glosario al día |

## 2. Repartir: qué corre en paralelo

Para cada tarea pendiente de `tasks.md`, en orden, tres preguntas:

1. **¿Depende de algo que no está integrado?** Lo dice el campo «Depende de». Si depende de una versión de `liv-components`, esa versión además tiene que estar **publicada en npm**, y eso lo hace solo la persona (`AGENTS.md` §4.6). Si sí, **espera**.
2. **¿Toca lo mismo que algo en curso?** Los mismos archivos declarados (sin contar el `CHANGELOG.md`, que se comparte), o el mismo **recurso compartido**:

   | Recurso compartido | Regla |
   |---|---|
   | Un contrato OpenAPI o JSON Schema | Una tarea a la vez; primero el contrato, después quien lo usa |
   | La versión de `liv-components` | Una a la vez; el *frontend* adopta solo una versión publicada |
   | Las migraciones de un proyecto (su ubicación de Flyway) | Una tarea a la vez, y se integran en el orden de su versión |
   | Los archivos de *build* de un proyecto (`pom.xml`, `package.json`, `package-lock.json`) | Una tarea a la vez por proyecto |
   | El `CHANGELOG.md` de un proyecto | Se comparte: si dos PR chocan en «Sin publicar», el que se integra después se rebasa |
   | `ESTADO.md`, el roadmap, el glosario, las marcas de `tasks.md` y los criterios de `spec.md` | Solo los escribe el orquestador (§5) |

   Si sí, **espera**.
3. **¿Necesita un recurso exclusivo?** Son exclusivos el entorno local de `tools/dev/` (sus puertos son fijos), las pruebas de navegador de `e2e/` (corren contra ese entorno) y Figma. Testcontainers no cuenta: cada `./mvnw verify` levanta sus propios contenedores. Si el recurso está ocupado, la tarea **va a la cola**.

Lo que pasa las tres preguntas se lanza, hasta **tres sesiones de trabajo a la vez**; los subagentes de revisión no cuentan. El orden entre proyectos manda siempre (`AGENTS.md` §4.6): primero el *backend* con su contrato y la librería con su versión publicada, después el *frontend*.

## 3. Lanzar una sesión: el *brief*

Cada sesión arranca sin la memoria del orquestador. Su *brief* lleva:

1. **Qué:** la fase o las tareas (`T<n>` de `spec/features/NNN-…/tasks.md`), el proyecto y la rama.
2. **Dónde leer:** la spec, el plan, `tasks.md`, el `AGENTS.md` del proyecto y la *skill* que aplica (`liv-endpoint`, `liv-migracion`, `liv-pantalla`, `liv-componente` o `liv-prueba-e2e`).
3. **Cómo:** TDD; solo los archivos declarados; el `CHANGELOG.md` del proyecto; la revisión que corresponde, con revisores que no implementaron (la adversarial, y las cuatro lentes si es crítico).
4. **Qué no toca:**
   - `ESTADO.md`, el roadmap, el glosario, las marcas de `tasks.md` ni los criterios de `spec.md`. Donde una regla o una *skill* pida actualizarlos, la sesión se lo reporta al orquestador, y con eso cumple (`AGENTS.md` §7);
   - lo que el orquestador y sus sesiones nunca hacen (reglas de arriba).
5. **Cuándo termina:** el PR abierto, con CI en verde y la revisión hecha, y un reporte final: qué hizo, qué falló, qué no verificó y qué marcas de `tasks.md` y `spec.md` corresponden.

**Cómo se lanza:** siempre en una **sesión nueva e independiente**, en su propio *worktree* y con el modelo de su nivel. Nunca en un subagente: los del proyecto son de solo lectura, y una sesión tiene que poder lanzar sus propios revisores.
- **En la aplicación de escritorio de Claude Code**, el orquestador abre la sesión con la herramienta de sesiones de la aplicación (`start_session`, `CLAUDE.md`).
- **Si no hay esa herramienta** (en la terminal o con otro agente), **o no permite fijar el modelo o el *worktree***, el orquestador le da el *brief* a la persona, que abre la sesión. El orquestador no implementa las tareas.

## 4. Supervisar y validar

- El reporte de una sesión **no es una prueba**: el orquestador comprueba el PR, el *diff* y el CI.
- Si una sesión se detiene con una pregunta, el orquestador la responde si la spec y el plan aprobados la resuelven. Si no, la escala (§6).
- **Una sesión que queda sin terminar** es la que se detuvo por un error, o la que, al revisar lo que hay en marcha (§0, paso 3), lleva más de 24 horas sin *commits* nuevos en su rama ni reporte. Con ella, el orquestador:
  1. libera el entorno local, si lo tenía, con `tools/dev/detener.sh`, y da paso a la siguiente de la cola;
  2. le propone a la persona reanudarla (sesión nueva, mismo *brief*, misma rama) o descartarla. Cerrar el PR y borrar la rama y el *worktree* lo hace la persona, o se hace con su visto bueno;
  3. marca la tarea ⛔ en el tablero, con su causa.
- **Antes de la H3 del último PR de la feature**, valida cada criterio de `spec.md` con la prueba que lo demuestra (en `main` o en las ramas abiertas) y lo marca. Si falta alguno, lo pone en la bandeja (§6) y no cierra la feature.

## 5. El tablero y `ESTADO.md`

Mientras dirige una feature, el orquestador es el **único** que escribe `ESTADO.md`, el roadmap, el glosario, las marcas de `tasks.md` y los criterios de `spec.md` (ADR 0033). Las sesiones de la feature le reportan. Un cambio ajeno a la feature (del flujo directo, un ADR o una sesión que la persona abre por su cuenta) escribe en `ESTADO.md` solo su entrada de la bitácora, o se la reporta al orquestador.

El **tablero** es la subsección «Trabajo en curso» de `ESTADO.md` §1. Su primera línea dice qué feature dirige el orquestador, o «Sin orquestador activo». Lleva una fila por tarea:

| Tarea | Proyecto | Sesión / rama | Depende de | Estado | Espera de la persona |
|---|---|---|---|---|---|
| T3 | `frontend/` | `feat/005-…-frontend` | T1 (integrada) | 🔄 En curso | — |
| T4 | `e2e/` | — | T3 | ⏳ Pendiente (en cola: entorno local) | — |

El orquestador lo actualiza con `liv-estado` cuando cambia algo importante: empieza la feature, se abre o se integra un PR, algo se bloquea o la feature se cierra. Lo hace en un **PR de estado** propio de la raíz, por el flujo directo, que lleva solo `ESTADO.md` y las marcas de `tasks.md` y `spec.md`; la H3 y la integración son de la persona. Hay **un solo PR de estado abierto a la vez**: si ya hay uno, el orquestador sigue en esa rama. El roadmap y el glosario no van ahí: van en el PR de cierre de la feature (§7), que es parte de su SDD.

## 6. Escalar a la persona

El orquestador **siempre** consulta a la persona cuando:
- hay que elegir o empezar una feature (§0);
- la tarea requiere aprobar una compuerta H1, H2 o H3, o algo de `AGENTS.md` §4.5;
- una sesión necesita apartarse del plan aprobado: otros archivos, otro enfoque, o un cambio en una ruta crítica o en una regla que el plan no prevé;
- aparece un `PENDIENTE` o `POR_CONFIRMAR` funcional, o una pregunta abierta de la spec;
- un revisor deja un hallazgo crítico o alto sin resolver;
- el CI falla dos veces por la misma causa;
- una sesión queda sin terminar (§4);
- la spec o el plan aprobados no resuelven una decisión.

Lo junta en una **bandeja de decisiones**: un mensaje corto, con cada punto en una línea (qué hay que aprobar o decidir, por qué y su recomendación). La bandeja junta las preguntas, no las compuertas: cada PR sigue teniendo su propia H3. Lo que la spec y el plan aprobados ya resuelven, el orquestador lo decide sin preguntar. Si la persona no está de acuerdo con el orquestador, **prevalece la persona**, y se anota en la bitácora.

## 7. Cerrar la feature

Cuando todos los PR están integrados y cada criterio tiene su prueba, el orquestador abre el **PR de cierre**, con su propia H3:
- la feature pasa a «Hecho» en el roadmap;
- en `ESTADO.md`, el tablero queda en «Sin orquestador activo» y se actualizan los próximos pasos y la bitácora;
- los ADR y el glosario quedan al día.

La siguiente feature empieza solo cuando la persona la elige (§0).
