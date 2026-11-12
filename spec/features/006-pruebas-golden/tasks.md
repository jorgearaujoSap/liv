# 006 · Pruebas *golden* en rojo — Tareas

_Checklist accionable derivada del `plan.md` (aprobado en H2, 2026-11-11). Tareas pequeñas y concretas. Marca `[x]` al completarlas. Cada tarea indica:_

- _su proyecto, su PR del plan y los archivos que puede tocar;_
- _de qué otras tareas depende;_
- _qué recursos compartidos usa;_
- _la prueba o verificación que se escribe primero (TDD: rojo → verde → refactorizar). En la tarea de documentación, la «prueba primero» es la verificación de «Verificaciones por PR» del plan: se escribe antes que el texto, como lista de comprobación en el *scratchpad* de la sesión (no se sube), y se corre al final._

_Con eso el orquestador decide qué corre en paralelo (`liv-orquestador`)._

**Reglas para todas las tareas:**
- **Revisión:** es un cambio crítico. Antes de cada PR, `liv-revision-adversarial` con dos revisores de nivel alto (`liv-revisor-critico`) y las cuatro lentes de `liv-revision-4r`, con el resumen en la descripción del PR. Antes de cada *commit*, la lente de legibilidad.
- **Proyectos:** un PR toca un solo proyecto (`AGENTS.md` §4.6).
- **Lo que no escriben las tareas:** `ESTADO.md`, el *roadmap*, el glosario, las marcas de esta lista ni los criterios de `spec.md`. Se lo reportan al orquestador (`AGENTS.md` §7, ADR 0033).
- **Commits:** todo el código de esta feature es de prueba. Los *commits* de código de prueba usan `test:`, y el primero de cada par deja la prueba en rojo por el motivo correcto; los de documentación (`README`, `CHANGELOG.md`), `docs:`.

**Hecho el 2026-11-12:** D1 (#169), B1a (#170), B1b (#172, *issue* #171) y B2 (#174, *issue* #173), integrados por la persona después de su H3; el cierre (T13) va en su propio PR. Las sesiones de las tareas las abrió la sesión de diseño a pedido de la persona.

## Mapa de los cuatro PR

El plan prevé B1 en un solo PR si no pasa de ~650 líneas, y si no, partido en B1a y B1b. Estimado: B1 da unas 1.500 líneas (unas 400 de *fixtures* y esquema). Por eso va en dos PR.

| PR | Rama | Proyecto | Tareas | Tamaño estimado | Depende para **escribirse** | Depende para **integrarse** |
|---|---|---|---|---:|---|---|
| D1 · Doc. 04 v0.3 | `docs/006-ca-4-07` | raíz | T1 | ~30 | nada | nada |
| B1a · Base común | `feat/006-golden-base` | `backend/` | T2 a T4 | ~700 | nada | nada |
| B1b · Liquidación | `feat/006-golden-tax` | `backend/` | T5 a T8 | ~850, de las que ~400 son datos | B1a integrado | B1a |
| B2 · Cuenta corriente | `feat/006-golden-ledger` | `backend/` | T9 a T12 | ~750, de las que ~350 son datos | B1b y D1 integrados | B1b y D1 |

B1a, B1b y B2 pasan de 400 líneas. Cada PR lo justifica por Q12 (`docs/arquitectura/04` §6): son pruebas sin código de producción, y en B1b y B2 cerca de la mitad son *fixtures* y esquemas.

### Detalles que esta lista agrega al plan

El plan aprobado en la H2 no los nombra. No cambian su diseño; se declaran aquí para que la H3 los vea:

- **B1 partido en B1a y B1b,** como el plan permite (plan, «Decisiones»).
- **`GoldenComparatorTest.java`** (B1a): prueba el comparador común con valores sintéticos, antes de que haya casos reales. Las mutaciones sobre los casos reales siguen en `AssessmentGoldenFixturesTest` y `LedgerGoldenFixturesTest`, como dice el plan.
- **`backend/src/test/resources/golden/test-only/`** (B1a): un esquema mínimo y un *fixture* válido de los que parten las variantes genéricas de `GoldenFixturesTest`. No son casos *golden* y `GoldenFixturesTest` no los recorre como tales.
- **`AssessmentGoldenInputs.java` y `LedgerGoldenInputs.java`:** la conversión de un caso a la entrada de su adaptador, que el plan pide (B1, paso 6) sin nombrar la clase.
- **Variantes dañadas:** las once del plan, repartidas entre la parte común (T2) y la del esquema de liquidación (T5), más cuatro que se desprenden de la H2 y de la ronda 2 (Doc. 05 citado, documento sin versión, caso sin base que compara montos y `comparison` distinto de `const`). En total son quince.
- **`default_from`** entre los campos propios de la prueba del `README`. Es la clave de «mora desde» de L14 y L15, que la H2 aprobó (plan, «Aprobaciones que pide la H2», punto 1).

### Orden de integración (obligatorio; plan, «Dependencias y recursos»)

1. D1 y B1a, en paralelo.
2. B1b.
3. B2, cuando B1b y D1 estén integrados: sus *fixtures* citan el Doc. 04 v0.3.
4. El cierre del orquestador (T13).

### Paralelismo

- **Ola 1:** D1 (T1) y B1a (T2 a T4). No comparten archivos ni proyectos.
- **Ola 2:** B1b (T5 a T8), cuando B1a esté integrado.
- **Ola 3:** B2 (T9 a T12), cuando B1b y D1 estén integrados.
- **En serie siempre:**
  - T2 → T3 → T4;
  - T5 → T6 → T7 → T8;
  - T9 → T10 → T11 → T12.

### Recursos compartidos

| Recurso | Quién lo escribe | Regla |
|---|---|---|
| `backend/CHANGELOG.md` y `backend/src/test/resources/golden/README.md` | B1a crea el `README`; B1b y B2 agregan su sección. Los tres agregan su línea al `CHANGELOG.md` | En serie, en el orden de integración |
| Apoyo común (`co.com.sapenlinea.liv.golden`) | Solo B1a | B1b y B2 lo usan sin cambiarlo. Si necesitan cambiarlo, se avisa al orquestador y se declara el archivo en su tarea |
| `SafeYamlLoader.java`, `JurisdictionConfigSchemas.java` y `CommonCases.java` (feature 004) | Solo B1a, y solo su visibilidad | Ninguna otra tarea los toca |
| Copia de Ipiales 1.1 (`backend/src/test/resources/contracts/jsonschema/config-jurisdiccion/1.1/`) | Nadie | Solo se lee. Su SHA‑256 con LF es `e038e12a30223ce8944cb2220869b6dc52914c6a8cc90a93c273ec869680c872` |
| Doc. 04 | Solo D1 | — |
| `pom.xml`, *workflows*, `.gitattributes` | Nadie | Ninguna tarea los toca (decisión 1 de la H1) |

## PR D1 · Doc. 04 v0.3 (raíz, rama `docs/006-ca-4-07`) — 006‑13

- [x] **T1** · Doc. 04 v0.3: CA‑4.07 y el método del redondeo «al peso» como pendientes (plan, D1).
  - **Proyecto:** raíz · **PR:** D1
  - **Archivos:** `docs/funcional/04_Cuenta_Corriente_y_Recaudo.md`, solo en estas partes:
    - el encabezado: versión 0.3 y su motivo;
    - §13: «(pendiente 8 de §14)» en la columna «Esperado» de CA‑4.07;
    - §14: las filas 8 y 9, con el texto del plan, en estado **Pendiente**.
  - **Depende de:** ninguna.
  - **Recursos:** el Doc. 04 (solo D1 lo edita).
  - **Prueba primero** (lista de comprobación, en el *scratchpad*):
    - el diff solo toca el encabezado, la fila de CA‑4.07 en §13 y las filas 8 y 9 de §14;
    - las filas 8 y 9 eran las siguientes libres;
    - ningún texto calcula un resultado (ni $8.000, ni $42.000, ni un método de redondeo) y la fila 8 cita §4.1, §5.1 y §6.1;
    - ningún otro documento cambia.
  - **Al terminar:** la lente de legibilidad, la revisión adversarial y el PR (H3). La revisión adversarial de un documento funcional va con nivel alto, porque toca un pendiente de un módulo crítico.

## PR B1a · Base común de las *golden* (`backend/`, rama `feat/006-golden-base`) — 006‑05, 006‑06 (parte común), 006‑09, 006‑10, 006‑11 y 006‑12

- [x] **T2** · **Rojo:** las pruebas del apoyo común, con el esqueleto mínimo para que compilen (plan, B1, pasos 1 y 2). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B1a
  - **Archivos:**
    - visibilidad, sin otro cambio:
      - `backend/src/test/java/co/com/sapenlinea/liv/contracts/SafeYamlLoader.java`: la clase, `load(Path)` y `load(String)`;
      - `JurisdictionConfigSchemas.java`: la clase, `compile(JsonNode)` y `metaSchemaErrors`;
      - `CommonCases.java`: la clase, `BACKEND` y `EXACT`;
    - en `backend/src/test/java/co/com/sapenlinea/liv/golden/`:
      - `GoldenFixtures.java`, `GoldenConfiguration.java`, `GoldenCase.java`, `GoldenResult.java`, `GoldenEngineNotImplementedException.java` y `GoldenComparator.java`, como esqueletos cuyos métodos lanzan `UnsupportedOperationException`;
      - `GoldenFixturesTest.java` y `GoldenComparatorTest.java`, las pruebas;
    - `backend/src/test/resources/golden/test-only/`: un esquema mínimo y un *fixture* válido que usan solo las variantes de `GoldenFixturesTest`. No es un caso *golden*.
  - **Depende de:** ninguna.
  - **Recursos:** los tres archivos de la feature 004, solo su visibilidad. Las pruebas existentes de `contracts/` deben seguir en verde.
  - **Prueba primero (es la prueba):**
    - **`GoldenFixturesTest`:**
      - cada esquema *golden* es válido contra el meta-esquema 2020‑12;
      - todo *fixture* de `golden/tax/` y `golden/ledger/` carga, es válido contra el esquema de su módulo, trae el código y la versión de su documento, no cita el Doc. 05, y su configuración tiene el SHA‑256 con LF del *fixture* y una ruta dentro de `src/test/resources`. Hoy no hay ninguno: la prueba recorre una lista vacía;
      - **las variantes genéricas** se rechazan por el motivo correcto, comprobando la ruta o la palabra clave del error: id repetido, SHA‑256 distinto, ruta fuera de `src/test/resources`, documento Doc. 05, sin versión del documento y clave desconocida en la cabecera;
      - **el SHA‑256 se calcula con LF:** el mismo contenido con CRLF da el mismo resultado.
    - **`GoldenComparatorTest`**, con resultados y valores esperados sintéticos (no son casos *golden*):
      - pasa cuando todo coincide;
      - falla con cada tipo de mutación: ±1 peso, ±0,01 con escala 2 y `HALF_UP`, ±0,001 en una tasa, ±1 día, ±1 en un entero que no es monto, otro texto o código, el valor lógico invertido, un nulo y la clave ausente;
      - en las listas, falla con un elemento de más, uno de menos y uno cambiado, y no depende del orden (multiconjunto exacto);
      - reporta todas las diferencias juntas (`assertAll`).
  - **Verificación del rojo:** `./mvnw verify` falla en esas pruebas por `UnsupportedOperationException` del esqueleto, no por un error de compilación. Las pruebas que ya existían siguen en verde.
- [x] **T3** · **Verde:** el apoyo común (plan, B1, paso 2). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B1a
  - **Archivos:** las clases de `golden/` de T2 (sin las pruebas).
  - **Depende de:** T2.
  - **Recursos:** ninguno nuevo.
  - **Prueba primero:** las de T2 pasan a verde con el código mínimo.
    - `GoldenFixtures` carga con `SafeYamlLoader` y valida con `JurisdictionConfigSchemas.compile`, nunca con un `SchemaRegistry` propio.
    - El paquete `golden` no importa nada de `tax` ni de `ledger`, para que no haya ciclo.
    - Los montos se leen con `decimalValue()`.
    - Ningún valor tributario en el código (006‑08).
  - **Verificación:** `./mvnw verify` en verde, con Spotless y `CodeSizeGuardTest`. Comprobar que Spring Modulith no cuenta `golden` como un módulo (riesgo del plan); si lo cuenta, avisar al orquestador antes de seguir.
- [x] **T4** · `README` de las *golden* y registro (plan, B1, paso 8). *Commit* `docs:`.
  - **Proyecto:** `backend/` · **PR:** B1a
  - **Archivos:** `backend/src/test/resources/golden/README.md` (nuevo) y `backend/CHANGELOG.md` («Sin publicar»).
  - **Depende de:** T3.
  - **Recursos:** `backend/CHANGELOG.md` y el `README` (compartidos con B1b y B2).
  - **Prueba primero** (lista de comprobación): el `README` tiene todo lo que pide el paso 8 del plan:
    - qué son las *golden* y la frontera del adaptador;
    - los campos propios de la prueba: `effective_discount`, `discount_applies`, `assumptions`, `inferred_inputs`, `depends_on`, `derived_from`, `reverses`, `comparison` y `default_from`;
    - que las etiquetas `006-xx` son criterios de la spec, no reglas funcionales;
    - las comillas obligatorias;
    - el comando en rojo, verificado;
    - cómo se ponen en verde en la Fase 2;
    - qué hacer cuando cambia un ejemplo o un valor de `depends_on`.

    Las secciones de liquidación y de cuenta corriente quedan como títulos que llenan B1b y B2.
  - **Al terminar T2 a T4:** la revisión adversarial (los dos revisores comprueban uno de los dos altos de la ronda 2 del plan: que las mutaciones cubran textos, códigos, valores lógicos, tasas y listas de más o de menos; plan, «Revisión»), las cuatro lentes y el PR (H3).

## PR B1b · Casos de liquidación (`backend/`, rama `feat/006-golden-tax`) — 006‑01, CA‑L01 a CA‑L04, 006‑02, LQ‑C01·LQ‑C02·LQ‑C04, 006‑14, 006‑04, 006‑06 y 006‑07

- [x] **T5** · **Rojo:** las pruebas de los casos de liquidación, que fallan hasta tener sus *fixtures* (plan, B1, paso 6). Correrán en verde en el CI. *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B1b
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/tax/golden/`:
    - `AssessmentGoldenFixturesTest.java`;
    - `AssessmentGoldenInput.java` y `AssessmentGoldenResult.java` (que implementa `GoldenResult`);
    - `AssessmentGoldenInputs.java`, la conversión de un caso a su entrada, como esqueleto que lanza `UnsupportedOperationException`.
  - **Depende de:** B1a integrado.
  - **Recursos:** el apoyo común (solo lectura) y la copia de Ipiales 1.1.
  - **Prueba primero (es la prueba):**
    - el conjunto de ids de `golden/tax/` es exactamente L1 a L15 (006‑05);
    - cada caso se convierte en `AssessmentGoldenInput` sin error (006‑04);
    - las claves de `expected` de cada caso son exactamente las de la tabla «Qué se compara» del plan (006‑01);
    - cada entrada que el plan lista como inferida está en `inferred_inputs` con su motivo, y cada caso con supuesto lo trae en `assumptions`;
    - **variantes del esquema de liquidación**, rechazadas por el motivo correcto: monto entre comillas, monto en pesos con decimales, caso sin `expected`, dos formas de base, sin base en un caso que compara montos, `depends_on` o código de regla fuera de la lista, bruto con tres decimales y `comparison` distinto de `const`;
    - **mutaciones de cada caso real** con `GoldenComparator` y un adaptador que devuelve el `expected`: el adaptador tal cual pasa; ±1 peso, ±0,01 en el bruto, ±0,001 en la tasa, ±1 día, ±1 mes en los meses, `discount_applies` invertido, un nulo o la clave ausente fallan.
  - **Verificación del rojo:** falla porque no hay *fixtures* ni esquema de liquidación, no por un error de compilación.
- [x] **T6** · **Verde:** el esquema y los *fixtures* de liquidación (plan, B1, paso 3). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B1b
  - **Archivos:**
    - `backend/src/test/resources/golden/tax.schema.json`;
    - `backend/src/test/resources/golden/tax/doc02-b9.yaml` (L1 a L5), `doc02-11.yaml` (L6), `doc03-9-3.yaml` (L7 a L13) y `doc03-9-7.yaml` (L14 y L15);
    - `AssessmentGoldenInputs.java` (implementación).
  - **Depende de:** T5.
  - **Recursos:** la copia de Ipiales 1.1 (solo lectura; su SHA‑256 en cada *fixture*).
  - **Prueba primero:** las de T5 pasan a verde.
    - **Cada valor del *fixture* se comprueba contra su documento,** fila por fila, con la tabla «Casos *golden*» de la spec. Para hacerlo se recalcula aparte con `Decimal` en el *scratchpad*, sin subirlo.
    - **Comillas** en los códigos de regla y en el `sha256`.
    - **`depends_on`** según el plan: `tarifas.MOTO` (L11), `causacion.proporcionalidad_nuevos` (L4 y L13), `redondeo.empate` (L8), y `categorias` y `sujecion` (L11).
    - **`assumptions`:** `MODALIDAD_B_HIPOTETICA` en L7 a L9 y L11 a L13, y `ENTRADA_INFERIDA` donde corresponda.
- [x] **T7** · Adaptador, prueba en rojo e *issue* del motor (plan, B1, pasos 4, 5 y 7). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B1b
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/tax/golden/`:
    - `AssessmentGoldenEngine.java`, con el Javadoc de la frontera;
    - `NotImplementedAssessmentEngine.java`;
    - `AssessmentGoldenEngines.java`;
    - `AssessmentGoldenTest.java`;
    - y en `AssessmentGoldenFixturesTest.java`, la comprobación de `@Disabled`.
  - **Depende de:** T6.
  - **Recursos:** GitHub, para crear el *issue* «Pruebas *golden* del motor de liquidación (L1 a L15) en rojo hasta la Fase 2», aprobado en la H1 (decisión 1).
  - **Prueba primero:** la comprobación de `@Disabled` en `AssessmentGoldenFixturesTest`, en rojo hasta que exista `AssessmentGoldenTest` con su anotación:
    - en la clase y en cada método, todo `@Disabled` cumple `^Issue #\d+`;
    - no hay ninguna anotación de `org.junit.jupiter.api.condition` ni ningún `@ExtendWith`;
    - cada código de método tiene al menos un caso.

    Después, `AssessmentGoldenTest`:
    - `@Tag("golden")` y `@Disabled("Issue #<n>: …")`, con el número real del *issue*;
    - un método por criterio, con sus etiquetas: `CA-L01`, `CA-L02`, `CA-L03`, `CA-L04`, `006-02`, `006-14`, `LQ-C01`, `LQ-C02` y `LQ-C04`;
    - el nombre visible `{códigos} · {id} · {descripción}` (006‑07).
  - **Verificación (006‑04):**
    - el comando en rojo del `README`: todas las invocaciones fallan con `GoldenEngineNotImplementedException`; se anotan el número de invocaciones y el conteo del informe de Surefire;
    - sin el parámetro, `./mvnw verify` en verde, con `AssessmentGoldenTest` omitida.
- [x] **T8** · `README` (sección de liquidación) y registro. *Commit* `docs:`.
  - **Proyecto:** `backend/` · **PR:** B1b
  - **Archivos:** `backend/src/test/resources/golden/README.md` (la sección de liquidación: archivos, casos, supuestos y número del *issue*) y `backend/CHANGELOG.md`.
  - **Depende de:** T7.
  - **Recursos:** el `README` y el `CHANGELOG.md` (compartidos).
  - **Prueba primero** (lista de comprobación): la sección nombra los cuatro *fixtures*, los quince casos, la frontera de `tax` y el *issue*.
  - **Al terminar T5 a T8:** la revisión adversarial (los dos revisores comprueban otra vez las mutaciones, ahora sobre los casos reales, y la fidelidad de cada valor a su documento), las cuatro lentes y el PR (H3), con la salida del comando en rojo y la justificación de Q12.

## PR B2 · Casos de la cuenta corriente (`backend/`, rama `feat/006-golden-ledger`) — 006‑03, CA‑4.01·LQ‑C04, CA‑4.02·CA‑4.03, CA‑4.04·IM‑01·IM‑03, CA‑4.08, CA‑4.09, 006‑04, 006‑06 y 006‑07

- [x] **T9** · **Rojo:** las pruebas de los casos de la cuenta corriente, que fallan hasta tener sus *fixtures* (plan, B2, paso 4). Correrán en verde en el CI. *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B2
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/ledger/golden/`:
    - `LedgerGoldenFixturesTest.java`;
    - `LedgerGoldenInput.java` y `LedgerGoldenResult.java` (que implementa `GoldenResult`);
    - `LedgerGoldenInputs.java`, como esqueleto.
  - **Depende de:** B1b y D1 integrados.
  - **Recursos:** el apoyo común (solo lectura) y la copia de Ipiales 1.1.
  - **Prueba primero (es la prueba):**
    - el conjunto de ids de `golden/ledger/` es exactamente C1 a C9;
    - la conversión a `LedgerGoldenInput` no da error;
    - las claves de `expected` son las de la tabla de B2 del plan: estado solo en C2, sin saldos en C5, sin `VIGENTE` ni `EN_MORA`, y `reverses` y `payment_status` en C6;
    - en C1, C2, C3 y C6, la modalidad va en el evento `EMISSION`; en C7 a C9, en la obligación. C4 y C5 no tienen emisión: parten de `opening_movements`;
    - las entradas inferidas y los `derived_from` del plan están presentes;
    - las variantes del esquema de la cuenta corriente se rechazan por el motivo correcto;
    - **mutaciones de cada caso real:** las de B1b, más las de las listas: un movimiento o una línea de distribución de más, uno de menos, el componente cambiado, `reverses` cambiado, y el beneficiario o el «se retiene o se gira» cambiados.
  - **Verificación del rojo:** falla porque no hay *fixtures* ni esquema de la cuenta corriente.
- [x] **T10** · **Verde:** el esquema y los *fixtures* de la cuenta corriente (plan, B2, paso 1). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B2
  - **Archivos:**
    - `backend/src/test/resources/golden/ledger.schema.json`;
    - `backend/src/test/resources/golden/ledger/doc04-5-2.yaml` (C2, C4 y C5), `doc04-13.yaml` (C1, C3 y C6) y `doc04-7.yaml` (C7 a C9);
    - `LedgerGoldenInputs.java` (implementación).
  - **Depende de:** T9.
  - **Recursos:** la copia de Ipiales 1.1 (solo lectura).
  - **Prueba primero:** las de T9 pasan a verde.
    - **Cada valor se comprueba contra el Doc. 04 v0.3,** fila por fila (y, en C8, los $201.000 contra el Doc. 03 §9.3, L8). Los *fixtures* citan `version: "0.3"`.
    - **`depends_on`:**
      - `recaudo` (C3, C7 a C9);
      - `recaudo.descuento_pago_parcial` (C3);
      - `recaudo.imputacion.redondeo_componentes` (C5);
      - `redondeo.empate` (C7 y C8).
    - **`assumptions`:** `MODALIDAD_B_HIPOTETICA` en C7 a C9, y `DEUDA_DADA_A_LA_FECHA_DEL_PAGO` en C4 y C5.
- [x] **T11** · Adaptador, prueba en rojo e *issue* de la cuenta corriente (plan, B2, pasos 2, 3 y 5). *Commit* `test:`.
  - **Proyecto:** `backend/` · **PR:** B2
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/ledger/golden/`:
    - `LedgerGoldenEngine.java`, con el Javadoc de la frontera: la modalidad siempre es una entrada;
    - `NotImplementedLedgerEngine.java`;
    - `LedgerGoldenEngines.java`;
    - `LedgerGoldenTest.java`;
    - y en `LedgerGoldenFixturesTest.java`, la comprobación de `@Disabled`.
  - **Depende de:** T10.
  - **Recursos:** GitHub, para crear el *issue* «Pruebas *golden* de la cuenta corriente (C1 a C9) en rojo hasta la Fase 4» (decisión 1 de la H1).
  - **Prueba primero:** la comprobación de `@Disabled`, como en T7. Después, `LedgerGoldenTest`:
    - métodos `CA-4.01` (con `LQ-C04`), `CA-4.02`, `CA-4.03`, `CA-4.04` (con `IM-01` e `IM-03`), `CA-4.08` y `CA-4.09`;
    - en C4 y C5, además, que la suma imputada sea el pago y que ningún saldo quede negativo.
  - **Verificación (006‑04):** el comando en rojo, con todas las invocaciones de `LedgerGoldenTest` con `GoldenEngineNotImplementedException`, y `./mvnw verify` en verde.
- [x] **T12** · `README` (sección de la cuenta corriente) y registro. *Commit* `docs:`.
  - **Proyecto:** `backend/` · **PR:** B2
  - **Archivos:** `backend/src/test/resources/golden/README.md` (la sección de la cuenta corriente: archivos, casos, eventos, supuestos y número del *issue*) y `backend/CHANGELOG.md`.
  - **Depende de:** T11.
  - **Recursos:** el `README` y el `CHANGELOG.md` (compartidos).
  - **Prueba primero** (lista de comprobación): la sección nombra los tres *fixtures*, los nueve casos, los cuatro tipos de evento, la frontera de `ledger` y el *issue*.
  - **Al terminar T9 a T12:** la revisión adversarial (los dos revisores comprueban el otro alto de la ronda 2 del plan: que la modalidad de C1 viaje en `EMISSION` y la frontera no se contradiga; plan, «Revisión»), las cuatro lentes y el PR (H3).

## Cierre (orquestador; lo hizo la sesión de diseño a pedido de la persona)

- [x] **T13** · El orquestador cierra la feature. Lo escribe solo él (`AGENTS.md` §7):
  - el *roadmap*: F0‑11 hecho y, con él, la Fase 0 (ADR 0031);
  - el glosario (`docs/arquitectura/01` §15): «prueba *golden*» y «*fixture*»;
  - `ESTADO.md`: la tabla de F0‑11, la bitácora y el pendiente nuevo del Doc. 04 §14 (filas 8 y 9);
  - los criterios de `spec.md`, cada uno con su prueba.
- [x] **T13.1** (orquestador) · Revisión adversarial independiente de lo implementado y las cuatro lentes de `liv-revision-4r` en cada PR; hallazgos resueltos o justificados.
- [x] **T13.2** (orquestador) · Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra.
- [x] **T13.3** (orquestador) · Compuertas de CI en verde y cada PR integrado por una persona (H3).
- [x] **T13.4** (orquestador) · Mover la feature a «Hecho» en `../../constitution/roadmap.md` y actualizar `ESTADO.md`.

## Mantenimiento (checklist recurrente)

- [ ] Si cambia un ejemplo de los Docs. 02, 03 o 04, o un valor que aparece en `depends_on`: primero el PR del documento, con su versión; después el del *backend*, que cambia el *fixture* y su versión de origen (decisión 9 de la H1, `golden/README.md`).
- [ ] Si cambia la configuración de Ipiales: nueva copia en el *backend* (otro PR) y el SHA‑256 nuevo en cada *fixture*.
- [ ] Cuando la Fase 2 o la Fase 4 conecten su motor: cambiar solo `…GoldenEngines.current()`, mover el `@Disabled` a los métodos que aún fallen, quitarlos cuando pasen y cerrar el *issue*.
- [ ] Cuando se resuelvan los pendientes 8 y 9 del Doc. 04 §14: CA‑4.07 puede entrar como *golden*, y C5 actualiza su `depends_on`.
