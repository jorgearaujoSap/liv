---
name: liv-estado
description: Actualiza ESTADO.md, el registro del estado del proyecto LIV, al iniciar, completar o bloquear cualquier tarea (incluido trabajo en Figma o decisiones del equipo). Úsala al cerrar cualquier tarea, antes de dar algo por terminado, o cuando el usuario pregunte "en qué estado está el proyecto", "qué falta" o pida actualizar la bitácora.
---

# Actualizar `ESTADO.md`

`ESTADO.md` es el único lugar que dice dónde está el proyecto. Se actualiza **en el mismo cambio** que inicia, completa o bloquea una tarea. Una tarea no está terminada hasta que `ESTADO.md` la refleja (`AGENTS.md` §7).

**Si la feature la dirige un orquestador** (`liv-orquestador`, ADR 0033), solo él usa esta *skill*: las sesiones de las tareas no tocan `ESTADO.md` y se lo reportan, y con eso cumplen (`AGENTS.md` §7). Un cambio ajeno a la feature escribe solo su entrada de la bitácora. El orquestador también lleva el tablero «Trabajo en curso» de §1.

## Pasos

1. **Lee `ESTADO.md` completo** antes de tocarlo: otra persona u otro agente puede haberlo cambiado.
2. Actualiza **la fecha** de "Última actualización" (fecha real de hoy, `AAAA-MM-DD`).
3. Actualiza **solo las secciones afectadas**:

| Sección | Cuándo se toca |
|---|---|
| 1. Resumen | Cambia la fase, el estado de la arquitectura, de Figma o de las decisiones |
| 1. Trabajo en curso | Solo el orquestador: empieza o se cierra una feature, se abre o se integra un PR de sus tareas, o algo se bloquea |
| 2. Documentación | Se crea, mueve o cambia de versión o estado un documento |
| 3. Entregables de la Fase 0 | Avanza un entregable F0‑xx (mientras dure la Fase 0) |
| 4. Figma | Cambian páginas, estilos, componentes o pantallas, o su aprobación |
| 5. Decisiones del equipo | Se toma, cambia o reabre una decisión P‑xx |
| 6. Próximos pasos | Lo siguiente cambió después de esta tarea |
| 7. Bitácora | **Siempre** |

4. Usa solo estos estados: ✅ Hecho · 🔄 En curso · ⏳ Pendiente · ⛔ Bloqueado. Un ⛔ siempre dice qué lo bloquea y a quién se le pidió.
5. **Bitácora:** agrega una viñeta al principio del día actual (crea el encabezado `### AAAA-MM-DD` si no existe), en español y en pasado. Indica qué se hizo, dónde (archivos o secciones) y la decisión o motivo si no es obvio. Lo más reciente va primero.
6. Las tablas y la bitácora deben coincidir: si la bitácora dice que algo se terminó, su tabla lo marca ✅.
7. Si cerraste una feature, pásala también a "Hecho" en `spec/constitution/roadmap.md`.

## Qué no hacer

- No reescribas ni borres entradas antiguas de la bitácora; si algo estaba mal, agrega una entrada nueva que lo corrija.
- No marques ✅ algo que no se verificó.
- No dejes la actualización para un commit aparte: va con el cambio que la origina. La excepción es el orquestador, que la lleva en su propio PR (ADR 0033).
