# 004 · Esquema de la configuración de jurisdicción — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** aprobado (H2) — 2026-11-08, por el responsable. Implementado hasta el paso 3; los pasos 4 y 5 pasaron a la feature 1.5.9 al cerrar (`spec.md`, «Decisión del cierre»)
_(borrador → aprobado (H2))_

## Enfoque

El esquema es un **contrato del backend** (`AGENTS.md` §3, nivel 6; `docs/arquitectura/01` §5.3 y §14.1, IN‑11), así que se trata como los contratos OpenAPI del ADR 0027, con tres diferencias: es un JSON Schema, cada versión es un **archivo inmutable** (no hay comparador de compatibilidad: una versión nueva es un archivo nuevo) y se publica con otro prefijo de *release*.

- **Dónde vive:** `backend/contracts/jsonschema/config-jurisdiccion/1.0.json` (la ruta que ya fija el doc. 01 §5.3 y el árbol del §14). Draft 2020‑12.
- **Quién lo prueba:** el backend, con pruebas de JUnit sobre sus configuraciones de prueba, una meta‑prueba del propio esquema y pruebas de mutación sobre cada nodo (doc. 04 §2: «CI del backend para sus configuraciones de prueba y el esquema»). Corren en `backend · verify`, sin cambiar ese *workflow*.
- **Cómo se publica:** al integrar en `main`, `backend-main` publica cada versión nueva como *release* `jsonschema-config-jurisdiccion-v1.0`, con el esquema y su SHA‑256, con el protocolo del ADR 0027 (borrador, subir, verificar descargado, publicar; una versión publicada no cambia).
- **Quién valida los anexos:** la raíz, con un *script* propio en `tools/config-jurisdiccion/`, contra el esquema **publicado** (IN‑11): lo descarga por URL fija y comprueba el SHA‑256 que fija la raíz, como hace el frontend con `api-spec/versions.json`. Corre en un trabajo nuevo de `sistema-pr`, en todos los PR, y **juzga el *script* de la rama base**, no el del PR (como el comparador del ADR 0027).
- **Validadores:** `networknt json-schema-validator` en el backend (Java) y `jsonschema` de Python en la raíz. Son dependencias nuevas: **ADR 0032**, propuesto en este mismo PR, que se acepta con la H2.

Se mantiene la frontera de la spec: el esquema solo mira la forma (CJ01). No hay código de producción: el validador CJ02–CJ14, la carga y la publicación de la configuración en la aplicación son de la feature 1.5.9.

### Subconjunto portable de YAML (reglas de carga, iguales en el backend y en la raíz)

El esquema valida un árbol JSON; antes hay que convertir el YAML. PyYAML y SnakeYAML son YAML 1.1; `snakeyaml-engine`, que llega con el `jackson-dataformat-yaml` de Jackson 3, es YAML 1.2, y Jackson tiene además su propia lectura de los escalares (guiones bajos, hexadecimales…). Para que un archivo signifique lo mismo en todos, la carga **rechaza todo lo que YAML 1.1 y 1.2 leen distinto o que no hace falta**, antes de construir el árbol, leyendo los eventos del analizador:

1. **Sin etiquetas, anclas, alias ni fusiones:** ni `!!python/…`, `!!binary`, `!foo`, ni `&`, `*`, ni la clave `<<` (004‑10).
2. **Un solo documento**, y **claves de texto, sin repetir** en el mismo objeto (004‑10).
3. **Escalares sin comillas, solo de estas formas:**
   - `true`, `false` y `null`, en minúsculas;
   - enteros sin ceros a la izquierda ni signo `+` (`-?(0|[1-9][0-9]*)`);
   - decimales con punto, sin exponente (`-?(0|[1-9][0-9]*)\.[0-9]+`);
   - fechas `AAAA-MM-DD`, **que quedan como texto**;
   - cualquier otro texto, siempre que ninguno de los lectores de YAML del proyecto (1.1, 1.2 y Jackson) lo lea como otro tipo.

   Se rechazan `yes`, `no`, `on`, `off`, `y`, `n`, `~`, `True`, `NULL` y variantes, octales, hexadecimales, sexagesimales (`1:30`), exponentes y `.nan` o `.inf`. Hoy los dos anexos solo usan las formas permitidas (comprobado: 1.131 identificadores, 76 enteros, 47 booleanos, 32 decimales, 23 nulos, 14 fechas, 43 textos entre comillas y 32 textos libres).
4. **Sin caracteres de control** (U+0000 a U+001F) en claves ni valores: un salto de línea final no puede colarse en un código, y la salida del validador no puede inyectar órdenes en el registro de GitHub Actions.
5. **Números exactos:** los enteros se construyen como `BigInteger` en Java e `int` en Python, y los decimales como `BigDecimal` y `Decimal`, nunca como `double` (ADR 0016): `1.0000000000000001` es mayor que 1 en los dos lados. Sin normalizar (`1000` no pasa a `1E+3`). Un decimal no es entero: `multiplo: 1000.0` es inválido en los dos lados (en Java se configura el validador para que no lo acepte como `integer`, y en Python se usa un verificador de tipos que acepta `Decimal` como `number`).

Los casos comunes son **un solo archivo**, `contracts/jsonschema/config-jurisdiccion/1.0.casos.json`: cada caso tiene un texto YAML y su resultado esperado, de carga (las reglas de arriba, más `1_000`, `0x1F`, `0o17`, `.5`, `2026-1-1` y un valor vacío) y de validación (`1000.0` en un campo entero, `1.00` en una fracción, una clave desconocida…). Se publica en la misma *release* que el esquema, y la raíz fija su SHA‑256 en `esquemas.json`: sus pruebas corren la copia publicada, así que no hay dos copias que puedan separarse. El cargador de producción de la 1.5.9 debe pasar los mismos casos.

### Contenido del esquema 1.0

**Regla general de la forma (decisiones 3 y 4 de la H1):** cada objeto lleva `additionalProperties: false`; una clave que está en los dos YAML (en todos los elementos, si es una lista) es **obligatoria** en su objeto; una que trae solo uno es **opcional**. **Excepciones**, que la regla no calcula: en la raíz solo son obligatorias las seis secciones de la decisión 2; en `competencia.modalidades` ninguna modalidad es obligatoria, y `activa` lo es en las cuatro, también en `ADMINISTRACION_DIRECTA`, que solo trae la plantilla.

Subconjunto portable de Draft 2020‑12, para que Java y Python den lo mismo: sin `format`, `$dynamicRef`, `unevaluatedProperties` ni `$ref` externos (solo `#/$defs/…`); el validador de Java se configura sin carga remota. `$id` = `urn:liv:jsonschema:config-jurisdiccion:1.0`, para no atarlo a la URL de GitHub, que cambia al separar el backend. Todo `pattern` va con `type: string`.

| Regla | Cómo se escribe | Criterio |
|---|---|---|
| Secciones obligatorias | `required` en la raíz: `esquema_version`, `jurisdiccion`, `competencia`, `vigencia`, `normas`, `distribucion_recaudo`. Las otras 11 secciones de Doc. 03 §5.2 y `recaudo` se declaran sin exigirlas | 004‑01 |
| Claves dentro de cada sección | Regla general. Son opcionales, entre otras, por estar en un solo YAML o no en todos los elementos de su lista: `jurisdiccion.codigo_divipola_8`, `departamento`, `zona_frontera`; `redondeo.empate`, `aplica_a`; `base_gravable.ajustes[].aplica_solo_dane`, `sobre`; `tarifas[].norma`, `tarifa_maxima`; `normas[].fecha`, `autoridad`; `sujecion.no_gravados[].clases`, `cilindraje_max`, `excepto_si`; `catalogo_ministerio.tablas[].unidad_potencia`, `unidad_potencia_electricos`; y la modalidad `ADMINISTRACION_DIRECTA`, que solo trae la plantilla. La lista completa sale de comparar los dos YAML y la fija la prueba de mutación | 004‑01, 004‑02 |
| `estado` | `$defs/estado`: `enum [VIGENTE, POR_CONFIRMAR, PENDIENTE]`. Obligatorio en los 21 objetos que lo traen en los dos YAML: `jurisdiccion`, `competencia`, `normas[]`, `base_gravable.catalogo_ministerio`, `base_gravable.vehiculo_nuevo`, las 3 reglas de `base_gravable.reglas_ubicacion` que lo traen, `base_gravable.ajustes[]`, `sujecion`, `sujecion.servicio_publico`, `categorias`, `tarifas[]`, `conservacion_tarifa`, `causacion.proporcionalidad_nuevos`, `calendario`, `beneficios[]`, `redondeo`, `distribucion_recaudo`, `recaudo`, `reportes.sibga_mensual` | 004‑03 |
| Modalidades | `competencia.modalidades`: las cuatro claves de Doc. 03 §9.5 como `properties`, `additionalProperties: false`, ninguna obligatoria; en cada una, `activa` booleano obligatorio | 004‑04 |
| Listas cerradas | Solo las que el YAML documenta completas: `jurisdiccion.tipo` (4); `base_gravable.vehiculo_nuevo.fuente_base` (2); `beneficios[].tipo` (3); `redondeo.metodo` (3); `calendario.vehiculos_nuevos.plazo_pago_sin_intereses.modo` (3) y `descuento.modo` (2); `recaudo.descuento_pago_parcial` (2). `normas[].tipo` **no** se cierra: el comentario solo da las opciones del estatuto territorial y el ERD no tiene lista | 004‑05 |
| Clases de texto | Cada campo de texto tiene una de estas clases, y la tabla completa va en el esquema: **código** `^[A-Z][A-Z0-9_]*$` sin `PENDIENTE` ni `POR_CONFIRMAR` (`universo`, `beneficiario`, `tratamiento`, `momento`, `inicio_mora`, las referencias `norma`, `normas[].tipo`…); **identificador** `^[a-z][a-z0-9_]*$` (`aplica_cuando`, `linea_no_encontrada`, `contenido`, `soportes[]`, `atributos[]`); **ruta** `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$` (`excepto_si`); **texto técnico**, que lee la máquina, sin `PENDIENTE` (`columna_anio_base`, `usar_columna`, `formula_meses`); **texto libre** con `minLength: 1`, que admite `PENDIENTE` (`jurisdiccion.nombre`, `departamento`, `administracion.nombre`, `nit`, `normas[].numero`, `descripcion`, `autoridad`, `convenio.numero`, `motos_electricas_criterio_sujecion` y `recaudo.archivo_banco.formato`). Como cualquier texto, un texto libre también admite `POR_CONFIRMAR`, que es lo que hoy dice `formato` | 004‑05, 004‑08 |
| Tasas y porcentajes | `$defs/fraccion`: número entre 0 y 1, o `PENDIENTE`. En `tarifa`, `tarifa_maxima`, `porcentaje`, `tasa` y `descuento_pronto_pago.tarifa`. Los factores de ajuste (`factor: 1.10`) no son fracciones: número mayor que 0 | 004‑06 |
| Montos y enteros | `$defs/monto`: número ≥ 0; enteros (`multiplicador_tablas`, `multiplo`, días, umbrales, `anio`, `version_config`): entero ≥ 0. Sin `PENDIENTE` | 004‑06, 004‑08 |
| `null` | Solo en los campos que lo usan hoy, uno por uno: `hasta_inclusivo`, `tope_pesos`, `anios_desde_matricula`, `departamento_padre_dane`, `convenio.numero`, `fecha_inicio` y `fecha_fin`, y los `dias` y `fecha` de `vehiculos_nuevos`. No en `$defs/fecha` ni en `$defs/monto` | CJ01 |
| Fechas | `$defs/fecha`: `^[0-9]{4}-(0[1-9]\|1[0-2])-(0[1-9]\|[12][0-9]\|3[01])$`, o `PENDIENTE`. `causacion.general`: `^(0[1-9]\|1[0-2])-(0[1-9]\|[12][0-9]\|3[01])$`. Una fecha que no existe en el calendario (30 de febrero) la detecta el validador de la 1.5.9 | 004‑07, 004‑08 |
| DANE | `^[0-9]{2}([0-9]{3})?$` o `PENDIENTE`; `codigo_divipola_8`: 8 dígitos; `departamento_padre_dane`: 2 dígitos, `null` o `PENDIENTE`; `aplica_solo_dane[]`: 2 o 5 dígitos | 004‑07, 004‑08 |
| `PENDIENTE` | Se acepta en fechas, fracciones, códigos DANE y los textos libres de la fila anterior. `motos_electricas_criterio_sujecion` es texto libre porque su forma aún no está definida (Doc. 03 §8, pendiente 5). Nunca en códigos, identificadores, rutas, textos técnicos, montos, enteros, booleanos ni listas cerradas | 004‑08 |
| Versión | `esquema_version`: `const "1.0"`. Además, las pruebas y el validador de la raíz eligen el esquema por la versión que declara el archivo **antes** de validar: una versión que no existe es un error, nunca se valida contra otra | 004‑09 |
| Sin valores tributarios | Lista blanca de palabras clave y de valores: solo 0 y 1 como límites numéricos; `const` solo en `esquema_version`; `enum` solo en las rutas exactas de `estado` y de las listas cerradas de 004‑05; `pattern` solo con los textos exactos de las clases de esta tabla; sin `default`, `examples`, `multipleOf`, `maxItems` ni `minItems` mayor que 0 | 004‑11 |

Las referencias a normas son códigos. Que existan en `normas` es CJ03, del validador.

### Versiones

- `esquema_version` tiene la forma `MAYOR.MENOR`, como ya dicen los dos YAML (`"1.0"`), y nombra el archivo: `1.0.json`.
- Cualquier cambio es una versión nueva, en un archivo nuevo (004‑12). Cada versión acepta solo su propio `esquema_version`, así que distinguir menor de mayor **solo informa** a quien adopta la versión nueva (si su YAML necesita cambios además del número). Lo revisa una persona en el PR.
- Cada YAML se valida contra la versión que declara. Adoptar una versión nueva en la raíz son dos PR: primero se fija su SHA‑256 en `esquemas.json` (el juez de la base no conoce la versión nueva hasta que esté integrada), y después se migran los anexos, como cambio del documento funcional. Esto reemplaza el «migrar en el mismo cambio» del doc. 01 §5.3, que obligaría a tocar `backend/` y `docs/` en un PR (hallazgo 5 de la spec).

## Implementación

> **Al cerrar** (`spec.md`, «Decisión del cierre»): se implementaron los pasos 1 a 3 y el 6. Todo lo que este plan dice del validador de la raíz, de sus *checks* y de la ruta crítica `tools/config-jurisdiccion/**` (pasos 4 y 5, y las menciones en «Enfoque», «Datos, API y seguridad» y «Riesgos») es diseño de partida para la feature 1.5.9, no algo hecho.

En este orden, porque cada paso usa lo que integró el anterior. Un PR por paso (`AGENTS.md` §4.6; ADR 0027: *scripts* y *workflows* en PR separados).

1. **Backend · el esquema y sus pruebas** (`backend/`, rama `feat/004-esquema-backend`).
   - **Primero, en rojo, la carga:** `SafeYamlLoader` (solo en pruebas, con SnakeYAML) y `SafeYamlLoaderTest` sobre el juego común de casos de carga. Si SnakeYAML no permite alguna regla, se detiene aquí y se replantea antes de escribir el esquema.
   - **En rojo, el esquema:** en `src/test/java/co/com/sapenlinea/liv/contracts/`:
     - `JurisdictionConfigSchemaTest`: un caso por criterio (004‑01 a 004‑11) con su código en el `@DisplayName`; los inválidos parten de una configuración válida, cambian un solo nodo y comprueban la ruta del error;
     - `JurisdictionConfigSchemaMutationTest`: parametrizada sobre **cada nodo** de las dos configuraciones de prueba, con el oráculo de la regla general y sus excepciones: en cada objeto, una clave desconocida da error; quitar cada clave obligatoria da error con su ruta, y quitar cada opcional sigue siendo válido; en cada objeto con `estado`, quitarlo o poner `APROBADO` da error; en cada campo que no admite `PENDIENTE`, ese valor da error; cada `null` fuera de la lista da error;
     - `JurisdictionConfigCommonCasesTest`: corre los casos comunes de `1.0.casos.json`;
     - `JsonSchemaFilesTest` (meta‑prueba): cada archivo de `contracts/jsonschema/<nombre>/` se llama `MAYOR.MENOR.json` o `MAYOR.MENOR.casos.json`, cada esquema tiene sus casos, es válido contra el metaesquema de Draft 2020‑12, su `$id` nombra la carpeta y la versión, su `esquema_version` tiene esa `const`, solo usa las palabras clave de la lista blanca, todo objeto lleva `additionalProperties: false`, todo `pattern` va con `type: string`, todo `$ref` es interno y los límites y constantes cumplen 004‑11.
   - Configuraciones de prueba: copias de los dos anexos en `src/test/resources/contracts/jsonschema/config-jurisdiccion/`. Son una **instantánea**: el backend no lee `../docs` (IN‑01), y la prueba de CJ01 sobre los anexos reales es la de la raíz (paso 5).
   - **Verde:** `contracts/jsonschema/config-jurisdiccion/1.0.json` y `1.0.casos.json`.
   - `pom.xml`: `com.networknt:json-schema-validator` 3.0.9 en `test` (ADR 0032). SnakeYAML **no** se declara: ya llega con `spring-boot-starter`, en `compile`, porque Spring Boot lo necesita para leer `application.yml`, y declararlo en `test` lo sacaría del empaquetado. `./mvnw dependency:tree` lo confirma en el PR.
   - `README.md` (sección del esquema) y `CHANGELOG.md`.
2. **Backend · inmutabilidad y publicación** (`backend/`, rama `feat/004-esquema-publicacion`).
   - `contracts/scripts/jsonschema.sh` con dos órdenes:
     - `check <referencia-base>`: falla si una versión (esquema o casos) que existe en la base cambió de contenido o se quitó; si hay un archivo fuera de la forma `<nombre>/<MAYOR.MENOR>.json` o `<nombre>/<MAYOR.MENOR>.casos.json`, un enlace simbólico o un nombre repetido entre `contracts/jsonschema/` y `contracts/openapi/` (el nombre nombra la *release*); y si el árbol del PR ya no tiene `jsonschema.sh` o sus pruebas mientras exista `contracts/jsonschema/`;
     - `publish`: para cada versión, *release* `jsonschema-<nombre>-v<MAYOR.MENOR>` con `<nombre>-<MAYOR.MENOR>.json`, `<nombre>-<MAYOR.MENOR>.casos.json` y el `.sha256` de cada uno, del commit `GITHUB_SHA`. El mismo protocolo de `publish.sh`: borrador, subir, verificar descargado, publicar, no marcar como la más reciente, se puede volver a correr, y si ya existe con otro contenido, falla.
   - Pruebas en `contracts/scripts/test/`, con el `gh` falso que ya existe; `run.sh` las incluye, y falla si faltan.
   - `README.md` y `CHANGELOG.md`.
3. **CI de contratos** (`.github/`, rama `ci/004-esquema-contratos`; ruta crítica, aprobación de Jorge).
   - `backend-contracts-pr.yml`, trabajo «contratos (Q08)», un paso nuevo con el *script* de la base (el juez), solo en la pierna `backend` de la matriz:
     - si la base tiene `contracts/jsonschema/`, corre `jsonschema.sh check HEAD^1` de la base. Si la base no tiene el *script* y el PR sí, juzga el del PR, que es la salida para restaurarlo, como hace el comparador; si no lo tiene ninguno, **falla**: nunca pasa por omisión;
     - si la base no tiene esa carpeta, no hay nada que proteger y el paso lo dice;
     - en la pierna `backend/bff` no corre: el BFF no tiene esquemas.
   - El trabajo de pruebas de los *scripts* ya corre `run.sh`, que incluye las de `jsonschema.sh`.
   - `backend-main.yml`: `jsonschema.sh publish` en un trabajo propio, sin `needs`, para que un fallo al publicar un contrato OpenAPI no deje el esquema sin publicar; con `if: github.ref == 'refs/heads/main'`, para que un `workflow_dispatch` desde otra rama no publique un borrador como versión inmutable; y con la misma regla de falla cerrada. El *workflow* se dispara por `backend/contracts/**` y por su propio archivo, así que al integrar este PR se publica la versión 1.0.
   - `aplica.sh` y los nombres de los *checks* obligatorios no cambian.
4. **Raíz · validador de los anexos** (`tools/`, rama `feat/004-validador-anexos`; después de que exista la *release* 1.0). **Trasladado a la feature 1.5.9** (`spec.md`, «Decisión del cierre»); se conserva aquí como diseño de partida.
   - `AGENTS.md` §4.3: `tools/config-jurisdiccion/**` en la lista de rutas críticas (decisión 3 de la H2, anulada al cerrar).
   - `tools/config-jurisdiccion/validar.py`:
     - valida cada `docs/funcional/anexos/config_jurisdiccion_*.yaml`; falla si no encuentra ninguno y si en `anexos/` hay un `.yml` o `.yaml` que no siga la convención `config_jurisdiccion_<DANE o plantilla>_<AÑO>.yaml` (`AGENTS.md` §5.2), para que ningún anexo se salga de la validación por su nombre;
     - carga con el subconjunto portable; la `esquema_version` tiene que ser texto y estar en `esquemas.json` **antes** de construir la URL (004‑09);
     - descarga el esquema de la *release* (sin *token*: el repositorio es público), comprueba el SHA‑256 de `esquemas.json` y valida;
     - escribe cada error como `CJ01 <archivo> <ruta>: <motivo>`, con la ruta de la clave también en `additionalProperties` (Python da la del objeto padre) y los valores del YAML escapados, de modo que ninguna línea pueda empezar con `::`. Sale con 1 si hay algún error.
   - `esquemas.json` con la versión 1.0 y el SHA‑256 del esquema y de sus casos publicados.
   - `.python-version` con la versión de Python (3.13), que lee `setup-python`: va junto a `requirements.txt`, para que el juez use siempre la versión y los *hashes* de la misma rama.
   - `requirements.txt` generado con `pip-compile --generate-hashes` (procedimiento en el README): `jsonschema` 4.26.0 y PyYAML 6.0.3 con todas sus dependencias, versiones exactas, *hashes* de las ruedas de Linux y Windows para la versión de Python fijada, e instalación con `--require-hashes --only-binary=:all:`.
   - Pruebas con `unittest` (biblioteca estándar) en `tools/config-jurisdiccion/test/`, sin red (el esquema se toma de una carpeta local con una variable de entorno):
     - los casos comunes publicados (`1.0.casos.json`), comprobando antes su SHA‑256;
     - casos de esquema violado (clave desconocida, valor fuera de lista, fracción mayor que 1), con el formato exacto de la salida y el código de salida;
     - versión desconocida, versión numérica en lugar de texto, SHA‑256 que no coincide, carpeta sin anexos e intento de inyección en la salida.
   - `README.md` de la carpeta: cómo correrlo en local, cómo regenerar `requirements.txt` y cómo adoptar una versión nueva del esquema.
5. **CI de la raíz** (`.github/`, rama `ci/004-validar-anexos`; aprobación de Jorge). **Trasladado a la feature 1.5.9**, con el paso 4.
   - `sistema-pr.yml`, dos trabajos que corren en **todos** los PR, como los otros de `sistema`, sin `aplica.sh`:
     - `sistema · configuraciones de jurisdicción` (el juez): *checkout* con `fetch-depth: 2`; toma `tools/config-jurisdiccion/` de `HEAD^1` (la rama base) en un *worktree* aparte; `actions/setup-python` fijado por SHA, con el `.python-version` de la base y **sin caché**; instala las dependencias de la base y corre `python -I` desde ese *worktree*, para que un módulo que agregue el PR no tape uno de la base; valida los anexos **del PR**. Falla si el árbol del PR ya no tiene el validador. Si la base no lo tiene y el PR sí, juzga el del PR (la salida para restaurarlo); si no lo tiene ninguno, falla;
     - `sistema · validador de configuraciones del PR`: con lo del PR (su `.python-version`, su `requirements.txt` y su `esquemas.json`), corre las pruebas del validador y **también valida los anexos**, con red. Exigir los dos *checks* impide integrar un validador, un SHA‑256 o unos *hashes* rotos, que después, como juez, dejarían en rojo todos los PR;
     - una guarda como la de `backend-contracts-pr`: un PR que cambia `sistema-pr.yml` no cambia a la vez `tools/config-jurisdiccion/` ni `docs/funcional/anexos/`.
   - Tras integrarlo, **Jorge agrega los dos *checks* a la protección de `main`** (*ruleset* `main`), como en la T5 de la feature 003. Sus nombres no cambian sin cambiar el *ruleset* en el mismo momento.
   - Demostración en PR de prueba, como en la 003, cerrados sin integrar:
     - un anexo con una clave mal escrita deja el PR en `BLOCKED` (004‑13);
     - un PR que cambia `1.0.json` deja `backend · contratos` en rojo (004‑12);
     - un PR que debilita `validar.py` y rompe un anexo sigue en rojo, porque juzga la base.
6. **Cierre** (raíz, documentación):
   - criterios de la spec marcados con su prueba;
   - `docs/arquitectura/04` §9: lista de *checks* obligatorios;
   - `docs/arquitectura/01` §5.3: versiones inmutables en lugar de «migrar en el mismo cambio», y publicación al integrar en lugar de «con cada versión del backend»;
   - `ESTADO.md` (F0‑10 hecho) y `roadmap.md`.

   Los cambios de los documentos de arquitectura los aprueba Jorge en ese PR.

## Datos, API y seguridad

- **Migraciones:** ninguna. La tabla de la configuración publicada es de la feature 1.5.9, en el esquema `jurisdiction` (decisión 1 de la H1).
- **Contrato OpenAPI:** ninguno. El contrato nuevo es el JSON Schema.
- **Permisos:** no hay casos de uso. Los trabajos nuevos piden `contents: read`, salvo la publicación de `backend-main`, que ya tiene `contents: write` para las *releases*.
- **Amenazas** (`docs/arquitectura/02`):
  - *YAML hostil* (bomba de alias, etiquetas que construyen objetos, fusiones, escalares ambiguos): subconjunto portable, rechazado antes de construir el árbol (004‑10). Hoy los YAML son de documentación, pero las mismas reglas valdrán para los archivos que suban los usuarios en la 1.5.9.
  - *Clave repetida que pisa a otra* (p. ej. una segunda `tarifa`): se rechaza (004‑10).
  - *Esquema cambiado en silencio*:
    - en el PR, el juez es el *script* de la base y falla si falta;
    - en la publicación, una *release* con otro contenido falla;
    - en la raíz, el SHA‑256 está fijado en `esquemas.json`, y el validador que juzga es el de la base, así que un PR no puede cambiar a la vez el pin y el anexo.
  - *Validador debilitado por un PR*: juzga el de la base (paso 5). Además, se propone hacer de `tools/config-jurisdiccion/**` una ruta crítica (`AGENTS.md` §4.3), lo que decide Jorge en la H2 (aprobado en la H2 y anulado al cerrar).
  - *Inyección en el registro de Actions* desde valores del YAML: caracteres de control rechazados en la carga y salida escapada.
  - *Cadena de suministro*: versiones exactas; en Python, `--require-hashes --only-binary=:all:`; en Maven, las del repositorio central como el resto del backend. Una sola acción nueva en los *workflows*, `actions/setup-python`, de GitHub y fijada por SHA, sin caché en el juez.
  - *Un PR que desactiva su propia validación* cambiando el *workflow*: riesgo ya declarado en la feature 003; la barrera es la revisión humana de `.github/`.

## Decisiones

- **El esquema es un contrato del backend publicado como *release*, y la raíz valida contra lo publicado** — lo piden el doc. 01 §5.3 y §14.1 (IN‑11). Se descartó que la raíz lea `backend/contracts/` directamente: al separar el backend en su repositorio, esa ruta deja de existir.
- **Versiones inmutables, un archivo por versión, sin comparador de compatibilidad** — cada YAML declara su versión, y una configuración publicada debe poder volver a validarse con la misma. Se descartó un comparador de JSON Schema: no hay uno maduro en el *stack*, y la 1.0 es la única versión.
- **Prefijo de *release* `jsonschema-`, no `contract-`** — `publish.sh` lista las *releases* `contract-<nombre>-v*` para juzgar cada contrato OpenAPI; un prefijo propio evita que se mezclen.
- **Dos validadores (Java y Python) con un subconjunto portable del estándar y de YAML** — el backend necesita el suyo para la 1.5.9, y la raíz no puede usar el *build* del backend (IN‑02, IN‑08). Detalle y alternativas en el ADR 0032.
- **En la raíz juzga el validador de la base** — es la regla del ADR 0027 para los contratos: un PR no cambia la regla con la que se lo juzga.
- **Sin código de producción en esta feature** — el cargador y las dependencias van en `test`. La 1.5.9 decide el cargador de producción (el doc. 00 §5 propone Jackson YAML) y debe pasar el juego común de casos de carga.
- **Los anexos no se tocan** (decisión 4 de la H1): el esquema se escribe para que los dos sean válidos tal como están.
- **Las listas cerradas son solo las que el YAML documenta completas** — `normas[].tipo` queda abierto, porque cerrarlo inventaría valores (004‑05).

## Riesgos

- **Java y Python no dan lo mismo** — subconjunto portable del estándar y de YAML, números exactos, y el mismo juego de casos de carga en los dos lados. Si aun así difieren, manda el backend, que es el dueño, y se corrige el *script* de la raíz.
- **SnakeYAML o PyYAML no permiten alguna regla de carga** — es lo primero que se prueba en el paso 1, en rojo; si falla, se detiene y se replantea antes de escribir el esquema.
- **La raíz depende de la red para validar** (descarga la *release*) — si GitHub no responde, el *check* falla: falla cerrado, se reintenta. Si alguien borra la *release* a mano, todos los PR quedan en rojo hasta volver a publicarla con `workflow_dispatch` de `backend-main`; el SHA‑256 fijado impide que una *release* reemplazada pase. Activar las *releases* inmutables sigue siendo la decisión pendiente del ADR 0027.
- **`networknt` 3.x usa Jackson 3** — es el mismo Jackson de Spring Boot 4; si su versión no coincide con la que gestiona Spring Boot, manda la de Spring Boot y se comprueba con `./mvnw verify`.
- **Primera publicación** — si `backend-main` falla al integrar el paso 3, se reintenta con «Re-run» o `workflow_dispatch`, como en el ADR 0027. El paso 4 no empieza sin la *release* 1.0.
- **Los *checks* nuevos no quedan obligatorios** — si Jorge no los agrega al *ruleset*, un anexo inválido se puede integrar. Va en el paso 5 y en el cierre. **Al cerrar:** el paso 5 pasó a la feature 1.5.9, así que hasta entonces un anexo inválido se puede integrar.
- **Guardas que solo comprueban que existan** (paso 2) — `jsonschema.sh` comprueba que existan el *script*, `lib.sh`, `run.sh`, `fake-gh.sh` y los escenarios, no su contenido. Un PR que los debilite lo detiene solo la revisión H3. Aceptado por el responsable.
- **Un juez roto en `main` deja en rojo todos los PR** — el *check* del PR con su propio validador (paso 5) lo evita en el caso normal. Queda un caso: dos PR en verde por separado que, integrados uno detrás del otro, dejan un anexo que el validador nuevo rechaza, porque la rama no tiene que estar al día (decisión de la H1 de la feature 003). La salida es un PR que corrige el anexo; si aun así el juez de la base no deja pasar ninguno, Jorge omite el *check* en el *ruleset* para ese PR y lo registra en `ESTADO.md`. Exigir la rama al día o una cola de integración, como recomienda el ADR 0027, lo evitaría.
- **Tamaño del PR del paso 1** — las dos copias de los anexos suman unas 760 líneas de datos; el código y el esquema rondan las 400. Se justifica en el PR (Q12), o se separa la carga (`SafeYamlLoader` y sus casos) en un PR propio si pasa de ahí.

## Decisiones de la implementación

Tomadas por el responsable al revisar cada PR (H3):

- **Paso 1 (PR #141):** acepta las reglas que la revisión agregó al plan (los límites del cargador y el final de los patrones), y el contenido del esquema queda como está, sin restricciones extra. Integrar fija la versión 1.0: cualquier cambio posterior es una versión 1.1.
- **Paso 2 (PR #142):** acepta que las guardas de `jsonschema.sh` solo comprueben que existan el *script* y sus pruebas, no su contenido: un PR que las debilite solo lo detiene la revisión H3 (ver Riesgos). La *release* con archivos de más que pasa la verificación (heredado de `publish.sh`, ADR 0027) se arregla en un PR aparte del backend.
- **Paso 3 (PR #144):** acepta dos desviaciones más estrictas que el plan: `check` corre también si la base no tiene la carpeta pero el PR sí, y con el *script* de respaldo el mismo cambio no puede tocar los esquemas.
- **Observado al integrar el paso 3:** la *release* `jsonschema-config-jurisdiccion-v1.0` quedó marcada como «Latest» en GitHub, aunque se esperaba que no. Se revisa con el arreglo de `publish` del backend.

## Revisión

- [x] **Revisión adversarial independiente del plan, 2026-11-08:** dos revisores ciegos de nivel alto (`liv-revisor-critico`) sobre `spec.md`, este plan y el ADR 0032. Primera ronda: 0 críticos, 12 altos, 12 medios y 6 bajos, con muchos repetidos entre los dos. Todos se corrigieron en el plan y en el ADR:
  - **Confirmados por los dos:**
    - en la raíz, el juez era el *script* del PR: ahora es el de la base (paso 5);
    - la comprobación de inmutabilidad pasaba si faltaba el *script*: ahora falla cerrada (paso 3);
    - el patrón de código aceptaba `PENDIENTE`: ahora hay clases de texto, y los códigos lo excluyen;
    - los `null` de los anexos no cabían: ahora se admiten campo por campo;
    - YAML 1.1 frente a 1.2 y números como `double`: ahora rige un subconjunto portable de YAML, con números exactos y un juego común de casos de carga;
    - las pruebas de Python no tenían ningún caso de esquema violado, y Python da la ruta del objeto padre: se agregaron los casos y la ruta de la clave;
    - la pierna del BFF de la matriz no tiene el *script*: el paso solo corre en `backend`;
    - 004‑11 se probaba a medias: ahora hay una meta‑prueba con lista blanca;
    - patrones laxos (`2026-13-45`, salto de línea final) e inyección en el registro: se corrigieron los patrones y se agregó la regla de caracteres de control;
    - dependencias de Python sin fijar: ahora con `setup-python`, ruedas binarias con *hash* y el procedimiento en el ADR;
    - doc. 01 §5.3 y las copias de los anexos: se agregó el ajuste del §5.3 y la declaración de que las copias son una instantánea.
  - **De uno solo, verificados y corregidos:**
    - `$ref` externos y carga remota;
    - selección de la versión antes de validar (004‑09);
    - campos obligatorios dentro de cada sección: regla general explícita y pruebas de mutación sobre cada nodo;
    - `normas[].tipo` inventaba una lista cerrada: queda abierto;
    - la publicación del esquema dependía del trabajo de los contratos: ahora es un trabajo aparte;
    - la clasificación entre versión menor y mayor no tenía efecto: queda solo informativa;
    - patrones sin `type: string`.
  - **Segunda ronda, 2026-11-08, solo sobre lo corregido** (los mismos dos revisores de nivel alto): 0 críticos, 2 altos, 7 medios y 10 bajos. Los dos revisores confirman que los huecos de la primera ronda quedaron cerrados y que el esquema, tal como lo describe la tabla, acepta los dos anexos. Los dos altos eran problemas nuevos de las correcciones, y se corrigieron:
    - declarar SnakeYAML en `test` lo sacaba del empaquetado y la aplicación no leería `application.yml`: no se declara (paso 1);
    - un validador roto, integrado en verde porque lo juzgaba la base anterior, dejaba en rojo todos los PR, incluido el que lo corrige: ahora hay un segundo *check* con el validador del PR que también valida los anexos, más la salida para restaurar un juez quitado y el procedimiento de recuperación en Riesgos (paso 5).
  - **Medios y bajos de la segunda ronda, corregidos:**
    - enteros como `BigInteger`/`int` y decimales como `BigDecimal`/`Decimal`, con `1000.0` inválido en un campo entero en los dos lados;
    - los casos comunes de carga y validación son un solo archivo publicado con el esquema y fijado por SHA‑256 en la raíz;
    - el oráculo de las pruebas de mutación, con las excepciones de la regla general y la mutación inversa;
    - quitar `jsonschema.sh` falla en el PR que lo quita, y hay salida si la base ya no lo tiene;
    - la publicación solo corre en `main`;
    - texto técnico sin `PENDIENTE` y lista exacta de textos libres que lo admiten;
    - `.python-version` junto a `requirements.txt`;
    - el juez con `fetch-depth: 2`, sin caché y con `python -I`;
    - un anexo con otro nombre hace fallar la validación;
    - la guarda entre `sistema-pr.yml` y lo que valida;
    - la meta‑prueba con las rutas exactas de `enum` y los textos exactos de `pattern`;
    - el riesgo de dos PR integrados seguidos;
    - la redacción sobre YAML 1.2 y Jackson;
    - el ADR con todas las dependencias y licencias.
  - **No hubo tercera ronda** (la *skill* permite dos): como la segunda encontró dos altos, se escalan a Jorge en la H2 con su corrección.
  - **Llevados a la H2** (cambian criterios aprobados en la H1 o reglas de `AGENTS.md`):
    - 004‑08: `PENDIENTE` también se admite en los textos libres que lista la tabla, incluido `motos_electricas_criterio_sujecion`;
    - 004‑05: `normas[].tipo` queda abierto aunque la plantilla documente tres opciones para el estatuto territorial, porque Ipiales usa otras cuatro;
    - 004‑07: la validez real de una fecha (30 de febrero) queda en el validador de la 1.5.9;
    - `tools/config-jurisdiccion/**` pasa a ser ruta crítica (`AGENTS.md` §4.3). Anulada al cerrar (`spec.md`, «Decisión del cierre»).
- [x] **Aprobación H2:** el responsable, el 2026-11-08. Aprobó el plan con los dos altos de la segunda ronda ya corregidos, aceptó el ADR 0032 y los tres ajustes de criterios (004‑05, 004‑07 y 004‑08, en la spec), y decidió que `tools/config-jurisdiccion/**` sea ruta crítica (paso 4).
