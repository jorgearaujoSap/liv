# 004 · Esquema de la configuración de jurisdicción — Tareas

_Checklist accionable derivada del `plan.md`. Tareas pequeñas y concretas; cada una cabe en un PR revisable (≤ ~400 líneas, salvo los datos que se justifican en el PR). Cada tarea indica los archivos que puede tocar y la prueba que se escribe primero (TDD: rojo → verde → refactorizar). Marca `[x]` al completarlas._

Un PR por paso del plan, en su orden: cada paso usa lo que integró el anterior (`AGENTS.md` §4.6; ADR 0027: *scripts* y *workflows* en PR separados). Es un cambio crítico: antes de cada PR, `liv-revision-adversarial` con dos revisores de nivel alto y las cuatro lentes de `liv-revision-4r`, con el resumen en la descripción del PR.

## Paso 1 · Backend: el esquema y sus pruebas (rama `feat/004-esquema-backend`)

Un solo PR, que pasa de ~400 líneas y se justifica en su descripción (Q12), como permite el plan (riesgo «Tamaño del PR del paso 1»): las correcciones de la revisión tocan a la vez el cargador y el esquema.

- [x] **T1** · Carga segura del YAML con el subconjunto portable (plan, «Subconjunto portable de YAML»).
  - **Archivos:** en `backend/src/test/java/co/com/sapenlinea/liv/contracts/`: `SafeYamlLoader.java`, `SafeYamlLoaderTest.java` y `CommonCases.java` (lee los casos con números exactos) y la sección `carga` de `backend/contracts/jsonschema/config-jurisdiccion/1.0.casos.json`.
  - **Prueba primero:** `SafeYamlLoaderTest` sobre los casos de carga (004‑10 y las reglas 1 a 5 del subconjunto), en rojo sin el cargador. Si SnakeYAML no permite alguna regla, se detiene aquí y se replantea.
- [x] **T2** · Esquema 1.0 y sus pruebas (plan, «Contenido del esquema 1.0»).
  - **Archivos:** `backend/pom.xml` (`networknt` 3.0.9 en `test`, ADR 0032), `backend/contracts/jsonschema/config-jurisdiccion/1.0.json`, la sección `validacion` de `1.0.casos.json`, en `backend/src/test/java/co/com/sapenlinea/liv/contracts/`: `JurisdictionConfigSchemas.java` (ayuda de las pruebas que elige el esquema por `esquema_version` antes de validar, fila «Versión» del plan, 004‑09), `TestConfigurations.java` (carga y cambia las configuraciones de prueba), `JurisdictionConfigFieldClasses.java` y `JurisdictionConfigFieldTable.java` (la clase de cada campo según el plan, oráculo de la mutación), `JurisdictionConfigSchemaTest.java`, `JurisdictionConfigSchemaMutationTest.java`, `JurisdictionConfigCommonCasesTest.java` y `JsonSchemaFilesTest.java`; `CommonCases.java` (rutas desde `basedir`); las copias de los dos anexos en `backend/src/test/resources/contracts/jsonschema/config-jurisdiccion/`; `backend/.gitattributes` (`contracts/jsonschema/**` con LF, como se publica).
  - **Prueba primero:** las cuatro clases de prueba, en rojo sin `1.0.json`: un caso por criterio (CJ01, 004‑01 a 004‑11) con su código en el `@DisplayName`, la mutación de cada nodo de las dos configuraciones, los casos comunes y la meta‑prueba.
  - **Verificación local:** `./mvnw verify` en `backend/` y `./mvnw dependency:tree` (SnakeYAML sigue en `compile`).
- [x] **T3** · Documentación del paso 1. T1 a T3 en el PR #141, con la revisión adversarial y las cuatro lentes en dos rondas.
  - **Archivos:** `backend/README.md` (sección del esquema) y `backend/CHANGELOG.md` («Sin publicar»).

## Paso 2 · Backend: inmutabilidad y publicación (rama `feat/004-esquema-publicacion`)

- [ ] **T4** · `jsonschema.sh check` y `jsonschema.sh publish`.
  - **Archivos:** `backend/contracts/scripts/jsonschema.sh`, sus escenarios en `backend/contracts/scripts/test/`, `backend/contracts/scripts/test/run.sh` (las incluye y falla si faltan).
  - **Prueba primero:** los escenarios de `check` (versión cambiada o quitada, archivo fuera de la forma, enlace simbólico, nombre repetido con `openapi/`, *script* o pruebas quitados) y de `publish` (con el `gh` falso: borrador, subir, verificar descargado, publicar, volver a correr, otro contenido), en rojo sin el *script*.
- [ ] **T5** · Documentación del paso 2.
  - **Archivos:** `backend/README.md` y `backend/CHANGELOG.md`.

## Paso 3 · CI de contratos (rama `ci/004-esquema-contratos`; ruta crítica, aprobación explícita del responsable)

- [ ] **T6** · Juez de inmutabilidad y publicación del esquema.
  - **Archivos:** `.github/workflows/backend-contracts-pr.yml` (paso nuevo en «contratos (Q08)», solo en la pierna `backend`) y `.github/workflows/backend-main.yml` (trabajo propio de publicación, solo en `main`).
  - **Prueba:** la ejecución del propio PR (`backend · contratos` en verde, con el paso que dice que la base aún no tiene `contracts/jsonschema/`), el caso de falla cerrada sin *script* en la base ni en el PR, y al integrarlo la *release* `jsonschema-config-jurisdiccion-v1.0`. El cambio de `1.0.json` se demuestra en T11. `aplica.sh` y los nombres de los *checks* no cambian.

## Paso 4 · Raíz: validador de los anexos (rama `feat/004-validador-anexos`; después de la *release* 1.0)

- [ ] **T7** · `tools/config-jurisdiccion/**` como ruta crítica (`spec.md`, decisión 3 de la H2).
  - **Archivos:** `AGENTS.md` §4.3.
  - **Prueba:** no aplica (regla de trabajo).
- [ ] **T8** · Validador de los anexos contra el esquema publicado.
  - **Archivos:** `tools/config-jurisdiccion/validar.py`, `esquemas.json`, `.python-version`, `requirements.txt`, `README.md` y `test/`.
  - **Prueba primero:** las pruebas con `unittest`, sin red: los casos comunes publicados (con su SHA‑256), los de esquema violado con el formato exacto de la salida, la versión desconocida o numérica, el SHA‑256 que no coincide, la carpeta sin anexos, el anexo con otro nombre y la inyección en la salida.

## Paso 5 · CI de la raíz (rama `ci/004-validar-anexos`; aprobación explícita del responsable)

- [ ] **T9** · Trabajos `sistema · configuraciones de jurisdicción` (el juez de la base) y `sistema · validador de configuraciones del PR`, y la guarda que impide que un PR cambie `sistema-pr.yml` junto con `tools/config-jurisdiccion/` o `docs/funcional/anexos/`.
  - **Archivos:** `.github/workflows/sistema-pr.yml`.
  - **Prueba:** la ejecución del propio PR y la demostración de T11.
- [ ] **T10** · Los dos *checks* nuevos en el *ruleset* `main`, después de integrar T9. Lo hace el responsable (`AGENTS.md` §4.5).
- [ ] **T11** · Demostración en PR de prueba, cerrados sin integrar: anexo con una clave mal escrita (004‑13), cambio de `1.0.json` (004‑12) y validador debilitado con un anexo roto.
  - **Archivos:** solo los de cada PR de prueba, y `spec.md` con sus enlaces.

## Paso 6 · Cierre (raíz, documentación)

- [ ] **T12** · Documentación.
  - **Archivos:** `docs/arquitectura/04` §9 (*checks* obligatorios), `docs/arquitectura/01` §5.3 (versiones inmutables y publicación al integrar), `spec.md` (criterios marcados), `ESTADO.md` (F0‑10 hecho) y `spec/constitution/roadmap.md`. Los cambios de los documentos de arquitectura los aprueba el responsable.
- [ ] Revisión adversarial independiente de lo implementado (dos revisores de nivel alto) y las cuatro lentes de `liv-revision-4r` en cada PR; hallazgos resueltos o justificados.
- [ ] Validar contra los criterios de aceptación de `spec.md`: cada uno marcado `[x]` con la prueba que lo demuestra.
- [ ] Compuertas de CI en verde y cada PR integrado por un humano (H3).
- [ ] Mover la feature a «Hecho» en `../../constitution/roadmap.md` y actualizar `ESTADO.md`.

## Mantenimiento (checklist recurrente)

- [ ] Un cambio del esquema es una versión nueva, en un archivo nuevo (`<MAYOR.MENOR>.json` y `<MAYOR.MENOR>.casos.json`). Adoptarla en la raíz son dos PR: primero su SHA‑256 en `tools/config-jurisdiccion/esquemas.json`, luego los anexos.
- [ ] Si cambian los anexos, actualizar las copias de prueba del backend en otro PR (son una instantánea).
- [ ] Si cambia el nombre de uno de los *checks* nuevos, cambiar también el *ruleset* `main` en el mismo momento.
