# ADR-0041 · El agente mantiene el tablero del backlog: lo trivial lo hace solo y lo que necesita autorización lo pide en el chat

- **Estado:** Aceptado (2026-11-18, decidido por el responsable del proyecto en la sesión)
- **Fecha:** 2026-11-18
- **Decisores:** responsable del proyecto (`AGENTS.md` §4.3, «quien dirige el trabajo decide»)
- **Documentos relacionados:** ADR 0040; `AGENTS.md` §4.2 y §7; `docs/arquitectura/05` §3.1; *skills* `liv-backlog`, `liv-sdd-feature` y `liv-orquestador`; `spec/README.md`

## Contexto

El ADR 0040 llevó el backlog a GitHub Issues y a su *Project* (`liv-project`), pero dejó en manos de la persona mover cada rebanada a «Lista» y no dijo quién mantiene el tablero al día. Para la persona, eso es trabajo manual que no aporta. Ella quiere que el agente se encargue del tablero completo y le pida autorización en el chat solo para lo que la necesita.

## Opciones consideradas

1. **Dejarlo como estaba:** la persona mueve las tarjetas a «Lista» y nadie tiene asignado el resto. Es seguro, pero el tablero se desactualiza y la persona hace trabajo mecánico.
2. **El agente lo hace todo sin preguntar.** Es lo más rápido, pero el agente decidiría solo qué se construye y qué entra al backlog.
3. **El agente mantiene el tablero completo.** Hace lo trivial y reversible por su cuenta. Lo que es una decisión de la persona lo pide en el chat y lo ejecuta en cuanto ella responde, incluido mover una rebanada a «Lista».

## Decisión

Opción 3.

**El agente hace sin preguntar** lo trivial y reversible:
- crear los *issues* de las rebanadas de una spec aprobada (H1);
- mover las tarjetas según el trabajo real: *In progress* al empezar, *In review* al abrir el PR y *Done* al integrar el último;
- enlazar los PR, comentar, poner etiquetas y llenar los campos;
- completar el detalle de un *issue* y anotar en él sus cambios;
- al empezar cada sesión, corregir las tarjetas que no coinciden con los PR reales;
- sacar del tablero las tarjetas de PR.

**El agente pide autorización en el chat y la ejecuta en cuanto la recibe:**
- **mover una rebanada a «Lista»** (*Ready*). Es el visto bueno para construirla. La persona puede darlo para varias rebanadas a la vez, por ejemplo «adelante con R1 a R3». El agente lo anota en un comentario del *issue*, con la fecha;
- crear un *issue* nuevo que encontró (defecto, mejora o deuda);
- cerrar un *issue* sin terminarlo, borrarlo o reabrirlo;
- cambiar las columnas, los campos o las automatizaciones del tablero.

**El criterio:** si la acción decide qué se construye, qué entra al backlog o cómo funciona el tablero, se pregunta. Si solo refleja lo que ya pasó o lo que la persona ya aprobó, se hace.

## Consecuencias

- **Positivas:**
  - el tablero siempre refleja el trabajo real;
  - la persona solo decide, sin mover tarjetas a mano;
  - los vistos buenos quedan escritos en el *issue*.
- **Negativas o costos:**
  - el agente necesita el permiso `project` en la cuenta del repositorio;
  - un visto bueno mal entendido mueve una rebanada que no debía. Por eso el agente cita en el comentario el mensaje de la persona.
- **Qué cambia en el repositorio:**
  - `AGENTS.md` §4.2 y §7;
  - `docs/arquitectura/05` §3.1;
  - `spec/README.md`;
  - las *skills* `liv-backlog`, `liv-sdd-feature` y `liv-orquestador`;
  - el doc. 00 §6.

  Complementa al ADR 0040, que decía que solo la persona mueve una rebanada a «Lista». El visto bueno sigue siendo de la persona: lo que cambia es quién lo ejecuta en el tablero.
