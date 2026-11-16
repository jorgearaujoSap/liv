# 007 · Vigencias fiscales

**Estado:** aprobada (H1) · plan aprobado (H2) — 2026-11-13. H1 del 2026-11-12, por la persona que dirige el trabajo (`AGENTS.md` §4.3), con las siete recomendaciones de la spec (véase «Decisiones de la H1») · enmiendas 1, 2 y 3 (007-04, 007-06, 007-08, 007-10 y 007-20) aprobadas por la misma persona como responsable funcional en la H2 del 2026-11-12, que firmó de nuevo la H1 (véase «Enmiendas de la H2») · **enmienda 4 (años de 2002 al año siguiente al año en curso, en cualquier orden): propuesta del 2026-11-16, pendiente de la H1** (véase «Enmienda 4»). Los criterios 007-03, 007-04, 007-06, 007-07, 007-08 y 007-21 ya tienen el texto propuesto y los criterios 007-22 y 007-23 son nuevos; hasta la H1, lo aprobado es el texto anterior, que conserva el historial de Git
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `tax` (vigencias), con la matriz de permisos de `platform`, la auditoría de `audit`, el frontend y, si el diseño lo pide, la librería `liv-components` · **Menú:** 1.4 · **Tipo de cambio:** crítico (`AGENTS.md` §4.3; decisión 4 de la H1)
**Fuentes:** Doc. 01 v0.4 §2 (principios 3, 7 y 9), §4 y §4.1 (roles, matriz de acciones y permisos del menú), §5 (estados y reglas V‑01 a V‑04), §6.1 (lista de preparación 1.5.1 y «Habilitar liquidación»), §7.1 (consultas y exportación), §8 (auditoría), §9 (CA‑12 y CA‑14) y §11 (pendiente 5); ERD 2.2, esquema `tax` (`tax.fiscal_year` y sus relaciones) y relaciones externas de `valuation` y `ledger`; Doc. 02 v0.2 §B.2 y §B.3 (LQ‑P01); Doc. 03 v0.5 §5.2 (sección `vigencia`), §5.5 y §5.6; Doc. 05 v0.1 §3 (`cartera_historica.vigencias_desde`); `docs/arquitectura/01` §3.1 y §15; `docs/arquitectura/02` §4.1 a §4.4, §5.1 a §5.3 y §9; `AGENTS.md` §4.3, §4.5, §5.1 y §6.2

> Primera feature de la Fase 1 («Tablas base y configuración»). La vigencia es el prerrequisito de todo el Módulo 1: los catálogos de avalúos, las tarifas, el calendario y la configuración de jurisdicción se cargan **para una vigencia** (Doc. 01 §5 y §6.1).

## Qué hace

Abre la opción **1.4 Vigencias fiscales**, que hoy aparece en el menú como «Próximamente». Con ella, la administración tributaria lleva el ciclo de vida de cada año fiscal:

- **Consultar** las vigencias de su administración: el año, las fechas de inicio y fin, el estado (*Planeada*, *En preparación*, *Lista*, *Habilitada* o *Cerrada*) y si la liquidación está habilitada. La ven los cinco roles del Módulo 1 que hoy tienen la opción en el menú.
- **Crear** una vigencia nueva, en estado *Planeada*. Solo el `ADMIN_SISTEMA`. Las fechas son del 1 de enero al 31 de diciembre del año (V‑01). Se puede crear cualquier año de 2002 al año siguiente al año en curso que la administración aún no tenga, en cualquier orden y con huecos, también la primera vigencia (V‑02 y V‑05; enmienda 4, que reemplaza la decisión 2). Un año pasado sigue el mismo ciclo que los demás: *Planeada*, preparación, habilitación y liquidación.
- **Abrir la preparación**: la vigencia pasa a *En preparación* y desde ese momento las opciones 1.5.x pueden cargarle sus tablas. Solo el `ADMIN_SISTEMA`.
- **Ver la preparación** de la vigencia: la lista de preparación de la opción 1.5.1 (el *checklist* del Doc. 01 §6.1), con el estado de cada punto y por qué falta. El botón **«Habilitar liquidación»** solo se activa cuando la lista está completa (V‑03, CA‑14).
- **Habilitar la liquidación con doble firma** y **cerrar la vigencia** no son de esta feature (decisión 1): van en la feature de la opción 1.5.1, con los criterios que esta spec le deja escritos («Trasladado a la feature 1.5.1»).

Cada administración ve y maneja solo sus vigencias. Cada creación y cada cambio de estado quedan en la auditoría inmutable, con el estado anterior y el nuevo.

## Por qué

Ninguna de las opciones siguientes de la Fase 1 (1.5.9 configuración de jurisdicción, 1.3 normas, 1.5.1 a 1.5.8 tablas base, 1.6 consultas) puede empezar sin una vigencia a la que asociar lo que carga: el ERD ata `valuation.valuation_catalog`, `tax.tax_rate`, `tax.tax_calendar`, `tax.tax_benefit`, `tax.penalty_rule` y `core.jurisdiction_config` a `fiscal_year`. Además, la vigencia es el único control que impide liquidar con tablas incompletas (LQ‑P01, Doc. 02 §B.3), y su habilitación es una de las segregaciones de funciones del sistema (`docs/arquitectura/02` §4.3). Es la feature más pequeña con la que empieza el Módulo 1 y fija el patrón de aislamiento, permisos y auditoría de las siguientes.

## Criterios de aceptación

_Cada criterio se comprueba con sí/no y tiene su número propio `007-xx`. Si verifica una regla o un criterio del Doc. 01, ese código va delante (`V‑01` a `V‑04`, `CA‑12`, `CA‑14`). Los estados se escriben con su etiqueta en la interfaz; sus códigos son los del Doc. 01 §5 (*Planeada* = `PLANEADA`, *En preparación* = `EN_PREPARACION`, *Lista* = `LISTA`, *Habilitada* = `HABILITADA`, *Cerrada* = `CERRADA`)._

**A. Consulta de vigencias**

- [ ] **007-01** — Dado un usuario con permiso de consulta de vigencias (`tax.fiscal-year.read`: `ADMIN_SISTEMA`, `CARGADOR_TABLAS`, `APROBADOR_TABLAS`, `CONSULTA` o `AUDITOR`, Doc. 01 §4.1), cuando abre la opción 1.4, entonces ve las vigencias de su administración, de la más reciente a la más antigua, con el año, la fecha de inicio, la fecha de fin, el estado y si la liquidación está habilitada; la opción 1.4 deja de mostrarse como «Próximamente». _Prueba:_ pendiente
- [ ] **007-02** — Dado un usuario sin ese permiso (por ejemplo, `LIQUIDADOR` o `JURIDICO`), cuando abre la opción 1.4 por su URL o llama a su API, entonces recibe «Sin permiso» (403), como en el resto del menú (Doc. 01 §4.1). _Prueba:_ pendiente
- [ ] **007-03** — Dada una administración sin vigencias, cuando un usuario con permiso de consulta abre la opción 1.4, entonces ve un estado vacío que lo explica y, si es `ADMIN_SISTEMA`, la acción para crear la primera. Para el `ADMIN_SISTEMA`, la explicación dice el primer y el último año que se pueden crear (por ejemplo, «de 2002 a 2027»), según lo que dice el servidor, y no enumera los años uno por uno (enmienda 4). _Prueba:_ pendiente

**B. Creación de la vigencia**

- [ ] **V‑01 · 007-04** — Dado un `ADMIN_SISTEMA`, cuando crea la vigencia de un año, entonces la vigencia queda en estado *Planeada*, sin la liquidación habilitada, con inicio el 1 de enero y fin el 31 de diciembre de ese año. Las fechas no se escriben a mano (decisión 6). Antes de crearla, la pantalla advierte que una vigencia no se puede borrar y pide revisar el año (enmienda 1). Ya no dice que los años anteriores a la más antigua quedan como históricos, porque con la enmienda 4 se pueden crear. _Prueba:_ pendiente
- [ ] **V‑01 · 007-05** — Dada una vigencia que ya existe en la administración, cuando un `ADMIN_SISTEMA` intenta crear otra del mismo año, entonces se rechaza con un mensaje que cita V‑01 y no cambia nada. Si dos personas crean el mismo año al mismo tiempo, se crea una sola vigencia y la otra recibe ese rechazo. _Prueba:_ pendiente
- [ ] **V‑02 · 007-06** — Dada una administración con vigencias, cuando un `ADMIN_SISTEMA` crea un año que no tiene y que está entre 2002 y el año siguiente al año en curso, entonces se crea, sea anterior a su vigencia más antigua, posterior a la más reciente aunque deje años sin crear entre las dos, o uno de los que faltan entre dos vigencias: el orden es libre y se admiten huecos (enmienda 4). Por ejemplo, en 2026 y con solo la vigencia 2026, se pueden crear 2002, 2015 y 2027, en ese orden o en cualquier otro. Ningún rechazo cita V‑02. _Prueba:_ pendiente
- [ ] **V‑05 · 007-07** — Dado un `ADMIN_SISTEMA`, con o sin vigencias en su administración, cuando intenta crear un año anterior a 2002 o posterior al año siguiente al año en curso, entonces se rechaza con un mensaje que cita V‑05 y dice cuál es el primer año o el último que se puede crear, y no cambia nada. Si el año ya existe, el rechazo es el de V‑01 (007-05). Un año fuera de la tabla nacional (1999 a 2100) sigue siendo una solicitud mal formada, como en el contrato aprobado (enmienda 4). _Prueba:_ pendiente
- [ ] **V‑05 · 007-08** — Dada una administración sin vigencias, cuando un `ADMIN_SISTEMA` crea la primera, entonces se admite cualquier año de 2002 al año siguiente al año en curso, el mismo rango que para las demás. El año en curso es el de la hora oficial de Colombia, según el reloj del sistema. La primera vigencia ya no tiene una regla propia (enmienda 4, que retira la regla de la decisión 2 y el motivo de la enmienda 3). _Prueba:_ pendiente
- [ ] **007-09** — Dado un usuario sin rol `ADMIN_SISTEMA` (por ejemplo, `APROBADOR_TABLAS` o `CARGADOR_TABLAS`), cuando intenta crear una vigencia o abrir su preparación por la API, entonces recibe «Sin permiso» (403), no cambia nada y la interfaz no le muestra esas acciones (Doc. 01 §4, «Crear / abrir vigencia fiscal»). _Prueba:_ pendiente
- [ ] **V‑05 · 007-22** — Dado un `ADMIN_SISTEMA`, cuando abre «Crear vigencia», entonces el diálogo ofrece todos los años que la creación aceptaría, y solo esos: de 2002 al año siguiente al año en curso, sin los que ya existen. Los años los da el servidor y la pantalla no los calcula. Van del más reciente al más antiguo, en una lista desplegable que sirve para 26 años o más. El año en curso y el siguiente llevan su etiqueta y los años pasados solo su número. Si el año en curso se puede crear, viene marcado; si no, no viene marcado ninguno y «Crear vigencia» espera a que la persona elija. El diálogo muestra las fechas del año elegido (enmienda 4; preguntas E4‑2 a E4‑4). _Prueba:_ pendiente
- [ ] **V‑05 · 007-23** — Dada una administración que ya tiene todas las vigencias de 2002 al año siguiente al año en curso, cuando un `ADMIN_SISTEMA` ve la opción 1.4, entonces «Crear vigencia» está inhabilitado y dice por qué: ya existen todas las vigencias que se pueden crear, de 2002 al último año. La creación por la API se rechaza con V‑01 o V‑05, según el año (enmienda 4). _Prueba:_ pendiente

**C. Preparación**

- [ ] **007-10** — Dada una vigencia *Planeada*, cuando un `ADMIN_SISTEMA` abre su preparación, entonces pasa a *En preparación*. Desde cualquier otro estado, la acción no se ofrece y la API la rechaza con un mensaje que cita V‑06, sin cambiar nada (Doc. 01 §5, diagrama de estados; enmienda 3). _Prueba:_ pendiente
- [ ] **007-11** — Dada una vigencia, cuando un usuario con permiso de consulta abre su preparación, entonces ve los nueve puntos de la lista 1.5.1 (Doc. 01 §6.1), cada uno con su estado (cumplido o pendiente; los puntos 6 y 7 son opcionales) y, si está pendiente, por qué. Los puntos de opciones que todavía no existen (2 a 9) se muestran *pendientes* con el texto «Opción aún no disponible», nunca como cumplidos. El punto 1 se cumple si la vigencia está *En preparación* o en un estado posterior (el Doc. 01 §6.1 dice «en estado `EN_PREPARACION`»; los estados posteriores también lo cumplen, porque solo se llega a ellos desde ese). _Prueba:_ pendiente
- [ ] **V‑03 · CA‑14 · 007-12** — Dada una vigencia a la que le falta alguno de los puntos obligatorios de la lista (1 a 5, 8 y 9), cuando un usuario la ve, entonces el botón «Habilitar liquidación» está inhabilitado y la pantalla lista los puntos que faltan; ninguna llamada a la API puede poner `assessments_enabled` en verdadero. _Prueba:_ pendiente

**D. Aislamiento entre administraciones**

- [ ] **CA‑12 · 007-18** — Dadas las administraciones A y B, cuando un usuario de A consulta, crea o cambia el estado de vigencias, entonces solo ve y modifica las de A: las de B no aparecen, no se pueden abrir por su identificador ni se pueden inferir por un conteo o por el mensaje de un rechazo. La administración se toma de la sesión, nunca de un parámetro del navegador (`docs/arquitectura/02` §5.1 y §5.3; decisión 3). _Prueba:_ pendiente
- [ ] **007-19** — Dada la vigencia 2026 creada en la administración A, cuando un `ADMIN_SISTEMA` de B crea su vigencia 2026, entonces se crea sin conflicto, y abrir la preparación de la vigencia de una administración no cambia la de la otra (decisión 3). _Prueba:_ pendiente

**E. Auditoría**

- [ ] **007-20** — Dada cualquier creación o cambio de estado de una vigencia, entonces queda un evento en `audit.audit_event` con la entidad `tax.administration_fiscal_year` y el identificador de su fila, el año, el usuario, la administración, el estado anterior y el nuevo, el instante y el `correlation_id` de la operación (Doc. 01 §8; enmienda 2). _Prueba:_ pendiente

**F. Calidad**

- [ ] **007-21** — Dadas las pantallas de la opción 1.4, entonces están construidas solo con componentes de `liv-components`, coinciden con su diseño aprobado en Figma (decisión 5), pasan axe sin violaciones de WCAG 2.1 AA y tienen sus pruebas de navegador en `e2e/` en verde contra el entorno local (ADR 0030). Con la enmienda 4 vale también para el diálogo de creación con su lista desplegable, que se diseña en Figma y se aprueba antes de implementarse. _Prueba:_ pendiente

### Trasladado a la feature 1.5.1

_Por la decisión 1, la habilitación con doble firma y el cierre no son de esta feature. Estos criterios quedan escritos como punto de partida de la feature de la opción 1.5.1 («Asistente de preparación y habilitación»), que se hará cuando existan los puntos 2 a 9 de la lista. Conservan su número para que se puedan rastrear; en esta feature no se implementan ni se marcan._

- **007-13** — Dada una vigencia *En preparación* cuya lista de preparación se completa, entonces pasa a *Lista*; y dada una vigencia *Lista*, cuando se publica una versión nueva de una de sus tablas, entonces vuelve a *En preparación* (Doc. 01 §5).
- **V‑03 · 007-14** — Dada una vigencia *Lista*, cuando un `ADMIN_SISTEMA` y un `APROBADOR_TABLAS` firman la habilitación, entonces pasa a *Habilitada* con `assessments_enabled` verdadero; con una sola firma, sigue *Lista* y muestra que falta la otra.
- **007-15** — Dada una persona que tiene a la vez los roles `ADMIN_SISTEMA` y `APROBADOR_TABLAS`, cuando ya firmó la habilitación con uno de ellos e intenta firmar con el otro, entonces se rechaza, la vigencia sigue *Lista* y el intento queda en la auditoría: las dos firmas son de dos personas distintas. La regla la aplica el dominio, no solo la interfaz (`docs/arquitectura/02` §4.3; decisión 1, detalle 1a).
- **007-16** — Dada una firma pendiente, cuando la lista de preparación deja de estar completa antes de la segunda firma, entonces la firma pendiente se anula y la habilitación vuelve a empezar (decisión 1, detalle 1a).
- **V‑04 · 007-17** — Dada una vigencia *Cerrada*, cuando una opción 1.5.x pide asociarle un catálogo o una tabla nueva, entonces la vigencia lo rechaza. Quién cierra, cuándo y cómo se reabre está por preguntar al responsable funcional (decisión 1, detalle 1b).
- Los rechazos por segregación de funciones (007-15) y los intentos de habilitar con la lista incompleta quedan en la auditoría, con su motivo (Doc. 01 §8).

## Fuera de alcance

- **Exportar a Excel** la lista de vigencias (decisión 7): llega con la primera feature de consultas 1.6, que elige la librería de exportación con su ADR (pendiente 6 del Doc. 01 §11).
- **Los puntos 2 a 9 de la lista de preparación:** cada uno lo verifica la feature de su opción (1.3, 1.5.2 a 1.5.9 y el simulador 3.3, este último de la Fase 2). Esta feature solo los muestra como pendientes.
- **Qué estados de la vigencia admiten cargar cada tabla:** lo decide cada feature 1.5.x, con V‑04 como límite.
- **La apertura de cartera histórica** (5.1, Fase 5): la enmienda 4 deja crear vigencias de años pasados para liquidarlas en LIV, pero no decide desde qué vigencia se migran saldos del sistema anterior (`cartera_historica.vigencias_desde`, en `PENDIENTE` en la configuración de Ipiales). Tampoco decide qué pasa con un año que tenga a la vez saldos migrados y vigencia en LIV (véase «Preguntas abiertas»).
- **Las tablas de cada año pasado** (resolución y catálogo de avalúos, tarifas, calendario, beneficios, sanciones y configuración de jurisdicción de ese año): las carga la feature de cada opción 1.5.x y 1.5.9. Esta feature muestra sus puntos de la lista de preparación como pendientes, igual que para el año en curso (007-11).
- **Liquidar un año pasado** y lo que eso exige: intereses de mora desde su fecha límite, sanciones, prescripción de la acción de cobro y firmeza o caducidad de la facultad de liquidar (Doc. 02, Doc. 05 y revisión jurídica). Es de la Fase 2 y de las features de los Módulos 4 y 5. Crear la vigencia no liquida nada.
- **Un año mínimo distinto por jurisdicción:** 2002 es igual para todas (pregunta E4‑1).
- **Fechas de la vigencia distintas del año calendario** («parametrizable si la norma lo exige», V‑01): no se conoce ninguna norma que lo exija (decisión 6).
- **Habilitar la liquidación, la doble firma, el estado *Lista* y el cierre** (decisión 1): van en la feature 1.5.1 (véase «Trasladado a la feature 1.5.1»).
- **Liquidar** con la vigencia habilitada: es la Fase 2 (LQ‑P01).
- **La administración de usuarios y roles** (menú 1.2): los usuarios de prueba se dan de alta en Keycloak, como en la feature 001.

## Enmiendas de la H2

_Aprobadas el 2026-11-12 por la persona que dirige el trabajo, como responsable funcional, al aprobar la H2 del `plan.md` (decisiones H2‑6 a H2‑8). Cambian reglas, así que la H1 se firmó de nuevo para esos criterios. Las reglas nuevas van al Doc. 01 v0.5._

| # | Criterios | Cambio | Motivo |
|---|---|---|---|
| 1 (H2‑6) | 007-04, 007-06 | Además de V‑02, no se puede crear un año posterior al siguiente al año en curso (`V‑05`). La pantalla advierte, antes de crear, que una vigencia no se puede borrar y que los años anteriores a la más antigua quedan como históricos | Sin el límite, se podían encadenar años futuros sin forma de corregirlos, porque no hay borrado |
| 2 (H2‑7) | 007-20 | La entidad auditada es `tax.administration_fiscal_year`, con el identificador de su fila | Por la decisión 3, la fila que cambia es la de la administración, y `entity_id` es un `UUID` |
| 3 (H2‑8) | 007-08, 007-10 | Los rechazos citan códigos de regla del Doc. 01 v0.5: `V‑05` para la primera vigencia y el límite de años futuros, y `V‑06` para una transición no permitida | Un contrato no debe publicar números de criterios de una feature como códigos de error |

## Enmienda 4 · Años de 2002 al año siguiente al año en curso (propuesta, pendiente de H1)

_La pidió el 2026-11-16 la persona que dirige el trabajo, como responsable funcional, al probar la opción 1.4: «debe permitirme adicionar una vigencia desde el año 2002 hasta el año actual + 1». Cambia dos reglas aprobadas (V‑02 y V‑05) y toca el pendiente 5 del Doc. 01 §11, así que necesita la H1 antes del plan. Se reabre la feature 007: no es una feature nueva. Los criterios de arriba ya tienen el texto propuesto; si la H1 cambia una recomendación, se ajustan antes del plan._

**Respuestas de la persona a las preguntas de aclaración**

| Tema | Respuesta |
|---|---|
| Uso de los años pasados | Se liquidan en LIV y siguen el ciclo normal: *Planeada*, preparación, habilitación y liquidación. No son solo para la cartera histórica |
| Orden | Libre: cualquier año de 2002 al año en curso + 1 que no exista, en cualquier orden y con huecos |
| Año mínimo | Fijo en 2002, igual para todas las jurisdicciones |
| Flujo | Se reabre la 007 con una enmienda |

**Qué cambia**

| # | Criterios | Cambio | Motivo |
|---|---|---|---|
| 4a | 007-06 | V‑02 deja de rechazar: el orden es libre y se admiten huecos. Desaparecen los motivos `GAP` y `HISTORICAL` | La persona necesita crear años pasados para liquidarlos, en el orden en que reúna sus tablas |
| 4b | 007-07, 007-08 | V‑05 pasa a ser un rango, de 2002 al año siguiente al año en curso, igual para la primera vigencia y las demás. Desaparece `FIRST_YEAR`, aparece un motivo para un año anterior a 2002 (se propone `PAST_LIMIT`) y `FUTURE_LIMIT` sigue | El límite inferior es el de la persona. El superior sigue por el motivo de la enmienda 1: una vigencia no se borra |
| 4c | 007-03, 007-04, 007-22, 007-23 | El diálogo ofrece hasta 26 años en 2026, y uno más cada año, en una lista desplegable. La advertencia ya no habla de años históricos. El estado vacío dice el rango. La acción se inhabilita cuando ya existen todas | Un grupo de opciones (`RadioGroup`) no sirve para 26 años, y la advertencia aprobada dejaría de ser cierta |
| 4d | 007-21 | El diálogo cambia de diseño, y sus pruebas de navegador también | Las pantallas deben coincidir con Figma |

**Criterios que se reabren.** En `main`, todos los criterios siguen en `[ ]`: las marcas `[x]` están en la rama de cierre `docs/007-cierre`, sin integrar. Al retomar el cierre, vuelven a `[ ]` 007-03, 007-04, 007-06, 007-07, 007-08 y 007-21, y se agregan 007-22 y 007-23. Los demás criterios (007-01, 007-02, 007-05, 007-09 a 007-12 y 007-18 a 007-20) no cambian: un año pasado se consulta, se abre, se audita y se aísla igual que los demás.

**Qué hace la enmienda y qué no**

- **Hace:** crear la vigencia de un año pasado en *Planeada* y abrir su preparación, con los mismos permisos, la misma auditoría y el mismo aislamiento que cualquier otra vigencia.
- **No hace:** cargar las tablas de ese año, habilitar su liquidación ni liquidarlo (véase «Fuera de alcance»). Un año pasado se queda en *En preparación*, con los puntos 2 a 9 de su lista pendientes, hasta que existan las features 1.5.x. Cómo se liquida un año pasado lo decide el motor (Fase 2).

**Riesgos**

1. **Un año fijo en el código.** 2002 es un valor de la regla V‑05 y no de la configuración de jurisdicción, aunque `AGENTS.md` §4.5 y §5.1 prohíben escribir en el código valores tributarios, fechas entre ellos. Hay un precedente: el «+1» de V‑05 ya está en el dominio (H2‑6), porque no cambia entre jurisdicciones. El riesgo: si una jurisdicción necesita años anteriores a 2002, o si jurídica dice que un año ya no se puede liquidar, cambiarlo exige una versión del Doc. 01 y otra del backend. La mitigación: una sola constante con el código de la regla, citada en el Doc. 01, y una prueba que la fija (pregunta E4‑1).
2. **Liquidar años que ya no se pueden cobrar ni liquidar.** Un año de 2002 liquidado en 2026 puede estar prescrito para el cobro (cinco años, Doc. 05 §6.1) o fuera del término para liquidar de oficio. Crear la vigencia no liquida, pero abre el camino: la feature del motor debe decidirlo con jurídica (pregunta E4‑6).
3. **Un error irreversible es más fácil.** En una lista de 26 años es más fácil elegir uno equivocado, y una vigencia no se borra. La mitigación: no se marca de entrada ningún año pasado (E4‑3), y la advertencia y las fechas del año elegido están a la vista antes de confirmar.
4. **Un año pasado con dos orígenes.** Un mismo año podría tener saldos migrados por la cartera histórica (5.1) y liquidaciones de LIV. Lo decide la 5.1 (véase «Preguntas abiertas»).
5. **Un despliegue intermedio.** Si el backend nuevo corre con el frontend actual, el diálogo mostraría hasta 26 opciones con la advertencia antigua. Sin producción no tiene efecto. El plan ordena los PR del backend, la librería y el frontend, y el frontend no se versiona hasta su PR.

**Preguntas de la H1**

| # | Pregunta | Recomendación | Alternativas |
|---|---|---|---|
| E4‑1 | ¿Confirmas 2002 como año mínimo fijo, igual para todas las jurisdicciones y escrito en el código, como excepción aprobada a `AGENTS.md` §4.5? ¿De qué norma o motivo sale 2002, para citarlo como fuente de V‑05? | **Sí**, como regla del Doc. 01 (V‑05), igual que el «+1» de H2‑6: una constante del dominio con el código de la regla, no la configuración de jurisdicción. El Doc. 01 registra la fuente que des, la fecha de la decisión y el riesgo 1 | (b) Una propiedad de la aplicación: se cambia sin compilar, pero sigue siendo igual para todas. (c) La configuración de jurisdicción: permitiría variar por jurisdicción, pero la descartaste, y la configuración de un año se publica para una vigencia que ya debe existir |
| E4‑2 | ¿Qué control lleva el diálogo para elegir el año? | **Una lista desplegable**, un componente nuevo `Select` de `liv-components`, sobre el `<select>` nativo con su etiqueta. Se diseña en `02 Componentes` de Figma, se implementa en su propio cambio de `ui/` y se publica en 0.5.0 antes del PR del frontend (`AGENTS.md` §6.2). Sirve también para los filtros de las consultas 1.6 | (b) `TextField`, que ya existe, para escribir el año: no hay componente nuevo, pero la persona no ve qué años están libres y hacen falta más mensajes. (c) Seguir con `RadioGroup`: no hay componente nuevo, pero la lista es larga y crece cada año |
| E4‑3 | ¿En qué orden van los años y cuál viene marcado? | **Del más reciente al más antiguo.** Viene marcado el año en curso si se puede crear; si no, ninguno, y «Crear vigencia» espera a que se elija | (b) Ninguno marcado nunca. (c) Del más antiguo al más reciente, como hoy, que marcaría 2002 |
| E4‑4 | ¿Qué pasa con `relation` y sus etiquetas? | **El contrato no cambia** (`PAST`, `CURRENT` y `NEXT`). En la lista: «2026 · Año en curso», «2027 · Año siguiente al año en curso» y los años pasados solo con su número. La etiqueta de un año pasado del *frame* 75:4479 se retira | (b) Cada año pasado con «Año pasado»: se repetiría 24 veces |
| E4‑5 | ¿Se puede crear un año pasado cuando ya existen años posteriores, dejando huecos, y lo autoriza solo el `ADMIN_SISTEMA`? | **Sí a las dos**, como respondiste sobre el orden: el `ADMIN_SISTEMA`, con `tax.fiscal-year.create` y sin otra firma, como cualquier vigencia, y queda en la auditoría. Con esto el pendiente 5 del Doc. 01 §11 queda resuelto para la creación; lo que dice de la cartera pasa a la 5.1 | (b) Una segunda firma (`APROBADOR_TABLAS`) para los años pasados: más control, pero exige un flujo y un permiso nuevos (§4.5) |
| E4‑6 | ¿Aceptas que esta enmienda no decida si un año pasado se puede liquidar hoy (prescripción y firmeza)? | **Sí.** Se vuelve un pendiente del Doc. 02, que reemplaza su pendiente 7, para la feature del motor, con revisión jurídica antes de liquidar cualquier año pasado | (b) Limitar desde ahora el año mínimo por la prescripción (por ejemplo, el año en curso − 5): contradice tu pedido y sería una regla tributaria de cada jurisdicción |

**Cambios propuestos a los documentos funcionales.** Este PR no los edita (`AGENTS.md` §4.5): van en el paso 1 del plan, después de la H1.

- **Doc. 01 v0.6:**
  - *Encabezado:* la versión 0.6 cita la enmienda 4 de la feature 007.
  - *§5, V‑02:* «El orden de creación es libre: dentro del rango de V‑05 se puede crear cualquier año que la administración no tenga, antes de la más antigua, después de la más reciente o entre dos, y se admiten huecos». V‑02 ya no rechaza nada y conserva su número para que se pueda rastrear. Salen los motivos `GAP` y `HISTORICAL` y la frase «Estos dos motivos suponen…».
  - *§5, V‑05:* el rango va de 2002 al año siguiente al año en curso, igual para la primera vigencia y las demás. Un año anterior a 2002 se rechaza con `PAST_LIMIT` y dice el primer año. Uno posterior al límite se rechaza con `FUTURE_LIMIT` y dice el último. 2002 es fijo e igual para todas las jurisdicciones por decisión del responsable funcional, con su fecha y su fuente (E4‑1): es una excepción a la regla de no escribir valores en el código, y cambiarlo exige una versión del documento. Salen `FIRST_YEAR` y la frase «Quien crea como primera vigencia el año siguiente…». Se quedan la zona `America/Bogota` y el motivo del «+1».
  - *§5, orden de evaluación:* V‑01, V‑05 `PAST_LIMIT` y V‑05 `FUTURE_LIMIT`, con un ejemplo nuevo.
  - *§11, pendiente 5:* resuelto para la creación (feature 007, enmienda 4): se admiten vigencias desde 2002 para liquidarlas en LIV, y las crea el `ADMIN_SISTEMA` como cualquier otra. Sigue abierto para la cartera: desde qué vigencia se migran saldos (`cartera_historica.vigencias_desde`) y qué pasa con un año que tenga saldos migrados y vigencia en LIV. Lo decide la 5.1.
  - *§13:* la entrada de la versión 0.6, con los cambios a otros documentos.
- **Doc. 02 v0.4**, §12, pendiente 7: «Resuelto en parte: el motor sí liquida vigencias anteriores (Doc. 01 v0.6, V‑05)». Falta decidir con jurídica cómo se liquida un año pasado: los intereses de mora desde su fecha límite, las sanciones y qué años ya no se pueden liquidar ni cobrar (firmeza y prescripción, Doc. 05 §6).
- **Doc. 05 v0.2**, §11: un pendiente nuevo, el de un año con saldos migrados en 5.1 y vigencia en LIV.
- **Sin cambios:** el ERD, porque la tabla nacional ya tiene los años 1999 a 2100 (el rango técnico de H2‑2), 2002 incluido. Tampoco cambian el Doc. 03, los YAML de `anexos/` ni el glosario.

**Impacto preliminar** (para el `plan.md`; no es el plan)

| Proyecto | Qué cambia | Clasificación |
|---|---|---|
| Raíz: documentos | Doc. 01 v0.6, Doc. 02 v0.4 y Doc. 05 v0.2, como se propone arriba | Proceso de los documentos funcionales (versión y aprobación humana), paso 1 |
| Figma | Si E4‑2 se aprueba, el componente `Select` en `02 Componentes`. El diálogo «1.4 · Crear vigencia» (67:3040), con la lista, el orden, el año marcado y la advertencia nuevas. Sale la etiqueta `PAST` (75:4479). Cambian el texto del estado vacío y el motivo de la acción inhabilitada | Aprobación de la persona antes de cada PR que los use |
| `backend/`: contrato | `tax.yaml` pasa de 1.0.0 a 1.1.0. Cambian las descripciones: el orden de evaluación, `CreationRejected`, `creatableYears` («de cero a dos») y el ejemplo de `PAST`. Se agrega el motivo `PAST_LIMIT`, con la extensión opcional `minYear`, y un `minYear` opcional en `CreateBlocked` para el texto de 007-23. `GAP`, `HISTORICAL` y `FIRST_YEAR` dejan de emitirse. Los motivos son cadenas extensibles y las propiedades nuevas son opcionales, así que el cambio debería ser compatible (Q08): lo confirma `openapi-diff`. Si no lo es, necesita la etiqueta `contrato-incompatible` y la aprobación de la persona (§4.5) | Crítico (`backend/contracts/**`) |
| `backend/`: `tax` | En `FiscalYearOpeningPolicy`, el rango de 2002 al año en curso + 1, sin `GAP`, `HISTORICAL` ni `FIRST_YEAR` y con `PAST_LIMIT`. `createBlocked` cuando existen todos los años, y su Javadoc. Las pruebas que cambian: `FiscalYearOpeningPolicyTest`, `CreateFiscalYearTest`, `ListFiscalYearsTest`, `FiscalYearsApiTest` y `OpenApiContractTest`. No hay migración, porque la tabla nacional ya tiene los años. Tampoco cambian los permisos, la RLS ni la auditoría. El bloqueo por administración al crear se justificaba en parte por los huecos, que ahora se permiten: el plan decide si se queda solo para proteger V‑01 | Crítico (`backend/**/liv/tax/**`) |
| `ui/` | Si E4‑2 se aprueba, el componente nuevo `Select` en `liv-components` 0.5.0. Lo publica una persona antes del PR del frontend | Crítico por la ruta (`ui/**`) |
| `frontend/` | La copia fijada de `tax.yaml` 1.1.0 y sus modelos generados, y `liv-components` 0.5.0. En `create-fiscal-year-dialog`, el control, el orden, el año marcado, las etiquetas y la advertencia. En `fiscal-year-rejections`, el mensaje de `PAST_LIMIT`: los motivos que ya no llegan caen en el mensaje general. En `fiscal-years`, el estado vacío con el rango y el motivo de 007-23. También cambian sus pruebas, incluida axe | Normal por la ruta, dentro de una feature crítica |
| `e2e/` | En `vigencias.spec.ts`: 007-03 y 007-08; 007-06, con un año pasado y uno con hueco; el teclado de 007-21 con la lista desplegable; y la captura `vigencias-crear`. Para 007-23 hay que crear unas 26 vigencias: el plan decide si la prueba las prepara por la API o si queda solo en las pruebas del backend y del frontend | Normal |
| Spec, plan y tareas | La sección de la enmienda 4 en `plan.md`, las tareas nuevas en `tasks.md` y, en `docs/007-cierre`, las marcas reabiertas | Orquestador |

Orden propuesto: documentos, Figma, contrato y backend, `ui/` publicada, frontend, `e2e/` y cierre.

## Decisiones de la H1 (resueltas)

_La persona que dirige el trabajo aprobó el 2026-11-12 las siete recomendaciones de la spec («Sí, todas»; `AGENTS.md` §4.3, excepción del equipo de una persona). Cada decisión conserva el motivo con el que se propuso._

| # | Tema | Decisión |
|---|---|---|
| 1 | Hasta dónde llega la habilitación | **Opción A.** La feature llega hasta *En preparación*, con la lista de preparación de solo lectura y «Habilitar liquidación» inhabilitado con los faltantes (CA‑14). La habilitación con doble firma, el estado *Lista* y el cierre van en la feature 1.5.1, que se hará cuando existan los puntos 2 a 9 de la lista: la habilitación no tiene uso hasta que exista el motor (punto 8, Fase 2), y la doble firma es más segura si se construye cuando se puede ejercer de verdad |
| 1a | La doble firma (para la 1.5.1) | Dos personas distintas, una con `ADMIN_SISTEMA` y otra con `APROBADOR_TABLAS`, en cualquier orden; la primera firma queda pendiente, quien la dio la puede retirar y se anula si la lista deja de estar completa (007-15 y 007-16) |
| 1b | El cierre y la reapertura (V‑04) | Se le preguntan al responsable funcional y quedan fuera de esta feature (véase «Preguntas abiertas») |
| 2 | Vigencias históricas y primera vigencia | La primera vigencia de una administración es el año en curso o el siguiente (007-08); después, solo el año siguiente al más reciente (007-06); un año anterior al más antiguo se rechaza como histórico (007-07). La carga histórica se decide con la feature 5.1, que resuelve antes el pendiente 5 del Doc. 01 §11 (la primera vigencia de cartera, `cartera_historica.vigencias_desde`, está en `PENDIENTE` en la configuración de Ipiales). El límite de la primera vigencia es una regla nueva: se agrega al Doc. 01 §5 en el PR de documentos de esta feature. **La enmienda 4 (propuesta) la reemplaza:** cualquier año de 2002 al año siguiente al año en curso, en cualquier orden, también la primera vigencia |
| 3 | Alcance: nacional o por administración | **Opción C.** El año, con sus fechas, sigue nacional en `tax.fiscal_year`, y el estado y `assessments_enabled` pasan a una tabla nueva por administración, con RLS. El ajuste al modelo (ERD 2.3, Doc. 01 §5 v0.5 y la referencia de LQ‑P01 en el Doc. 02 §B.3) queda aprobado como alcance y va en su propio PR de documentos; el `plan.md` propone los nombres y las claves |
| 4 | Tipo de cambio | **Crítico:** toca `backend/**/liv/tax/**`, crea una migración con RLS, agrega permisos y roles, cambia el contrato OpenAPI y el modelo de datos, y crea pantallas. Lleva revisión adversarial y lente de riesgo de nivel alto, las cuatro lentes de `liv-revision-4r` antes de cada PR y la aprobación de los permisos nuevos en la H2 (`AGENTS.md` §4.5) |
| 5 | Pantallas y Figma | No hay diseño de la 1.4 en `03 Pantallas` y no aplica la excepción de reutilización (`AGENTS.md` §6.2): falta al menos una **tabla** en `liv-components`, cuyos 18 componentes son los de acceso y estructura de la feature 001. Se diseñan en Figma (sección «1.4 · Vigencias fiscales») las tres pantallas de abajo y los componentes que falten, y se aprueban antes de la H2; los componentes se implementan en `liv-components` en su propio cambio, con su versión publicada antes del PR del frontend. La tabla se diseña pensando en las consultas 1.6 (filtros, paginación del servidor), aunque la 007 use su forma básica |
| 6 | Fechas y configuración de jurisdicción | La vigencia la crea la opción 1.4, siempre del 1 de enero al 31 de diciembre. La configuración no crea la vigencia: al publicarla, la feature 1.5.9 comprueba que su sección `vigencia` coincide con la de la administración, como CJ10 hace con el catálogo, y ajusta la redacción del Doc. 03 §5.2, que hoy dice que esa sección «se materializa en» `tax.fiscal_year` |
| 7 | Exportación a Excel | Fuera de esta feature: llega con la primera feature de consultas 1.6, que trae la librería y su ADR (pendiente 6 del Doc. 01 §11) |

**Pantallas (decisión 5)**

| Pantalla | Contenido |
|---|---|
| 1.4 · Vigencias | Lista de vigencias con su estado y la acción «Crear vigencia» (solo `ADMIN_SISTEMA`); estado vacío |
| 1.4 · Crear vigencia | Diálogo con el año y las fechas que se derivan de él, y los rechazos de 007-05 a 007-08 |
| 1.4 · Detalle y preparación | Datos de la vigencia, la acción «Abrir preparación», la lista 1.5.1 con el estado de cada punto y el botón «Habilitar liquidación» inhabilitado con los faltantes (CA‑14) |

**Por qué el modelo de la decisión 3.** El ERD define `tax.fiscal_year` con `year` como clave y sin `administration_id`, pero la lista 1.5.1 es «por `fiscal_year` + `administration_id`» (Doc. 01 §6.1), toda operación se filtra por la administración del usuario (Doc. 01 §2, principio 7) y los roles valen dentro de una administración (`docs/arquitectura/02` §4.1). Con una vigencia nacional, que Ipiales habilitara la liquidación la habilitaría para todas, y el `ADMIN_SISTEMA` de una administración escribiría un dato compartido, que el RLS solo deja escribir al proceso de publicación (`docs/arquitectura/02` §5.1). Si `tax.fiscal_year` ganara `administration_id` (opción B), las tarifas nacionales (`tax.tax_rate` con `administration_id` nulo) no tendrían a qué vigencia apuntar.

## Preguntas abiertas

_Ninguna cambia un criterio de esta feature._

- **Cierre y reapertura de la vigencia (V‑04; decisión 1, detalle 1b).** El Doc. 01 §5 no dice quién cierra una vigencia ni cuándo, ni a qué estado vuelve una reapertura ni quién la autoriza. Se le pregunta al responsable funcional; la respuesta la necesita la feature 1.5.1, no esta.
- **Un año con saldos migrados y con vigencia en LIV (enmienda 4).** La cartera histórica (5.1) migra saldos de vigencias anteriores desde `cartera_historica.vigencias_desde`. Con la enmienda 4, ese mismo año también puede tener su vigencia en LIV y sus liquidaciones. Hay que decidir si un año puede tener las dos cosas y, si puede, cómo se evita cobrar dos veces. La respuesta la necesita la feature 5.1 (pendiente nuevo del Doc. 05), no esta.
