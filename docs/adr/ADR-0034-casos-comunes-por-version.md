# ADR-0034 · Casos comunes por versión: los casos de la versión X del esquema se evalúan con las versiones hasta X

- **Estado:** Propuesto
- **Fecha:** 2026-11-09
- **Decisores:** responsable del proyecto, que aprobó la decisión en la H2 de la feature 005 (P18 de su `plan.md`) el 2026-11-09 (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** ADR 0032 (que este ADR complementa y no modifica); feature 005 (`spec/features/005-cartera-y-recaudo/plan.md`, P15 y P18); feature 004 (`spec/features/004-esquema-configuracion-jurisdiccion/`); `docs/arquitectura/01` §5.3 y §14.1 (IN‑11); `docs/funcional/03` §5.4 (CJ01); `backend/contracts/jsonschema/config-jurisdiccion/1.0.casos.json`

## Contexto

El ADR 0032 fija que cada versión del esquema de configuración de jurisdicción tiene **un archivo de casos comunes** (`<MAYOR.MENOR>.casos.json`), inmutable y publicado con el esquema. Lo corren las pruebas del backend; lo correrán también el validador de la raíz, que la feature 004 trasladó a la opción 1.5.9, y el validador de producción de esa opción, que debe superarlo. El 0032 fija además que cada versión acepta solo su propio `esquema_version`.

La feature 005 crea la versión 1.1. El caso «versión desconocida» de `1.0.casos.json` usa `esquema_version: "1.1"` como ejemplo de una versión que el esquema no conoce, y espera el error de `/esquema_version`. Si los casos de la 1.0 se corren con todas las versiones presentes, en cuanto existe `1.1.json` ese caso deja de ser una versión desconocida y la prueba falla. El ADR 0032 no dice con qué versiones se evalúan los casos de una versión, y no se puede editar: es un ADR aceptado, y `1.0.casos.json` es inmutable.

## Opciones consideradas

1. **Evaluar los casos de la versión X con las versiones hasta X (P18).**
   - A favor: el resultado de un caso publicado no cambia porque salga una versión nueva; no se toca `1.0.casos.json`; la versión nueva se numera 1.1, como se aprobó en la H1 de la feature 005.
   - En contra: el registro de versiones de quien corre los casos debe poder limitarse a una versión máxima.
2. **Llamar 1.2 a la versión nueva,** para que el caso de la 1.0 siga usando una versión que no existe.
   - A favor: no cambia ninguna prueba.
   - En contra: cambia los criterios aprobados en la H1 y deja un hueco en la numeración; el problema reaparece con la siguiente versión.
3. **Cambiar `"1.1"` por otro valor en `1.0.casos.json`.**
   - A favor: es la corrección más corta.
   - En contra: rompe la inmutabilidad de la versión; lo impiden `contracts/scripts/jsonschema.sh check` y el SHA‑256 de la *release* (ADR 0032).
4. **Evaluar los casos de cada versión con todas las versiones presentes.**
   - A favor: es el comportamiento actual, sin cambios.
   - En contra: obliga a editar los casos viejos cada vez que sale una versión, que es el defecto que este ADR evita.
5. **Evaluar los casos de la versión X solo con la X.**
   - A favor: es más simple, no necesita orden entre versiones y encaja con «cada versión acepta solo su `esquema_version`».
   - En contra: un archivo de casos no podría declarar una versión anterior y resolverla con su esquema, como pasa en producción cuando conviven varias versiones.
   - Cumple el mismo objetivo. Se prefirió «hasta X» porque reproduce lo que el validador ve en producción cuando X era la última versión, y porque es lo que aprobó la H2.

## Decisión

Opción 1. Los **casos de validación** de la versión X se evalúan con el registro de versiones del esquema que tiene **las versiones menores o iguales a X**. X es la `version` de cabecera del archivo de casos.

- **Registro:** las versiones que el validador tiene a mano, sin importar si ya se publicaron: en el backend, los esquemas de `contracts/jsonschema/<nombre>/`; en la raíz, `esquemas.json`, que solo fija versiones publicadas (ADR 0032). Esto precisa la palabra «publicadas» de P18: en el PR que agrega la versión nueva al backend, sus casos necesitan su esquema antes de publicarlo.
- **Orden:** las versiones se comparan como números por (MAYOR, MENOR), no como texto: `1.10` es mayor que `1.9`. Una versión nueva se numera mayor que todas las ya publicadas, porque si no, los casos de las posteriores cambiarían de resultado. Lo revisa una persona, como la clasificación en menor o mayor del ADR 0032.
- **Alcance:** toda ejecución de un archivo de casos con sus resultados tal como están escritos en el archivo, sin reescribirlos:
  - las pruebas del backend;
  - el validador de la raíz, que la feature 004 trasladó a la opción 1.5.9;
  - el validador de producción de la opción 1.5.9.
- **Versión desconocida:** el caso de una versión X que prueba una versión desconocida usa un valor mayor que X. El de la 1.0 usa `"1.1"` y sigue dando el error de `/esquema_version` aunque exista la 1.1. El contenido concreto de los casos de la 1.1 lo fija el plan de la feature 005.
- **Casos de carga:** no dependen de la versión, porque cada implementación tiene un solo cargador de YAML, común a todas las versiones, que corre antes de leer `esquema_version`. El cargador debe superar los casos de carga de todos los archivos de casos. Un cambio del subconjunto portable de YAML que rompa los casos de carga de una versión anterior exige un ADR nuevo.
- **Fuera de la regla:** los usos derivados que reescriben el contenido de los casos, como la prueba de compatibilidad 005‑18 (lo válido en 1.0 es válido en 1.1), usan el registro completo.
- **Producción:** el límite está en el registro que arma quien corre los casos y que le pasa al validador, nunca en un argumento de la ruta de validación. Esa ruta resuelve siempre con todas las versiones y con la versión exacta que declara la configuración.
- **No cambia** lo que decide el ADR 0032: un archivo de casos inmutable por versión, validadores, publicación y comprobación de inmutabilidad.

## Consecuencias

- **Positivas:**
  - un caso publicado da siempre el mismo resultado, salga la versión que salga después;
  - los archivos de casos de versiones anteriores no se editan nunca;
  - las pruebas, la raíz y el validador de la 1.5.9 siguen la misma regla.
- **Negativas o costos:**
  - quien corre los casos debe poder limitar su registro a una versión máxima;
  - la regla depende de que las versiones nuevas se numeren en orden creciente, que revisa una persona; así, mientras valga, no se puede mantener una versión mayor anterior (una 1.2 después de la 2.0);
  - con el registro limitado, los casos ya no detectan un error de enrutado que mande una configuración a la versión más nueva; ese enrutado se prueba aparte, con el registro completo, también en la 1.5.9;
  - la compatibilidad con la versión anterior la comprueban las pruebas de compatibilidad de la versión nueva (005‑18 en la 1.1), no los archivos de casos.
- **Qué cambia en el repositorio:**
  - la tabla del doc. 00 §6, con la fila 0034;
  - en la feature 005, las pruebas del backend que cargan casos y esquemas por versión (`JurisdictionConfigSchemas.forVersion`, `CommonCases.load` y las pruebas de carga y de validación de los casos, en B0), la prueba de P18 y `1.1.casos.json` (en B1);
  - el validador de la raíz y el de la opción 1.5.9, que llegan con esa feature y aplicarán la misma regla;
  - el ADR 0032 y `1.0.casos.json` no cambian.
