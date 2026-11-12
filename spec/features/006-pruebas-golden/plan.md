# 006 · Pruebas *golden* en rojo — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** borrador
_(borrador → aprobado (H2))_

## Enfoque

Solo pruebas del *backend*, más un PR de documento en la raíz:

- **Datos:** cada ejemplo de la spec queda como un caso en un *fixture* YAML de `backend/src/test/resources/golden/`. Un archivo por tabla de origen, validado contra su JSON Schema.
- **Pruebas en rojo:** una clase de pruebas *golden* por módulo (`tax` y `ledger`), parametrizada con los casos, que llama a un **adaptador de prueba**. Hoy el adaptador solo lanza «no implementado». La clase lleva `@Disabled` con su *issue* (decisión 1), así que el CI no la ejecuta.
- **Pruebas en verde,** sin `@Disabled`, en cada CI:
  - los *fixtures* están completos y bien formados, y la configuración que citan es la que hay;
  - la lógica de comparación detecta un resultado erróneo, aunque el motor todavía no exista.
- **Frontera:** el adaptador recibe solo lo que el documento da por hecho y prueba solo el cálculo del ejemplo (véase «Frontera del adaptador»). Así, un motor correcto de la Fase 2 o de la Fase 4 no rechaza los casos por una regla que el ejemplo no ejerce.
- **Sin código nuevo en `src/main`.** No se agregan dependencias ni se tocan el `pom.xml` ni los *workflows*.

Encaja con el proyecto así:
- usa lo que el *backend* ya tiene en `test`: JUnit 6.0.3 (Spring Boot 4.1.2), `SafeYamlLoader` (feature 004) y el validador de JSON Schema con el `type` estricto del ADR 0032;
- el documento funcional sigue siendo la fuente de verdad (`docs/arquitectura/04` §5.1);
- el diseño del motor queda para la Fase 2 (decisión 3).

## Frontera del adaptador

Lo que ejerce cada caso y lo que el adaptador **no** debe evaluar. Si lo evaluara, rechazaría casos que el documento calcula con supuestos:

| Módulo | Ejerce | No evalúa (se da como entrada o no aplica) |
|---|---|---|
| `tax` | Doc. 02 §B.6 (ajustes de base y multiplicador de tablas), §B.7 (tarifa y meses gravables), §B.8 (bruto, neto redondeado y descuento) y las fechas del calendario de la configuración (Doc. 03 §9.6 y §9.7) | Prerrequisitos y sujeción (§B.3; p. ej. el cilindraje de la moto de L11), competencia y universo de la modalidad (Doc. 03 §9.5, OB‑02 y OB‑03), homologación y derivación de la categoría: la categoría, el origen de la base y los ajustes son entradas |
| `ledger` | Nacimiento de la obligación (Doc. 04 §3.1), imputación de un pago ya `CONCILIADO` (§5.1), reverso bancario (§6.2) y distribución de una imputación (§7) | Conciliación (§4.4), causación de intereses (Módulo 5: la deuda de apertura es la deuda a la fecha del pago, sin causación adicional) y competencia. La modalidad es siempre una entrada: en C1 a C6 viaja en el evento `EMISSION`, porque la decidió la liquidación, y C1 comprueba que la obligación la toma de ahí (§3.1); en C7 a C9, en la obligación de apertura (B hipotética) |

- **La configuración** se usa solo como fuente de parámetros: tarifas, ajustes, redondeo, calendario, `recaudo` y el bloque de distribución de la modalidad B, que se lee aunque B tenga `activa: false` (decisión 4).
- **Cada caso con supuestos los declara** en `assumptions`, un campo con valores cerrados que valida el esquema:
  - `MODALIDAD_B_HIPOTETICA`: L7 a L9, L11 a L13 y C7 a C9, por la nota del Doc. 03 §9.3 y la frase «con B activa» del Doc. 04 §7. L10 no lo lleva, porque el internado es de la modalidad A, que Ipiales tiene activa. L1 a L6 no llevan supuesto de competencia: el Doc. 02 §B.9 es «régimen nacional» y la competencia está fuera de la frontera;
  - `DEUDA_DADA_A_LA_FECHA_DEL_PAGO`: C4 y C5;
  - `ENTRADA_INFERIDA`: el caso trae una entrada que el documento no da, y la lista con su motivo en `inferred_inputs`.
- **El `README`** explica la frontera, para que las Fases 2 y 4 conecten el adaptador sin tocar los casos.

## Implementación

Hay tres PR, cada uno de un solo proyecto (`AGENTS.md` §4.6). El orden obligatorio está en «Dependencias y recursos».

### D1 · Doc. 04 v0.3: CA‑4.07 como pendiente (raíz, `docs/006-ca-4-07`)

Cubre el criterio 006‑13. El texto registra el hueco **sin calcular un resultado**: no se resuelve con un supuesto (`AGENTS.md` §2).

- **Encabezado:** la versión pasa de 0.2 a 0.3, con el motivo: «registra como pendiente la fecha del pago de CA‑4.07 (feature 006)».
- **§14, fila nueva 8:** «CA‑4.07 (pago de $350.000 sobre un recibo de $342.000; también en §6.1) no da la fecha del pago, y con las fechas de Ipiales su resultado no está definido».
  - Si el recibo de $342.000 es el neto sin descuento, solo existe entre el límite del descuento y el límite sin intereses (§4.1). En Ipiales los dos son el 30‑abr, así que esa ventana está vacía.
  - Hasta el 30‑abr, el recibo sería el valor con descuento, y lo pagado lo supera. El paso 2 de §5.1 manda registrar el descuento «por la diferencia entre el neto y lo pagado», que aquí sería negativa: no queda claro si se reconoce el descuento.
  - Después del 30‑abr, la obligación está en mora y el ejemplo no da los intereses.
  - Se resuelve fijando la fecha del pago y el resultado esperado. Hasta entonces no es *golden* (feature 006, decisión 6).
  - Estado: **Pendiente**; lo decide el responsable funcional.
- **§13:** a CA‑4.07 se le agrega «(pendiente 8 de §14)» en la columna «Esperado», sin cambiar su texto.
- **Según la H2:** si se aprueba el punto 2 de «Aprobaciones que pide la H2», D1 agrega la fila 9 («método del redondeo al peso en la imputación y en la distribución, §5.1 paso 4 y §7»), con la misma forma.
- **Fuera de D1:** ningún otro documento cambia. El Doc. 03 §8, punto 8, ya remite a los pendientes del Doc. 04 §14. La bitácora de `ESTADO.md` la escribe el orquestador (`AGENTS.md` §7).

### B1 · Base de las *golden* y casos de liquidación (`backend/`, `feat/006-golden-tax`)

Cubre 006‑01, CA‑L01 a CA‑L04, 006‑02, LQ‑C01·LQ‑C02·LQ‑C04, y 006‑04 a 006‑12 en su parte de liquidación.

1. **Reuso de la feature 004: solo visibilidad.** Pasan a `public`, sin cambiar su comportamiento ni sus pruebas:
   - en `SafeYamlLoader.java`, la clase, `load(Path)` y `load(String)` (para las variantes dañadas escritas como texto);
   - en `JurisdictionConfigSchemas.java`, la clase, `compile(JsonNode)`, para validar con el dialecto de `type` estricto (ADR 0032; `1000.0` no es un entero), y `metaSchemaErrors`, para validar cada esquema *golden* contra el meta-esquema 2020‑12;
   - en `CommonCases.java`, la clase, `BACKEND` (la raíz del *backend*, desde la propiedad `basedir`) y `EXACT` (el lector JSON sin pérdida de decimales).

   Se descartó moverlos a un paquete común, porque toca más archivos de la feature 004. También se descartó copiarlos, porque duplicaría el cargador seguro y el dialecto.
2. **Apoyo común**, en el paquete de pruebas `co.com.sapenlinea.liv.golden`, que no depende de `tax` ni de `ledger`, para que los paquetes no formen un ciclo. No es un módulo: `ModuleCatalogTest`, `ArchitectureRulesTest` y `CodeSizeGuardTest` excluyen las clases de prueba, y `./mvnw verify` confirma que Spring Modulith también.
   - `GoldenFixtures`: lista y carga los *fixtures* de `src/test/resources/golden/<módulo>/`, los valida contra `golden/<módulo>.schema.json` y comprueba lo que el esquema no expresa:
     - una sola forma de la base;
     - como máximo dos decimales en el bruto;
     - ids únicos.
   - `GoldenConfiguration`:
     - carga la copia de la configuración que cita el *fixture* (`contracts/jsonschema/config-jurisdiccion/1.1/config_jurisdiccion_52356_2026.yaml`);
     - comprueba su SHA‑256 sobre el contenido con fin de línea LF, como el Doc. 03 §9.2. En Windows, con `core.autocrlf=true`, la copia tiene CRLF y su SHA‑256 directo es otro: comprobado en este *worktree* (`1ef110d9…` en disco, `e038e12a…` en Git).
   - `GoldenCase`: el id, los códigos de regla, la descripción, la fuente (documento, versión, sección y fila), `assumptions`, `inferred_inputs` y `depends_on`.
   - `GoldenEngineNotImplementedException`, que extiende `UnsupportedOperationException`, con el mensaje «<motor> no implementado (Fase N); ver golden/README.md».
   - `GoldenResult`: la interfaz que implementan los resultados de cada módulo, con `JsonNode asTree()`, para que el comparador no conozca los tipos de `tax` ni de `ledger` y no haya ciclo de paquetes.
   - `GoldenComparator`: compara `GoldenResult.asTree()` con el `expected` de un caso:
     - los montos en pesos y las tasas, con `BigDecimal.compareTo`;
     - el bruto, con la escala y el redondeo de `comparison`, que vienen del *fixture*;
     - las fechas, los textos, los códigos y los valores lógicos, por igualdad de valor;
     - los movimientos y la distribución, como multiconjunto **exacto**: ni un elemento de más ni uno de menos.

     Usa `SoftAssertions` con `assertAll`. Un valor esperado con resultado nulo falla.
   - `GoldenFixturesTest` (en verde) comprueba, para todos los *fixtures*:
     - que cada esquema *golden* es válido contra el meta-esquema 2020‑12, para que una palabra clave mal escrita no apague una regla;
     - que los *fixtures* cargan y son válidos;
     - que el SHA‑256 de la configuración coincide y su ruta, relativa a `src/test/resources`, queda dentro de esa carpeta (006‑09);
     - que traen el código y la versión de su documento, y que ninguno cita el Doc. 05 (006‑10 y 006‑11);
     - **que las variantes dañadas se rechazan por el motivo correcto** (006‑05). Cada una parte de un *fixture* válido, escrito como texto en la prueba, con un solo cambio, y la prueba comprueba la ruta o la palabra clave del error. Son once:
       - un monto entre comillas;
       - un monto en pesos con decimales;
       - una clave desconocida;
       - un caso sin `expected`;
       - dos formas de base;
       - un `depends_on` fuera de la lista;
       - un código de regla fuera de la lista;
       - un bruto con tres decimales;
       - un id repetido;
       - un SHA‑256 distinto;
       - una ruta de configuración fuera de `src/test/resources`.
3. **Esquema y *fixtures* de liquidación.**
   - `src/test/resources/golden/tax.schema.json`:
     - JSON Schema 2020‑12 con el dialecto de la feature 004, y por voluntad propia dentro de su subconjunto portable (`anyOf`, sin `oneOf` ni `multipleOf`);
     - con `additionalProperties: false` en todos los objetos;
     - montos en pesos de tipo `integer`, tasas y bruto de tipo `number`;
     - con valores cerrados (`enum`) en el código del documento (`Doc. 02`, `Doc. 03`, `Doc. 04`), en los códigos de regla (los de la spec), en `assumptions` y en `depends_on`;
     - con `comparison` fijado por `const` (`scale: 2`, `rounding: HALF_UP`, decisión 7), para que editar el *fixture* no cambie la precisión;
     - en `input`, la base viene de una de cuatro formas: `taxable_base` (L1, L2, L5 y L6, la «Base» que da el Doc. 02), `table_value` (L3, valor de tabla en pesos), `table_value_thousands` (L7 a L12, Doc. 03 §9.3) o `invoice_value_without_vat` (L4 y L13, vehículo nuevo).
   - **Tres *fixtures*** en `src/test/resources/golden/tax/`:
     - `doc02-b9.yaml`: L1 a L5;
     - `doc02-11.yaml`: L6;
     - `doc03-9-3.yaml`: L7 a L13.
   - **Claves en inglés**, con los nombres del ERD (`tax.tax_assessment`: `taxable_base`, `gross_tax`, `net_tax`; `tax.tax_calendar` y `ledger.tax_obligation`: `discount_deadline`, `due_date`) y del Doc. 02 §B.8 (`net_tax_with_discount`). La descripción va en español y los valores, como en el documento.
     - `effective_discount` es una clave propia de la prueba: el descuento efectivo del Doc. 03 §9.3, nota 1 (neto menos el valor a pagar redondeado). No se usa `discount` porque en el Doc. 02 §B.8 y en `tax_assessment` es el 10 % del neto redondeado, antes de redondear el valor a pagar (34.200 en L10), otro valor.
     - `discount_applies` es un campo propio de la prueba, para el «—» de L13.
   - **Comillas obligatorias** en los códigos de regla y en el `sha256`: el cargador seguro rechaza, por ambiguo, un escalar sin comillas que empieza por dígito (`006-02`).
   - Ejemplo del caso L9:

     ```yaml
     document: {code: "Doc. 03", version: "0.5", section: "§9.3"}
     configuration:
       file: "contracts/jsonschema/config-jurisdiccion/1.1/config_jurisdiccion_52356_2026.yaml"   # relativa a src/test/resources
       sha256: "e038e12a30223ce8944cb2220869b6dc52914c6a8cc90a93c273ec869680c872"   # con fin de línea LF
     comparison:
       gross_tax: {scale: 2, rounding: HALF_UP}   # decisión 7; lo demás, exacto
     cases:
       - id: "L9"
         row: 3
         rules: ["CA-L04", "LQ-C01", "LQ-C02", "LQ-C04"]
         description: "Camioneta blindada, valor de tabla 120.000 (+10 %)"
         assumptions: ["MODALIDAD_B_HIPOTETICA", "ENTRADA_INFERIDA"]
         inferred_inputs:
           - {field: "vehicle_category", reason: "el documento dice «camioneta»; PARTICULAR por el mapeo categorias de la configuración"}
         input:
           fiscal_year: 2026
           vehicle_category: "PARTICULAR"
           base_source_type: "OFFICIAL_TABLE"
           table_value_thousands: 120000
           base_adjustments: ["BLINDADO"]
           new_vehicle: false
         expected:
           taxable_base: 132000000
           applied_rate: 0.035
           taxable_months: 12
           gross_tax: 4620000.00
           net_tax: 4620000
           effective_discount: 462000
           net_tax_with_discount: 4158000
           discount_deadline: 2026-04-30
     ```

   - **Qué se compara:** solo las claves de `expected`, que son exactamente las columnas con valor de la spec. Una celda vacía es una clave ausente, y no se compara nada que el documento no dé ni un eco de la entrada:

     | Casos | Claves de `expected` |
     |---|---|
     | L1, L2 y L5 | `applied_rate`, `taxable_months` y `gross_tax` (la base es la entrada) |
     | L3 | `taxable_base`, `applied_rate`, `taxable_months` y `gross_tax` |
     | L4 | `taxable_base`, `applied_rate`, `taxable_months` y `gross_tax` (el Doc. 02 §B.9 da la base) |
     | L6 | `applied_rate` |
     | L7 a L12 | `taxable_base`, `applied_rate`, `taxable_months`, `gross_tax`, `net_tax`, `effective_discount`, `net_tax_with_discount` y `discount_deadline` |
     | L13 | `taxable_base`, `applied_rate`, `taxable_months`, `gross_tax`, `net_tax`, `discount_applies: false` (el «—») y `due_date: 2026-12-31` |

   - **Entradas inferidas**, en `inferred_inputs`, cada una con su motivo:
     - la categoría `PARTICULAR` de L1 a L10 y L13, que el documento no nombra o solo describe («automóvil», «camioneta», «nuevo», «internado», o nada en L5 y L6). En L5 y L6, `PARTICULAR` es la única categoría con tramos en la configuración;
     - el año 2026 de L6, que CA‑L02 no repite (CA‑L01 sí lo trae);
     - la categoría de L11 (`MOTO`) y de L12 (`ELECTRICO`), por el mapeo `categorias` de la configuración;
     - la forma de la base: L1, L2, L5 y L6 traen `taxable_base`, la «Base» que da el Doc. 02, sin origen. L3 trae `table_value` (el documento dice «tabla $120.000.000»);
     - la causación de los usados no está en el *fixture*: la da la configuración (`causacion.general`). Los casos nuevos la traen del documento (`accrual_date: 2026-08-15`).
   - **`depends_on`**, una lista estructurada que reemplaza los comentarios: el `README` la usa para encontrar los casos afectados cuando cambia un valor. Sus valores:
     - `tarifas.MOTO` (L11);
     - `causacion.proporcionalidad_nuevos` (L4 y L13; Doc. 02 §12, pendiente 2);
     - `redondeo.empate` (L8, y en B2, C7 y C8);
     - `categorias` y `sujecion` (L11, los dos `POR_CONFIRMAR`; la lista de la decisión 5.4 no los tenía);
     - `recaudo`, `recaudo.descuento_pago_parcial` y `recaudo.imputacion.redondeo_componentes`, que suma B2.
4. **Adaptador de liquidación**, en `src/test/java/co/com/sapenlinea/liv/tax/golden/`:
   - `AssessmentGoldenInput` y `AssessmentGoldenResult`: *records* con `BigDecimal`, `LocalDate`, `Boolean` y `String`, que se convierten con `decimalValue()`, nunca con `doubleValue()`. `AssessmentGoldenResult` implementa `GoldenResult`;
   - `AssessmentGoldenEngine`: la interfaz, con `AssessmentGoldenResult assess(AssessmentGoldenInput input, JsonNode configuration)`. Su Javadoc fija la frontera de la tabla;
   - `NotImplementedAssessmentEngine`: lanza `GoldenEngineNotImplementedException("motor de liquidación", 2)`;
   - `AssessmentGoldenEngines.current()`: el único punto que elige el adaptador. Hoy devuelve el *stub*; en la Fase 2 solo cambia ese punto.
5. **Prueba en rojo `AssessmentGoldenTest`**, en el mismo paquete:
   - **Anotaciones:** `@Tag("golden")` y `@Disabled("Issue #<n>: el motor de liquidación no existe (Fase 2); ver golden/README.md")`.
   - **Un método `@ParameterizedTest` por criterio de la spec**, con un `@Tag` por cada código del criterio: `CA-L01`, `CA-L02`, `CA-L03`, `CA-L04`, `006-02`, `LQ-C01`, `LQ-C02` y `LQ-C04`.
     - Las etiquetas usan el guion ASCII; el nombre visible, el del documento.
     - Cada método toma los casos que citan su código, así que un caso con varios códigos corre en varios métodos.
     - El `README` aclara que `006-02` identifica un criterio de la spec, no una regla funcional: la tarifa por tramo del Doc. 02 §B.7 no tiene código.
   - **Nombre visible:** `{códigos} · {id} · {descripción}`, p. ej. «CA‑L04 · L9 · Camioneta blindada, valor de tabla 120.000 (+10 %)» (006‑07).
   - **Cada caso:** `GoldenComparator.assertMatches(caso, AssessmentGoldenEngines.current().assess(...))`.
6. **Prueba en verde `AssessmentGoldenFixturesTest`**, en el mismo paquete, sin `@Disabled`:
   - el conjunto de ids es exactamente L1 a L13 (006‑05);
   - cada caso se convierte sin error en `AssessmentGoldenInput` (006‑04). Así, al ejecutar en rojo, el único fallo posible es el del *stub*;
   - cada código de método de `AssessmentGoldenTest` tiene al menos un caso, para que JUnit no falle por un método sin argumentos;
   - **`@Disabled` con *issue*, por *reflection*, en la clase y en cada método de `AssessmentGoldenTest`** (006‑06):
     - todo `@Disabled`, de clase o de método, tiene un motivo que cumple `^Issue #\d+`;
     - hoy hay un `@Disabled` de clase. En la Fase 2 puede pasar a los métodos que aún fallen sin cambiar esta prueba;
     - no hay ninguna anotación del paquete `org.junit.jupiter.api.condition` (todas las `@Enabled…` y `@Disabled…` condicionales: sistema operativo, JRE, propiedades, imagen nativa) ni ningún `@ExtendWith`, que podrían omitir casos en silencio también en la ejecución en rojo.

     La comprobación es solo para las clases *golden*: no crea una regla nueva para el resto de las pruebas. Que ninguna prueba existente se desactive se revisa en el diff de la H3.
   - **La comparación detecta los errores** (`GoldenComparator` con un adaptador de prueba que devuelve el `expected` de cada caso):
     - el adaptador tal cual pasa;
     - falla, por cada clave esperada, con cada una de estas mutaciones:
       - ±1 peso en un monto;
       - ±0,01 en el bruto;
       - ±0,001 en una tasa;
       - ±1 día en una fecha;
       - ±1 mes en los meses gravables;
       - otro valor no nulo en un texto o código;
       - el valor lógico invertido (`discount_applies: true` en L13);
       - un nulo o la clave ausente.

     Así, la comparación no deja pasar en verde un resultado que difiera en una sola clave del documento.
7. ***Issue* del motor.** Se crea al abrir B1, como se aprobó en la H1 (decisión 1). Título: «Pruebas *golden* del motor de liquidación (L1 a L13) en rojo hasta la Fase 2». Lleva la lista de casos, el comando para ejecutarlas y el criterio para cerrarlo (todas en verde y sin `@Disabled`). Su número va en el `@Disabled`.
8. **`src/test/resources/golden/README.md`** (006‑12):
   - **Qué son** las *golden*, de dónde sale cada *fixture*, la frontera del adaptador y el significado de `assumptions`, `inferred_inputs` y `depends_on`.
   - **Cómo se ejecutan en rojo.** El comando se comprobó en este *worktree* con JUnit 6.0.3 y Surefire 3.5.6: con el parámetro, una prueba con `@Disabled` se ejecutó y falló; sin él, se omitió.

     ```bash
     sh mvnw -B -ntp test -Dtest='*GoldenTest' -Dsurefire.failIfNoSpecifiedTests=false '-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition'
     ```

     Si una versión futura de JUnit quita el parámetro, se quita el `@Disabled` en local, sin *commit*.
   - **Cómo se ponen en verde:** se implementa el adaptador real en `…GoldenEngines.current()`, se mueve el `@Disabled` (con el mismo *issue*) a los métodos que aún fallan, se quitan cuando pasan y se cierra el *issue*. La prueba de `@Disabled` acepta los dos lugares, así que no hay que cambiarla.
   - **Qué hacer cuando cambia un ejemplo o un valor de `depends_on`** (decisión 9):
     1. primero, el PR del documento, con su versión;
     2. después, el del *backend*, que cambia el *fixture* y su versión de origen. Si cambió la configuración, también cambian la copia y su SHA‑256.
9. **`CHANGELOG.md`**: una entrada en «Sin publicar».

### B2 · Casos de la cuenta corriente (`backend/`, `feat/006-golden-ledger`)

Cubre 006‑03, CA‑4.01·LQ‑C04, CA‑4.02·CA‑4.03, CA‑4.04·IM‑01·IM‑03, CA‑4.08, CA‑4.09, y 006‑04 a 006‑12 en su parte de cuenta corriente.

1. **Esquema y *fixtures*.**
   - `src/test/resources/golden/ledger.schema.json`, con las mismas reglas que el de liquidación.
   - **Tres *fixtures*** en `src/test/resources/golden/ledger/`:
     - `doc04-5-2.yaml`: C2, C4 y C5;
     - `doc04-13.yaml`: C1, C3 y C6;
     - `doc04-7.yaml`: C7, C8 y C9.
   - **Entrada:**
     - `input.obligation` (C7 a C9): `origin_modality: CONVENIO_DEPTO` y la dirección declarada como `{same_as_jurisdiction: true}` (Ipiales, la jurisdicción de la configuración) o `{same_as_jurisdiction: false, name: "Pasto"}`. Así, el adaptador sabe si se retiene o se gira comparando con `jurisdiccion` de la configuración, sin códigos DANE inventados. C1 a C6 no llevan la dirección, que el documento no da y no se usa;
     - `input.opening_movements`: la deuda dada por componente, solo en C4 y C5 (decisión 5, hallazgo 1). En C4, `ASSESSMENT` TAX 342.000 e `INTEREST_ACCRUAL` INTEREST 15.000; en C5, además, `PENALTY` 30.000. Es la deuda a la fecha del pago, y el adaptador no causa intereses;
     - `input.events`, cada uno con su tipo:
       - `EMISSION` (C1, C2, C3 y C6): la liquidación emitida del internado (L10), con `net_tax` 342.000, `net_tax_with_discount` 308.000 y `origin_modality: UEDF_INTERNACION`, que decidió la liquidación (Doc. 04 §3.1);
       - `PAYMENT`: un pago `CONCILIADO`, con fecha y valor. C4 y C5 usan el 15‑jul‑2026, inferido de «Saldo al 15‑jul‑2026» (Doc. 04 §5.2), y lo anotan en `inferred_inputs`;
       - `BANK_REVERSAL` (C6): el reverso del pago;
       - `ALLOCATION` (C7 a C9): la imputación ya hecha de un pago conciliado, con sus movimientos. Se modela así porque la distribución se hace por cada imputación (Doc. 04 §7). Que todo el pago vaya al componente TAX es una entrada inferida: el documento solo da el valor pagado.
   - **Esperado:** solo lo que el documento da. Los movimientos que se derivan de una regla y no de una cifra del ejemplo se marcan con `derived_from` (la sección de la regla):

     | Caso | Claves de `expected` |
     |---|---|
     | C1 | `obligation`: `original_principal` 342.000, `due_date` 2026‑04‑30 y `origin_modality`, tomada del evento (§3.1); `movements`: `ASSESSMENT` TAX +342.000 |
     | C2 | `movements`: `ASSESSMENT` +342.000, `PAYMENT` −308.000 y `EARLY_PAYMENT_DISCOUNT` −34.000; `balances`: TAX 0; `status`: `PAGADA` |
     | C3 | `movements`: `ASSESSMENT` +342.000 y `PAYMENT` −200.000 (`derived_from`: el valor pagado), sin `EARLY_PAYMENT_DISCOUNT`; `balances`: TAX 142.000. Sin `status`: CA‑4.03 no lo da |
     | C4 | `allocations`: TAX 191.597 e INTEREST 8.403; `balances`: TAX 150.403 e INTEREST 6.597; `total_balance` 157.000 |
     | C5 | `allocations`: TAX 176.744, INTEREST 7.752 y PENALTY 15.504. Sin saldos: el documento no los da |
     | C6 | `movements`: los de C2 más un `REVERSAL` +308.000 con `reverses: PAYMENT` y un `REVERSAL` +34.000 con `reverses: EARLY_PAYMENT_DISCOUNT` (`derived_from`: Doc. 04 §2.1 y ERD 2.2, «el opuesto exacto»); `balances`: TAX 342.000; `payment_status`: `REVERSADO` (§6.2) |
     | C7 a C9 | `distribution`: cada beneficiario, con su valor y si se retiene o se gira |

     - `movements` y `distribution` se comparan como multiconjunto exacto. El campo `reverses` también se compara, para que un reverso enlazado al movimiento equivocado falle aunque los montos cuadren.
     - Ningún caso compara `VIGENTE` ni `EN_MORA`: dependen de «hoy» (§3.3).
     - **C8 prueba que el descuento no se distribuye** (Doc. 04 §7). Su imputación trae el `PAYMENT` TAX −1.811.000 y el `EARLY_PAYMENT_DISCOUNT` TAX −201.000 de la camioneta de L8, y la distribución esperada suma $1.811.000. Los $201.000 salen del Doc. 03 §9.3 (L8) y se marcan como inferidos.
     - **C7 es coherente con el calendario de Ipiales:** un pago de $2.012.000 hasta el 30‑abr supera el valor con descuento, y «la diferencia entre el neto y lo pagado» es 0. La única duda es si se registra un descuento de 0, y no afecta a C7, cuya entrada es la imputación.
2. **Adaptador de la cuenta corriente**, en `src/test/java/co/com/sapenlinea/liv/ledger/golden/`:
   - `LedgerGoldenInput` y `LedgerGoldenResult`, que implementa `GoldenResult`;
   - `LedgerGoldenEngine`, cuyo Javadoc fija la frontera;
   - `NotImplementedLedgerEngine`, que lanza `GoldenEngineNotImplementedException("cuenta corriente", 4)`;
   - `LedgerGoldenEngines.current()`.
3. **Prueba en rojo `LedgerGoldenTest`.**
   - **Anotaciones:** como en B1, con su *issue* («Pruebas *golden* de la cuenta corriente (C1 a C9) en rojo hasta la Fase 4»).
   - **Métodos por criterio:** `CA-4.01`, `CA-4.02`, `CA-4.03`, `CA-4.04` (con las etiquetas `IM-01` e `IM-03`), `CA-4.08` y `CA-4.09`.
   - **Aserciones:** comparan cada componente al peso. En C4 y C5, además, que la suma imputada sea igual al pago y que ningún saldo quede negativo, con los valores del *fixture*.
4. **Prueba en verde `LedgerGoldenFixturesTest`**, como la de B1: ids C1 a C9, conversión a entradas, un caso como mínimo por método, `@Disabled` con *issue* y mutaciones de la comparación. A las de B1 suma las de las listas: un movimiento o una línea de distribución de más, uno de menos, el componente cambiado (TAX ↔ INTEREST), `reverses` cambiado y el beneficiario o el «se retiene o se gira» cambiados. Con un comparador de «contiene» en lugar de multiconjunto exacto, C3 quedaría en verde con un `EARLY_PAYMENT_DISCOUNT` indebido; estas mutaciones lo impiden.
5. ***Issue* de la cuenta corriente**, `README` (sección de la cuenta corriente) y `CHANGELOG.md`.

### Lo que se cita en el PR de cada B

- **Para 006‑04:** la salida del comando en rojo, que muestra el número de invocaciones y que todas terminan en `GoldenEngineNotImplementedException`, y el conteo del informe de Surefire.
- **Para 006‑05:** las once variantes dañadas que se rechazan, en `GoldenFixturesTest`.
- **Verificación general:** `./mvnw verify` en verde.

## Cobertura que los ejemplos no dan

Los ejemplos coinciden con su documento, pero algunos no distinguen la regla que citan. No se inventan casos para cubrirla. Queda para las pruebas unitarias y de propiedades de las Fases 2 y 4 (`docs/arquitectura/04` §5):

- **Residuo al impuesto (Doc. 04 §5.1, paso 4):** C4 y C5 dan lo mismo si cada componente se redondea por separado. La prueba no fallaría con esa implementación.
- **El método de «al peso» (§5.1 y §7) no está definido:** C5 depende de él. Con el peso más cercano da 176.744 / 7.752 / 15.504, lo que dice el documento; truncando los intereses y la sanción y dando el residuo al impuesto, 176.746 / 7.751 / 15.503. C5 lo anota en `depends_on: ["recaudo.imputacion.redondeo_componentes"]`. Es un hueco distinto del empate de medio peso de la decisión 5.8, que ningún caso tiene (véase «Aprobaciones que pide la H2», punto 2).
- **El 80 % redondeado con el residuo al municipio (§7):** C7 a C9 son exactos al peso; no se ejerce.
- **El borde superior del segundo tramo** ($129.032.000) y el de los descuentos en pagos parciales: ningún ejemplo los da.

## Datos, API y seguridad

- **Migraciones:** ninguna.
- **Contrato OpenAPI:** ninguno. El esquema `config-jurisdiccion` tampoco cambia; los esquemas de los *fixtures* son de prueba y no se publican.
- **Permisos:** ninguno.
- **Amenazas:**
  - los *fixtures* son datos de prueba y se cargan con el cargador seguro: sin anclas, alias ni etiquetas, con límites de tamaño, de profundidad y de dígitos;
  - no hay datos de contribuyentes: sin placas ni documentos, solo montos y fechas de los documentos funcionales (`docs/arquitectura/04` §5.3).

## Dependencias y recursos

- **Depende de:** nada que no esté integrado. El `SafeYamlLoader` y el dialecto (feature 004) y la copia de Ipiales 1.1 (feature 005) ya están en `main`.
- **Recursos compartidos:**
  - `backend/CHANGELOG.md`, el `README` de las *golden*, `GoldenFixtures` y `GoldenComparator`: los tocan B1 y B2, en serie;
  - `SafeYamlLoader.java`, `JurisdictionConfigSchemas.java` y `CommonCases.java` (feature 004): solo B1, y solo su visibilidad;
  - el Doc. 04: solo D1.
- **Orden entre proyectos** (orden de integración), obligatorio:
  1. **D1 y B1, en paralelo.** No comparten archivos.
  2. **B2, cuando B1 y D1 estén integrados.** De B1 usa el apoyo común; sus *fixtures* citan el Doc. 04 en la versión 0.3 de D1, aunque los ejemplos que transcribe no cambian entre la 0.2 y la 0.3.
  3. **Cierre del orquestador:** `ESTADO.md`, el *roadmap* (F0‑11 hecho, y con ello la Fase 0), los criterios de la spec y el glosario («prueba *golden*», «*fixture*»).
- **Sesiones:** D1 y B1 pueden ir en sesiones a la vez; la de B2 empieza cuando B1 y D1 estén integrados.

## Decisiones

- **Dos PR de *backend*, no uno.** B1 y B2 juntos pasarían de 1.000 líneas. Por separado, B1 queda en ~650 y B2 en ~450, y cerca de la mitad son *fixtures* y esquemas, que son datos. Se justifica en cada PR por Q12. Si `tasks.md` ve que B1 pasa de ~650, lo parte en dos:
  - B1a: reuso, apoyo común, esquema, `GoldenFixturesTest` y `README`;
  - B1b: *fixtures*, adaptador y pruebas de liquidación.
- **Claves de los *fixtures* en inglés**, con los nombres del ERD y del Doc. 02 §B.8, para que la Fase 2 traduzca poco (`AGENTS.md` §8). Los campos propios de la prueba (`effective_discount`, `discount_applies`, `assumptions`, `inferred_inputs`, `depends_on`, `derived_from`, `reverses`, `comparison`) se declaran como tales en el `README`.
- **La frontera del adaptador se fija en el plan**, no en el motor. Prueba lo que el ejemplo calcula y deja fuera las reglas que el ejemplo da por hechas (competencia, sujeción, conciliación, causación). Se descartó una configuración variante con B activa: exigiría los datos del convenio, que ningún documento da (CJ12).
- **Un método por criterio de la spec, con sus etiquetas.**
  - JUnit no permite etiquetas distintas en cada invocación de una prueba parametrizada. Con un método por criterio, cada código de regla es una etiqueta filtrable para la matriz de trazabilidad (`docs/arquitectura/04` §4.3).
  - El costo es que un caso con varios códigos corre en varios métodos.
- **Un esquema por módulo, con el dialecto de la feature 004.** Se reusa el validador que ya está en `test`, sin dependencias nuevas. Lo que el subconjunto portable no expresa (exclusión de formas, escala del bruto) lo comprueba `GoldenFixtures`.
- **La comparación también se prueba en verde**, con un adaptador que devuelve lo esperado y con mutaciones. Si no, el código de aserción no se ejecutaría hasta la Fase 2, y un error en él dejaría pasar un motor equivocado.
- **El SHA‑256 se calcula con LF**, igual que el Doc. 03 §9.2, para que la prueba pase en Windows y en el CI. Se descartó agregar `eol=lf` en `backend/.gitattributes` por una sola feature.
- **La comprobación de `@Disabled` vale solo para las clases *golden*.** Una prohibición general de `@Disabled` sería una regla de calidad nueva (`AGENTS.md` §4.5), y Q02 ya permite desactivar con *issue* y aprobación.
- **Las pruebas no calculan nada:** no hay en ellas tarifas, redondeo ni porcentajes (006‑08), y todo valor esperado sale del *fixture*.

## Riesgos

- **Un *fixture* que no dice lo que el documento:**
  - cada caso cita su fila, y la revisión adversarial de B1 y B2 compara cada valor con su documento;
  - los valores se recalcularon aparte con `Decimal` (spec, «Casos *golden*»), y los dos revisores del plan los recalcularon otra vez.
- **Que el `@Disabled` se olvide en la Fase 2:**
  - el *issue* queda abierto con el criterio de cierre;
  - las pruebas en verde exigen que el motivo cite el *issue*;
  - el `README` explica cómo quitarlo.
- **Que el adaptador de prueba fije sin querer el diseño del motor:** sus tipos son de los *fixtures*, viven en `src/test` y la Fase 2 los puede cambiar. Lo que no cambia son los valores esperados y la frontera.
- **Que el parámetro de JUnit cambie en una versión futura:** el `README` da la alternativa.
- **Que Spring Modulith incluya las clases de prueba al verificar los módulos:** `./mvnw verify` de B1 lo confirma. Si las incluyera, el apoyo común pasa a `co.com.sapenlinea.liv.shared`, en `src/test`.
- **El tamaño de B1:** véase «Decisiones».

## Verificaciones por PR

| PR | Verificación |
|---|---|
| D1 | Diff del Doc. 04 solo en el encabezado, §13 (CA‑4.07) y §14: la fila 8, y la 9 si la H2 aprueba el punto 2. El texto no calcula un resultado. Revisión de legibilidad |
| B1 | `./mvnw verify` en verde, con las pruebas en verde de los *fixtures* y de la comparación. Comando en rojo: todas las invocaciones de `AssessmentGoldenTest` fallan con `GoldenEngineNotImplementedException`. Diff sin otro `@Disabled`. Revisión adversarial con `liv-revisor-critico` y las cuatro lentes (ruta crítica) |
| B2 | Igual que B1, con `LedgerGoldenTest` |

## Aprobaciones que pide la H2

La revisión adversarial encontró puntos que tocan documentos funcionales, la spec aprobada o el propio proceso de revisión. Los decide la persona:

1. **Las filas del Doc. 03 §9.7** (causación el 10‑mar‑2026 y el 20‑dic‑2026) tienen resultado esperado: hasta cuándo hay descuento, hasta cuándo no hay intereses y desde cuándo hay mora. La spec no las incluye ni las excluye, y `docs/arquitectura/04` §5 pide el 100 % de los ejemplos.
   - **Recomendación:** agregarlas como L14 y L15, con solo esas fechas como valor esperado:
     - `discount_deadline` (o `discount_applies: false`), `due_date` y `default_from`, este último una clave propia de la prueba para «mora desde»;
     - la entrada es `new_vehicle: true` y `accrual_date`. El documento no da base ni categoría, y no se agregan: el adaptador de L14 y L15 solo devuelve las fechas del calendario.
   - La fila del 15‑ago ya está en L13 y no se repite. No se suma «mora desde» a L13, para no mezclar dos fuentes en un caso.
   - Cambia el alcance aprobado en la H1: dos casos más en B1, y el conjunto de 006‑05 pasa a L1 a L15.
2. **El método del redondeo «al peso»** en la imputación y en la distribución (Doc. 04 §5.1, paso 4, y §7) no está definido, y C5 depende de él: con el peso más cercano da lo que dice el documento; truncando, otra cosa. Es un hueco distinto del de la decisión 5.8 (el empate de medio peso), que sigue siendo cierto: ningún caso tiene medio peso.
   - **Recomendación:** transcribir el valor del documento con `depends_on: ["recaudo.imputacion.redondeo_componentes"]`, como dice la decisión 5, y registrar el hueco en D1 como la fila 9 del Doc. 04 §14.
   - La alternativa es dejarlo solo en el *fixture*.
3. **Las aclaraciones de 006‑01 y 006‑07** ya están en la spec. Necesitan tu visto bueno, porque cambian la redacción de criterios aprobados:
   - 006‑01: «valor **esperado**»; las entradas inferidas se marcan;
   - 006‑07: las etiquetas son los códigos del criterio de la spec de cada método, porque JUnit no permite etiquetas por invocación; el número del caso va en el nombre visible.
4. **La segunda ronda de la revisión adversarial terminó con dos hallazgos altos, ya corregidos** (véase «Revisión»). La *skill* `liv-revision-adversarial` permite dos rondas como máximo y pide escalar a la persona si en la segunda siguen apareciendo críticos o altos.
   - **Recomendación:** aprobar la H2 con las correcciones, y que la revisión adversarial de B1 y B2, que se hace sobre el código con el mismo nivel, compruebe estos dos puntos.
   - La alternativa es pedir una tercera ronda sobre el plan.

## Revisión

**Ronda 1 (2026-11-11).** Dos revisores ciegos de nivel alto (`liv-revisor-critico`) en paralelo, sobre `spec.md` y `plan.md`. Encontraron 0 críticos, 4 altos, 13 medios y 12 bajos, 29 en total; algunos los encontraron los dos.

- **Confirmados por los dos y corregidos:**
  - la frontera del adaptador, con la modalidad B hipotética y la competencia, la sujeción, la conciliación y la causación fuera;
  - C7 a C9 como imputación con un evento `ALLOCATION`, y C8 con el descuento no distribuido;
  - la fecha y la deuda a la fecha del pago de C4 y C5;
  - el texto de D1, que ya no calcula un resultado;
  - el reuso de `CommonCases` y `JurisdictionConfigSchemas`;
  - montos `integer` y *fixtures* dañados a propósito, en lugar de una comprobación de `float` que no podía fallar;
  - la comprobación de `@Disabled`, limitada a las clases *golden*;
  - los nombres del ERD;
  - las entradas inferidas marcadas, con la base final como forma de entrada.
- **De un solo revisor, verificados y corregidos:**
  - la comparación probada en verde con mutaciones (alto);
  - la estructura de la emisión;
  - `oneOf`, que no está en el subconjunto portable;
  - un método sin casos;
  - el orden de B2 después de D1;
  - la etiqueta `006-02`;
  - las comillas obligatorias;
  - `depends_on` estructurado, con `categorias` y `sujecion`;
  - el redondeo de C5 y la cobertura que los ejemplos no dan.
- **Descartados:** ninguno.

**Ronda 2 (2026-11-11), solo sobre lo corregido, con los mismos dos niveles.**
- **Conteo:** revisor A, 0 críticos, 0 altos, 9 medios y 1 bajo; revisor B, 0 críticos, 2 altos, 5 medios y 3 bajos.
- **Altos, que los dos encontraron (A los calificó de medios), corregidos:**
  - la frontera contradecía a C1: ahora la modalidad viaja en el evento `EMISSION` y C1 comprueba que la obligación la toma de ahí;
  - las mutaciones no cubrían textos, códigos, valores lógicos, tasas ni listas de más o de menos: ahora sí, y el comparador de listas es de multiconjunto exacto.
- **Medios y bajos, corregidos:**
  - el comparador recibe un `GoldenResult` neutro, sin ciclo de paquetes;
  - una tabla de claves esperadas por caso, sin estados ni saldos que el documento no da, y sin estados que dependen de «hoy»;
  - `reverses` y `payment_status` en C6;
  - la comprobación de `@Disabled` en la clase y en los métodos, contra todo el paquete de condiciones de JUnit y `@ExtendWith`, con `^Issue #\d+`, y compatible con la Fase 2;
  - once variantes dañadas, cada una con su motivo, y los esquemas validados contra el meta-esquema;
  - `load(String)` y `metaSchemaErrors` en la lista de visibilidad;
  - `effective_discount` en lugar de `discount`, que en el Doc. 02 §B.8 es otro valor, y la atribución de `discount_deadline`;
  - el ejemplo de L9 (sin `redondeo.empate`, con su entrada inferida);
  - las entradas inferidas de L1 a L13, C4, C5, C7 a C9, y L1 a L6 sin supuesto de competencia;
  - `comparison` fijado por `const`;
  - la dirección declarada comparada con la jurisdicción de la configuración;
  - la fila 8 de D1, condicional y con §6.1;
  - D1 y su verificación dependen de la H2;
  - la premisa falsa sobre C7, que ya no se lleva a la H2;
  - el redondeo «al peso» planteado como un hueco nuevo, sin corregir la decisión 5.8;
  - `depends_on` con una ruta real de la configuración;
  - la ruta de la configuración, relativa a `src/test/resources`;
  - el detalle de L14 y L15 en la H2.
- **Descartados:** ninguno.
- **Estado:** la revisión **no** se da por pasada sin la persona. La ronda 2 terminó con dos altos que se corrigieron sin una tercera ronda (punto 4 de «Aprobaciones que pide la H2»).

**Lente de legibilidad (2026-11-11),** antes del *commit*: 10 hallazgos (3 altos de referencias y nombres internos, 6 medios y 1 bajo), todos corregidos. Dos ajustan la redacción de la spec (006‑07 y el alcance de «Qué hace»).

- [ ] Aprobación H2: <quién, fecha>.
