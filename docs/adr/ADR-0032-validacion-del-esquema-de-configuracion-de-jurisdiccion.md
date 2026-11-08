# ADR-0032 · Validación y publicación del esquema de la configuración de jurisdicción

- **Estado:** Aceptado
- **Fecha:** 2026-11-08
- **Decisores:** responsable del proyecto, que lo aceptó el 2026-11-08 en la aprobación H2 de la feature 004 (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** feature 004 (`spec/features/004-esquema-configuracion-jurisdiccion/`); `docs/arquitectura/00` §5 (*stack*: «validador JSON Schema») y §8.1 (F0‑10); `docs/arquitectura/01` §5.3 y §14.1 (IN‑02, IN‑08, IN‑11); `docs/arquitectura/04` §2 y §6 (Q09); ADR 0005, ADR 0016, ADR 0027, ADR 0031; `AGENTS.md` §4.5

## Contexto

El entregable F0‑10 pide el esquema JSON de la configuración de jurisdicción, validado en CI contra los dos YAML de `docs/funcional/anexos/` (ADR 0031, condición 3). El doc. 01 §5.3 y §14.1 (IN‑11) ya fijan que el esquema es del backend (`backend/contracts/jsonschema/`), que el backend lo prueba con sus configuraciones y que los ejemplos de la raíz se validan contra el esquema **publicado**. El doc. 00 §5 nombra «Jackson YAML (carga segura) + validador JSON Schema» sin elegir el validador.

Hacen falta dos validadores, porque la raíz no puede usar el *build* del backend (IN‑02, IN‑08): uno en Java para el backend (sus pruebas hoy y la opción 1.5.9 después) y otro en la raíz. Cada uno es una dependencia nueva (`AGENTS.md` §4.5). También hay que decidir cómo se publica el esquema.

## Opciones consideradas

**Validador del backend (Java):**

1. **`com.networknt:json-schema-validator` 3.0.9** (Apache‑2.0).
   - A favor: Draft 2020‑12; la serie 3.x usa Jackson 3, el mismo de Spring Boot 4; es el validador de Java más usado y se mantiene activo.
   - En contra: trae `com.ethlo.time:itu` y `jackson-dataformat-yaml` como dependencias transitivas.
2. **`everit-org/json-schema` o su sucesor `erosb/json-sKema`**. El primero llega solo a Draft 7 y usa `org.json`; el segundo está en Kotlin y es menos usado.
3. **Vert.x JSON Schema o `leadpony/justify`**. Traen su propio modelo de JSON (Vert.x o JSON‑P) en lugar de Jackson.

**Validador de la raíz (anexos):**

1. **Un *script* propio en Python con `jsonschema` 4.26.0 y PyYAML 6.0.3** (MIT los dos), con la versión de Python fijada y las dependencias instaladas con *hash*.
   - A favor: controla la carga del YAML (claves repetidas, alias, etiquetas y fechas como texto) igual que el backend; pocas dependencias; corre igual en local.
   - En contra: es otro validador, que podría no coincidir con el del backend en casos raros.
2. **`check-jsonschema` (CLI de Python).** Más dependencias (`click`, `requests`, `ruamel.yaml`…) y menos control de la carga del YAML.
3. **`ajv-cli` con `npx`.** Sin `package.json` en la raíz (IN‑02) no hay archivo de bloqueo: se descargaría sin versiones fijas de sus dependencias.
4. **El mismo validador de Java, con los *jar* descargados de Maven Central y su SHA‑256 fijado.** Da exactamente lo mismo que el backend, pero obliga a mantener a mano la lista de *jar* y sus dependencias.

**Publicación del esquema:**

1. **Una *release* de GitHub por versión, `jsonschema-<nombre>-v<MAYOR.MENOR>`, con el mismo protocolo del ADR 0027.** URL fija, sin *token*, inmutable por regla.
2. **Que la raíz lea el archivo de `backend/contracts/`.** Deja de funcionar al separar el backend (IN‑11).

## Decisión

- **Backend:** `com.networknt:json-schema-validator` **3.0.9** (Apache‑2.0), en `test` mientras no haya código de producción que lo use; la opción 1.5.9 lo pasa a `compile`, con su feature. Trae `tools.jackson.core:jackson-databind` y `tools.jackson.dataformat:jackson-dataformat-yaml` (con `snakeyaml-engine`), con la versión que gestiona Spring Boot, y `com.ethlo.time:itu` 1.17.0 (Apache‑2.0). Se configura sin carga remota de esquemas y sin aceptar un decimal como `integer`. Para cargar el YAML en las pruebas se usa `org.yaml:snakeyaml` (Apache‑2.0), que ya llega en `compile` con `spring-boot-starter` y **no se declara**: declararlo en `test` lo sacaría del empaquetado, y Spring Boot lo necesita para leer `application.yml`.
- **Raíz:** *script* propio `tools/config-jurisdiccion/validar.py` con Python **3.13**, fijado en `tools/config-jurisdiccion/.python-version` e instalado con `actions/setup-python` (de GitHub, MIT, fijado por SHA, sin caché en el juez), y estas bibliotecas, todas MIT y con versión exacta y *hash* en `requirements.txt`:
  - `jsonschema` **4.26.0** y PyYAML **6.0.3**;
  - sus dependencias `attrs`, `referencing`, `jsonschema-specifications` y `rpds-py`, esta última con código nativo.

  `requirements.txt` se genera con `pip-compile --generate-hashes` (de `pip-tools`, BSD, solo en el equipo de quien lo regenera, no en CI), con las ruedas de Linux (CI) y Windows (local), y se instala con `--require-hashes --only-binary=:all:`.
- **En la raíz juzga el validador de la rama base**, no el del PR, como el comparador del ADR 0027. Un segundo *check* corre el validador del PR, con sus pruebas y sobre los anexos, para que no se integre un validador que se rompe a sí mismo y que después, como juez, dejaría en rojo todos los PR.
- **Un solo comportamiento en los dos lados:**
  - el esquema usa un subconjunto portable de Draft 2020‑12: sin `format`, `$dynamicRef`, `unevaluatedProperties` ni `$ref` externos, y todo `pattern` con `type: string`;
  - las dos cargas de YAML aceptan solo un **subconjunto portable de YAML**, que se lee igual en YAML 1.1 (SnakeYAML, PyYAML) y en YAML 1.2 (`snakeyaml-engine`, Apache‑2.0, que trae Jackson 3) y en la lectura de escalares propia de Jackson: sin etiquetas, anclas, alias ni fusiones; un documento; claves de texto sin repetir; sin caracteres de control; escalares sin comillas solo como `true`, `false`, `null`, enteros y decimales sin exponente, fechas (que quedan como texto) y texto que ninguna de las dos versiones lea como otro tipo;
  - los números se leen exactos: enteros como `BigInteger` e `int`, decimales como `BigDecimal` y `Decimal` (ADR 0016), y un decimal nunca vale como entero;
  - los casos comunes de carga y de validación son un solo archivo por versión (`<MAYOR.MENOR>.casos.json`), publicado con el esquema y fijado por SHA‑256 en la raíz, que el cargador de producción de la 1.5.9 también debe pasar;
  - si aun así difieren, manda el backend, que es el dueño del contrato.
- **Versiones:** `MAYOR.MENOR`, un esquema y un archivo de casos inmutables por versión (`contracts/jsonschema/<nombre>/<MAYOR.MENOR>.json` y `<MAYOR.MENOR>.casos.json`). Cada versión acepta solo su propio `esquema_version`, así que distinguir menor de mayor solo informa a quien adopta la versión nueva; lo revisa una persona.
- **Inmutabilidad en el PR:** `contracts/scripts/jsonschema.sh check`, el de la base (como el juez del ADR 0027), falla si un PR cambia o quita una versión que ya está en `main`. También falla si el PR quita el *script* o sus pruebas. Si la base no tiene el *script* y el PR sí, juzga el del PR (la salida para restaurarlo); si no lo tiene ninguno, falla: nunca pasa por omisión.
- **Publicación:** opción 1. `backend-main` corre `contracts/scripts/jsonschema.sh publish` al integrar, solo desde `main` y en un trabajo aparte del de los contratos OpenAPI. La raíz fija el SHA‑256 de cada versión que usa en `tools/config-jurisdiccion/esquemas.json`.

## Consecuencias

- **Positivas:**
  - F0‑10 queda cubierto con el contrato donde la arquitectura lo puso, y la raíz valida contra lo publicado, también cuando el backend esté en otro repositorio;
  - la 1.5.9 empieza con el validador del backend elegido y probado contra los mismos casos;
  - una configuración publicada siempre puede volver a validarse con su versión exacta del esquema.
- **Negativas o costos:**
  - dos validadores que mantener al día, con un subconjunto del estándar y de YAML como regla;
  - la validación de la raíz depende de la red (descarga la *release*);
  - clasificar un cambio como menor o mayor depende de la revisión humana;
  - Python pasa a ser una herramienta de CI de la raíz, con sus dependencias fijadas, y `rpds-py` obliga a regenerar los *hashes* si cambia la versión de Python;
  - adoptar una versión nueva del esquema en la raíz lleva dos PR: primero el SHA‑256 en `esquemas.json`, luego los anexos.
- **Qué cambia en el repositorio:**
  - `backend/pom.xml` (una dependencia en `test`, `networknt`; SnakeYAML ya llega con `spring-boot-starter`), `backend/contracts/jsonschema/`, `backend/contracts/scripts/jsonschema.sh` y sus pruebas, `backend/README.md` y `backend/CHANGELOG.md`;
  - `tools/config-jurisdiccion/`;
  - `.github/workflows/backend-contracts-pr.yml`, `backend-main.yml` y `sistema-pr.yml`, y dos *checks* obligatorios más en el *ruleset* `main`;
  - la tabla del doc. 00 §6, el doc. 01 §5.3 (versiones inmutables y publicación al integrar), el doc. 04 §9 (*checks* obligatorios) y `ESTADO.md` (F0‑10).
