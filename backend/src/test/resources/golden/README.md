# Pruebas *golden*

Las pruebas *golden* convierten los ejemplos numéricos de los documentos funcionales (Docs. 02, 03 y 04 de `docs/funcional/`) en pruebas del *backend*. Cada ejemplo es un **caso** en un archivo de datos (un *fixture* YAML) con su entrada, la configuración que usa y el resultado esperado al peso. Una prueba parametrizada se lo pasa al motor y compara.

Esta carpeta y su código de prueba (`src/test/java/co/com/sapenlinea/liv/golden/`) son de la feature 006. No hay código en `src/main`.

## Estado

Hoy el motor de liquidación (Fase 2) y la cuenta corriente (Fase 4) no existen. Por eso:

- las pruebas *golden* de cada módulo (`AssessmentGoldenTest` y `LedgerGoldenTest`, que llegan con los PR de los casos de cada módulo) fallan («en rojo») por una sola razón, `GoldenEngineNotImplementedException`, y llevan `@Disabled` con su *issue*, así que el CI no las ejecuta;
- las pruebas **en verde** (`GoldenFixturesTest`, `GoldenComparatorTest` y, por módulo, una prueba de sus *fixtures*) se ejecutan en cada CI: comprueban que los *fixtures* están completos y bien formados y que la comparación detecta un resultado erróneo.

## Qué hay

| Parte | Dónde | Qué hace |
|---|---|---|
| Apoyo común | `golden/` (paquete de prueba `co.com.sapenlinea.liv.golden`) | Carga y valida los *fixtures* (`GoldenFixtures`), comprueba la configuración que citan (`GoldenConfiguration`) y compara un resultado con lo esperado (`GoldenComparator`). No depende de `tax` ni de `ledger` y no es un módulo. Lo que el esquema de un módulo no expresa (por ejemplo, que la base venga de una sola forma) lo agrega ese módulo como una `GoldenFixtures.Rule`, sin cambiar el apoyo común |
| Esquemas | `golden/<módulo>.schema.json` (llegan con los casos de cada módulo) | JSON Schema 2020‑12 de los *fixtures* de cada módulo |
| *Fixtures* | `golden/<módulo>/*.yaml` (ídem) | Un archivo por tabla de origen |
| Prueba sintética | `golden/test-only/` | Un esquema y un *fixture* mínimos de los que parten las variantes dañadas de `GoldenFixturesTest`. **No son casos *golden*** |

El esquema, los *fixtures* y las pruebas de cada módulo se describen en sus secciones, más abajo.

## Formato de un *fixture*

```yaml
document: {code: "Doc. 03", version: "0.5", section: "§9.3"}   # de dónde sale y en qué versión
configuration:
  file: "contracts/jsonschema/config-jurisdiccion/1.1/config_jurisdiccion_52356_2026.yaml"   # relativa a src/test/resources
  sha256: "e038e12a30223ce8944cb2220869b6dc52914c6a8cc90a93c273ec869680c872"   # con fin de línea LF
comparison:
  <campo>: {scale: 2, rounding: HALF_UP}   # solo los campos que se comparan redondeados; lo demás, exacto
cases:
  - id: "<caso>"
    row: 3                      # fila del documento (si la tiene)
    rules: ["…"]                # códigos de regla de la fila
    description: "…"
    assumptions: ["…"]
    inferred_inputs: [{field: "…", reason: "…"}]
    depends_on: ["…"]
    input: {…}
    expected: {…}
```

- **Claves en inglés**, con los nombres del ERD; descripciones y motivos en español.
- **Comillas obligatorias** en los códigos de regla y en el `sha256`: el cargador seguro rechaza, por ambiguo, un escalar sin comillas que empieza por dígito (`006-02`).
- **Montos** de tipo entero (en pesos) y **tasas** y brutos de tipo decimal. Nunca `float` ni `double`: el cargador lee los decimales exactos y el comparador rechaza un resultado en coma flotante.
- **Solo lo que el documento da.** Una celda vacía del documento es una clave ausente de `expected`: la prueba no compara nada que el documento no diga.
- **Sin Doc. 05.** Ningún *fixture* transcribe ni cita el Doc. 05 (intereses de mora) mientras estén abiertos sus pendientes 2, 10, 11 y 12 (`AGENTS.md` §5.3).

### Campos propios de la prueba

No son del ERD ni de los documentos: los define esta feature.

| Campo | Significado |
|---|---|
| `effective_discount` | El descuento efectivo del Doc. 03 §9.3, nota 1 (neto menos el valor a pagar redondeado). No es el `discount` del Doc. 02 §B.8, que es otro valor |
| `discount_applies` | `false` cuando el documento dice «—» (no hay descuento) |
| `default_from` | «Mora desde» del calendario (Doc. 03 §9.7) |
| `assumptions` | Lo que el documento da por hecho. Valores cerrados que valida el esquema (por ejemplo, `MODALIDAD_B_HIPOTETICA`, `DEUDA_DADA_A_LA_FECHA_DEL_PAGO` y `ENTRADA_INFERIDA`) |
| `inferred_inputs` | Entradas que el documento no da y el caso necesita, cada una con su motivo |
| `depends_on` | Las claves de la configuración de jurisdicción cuyo valor usa el caso. Si ese valor cambia, estos son los casos que hay que revisar |
| `derived_from` | En un movimiento esperado que sale de una regla y no de una cifra del ejemplo: la sección de la regla. Es un dato del *fixture*: el comparador no lo busca en el resultado |
| `reverses` | En un reverso, el movimiento que revierte. Se compara, para que un reverso enlazado al movimiento equivocado falle aunque los montos cuadren |
| `comparison` | Escala y redondeo por campo (el bruto, al centavo con la mitad hacia arriba). El esquema de cada módulo lo fija (por `const`), para que editar el *fixture* no cambie la precisión. Se aplica a todo campo con ese nombre, a cualquier profundidad: no le pongas el nombre de un monto que se compara exacto |

### Etiquetas

Las etiquetas `006-xx` (por ejemplo, `006-02`) son **criterios de la spec** de la feature (`spec/features/006-pruebas-golden/spec.md`), no reglas funcionales: la tarifa por tramo del Doc. 02 §B.7 no tiene código. Los demás códigos (`CA-L04`, `LQ-C01`, `CA-4.02`…) son los de los documentos. JUnit no permite una etiqueta distinta en cada invocación de una prueba parametrizada, así que hay un método por código y un caso con varios códigos corre en varios métodos. En las pruebas de cada módulo, el nombre visible de un caso es `{códigos} · {id} · {descripción}`.

## La comparación

`GoldenComparator` compara solo las claves de `expected`:

- los montos y las tasas, por valor (`BigDecimal.compareTo`);
- los campos de `comparison`, redondeados a su escala;
- las fechas (texto ISO‑8601), los textos, los códigos y los valores lógicos, por igualdad;
- las listas (movimientos, distribución), como **multiconjunto exacto**: ni un elemento de más ni uno de menos, en cualquier orden;
- un valor esperado con resultado nulo o con la clave ausente falla;
- un número en coma flotante falla;
- un valor esperado `null` exige que el resultado emita la clave con `null` (`GoldenResult.asTree()`): una clave ausente es una diferencia;
- un objeto se compara por las claves del esperado: una clave que el resultado agrega no cuenta, también dentro de una lista (para exigir `reverses` en todo movimiento, el *fixture* lo escribe, con `null` en los que no son reversos);
- un objeto esperado sin ninguna clave que comparar (`{}`) falla, porque aceptaría cualquier resultado;
- todas las diferencias se reportan juntas.

El SHA‑256 de la configuración se calcula sobre el contenido con **fin de línea LF**, como el Doc. 03 §9.2. En Windows, con `core.autocrlf=true`, la copia tiene CRLF y su SHA‑256 directo es otro.

## Qué comprueban los *fixtures* en cada CI

`GoldenFixtures` comprueba, además del esquema del módulo: el código y la versión del documento; que ninguno cite el Doc. 05 (por nombre, archivo, códigos `MO-`, `SA-`, `PS-` y `CH-` o secciones `cartera.`); que la configuración citada esté dentro de `src/test/resources` y su SHA‑256 con LF coincida; que los ids no se repitan; que un campo con `comparison` no traiga más decimales que su escala; que las listas de reglas, supuestos y `depends_on` sean de texto; y que `expected` no tenga objetos vacíos. En `golden/` solo puede haber las carpetas de módulo, `test-only/`, los esquemas y este `README`, y en la carpeta de un módulo solo archivos `.yaml`. Cada esquema se valida contra el meta‑esquema 2020‑12 **y** contra la lista de palabras clave del subconjunto portable (sin `oneOf` ni `multipleOf`), porque el meta‑esquema acepta una palabra clave mal escrita y apagaría la regla en silencio.

El esquema del módulo puede exigir más (por ejemplo, fijar la copia de Ipiales por `const`), y una regla propia del módulo se agrega con `GoldenFixtures.Rule`. La prueba de cada módulo debe exigir además el conjunto exacto de ids de sus casos: sin eso, una carpeta borrada o vacía no se notaría.

## La frontera del adaptador

Cada módulo tendrá un **adaptador de prueba** (una interfaz y una implementación que hoy lanza `GoldenEngineNotImplementedException`), en `src/test`; llega con los casos del módulo. Recibe solo lo que el documento da por hecho y prueba solo el cálculo del ejemplo. Lo que sigue **no** lo evalúa el adaptador, sino que se le da como entrada o no aplica; si lo evaluara, rechazaría casos que el documento calcula con supuestos:

| Módulo | Ejerce | No evalúa |
|---|---|---|
| `tax` | Doc. 02 §B.6 (ajustes de base y multiplicador de tablas), §B.7 (tarifa y meses gravables), §B.8 (bruto, neto redondeado y descuento) y las fechas del calendario (Doc. 03 §9.6 y §9.7) | Prerrequisitos y sujeción (§B.3), competencia y universo de la modalidad (Doc. 03 §9.5, OB‑02 y OB‑03), homologación y derivación de la categoría: la categoría, el origen de la base y los ajustes son entradas |
| `ledger` | Nacimiento de la obligación (Doc. 04 §3.1), imputación de un pago ya `CONCILIADO` (§5.1), reverso bancario (§6.2) y distribución de una imputación (§7) | Conciliación (§4.4), causación de intereses (Módulo 5) y competencia. La modalidad siempre es una entrada |

La configuración se usa solo como fuente de parámetros: tarifas, ajustes, redondeo, calendario, `recaudo` y el bloque de distribución de la modalidad B, que se lee aunque B tenga `activa: false`.

## Ejecutarlas en rojo

Con `@Disabled` el CI no las ejecuta. Para verlas fallar, desactiva la condición de JUnit que las omite. El comando elige las clases cuyo nombre termina en `GoldenTest` (las pruebas *golden* de cada módulo); `GoldenFixturesTest` y `GoldenComparatorTest` ya corren en verde y no se seleccionan:

```bash
sh mvnw -B -ntp test -Dtest='*GoldenTest' -Dsurefire.failIfNoSpecifiedTests=false '-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition'
```

Comprobado con JUnit 6.0.3 y Surefire 3.5.6: con el parámetro, una prueba con `@Disabled` se ejecuta y falla; sin él, se omite. Todas deben fallar con `GoldenEngineNotImplementedException`; si alguna falla por otra causa (un *fixture* que no carga, un campo que falta), es un error de la prueba. Si una versión futura de JUnit quita el parámetro, se quita el `@Disabled` en local, sin *commit*.

## Ponerlas en verde (Fases 2 y 4)

1. Implementa el adaptador real y cambia solo el punto que lo elige, `<Módulo>GoldenEngines.current()` (la clase de selección de cada módulo). No se tocan los casos.
2. Mueve el `@Disabled` (con el mismo *issue*) a los métodos que aún fallen.
3. Quítalos cuando pasen.
4. Cierra el *issue*.

La prueba que cuida los `@Disabled` acepta el de la clase y el de los métodos, así que no hay que cambiarla.

**No debilites la prueba para que pase** (`AGENTS.md` §4.5): no cambies un `expected` para que coincida con el motor, no quites un caso y no muevas un `@Disabled` sin su *issue*. Si el motor y el *fixture* difieren, el documento manda: o el motor está mal, o el documento cambia por su propio proceso (siguiente sección).

## Cuando cambia un ejemplo o un valor de `depends_on`

Cambiar una regla o un ejemplo no es un cambio directo: va por el SDD (`AGENTS.md` §4.3, criterio 1). Un *fixture* solo cambia si cambió el documento o si se corrige una transcripción contra el documento.

1. Primero, el PR del documento funcional, con su versión nueva (`AGENTS.md` §3.1).
2. Después, el PR del *backend*, que cambia el *fixture* y la versión de origen que cita. Si cambió la configuración de Ipiales, también cambian su copia en `src/test/resources/contracts/jsonschema/config-jurisdiccion/<versión>/` y el `sha256` de cada *fixture*. Un PR toca un solo proyecto (`AGENTS.md` §4.6).

## Liquidación (`tax`)

Quince casos del motor de liquidación (Fase 2, Módulo 3), de los Docs. 02 v0.2 y 03 v0.5. *Issue*: [#171](https://github.com/jorgearaujoSap/liv/issues/171), «Pruebas *golden* del motor de liquidación (L1 a L15) en rojo hasta la Fase 2».

### Archivos

| Qué | Dónde |
|---|---|
| Esquema | `golden/tax.schema.json`. Fija por `const` la copia de Ipiales (ruta y SHA‑256) y `comparison` (`gross_tax`: escala 2, `HALF_UP`); los códigos de regla, los supuestos y `depends_on` son listas cerradas; los montos en pesos son enteros |
| *Fixtures* | `golden/tax/doc02-b9.yaml` (L1 a L5), `doc02-11.yaml` (L6), `doc03-9-3.yaml` (L7 a L13) y `doc03-9-7.yaml` (L14 y L15) |
| Prueba en rojo | `tax/golden/AssessmentGoldenTest`: `@Tag("golden")` y `@Disabled("Issue #171: …")`; un método por código que citan los casos (`CA-L01` a `CA-L04`, `006-02`, `006-14`, `LQ-C01`, `LQ-C02` y `LQ-C04`) |
| Prueba en verde | `tax/golden/AssessmentGoldenFixturesTest`: los quince casos y su fuente, sus claves esperadas, las entradas inferidas, las variantes dañadas del esquema, las mutaciones sobre los casos reales y que el `@Disabled` cite su *issue* |
| Adaptador | `tax/golden/AssessmentGoldenEngine` (la interfaz, con la frontera en su Javadoc), `NotImplementedAssessmentEngine` (lanza `GoldenEngineNotImplementedException`) y `AssessmentGoldenEngines.current()`, el único punto que elige el adaptador |
| Entrada y resultado | `AssessmentGoldenInput` y `AssessmentGoldenResult`, *records* con `BigDecimal`, `LocalDate`, `Integer` y `Boolean`; `AssessmentGoldenInputs` convierte un caso en su entrada y `AssessmentGoldenRules` comprueba lo que el esquema no expresa |

### Casos

La columna «Fuente» es el documento y la fila; «Códigos» son los que cita el campo `rules` del *fixture*. Cada caso compara solo las claves de `expected`, que son las columnas con valor en el documento.

| Caso | Fuente | Códigos | Compara |
|---|---|---|---|
| L1, L2 | Doc. 02 §B.9, filas 1 y 2 | `006-02` | tarifa, meses y bruto |
| L3 | Doc. 02 §B.9, fila 3 | `CA-L04` | base, tarifa, meses y bruto |
| L4 | Doc. 02 §B.9, fila 4 | `CA-L03` | base, tarifa, meses y bruto |
| L5 | Doc. 02 §B.9, fila 5 | `CA-L01` | tarifa, meses y bruto |
| L6 | Doc. 02 §11 | `CA-L02` | tarifa |
| L7, L8, L10, L11, L12 | Doc. 03 §9.3, filas 1, 2, 4, 5 y 6 | `LQ-C01`, `LQ-C02`, `LQ-C04` | la fila completa: base, tarifa, meses, bruto, neto, descuento efectivo, valor con descuento y fecha límite del descuento |
| L9 | Doc. 03 §9.3, fila 3 | `CA-L04` y los tres `LQ-C0x` | como L7 |
| L13 | Doc. 03 §9.3, fila 7 | `CA-L03` y los tres `LQ-C0x` | base, tarifa, meses, bruto, neto, `discount_applies: false` y plazo sin intereses (31‑dic‑2026) |
| L14, L15 | Doc. 03 §9.7, filas 1 y 3 | `006-14` | solo las fechas del calendario: límite del descuento (o que no aplica), límite sin intereses y mora desde |

Una celda vacía del documento es una clave ausente. Los montos finales se comparan al peso y el bruto al centavo con `HALF_UP`. L4 no tiene neto en el documento, y L6 solo tiene tarifa.

### Supuestos y entradas inferidas

- `MODALIDAD_B_HIPOTETICA`: L7 a L9 y L11 a L13. La nota del Doc. 03 §9.3 dice que, con A + C, el municipio solo liquida el internado (L10); las demás filas son el cálculo si se activa el convenio con Nariño. L10 no lo lleva. L1 a L6 son «régimen nacional» y la competencia está fuera de la frontera.
- `ENTRADA_INFERIDA`: el caso trae una entrada que el documento no da; cada una está en `inferred_inputs` con su motivo. Son la categoría (`PARTICULAR`, `MOTO` o `ELECTRICO`) en L1 a L13: por el mapeo `categorias` de la configuración, salvo en L5 y L6, donde el documento solo da un borde de tramo y `PARTICULAR` es la categoría con más de un tramo; `new_vehicle: false` en L3, L5, L6 y L7 a L12, que el documento no dice (L1 y L2 son «usado», y los vehículos nuevos lo dicen); la forma de la base de L1, L2, L5 y L6 (`taxable_base`, la «Base» del documento, sin origen) y de L3 (`table_value`); y la vigencia de L6. L14 y L15 no llevan ninguna: no traen base ni categoría.
- La causación de un vehículo usado no está en el *fixture*: la da la configuración. Los vehículos nuevos traen su `accrual_date`.

### `depends_on`

La lista no es exhaustiva: son los valores `POR_CONFIRMAR` de la configuración que la revisión del plan identificó. Si cambia alguno de ellos, estos son los casos que hay que revisar: `tarifas.MOTO` (L11), `causacion.proporcionalidad_nuevos` (L4 y L13; Doc. 02 §12, pendiente 2), `redondeo.empate` (L8, cuyo bruto cae en $500 exactos) y `categorias` y `sujecion` (L11; los dos están `POR_CONFIRMAR`).

### La frontera de `tax`

El adaptador ejerce el Doc. 02 §B.6 (ajustes de base y multiplicador de tablas), §B.7 (tarifa y meses), §B.8 (bruto, neto redondeado y descuento) y el calendario del Doc. 03 §9.6 y §9.7. **No** evalúa los prerrequisitos y la sujeción (§B.3; por ejemplo, el cilindraje de la moto de L11), la competencia y el universo de la modalidad, ni la homologación: la categoría, el origen de la base y los ajustes son entradas. El adaptador real de la Fase 2 solo **traduce** la entrada de un caso al puerto de aplicación de `tax` y carga la configuración con el mismo lector y modelo que producción; no elige el tramo, no multiplica ni redondea por su cuenta. Si lo hiciera, estas pruebas probarían el adaptador y no el motor.

### Ejecutarlas en rojo

El comando de «Ejecutarlas en rojo» (sección general de este README) selecciona `AssessmentGoldenTest`: sus 31 invocaciones (una por caso y por código que cita) fallan con `GoldenEngineNotImplementedException`. Para ponerlas en verde, sigue «Ponerlas en verde (Fases 2 y 4)» y cierra el *issue* #171. Mientras `AssessmentGoldenEngines.current()` devuelva `NotImplementedAssessmentEngine`, `AssessmentGoldenFixturesTest` comprueba en verde que cada una de las invocaciones falla por esa excepción; cuando devuelva el motor real, comprueba que el `@Disabled` ya no esté en la clase.

**Cuidado con la granularidad.** Un método agrupa todos los casos de un código: `LQ-C01`, `LQ-C02` y `LQ-C04` cubren L7 a L13. Si un solo caso falla en la Fase 2 (por ejemplo, L11 por un valor `POR_CONFIRMAR`), no desactives el método entero sin decirlo en el *issue*: apagarías también los casos que pasan. Lo preferible es corregir el motor o el documento; si hace falta apartar un caso, que sea por una decisión explícita de la persona.

## Cuenta corriente (`ledger`)

_La completa el PR de los casos de la cuenta corriente (feature 006, B2): archivos, casos C1 a C9, eventos, supuestos y número del *issue*._
