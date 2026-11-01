# NNN · <Nombre de la feature>

**Estado:** propuesta
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** <core | documents | jurisdiction | valuation | tax | ledger | portfolio | reporting> · **Menú:** <p. ej. 1.5.3> · **Tipo de cambio:** <normal | crítico> (`AGENTS.md` §4.3)
**Fuentes:** <documento funcional y sección, p. ej. Doc. 01 §6.3, Doc. 03 §3>

## Qué hace

<Descripción clara y breve de la funcionalidad desde el punto de vista del usuario: qué podrá hacer o ver. Sin detalles de implementación (eso va en `plan.md`).>

## Por qué

<Qué problema resuelve o qué valor aporta. Por qué merece la pena hacerla ahora.>

## Criterios de aceptación

_Condiciones verificables que deben cumplirse para dar la feature por terminada. Redacta cada una de forma que se pueda comprobar con un sí/no, en formato Dado / Cuando / Entonces. Cada criterio empieza con el código de la regla o del criterio de aceptación funcional que verifica (`R0xx`, `LQ-Cxx`, `CJxx`, `CA-x.xx`…); si no existe, usa `NNN-01`, `NNN-02`… Marca `[x]` al cumplirse y anota la prueba que lo demuestra._

- [ ] **<código>** — Dado <contexto>, cuando <acción>, entonces <resultado observable>. _Prueba:_ <pendiente>
- [ ] **<código>** — <Caso límite o de error contemplado>. _Prueba:_ <pendiente>
- [ ] **<código>** — <Segregación de funciones o aislamiento entre administraciones, si aplica>. _Prueba:_ <pendiente>
- [ ] <Requisito de calidad: rendimiento (`docs/arquitectura/03` §1), accesibilidad WCAG 2.1 AA… si aplica.>

## Fuera de alcance

_Lo que esta feature NO incluye, para evitar que crezca. Si algo se difiere, enlaza a dónde (roadmap/backlog)._

- <Cosa relacionada que se hará en otra feature o no se hará.>

## Preguntas abiertas

_Pendientes funcionales o jurídicos que afectan a la feature (`PENDIENTE`, `POR_CONFIRMAR`, "Pendientes por confirmar"). La spec no se aprueba mientras una pregunta abierta pueda cambiar un criterio de aceptación. Borra esta sección si no hay ninguna._

- <Pregunta> — <a quién se le hizo y cuándo>.
