# 007 · Vigencias fiscales

**Estado:** aprobada (H1) · plan aprobado (H2) — 2026-11-13. H1 del 2026-11-12, por la persona que dirige el trabajo (`AGENTS.md` §4.3), con las siete recomendaciones de la spec (véase «Decisiones de la H1») · enmiendas 1, 2 y 3 (007-04, 007-06, 007-08, 007-10 y 007-20) aprobadas por la misma persona como responsable funcional en la H2 del 2026-11-12, que firmó de nuevo la H1 (véase «Enmiendas de la H2»)

**Enmienda 4 · en reapertura:** vigencias de 2002 al año siguiente al año en curso, en cualquier orden (007-03, 007-04, 007-06 a 007-08 y 007-21 a 007-23).
- **H1:** aprobada el 2026-11-16 por la misma persona como responsable funcional, sobre el commit e118aef, con las siete recomendaciones (véase «Enmienda 4»).
- **Plan:** aprobado (H2) el 2026-11-16 por la misma persona, sobre el commit bba0833 (#204). En esa H2 cambiaron 007-22 (H2‑18) y la decisión E4‑6 (H2‑20), y la persona firmó de nuevo su H1.

_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `tax` (vigencias), con la matriz de permisos de `platform`, la auditoría de `audit`, el frontend y, si el diseño lo pide, la librería `liv-components` · **Menú:** 1.4 · **Tipo de cambio:** crítico (`AGENTS.md` §4.3; decisión 4 de la H1)
**Fuentes:** Doc. 01 v0.4 §2 (principios 3, 7 y 9), §4 y §4.1 (roles, matriz de acciones y permisos del menú), §5 (estados y reglas V‑01 a V‑04), §6.1 (lista de preparación 1.5.1 y «Habilitar liquidación»), §7.1 (consultas y exportación), §8 (auditoría), §9 (CA‑12 y CA‑14) y §11 (pendiente 5); ERD 2.2, esquema `tax` (`tax.fiscal_year` y sus relaciones) y relaciones externas de `valuation` y `ledger`; Doc. 02 v0.2 §B.2 y §B.3 (LQ‑P01); Doc. 03 v0.5 §5.2 (sección `vigencia`), §5.5 y §5.6; Doc. 05 v0.1 §3 (`cartera_historica.vigencias_desde`); `docs/arquitectura/01` §3.1 y §15; `docs/arquitectura/02` §4.1 a §4.4, §5.1 a §5.3 y §9; `AGENTS.md` §4.3, §4.5, §5.1 y §6.2

> Primera feature de la Fase 1 («Tablas base y configuración»). La vigencia es el prerrequisito de todo el Módulo 1: los catálogos de avalúos, las tarifas, el calendario y la configuración de jurisdicción se cargan **para una vigencia** (Doc. 01 §5 y §6.1).

## Qué hace

Abre la opción **1.4 Vigencias fiscales**, que hoy aparece en el menú como «Próximamente». Con ella, la administración tributaria lleva el ciclo de vida de cada año fiscal:

- **Consultar** las vigencias de su administración: el año, las fechas de inicio y fin, el estado (*Planeada*, *En preparación*, *Lista*, *Habilitada* o *Cerrada*) y si la liquidación está habilitada. La ven los cinco roles del Módulo 1 que hoy tienen la opción en el menú.
- **Crear** una vigencia nueva, en estado *Planeada*. Solo el `ADMIN_SISTEMA`. Las fechas son del 1 de enero al 31 de diciembre del año (V‑01). Se puede crear cualquier año de 2002 al año siguiente al año en curso que la administración aún no tenga, en cualquier orden y con huecos, también la primera vigencia (V‑02 y V‑05; enmienda 4, que reemplaza la decisión 2). En esta feature, un año pasado sigue el mismo ciclo que los demás hasta *En preparación*; para liquidarlo, véase la decisión E4‑6.
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
- [ ] **007-03** — Dada una administración sin vigencias, cuando un usuario con permiso de consulta abre la opción 1.4, entonces ve un estado vacío que lo explica y, si es `ADMIN_SISTEMA`, la acción para crear la primera. Para el `ADMIN_SISTEMA`, la explicación dice el primer año y el último que se pueden crear, sin enumerarlos uno por uno; por ejemplo, en 2026, «de 2002 a 2027». Los dos años salen de lo que dice el servidor (enmienda 4). _Prueba:_ pendiente

**B. Creación de la vigencia**

- [ ] **V‑01 · 007-04** — Dado un `ADMIN_SISTEMA`, cuando crea la vigencia de un año, entonces la vigencia queda en estado *Planeada*, sin la liquidación habilitada, con inicio el 1 de enero y fin el 31 de diciembre de ese año. Las fechas no se escriben a mano (decisión 6). Antes de crearla, la pantalla advierte que una vigencia no se puede borrar y pide revisar el año. Si el año es pasado, advierte además que solo se podrá liquidar cuando estén cargadas las tablas de ese año (decisión E4‑6, cambiada en la H2 del 2026-11-16). El texto exacto es el del diseño aprobado en Figma (enmienda 4). _Prueba:_ pendiente
- [ ] **V‑01 · 007-05** — Dada una vigencia que ya existe en la administración, cuando un `ADMIN_SISTEMA` intenta crear otra del mismo año, entonces se rechaza con un mensaje que cita V‑01 y no cambia nada. Si dos personas crean el mismo año al mismo tiempo, se crea una sola vigencia y la otra recibe ese rechazo. _Prueba:_ pendiente
- [ ] **V‑02 · 007-06** — Dada una administración con vigencias, cuando un `ADMIN_SISTEMA` crea un año que no tiene, entre 2002 y el año siguiente al año en curso, entonces la vigencia se crea. Da igual si el año es anterior a la vigencia más antigua, posterior a la más reciente (aunque queden años sin crear entre las dos) o uno de los que faltan entre dos vigencias. El orden es libre y se admiten huecos. Por ejemplo, en 2026 y con solo la vigencia 2026, se pueden crear 2002, 2015 y 2027, en ese orden o en cualquier otro. Ningún rechazo cita V‑02 (enmienda 4). _Prueba:_ pendiente
- [ ] **V‑05 · 007-07** — Dado un `ADMIN_SISTEMA`, con vigencias o sin ellas, cuando intenta crear un año que no existe en su administración y que es anterior a 2002, entonces se rechaza con un mensaje que cita V‑05 y dice cuál es el primer año que se puede crear. Si el año es posterior al año siguiente al año en curso, se rechaza con un mensaje que cita V‑05 y dice cuál es el último. En los dos casos no cambia nada. Un año que ya existe da el rechazo de V‑01 (007-05). Un año fuera de la tabla nacional (1999 a 2100) sigue siendo una solicitud mal formada, como en el contrato aprobado (enmienda 4). _Prueba:_ pendiente
- [ ] **V‑05 · 007-08** — Dada una administración sin vigencias, cuando un `ADMIN_SISTEMA` crea la primera, entonces se admite cualquier año de 2002 al año siguiente al año en curso, el mismo rango que para las demás. El año en curso es el de la hora oficial de Colombia, según el reloj del sistema. La primera vigencia ya no tiene una regla propia (enmienda 4). _Prueba:_ pendiente
- [ ] **007-09** — Dado un usuario sin rol `ADMIN_SISTEMA` (por ejemplo, `APROBADOR_TABLAS` o `CARGADOR_TABLAS`), cuando intenta crear una vigencia o abrir su preparación por la API, entonces recibe «Sin permiso» (403), no cambia nada y la interfaz no le muestra esas acciones (Doc. 01 §4, «Crear / abrir vigencia fiscal»). _Prueba:_ pendiente
- [ ] **V‑05 · 007-22** — Dado un `ADMIN_SISTEMA`, cuando abre «Crear vigencia», entonces el diálogo cumple lo siguiente (enmienda 4; decisiones E4‑2 a E4‑4):
  - El año se elige en una lista desplegable sobre un `<select>` nativo, con la etiqueta accesible «Año de la vigencia». Sin año elegido, la lista muestra una opción vacía, «Elige un año», que no se puede enviar.
  - La lista ofrece los años que la creación aceptaría, y solo esos: de 2002 al año siguiente al año en curso, sin los que ya existen. Van en el orden en que los da el servidor, del más reciente al más antiguo, y la pantalla no calcula años.
  - El año en curso dice «Año en curso» y el siguiente, «Año siguiente al año en curso»; los años pasados solo muestran su número.
  - Si el año en curso se puede crear, viene elegido; si no, ninguno viene elegido. La regla es la misma al abrir el diálogo y al releer la lista después de un rechazo de V‑01 (007-05).
  - Si no se sabe si la creación ocurrió (sin respuesta, tiempo agotado o error del servidor), al releer la lista sigue elegido el año intentado si todavía está entre los que se pueden crear. Si ya no está, ninguno viene elegido. Así, en ese caso, el diálogo no cambia por su cuenta el año que la persona eligió (H2‑18 del plan; la persona firmó de nuevo la H1 de este punto el 2026-11-16).
  - Sin año elegido, «Crear vigencia» está inhabilitado y el diálogo no muestra fechas. Con un año elegido, muestra su inicio y su fin.
  - El foco inicial va a la lista, nunca a «Crear vigencia», para que Enter no cree nada al abrir.

  _Prueba:_ pendiente
- [ ] **V‑05 · 007-23** — Dada una administración que ya tiene todas las vigencias de 2002 al año siguiente al año en curso, cuando un `ADMIN_SISTEMA` ve la opción 1.4, entonces «Crear vigencia» está inhabilitado y dice por qué: ya existen todas las vigencias que se pueden crear, de 2002 al año siguiente al año en curso (en 2026, de 2002 a 2027). La API no ofrece ningún año, y crear uno se rechaza con V‑01 o V‑05, según el año (enmienda 4). _Prueba:_ pendiente

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

- [ ] **007-21** — Dadas las pantallas de la opción 1.4, entonces están construidas solo con componentes de `liv-components`, coinciden con su diseño aprobado en Figma (decisión 5), pasan axe sin violaciones de WCAG 2.1 AA y tienen sus pruebas de navegador en `e2e/` en verde contra el entorno local (ADR 0030). Incluye el diálogo de creación con su lista desplegable, que se diseña en Figma y se aprueba antes de implementarse (enmienda 4). _Prueba:_ pendiente

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
- **La apertura de cartera histórica** (5.1, Fase 5): desde qué vigencia se migran saldos (`cartera_historica.vigencias_desde`, en `PENDIENTE` en la configuración de Ipiales) y su relación con las vigencias de años pasados que crea la enmienda 4 (véase «Preguntas abiertas»).
- **Liquidar un año pasado, cargar sus tablas y retirar una vigencia que resulte no liquidable** (enmienda 4, decisiones E4‑6 y E4‑7): véase «Enmienda 4», «Qué hace la enmienda y qué no».
- **Fechas de la vigencia distintas del año calendario** («parametrizable si la norma lo exige», V‑01): no se conoce ninguna norma que lo exija (decisión 6).
- **Habilitar la liquidación, la doble firma, el estado *Lista* y el cierre** (decisión 1): van en la feature 1.5.1 (véase «Trasladado a la feature 1.5.1»).
- **Liquidar** con la vigencia habilitada: es la Fase 2 (LQ‑P01).
- **La administración de usuarios y roles** (menú 1.2): los usuarios de prueba se dan de alta en Keycloak, como en la feature 001.

## Enmiendas de la H2

_Aprobadas el 2026-11-12 por la persona que dirige el trabajo, como responsable funcional, al aprobar la H2 del `plan.md` (decisiones H2‑6 a H2‑8). Cambian reglas, así que la H1 se firmó de nuevo para esos criterios. Las reglas nuevas van al Doc. 01 v0.5._

| # | Criterios | Cambio | Motivo |
|---|---|---|---|
| 1 (H2‑6) | 007-04, 007-06 | Además de V‑02, no se puede crear un año posterior al siguiente al año en curso (`V‑05`). La pantalla advierte, antes de crear, que una vigencia no se puede borrar y que los años anteriores a la más antigua quedan como históricos. **La advertencia de los históricos la reemplaza la enmienda 4** | Sin el límite, se podían encadenar años futuros sin forma de corregirlos, porque no hay borrado |
| 2 (H2‑7) | 007-20 | La entidad auditada es `tax.administration_fiscal_year`, con el identificador de su fila | Por la decisión 3, la fila que cambia es la de la administración, y `entity_id` es un `UUID` |
| 3 (H2‑8) | 007-08, 007-10 | Los rechazos citan códigos de regla del Doc. 01 v0.5: `V‑05` para la primera vigencia y el límite de años futuros, y `V‑06` para una transición no permitida. **La regla de la primera vigencia la retira la enmienda 4** | Un contrato no debe publicar números de criterios de una feature como códigos de error |

## Enmienda 4 · Años de 2002 al año siguiente al año en curso

_La pidió el 2026-11-16 la persona que dirige el trabajo, como responsable funcional, al probar la opción 1.4: «debe permitirme adicionar una vigencia desde el año 2002 hasta el año actual + 1». En esta spec, «el año actual + 1» se escribe «el año siguiente al año en curso», como en V‑05. La enmienda cambia dos reglas aprobadas (V‑02 y V‑05) y toca el pendiente 5 del Doc. 01 §11. Se reabre la feature 007: no es una feature nueva._

_**H1:** la persona que dirige el trabajo, como responsable funcional, la aprobó el 2026-11-16 sobre el commit e118aef, con las siete recomendaciones (véase «Decisiones de la H1 de la enmienda 4»). Los cambios a los documentos funcionales van en el paso 13 del plan de la enmienda. En la H2 del 2026-11-16 cambiaron 007-22 y E4‑6, con la H1 firmada de nuevo._

**Respuestas de la persona a las preguntas de aclaración**

| Tema | Respuesta |
|---|---|
| Uso de los años pasados | Se liquidan en LIV y siguen el ciclo normal: *Planeada*, preparación, habilitación y liquidación. No son solo para la cartera histórica |
| Orden | Libre: cualquier año de 2002 al año siguiente al año en curso que no exista, en cualquier orden y con huecos |
| Año mínimo | Fijo en 2002, igual para todas las jurisdicciones |
| Flujo | Se reabre la feature 007 con una enmienda |

**Qué cambia**

| # | Criterios | Cambio | Motivo |
|---|---|---|---|
| 4a | 007-06 | V‑02 deja de rechazar: el orden es libre y se admiten huecos. Salen los motivos `GAP` y `HISTORICAL` | La persona necesita crear años pasados para liquidarlos, en el orden en que reúna sus tablas |
| 4b | 007-07, 007-08 | V‑05 pasa a ser un rango, de 2002 al año siguiente al año en curso, igual para la primera vigencia y las demás. Sale `FIRST_YEAR`, entra `PAST_LIMIT` para un año anterior a 2002 y `FUTURE_LIMIT` se queda | El límite inferior lo pide la persona. El superior se queda por el motivo de la enmienda 1 (H2‑6): una vigencia no se borra, y sin él se encadenarían años futuros |
| 4c | 007-03, 007-04, 007-22, 007-23 | El diálogo ofrece hasta 26 años en 2026, y uno más cada año, en una lista desplegable. La advertencia ya no habla de años históricos y, para un año pasado, avisa que solo se podrá liquidar con las tablas de ese año cargadas. El estado vacío dice el rango. La acción se inhabilita cuando ya existen todas las vigencias | Un grupo de opciones (`RadioGroup`) no sirve para 26 años, y la advertencia aprobada dejaría de ser cierta |
| 4d | 007-21 | El diálogo cambia de diseño, y sus pruebas de navegador también | Las pantallas deben coincidir con Figma |

**Criterios.** Los criterios reescritos (007-03, 007-04, 007-06 a 007-08 y 007-21) y los nuevos (007-22 y 007-23) están en «Criterios de aceptación», con la marca «(enmienda 4)». El texto anterior queda en el historial de Git.

**Lo que se vuelve a verificar.** En `main` todos los criterios siguen en `[ ]`. Las marcas `[x]` están en la rama de cierre `docs/007-cierre`, sin integrar. Al retomar el cierre:
- vuelven a `[ ]` los criterios que cambian: 007-03, 007-04, 007-06, 007-07, 007-08 y 007-21;
- se agregan 007-22 y 007-23;
- 007-05, 007-09 a 007-12, 007-18 y 007-19 no cambian de texto, pero sus pruebas sí, porque las de navegador eligen los años de la lista que da el servidor y las del bloqueo de creación concurrente cambian (el bloqueo se queda, H2‑16 del plan). Se vuelven a marcar con las pruebas nuevas;
- 007-01, 007-02 y 007-20 no cambian: un año pasado se consulta, se audita y se aísla igual que los demás.

**Qué hace la enmienda y qué no**

- **Hace:** crear la vigencia de un año pasado en *Planeada* y abrir su preparación, con los mismos permisos, la misma auditoría y el mismo aislamiento que cualquier otra vigencia.
- **No hace:** cargar las tablas de ese año, habilitar su liquidación ni liquidarlo. Un año pasado se queda en *En preparación*, con los puntos 2 a 9 de su lista pendientes, hasta que existan las features 1.5.x. Lo que necesita cada una para un año pasado está en el riesgo 3.
- **Fuera de alcance:**
  - la apertura de cartera histórica (5.1): desde qué vigencia se migran saldos (`cartera_historica.vigencias_desde`) y la relación con las vigencias de LIV (véase «Preguntas abiertas»);
  - liquidar un año pasado, con los intereses de mora desde su fecha límite, las sanciones, la prescripción del cobro y la firmeza: es de la Fase 2 y de los Módulos 4 y 5;
  - un año mínimo distinto por jurisdicción (E4‑1);
  - retirar una vigencia que resulte no liquidable (E4‑7).

**Riesgos**

1. **Un año fijo en el código, frente a la constitución.**
   - **Qué choca:** la constitución dice que las fechas, entre otras reglas tributarias, se leen de la configuración de jurisdicción (`spec/constitution/mission.md`, «Ninguna regla tributaria en el código»). `AGENTS.md` §4.5 y §5.1 dicen lo mismo, y cambiar la constitución exige un ADR (§4.2).
   - **Lo que decidía:** de dónde sale 2002. Si saliera de una norma (la prescripción, la firmeza o la competencia de un año), sería un valor tributario e iría en la configuración, o en un ADR que cambiara la constitución. Si es una decisión de producto, es un límite operativo, como el «+1» de H2‑6, y basta el Doc. 01.
   - **Resolución (E4‑1):** es una decisión de producto sobre la primera vigencia que admite LIV, y no viene del año modelo de las tablas del Ministerio (Doc. 03 §3). Por eso la spec no lo trata como valor tributario. La persona aprobó además una excepción explícita a la prohibición de escribir valores tributarios en el código (`AGENTS.md` §4.5), por si 2002 se entendiera como una fecha tributaria. El 2026-11-16 la persona decidió además que **no hace falta un ADR sobre la constitución** (`spec/constitution/mission.md`; `AGENTS.md` §4.2): 2002 no fija plazos, tarifas ni vencimientos; solo acota qué años puede crear LIV, como el «+1» de H2‑6. La constitución no cambia.
   - **Lo que queda:** si una jurisdicción necesita años anteriores a 2002, cambiarlo exige una versión del Doc. 01 y otra del backend.
2. **Vigencias que no se pueden borrar y quizá no se puedan liquidar.**
   - **Por qué:** un año de 2002 liquidado en 2026 puede estar prescrito para el cobro (cinco años, Doc. 05 §6.1) o fuera del término para liquidar de oficio. Además, la administración debe haber tenido competencia sobre el impuesto ese año: las modalidades de `competencia.modalidades` se configuran en la configuración de jurisdicción de cada año (Doc. 03 §9.5).
   - **Qué pasa:** si jurídica descarta un rango, sus vigencias se quedan para siempre en *Planeada* o *En preparación*, porque el diagrama del Doc. 01 §5 no tiene anulación.
   - **Mitigación:** la advertencia de 007-04 y la decisión E4‑7. En la H2 del 2026-11-16, la persona decidió que liquidar un año pasado no exige un concepto jurídico (E4‑6).
   - **Riesgo residual, abierto:** liquidar un año prescrito, en firme o sin competencia. Se resuelve en la Fase 2 y en el Módulo 5, con el pendiente 7 del Doc. 02. Esta feature no lo controla.
3. **Las tablas de un año pasado pueden no caber en las opciones 1.5.x.**
   - **Formatos:** las opciones 1.5.x se diseñan sobre la estructura real de las tablas de 2026 (Doc. 03 §3) y sus reglas `R0xx` y `F0xx`. Las resoluciones de años anteriores pueden tener otro formato.
   - **Configuración:** la configuración de cada año pasado debe pasar CJ01 a CJ16, también CJ15 y CJ16, que se aplican a toda publicación nueva.
   - **Tasas:** los intereses de mora de un año pasado liquidado en LIV necesitan las tasas de usura desde su fecha límite. El pendiente 7 del Doc. 05 solo pide las de los años a migrar con `RECALCULAR`, así que hace falta un pendiente aparte (va en el del Doc. 02).
   - **Quién lo hereda:** las features 1.5.3, 1.5.9 y 5.2, que deben estimar ese trabajo. Sin eso, la vigencia de un año pasado no sale de la preparación.
4. **Un error que no se corrige es más fácil.** En una lista de 26 años es más fácil elegir uno equivocado, y una vigencia no se borra. La mitigación: ningún año pasado viene elegido (007-22), y la advertencia y las fechas del año elegido están a la vista antes de confirmar.
5. **Efectos en el cobro.** Un pago sin indicación de vigencia se imputa a la más antigua primero (Doc. 04 §5, `imputacion.sin_indicacion`). Una obligación de un año pasado recién liquidada recibiría primero los pagos que el contribuyente quizá hizo para el año en curso. Lo debe revisar la feature del motor, con jurídica.
6. **Un despliegue intermedio.**
   - **Qué pasa:** el diálogo actual elige el primer año de la lista. Con el backend nuevo y el frontend actual, abriría con un año pasado elegido (2002, si el orden sigue siendo del más antiguo al más reciente) y con la advertencia antigua, y un clic crearía una vigencia que no se borra. Pasa también en el entorno local donde la persona prueba.
   - **Mitigación:** antes del backend se integra un cambio pequeño del frontend, compatible con el contrato 1.0.0, que solo elige un año `CURRENT` o `NEXT` y nunca uno pasado. Así la prueba de navegador de 007-05 en la administración B, que espera el año siguiente elegido al releer la lista, sigue en verde. Si el cambio rompiera una prueba de `e2e/`, iría con su propio PR de `e2e/`, integrado antes que el contrato y el backend. La otra salida es integrar el backend junto con el frontend que lo adopta.
   - **El caso más probable:** con el orden nuevo, del más reciente al más antiguo, el frontend actual elegiría el año siguiente (2027) y mostraría unas 26 opciones con la advertencia antigua de «históricos».

**Decisiones de la H1 de la enmienda 4**

_Aprobadas el 2026-11-16 por la persona que dirige el trabajo, como responsable funcional, con las siete recomendaciones (`AGENTS.md` §4.3, excepción del equipo de una persona), sobre el commit e118aef. En la H2 del 2026-11-16 la persona cambió E4‑6 (H2‑20) y precisó E4‑3 para el caso de un resultado que no se conoce (H2‑18), con la H1 firmada de nuevo. Cada decisión conserva las alternativas que se descartaron._

| # | Tema | Decisión | Alternativas descartadas |
|---|---|---|---|
| E4‑1 | El año mínimo 2002 | **Decisión de producto:** 2002 es la primera vigencia que admite LIV, un límite operativo de V‑05 como el «+1» de H2‑6, y no un valor tributario ni el año modelo de las tablas. Es una constante del dominio con el código de la regla y una prueba que la fija, y el Doc. 01 la registra con esta fecha y su motivo. La persona la aprobó también como **excepción explícita** a la prohibición de escribir valores tributarios en el código (`AGENTS.md` §4.5), por si 2002 se entendiera como una fecha tributaria. El 2026-11-16 decidió además que **no hace falta un ADR sobre la constitución:** 2002 no fija plazos, tarifas ni vencimientos, solo acota qué años puede crear LIV, como el «+1». El plan registra la excepción de `AGENTS.md` §4.5 junto a la constante (riesgo 1) | (b) Una propiedad de la aplicación: se cambia sin compilar, pero sigue siendo igual para todas. (c) La configuración de jurisdicción: permite variar por jurisdicción, pero la persona la descartó, y la configuración de un año se publica para una vigencia que ya debe existir |
| E4‑2 | El control del diálogo | **Una lista desplegable:** un componente nuevo `Select` en `liv-components`, que envuelve el `<select>` nativo y su etiqueta. Se diseña primero en `02 Componentes` de Figma, se implementa en su propio cambio de `ui/` y se publica en 0.5.0 antes del PR del frontend (`AGENTS.md` §6.2). Sirve también para los filtros de las consultas 1.6 | (b) `TextField` para escribir el año: no se ven los años libres y hacen falta más mensajes. (c) Seguir con `RadioGroup`: la lista es larga y crece cada año |
| E4‑3 | El orden y el año elegido | **Del más reciente al más antiguo,** en el orden que da el servidor. Viene elegido el año en curso; si ya existe, ninguno, también al releer la lista después de un V‑01. **Precisada en la H2 del 2026-11-16 (H2‑18):** si no se sabe si la creación ocurrió, al releer sigue elegido el año intentado si todavía se puede crear, y si no, ninguno (007-22) | (b) Que no venga elegido ninguno nunca. (c) Del más antiguo al más reciente, como hasta ahora, que elegiría 2002 |
| E4‑4 | `relation` y sus etiquetas | **`relation` no cambia** en el contrato (`PAST`, `CURRENT` y `NEXT`). Solo el año en curso («Año en curso») y el siguiente («Año siguiente al año en curso») llevan etiqueta; los años pasados, solo su número. Se retira la etiqueta de un año pasado (nodo 75:4479 de Figma) | (b) Cada año pasado con «Año pasado»: se repetiría 24 veces |
| E4‑5 | Orden libre y quién crea | **Se puede crear un año pasado aunque existan años posteriores,** con huecos. Basta el `ADMIN_SISTEMA` con `tax.fiscal-year.create`, sin otra firma, y queda en la auditoría. El control que compensa es la doble firma de la habilitación de la 1.5.1 (007-14 y 007-15): ningún año se liquida sin dos personas. El pendiente 5 del Doc. 01 §11 queda resuelto para la creación, y lo que dice de la cartera pasa a la 5.1 | (b) Una segunda firma (`APROBADOR_TABLAS`) para crear años pasados: exige un flujo y un permiso nuevos (§4.5) |
| E4‑6 | Si un año pasado se puede liquidar | **Cambiada en la H2 del 2026-11-16 (H2‑20 del plan), con la H1 firmada de nuevo por la persona:** liquidar una vigencia de un año pasado **no exige un concepto jurídico**. Se liquida con los mismos prerrequisitos que cualquier vigencia (`LQ‑P01` a `LQ‑P04` del Doc. 02 §B.3), y sus tablas las exige la habilitación de la 1.5.1 (V‑03). No hay prerrequisito nuevo en el Doc. 02. La prescripción, la firmeza y la competencia de la administración ese año **siguen abiertas** en el pendiente 7 del Doc. 02. Son preguntas para la feature del motor (Fase 2) y el Módulo 5 (Doc. 05 §6), y no bloquean nada en esta feature. *Decisión de la H1, reemplazada en la H2:* un `LQ‑P` nuevo que impedía liquidar un año pasado sin un concepto jurídico sobre la prescripción, la firmeza y la competencia | (b) Limitar desde ahora el año mínimo por la prescripción (por ejemplo, el año en curso − 5): contradice el pedido y sería una regla tributaria de cada jurisdicción |
| E4‑7 | Vigencias de años no liquidables | **Se aceptan vigencias permanentes,** con un pendiente nuevo del Doc. 01 §11: cómo se retira una vigencia que no se puede liquidar, sin borrarla. Lo decide la feature 1.5.1, junto con el cierre (pendiente 7) | (b) Admitir solo los años que no hayan prescrito: contradice el pedido |

**Cambios propuestos a los documentos funcionales.** Este PR no los edita (`AGENTS.md` §4.5): van en el paso 13 del plan de la enmienda, como propuestas pendientes de revisión jurídica (§8).

- **Doc. 01 v0.6:**
  - *Encabezado:* la versión 0.6 cita la enmienda 4 de la feature 007.
  - *§5, V‑02:* «El orden de creación es libre: dentro del rango de V‑05 se puede crear cualquier año que la administración no tenga, antes de la más antigua, después de la más reciente o entre dos, y se admiten huecos». V‑02 ya no rechaza nada y conserva su número para que se pueda rastrear. Salen los motivos `GAP` y `HISTORICAL` y la frase «Estos dos motivos suponen…».
  - *§5, V‑05:*
    - el rango va de 2002 al año siguiente al año en curso, igual para la primera vigencia y las demás;
    - un año anterior a 2002 se rechaza con `PAST_LIMIT` y dice el primer año; uno posterior al año siguiente al año en curso se rechaza con `FUTURE_LIMIT` y dice el último;
    - registra 2002 como límite operativo, por la decisión de producto del responsable funcional del 2026-11-16 (E4‑1), con la excepción explícita a `AGENTS.md` §4.5 que aprobó;
    - salen `FIRST_YEAR` y la frase «Quien crea como primera vigencia el año siguiente…»;
    - se quedan la zona `America/Bogota` y el motivo del «+1».
  - *§5, orden de evaluación:* V‑01, V‑05 `PAST_LIMIT` y V‑05 `FUTURE_LIMIT`, con un ejemplo nuevo.
  - *§11, pendiente 5:* resuelto para la creación (feature 007, enmienda 4). Se admiten vigencias desde 2002 para liquidarlas en LIV, y las crea el `ADMIN_SISTEMA` como cualquier otra. Sigue abierto para la cartera: desde qué vigencia se migran saldos y la relación con las vigencias de LIV. Lo decide la 5.1.
  - *§11, pendiente nuevo:* cómo se retira una vigencia que no se puede liquidar (E4‑7).
  - *§13:* la entrada de la versión 0.6, con los cambios a otros documentos.
- **Doc. 02 v0.4:**
  - *§B.3:* sin cambios. Por E4‑6, tal como quedó en la H2 del 2026-11-16, no hay prerrequisito nuevo: un año pasado se liquida con `LQ‑P01` a `LQ‑P04`, como cualquier vigencia, y sus tablas las exige la habilitación de la 1.5.1 (V‑03).
  - *§12, pendiente 7:* se propone dar por respondida su pregunta («si se liquidan vigencias anteriores con este motor»): sí, sin concepto jurídico previo (E4‑6). El pendiente **no se reduce**. Siguen abiertas, para la feature del motor (Fase 2) y el Módulo 5 (Doc. 05 §6), sin bloquear nada en esta feature:
    - la prescripción, la firmeza y la competencia de la administración ese año, con los criterios que aportó la persona (paso 13 del plan), como propuesta pendiente de revisión jurídica;
    - los intereses de mora desde su fecha límite y las sanciones;
    - las tasas de usura desde esa fecha (riesgo 3);
    - qué hace el motor con un año que ya tiene una obligación migrada;
    - el efecto de la imputación a la más antigua primero (Doc. 04 §5).
- **Doc. 05 v0.2, §11:** un pendiente nuevo, el de un año con saldos migrados en la 5.1 y vigencia en LIV: cuál de los dos orígenes prevalece. CH‑02 y OB‑01 ya impiden la obligación doble.
- **ERD 2.4:** la descripción de `tax.fiscal_year` dice hoy que su rango técnico (1999 a 2100) «no decide qué vigencias históricas se cargarán (pendiente 5)». Debe decir que el límite inferior lo fija V‑05. La tabla y sus años no cambian. El comentario de la migración `V202611131000` queda desactualizado, porque una migración aplicada no se edita.
- **Sin cambios:** el Doc. 03, el Doc. 04 (su efecto va como pendiente del Doc. 02), los YAML de `anexos/` y el glosario.

**Impacto preliminar** (para el `plan.md`; no es el plan)

| Proyecto | Qué cambia | Clasificación |
|---|---|---|
| Raíz: documentos | Doc. 01 v0.6, Doc. 02 v0.4, Doc. 05 v0.2 y ERD 2.4, como se propone arriba. Sin ADR: la persona decidió el 2026-11-16 que 2002 es un límite operativo y que la constitución no cambia (E4‑1, riesgo 1) | Proceso de los documentos funcionales (versión y aprobación humana), paso 1 |
| Figma | El componente `Select` en `02 Componentes`. En el diálogo «1.4 · Crear vigencia» (67:3040): la lista, el orden, el año elegido, el estado sin año elegido y la advertencia nueva. Se retira la etiqueta de un año pasado (75:4479). Cambian el texto del estado vacío y el motivo de la acción inhabilitada | Aprobación de la persona antes de cada PR que los use |
| `backend/`: contrato | `tax.yaml` pasa de 1.0.0 a 1.1.0:<br>• **descripciones:** el orden de evaluación, `CreationRejected`, `creatableYears` (hoy dice que trae de cero a dos años) y el ejemplo de `PAST`;<br>• **orden de `creatableYears`:** queda documentado, del más reciente al más antiguo, y lo da el servidor;<br>• **motivos:** se agrega `PAST_LIMIT` con la extensión opcional `minYear`; `GAP`, `HISTORICAL` y `FIRST_YEAR` dejan de emitirse, y sus extensiones (`requiredYear`, `oldestYear`, `currentYear` y `nextYear`) se quedan en el esquema como obsoletas para no quitar propiedades;<br>• **`CreateBlocked`:** un motivo nuevo, `ALL_CREATED` («ya existen todas»), con un `minYear` que siempre viene con ese motivo; `FUTURE_LIMIT` deja de emitirse en `createBlocked` y se queda documentado, para no quitarlo, y sigue como motivo de rechazo en `CreationRejected`. El frontend actual muestra su texto general ante `ALL_CREATED`, que no conoce.<br>Si es compatible (Q08) lo dicen `openapi-diff`, para la estructura, y una revisión del significado, porque `openapi-diff` no la ve. Si no es compatible, necesita la etiqueta `contrato-incompatible` y la aprobación de la persona (§4.5) | Crítico (`backend/contracts/**`) |
| `backend/`: `tax` | `FiscalYearOpeningPolicy`:<br>• el rango de 2002 al año siguiente al año en curso y `PAST_LIMIT`, sin `GAP`, `HISTORICAL` ni `FIRST_YEAR`;<br>• `creatableYears` del más reciente al más antiguo y `createBlocked` con `ALL_CREATED`;<br>• su Javadoc.<br>No hay migración, porque la tabla nacional ya tiene los años. Tampoco cambian los permisos, la RLS ni la auditoría.<br>El bloqueo de creación concurrente por administración se justificaba por los huecos, que ahora se permiten. Si el plan lo quita, V‑01 bajo concurrencia depende solo de traducir el `23505`, y la prueba de 007-05 debe correr dos transacciones reales en paralelo sin el bloqueo.<br>Pruebas que cambian: `FiscalYearOpeningPolicyTest`, `CreateFiscalYearTest`, `ListFiscalYearsTest`, `FiscalYearsApiTest`, `OpenApiContractTest`, `JpaFiscalYearsRacesTest` (su caso de V‑02 `GAP` al cruzar la medianoche pierde la premisa), `FakeFiscalYears`, y `ErrorResponsesTest` y `RuleViolationTest`, que usan `GAP` de ejemplo | Crítico (`backend/**/liv/tax/**`) |
| `ui/` | El componente nuevo `Select` en `liv-components` 0.5.0. Lo publica una persona antes del PR del frontend | Crítico por la ruta (`ui/**`) |
| `frontend/` | La mitigación del riesgo 6: un cambio previo, compatible con 1.0.0, que solo elige un año `CURRENT` o `NEXT` y nunca uno pasado.<br>La copia fijada de `tax.yaml` 1.1.0 y sus modelos generados (`creation-rejection-extensions.ts`, `fiscal-year-list.ts`), y `liv-components` 0.5.0.<br>Archivos que cambian:<br>• `create-fiscal-year-dialog`: el control, el orden, el año elegido al abrir y al releer, el estado sin año elegido, el foco, las etiquetas y la advertencia;<br>• `fiscal-year-rejections`: el mensaje de `PAST_LIMIT`; los motivos que ya no llegan caen en el mensaje general;<br>• `fiscal-years`: el estado vacío con el rango y el motivo `ALL_CREATED`.<br>También cambian sus pruebas, incluida axe | Normal por la ruta, dentro de una feature crítica |
| `e2e/` | `vigencias.spec.ts`:<br>• la preparación toma los dos primeros años de `creatableYears` (`[primera, segunda]`), y con la regla nueva serían otros años. Afecta a las pruebas de 007-04, 007-05, 007-09 a 007-12 y 007-19, y la de 007-09 espera exactamente dos vigencias;<br>• 007-03, 007-08, 007-06 (un año pasado y uno con hueco) y el teclado de 007-21 con la lista desplegable;<br>• la prueba de la administración B toma `[anioDeB, anioSiguiente]` de `creatableYears` y exige que el primero sea `CURRENT`, así que con el orden nuevo afecta a 007-05, 007-18 y 007-19;<br>• la prueba «sin años que crear» deja de cumplirse con dos vigencias, y cambian las capturas `vigencias-crear` y `vigencias-lista`.<br>Para 007-23 hay que crear unas 26 vigencias: si va en `e2e/`, en una administración aparte, y si no, solo en las pruebas del backend y del frontend | Normal |
| Spec, plan y tareas | La sección de la enmienda 4 en `plan.md`, las tareas nuevas en `tasks.md` y, en `docs/007-cierre`, las marcas reabiertas | Orquestador |

Orden propuesto: documentos, Figma, el cambio previo del frontend (con su PR de `e2e/`, si hace falta), contrato y backend, `ui/` publicada, frontend, `e2e/` y cierre.

## Decisiones de la H1 (resueltas)

_La persona que dirige el trabajo aprobó el 2026-11-12 las siete recomendaciones de la spec («Sí, todas»; `AGENTS.md` §4.3, excepción del equipo de una persona). Cada decisión conserva el motivo con el que se propuso._

| # | Tema | Decisión |
|---|---|---|
| 1 | Hasta dónde llega la habilitación | **Opción A.** La feature llega hasta *En preparación*, con la lista de preparación de solo lectura y «Habilitar liquidación» inhabilitado con los faltantes (CA‑14). La habilitación con doble firma, el estado *Lista* y el cierre van en la feature 1.5.1, que se hará cuando existan los puntos 2 a 9 de la lista: la habilitación no tiene uso hasta que exista el motor (punto 8, Fase 2), y la doble firma es más segura si se construye cuando se puede ejercer de verdad |
| 1a | La doble firma (para la 1.5.1) | Dos personas distintas, una con `ADMIN_SISTEMA` y otra con `APROBADOR_TABLAS`, en cualquier orden; la primera firma queda pendiente, quien la dio la puede retirar y se anula si la lista deja de estar completa (007-15 y 007-16) |
| 1b | El cierre y la reapertura (V‑04) | Se le preguntan al responsable funcional y quedan fuera de esta feature (véase «Preguntas abiertas») |
| 2 | Vigencias históricas y primera vigencia | La primera vigencia de una administración es el año en curso o el siguiente (007-08); después, solo el año siguiente al más reciente (007-06); un año anterior al más antiguo se rechaza como histórico (007-07). La carga histórica se decide con la feature 5.1, que resuelve antes el pendiente 5 del Doc. 01 §11 (la primera vigencia de cartera, `cartera_historica.vigencias_desde`, está en `PENDIENTE` en la configuración de Ipiales). El límite de la primera vigencia es una regla nueva: se agrega al Doc. 01 §5 en el PR de documentos de esta feature. **La reemplaza la enmienda 4 (H1 del 2026-11-16):** cualquier año de 2002 al año siguiente al año en curso, en cualquier orden, también para la primera vigencia |
| 3 | Alcance: nacional o por administración | **Opción C.** El año, con sus fechas, sigue nacional en `tax.fiscal_year`, y el estado y `assessments_enabled` pasan a una tabla nueva por administración, con RLS. El ajuste al modelo (ERD 2.3, Doc. 01 §5 v0.5 y la referencia de LQ‑P01 en el Doc. 02 §B.3) queda aprobado como alcance y va en su propio PR de documentos; el `plan.md` propone los nombres y las claves |
| 4 | Tipo de cambio | **Crítico:** toca `backend/**/liv/tax/**`, crea una migración con RLS, agrega permisos y roles, cambia el contrato OpenAPI y el modelo de datos, y crea pantallas. Lleva revisión adversarial y lente de riesgo de nivel alto, las cuatro lentes de `liv-revision-4r` antes de cada PR y la aprobación de los permisos nuevos en la H2 (`AGENTS.md` §4.5) |
| 5 | Pantallas y Figma | No hay diseño de la 1.4 en `03 Pantallas` y no aplica la excepción de reutilización (`AGENTS.md` §6.2): falta al menos una **tabla** en `liv-components`, cuyos 18 componentes son los de acceso y estructura de la feature 001. Se diseñan en Figma (sección «1.4 · Vigencias fiscales») las tres pantallas de abajo y los componentes que falten, y se aprueban antes de la H2; los componentes se implementan en `liv-components` en su propio cambio, con su versión publicada antes del PR del frontend. La tabla se diseña pensando en las consultas 1.6 (filtros, paginación del servidor), aunque la 007 use su forma básica |
| 6 | Fechas y configuración de jurisdicción | La vigencia la crea la opción 1.4, siempre del 1 de enero al 31 de diciembre. La configuración no crea la vigencia: al publicarla, la feature 1.5.9 comprueba que su sección `vigencia` coincide con la de la administración, como CJ10 hace con el catálogo, y ajusta la redacción del Doc. 03 §5.2, que hoy dice que esa sección «se materializa en» `tax.fiscal_year` |
| 7 | Exportación a Excel | Fuera de esta feature: llega con la primera feature de consultas 1.6, que trae la librería y su ADR (pendiente 6 del Doc. 01 §11) |

**Pantallas (decisión 5)**

| Pantalla | Contenido |
|---|---|
| 1.4 · Vigencias | Lista de vigencias con su estado y la acción «Crear vigencia» (solo `ADMIN_SISTEMA`); estado vacío. Con la enmienda 4, el estado vacío dice el rango de años (007-03) y la acción se inhabilita con su motivo cuando ya existen todas (007-23) |
| 1.4 · Crear vigencia | Diálogo con el año y las fechas que se derivan de él, y los rechazos de 007-05 a 007-08. Con la enmienda 4, el año se elige en una lista desplegable (007-22) y los rechazos son los de 007-05 y 007-07 |
| 1.4 · Detalle y preparación | Datos de la vigencia, la acción «Abrir preparación», la lista 1.5.1 con el estado de cada punto y el botón «Habilitar liquidación» inhabilitado con los faltantes (CA‑14) |

**Por qué el modelo de la decisión 3.** El ERD define `tax.fiscal_year` con `year` como clave y sin `administration_id`, pero la lista 1.5.1 es «por `fiscal_year` + `administration_id`» (Doc. 01 §6.1), toda operación se filtra por la administración del usuario (Doc. 01 §2, principio 7) y los roles valen dentro de una administración (`docs/arquitectura/02` §4.1). Con una vigencia nacional, que Ipiales habilitara la liquidación la habilitaría para todas, y el `ADMIN_SISTEMA` de una administración escribiría un dato compartido, que el RLS solo deja escribir al proceso de publicación (`docs/arquitectura/02` §5.1). Si `tax.fiscal_year` ganara `administration_id` (opción B), las tarifas nacionales (`tax.tax_rate` con `administration_id` nulo) no tendrían a qué vigencia apuntar.

## Preguntas abiertas

_Ninguna cambia un criterio de esta feature._

- **Cierre y reapertura de la vigencia (V‑04; decisión 1, detalle 1b).** El Doc. 01 §5 no dice quién cierra una vigencia ni cuándo, ni a qué estado vuelve una reapertura ni quién la autoriza. Se le pregunta al responsable funcional; la respuesta la necesita la feature 1.5.1, no esta.
- **Un año con saldos migrados y con vigencia en LIV (enmienda 4).** Con la enmienda 4, un año pasado puede tener a la vez saldos migrados por la cartera histórica (5.1) y su vigencia en LIV. El cobro doble ya está cubierto en un sentido: una sola obligación activa por vehículo, vigencia, administración y concepto (OB‑01, Doc. 04), y la migración rechaza una placa y vigencia que ya está en la cuenta corriente (CH‑02, Doc. 05). Falta decidir dos cosas. La primera, cuál de los dos orígenes prevalece para un año. La segunda, qué hace el motor cuando liquida un año que ya tiene una obligación migrada. Lo necesitan la feature 5.1 y la del motor, no esta.
