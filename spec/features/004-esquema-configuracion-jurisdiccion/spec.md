# 004 · Esquema de la configuración de jurisdicción

**Estado:** hecha — 2026-11-08. Aprobada (H1) el mismo día por el responsable funcional; criterios 004‑05, 004‑07 y 004‑08 ajustados en la H2; criterio 004‑13 trasladado a la feature 1.5.9 al cerrar (decisión del cierre)
_(propuesta → aprobada (H1) → en curso → hecha)_

**Módulo:** `jurisdiction` (contrato del backend; sin código de negocio) · **Menú:** ninguno (prepara la 1.5.9) · **Tipo de cambio:** crítico: contrato del backend, configuración de jurisdicción y CI (`AGENTS.md` §4.3)
**Fuentes:** Doc. 03 §5 (en especial §5.2, §5.4, §5.5 y §5.6) y §9; `docs/funcional/anexos/config_jurisdiccion_plantilla_2026.yaml` y `config_jurisdiccion_52356_2026.yaml`; `docs/arquitectura/00` §5 y §8.1 (F0‑10); `docs/arquitectura/01` §3.1, §5.3 y §14.1 (IN‑11); `docs/arquitectura/04` §2 y §6 (Q09); `AGENTS.md` §5.2; ADR 0003, 0005, 0009, 0027 y 0031

## Qué hace

Cada jurisdicción y vigencia tiene un archivo YAML con sus reglas de liquidación (Doc. 03 §5). Hoy nada comprueba que ese archivo esté bien escrito: una clave mal escrita, una tarifa en porcentaje en vez de fracción o una fecha con otro formato pasan sin aviso.

Con esta feature:
- existe el **esquema 1.0** de la configuración de jurisdicción, en JSON Schema, del que el backend es dueño. Describe la **forma** del archivo: qué secciones y campos hay, de qué tipo es cada uno, cuáles son obligatorios y qué valores admiten los campos cerrados (`estado`, las modalidades de `competencia`, el tipo de jurisdicción…);
- los dos YAML de `docs/funcional/anexos/` (la plantilla y la de Ipiales) son válidos contra ese esquema: lo prueban las pruebas del backend sobre sus copias. Validarlos en el CI de la raíz pasó a la feature 1.5.9 (decisión del cierre);
- la validación da, por cada error, la ruta del campo y el motivo, con el código `CJ01`.

El esquema **no** decide si la configuración es correcta como norma: eso es el validador CJ02–CJ14 (Doc. 03 §5.4), que llega con la feature de la opción 1.5.9. La frontera entre los dos está en la tabla de abajo.

### Qué valida el esquema y qué no

| Regla | ¿En el esquema 1.0? | Por qué |
|---|---|---|
| `CJ01` — el YAML es válido contra el esquema de su `esquema_version` | **Sí**: es esta feature | Forma: tipos, campos obligatorios, valores permitidos, formato de fechas, tasas como fracción, claves desconocidas |
| `CJ02` — sin `PENDIENTE` en reglas obligatorias | No | Es una condición para **publicar**, no para que el archivo esté bien escrito: la plantilla tiene `PENDIENTE` en reglas obligatorias y tiene que ser válida |
| `CJ03` — cada norma referenciada existe en `normas` | No | Cruza dos partes del archivo |
| `CJ04` — rangos continuos, sin huecos ni solapamientos, el último abierto | No | Compara rangos entre sí |
| `CJ05` — tarifa de eléctricos ≤ 1 % | No | Es un valor tributario (`AGENTS.md` §4.5): el validador lo compara con `tarifa_maxima` del propio archivo, no con un número escrito en el esquema |
| `CJ06` — la distribución suma 100 % | No | Suma valores (y ver el hallazgo 2) |
| `CJ08` — `SAN_ANDRES` solo con DANE 88 | No | Cruza dos partes del archivo; la plantilla trae el ajuste con el DANE en `PENDIENTE` |
| `CJ09`, `CJ10`, `CJ11` | No | Necesitan datos de fuera del archivo: tablas cargadas, catálogo publicado, versión anterior |
| `CJ12` — compatibilidad de modalidades | No (solo los nombres de las modalidades y que `activa` sea booleano) | Combina varias modalidades; la plantilla las trae todas inactivas y tiene que ser válida |
| `CJ13` — orden de las fechas del calendario | No (solo que cada fecha tenga formato de fecha) | Compara fechas |
| Que una fecha exista en el calendario (p. ej. no `2026-02-30`) | No (solo mes de 01 a 12 y día de 01 a 31) | El patrón no conoce el largo de cada mes; lo revisa el validador de la 1.5.9 (decisión 2 de la H2) |
| `CJ14` — plazos de los vehículos nuevos | No (solo los valores permitidos de cada `modo`) | Un campo es obligatorio según el valor de otro y compara días: es regla de negocio con su código, y se queda entera en el validador para no partirla en dos |

## Por qué

- Es el entregable **F0‑10**, una de las tres condiciones que faltan para cerrar la Fase 0 (ADR 0031).
- El motor de liquidación no tiene reglas en el código: todo lo lee de esta configuración (`AGENTS.md` §5.1). Un archivo mal formado es una liquidación mal hecha. Tener el contrato antes de la primera feature de negocio (1.5.9, configuración de jurisdicción) fija la forma del archivo antes de que se escriba código que lo lea.
- Las pruebas *golden* (F0‑11) y el motor se escriben contra esta forma.

## Criterios de aceptación

- [x] **CJ01** — Dados los dos YAML de `docs/funcional/anexos/` tal como están hoy, cuando se validan contra el esquema 1.0, entonces los dos son válidos. _Prueba:_ `JurisdictionConfigSchemaTest` («CJ01 · la plantilla y la configuración de Ipiales, tal como están, son válidas», sobre las copias de los anexos del backend) y `JurisdictionConfigCommonCasesTest` (los casos comunes publicados en `1.0.casos.json`)
- [x] **004-01** — Dado un YAML al que le falta una sección obligatoria, cuando se valida, entonces es inválido y el error nombra la sección. Son obligatorias `esquema_version`, `jurisdiccion`, `competencia`, `vigencia`, `normas` y `distribucion_recaudo`: lo mínimo de un municipio que solo recibe el 20 % y no liquida (Doc. 03 §5.5). Las demás secciones de Doc. 03 §5.2 son opcionales para el esquema; que estén cuando alguna modalidad activa liquida lo exige el validador (decisión 2 de la H1). _Prueba:_ `JurisdictionConfigSchemaTest` («004-01 · sin la sección {0}, el error la nombra» y «con solo las seis secciones obligatorias») y `JurisdictionConfigSchemaMutationTest`
- [x] **004-02** — Dado un YAML con una clave que el esquema no declara, en cualquier nivel (p. ej. `fecha_limite_pago_sin_interes` o una sección `tarifa`), cuando se valida, entonces es inválido y el error da la ruta de la clave (decisión 3 de la H1). _Prueba:_ `JurisdictionConfigSchemaTest` («004-02 · una clave que el esquema no declara, en cualquier nivel») y `JurisdictionConfigSchemaMutationTest` («clave desconocida en …», en cada objeto)
- [x] **004-03** — Dado un `estado` con un valor distinto de `VIGENTE`, `POR_CONFIRMAR` o `PENDIENTE` (p. ej. `APROBADO` o `vigente`), cuando se valida, entonces es inválido. En cada objeto que hoy lleva `estado` en los dos YAML, el `estado` es obligatorio. _Prueba:_ `JurisdictionConfigSchemaTest` («004-03 · estado {0} es inválido» y «los tres valores de estado son válidos») y `JurisdictionConfigSchemaMutationTest`
- [x] **004-04** — Dada `competencia.modalidades`, cuando trae una clave distinta de `UEDF_INTERNACION`, `CONVENIO_DEPTO`, `PARTICIPACION_20` o `ADMINISTRACION_DIRECTA`, o una modalidad sin `activa` o con `activa` no booleano, entonces es inválido. Una modalidad ausente es válida (Ipiales no trae `ADMINISTRACION_DIRECTA`). Qué combinaciones están permitidas lo decide `CJ12`, no el esquema. _Prueba:_ `JurisdictionConfigSchemaTest` (modalidad desconocida, sin `activa` o con `activa` no booleano, y modalidad ausente) y `JurisdictionConfigSchemaMutationTest`
- [x] **004-05** — Dados los campos de tipo cerrado, cuando traen un valor fuera de su lista, entonces es inválido: `jurisdiccion.tipo` (`DEPARTAMENTO`, `DISTRITO_CAPITAL`, `DISTRITO`, `MUNICIPIO`), `redondeo.metodo`, los `modo` de `calendario.vehiculos_nuevos` y los demás que el YAML documenta completos con sus opciones (`A | B | C`). `normas[].tipo` queda abierto: el comentario de la plantilla solo da tres opciones para el estatuto territorial, e Ipiales usa otras cuatro (decisión 2 de la H2). La lista completa va en el plan. _Prueba:_ `JurisdictionConfigSchemaTest` («004-05 · {0} fuera de su lista es inválido» y «normas[].tipo queda abierto») y `JurisdictionConfigSchemaMutationTest` («cada lista cerrada del esquema tiene exactamente sus valores»)
- [x] **004-06** — Dada una tasa o un porcentaje, cuando no es una fracción entre 0 y 1 (p. ej. `tarifa: 1.5` en vez de `0.015`), entonces es inválido (`AGENTS.md` §5.2). Un monto en pesos negativo también es inválido. _Prueba:_ `JurisdictionConfigSchemaTest` (tarifa que no es fracción entre 0 y 1, monto o entero negativo, decimal en un campo entero) y `JurisdictionConfigSchemaMutationTest`
- [x] **004-07** — Dada una fecha, cuando no tiene la forma `AAAA-MM-DD` con mes de 01 a 12 y día de 01 a 31 (p. ej. `30/04/2026` o `2026-13-01`), entonces es inválida. Dado un código DANE, cuando no tiene 2 dígitos (departamento) o 5 (municipio), entonces es inválido. _Prueba:_ `JurisdictionConfigSchemaTest` (fecha sin la forma AAAA-MM-DD, código DANE sin 2 ni 5 dígitos, salto de línea final) y `JurisdictionConfigSchemaMutationTest`
- [x] **004-08** — Dado un campo que el equipo debe diligenciar (fechas, tasas, códigos DANE y los textos libres que lista el plan: nombres, NIT, números de normas o actos, `motos_electricas_criterio_sujecion`…), cuando trae el texto `PENDIENTE`, entonces es válido, como hoy en la plantilla y en el NIT de Ipiales (decisión 2 de la H2). En cualquier otro campo (p. ej. `redondeo.multiplo`, un código como `universo` o un texto técnico como `formula_meses`), `PENDIENTE` es inválido. Que no quede ningún `PENDIENTE` al publicar lo decide `CJ02`. _Prueba:_ `JurisdictionConfigSchemaTest` («004-08 · PENDIENTE es válido en {0}», «PENDIENTE es inválido en {0}» y los códigos) y `JurisdictionConfigSchemaMutationTest`
- [x] **004-09** — Dado un YAML con `esquema_version` distinto de una versión publicada del esquema, cuando se valida, entonces la validación falla y lo dice; nunca se valida contra otra versión. _Prueba:_ `JurisdictionConfigSchemaTest` («004-09 · esquema_version {0} no es una versión publicada», versión numérica y «con una versión desconocida no se valida el resto»)
- [x] **004-10** — Dado un YAML con una clave repetida en el mismo objeto, con anclas o alias (`&`, `*`) o con etiquetas (`!!python/…`), cuando se carga, entonces se rechaza antes de validar: una clave repetida no puede pisar a otra en silencio. _Prueba:_ `SafeYamlLoaderTest` («004-10 · se rechaza: {0}» y «se acepta: {0}»), con los casos de carga de `1.0.casos.json`
- [x] **004-11** — Dado el esquema, entonces no fija ningún valor tributario: ni tarifas, rangos, fechas, porcentajes, multiplicadores ni redondeos. Sus únicos límites numéricos son los de la convención (0 y 1 en las fracciones; 0 como mínimo de montos, días y contadores) (`AGENTS.md` §4.5). _Prueba:_ `JsonSchemaFilesTest` («004-11 · {0} solo usa el subconjunto portable y no fija valores tributarios», sin esquemas remotos y sin `jackson-dataformat-yaml`)
- [x] **004-12** — Dada una versión del esquema ya integrada en `main`, cuando un PR cambia su contenido, entonces el CI falla: un cambio de esquema es una versión nueva, en un archivo nuevo (`docs/arquitectura/01` §5.3). _Prueba:_ los escenarios de `check` en `backend/contracts/scripts/test/jsonschema-escenarios.sh` (`esquema-cambiado` falla con `VERSION_CHANGED`) y el paso de esquemas de «backend · contratos (Q08)», que corre en cada PR desde el PR #144
- **004-13** — ~~Dado un PR que cambia uno de los YAML de `docs/funcional/anexos/`, cuando corre el CI, entonces corre su validación contra el esquema **publicado** por el backend (IN‑11), y si falla, el PR no se puede integrar.~~ **Trasladado a la feature 1.5.9** (decisión del cierre): no se implementó en esta feature.

## Fuera de alcance

- El validador CJ02–CJ14, la carga, la aprobación y la publicación de la configuración, la tabla en la base de datos y su materialización: feature de la opción 1.5.9 (`spec/constitution/roadmap.md`, Fase 1).
- Cambiar los YAML de `docs/funcional/anexos/`: el esquema 1.0 los describe tal como están (decisión 4 de la H1). Los hallazgos de abajo van a una versión nueva del Doc. 03 y sus anexos, en su propio proceso.
- Las pruebas *golden* (F0‑11) y el motor de liquidación.
- El validador de los anexos en la raíz y su CI (pasos 4 y 5 del plan, criterio 004‑13): pasan a la feature 1.5.9 (decisión del cierre).
- Pantallas: no hay interfaz.

## Preguntas abiertas

- Ninguna.

Ningún `PENDIENTE` ni `POR_CONFIRMAR` de los documentos cambia un criterio de esta feature: el esquema mira la forma, no los valores.

## Decisiones tomadas en la H1

Decididas por el responsable funcional el 2026-11-08, todas con la opción propuesta.

1. **La configuración publicada vive en un esquema propio de la base, `jurisdiction`**, no en `core`: «un módulo, un esquema» (ADR 0003, que lo dejó pendiente; `docs/arquitectura/01` §3.1, nota 1). No cambia esta feature, que no crea tablas. El Doc. 03 §5.6 y el Doc. 01 (menú 1.5.9 y lista de preparación, ítem 9) dicen hoy `core.jurisdiction_config`: se ajustan en su propio proceso, con la feature 1.5.9, que crea la tabla.
2. **El esquema exige solo las seis secciones mínimas** (004‑01): `esquema_version`, `jurisdiccion`, `competencia`, `vigencia`, `normas` y `distribucion_recaudo`. Un municipio que solo recibe el 20 % no liquida (Doc. 03 §5.5). Que estén las secciones de liquidación cuando alguna modalidad activa liquida lo exige el validador de la feature 1.5.9. Se descartó exigir las 18 siempre.
3. **El esquema es estricto en todos los niveles** (004‑02): rechaza las claves que no declara. Las claves que trae solo uno de los dos YAML se declaran opcionales. Se descartó aceptar claves desconocidas, porque una errata en un nombre pasaría como un campo ausente.
4. **El esquema 1.0 describe los dos YAML tal como están, sin tocarlos:** `norma` es opcional donde aparece y `estado` es obligatorio donde ya está. No se exige `fuente`, que no existe en ningún YAML. La redacción de `AGENTS.md` §5.2 y del comentario de los anexos («cada regla lleva `fuente` y `estado`») se corrige aparte (hallazgo 1). Se descartó exigir `fuente` y `estado` en todas las reglas, que obligaba a cambiar los anexos y el Doc. 03 antes de cerrar la Fase 0.

## Decisiones tomadas en la H2

Decididas por el responsable el 2026-11-08, al aprobar el plan (H2), todas con la opción propuesta.

1. **Plan aprobado y ADR 0032 aceptado**, con los dos hallazgos altos de la segunda ronda de la revisión ya corregidos en el plan, sin tercera ronda.
2. **Tres ajustes de criterios** que salieron de la revisión adversarial del plan:
   - 004‑08: `PENDIENTE` también vale en los textos libres que lista el plan, incluido `motos_electricas_criterio_sujecion`, cuya forma aún no está definida. Sin esto, la plantilla no sería válida (`jurisdiccion.nombre: "PENDIENTE"`);
   - 004‑05: `normas[].tipo` queda abierto. Cerrarlo con las opciones del comentario de la plantilla haría inválida a Ipiales, y cerrarlo con otras sería inventar una lista;
   - 004‑07: el esquema solo comprueba la forma de la fecha; que la fecha exista en el calendario lo revisa el validador de la feature 1.5.9.
3. **(Anulada al cerrar, ver «Decisión del cierre».)** ~~**`tools/config-jurisdiccion/**` pasa a ser ruta crítica** (`AGENTS.md` §4.3):~~ un cambio en el validador de los anexos no puede ir por el flujo directo. Se agrega en el PR del paso 4 del plan, que crea la carpeta.

## Decisión del cierre

Decidida por el responsable el 2026-11-08, al cerrar la feature, para no agregar más verificaciones de CI ni *rulesets* en este punto inicial:

- **Los pasos 4 y 5 del plan pasan a la feature 1.5.9** (configuración de jurisdicción, Fase 1), que carga, valida y publica los YAML. Son el validador de los anexos en `tools/config-jurisdiccion/` (con sus dependencias de Python) y los dos trabajos de `sistema-pr.yml` con sus *checks* en el *ruleset* `main`. Se traslada con ellos el criterio 004‑13.
- **Se anula la decisión 3 de la H2:** `tools/config-jurisdiccion/**` no pasa a ser ruta crítica todavía, porque la carpeta no existe. Lo decide la feature 1.5.9.
- **Lo que queda sin cubrir hasta entonces:** un PR que deje inválido uno de los anexos se puede integrar sin aviso, y la regla IN‑11 (`docs/arquitectura/01` §5.3) queda pendiente para los anexos. Las pruebas del backend validan sus copias de los anexos, que son una instantánea.

## Hallazgos para otras features

No bloquean esta feature. Se anotan para la feature 1.5.9 y la próxima versión del Doc. 03 y sus anexos.

1. **`fuente`:** `AGENTS.md` §5.2 y el comentario de los dos YAML dicen que cada regla lleva `fuente` y `estado`; los YAML usan `norma`, y no en todas las reglas (decisión 4 de la H1).
2. **CJ06 y la modalidad C:** CJ06 dice que la distribución suma 100 %, pero `PARTICIPACION_20` trae una sola línea de 0,20, y el Doc. 03 §9.2 dice que Ipiales (A + C) pasa sin errores. Hay que precisar a qué distribución aplica CJ06 (¿solo a las modalidades que liquidan?).
3. **Plantilla y configuración de Ipiales no tienen las mismas claves:** la plantilla no trae `jurisdiccion.zona_frontera` (que CJ12 necesita para la modalidad A), `codigo_divipola_8`, `departamento`, `redondeo.empate` ni `redondeo.aplica_a`. `AGENTS.md` §5.2 pide mantenerlas sincronizadas; hoy solo coinciden en el primer nivel.
4. **`ADMINISTRACION_DIRECTA`:** CJ12 la limita a «departamento o distrito capital»; el comentario de la plantilla dice `DEPARTAMENTO / DISTRITO_CAPITAL`, pero el Doc. 03 §9.5 y `AGENTS.md` §5.2 dicen «departamentos y distritos». No está claro si un `DISTRITO` que no es capital la puede activar.
5. **Migrar las configuraciones «en el mismo cambio»:** el `docs/arquitectura/01` §5.3 pide que un cambio de esquema migre la plantilla y las configuraciones en el mismo cambio, pero el esquema está en `backend/` y los ejemplos en `docs/`, y un PR toca un solo proyecto (`AGENTS.md` §4.6). Con versiones inmutables (004‑12), cada YAML se sigue validando contra su versión y se migra en un PR aparte. Lo resuelve el plan.
6. **Estados del ciclo de vida:** el Doc. 03 §5.3 usa `CARGADA`, `VALIDADA`, `APROBADA`, `PUBLICADA`; el comentario de los YAML usa `CARGADO`, `VALIDADO`, `APROBADO`, `PUBLICADO`.
