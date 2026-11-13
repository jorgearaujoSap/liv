# ADR-0035 · Dependencias entre módulos y errores de negocio de la feature 007: `audit → tax`, `tax → platform`, `shared` como módulo y la zona tributaria

- **Estado:** Aceptado
- **Fecha:** 2026-11-13
- **Decisores:** responsable del proyecto, que aprobó la decisión en la H2 de la feature 007 (H2‑5 de su `plan.md`) el 2026-11-12 y la aceptó formalmente el 2026-11-13, después de integrar el PR que lo agregó como «Propuesto» (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** `spec/features/007-vigencias-fiscales/plan.md` (paso 2, H2‑5 y «Decisiones»); `docs/arquitectura/01` §3.1 y §3.2; `docs/arquitectura/02` §8 y §9; `docs/funcional/01` §6.1; ADR 0002, 0008, 0016, 0021 y 0025

## Contexto

La feature 007 (vigencias fiscales, opción 1.4) es la primera feature de negocio del módulo `tax`. Su plan necesita cinco cosas que el doc. 01 §3.2 todavía no permite («una dependencia nueva entre módulos exige un ADR»):

- **La seguridad.** `tax` debe usar `@RequiresPermission`, `CurrentUser` y el contexto de la administración, que son de `platform`, y la figura del §3.2 no tiene la flecha `tax → platform`.
- **La auditoría.** Debe seguir siendo automática (doc. 02 §9), con el patrón del ADR 0025: el módulo de negocio publica un evento en su paquete raíz y `audit` lo escucha. Eso exige que `audit` dependa de `tax`.
- **Los errores de negocio.** El dominio de `tax` es Java puro y debe rechazar con el código de la regla (`V-01`, `V-02`…). Esos rechazos tienen que llegar al cliente como Problem Details con 409 o 422, y quien los arma es `ProblemDetailsHandler`, en `platform`. Como `tax → platform` es una flecha nueva, que `platform` conozca las excepciones de `tax` cerraría un ciclo.
- **El error «no existe».** Una vigencia que no existe en la administración no tiene hoy un error propio y caería en el manejador genérico como 500: el 404 `SYS-404` que hay solo sale de los errores de Spring MVC.
- **El «año en curso».** El `Clock` de `platform` (`ClockConfiguration`) es `Clock.systemUTC()` y lo usan las sesiones y la auditoría. Entre las 19:00 del 31 de diciembre y la medianoche en Bogotá, la fecha UTC ya es del año siguiente.

El doc. 01 §3.1 ya pone en `shared` los «errores con código de regla», pero hoy `shared` solo tiene su `package-info` y ningún módulo depende de él: `ModularityTest` exige, por ejemplo, que `platform` no dependa de nadie. Además, la nota de `LivApplication` (hoy en el código) dice que, cuando `shared` y `platform` tengan tipos, se declaren en `sharedModules`. Este ADR decide otra cosa (opción 7), así que esa nota hay que corregirla.

Por último, la lista de preparación de la vigencia (Doc. funcional 01 §6.1) tiene nueve puntos. Cinco los verifica `tax` con sus propios datos (1 y 4 a 7), tres leen datos de otros módulos (2 de `documents`, 3 de `valuation` y 9 de `jurisdiction`) y el 8 simula una liquidación. El plan define un puerto por punto en el dominio de `tax`, y hay que decidir dónde viven sus implementaciones sin crear ciclos.

## Opciones consideradas

**Los errores de negocio**

1. **`ProblemDetailsHandler` conoce las excepciones de cada módulo.** Cada módulo nuevo obliga a editar `platform` y a depender de él: con `tax → platform` ya existente, forma un ciclo.
2. **El dominio lanza excepciones de `platform`.** Rompe la regla del dominio puro (`backend/AGENTS.md`, «Hexagonal») y ata las reglas al módulo técnico.
3. **Una excepción con código de regla y otra para «no existe», en `shared`; `platform` las traduce.** El dominio no sabe de HTTP, `platform` no conoce a ningún módulo de negocio y no hay ciclos. Es lo que el doc. 01 §3.1 ya prevé para `shared`.

**La auditoría**

4. **`tax` llama a `audit`.** Crea `tax → audit` y obliga a cada caso de uso a acordarse de auditar, contra el doc. 02 §9. Es el mismo motivo por el que el ADR 0025 descartó su opción 3.
5. **`tax` publica eventos y `audit` los escucha (patrón del ADR 0025).** Cuesta una flecha, `audit → tax`, y nadie depende de `audit`. Los eventos se guardan en la misma transacción (ADR 0008).

**`shared`**

6. **`shared` en `sharedModules` de Spring Modulith** (lo que dice hoy la nota de `LivApplication`). Todo módulo puede usarlo sin declarar una flecha, así que la regla de §3.2 («las flechas de la figura son las únicas permitidas») deja de valer para `shared`.
7. **`shared` como un módulo normal**, con sus flechas explícitas. Cada módulo que lo use lo declara en la figura y en `ModularityTest`.

**La zona tributaria**

8. **Cambiar el `Clock` de `platform` a la zona de Bogotá.** Cambia el reloj de todo lo que ya lo usa (sesiones, auditoría) para resolver un solo caso.
9. **Escribir `America/Bogota` en el código de `tax`.** Un valor fijo que solo cambiaría con una versión nueva del programa.
10. **Guardar la zona en la configuración de jurisdicción o en la administración.** La vigencia existe antes de que haya una configuración publicada (el punto 9 de la lista de preparación la exige), así que no se puede leer de allí cuando se crea.
11. **Una propiedad de configuración del despliegue con la zona**, junto a un `Clock` que sigue en UTC.

**La lista de preparación**

12. **Cada módulo dueño del dato implementa el puerto de `tax`** (por ejemplo, `valuation` implementa la verificación de catálogos). Obliga a `valuation → tax`, y `tax → valuation` ya está en la figura: es un ciclo.
13. **Las implementaciones viven en `tax/infrastructure` y consultan las fachadas** del paquete raíz de cada módulo. Solo agrega flechas de `tax` hacia los módulos que consulta.

## Decisión

Opciones 3 (errores), 5 (auditoría), 7 (`shared`), 11 (zona) y 13 (lista de preparación).

**Flechas.**

| Flecha | Motivo | Estado |
|---|---|---|
| `tax → platform` | `@RequiresPermission`, `CurrentUser` y el contexto de la administración | **Nueva** |
| `audit → tax` | `audit` escucha `FiscalYearCreated` y `FiscalYearStatusChanged`, publicados en el paquete raíz de `tax`, como `audit → core` en el ADR 0025 | **Nueva** |
| `tax → shared` | Usa `RuleViolation` y `NotFound` | **Nueva** |
| `platform → shared` | `ProblemDetailsHandler` traduce `RuleViolation` y `NotFound` | Ya en la figura; **nueva en `ModularityTest`**, que hoy exige que `platform` no dependa de nadie |

- **Ningún módulo depende de `audit`**, como en el ADR 0025.
- **Los eventos de `tax` llevan solo tipos del JDK** (el estado, por ejemplo, como texto), como `core.SessionStarted`. Así `audit` no usa tipos internos de `tax` y no necesita una flecha hacia `shared`.
- **`shared` es un módulo normal** de Spring Modulith, no `sharedModules`. Sus tipos públicos van en su paquete raíz, como en los demás módulos, y sigue siendo Java puro.
- Este ADR aprueba solo las flechas hacia `shared` de `platform` y de `tax`. Las de otro módulo (por ejemplo, `ledger` con `Dinero`, ADR 0016) las trae la feature que las necesite, con la regla de §3.2: figura, `ModularityTest` y ADR.

**El error de negocio.**

- **`shared.RuleViolation`:** una excepción sin marcar (`RuntimeException`), para que un rechazo deshaga la transacción sin que Spring intente confirmarla. Lleva el **código de la regla** (`V-01`, `V-05`…), un **tipo** y unos **detalles**.
  - **Tipo:** `CONFLICT` cuando el estado actual de la vigencia sobre la que se actúa (si existe, o en qué estado está) impide la acción (409), y `UNPROCESSABLE` cuando la petición es válida pero la regla la rechaza (422). El OpenAPI fija el tipo de cada código: en la 007, `V-01` y `V-06` son 409, y `V-02` y `V-05` son 422.
  - **Detalles** (por ejemplo, `reason`): van como extensiones del Problem Details. Solo valores escalares (texto, número o booleano); no pueden usar las claves del propio Problem Details (`type`, `title`, `status`, `detail`, `instance`, `code`), y la lista de extensiones de cada código está en el contrato. Una prueba comprueba la restricción.
  - **Formato:** en el código y en la respuesta, el código de la regla lleva el guion ASCII (`V-01`).
- **`shared.NotFound`:** también sin marcar, para lo que no existe en la administración. «No existe» no es una regla: no lleva código de regla. Se traduce a 404 con el código `SYS-404` que ya existe y sin detalle, como el 404 de Spring MVC, y una vigencia de otra administración da exactamente el mismo cuerpo que una que no existe.
- **Traducción:** `ProblemDetailsHandler`, en `platform`, hace las dos conversiones. El dominio de `tax` las lanza sin saber de HTTP, y ninguna excepción de un módulo de negocio llega a `platform`.
- **Sin cambios:** `RequestRefusedException`, `AuthenticationRejectedException` y los demás errores de `platform` siguen donde están.

**La zona tributaria.**

- El `Clock` de `platform` **sigue en UTC**: no cambia nada de lo que ya lo usa.
- La propiedad **`liv.business-zone`** da la zona con la que `tax` calcula «el año en curso», siempre con `LocalDate.now(clock.withZone(zona))`: nunca `now(ZoneId)`, que lee el reloj del sistema.
  - `application.yml` trae `America/Bogota`, pero el código **no tiene valor por omisión**: si la propiedad falta o no es una zona válida, la aplicación no arranca.
  - No es una tarifa, un rango ni una fecha tributaria: es la zona en la que se cuenta el día, un dato del despliegue y no de la jurisdicción.
- El cálculo vive en un solo componente de `tax/application`, `BusinessYear`, para que la lista y la creación nunca discrepen.
- Si otro módulo necesita la misma fecha de negocio, la feature que lo necesite decide dónde vive; si requiere una flecha nueva, con su ADR.

**La lista de preparación.**

- Las verificaciones de sus puntos, que implementan el puerto `PreparationItemCheck` del dominio de `tax`, **vivirán en `tax/infrastructure`** y consultarán las **fachadas del paquete raíz** de cada módulo.
- `tax → valuation` (punto 3) y `tax → jurisdiction` (punto 9) ya están en la figura.
- **`tax → documents`** (punto 2) será una flecha nueva con **su propio ADR**, en la feature de la opción 1.3. Lo mismo vale para cualquier otro punto que necesite una flecha que `tax` todavía no tiene.

**Cómo se verifica.** `ModularityTest` admite cada flecha junto con el código que la crea, en la implementación de la feature 007 (pasos 5 a 7 del plan; `tasks.md` fija en qué PR va cada comprobación). Al terminar:

- `platform` depende solo de `shared`;
- `tax` depende de `platform` y de `shared`;
- `audit` depende de `core`, `platform` y `tax`;
- nadie depende de `audit`;
- una comprobación sobre todos los módulos: los que dependen de `shared` son exactamente `platform` y `tax`.

## Consecuencias

- **Positivas:**
  - grafo de módulos acíclico y verificado, con `tax` ya usando la seguridad y la auditoría automática;
  - el dominio sigue siendo Java puro y `platform` sigue sin conocer a los módulos de negocio, así que el patrón sirve para `ledger`, `valuation` y los módulos que vienen;
  - un rechazo da siempre el mismo Problem Details (409, 422 o 404) y nunca un 500;
  - el «año en curso» no cambia el reloj de las sesiones ni de la auditoría, y se prueba con un `Clock` fijo a ambos lados de la medianoche del 31 de diciembre en Bogotá.
- **Negativas o costos:**
  - tres flechas nuevas en la figura y una más en `ModularityTest`;
  - `shared` deja de ser un paquete que nadie usa: cada módulo que lo necesite pide su flecha, con ADR, y los módulos que ya describen sus tipos como «usados por todos» (ADR 0016, doc. 01 §3.1) tendrán que pasar por esa regla;
  - `RuleViolation` es una excepción sin marcar: nada obliga a un caso de uso a atraparla, y un tipo mal elegido cambia el estado HTTP; lo cubren las pruebas de la API;
  - una sola zona para todo el sistema: sirve para Colombia, y una jurisdicción con otra zona exigiría replantear la propiedad;
  - cada punto de la lista de preparación hacia un módulo al que `tax` todavía no tiene flecha trae su propio ADR.
- **Qué cambia en el repositorio:**
  - la tabla del doc. 00 §6, con la fila 0035;
  - la figura del doc. 01 §3.2, con su nota y su versión (el glosario lo actualiza el orquestador de la feature);
  - en el *backend* (pasos 5 a 7 de la feature 007): `shared.RuleViolation` y `shared.NotFound`, la traducción en `ProblemDetailsHandler`, la propiedad `liv.business-zone` con `BusinessYear`, las comprobaciones de `ModularityTest`, y la corrección de la nota de `LivApplication` y de la `package-info` de `shared`, en el primer PR del *backend* que use `shared`;
  - queda para quien actualice el doc. 01 §3.1: ubica el reloj inyectable en `shared`, pero está en `platform` y este ADR lo deja ahí;
  - las reglas de trabajo, la constitución y los ADR 0002 y 0025 no cambian.
