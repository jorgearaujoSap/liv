# 007 · Vigencias fiscales

**Estado:** aprobada (H1) — 2026-11-12, por la persona que dirige el trabajo (`AGENTS.md` §4.3), con las siete recomendaciones de la spec (véase «Decisiones de la H1»)
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `tax` (vigencias), con la matriz de permisos de `platform`, la auditoría de `audit`, el frontend y, si el diseño lo pide, la librería `liv-components` · **Menú:** 1.4 · **Tipo de cambio:** crítico (`AGENTS.md` §4.3; decisión 4 de la H1)
**Fuentes:** Doc. 01 v0.4 §2 (principios 3, 7 y 9), §4 y §4.1 (roles, matriz de acciones y permisos del menú), §5 (estados y reglas V‑01 a V‑04), §6.1 (lista de preparación 1.5.1 y «Habilitar liquidación»), §7.1 (consultas y exportación), §8 (auditoría), §9 (CA‑12 y CA‑14) y §11 (pendiente 5); ERD 2.2, esquema `tax` (`tax.fiscal_year` y sus relaciones) y relaciones externas de `valuation` y `ledger`; Doc. 02 v0.2 §B.2 y §B.3 (LQ‑P01); Doc. 03 v0.5 §5.2 (sección `vigencia`), §5.5 y §5.6; Doc. 05 v0.1 §3 (`cartera_historica.vigencias_desde`); `docs/arquitectura/01` §3.1 y §15; `docs/arquitectura/02` §4.1 a §4.4, §5.1 a §5.3 y §9; `AGENTS.md` §4.3, §4.5, §5.1 y §6.2

> Primera feature de la Fase 1 («Tablas base y configuración»). La vigencia es el prerrequisito de todo el Módulo 1: los catálogos de avalúos, las tarifas, el calendario y la configuración de jurisdicción se cargan **para una vigencia** (Doc. 01 §5 y §6.1).

## Qué hace

Abre la opción **1.4 Vigencias fiscales**, que hoy aparece en el menú como «Próximamente». Con ella, la administración tributaria lleva el ciclo de vida de cada año fiscal:

- **Consultar** las vigencias de su administración: el año, las fechas de inicio y fin, el estado (*Planeada*, *En preparación*, *Lista*, *Habilitada* o *Cerrada*) y si la liquidación está habilitada. La ven los cinco roles del Módulo 1 que hoy tienen la opción en el menú.
- **Crear** una vigencia nueva, en estado *Planeada*. Solo el `ADMIN_SISTEMA`. Las fechas son del 1 de enero al 31 de diciembre del año (V‑01), no se puede saltar un año (V‑02) y la primera vigencia es el año en curso o el siguiente (decisión 2).
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
- [ ] **007-03** — Dada una administración sin vigencias, cuando un usuario con permiso de consulta abre la opción 1.4, entonces ve un estado vacío que lo explica y, si es `ADMIN_SISTEMA`, la acción para crear la primera. _Prueba:_ pendiente

**B. Creación de la vigencia**

- [ ] **V‑01 · 007-04** — Dado un `ADMIN_SISTEMA`, cuando crea la vigencia de un año, entonces la vigencia queda en estado *Planeada*, sin la liquidación habilitada, con inicio el 1 de enero y fin el 31 de diciembre de ese año. Las fechas no se escriben a mano (decisión 6). _Prueba:_ pendiente
- [ ] **V‑01 · 007-05** — Dada una vigencia que ya existe en la administración, cuando un `ADMIN_SISTEMA` intenta crear otra del mismo año, entonces se rechaza con un mensaje que cita V‑01 y no cambia nada. Si dos personas crean el mismo año al mismo tiempo, se crea una sola vigencia y la otra recibe ese rechazo. _Prueba:_ pendiente
- [ ] **V‑02 · 007-06** — Dada una administración cuya vigencia más reciente es N, cuando un `ADMIN_SISTEMA` intenta crear un año posterior a N + 1, entonces se rechaza con un mensaje que cita V‑02 y dice que primero debe existir la vigencia N + 1. Crear N + 1 sí se permite. _Enmienda pendiente de la H2 (H2‑6 del `plan.md`): el límite de años futuros._ _Prueba:_ pendiente
- [ ] **V‑02 · 007-07** — Dada una administración con vigencias, cuando un `ADMIN_SISTEMA` intenta crear un año anterior a su vigencia más antigua (una vigencia histórica), entonces se rechaza con un mensaje que dice que la carga de vigencias históricas aún no está autorizada (pendiente 5 del Doc. 01 §11; decisión 2). _Prueba:_ pendiente
- [ ] **007-08** — Dada una administración sin vigencias, cuando un `ADMIN_SISTEMA` crea la primera, entonces solo se admite el año en curso o el siguiente, según el reloj del sistema; cualquier otro año se rechaza con un mensaje que lo explica (decisión 2). _Enmienda pendiente de la H2 (H2‑8 del `plan.md`): el código `V‑05`._ _Prueba:_ pendiente
- [ ] **007-09** — Dado un usuario sin rol `ADMIN_SISTEMA` (por ejemplo, `APROBADOR_TABLAS` o `CARGADOR_TABLAS`), cuando intenta crear una vigencia o abrir su preparación por la API, entonces recibe «Sin permiso» (403), no cambia nada y la interfaz no le muestra esas acciones (Doc. 01 §4, «Crear / abrir vigencia fiscal»). _Prueba:_ pendiente

**C. Preparación**

- [ ] **007-10** — Dada una vigencia *Planeada*, cuando un `ADMIN_SISTEMA` abre su preparación, entonces pasa a *En preparación*. Desde cualquier otro estado, la acción no se ofrece y la API la rechaza sin cambiar nada (Doc. 01 §5, diagrama de estados). _Enmienda pendiente de la H2 (H2‑8 del `plan.md`): el código `V‑06`._ _Prueba:_ pendiente
- [ ] **007-11** — Dada una vigencia, cuando un usuario con permiso de consulta abre su preparación, entonces ve los nueve puntos de la lista 1.5.1 (Doc. 01 §6.1), cada uno con su estado (cumplido o pendiente; los puntos 6 y 7 son opcionales) y, si está pendiente, por qué. Los puntos de opciones que todavía no existen (2 a 9) se muestran *pendientes* con el texto «Opción aún no disponible», nunca como cumplidos. El punto 1 se cumple si la vigencia está *En preparación* o en un estado posterior (el Doc. 01 §6.1 dice «en estado `EN_PREPARACION`»; los estados posteriores también lo cumplen, porque solo se llega a ellos desde ese). _Prueba:_ pendiente
- [ ] **V‑03 · CA‑14 · 007-12** — Dada una vigencia a la que le falta alguno de los puntos obligatorios de la lista (1 a 5, 8 y 9), cuando un usuario la ve, entonces el botón «Habilitar liquidación» está inhabilitado y la pantalla lista los puntos que faltan; ninguna llamada a la API puede poner `assessments_enabled` en verdadero. _Prueba:_ pendiente

**D. Aislamiento entre administraciones**

- [ ] **CA‑12 · 007-18** — Dadas las administraciones A y B, cuando un usuario de A consulta, crea o cambia el estado de vigencias, entonces solo ve y modifica las de A: las de B no aparecen, no se pueden abrir por su identificador ni se pueden inferir por un conteo o por el mensaje de un rechazo. La administración se toma de la sesión, nunca de un parámetro del navegador (`docs/arquitectura/02` §5.1 y §5.3; decisión 3). _Prueba:_ pendiente
- [ ] **007-19** — Dada la vigencia 2026 creada en la administración A, cuando un `ADMIN_SISTEMA` de B crea su vigencia 2026, entonces se crea sin conflicto, y abrir la preparación de la vigencia de una administración no cambia la de la otra (decisión 3). _Prueba:_ pendiente

**E. Auditoría**

- [ ] **007-20** — Dada cualquier creación o cambio de estado de una vigencia, entonces queda un evento en `audit.audit_event` con la entidad `tax.fiscal_year`, el año, el usuario, la administración, el estado anterior y el nuevo, el instante y el `correlation_id` de la operación (Doc. 01 §8). _Enmienda pendiente de la H2 (H2‑7 del `plan.md`): la entidad `tax.administration_fiscal_year`._ _Prueba:_ pendiente

**F. Calidad**

- [ ] **007-21** — Dadas las pantallas de la opción 1.4, entonces están construidas solo con componentes de `liv-components`, coinciden con su diseño aprobado en Figma (decisión 5), pasan axe sin violaciones de WCAG 2.1 AA y tienen sus pruebas de navegador en `e2e/` en verde contra el entorno local (ADR 0030). _Prueba:_ pendiente

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
- **La carga de vigencias históricas** para la cartera (pendiente 5 del Doc. 01 §11 y `cartera_historica.vigencias_desde`, en `PENDIENTE` en la configuración de Ipiales): se decide con la apertura de cartera histórica (5.1, Fase 5).
- **Fechas de la vigencia distintas del año calendario** («parametrizable si la norma lo exige», V‑01): no se conoce ninguna norma que lo exija (decisión 6).
- **Habilitar la liquidación, la doble firma, el estado *Lista* y el cierre** (decisión 1): van en la feature 1.5.1 (véase «Trasladado a la feature 1.5.1»).
- **Liquidar** con la vigencia habilitada: es la Fase 2 (LQ‑P01).
- **La administración de usuarios y roles** (menú 1.2): los usuarios de prueba se dan de alta en Keycloak, como en la feature 001.

## Decisiones de la H1 (resueltas)

_La persona que dirige el trabajo aprobó el 2026-11-12 las siete recomendaciones de la spec («Sí, todas»; `AGENTS.md` §4.3, excepción del equipo de una persona). Cada decisión conserva el motivo con el que se propuso._

| # | Tema | Decisión |
|---|---|---|
| 1 | Hasta dónde llega la habilitación | **Opción A.** La feature llega hasta *En preparación*, con la lista de preparación de solo lectura y «Habilitar liquidación» inhabilitado con los faltantes (CA‑14). La habilitación con doble firma, el estado *Lista* y el cierre van en la feature 1.5.1, que se hará cuando existan los puntos 2 a 9 de la lista: la habilitación no tiene uso hasta que exista el motor (punto 8, Fase 2), y la doble firma es más segura si se construye cuando se puede ejercer de verdad |
| 1a | La doble firma (para la 1.5.1) | Dos personas distintas, una con `ADMIN_SISTEMA` y otra con `APROBADOR_TABLAS`, en cualquier orden; la primera firma queda pendiente, quien la dio la puede retirar y se anula si la lista deja de estar completa (007-15 y 007-16) |
| 1b | El cierre y la reapertura (V‑04) | Se le preguntan al responsable funcional y quedan fuera de esta feature (véase «Preguntas abiertas») |
| 2 | Vigencias históricas y primera vigencia | La primera vigencia de una administración es el año en curso o el siguiente (007-08); después, solo el año siguiente al más reciente (007-06); un año anterior al más antiguo se rechaza como histórico (007-07). La carga histórica se decide con la feature 5.1, que resuelve antes el pendiente 5 del Doc. 01 §11 (la primera vigencia de cartera, `cartera_historica.vigencias_desde`, está en `PENDIENTE` en la configuración de Ipiales). El límite de la primera vigencia es una regla nueva: se agrega al Doc. 01 §5 en el PR de documentos de esta feature |
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
