---
name: liv-estado
description: Actualiza ESTADO.md, el registro del estado del proyecto LIV - al arrancar o cerrar una feature, con una decisión del equipo, un cambio de fase, de Figma o de los próximos pasos, y con los ajustes de la raíz. Úsala en esos casos, o cuando el usuario pregunte "en qué estado está el proyecto", "qué falta" o pida actualizar la bitácora. El avance de las rebanadas no va aquí, sino en el Project de GitHub (ADR 0040).
---

# Actualizar `ESTADO.md`

`ESTADO.md` dice dónde está el proyecto: la fase, la feature en construcción, las decisiones abiertas, Figma, los próximos pasos y la bitácora (`AGENTS.md` §7). **El avance de cada rebanada no va aquí:** lo lleva el *Project* de GitHub, y los PR de las rebanadas no tocan este archivo (ADR 0040).

## Cuándo

- Arranca una feature (con su H1) o se cierra.
- Cambia la fase, una decisión del equipo, el estado de Figma o los próximos pasos.
- Un ajuste de la raíz (`docs/`, `spec/`, `.claude/`, `.github/`), que no tiene `CHANGELOG.md`, deja su línea en la bitácora.

## Pasos

1. **Lee `ESTADO.md`** antes de tocarlo: otra persona u otro agente puede haberlo cambiado.
2. Actualiza **la fecha** de «Última actualización» (fecha real de hoy, `AAAA-MM-DD`).
3. Actualiza **solo las secciones afectadas**:

| Sección | Cuándo se toca |
|---|---|
| 1. Resumen | Cambia la fase, la feature en construcción, el estado de la arquitectura, de Figma o de las decisiones |
| 2. Documentación | Se crea, mueve o cambia de versión o estado un documento |
| 4. Figma | Cambian páginas, estilos, componentes o pantallas, o su aprobación |
| 5. Decisiones del equipo | Se toma, cambia o reabre una decisión P‑xx |
| 6. Próximos pasos | Lo siguiente cambió |
| 7. Bitácora | **Siempre** |

4. Usa solo estos estados: ✅ Hecho · 🔄 En curso · ⏳ Pendiente · ⛔ Bloqueado. Un ⛔ siempre dice qué lo bloquea y a quién se le pidió.
5. **Bitácora:** agrega una viñeta al principio del día actual (crea el encabezado `### AAAA-MM-DD` si no existe), en español y en pasado. Di qué se hizo, dónde y el motivo si no es obvio. Sé breve: el detalle está en los PR y en los *issues*.
6. Las tablas y la bitácora deben coincidir.
7. Si cerraste una feature, pásala también a «Hecho» en `spec/constitution/roadmap.md`.

## Qué no hacer

- No reescribas ni borres entradas antiguas de la bitácora; si algo estaba mal, agrega una entrada nueva que lo corrija.
- No marques ✅ algo que no se verificó.
- No copies aquí el avance de las rebanadas ni el tablero: eso ya está en GitHub.
- `ESTADO.md` se guarda con fin de línea CRLF. Edítalo en su lugar, prepáralo con `git -c core.autocrlf=false add ESTADO.md` y revisa con `git diff --cached --numstat` que solo cambien tus líneas.
