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

_La completa el PR de los casos de liquidación (feature 006, B1b): archivos, casos L1 a L15, supuestos y número del *issue*._

## Cuenta corriente (`ledger`)

_La completa el PR de los casos de la cuenta corriente (feature 006, B2): archivos, casos C1 a C9, eventos, supuestos y número del *issue*._
