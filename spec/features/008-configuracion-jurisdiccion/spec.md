# 008 · Configuración de jurisdicción

**Estado:** propuesta
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `jurisdiction` (carga, validación, aprobación y publicación), con el punto 9 de la lista de preparación de `tax`, los permisos de `platform`, la auditoría de `audit`, el frontend, la librería `liv-components` y, en la raíz, el validador de los anexos (`tools/`) y su CI (`.github/`) · **Menú:** 1.5.9 · **Tipo de cambio:** crítico (`AGENTS.md` §4.3: configuración de jurisdicción, `backend/**/liv/jurisdiction/**`, migración con RLS, contrato OpenAPI nuevo, permisos de acción, CI y `ui/**`)
**Fuentes:** Doc. 03 v0.5 §1 (decisión 1.1), §5 (§5.1 a §5.6), §7, §8 (pendiente 9) y §9 (§9.2 resultado del validador en Ipiales, §9.5 a §9.7); Doc. 01 v0.6 §2 (principios 1, 3, 5, 7, 8 y 9), §3 (menú 1.5.9), §4 y §4.1 (roles, matriz de acciones y permiso `jurisdiction.config.read`), §5 (estados de la vigencia, V‑04), §6.1 (punto 9), §6.7, §8 (auditoría), §9 (CA‑05, CA‑12) y §11 (pendiente 8); Doc. 05 v0.2 §5.1 y CA‑5.13; ERD 2.4 (relaciones externas de `ledger` hacia `core.jurisdiction_config`, nota de `tax.fiscal_year` y checklist previo a Flyway); `docs/arquitectura/01` §3.1 (nota 1), §3.2, §5.3 (IN‑11) y §6 (motor de ingesta); `docs/arquitectura/02` §4.2, §4.3, §5, §7 y §9; feature 004 (spec, decisiones de la H1 y del cierre, hallazgos 1 a 6; plan, pasos 4 y 5); feature 005 (decisión 6 de la H3 de R3a); feature 007 (decisión 6 de la H1, 007-11 y el puerto `PreparationItemCheck`); ADR 0003, 0032 y 0034; `AGENTS.md` §4.3 a §4.6, §5.1 y §5.2; anexos `config_jurisdiccion_plantilla_2026.yaml` y `config_jurisdiccion_52356_2026.yaml`

> Segunda feature de la Fase 1. La configuración de jurisdicción es la base del motor de liquidación: el motor no tiene reglas en el código y lo lee todo de la configuración publicada (`AGENTS.md` §5.1). Completa el punto 9 de la lista de preparación de la vigencia, que la feature 007 dejó con «Opción aún no disponible».
>
> Todo lo que esta spec dice de las reglas es una **propuesta de diseño pendiente de revisión jurídica** (`AGENTS.md` §8).

## Qué hace

Abre la opción **1.5.9 Configuración de jurisdicción**, que hoy aparece en el menú como «Próximamente». Con ella, la administración tributaria lleva el ciclo de vida del archivo YAML que reúne las reglas de su jurisdicción para una vigencia (Doc. 03 §5.1):

- **Cargar** el YAML de una vigencia de su administración. El sistema guarda el archivo original con su SHA‑256 y lo carga de forma segura, con las reglas de la feature 004 (sin alias, etiquetas ni claves repetidas).
- **Validar** el archivo con las reglas CJ01 a CJ16 del Doc. 03 §5.4 y con dos reglas nuevas que propone esta spec (pregunta 3). La validación corre sola al cargar. Da, por cada hallazgo, el código de la regla, la ruta del campo y el motivo, separados en errores y advertencias, y la lista de reglas `POR_CONFIRMAR` que el aprobador debe revisar. Un archivo con errores queda *Con errores* y no sigue; uno sin errores queda *Validada*.
- **Comparar** la versión nueva con la publicada (CJ11): qué cambió, con el valor anterior y el nuevo, para que el aprobador lo revise.
- **Aprobar o rechazar.** Una persona distinta de la que cargó el archivo lo revisa, marca como revisada cada regla `POR_CONFIRMAR` y acepta las advertencias (CJ02, Doc. 03 §5.3).
- **Publicar** la versión aprobada. La publicación es atómica: la versión nueva queda *Publicada* e inmutable, la anterior queda *Reemplazada*, y se guardan la versión del esquema y la del validador con las que se validó (Doc. 03 §8, pendiente 9). Una corrección es siempre una versión nueva.
- **Consultar** las versiones de cada vigencia, su estado, quién las cargó y aprobó, su resultado de validación, y **descargar** el archivo original.
- **Cumplir el punto 9** de la lista de preparación de la vigencia (opción 1.4): «Configuración de jurisdicción publicada» queda cumplido cuando la vigencia tiene una versión publicada.

Además, en la raíz del repositorio, el **validador de los anexos** comprueba en cada PR que los YAML de `docs/funcional/anexos/` son válidos contra el esquema publicado por el backend (regla IN‑11). Es el criterio 004‑13, que la feature 004 dejó para esta.

Cada administración ve y maneja solo sus configuraciones. Cada acción queda en la auditoría inmutable.

**Pantallas** (se diseñan en Figma después de la H1, en otra sesión; pregunta 15):

| Pantalla | Contenido |
|---|---|
| 1.5.9 · Configuraciones | Vigencias de la administración con su versión publicada y la versión en curso, y la acción «Cargar configuración» |
| 1.5.9 · Cargar configuración | Diálogo para elegir la vigencia y el archivo YAML |
| 1.5.9 · Detalle de la versión | Datos de la versión, el resultado de la validación (errores, advertencias y reglas `POR_CONFIRMAR`), la comparación con la versión publicada (CJ11), la descarga del original y las acciones que correspondan al estado y al rol |
| 1.5.9 · Aprobar, rechazar y publicar | Diálogos: la aprobación con la marca de cada regla `POR_CONFIRMAR` y la aceptación de las advertencias; el rechazo con su motivo; la confirmación de la publicación |

## Por qué

- El motor de liquidación (Fase 2) no puede empezar sin una configuración publicada que leer: tarifas, calendario, redondeo, competencia y distribución están en ella (`AGENTS.md` §5.1). Cada liquidación guarda el `jurisdiction_config_id` con el que se calculó (Doc. 03 §5.6), y `ledger.tax_obligation` ya lo referencia (ERD 2.4).
- Hoy el validador CJ01 a CJ16 solo existe como evaluación a mano del Doc. 03 §9.2. Llevarlo a código confirma ese resultado y protege cada publicación futura.
- La habilitación de la liquidación exige el punto 9 de la lista de preparación (Doc. 01 §6.1). Sin esta feature, ninguna vigencia puede habilitarse.
- Desde la feature 004, un PR que deje inválido uno de los anexos se puede integrar sin aviso (decisión del cierre de la 004). El validador de la raíz cierra ese hueco.

## Criterios de aceptación

_Cada criterio se comprueba con sí/no y tiene su número propio `008-xx`. Si verifica una regla o un criterio de un documento funcional, ese código va delante (`CJxx`, `CA‑x.xx`, `V‑xx`). Los estados se escriben con su etiqueta en la interfaz; sus códigos son los del Doc. 03 §5.3, más los dos que propone la pregunta 5 (*Cargada* = `CARGADA`, *Con errores* = `CON_ERRORES`, *Validada* = `VALIDADA`, *Aprobada* = `APROBADA`, *Publicada* = `PUBLICADA`, *Reemplazada* = `REEMPLAZADA`, *Rechazada* = `RECHAZADA`, *Anulada* = `ANULADA`). Los roles de cada acción son los que propone la pregunta 4. Los criterios que dependen de una pregunta abierta lo dicen._

**A. Consulta**

- [ ] **008-01** — Dado un usuario con permiso de consulta (`jurisdiction.config.read`: `ADMIN_SISTEMA`, `CARGADOR_TABLAS`, `APROBADOR_TABLAS`, `JURIDICO` o `AUDITOR`, Doc. 01 §4.1), cuando abre la opción 1.5.9, entonces ve las vigencias de su administración, de la más reciente a la más antigua, cada una con su versión publicada (si la hay) y su versión en curso (si la hay); la opción deja de mostrarse como «Próximamente». _Prueba:_ pendiente
- [ ] **008-02** — Dada una vigencia, cuando ese usuario abre su historial, entonces ve todas sus versiones con el número de versión, el estado, el SHA‑256, quién cargó y cuándo, quién aprobó y cuándo, y cuándo se publicó o se reemplazó. Al abrir una versión ve su resultado de validación completo (008-28) y su comparación con la versión publicada (CJ11, 008-20). _Prueba:_ pendiente
- [ ] **008-03** — Dado un usuario sin ese permiso (por ejemplo, `CONSULTA` o `LIQUIDADOR`), cuando abre la opción 1.5.9 por su URL o llama a su API, entonces recibe «Sin permiso» (403) (Doc. 01 §4.1). _Prueba:_ pendiente
- [ ] **008-04** — Dado un usuario con permiso de descarga (pregunta 4), cuando descarga el archivo original de una versión, entonces recibe exactamente los bytes cargados, con el mismo SHA‑256, y la descarga queda en la auditoría (Doc. 01 §8, «Descargar original»; `docs/arquitectura/02` §7). Sin ese permiso, recibe 403. _Prueba:_ pendiente

**B. Carga**

- [ ] **008-05** — Dado un usuario con permiso de carga (pregunta 4) y una vigencia de su administración en un estado que admite la carga (pregunta 7), cuando carga un YAML, entonces se crea una versión *Cargada* con el archivo original guardado sin cambios, su SHA‑256 calculado al recibirlo, el usuario y el instante, y se valida enseguida (008-28). _Prueba:_ pendiente
- [ ] **V‑04 · 008-06** — Dada una vigencia que no existe en la administración, o que está en un estado que no admite la carga (pregunta 7; por ejemplo, *Planeada* o *Cerrada*), cuando se intenta cargar una configuración para ella, entonces se rechaza con el motivo y no se crea nada. _Prueba:_ pendiente
- [ ] **008-07** — Dado un archivo que no es texto UTF‑8, que supera el tamaño máximo de la carga (pregunta 8) o que el cargador seguro rechaza (clave repetida, ancla o alias, etiqueta, escalar ambiguo; criterio 004‑10 y casos de carga de todos los archivos `*.casos.json`, ADR 0034), cuando se carga, entonces se rechaza antes de interpretarlo, con el motivo, y no se valida contra el esquema. _Prueba:_ pendiente
- [ ] **008-08** — Dado un archivo con el mismo SHA‑256 que la versión publicada o que la versión en curso de la misma vigencia y administración, cuando se carga, entonces se rechaza y dice con qué versión coincide (Doc. 01 §2, principio 5, como `F003` para los catálogos). El mismo archivo en otra administración no choca. El SHA‑256 es el de los bytes cargados: el mismo contenido con otro fin de línea (CRLF en lugar de LF) es otro archivo. _Prueba:_ pendiente
- [ ] **008-09** — Dada una vigencia que ya tiene una versión en curso (*Cargada*, *Validada* o *Aprobada*), cuando se carga otra, entonces se rechaza hasta que la versión en curso se publique, se rechace o se anule (pregunta 6). Si dos personas cargan a la vez, solo una carga queda. _Prueba:_ pendiente

**C. Validación** _(reglas del Doc. 03 §5.4; cada hallazgo da el código de la regla, la ruta del campo y el motivo; el validador no contiene valores tributarios: compara contra lo que dice el propio archivo, `AGENTS.md` §4.5)_

- [ ] **CJ01 · 008-10** — Dado un YAML, cuando se valida, entonces se valida contra el esquema de la versión que declara en `esquema_version` (hoy 1.0 o 1.1), nunca contra otra. Una versión que no existe es un error de CJ01 y no se valida el resto. Si CJ01 da errores, las reglas CJ02 en adelante no se evalúan y el resultado lo dice. El validador supera los casos de validación de cada archivo `*.casos.json` con el registro de versiones que fija el ADR 0034. _Prueba:_ pendiente
- [ ] **CJ02 · 008-11** — Dada una regla obligatoria (pregunta 9) con `estado: PENDIENTE`, cuando se valida, entonces es un error de CJ02. `cartera.intereses_mora` y `cartera.prescripcion` son obligatorias solo si alguna modalidad activa liquida. `cartera.prescripcion.competente` no cuenta para CJ02. Un campo numérico de `cartera` (fracción o año) en `PENDIENTE` cuya regla más cercana con `estado` no está en `PENDIENTE` es un error. CJ02 se evalúa sobre el `estado` de la regla, no sobre cada valor que contiene (interpretación confirmada el 2026-11-10, `AGENTS.md` §5.2). _Prueba:_ pendiente
- [ ] **CJ02 · 008-12** — Dado un archivo sin errores, cuando se valida, entonces cada valor `PENDIENTE` que no dio un error es una **advertencia** con su ruta, y cada regla `POR_CONFIRMAR` queda en la lista de reglas por revisar del aprobador, con su ruta y su norma (Doc. 03 §9.2). _Prueba:_ pendiente
- [ ] **CJ03 · 008-13** — Dada una `norma` citada en cualquier parte del archivo que no existe en la sección `normas`, cuando se valida, entonces es un error con la ruta de la cita. _Prueba:_ pendiente
- [ ] **CJ04 · 008-14** — Dados los rangos de tarifa de una categoría que se usa, cuando tienen un hueco, un solapamiento o un último rango que no es abierto (`hasta_inclusivo: null`), entonces es un error; el inferior es exclusivo y el superior inclusivo. Una categoría que no se usa y no tiene rangos (`PUBLICO` en Ipiales) es una advertencia (interpretación por confirmar, pregunta 10). _Prueba:_ pendiente
- [ ] **CJ05 · 008-15** — Dada la categoría de eléctricos, cuando una de sus tarifas supera su `tarifa_maxima`, o no declara `tarifa_maxima` (pregunta 11), entonces es un error (Ley 1964/2019 art. 3). _Prueba:_ pendiente
- [ ] **CJ06 · 008-16** — Dada la distribución de cada modalidad que liquida, activa o no, cuando sus porcentajes no suman exactamente 1 (100 %), entonces es un error. La modalidad `PARTICIPACION_20`, que no liquida, no se suma (interpretación por confirmar, pregunta 10). _Prueba:_ pendiente
- [ ] **CJ08 · 008-17** — Dado un ajuste `SAN_ANDRES` en `base_gravable.ajustes`, cuando el código DANE de la jurisdicción no es 88, entonces es un error. _Prueba:_ pendiente
- [ ] **CJ09 · CJ10 · 008-18** — Dada una vigencia sin catálogo de avalúos publicado (la opción 1.5.4 aún no existe), cuando se valida, entonces CJ09 y CJ10 se informan como «no evaluables: no hay catálogo publicado», sin bloquear la publicación, y la comprobación queda para la feature del catálogo, que la hace contra la configuración publicada (F009 y F010; pregunta 2). _Prueba:_ pendiente
- [ ] **CJ12 · 008-19** — Dadas las modalidades de `competencia`, cuando no hay ninguna activa, cuando están activas a la vez `CONVENIO_DEPTO` y `PARTICIPACION_20`, cuando `UEDF_INTERNACION` está activa sin `jurisdiccion.zona_frontera: true`, cuando `CONVENIO_DEPTO` está activa sin número, fecha de inicio o fecha de fin del convenio, o cuando `ADMINISTRACION_DIRECTA` está activa en una jurisdicción que no es departamento ni distrito capital (pregunta 12) o junto con `CONVENIO_DEPTO` o `PARTICIPACION_20`, entonces es un error. Las 15 variantes del Doc. 03 §9.5 dan el resultado de su tabla. _Prueba:_ pendiente
- [ ] **CJ11 · 008-20** — Dada una vigencia con una versión publicada, cuando se valida una versión nueva, entonces el resultado muestra cada valor que cambió, se agregó o se quitó, con su ruta, el valor anterior y el nuevo, agrupado por sección. Sin versión publicada, dice «Primera versión: no hay nada que comparar». CJ11 informa, no da errores. _Prueba:_ pendiente
- [ ] **CJ13 · 008-21** — Dado un grupo del calendario, cuando no cumple `fecha_inicio_pago ≤ fecha_limite_descuento ≤ fecha_limite_pago_sin_intereses`, entonces es un error; cuando el límite sin intereses es posterior al 31 de diciembre de la vigencia, es una advertencia. Las fechas son obligatorias solo si alguna modalidad activa liquida. _Prueba:_ pendiente
- [ ] **CJ14 · 008-22** — Dados los plazos de los vehículos nuevos, cuando un plazo por días no trae los días, una fecha fija no trae la fecha, o los días del descuento superan los del plazo sin intereses, entonces es un error. _Prueba:_ pendiente
- [ ] **CJ15 · 008-23** — Dada cualquier publicación nueva, **declare la `esquema_version` que declare** (también `"1.0"`), cuando se valida, entonces se aplican todas las condiciones de CJ15 del Doc. 03 §5.4: si alguna modalidad activa liquida y falta `cartera.intereses_mora`, es un error; `base` incluye `TAX` y no `INTEREST`; `sobre_sanciones` es coherente con `base`; `bloquear_si_falta_tasa` es `true`; con un método de `redondeo_causacion` distinto de `SIN_REDONDEO`, el `multiplo` es mayor que 0; e `inicio` coincide con `calendario.inicio_mora`. _Prueba:_ pendiente
- [ ] **CJ16 · 008-24** — Dada cualquier publicación nueva, con la misma regla de versiones que CJ15, cuando se valida, entonces se aplican todas las condiciones de CJ16 del Doc. 03 §5.4 (sanciones, prescripción, antigüedad y formato bancario), con `sistema_determinacion` en `PENDIENTE` como **error**, y solo dos advertencias: `prescripcion.competente` en `PENDIENTE` y una configuración con `recaudo.archivo_banco.formato` y también `formato_preferido`. _Prueba:_ pendiente
- [ ] **CJ17 (propuesta) · 008-25** — Dado un archivo, cuando su `jurisdiccion.codigo_dane` o su `jurisdiccion.tipo` no coinciden con la entidad territorial de la administración del usuario, cuando su `vigencia.anio` no es el año de la vigencia elegida, cuando `vigencia.inicio` y `vigencia.fin` no son las fechas de esa vigencia, o cuando `vigencia.version_config` no es el número de la última versión publicada más uno (1 si no hay ninguna), entonces es un error (decisión 6 de la H1 de la feature 007; pregunta 3). _Prueba:_ pendiente
- [ ] **CJ18 (propuesta) · 008-26** — Dada una fecha del archivo con la forma correcta pero que no existe en el calendario (por ejemplo, `2026-02-30`), cuando se valida, entonces es un error (decisión 2 de la H2 de la feature 004; pregunta 3). _Prueba:_ pendiente
- [ ] **008-27** — Dado un municipio que solo recibe la participación del 20 % (solo `PARTICIPACION_20` activa) con las seis secciones mínimas del esquema, cuando se valida, entonces no le exige las secciones de liquidación y puede quedar *Validada* (Doc. 03 §5.5; decisión 2 de la H1 de la feature 004). _Prueba:_ pendiente
- [ ] **008-28** — Dada una validación, entonces evalúa todas las reglas y da todos sus hallazgos, no solo el primero (salvo lo que dice 008-10 de CJ01). Con algún error, la versión pasa a *Con errores*; sin errores, a *Validada*. El resultado queda guardado con la versión del esquema y la del validador con que se obtuvo (pregunta 14) y no cambia después. _Prueba:_ pendiente
- [ ] **CA‑5.13 · 008-29** — Dado el anexo `config_jurisdiccion_52356_2026.yaml` con el SHA‑256 del Doc. 03 §9.2 (`e038e12a…0c872`, el contenido guardado en Git, con fin de línea LF), cargado para la vigencia 2026 de Ipiales, cuando se valida, entonces el resultado es el de la evaluación a mano del Doc. 03 §9.2: 0 errores, las 17 advertencias y las 19 reglas `POR_CONFIRMAR` que lista, CJ09 y CJ10 no evaluables (008-18) y CJ11 como primera versión. Dada la plantilla con el SHA‑256 de §9.2, el resultado tiene errores de CJ02, CJ12 y CJ16, como dice §9.2. Las dos son pruebas *golden* del validador. _Prueba:_ pendiente

**D. Aprobación, rechazo y anulación**

- [ ] **Doc. 03 §5.3 · CA‑05 · 008-30** — Dada una versión *Validada*, cuando la intenta aprobar o rechazar la persona que la cargó, entonces se rechaza, no cambia nada y el intento queda en la auditoría, también si esa persona tiene a la vez el rol que carga y el que aprueba. La regla la aplica el dominio, no solo la interfaz (Doc. 01 §2, principio 3; `docs/arquitectura/02` §4.3). _Prueba:_ pendiente
- [ ] **CJ02 · 008-31** — Dada una versión *Validada*, cuando un usuario con permiso de aprobación, distinto del cargador, la aprueba, entonces debe marcar como revisada cada regla `POR_CONFIRMAR` de la lista y aceptar las advertencias; si falta alguna marca, la aprobación se rechaza y dice cuáles faltan. Al aprobar, la versión pasa a *Aprobada* con el aprobador, el instante y las reglas que marcó. _Prueba:_ pendiente
- [ ] **008-32** — Dada una versión *Validada*, cuando un aprobador distinto del cargador la rechaza con un motivo, entonces pasa a *Rechazada* con el motivo; sin motivo, se rechaza la acción (pregunta 5). _Prueba:_ pendiente
- [ ] **008-33** — Dada una versión *Cargada*, *Con errores* o *Validada*, cuando la persona que la cargó la anula, entonces pasa a *Anulada*. Nadie más la puede anular, y una versión *Aprobada*, *Publicada* o *Reemplazada* no se anula (pregunta 5; Doc. 01 §4, «anular lote propio no aprobado»). _Prueba:_ pendiente
- [ ] **008-34** — Dada una acción que el ciclo de vida no permite desde el estado actual (por ejemplo, aprobar una versión *Con errores* o publicar una *Validada*), cuando se pide, entonces se rechaza con el estado actual y no cambia nada, también cuando otra persona cambió el estado un instante antes. _Prueba:_ pendiente

**E. Publicación**

- [ ] **008-35** — Dada una versión *Aprobada*, cuando un usuario con permiso de publicación la publica, entonces, en una sola transacción: la versión pasa a *Publicada* con el instante; la versión publicada anterior de esa vigencia, si la hay, pasa a *Reemplazada* y apunta a la nueva; y queda a lo sumo una versión *Publicada* por administración y vigencia, también con dos publicaciones a la vez (Doc. 03 §5.6, índice único parcial). Si algo falla, no cambia nada y la versión sigue *Aprobada*. _Prueba:_ pendiente
- [ ] **008-36** — Dada una publicación, cuando se ejecuta, entonces vuelve a validar el archivo con la misma versión del validador y comprueba que la versión publicada contra la que se comparó (CJ11) sigue siendo la publicada y que la vigencia sigue admitiendo la carga; si algo cambió, la publicación se rechaza con el motivo. _Prueba:_ pendiente
- [ ] **008-37** — Dada una versión publicada, entonces guarda la versión del esquema (`schema_version`) y la versión del validador con que se validó y aprobó (Doc. 03 §8, pendiente 9; decisión 6 de la H3 de R3a de la feature 005; pregunta 14). _Prueba:_ pendiente
- [ ] **008-38** — Dada una versión publicada o reemplazada, entonces ni la API ni el rol de la aplicación en la base de datos pueden cambiar su contenido, su archivo, su SHA‑256, sus versiones ni sus datos de carga y aprobación; solo cambian su estado y su reemplazo, por las transiciones válidas. Una corrección es una versión nueva (Doc. 01 §2, principio 1; `AGENTS.md` §5.1). _Prueba:_ pendiente
- [ ] **008-39** — Dada una administración y una vigencia con versión publicada, cuando otro módulo pide la configuración vigente, entonces la obtiene por la fachada del módulo con su identificador, que es el `jurisdiction_config_id` que guardarán la liquidación y la obligación (Doc. 03 §5.6; ERD 2.4, `ledger.tax_obligation`). Qué más hace la publicación depende de la pregunta 1. _Prueba:_ pendiente

**F. Lista de preparación de la vigencia (punto 9)**

- [ ] **Doc. 01 §6.1 · 008-40** — Dada una vigencia, cuando un usuario abre su preparación (opción 1.4), entonces el punto 9, «Configuración de jurisdicción publicada», está *cumplido* si la vigencia tiene una versión *Publicada* y *pendiente* si no, con el motivo «Sin configuración publicada»; ya no muestra «Opción aún no disponible». Los demás puntos no cambian (007-11), y «Habilitar liquidación» sigue inhabilitado mientras falten otros (V‑03, CA‑14). _Prueba:_ pendiente

**G. Aislamiento entre administraciones**

- [ ] **CA‑12 · 008-41** — Dadas las administraciones A y B, cuando un usuario de A consulta, carga, aprueba, publica o descarga configuraciones, entonces solo ve y maneja las de A: las de B no aparecen, no se pueden abrir por su identificador ni se pueden inferir por un conteo o por el mensaje de un rechazo. La administración se toma de la sesión (`docs/arquitectura/02` §5.1 y §5.3). _Prueba:_ pendiente
- [ ] **008-42** — Dada la configuración 2026 publicada en A, cuando B publica la suya de 2026, entonces las dos conviven sin conflicto, y una configuración con el DANE de A no se puede cargar en B (CJ17). _Prueba:_ pendiente

**H. Auditoría**

- [ ] **008-43** — Dada cualquier carga, validación, aprobación, rechazo, anulación, publicación, reemplazo o descarga del original, y cualquier intento rechazado por segregación de funciones (008-30), entonces queda un evento en `audit.audit_event` con la entidad de la configuración y su identificador, la vigencia, la versión, el usuario, la administración, el estado anterior y el nuevo, el instante y el `correlation_id`; la validación registra los totales de errores y advertencias por código, la aprobación las reglas marcadas, y la publicación la versión reemplazada (Doc. 01 §2, principio 9, y §8; `docs/arquitectura/02` §9). _Prueba:_ pendiente

**I. Validador de los anexos en la raíz y su CI** _(pasos 4 y 5 del plan de la feature 004, como diseño de partida; pregunta 13)_

- [ ] **004‑13 · 008-44** — Dado un PR que cambia uno de los YAML de `docs/funcional/anexos/`, cuando corre el CI, entonces valida cada anexo contra el esquema **publicado** por el backend de la versión que declara (CJ01; regla IN‑11 de `docs/arquitectura/01` §5.3), y si falla, el PR no se puede integrar. _Prueba:_ pendiente
- [ ] **008-45** — Dada la carpeta `docs/funcional/anexos/`, cuando corre el validador de la raíz, entonces falla si no encuentra ningún anexo de configuración, si hay un `.yml` o `.yaml` que no sigue la convención `config_jurisdiccion_<DANE o plantilla>_<AÑO>.yaml` (`AGENTS.md` §5.2), si un anexo declara una versión que no está publicada o si el SHA‑256 del esquema descargado no es el fijado en la raíz. _Prueba:_ pendiente
- [ ] **008-46** — Dado un PR, cuando corre el CI, entonces el validador que juzga los anexos es el de la rama base, no el del PR; un segundo *check* corre las pruebas del validador del PR y también valida los anexos; y un PR que cambia el *workflow* no cambia a la vez el validador ni los anexos (ADR 0027; plan de la feature 004, paso 5). Los dos *checks* quedan obligatorios en el *ruleset* de `main`. _Prueba:_ pendiente
- [ ] **008-47** — Dado el validador de la raíz, entonces supera los casos de carga y de validación de cada archivo `*.casos.json` publicado, con el registro de versiones del ADR 0034. _Prueba:_ pendiente

**J. Calidad**

- [ ] **008-48** — Dadas las pantallas de la opción 1.5.9, entonces están construidas solo con componentes de `liv-components`, coinciden con su diseño aprobado en Figma, pasan axe sin violaciones de WCAG 2.1 AA y tienen sus pruebas de navegador en `e2e/` en verde contra el entorno local (ADR 0030). Los componentes nuevos se diseñan e implementan en `liv-components` en su propio cambio (`AGENTS.md` §6). _Prueba:_ pendiente

## Fuera de alcance

- **Editar la configuración en la aplicación:** se carga un archivo; no hay editor ni formulario.
- **La materialización de la configuración en las tablas de `tax`, `ledger` y `documents`** y la convivencia con las opciones 1.5.5 a 1.5.8, si la H1 acepta la recomendación de la pregunta 1.
- **CJ09 y CJ10 contra el catálogo de avalúos:** los evalúa la feature del catálogo (1.5.2 a 1.5.4), si la H1 acepta la recomendación de la pregunta 2.
- **Volver a validar una versión publicada con un validador anterior** (CJ15, excepción de las configuraciones 1.0): hoy hay una sola versión del validador; la acción y la conservación de versiones anteriores las trae la feature que publique la segunda versión del validador (pregunta 14).
- **Habilitar la liquidación, el estado *Lista* y la vuelta a *En preparación* cuando se publica una versión nueva** (007-13 a 007-17): son de la feature 1.5.1. Esta feature no cambia el estado de la vigencia.
- **Registrar las normas de la configuración en la opción 1.3** con su PDF (Doc. 01 §2, principio 8): CJ03 comprueba solo que cada norma citada existe en la sección `normas` del archivo (pregunta 17).
- **La precedencia jurisdicción → nacional** del Doc. 03 §5.5 (pregunta 16).
- **Exportar a Excel** las versiones o el resultado de la validación: llega con la primera feature de consultas 1.6 (pendiente 6 del Doc. 01 §11).
- **El antivirus y el almacenamiento de objetos** del `docs/arquitectura/02` §7, si la H1 acepta la recomendación de la pregunta 8.
- **Cambiar los anexos YAML o el esquema** (versión 1.2): si una pregunta lo exige, va en su propio proceso (Doc. 03 y feature del esquema). Esta feature valida los anexos tal como están.
- **Lo que hacen los Módulos 4 y 5 con las secciones `recaudo` y `cartera`:** aquí solo se validan.
- **Liquidar con la configuración publicada:** es la Fase 2.

## Preguntas abiertas

_Para la H1. Cada una lleva la recomendación de esta spec. Las que cambian un criterio lo dicen. Ninguna se resuelve con un supuesto: si la H1 elige otra opción, los criterios afectados se reescriben antes de aprobar._

**Alcance**

1. **¿Qué hace la publicación: guardar una instantánea o materializar en tablas?** El Doc. 03 §5.2 y §5.3 dicen que publicar es una «materialización transaccional en tablas» (`tax.tax_rate`, `tax.tax_calendar`, `tax.tax_benefit`, `tax.penalty_rule`, `tax.base_adjustment_rule`, `tax.vehicle_category_map`, `ledger.revenue_distribution`, `documents.legal_instrument`, `core.tax_administration`…). Ninguna de esas tablas existe salvo las de `core`, y varias dependen de opciones que no existen (1.3, 1.5.5 a 1.5.8 y el Módulo 4). Además, el Doc. 01 §6.7 hace que las opciones 1.5.5 a 1.5.8 registren tarifas, calendario, beneficios y sanciones por formulario, que son los mismos datos de la configuración, y el Doc. 05 §5.1 deja a esta feature decidir «cómo conviven». **Recomendación:** en esta feature, publicar guarda una **instantánea inmutable** (el archivo original y su contenido) que es la única fuente de esas reglas. Las opciones 1.5.5 a 1.5.8 pasan a ser consultas de solo lectura de la configuración publicada, sin registrar valores propios, y la feature del motor (Fase 2) decide si proyecta la instantánea en tablas para leerla. Así no hay dos fuentes para la misma tarifa. Exige cambiar el Doc. 01 §6.7, el Doc. 03 §5.2 y §5.3 y el Doc. 05 §5.1 (véase «Documentos que cambian»). Cambia 008-39 y el fuera de alcance.
2. **CJ09 y CJ10 sin catálogo.** Las dos comparan la configuración con el catálogo de avalúos (Doc. 03 §9.2: «✅ al publicar»), que todavía no se puede cargar. **Recomendación:** se informan como «no evaluables» sin bloquear la publicación (008-18), y la feature del catálogo las comprueba contra la configuración publicada al cargar y al publicar el catálogo (F009 y F010 ya leen el multiplicador de la configuración, Doc. 03 §3.3). La alternativa, bloquear la publicación hasta que haya catálogo, deja el punto 9 sin cumplirse hasta la opción 1.5.4. Cambia 008-18 y 008-29.
3. **Dos reglas nuevas, CJ17 y CJ18.** El Doc. 03 §5.4 no comprueba que el archivo sea de la administración y la vigencia a la que se carga, ni que sus fechas existan. La feature 007 dejó para esta la comprobación de la sección `vigencia` (decisión 6 de su H1) y la feature 004, la de las fechas reales (decisión 2 de su H2). **Recomendación:** agregarlas al Doc. 03 §5.4 en su versión 0.6 con los códigos **CJ17** (identidad: DANE y tipo de la administración, año y fechas de la vigencia, y `version_config` igual a la última publicada más uno; el sistema numera las versiones publicadas y el archivo debe declarar la que le toca) y **CJ18** (fechas que existen en el calendario). Cambia 008-25, 008-26 y 008-42.

**Roles, estados y vigencia**

4. **Permisos de acción.** El Doc. 01 §4 no tiene filas para la 1.5.9; el Doc. 03 §5.3 dice que aprueba «jurídico/tributario» y `docs/arquitectura/02` §4.2 dice que `JURIDICO` aprueba las configuraciones. **Recomendación** (como la tabla de permisos de acción de la opción 1.4 en el Doc. 01 §5):

   | Permiso | Roles | Acción |
   |---|---|---|
   | `jurisdiction.config.read` | `ADMIN_SISTEMA`, `CARGADOR_TABLAS`, `APROBADOR_TABLAS`, `JURIDICO`, `AUDITOR` (ya existe, Doc. 01 §4.1) | Consultar versiones y resultados |
   | `jurisdiction.config.upload` | `ADMIN_SISTEMA`, `CARGADOR_TABLAS` | Cargar y anular la versión propia. `ADMIN_SISTEMA` porque «configura parámetros» (Doc. 01 §4) y los anexos dicen que el administrador del sistema activa las modalidades |
   | `jurisdiction.config.approve` | `JURIDICO`, `APROBADOR_TABLAS` | Aprobar y rechazar (siempre distinto del cargador, 008-30) |
   | `jurisdiction.config.publish` | `JURIDICO`, `APROBADOR_TABLAS` | Publicar una versión aprobada, como el aprobador publica el catálogo (Doc. 01 §4) |
   | `jurisdiction.config.download` | `CARGADOR_TABLAS`, `APROBADOR_TABLAS`, `JURIDICO`, `AUDITOR` | Descargar el original («Descargar archivo original», Doc. 01 §4, más `JURIDICO` como aprobador) |

   Se aprueba en la H1 como regla y su prueba de matriz se agrega con la migración (`docs/arquitectura/02` §4.4; cambiar roles exige aprobación humana, `AGENTS.md` §4.5). Cambia 008-01 a 008-05, 008-30 a 008-33 y 008-35.
5. **Estados que faltan en el ciclo de vida.** El diagrama del Doc. 03 §5.3 no tiene cómo rechazar una versión validada ni cómo descartar una propia, y *Con errores* no tiene salida. Los anexos dicen `CARGADO`, `VALIDADO`, `APROBADO`, `PUBLICADO`, y el Doc. 03, `CARGADA`… (hallazgo 6 de la feature 004). **Recomendación:** usar los nombres del Doc. 03 (femeninos, como «la configuración»), agregar *Rechazada* (el aprobador, con motivo, desde *Validada*) y *Anulada* (el cargador, sobre la suya, desde *Cargada*, *Con errores* o *Validada*), y dejar *Con errores*, *Rechazada*, *Anulada* y *Reemplazada* como finales: se corrige con una carga nueva. El comentario de los anexos se alinea en su propio proceso. Cambia 008-32 a 008-34.
6. **¿Una sola versión en curso por vigencia?** Con dos en curso, CJ11 de la segunda queda desactualizada cuando se publica la primera. **Recomendación:** sí, una sola (008-09), y además la publicación comprueba que la publicada no cambió (008-36).
7. **¿En qué estados de la vigencia se puede cargar?** Lo decide cada feature 1.5.x (pendiente 8 del Doc. 01 §11), con V‑04 como límite. **Recomendación:** se carga y se publica con la vigencia *En preparación*, *Lista* o *Habilitada*; con *Planeada* no (la preparación no se ha abierto, como dice la feature 007) y con *Cerrada* tampoco (V‑04). Hoy solo pueden existir *Planeada* y *En preparación*. Cambia 008-05, 008-06 y 008-36.

**Seguridad y archivos**

8. **Archivo original, antivirus y almacenamiento.** `docs/arquitectura/02` §7 pide escanear con ClamAV antes de cualquier lectura y guardar el original en almacenamiento de objetos con versionado y bloqueo, con el tamaño máximo en `parametros_sistema`. Es la primera carga de archivos de LIV: no hay almacenamiento de objetos, ni ClamAV, ni componente de carga, y `parametros_sistema` es una sección del mismo archivo que se carga. **Recomendación:** guardar el original en la base de datos, inmutable y con su SHA‑256; aceptar solo texto UTF‑8 hasta un tamaño máximo fijado en la configuración de la aplicación (no es un valor tributario); y dejar ClamAV y el almacenamiento de objetos para la feature de la opción 1.3 (normas con su PDF), que es la primera con archivos binarios. Es una excepción explícita al §7 que la H1 debe aceptar o rechazar; si la rechaza, entran el almacenamiento de objetos y ClamAV, con sus ADR, y la feature crece. Cambia 008-04, 008-05 y 008-07.

**Reglas del validador**

9. **¿Cuáles son las reglas obligatorias de CJ02?** El Doc. 03 §5.4 las lista (jurisdicción, tarifas de las categorías usadas, redondeo, calendario y, si alguna modalidad activa liquida, `cartera.intereses_mora` y `cartera.prescripcion`), mientras que `AGENTS.md` §5.2 y los anexos hablan de las reglas marcadas `obligatoria: true`, que en Ipiales son solo `calendario`, `redondeo`, `cartera.intereses_mora` y `cartera.prescripcion`. **Recomendación:** son obligatorias las de la lista del Doc. 03 más las que el archivo marque `obligatoria: true`; `obligatoria: true` en `intereses_mora` o `prescripcion` no las vuelve obligatorias si ninguna modalidad activa liquida. Cambia 008-11.
10. **Interpretaciones de CJ04 y CJ06 por confirmar** (Doc. 03 §9.2): CJ04 se evalúa sobre las categorías que se usan, y CJ06 sobre cada modalidad que liquida, esté activa o no (hallazgo 2 de la feature 004). Además falta definir «categoría usada». **Recomendación:** confirmarlas como están; una categoría se usa si alguna regla de `categorias` la asigna y, para `PUBLICO`, si `sujecion.servicio_publico.gravado` es `true`. Cambia 008-14, 008-16 y 008-29.
11. **CJ05 sin valores en el código.** El 1 % de la Ley 1964/2019 es un valor tributario y no se puede escribir en el validador (`AGENTS.md` §4.5; feature 004, tabla de su spec). **Recomendación:** CJ05 compara cada tarifa de la categoría `ELECTRICO` con su `tarifa_maxima`, que pasa a ser obligatoria en esa categoría (si falta, es un error), y el aprobador revisa ese valor contra la norma citada. Cambia 008-15.
12. **`ADMINISTRACION_DIRECTA` en un distrito que no es capital** (hallazgo 4 de la feature 004). CJ12 y el comentario de la plantilla dicen «departamento o distrito capital»; el Doc. 03 §9.5 y `AGENTS.md` §5.2 dicen «departamentos y distritos». **Recomendación:** aplicar CJ12 tal como está (solo `DEPARTAMENTO` o `DISTRITO_CAPITAL`) y corregir la redacción de `AGENTS.md` §5.2 y del Doc. 03 §9.5, salvo que jurídica diga lo contrario. Cambia 008-19.
13. **El validador de los anexos en la raíz: ¿en esta feature y con qué alcance?** El *roadmap* lo pone aquí. **Recomendación:** dejarlo en esta feature como un bloque independiente (sus PR van a `tools/` y `.github/`, no dependen del backend y se pueden hacer en paralelo); que valide solo CJ01 (la forma, contra el esquema publicado), no CJ02 a CJ18, para no escribir el validador dos veces; y que `tools/config-jurisdiccion/**` pase a ser ruta crítica en `AGENTS.md` §4.3 (la decisión 3 de la H2 de la 004, anulada al cerrar). Cambiar el CI y el *ruleset* exige aprobación humana (`AGENTS.md` §4.5). La alternativa es una feature aparte, pequeña. Cambia 008-44 a 008-47.
14. **¿Qué es la «versión del validador»?** El Doc. 03 §8 (pendiente 9) la deja a esta feature. **Recomendación:** un número `MAYOR.MENOR.PARCHE` del conjunto de reglas CJ, escrito en el backend, que sube con cualquier cambio de una regla (MENOR si una regla nueva o más estricta, PARCHE si es una corrección sin cambio de resultado, MAYOR si se retira o se relaja una regla). Se guarda en cada validación y en cada publicación, junto con `schema_version`. Sin acción de revalidar en esta feature (fuera de alcance). Cambia 008-28 y 008-37.
15. **Pantallas y Figma.** No hay diseño de la 1.5.9 y faltan componentes en `liv-components` 0.5.0: al menos uno para elegir un archivo, una casilla de verificación (las marcas de `POR_CONFIRMAR`) y un área de texto (el motivo del rechazo), y quizá una vista de cambios para CJ11. **Recomendación:** diseñar en Figma, sección «1.5.9 · Configuración de jurisdicción», las cuatro pantallas de «Qué hace» y los componentes que falten, aprobarlos antes de la H2, e implementar los componentes en su propio cambio de `liv-components`, con su versión publicada antes del PR del frontend (como en la feature 007).

**Otras**

16. **Precedencia jurisdicción → nacional** (Doc. 03 §5.5): dice que la regla territorial prevalece sobre la de la «plantilla nacional», pero la plantilla no se publica (Doc. 03 §9.2) y no hay configuración nacional. **Recomendación:** cada configuración es completa por sí misma y no se combina con otra; la precedencia queda fuera de esta feature y la pregunta pasa al Doc. 03 §8 hasta que exista un caso real (por ejemplo, un departamento con municipios).
17. **Normas sin la opción 1.3.** El Doc. 01 §2 (principio 8) pide norma registrada con PDF para publicar un catálogo, y el Doc. 03 §5.2 dice que `normas` se materializa en `documents.legal_instrument`. **Recomendación:** en esta feature no se exige que las normas estén registradas en la 1.3; CJ03 basta. La feature de la 1.3 decide si la publicación de la configuración pasa a exigirlo.
18. **El modelo de datos.** La tabla del Doc. 03 §5.6 está en `core`, pero la H1 de la feature 004 decidió un esquema propio, `jurisdiction` (decisión 1). La tabla está fuera de las 80 del ERD 2.4 y le faltan columnas para lo que esta spec pide (versión del validador, resultado de la validación, motivo del rechazo, marcas de la aprobación, anulación). El checklist previo a Flyway sigue abierto: como en las features 001 y 007, migrar esta tabla exige una autorización explícita, aunque guarde tarifas en su contenido. **Recomendación:** el `plan.md` propone el modelo (ERD 2.5, 81 tablas o más) y la H2 aprueba el modelo y la autorización de migrar con el checklist abierto, como la H2‑1 de la feature 007. En la H1 basta con aceptar que la feature crea el esquema `jurisdiction` y su tabla.

**Tamaño y partición**

19. **¿Se parte la feature?** Estimación (sin contar *lockfiles* ni archivos generados), del orden de 6.000 a 9.000 líneas en unos 25 a 35 PR, en cinco bloques:

    | Bloque | Proyecto | Contenido | Tamaño aproximado |
    |---|---|---|---|
    | A | Raíz | Validador de los anexos (`tools/`) y su CI (`.github/`) | 600 a 900 líneas, 2 o 3 PR |
    | B | `backend/` | Cargador de producción y validador CJ02 a CJ18, de dominio puro, con sus pruebas y las *golden* de Ipiales y la plantilla | 2.500 a 3.500 líneas, 8 a 10 PR |
    | C | `backend/` | Migración, ciclo de vida, permisos, auditoría, contrato OpenAPI nuevo, BFF y punto 9 | 1.500 a 2.500 líneas, 6 a 9 PR |
    | D | `ui/` | Componentes nuevos de `liv-components` | 600 a 1.000 líneas, 3 o 4 PR |
    | E | `frontend/` y `e2e/` | Pantallas y pruebas de navegador | 1.000 a 1.500 líneas, 5 a 7 PR |

    **Recomendación:** una sola feature con estos bloques, como la 007, porque las pantallas sin el ciclo de vida no sirven y el punto 9 necesita la publicación. A y B no dependen de nada y pueden empezar en paralelo después de la H2; D, después de Figma; C, después de B; y E, después de C y D. La alternativa es sacar el bloque A a una feature propia (pregunta 13).

## Documentos que cambian

_Para el orquestador: esta sesión no los toca. Cada cambio va en su propio PR de documentos, con la versión de cada documento, después de la H1 (o de la H2, si es del modelo)._

| Documento | Cambio | Por |
|---|---|---|
| Doc. 03 (v0.6) | §5.2: fila `vigencia` (se valida contra `tax.administration_fiscal_year`, no se materializa en `tax.fiscal_year`), filas que se «materializan» según la pregunta 1, nota de la precedencia; §5.3: estados *Rechazada* y *Anulada* y los nombres; §5.4: CJ17 y CJ18, alcance de CJ02, CJ04, CJ05, CJ06 y CJ09/CJ10, y versión del validador; §5.5: precedencia; §5.6: esquema `jurisdiction` y columnas; §7: cambios a otros documentos; §8: pendiente 9 resuelto y precedencia; §9.2: CJ09 y CJ10 | Preguntas 1 a 3, 5, 9 a 12, 14, 16 y 18 |
| Doc. 01 (v0.7) | §3: esquema de la 1.5.9; §4: filas de la 1.5.9 en la matriz de acciones; tabla de permisos de acción de la 1.5.9 (como la de §5); §6.1: tabla del punto 9; §6.7: convivencia con la configuración; §8: eventos de la 1.5.9; §11: pendiente 8 para la 1.5.9 | Preguntas 1, 4, 7 y 18 |
| Doc. 05 (v0.3) | §5.1: convivencia de las sanciones con la configuración | Pregunta 1 |
| ERD (2.5) | Esquema `jurisdiction` con su tabla (o tablas), RLS, permisos de `liv_app`, FK de `ledger.tax_obligation` y del checklist previo a Flyway | Pregunta 18 (en la H2) |
| `AGENTS.md` | §4.3: ruta crítica `tools/config-jurisdiccion/**`; §5.2: `ADMINISTRACION_DIRECTA`, si cambia | Preguntas 12 y 13 |
| `docs/arquitectura/01` | §3.1 (nota 1): el esquema `jurisdiction` deja de ser propuesta; §3.2: la flecha `tax → jurisdiction` del punto 9, con su ADR si hace falta (lo decide el plan) | Pregunta 18 y 008-40 |
| `docs/arquitectura/02` | §7: excepción del antivirus y del almacenamiento para la 1.5.9, si se acepta | Pregunta 8 |
| Anexos YAML | Comentario del ciclo de vida (`CARGADO` → `CARGADA`…); `tarifa_maxima` ya está en Ipiales y en la plantilla, se revisa | Preguntas 5 y 11 (en su propio proceso) |
