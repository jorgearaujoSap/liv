# ADR-0027 · Comparador de cambios incompatibles y publicación de los contratos OpenAPI

- **Estado:** Aceptado
- **Fecha:** 2026-11-04
- **Decisores:** responsable del proyecto, el 2026-11-03, al elegir el diseño de la tarea aparte de F0‑07 y después, tras la revisión adversarial y las cuatro lentes, sus ajustes (equipo de una persona, `AGENTS.md` §4.3)
- **Documentos relacionados:** ADR 0005, ADR 0023; `AGENTS.md` §4.5 y §4.6; `docs/arquitectura/01` §10 y §14.1; `docs/arquitectura/04` §8 (Q08) y §9; plan de la feature 001, pasos 6 y 11; tareas T02, T18 y T20 de la feature 001

## Contexto

El único contrato entre el backend y el frontend es el OpenAPI (`AGENTS.md` §4.6). El backend lo publica versionado, y el frontend guarda copias fijadas en `frontend/api-spec/` con `api-spec/versions.json`, que descarga `npm run api:update` (plan de la feature 001, pasos 6 y 11). El doc. 01 §10 pide que un cambio incompatible se detecte en CI con un comparador de OpenAPI y exija nueva versión o aprobación explícita. La compuerta Q08 (doc. 04 §8) pide "sin cambios incompatibles no aprobados".

T02 dejó el comparador y la publicación para una tarea aparte de F0‑07, antes de T20 (decisión del responsable, 2026-11-02). El comparador es una herramienta nueva, y `AGENTS.md` §4.5 exige un ADR.

## Opciones consideradas

**Comparador:**

1. **`openapi-diff` de OpenAPITools como plugin de Maven** (`org.openapitools.openapidiff:openapi-diff-maven`, Apache‑2.0).
   - A favor: corre con el `./mvnw` del proyecto, igual en CI que en local, sin Docker ni instalaciones, y queda en el ecosistema de Java del backend.
   - En contra: detecta menos casos que oasdiff, y el *script* tiene que sacar de Git la versión base y llamarlo una vez por contrato.
2. **oasdiff con su imagen de Docker.**
   - A favor: es el comparador más completo, con más de 300 reglas.
   - En contra: hace de Docker un requisito del comparador y suma otra imagen que fijar y vigilar.

**Publicación:**

1. **Una *release* de GitHub por contrato y versión.** El repositorio es público: se descarga por URL fija, sin *token*, y no vence.
2. **Artefacto de GitHub Actions.** Vence a los 90 días como máximo, no se busca por versión y para descargarlo hace falta un *token*.
3. **Paquete npm en GitHub Packages.** Pide un *token* incluso para leer, y se aparta del plan, que pide copias fijadas en `api-spec/`.

**Aprobación de un cambio incompatible:**

1. La etiqueta `contrato-incompatible` en el PR, más la versión mayor subida.
2. Solo la versión mayor subida.
3. No permitirlo nunca.

## Decisión

- **Comparador.** `openapi-diff-maven` **2.1.7**, declarado solo en `pluginManagement` de `backend/pom.xml`: no corre en el *build*. Lo llama `contracts/scripts/compare.sh` de cada proyecto, que compara cada contrato con su versión base.
- **Versión.** Cada contrato lleva la suya en `info.version` (`X.Y.Z`, una sola vez). Si el contrato cambia, la versión sube, comparada número por número.
- **Aprobación.**
  - Un cambio incompatible, o quitar un contrato, pasa solo con la etiqueta `contrato-incompatible` en el PR y con la versión mayor subida. Un fallo del comparador no se puede aprobar con la etiqueta.
  - La etiqueta la pone una persona con permiso de escritura, nunca un agente, y se lee en vivo por la API.
- **Reglas que acompañan a la comparación.** En `contracts/openapi/` solo van contratos `.yaml`, con nombre en minúsculas, dígitos y guiones, sin enlaces simbólicos y con los `$ref` internos. Un contrato nuevo tiene que poder leerse. Un proyecto sin contratos falla.
- **Quién juzga.** En CI juzgan los *scripts* de la base (el commit anterior, con su `pom.xml`), en un *worktree* aparte, no los del PR: un PR no cambia la regla con la que se juzga. Los *scripts* del PR solo se prueban, en otro trabajo que no comparte caché de Maven con el juez. Un PR no cambia a la vez los *workflows* de contratos y los contratos. Quitar los *scripts* o sus pruebas hace fallar la compuerta.
- **Publicación.**
  - Al integrar en `main`, `contracts/scripts/publish.sh` publica cada versión nueva como *release* `contract-<nombre>-vX.Y.Z`, con `<nombre>.yaml` y su SHA-256, a partir del contenido del commit integrado.
  - Antes de publicar, juzga cada versión contra la *release* publicada más alta de su contrato, con la etiqueta del PR integrado (decisión del responsable, 2026-11-04). Q08 todavía no es un *check* obligatorio. Así no se publica nada que no la cumpla frente a lo que el frontend ya usa: ni un cambio integrado con el *check* en rojo, ni un contrato quitado y vuelto a agregar, ni dos PR con la misma versión. Un contrato sin *releases* se valida como contrato nuevo.
  - La *release* se crea como borrador. Se suben los dos archivos, se verifican descargados y solo entonces se publica. No se marca como la más reciente.
  - Una versión publicada no cambia: si su contenido difiere, la publicación falla.
- **Proyectos.** La API (`backend/`) y, desde T18, el BFF (`backend/bff/`), que tiene su propia copia de los *scripts* y del plugin para seguir siendo un proyecto independiente (decisión del responsable, 2026-11-03). El nombre del contrato nombra su *release*, así que es único en todo el repositorio.
- **CI.**
  - `backend-contracts-pr` corre en los PR que tocan los contratos, sus *scripts* o el `pom.xml` de cada proyecto, también al poner o quitar etiquetas y al cambiar la base del PR. `backend-main` publica al integrar.
  - Los dos recorren los dos proyectos y solo ejecutan *scripts* de cada uno (IN‑08). Los *scripts* y los *workflows* van en PR separados (`AGENTS.md` §4.6).
- **Huecos conocidos que se aceptan** (decisión del responsable, 2026-11-03). `openapi-diff` 2.1.7 no detecta una petición más estricta por `minLength`, `minimum` o similares, ni un campo obligatorio de la respuesta que pasa a `nullable`. Esos cambios los revisa una persona; dos escenarios de las pruebas fijan el comportamiento actual para que una versión nueva del plugin que lo cambie se note.

## Consecuencias

- **Positivas:**
  - Q08 queda automatizada, y la aprobación explícita queda registrada en el PR.
  - El frontend tiene versiones inmutables por URL fija y puede verificar su SHA-256.
  - El comparador corre igual en local que en CI, sin otras herramientas, y sus 79 casos de prueba (`contracts/scripts/test/run.sh`) cubren la comparación, el modo con referencia de Git y la publicación con un `gh` falso.
- **Negativas o costos:**
  - **Detección:** `openapi-diff` detecta menos casos que oasdiff (ver los huecos conocidos).
  - **Reporte:** el resultado se lee del texto del reporte de consola. Si una versión nueva lo cambia, las pruebas fallan.
  - **Tiempo:** cada contrato cambiado arranca Maven una vez, unos 5 a 10 s.
  - **Etiqueta:**
    - vale como aprobación mientras la escritura en el repositorio esté limitada a los responsables;
    - sigue puesta aunque lleguen *commits* nuevos: la revisión humana del PR (H3) cubre el contenido final.
  - **Inmutabilidad:** por ahora no se activan las *releases* inmutables del repositorio ni un *ruleset* para las etiquetas `contract-*` (decisión del responsable, 2026-11-03). Quien tenga permiso de escritura podría borrar o reemplazar una *release* a mano. El SHA-256 que el frontend fija en `versions.json` lo detecta al descargar.
  - **Mientras Q08 no sea obligatoria:** un PR puede integrarse con el *check* en rojo. No se publica, pero `main` queda con un contrato que falla al publicar hasta que otro PR lo corrija. Cuando sea obligatoria, conviene exigir la rama al día o una cola de integración.
  - **Límite del *workflow*:** un `pull_request` corre el *workflow* tal como está en el PR. La regla de no cambiar a la vez los *workflows* y los contratos, y la revisión humana de `.github/`, cubren ese límite.
  - **Comprobaciones de texto:** la versión y los `$ref` se leen con expresiones sobre el YAML, no con un analizador. Rechazan las formas ambiguas que se conocen (claves repetidas, entre comillas, escapes), pero una ofuscación deliberada la detecta la revisión humana.
  - **Concurrencia en `main`:** GitHub deja una sola ejecución en espera. Si se cancela la del medio de tres integraciones seguidas, la siguiente publica la versión que quede en el árbol, juzgada contra lo publicado.
- **Qué cambia en el repositorio:**
  - `backend/pom.xml` (`openapi-diff.version`), `backend/contracts/scripts/`, `backend/README.md` y `backend/CHANGELOG.md`;
  - `.github/workflows/backend-contracts-pr.yml` y `backend-main.yml`;
  - la etiqueta `contrato-incompatible` del repositorio;
  - la tabla de *workflows* del doc. 04 §9, la tabla del doc. 00 §6, las tareas T02, T18 y T20 de la feature 001 y `ESTADO.md` (F0‑07).
