# NNN · <Nombre de la feature>

**Estado:** propuesta
_(propuesta → aprobada (H1) → en construcción → hecha)_

**Módulo:** <core | documents | jurisdiction | valuation | tax | ledger | portfolio | reporting> · **Menú:** <p. ej. 1.5.3> · **Zona roja:** <sí: cuáles | no> (`AGENTS.md` §4.3)
**Fuentes:** <documento funcional y sección, p. ej. Doc. 01 §6.3, Doc. 03 §3>
**Backlog:** *issues* con la etiqueta `feature:NNN`

_Spec corta (ADR 0040): lo justo para arrancar. El detalle de cada rebanada se decide en su *issue*, justo antes de construirla._

## Qué hace y por qué

<En cinco líneas, desde el punto de vista del usuario: qué podrá hacer o ver, y qué problema resuelve.>

## Reglas

_Las reglas de negocio o tributarias que aplica, con su código y su fuente. Ninguna se inventa: si falta, va a «Preguntas abiertas»._

| Código | Regla | Fuente |
|---|---|---|
| <`R0xx`, `LQ-Cxx`, `CJxx`…> | <qué dice, en una línea> | <Doc. NN §x> |

## Alcance del MVP

- **Entra:** <lo mínimo que hace útil la feature.>
- **Después:** <lo que se deja para más adelante. Cada punto va al backlog como *issue* sin feature, y no se discute más en esta.>

## Criterios de aceptación

_Verificables con sí o no, en formato Dado / Cuando / Entonces. Cada uno empieza con el código de la regla o del criterio funcional que verifica; si no hay, `NNN-01`, `NNN-02`… Cada criterio queda asignado a una rebanada. Se marca `[x]` al cerrar la feature, con la prueba que lo demuestra._

- [ ] **<código>** — Dado <contexto>, cuando <acción>, entonces <resultado observable>. _Rebanada:_ R<n>. _Prueba:_ <pendiente>
- [ ] **<código>** — <Caso límite o de error>. _Rebanada:_ R<n>. _Prueba:_ <pendiente>
- [ ] **<código>** — <Segregación de funciones o aislamiento entre administraciones, si aplica>. _Rebanada:_ R<n>. _Prueba:_ <pendiente>
- [ ] <Requisito no funcional, si aplica: rendimiento (`docs/arquitectura/03` §1), accesibilidad WCAG 2.1 AA…>. _Rebanada:_ R<n>. _Prueba:_ <pendiente>

## Diseño técnico

_Una página como máximo. Lo caro de cambiar se decide aquí; lo barato, en cada rebanada. Borra las filas que no apliquen._

- **Módulos y capas:** <módulos del backend que toca; dependencias nuevas entre módulos (requieren ADR).>
- **Datos:** <tablas, columnas, restricciones y políticas RLS nuevas o cambiadas; *expand/contract* si cambia una tabla existente, porque una migración aplicada no se edita.>
- **Contrato:** <endpoints nuevos o cambiados, y su versión; ¿algún cambio incompatible?>
- **Permisos:** <permiso de cada caso de uso y reglas de segregación de funciones. Una rebanada que expone un caso de uso lo expone con su permiso y su segregación completos.>
- **Amenazas:** <riesgos de seguridad de `docs/arquitectura/02` §2 que toca la feature y su control.>
- **Pantallas:** <pantallas y su nodo en `03 Pantallas` de Figma, o «Reutiliza la estructura de: <pantalla> (<enlace>); cambia: <contenido>»; componentes de `liv-components` nuevos.>
- **Riesgos y decisiones:** <lo que puede salir mal y cómo se mitiga; decisiones con su alternativa descartada. Una decisión cara de revertir lleva su ADR.>
- **Revisión:** <si hay zona roja, el resultado de la revisión adversarial: fecha y hallazgos por severidad.>

## Rebanadas

_En orden. La primera es el esqueleto: el camino más delgado de punta a punta. Las demás van por riesgo y por valor. Cada una se convierte en un *issue*._

| # | Rebanada | Proyectos | Criterios | *Issue* |
|---|---|---|---|---|
| R1 | Esqueleto: <opción del menú, pantalla mínima, endpoint y tabla, conectados> | `backend/`, `frontend/` | <códigos> | #<n> |
| R2 | <…> | <…> | <…> | #<n> |

## Cambios

_Lo que cambió durante la construcción, con su fecha. Los cambios que agregan, quitan o alteran un criterio, el diseño técnico o una rebanada con zona roja llevan el visto bueno de la persona y entran aquí en un PR pequeño de la raíz; los demás, y los términos nuevos del glosario, se anotan en el *issue* y entran aquí en el PR de cierre. Un cambio de una regla tributaria sigue el proceso de los documentos funcionales._

- <AAAA-MM-DD — qué cambió y por qué. Visto bueno: <persona, fecha>.>

## Preguntas abiertas

_Pendientes funcionales o jurídicos (`PENDIENTE`, `POR_CONFIRMAR`, «Pendientes por confirmar»). La spec no se aprueba (H1) mientras una pregunta abierta pueda cambiar un criterio; si una aparece durante la construcción, la rebanada afectada no empieza hasta resolverla. Borra esta sección si no hay ninguna._

- <Pregunta> — <a quién se le hizo y cuándo>.
