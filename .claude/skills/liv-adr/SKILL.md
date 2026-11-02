---
name: liv-adr
description: Crea, acepta o reemplaza una decisión de arquitectura (ADR) del proyecto LIV en formato MADR dentro de docs/adr/. Úsala cuando una feature o tarea implique una decisión de arquitectura, cuando se agregue una dependencia, cuando haya que aceptar los ADR propuestos del doc. 00 §6, o cuando el usuario pida "registrar una decisión", "crear un ADR" o "aceptar los ADR".
---

# ADR del proyecto LIV

## Cuándo hace falta un ADR

- Una decisión que afecta a más de una feature o que es difícil de revertir: tecnología, patrón, estructura, contrato, seguridad, alojamiento.
- Agregar, quitar o cambiar de versión mayor una dependencia (`AGENTS.md` §4.5).
- Cambiar la constitución (`spec/constitution/`) o un umbral de calidad.
- Una nueva dependencia entre módulos del backend.

## Numeración y ubicación

- Archivo: `docs/adr/ADR-NNNN-titulo-en-minusculas-con-guiones.md`.
- Los ADR 0001–0020 ya tienen número y título en `docs/arquitectura/00_Decisiones_y_Hoja_de_Ruta.md` §6: al crear su archivo, conserva ese número.
- Un ADR nuevo toma el siguiente número libre y se agrega también a la tabla del doc. 00 §6.

## Plantilla

```markdown
# ADR-NNNN · <Título de la decisión>

- **Estado:** Propuesto | Aceptado | Reemplazado por ADR-XXXX
- **Fecha:** AAAA-MM-DD
- **Decisores:** <personas o roles>
- **Documentos relacionados:** <docs/arquitectura/… §…, docs/funcional/… §…, feature NNN>

## Contexto

<Qué problema o necesidad obliga a decidir. Cita los impulsores del doc. 00 §2 (D1–D16) o las reglas funcionales que aplican.>

## Opciones consideradas

1. **<Opción A>** — <ventajas y desventajas>.
2. **<Opción B>** — <ventajas y desventajas>.

## Decisión

<La opción elegida y por qué, en pocas frases.>

## Consecuencias

- **Positivas:** <…>
- **Negativas o costos:** <…>
- **Qué cambia en el repositorio:** <documentos, reglas, herramientas o verificaciones afectadas>.
```

## Estados

- **Propuesto:** escrito, pendiente de aprobación humana (arquitecto o líder técnico).
- **Aceptado:** aprobado. A partir de aquí **no se edita**: solo se corrigen errores de forma.
- **Reemplazado:** otro ADR lo sustituye. Se agrega "Reemplazado por ADR-XXXX" y el nuevo ADR dice a cuál reemplaza.

Un agente puede redactar un ADR en estado Propuesto, pero **nunca** lo marca como Aceptado sin la aprobación explícita de un humano en la conversación.

## Al terminar

1. Actualiza la columna **Estado** de la tabla del doc. 00 §6.
2. Si el ADR cambia una regla de trabajo, actualiza `AGENTS.md` y, si aplica, `spec/constitution/tech-stack.md`.
3. Actualiza `ESTADO.md` (entregable F0‑02 mientras dure la Fase 0) con la *skill* `liv-estado`.
