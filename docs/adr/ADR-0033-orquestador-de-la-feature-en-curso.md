# ADR-0033 · Orquestador de la feature en curso: una feature a la vez, paralelismo dentro de ella y un solo escritor de `ESTADO.md`

- **Estado:** Aceptado
- **Fecha:** 2026-11-08
- **Decisores:** responsable del proyecto, que lo decidió en la sesión del 2026-11-08 (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** `docs/arquitectura/05` §4.1, §6 (roles) y §7.2; `AGENTS.md` §4.3, §4.4, §7, §9, §10 y §11; `spec/constitution/roadmap.md`; *skill* `liv-orquestador`; ADR 0014 y 0028

## Contexto

El doc. 05 §6 ya define el rol de **orquestador**: conduce el flujo SDD, delega en subagentes y mantiene el estado del cambio. Pero no hay un procedimiento escrito, así que hasta hoy ese papel lo cumplía un agente a mano en cada conversación:
- escribía los *briefs* de cada sesión;
- decidía qué podía correr en paralelo;
- seguía los resultados y le pedía las aprobaciones a la persona.

En las features 003 y 004 eso funcionó, pero dejó cuatro problemas a la vista:
- las dependencias entre tareas no estaban escritas;
- varios PR en paralelo chocaron en `ESTADO.md`;
- las sesiones se pisaron en el entorno local;
- el criterio para escalar a la persona dependía de la conversación.

## Opciones consideradas

1. **Seguir sin procedimiento.** Cada conversación orquesta a su manera. No escala, y el estado vive en el historial de la conversación, no en el repositorio.
2. **Un orquestador con varias features en paralelo.** Más rápido, pero choca con la regla de una sola feature en curso (`spec/constitution/roadmap.md`) y multiplica lo que la persona tiene que revisar.
3. **Un orquestador de la feature en curso, con paralelismo solo dentro de ella.** Mantiene el control de una feature a la vez y aprovecha el paralelismo entre las tareas que no dependen unas de otras.

## Decisión

Opción 3, elegida por el responsable para mantener el control con una feature a la vez:

- **Una sola feature a la vez**, como dice la constitución. La persona elige cuál empieza, y solo después de cerrar la anterior.
- **El orquestador coordina y no hace el trabajo de las fases.** Cada fase va en su propia sesión nueva, con el modelo de su nivel: la spec y el plan, juntos, en el nivel alto; las tareas, Figma y la implementación en el estándar. El orquestador valida los criterios antes de la H3 del último PR y cierra la feature, con el nivel alto. Su sesión dura la feature, y el estado pasa por el repositorio.
- **Las sesiones son independientes**, cada una en su *worktree*: en la aplicación de escritorio de Claude Code las abre el orquestador; sin esa herramienta, la persona, con el *brief* del orquestador. Nunca son subagentes.
- **Paralelismo dentro de la feature.** Una tarea se lanza si cumple tres condiciones:
  - no depende de otra sin integrar (ni de una versión de `liv-components` sin publicar);
  - no toca los archivos ni los recursos compartidos de otra en curso;
  - no necesita un recurso exclusivo ocupado.
- **Recursos compartidos:**
  - un contrato;
  - la versión de `liv-components`;
  - las migraciones y los archivos de *build* de un proyecto;
  - el `CHANGELOG.md` de cada proyecto;
  - `ESTADO.md`, el roadmap, el glosario, las marcas de `tasks.md` y los criterios de `spec.md`.
- **Recursos exclusivos:** el entorno local de `tools/dev/`, las pruebas de navegador y Figma.
- Hay como máximo **tres sesiones de trabajo a la vez**; los subagentes de revisión no cuentan.
- **Dependencias y recursos declarados:** cada tarea de `tasks.md` dice su proyecto, de qué depende y qué recursos usa, y el plan los resume.
- **Un solo escritor:** mientras el orquestador dirige una feature, solo él escribe `ESTADO.md`, el *roadmap*, el glosario, las marcas de `tasks.md` y los criterios de `spec.md`. Las sesiones de las tareas le reportan, y eso cumple cualquier regla que les pida actualizarlos, también las de los `AGENTS.md` de los proyectos. Lo hace en un PR de estado propio, por el flujo directo y uno abierto a la vez, con solo `ESTADO.md` y esas marcas; el roadmap y el glosario van en el PR de cierre de la feature, por su SDD.
- **El tablero** «Trabajo en curso» de `ESTADO.md` §1 dice qué feature dirige el orquestador, o «Sin orquestador activo», y lleva una fila por tarea.
- **Escalamiento escrito:** siempre consulta a la persona en estos casos:
  - elegir o empezar una feature;
  - las compuertas H1, H2 y H3, y lo de `AGENTS.md` §4.5;
  - apartarse del plan aprobado, en especial en una ruta crítica o una regla;
  - los `PENDIENTE` funcionales y las preguntas abiertas de la spec;
  - un hallazgo crítico o alto sin resolver;
  - el CI que falla dos veces;
  - una sesión que queda sin terminar;
  - una decisión que la spec y el plan no resuelven.

  Se lo junta en una bandeja de decisiones, pero cada PR conserva su propia H3. Si la persona discrepa, prevalece la persona.
- **La persona sigue aprobando H1, H2 y H3 de cada PR, aunque lo abra una sesión, e integrándolo.** El orquestador y sus sesiones no integran de ninguna forma (tampoco `gh pr merge --auto`), no aprueban PR ni cambian la protección de `main`. Este ADR no da autonomía de integración. Esa autonomía, por niveles, es una decisión aparte, que necesita antes las barreras de F0‑03 y F0‑07.
- **Transición:** la feature 004 (F0‑10) empezó antes de este ADR y termina con las reglas anteriores; su `tasks.md` no declara dependencias ni recursos. El orquestador se aplica desde la siguiente feature.

El procedimiento completo está en la *skill* `liv-orquestador`.

## Consecuencias

- **Positivas:**
  - el método de orquestación queda en el repositorio, igual para cualquier agente;
  - el paralelismo entre tareas independientes reduce el tiempo de una feature;
  - `ESTADO.md` deja de chocar entre PR;
  - la persona recibe juntas las decisiones que le tocan.
- **Negativas o costos:**
  - `ESTADO.md` se actualiza en PR propios del orquestador, no en el mismo PR de cada tarea, así que en `main` puede ir un poco atrasado;
  - declarar dependencias y recursos en cada tarea es trabajo extra al planificar;
  - hasta que el entorno local se aísle por *worktree*, las tareas que lo usan van de a una;
  - dos tareas del mismo proyecto que cambian su *build* o sus migraciones no corren en paralelo.
- **Qué cambia en el repositorio:**
  - la *skill* `liv-orquestador`;
  - las plantillas de `tasks.md` (proyecto, dependencias y recursos) y de `plan.md` (sección «Dependencias y recursos»);
  - `AGENTS.md` §4.3 (la H3 es de la persona), §4.4, §7 (el escritor único y su precedencia), §9, §10 y §11;
  - `CLAUDE.md`;
  - el doc. 05 §4.1, §6 y §7.2, que pasa a la versión 1.1;
  - las *skills* `liv-estado`, `liv-sdd-feature` y `liv-figma`;
  - `spec/README.md` y la nota del roadmap;
  - el tablero en `ESTADO.md` §1 y la regla de su encabezado.
