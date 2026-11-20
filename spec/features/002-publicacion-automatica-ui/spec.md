# 002 · Publicación automática de liv-components

**Estado:** hecha (2026-11-20); aprobada en la H1 del 2026-11-19 y construida antes que la 008
_(propuesta → aprobada (H1) → en construcción → hecha)_

**Módulo:** ninguno de negocio; entrega de la librería `ui/` · **Menú:** no aplica · **Zona roja:** sí: `.github/**` y la configuración de *build* y de publicación de `ui/` (`AGENTS.md` §4.3)
**Fuentes:** `docs/arquitectura/04` §9 (fila `ui-release.yml`, P4); `docs/arquitectura/01` §11.1 (fila «Distribución»); `docs/arquitectura/02` §11 y §12; ADR 0022 y 0040; `ui/README.md` › «Publicar una versión»; `AGENTS.md` §4.5 y §4.6
**Backlog:** *issues* con la etiqueta `feature:002`

_Reemplaza la propuesta del PR #113, escrita con el método anterior (ADR 0040)._

## Qué hace y por qué

Hoy, publicar una versión de `liv-components` exige que una persona con cuenta y permiso en npm haga seis pasos a mano en su equipo: integrar, actualizar `main`, `npm login` con 2FA, `npm run release`, comprobar y etiquetar. Con esta feature, quien trabaja en la librería solo sube el número y escribe su sección del `CHANGELOG.md` en su PR, como hoy. Al integrar el PR en `main`, GitHub verifica, **espera un clic de aprobación**, publica en npm sin ninguna clave guardada y crea la etiqueta y la *release*.

**Por qué ahora:**
- con las rebanadas verticales (ADR 0040), cada rebanada que necesita un componente nuevo espera a que alguien publique a mano;
- la 008 necesita cuatro componentes nuevos;
- una versión publicada no se puede reemplazar, y hoy depende de que cada persona siga bien los seis pasos.

## Reglas

No aplica ninguna regla tributaria. Son reglas del proyecto:

| Código | Regla | Fuente |
|---|---|---|
| — | La librería se comparte solo como paquete npm, con versión exacta en el frontend | `AGENTS.md` §4.6, ADR 0022 |
| — | Solo un humano publica en npm, desde `main` y después de integrar el PR. Un agente nunca publica | `AGENTS.md` §4.5 y §4.6, ADR 0022 |
| — | `ui-release.yml` publica con aprobación manual en un entorno protegido | `docs/arquitectura/04` §9 |
| — | La lógica vive en *scripts* con pruebas; los *workflows* solo orquestan (P4). Acciones fijadas por SHA, `permissions` mínimos y `concurrency` | `docs/arquitectura/04` §9 |
| — | `main` siempre sano: nada se integra sin sus verificaciones | `AGENTS.md` §4.1 |

Esta feature cambia la segunda regla: «publica un humano» pasa a ser **«un humano aprueba la publicación»**. Va en el ADR 0042, que reemplaza en parte al ADR 0022 y se acepta en la H1 de esta spec.

## Alcance del MVP

- **Entra:**
  - publicación al integrar en `main` un PR que sube la versión, con la aprobación de una persona;
  - publicación de confianza de npm (OIDC, sin clave guardada) y con procedencia;
  - la etiqueta `ui-vX.Y.Z` y la *release* con su sección del `CHANGELOG.md`;
  - volver a ejecutar sin publicar dos veces;
  - la verificación en el PR;
  - la guía de `ui/README.md`;
  - las reglas y los documentos que cambian.
- **Después:**
  - **versiones previas automáticas** (`0.6.0-next.1`, etiqueta `next` de npm);
  - un bot que abra el PR del frontend con la versión nueva;
  - publicar el catálogo de Storybook;
  - `npm deprecate` y retirar versiones.

## Criterios de aceptación

Cada criterio dice con qué se prueba. Las pruebas de los *scripts* corren en el CI de `ui/`; las del *workflow*, en las ejecuciones que se citan.

- [x] **002-01** — Dado un PR que sube la versión de `liv-components` por encima de la publicada, cuando se integra en `main`, entonces corren las verificaciones de publicación y la publicación queda **esperando la aprobación** en el entorno `npm`. Las verificaciones son *lint*, formato, pruebas, Storybook, empaquetado, sección del `CHANGELOG.md`, número mayor y `repository` dentro del paquete. El resumen de la ejecución muestra `nombre@versión`, el *commit*, el SHA‑256 y la lista de archivos del `.tgz`. _Rebanada:_ R3 (pasó de la R2, 2026-11-19). _Prueba:_ la ejecución de la primera publicación real queda en «Waiting» con ese resumen. _Evidencia (2026-11-20):_ Ejecución 44778437294 (`liv-components` 0.5.1): `decidir` y `verificar` en verde y `publicar` en «Waiting» en el entorno `npm` (revisores `jorgearaujoSap` y `sapenlinea`), con el resumen de `verificar` leído por la persona.
- [x] **002-02** — Dado que la publicación espera la aprobación, cuando una persona autorizada la aprueba, entonces la versión queda en npm con su procedencia, y el repositorio no guarda ninguna clave de npm. _Rebanada:_ R3. _Prueba:_ `npm audit signatures` en `frontend/` con la versión nueva; `gh secret list` y `gh secret list --env npm` sin claves de npm. Al cerrar la feature, con la configuración posterior a la primera publicación hecha por la persona: la página de acceso del paquete en npm con «Require two-factor authentication and disallow tokens» activo, y `gh api repos/jorgearaujoSap/liv/rulesets/<id>` con el *ruleset* de las etiquetas `ui-v*` (`conditions.ref_name.include` con `ui-v*`; reglas `update` y `deletion`, sin `creation`). _Evidencia (2026-11-20):_ Misma ejecución, aprobada por la persona: `npm view liv-components@0.5.1 dist.attestations` muestra la procedencia (SLSA v1); `npm audit signatures`, en un directorio temporal con la versión instalada (el frontend aún no la adopta), da 2 firmas del registro verificadas y 1 atestación verificada; `gh secret list` y `gh secret list --env npm` salen vacíos. *Ruleset* 27757900: activo, `refs/tags/ui-v*`, reglas `deletion` y `update`, sin `creation` ni *bypass*. La persona declaró el 2026-11-20 que activó «Require two-factor authentication and disallow tokens»: la API no lo muestra.
- [x] **002-03** — Dada una versión publicada por una ejecución, cuando termina, entonces existen la etiqueta `ui-vX.Y.Z` **en el *commit* de esa misma ejecución** y una *release* con la sección de esa versión del `CHANGELOG.md`. Si la etiqueta ya existe en otro *commit*, la ejecución falla y no la mueve. _Rebanada:_ R3. _Prueba:_ las pruebas de `release-decide` (etiqueta ajena → error) y `git rev-parse ui-vX.Y.Z` igual al *commit* publicado. _Evidencia (2026-11-20):_ `git ls-remote`: `ui-v0.5.1` apunta a `981d14f`, el *commit* de la ejecución; *release* `liv-components 0.5.1`, no borrador, con la sección del `CHANGELOG.md`. Queda marcada «Latest» pese a `--latest=false` (pendiente).
- [x] **002-04** — Dado un PR que sube la versión, cuando falla una verificación, entonces no se pide la aprobación ni se publica ni se etiqueta, y el registro dice qué paso falló con el enlace a la sección de la guía que lo resuelve. Las verificaciones del `CHANGELOG.md`, la versión y el paquete fallan **ya en el PR** (002-10), antes de llegar a `main`. _Rebanada:_ R1, R2 y R3 (los enlaces a la guía existen desde la R3). _Prueba:_ las pruebas de `release-decide` y de `check-release --ci` con cada fallo; los enlaces, en la R3. _Evidencia (2026-11-20):_ Pruebas de `release-decide`, `release-publish`, `release-tag` y `check-release --ci` con cada fallo (185 pruebas de *scripts* en `npm test`); los errores de los *scripts* y el `::error::` de cada trabajo terminan con la sección de `ui/README.md` que los resuelve.
- [x] **002-05** — Dado un cambio en `main` que no sube la versión (o que no toca `ui/`), cuando corre el *workflow*, entonces termina en «nada que publicar» sin pedir la aprobación, sin etiquetar y sin quedar en espera detrás de otra ejecución. Una versión igual a una ya publicada es «nada que publicar». Una versión menor que la última publicada, y no publicada, es un error. _Rebanada:_ R1 y R2. _Prueba:_ las pruebas de `release-decide`; una ejecución con un cambio de `ui/` sin subir la versión. _Evidencia (2026-11-20):_ Ejecuciones 44625271457 (R2), 44775041383 y 44775076372: cambios en `main` que no subieron la versión terminaron en «nada que publicar», sin aprobación pendiente ni espera.
- [x] **002-06** — Dada una ejecución cuyo `publicar` subió el paquete pero falló después, cuando se vuelve a ejecutar esa misma ejecución («Re-run failed jobs» o «Re-run all jobs»), entonces no se publica otra vez y se crea lo que falte (la etiqueta, la *release* o las dos). `publicar` compara la integridad del `.tgz` con la de npm: sigue si coinciden y falla si no. Si se vuelve a ejecutar todo, `decidir` ve la versión publicada **por el *commit* de esta ejecución** (con o sin etiqueta) y salta directo a `etiquetar`, que no cambia nada de lo que ya existe. _Rebanada:_ R3. _Prueba:_ las pruebas de `release-publish` con un `npm` falso que ya tiene la versión, con la misma integridad, con otra y sin respuesta; las de `release-decide` con la salida `etiquetar`, **incluida la etiqueta que ya está en este *commit***; y las de `release-tag` con la etiqueta y la *release* ya existentes, en este *commit* y en otro. _Evidencia (2026-11-20):_ Intento 2 de la ejecución 44778437294 («Re-run all jobs», hecho por la persona): `decidir` y `etiquetar` en verde, `verificar` y `publicar` omitidos, y npm sigue con una sola `0.5.1`. Más las pruebas de los *scripts*.
- [x] **002-07** — Dada cualquier ejecución que no sea de `main` (un PR, un *fork* o **una rama del propio repositorio con el *workflow* modificado**), cuando corre, entonces nunca puede publicar. Lo impiden tres controles a la vez: npm solo acepta el *workflow* `ui-release.yml` **con el entorno `npm`**; ese entorno solo admite la rama `main` y pide la aprobación; y cada trabajo exige `github.ref == 'refs/heads/main'`. _Rebanada:_ R2 y R3. _Prueba:_
  - **en la R2:** `gh api` del entorno (revisores y regla de ramas) y una ejecución en `--dry-run` desde una rama de prueba **sin la condición `if`**, que el entorno rechaza;
  - **en la R3:** la evidencia de la configuración del publicador de confianza, con el entorno. No se prueba publicando desde otra rama. _Evidencia (2026-11-20):_ R2: una rama de prueba fue rechazada por el entorno («Branch … is not allowed to deploy to npm») y `gh api` del entorno `npm` (revisores, solo `main`, sin *bypass*). R3: el publicador de confianza, con el entorno `npm`, publicó 0.5.1; y la prueba de 002-08 exige `github.ref == 'refs/heads/main'` en cada trabajo.
- [x] **002-08** — Dado el trabajo que tiene el permiso de publicar, cuando se ejecuta, entonces no hace `checkout`, no instala las dependencias del proyecto y no ejecuta nada que haya pasado por un trabajo que las instaló. Sus *scripts* vienen de `decidir`, que no instala nada. Usa Node y npm en las versiones exactas que valida `decidir`, sin caché, y publica con `--ignore-scripts` el `.tgz` que construyó y verificó `verificar`. _Rebanada:_ R3. _Prueba:_ una prueba de «sistema · scripts de CI» que lee `ui-release.yml` y falla si `publicar` tiene `checkout`, `npm ci`, caché, un *script* que venga del artefacto de `verificar`, o le falta `--ignore-scripts` o la comprobación de la huella en el mismo paso que ejecuta el *script*; y falla si `etiquetar` tiene `checkout`, no exige `publicar` en verde (o la salida `etiquetar`), usa un *script* o unas notas de `verificar`, o no comprueba sus huellas. _Evidencia (2026-11-20):_ `.github/scripts/test/ui-release.test.sh` en «sistema · scripts de CI» (PR #242): 53 pruebas, 41 de ellas mutaciones del *workflow* que debe rechazar.
- [x] **002-09** — Dada la guía de `ui/README.md` › «Publicar una versión», cuando se lee, entonces tiene pasos numerados para preparar la versión en el PR, aprobar la publicación (quién y dónde), resolver cada fallo de 002-04 y adoptar la versión en el frontend. Desde la activación de la publicación automática ya no hay publicación a mano: si GitHub Actions no está disponible, se espera. La guía no conserva ninguna subsección de publicación a mano (se retiró al activar, no después de la primera publicación). _Rebanada:_ R3. _Prueba:_ la revisión de la guía en la H3. _Evidencia (2026-11-20):_ Guía revisada en la H3 de los PR #240 y #241.
- [x] **002-10** — Dado un PR de `ui/` que sube la versión, cuando corre su CI, entonces `release:check --ci` verifica la sección del `CHANGELOG.md`, que el número sea mayor que el publicado y el campo `repository` del paquete. Si falla, el PR no se puede integrar. _Rebanada:_ R2. _Prueba:_ un PR de prueba sin la sección del `CHANGELOG.md` queda en rojo. _Evidencia (2026-11-20):_ PR de prueba #236 (cerrado): `ui · verify` en rojo sin la sección del `CHANGELOG.md`.

## Diseño técnico

**La lógica, en *scripts* con pruebas (P4).** Van en `ui/scripts/`, en Node y sin dependencias, con pruebas `node --test` que simulan `npm` y `git`:
- **`release-decide.mjs`:**
  - valida que la versión cumpla `^\d+\.\d+\.\d+$`, y lee y valida las versiones exactas de Node (`ui/.nvmrc`) y de npm (`packageManager`) para `publicar`;
  - consulta `npm view liv-components versions dist-tags --json` y **falla si npm no responde**: una respuesta vacía nunca es «nada que publicar»;
  - sabe si **este *commit* subió la versión**: la versión de `HEAD` es mayor que la de `HEAD^1`, o el padre no tenía la librería o tenía una versión previa. Una versión que baja (un *revert*) no cuenta como subida. Como los PR se integran con *squash*, cada `push` a `main` es un solo *commit*. Solo el *commit* que subió la versión la publica: así nunca se publica contenido distinto del que se revisó con su `CHANGELOG.md`;
  - lee las etiquetas remotas con `git ls-remote --tags origin`, porque el `checkout` no las trae;
  - devuelve una de estas salidas:

    | Situación | Salida |
    |---|---|
    | **Este *commit* subió la versión**, mayor que la última y no publicada | `publicar` |
    | Versión no publicada, no menor que la última, que **no** subió este *commit* (la subió otro que espera, se rechazó o venció) | `nada`, con el aviso «la versión X está pendiente en la ejecución del *commit* que la subió». No nombra el *commit*: con `fetch-depth: 2` no se puede saber cuál es |
    | Ya publicada y este *commit* subió la versión (volver a ejecutar todo después de publicar: falta la etiqueta, la *release* o las dos; `etiquetar` es idempotente) | `etiquetar` |
    | Ya publicada y este *commit* no subió la versión | `nada` |
    | Menor que la última y no publicada | `error` |
    | La etiqueta existe en otro *commit* y este *commit* subió la versión | `error` |
    | Ya publicada, este *commit* subió la versión y npm dice que salió de otro *commit* (`gitHead`) | `error` |
    | npm no responde | `error` |

    Las filas se evalúan en este orden: primero se lee todo (si npm o Git no responden, `error`), después los `error` de versión y de etiqueta, y al final `etiquetar`, `nada` o `publicar`.
- **`check-release.mjs --ci`:** sin `npm whoami`, `npm owner` ni la rama local. Agrega la verificación del campo `repository` dentro del `package.json` empaquetado.
- **`release-notes.mjs`:** extrae la sección de la versión del `CHANGELOG.md` a un archivo.
- **`release-publish.mjs`:** si la versión ya existe con la misma integridad (`dist.integrity`), sigue sin publicar; si existe con otra integridad, falla; si npm responde que no existe (E404), ejecuta `npm publish <tgz> --ignore-scripts --access public`. Cualquier otro error falla.
- **`release-tag.mjs`:** crea la etiqueta en el *commit* de la ejecución por la API de GitHub. Si ya existe en el mismo *commit*, sigue; si existe en otro, falla. Después crea la *release* con `--verify-tag` y `--notes-file`.

**El *workflow* `ui-release.yml`.**
- **Disparo:**
  - `push` a `main` con cambios en `ui/**` o en el propio *workflow*;
  - `workflow_dispatch`, que también se limita a `main` y aplica la misma regla al *commit* de `main` sobre el que corre.
- **Condición en cada trabajo:** `if: github.ref == 'refs/heads/main'`.
- **Paso de valores:** la versión y los demás valores pasan solo por `env:`, nunca interpolados en `run:`.
- **Trabajos:**
  1. **`decidir`** (`contents: read`):
     - `checkout` con `fetch-depth: 2` y `persist-credentials: false`, sin instalar dependencias;
     - ejecuta `release-decide.mjs` y publica su salida;
     - con `publicar` o `etiquetar`, genera las notas de la versión con `release-notes.mjs` (no necesita dependencias) y sube `release-publish.mjs`, `release-tag.mjs` y las notas como artefacto propio, que usarán `publicar` y `etiquetar`. Las huellas SHA‑256 de `release-publish.mjs`, de `release-tag.mjs` y de las notas viajan **como salidas del trabajo `decidir`**, nunca dentro de un artefacto, y `publicar` y `etiquetar` las comprueban con `sha256sum --check --strict`, con una cuenta exacta de archivos, en el mismo paso que usa el archivo;
     - con `nada` o `error`, la ejecución termina ahí: `nada` en verde, `error` en rojo con su mensaje; con `etiquetar`, salta a `etiquetar`;
     - no tiene `concurrency`, así que un cambio que no publica nunca queda en espera (002-05).
  2. **`verificar`** (`contents: read`, sin `id-token`):
     - `npm ci` (con `--ignore-scripts` si el *build* de Angular lo tolera; se comprueba en la R1), *lint*, formato, pruebas, `build-storybook`, `check-release --ci` y `npm pack`;
     - comprueba la sección del `CHANGELOG.md` (`check-release --ci`), pero las notas de la *release* las genera `decidir`;
     - escribe en el resumen `nombre@versión`, el *commit*, el SHA‑256 y la lista de archivos del `.tgz`;
     - sube el `.tgz` y su SHA‑256 como artefacto.
  3. **`publicar`:**
     - permisos `id-token: write` y `contents: read`, en el entorno **`npm`**;
     - `concurrency` propia (`ui-release-publicar`), sin cancelar la ejecución en curso;
     - no hace `checkout`;
     - `setup-node` con la versión exacta de Node que dio `decidir`, sin caché ni `registry-url`;
     - instala `npm` en la versión exacta que dio `decidir` (la de `packageManager`), en un paso anterior a cualquier uso del permiso;
     - descarga el `.tgz` de `verificar` y los *scripts* de `decidir`, compara el nombre, la versión y el SHA‑256 del `.tgz` con las salidas de `decidir` y `verificar`, y ejecuta `release-publish.mjs` **del artefacto de `decidir`**, nunca del de `verificar` (002-06, 002-08);
     - ejecuta `release-publish.mjs`, que hace `npm publish --ignore-scripts` del `.tgz` si la versión no existe; exige la procedencia (`NPM_CONFIG_PROVENANCE`) y comprueba la huella del *script* y el SHA‑256 del `.tgz` en el mismo paso.
  4. **`etiquetar`** (`contents: write`):
     - condición: `github.ref == 'refs/heads/main' && (needs.decidir.outputs.salida == 'etiquetar' || (needs.decidir.outputs.salida == 'publicar' && needs.publicar.result == 'success'))`, con `!cancelled()` y `needs.decidir.result == 'success'`, para que corra aunque `verificar` y `publicar` se omitan, pero no si se cancela la ejecución ni si `decidir` falló. Si la aprobación se rechazó o `publicar` falló, no etiqueta (002-04);
     - sin `checkout`;
     - el *token* solo en el paso que lo usa;
     - ejecuta `release-tag.mjs` del artefacto de `decidir` con el `github.sha` **de esta ejecución** y las notas que generó `decidir`, que no instala nada (002-03, 002-06). Corre también cuando `decidir` da `etiquetar`, sin `verificar` ni `publicar`.
- **Varias versiones seguidas:** GitHub deja una sola ejecución en espera por grupo. Si se integran dos subidas de versión antes de aprobar la primera, la primera se cancela.
  - **La regla es aprobar o rechazar antes de integrar otra versión de la librería.**
  - Si pasa, la versión saltada se publica con «Re-run» de su ejecución cancelada, **antes** de aprobar la siguiente.
  - Si ya se aprobó la siguiente, la saltada no se puede publicar, porque sería menor que la última. Se acepta como pérdida: sus cambios salen con la siguiente, y se anota en el `CHANGELOG.md`.
  - Una aprobación rechazada o vencida no publica nada; se vuelve a ejecutar la misma ejecución cuando corresponda.
- **El PR de `ui/`:** `ui-pr.yml` corre `release:check --ci` cuando el PR cambia la versión (002-10). Ese cambio es de `.github/` y va en la R2.
- **`repository`:** `{"type": "git", "url": "git+https://github.com/jorgearaujoSap/liv.git", "directory": "ui/projects/liv-components"}`. Cuando `ui/` se separe en su propio repositorio (§4.6), cambian este campo y el publicador de confianza.
- **Datos y contrato:** no aplica.
- **Permisos:**
  - aprueban los revisores del entorno `npm`: el responsable y quien él designe;
  - `id-token: write` solo en `publicar` y `contents: write` solo en `etiquetar`.
- **Amenazas** (`docs/arquitectura/02` §11 y §12):
  - **Una rama del repositorio con el *workflow* modificado:** la frenan los tres controles de 002-07. El central es que npm exija el entorno `npm`, que solo admite `main`.
  - **Una dependencia comprometida en `publicar`:** no instala ni ejecuta nada del proyecto.
  - **Una dependencia comprometida en `verificar`** puede alterar el paquete antes del *hash*. Es un **riesgo residual aceptado**, mitigado así:
    - la lista de archivos del `.tgz` queda en el resumen que ve quien aprueba;
    - las dependencias de `ui/` pasan la revisión de dependencias (doc. 02 §11);
    - el SHA‑256 solo garantiza que el paquete no cambió entre trabajos.
  - **Credenciales personales:** después de la primera publicación con OIDC, la persona activa en npm «exigir 2FA y no permitir *tokens*». Así ninguna credencial personal puede publicar con *tokens*. No hay publicación a mano (decisión de la H1); si GitHub Actions no está disponible, se espera. Un *ruleset* protege las etiquetas `ui-v*`.
  - **Que un agente apruebe con el `gh` de la persona.** Es un **riesgo residual aceptado**:
    - los agentes usan la sesión de `gh` de la persona, que puede aprobar;
    - no se agrega una regla `deny` en `.claude/settings.json` (decisión de la H1). Sería una mitigación parcial que se evita con `curl`, con el navegador o desde otros agentes;
    - `AGENTS.md` §4.5 dice de forma explícita, para todo agente, que aprobar una publicación o un despliegue está prohibido;
    - el control real es la regla de `AGENTS.md`: ningún agente aprueba, y la persona aprueba solo desde la página de la ejecución, viendo su resumen;
    - no se puede activar «evitar que quien disparó la ejecución la apruebe», porque quien integra suele ser quien aprueba.

    Queda declarado en el ADR 0042.
  - **Inyección:** la versión se valida y va por `env:`, y las notas van por archivo.
- **Pantallas:** no hay.
- **Riesgos y decisiones:**
  - **El ADR 0042**, aceptado en la H1:
    - publica al integrar, con aprobación;
    - usa la publicación de confianza, con el entorno exigido;
    - no deja publicación a mano;
    - reemplaza en parte al ADR 0022.
  - **Configuración que hace la persona, en este orden:**
    1. **Antes de la R2:** crear el entorno `npm` con sus revisores y la regla de ramas «Selected branches: `main`». Se comprueba con `gh api repos/jorgearaujoSap/liv/environments/npm`.
    2. **Antes de la R3:** registrar en npmjs.com el publicador de confianza, con el repositorio `jorgearaujoSap/liv`, el *workflow* `ui-release.yml` y el entorno `npm`.
    3. **Después de la primera publicación:** activar «exigir 2FA y no permitir *tokens*» en npm y el *ruleset* de las etiquetas `ui-v*`. El *ruleset* bloquea la actualización y el borrado, pero **no la creación**, para que `etiquetar` pueda seguir creando etiquetas.
  - **Sin ventana en la que no se pueda publicar:** hasta integrar el PR que activa la publicación automática, la publicación a mano sigue vigente, como hoy. El PR de reglas lo dice así, y la guía nueva dice «a partir de la versión que publique el *workflow*».
  - **Las reglas cambian antes de activar la publicación:** el PR de la R3 que quita `--dry-run` se integra después de los PR de `AGENTS.md`, los docs. 01, 02 y 04, la constitución, el doc. 00 §6, `ui/README.md` y `ui/AGENTS.md`. Del ADR 0022 solo cambia su estado, a «Aceptado; reemplazado en parte por el ADR 0042», porque un ADR aceptado no se edita.
  - **Repositorio privado:** la procedencia y la aprobación del entorno dependen de que el repositorio sea público, o del plan de GitHub. Pasarlo a privado exige revisar el ADR 0042, y mientras tanto la publicación se detiene.
  - **Lo que hay que verificar contra la documentación oficial:**
    - **antes de la H1:** que el publicador de confianza de npm tenga el campo «entorno». Si no lo tiene, el control central de 002-07 cae y el diseño se replantea;
    - **en la R1:**
      - la versión exacta de Node, porque `ui/.nvmrc` dice `22` y la publicación de confianza pide Node 22.14 o mayor y npm 11.5.1 o mayor. La R1 fija `.nvmrc` en una versión exacta;
      - si el *build* tolera `npm ci --ignore-scripts`;
    - **en la R2:**
      - si un trabajo que espera la aprobación ocupa el grupo de `concurrency`. Si lo ocupa, en vez de cancelarse la primera queda en cola la segunda, y la regla de versiones seguidas se ajusta;
      - que el *ruleset* de `ui-v*` deje crear etiquetas al `GITHUB_TOKEN`;
    - **en la R3:** si `--provenance` es implícito y el nombre de la opción «no permitir *tokens*». Resultado: se exige explícito (`NPM_CONFIG_PROVENANCE`) y la opción se llama «Require two-factor authentication and disallow tokens».
- **Documentos que cambian:**
  - `AGENTS.md` §4.5 (dos líneas: publicar `liv-components` y «publicar versiones») y §4.6;
  - `ui/AGENTS.md` (fila «Publicación») y `ui/README.md`;
  - `docs/arquitectura/04` §9 (fila `ui-release.yml` y el diagrama), `docs/arquitectura/01` §11.1 (fila y diagrama) y `docs/arquitectura/02` §11;
  - `spec/constitution/tech-stack.md`;
  - el comentario de `.github/workflows/ui-pr.yml`;
  - el doc. 00 §6 (ADR 0042, y ADR 0022 reemplazado en parte);
  - `ESTADO.md`.
- **Revisión:** revisión adversarial del diseño técnico el 2026-11-18, con dos revisores ciegos de nivel alto. Encontraron 1 hallazgo crítico, 6 altos, 16 medios y 7 bajos, en buena parte coincidentes. Todos se resolvieron en este diseño. El crítico era que una rama del repositorio podía publicar sin aprobación. Los altos eran:
  - etiquetas en el *commit* equivocado;
  - volver a ejecutar sin garantizar 002-06;
  - un entorno desprotegido en la R1;
  - las reglas cambiadas después de publicar.

  Lo que no se puede comprobar desde el repositorio quedó en «Lo que hay que verificar en la R3».

## Rebanadas

| # | Rebanada | PR, en orden | Criterios | *Issue* |
|---|---|---|---|---|
| R1 | *Scripts* de publicación con sus pruebas: `release-decide`, `release-notes`, `release-publish`, `check-release --ci` y el campo `repository`. `.nvmrc` con versión exacta. Comprueba si el *build* tolera `npm ci --ignore-scripts`. No publica nada | `ui/` | 002-04, 002-05 (lógica), 002-06 (lógica) | #231 |
| R2 | Esqueleto del *workflow* en `--dry-run`. **Antes:** la persona crea el entorno `npm` protegido. Trae `ui-release.yml` con `decidir`, `verificar` y `publicar` en `--dry-run`, y `release:check --ci` en `ui-pr.yml` | `.github/` (raíz) | 002-04, 002-05, 002-07, 002-10 | #232 |
| R3 | Publicación real. **Antes:** el publicador de confianza en npm y las reglas, en dos PR: 1) raíz: `AGENTS.md` (con la prohibición de aprobar para todo agente), docs. 00 §6, 01, 02 y 04, constitución, estado del ADR 0022 y comentario de `ui-pr.yml`; 2) `ui/`: `README.md`, `AGENTS.md` y `release-tag.mjs` con sus pruebas. **Después**, 3) raíz: `publicar` sin `--dry-run`, más `etiquetar` y la prueba de 002-08. Se prueba con la siguiente versión real. **Requisito del PR 3:** la versión de `main` ya está publicada en npm. El PR de `ui/` cambia también `release-decide` (la etiqueta ya puesta en este *commit* da `etiquetar`); el PR 3 cambia `decidir` (notas y huellas) y retira las notas de transición | raíz, `ui/` y raíz | 002-01, 002-02, 002-03, 002-06, 002-07, 002-08, 002-09 | #233 |

## Cambios

- 2026-11-20 — **El ADR 0043 (aceptado el 2026-11-20, *issue* #244) cambia la regla de que ningún agente aprueba la publicación**, que esta spec registra en las reglas, en el diseño técnico (riesgo residual de «que un agente apruebe con el `gh` de la persona»), en el punto 5 de «Decisiones de la H1» y en el cambio de la R3 de más abajo. Desde que se integra, un agente puede aprobar o volver a ejecutar `ui-release.yml` y completar su etiqueta o su *release* solo con la confirmación de la persona en el chat, con los datos completos a la vista (las acciones y sus límites, en `AGENTS.md` §4.5); sigue prohibido mover o borrar etiquetas, cambiar el entorno `npm`, los *rulesets*, el publicador de confianza o el paquete en npm, y publicar con un comando. No cambia ningún criterio, el diseño del *workflow* ni las rebanadas. Lo que cuenta es `AGENTS.md` §4.5.
- 2026-11-20 — Cierre de la feature. Cambios hechos al construir la R3 que **entran con la H3 de este PR** (los tres de 2026-11-19 de arriba ya tenían el visto bueno de la persona):
  - `etiquetar` corre con `!cancelled()` y exige `decidir` en verde (no `always()`); no usa `setup-node`, que recibe `github.token`, y usa el Node del *runner*;
  - `publicar` exige la procedencia (`NPM_CONFIG_PROVENANCE`) y comprueba el SHA‑256 del `.tgz` en el mismo paso que publica. 0.5.1 salió con procedencia SLSA;
  - la prueba de 002-08 (`ui-release.test.sh`) cubre `publicar` y `etiquetar`: permisos, `env`, interpolaciones, token, `continue-on-error`, huellas y la condición `if`;
  - cada trabajo termina, si falla, con un `::error::` que remite a la sección de la guía (002-04);
  - la guía restringe los reintentos: con la versión ya en npm, solo «Re-run failed jobs» (compara la integridad); «Re-run all jobs» salta a `etiquetar` sin compararla. Una aprobación vencida (30 días) se resuelve con `workflow_dispatch` o un PR nuevo, porque GitHub no deja volver a ejecutar una ejecución de más de 30 días. Para una etiqueta ajena con la versión ya publicada, la persona se agrega a la lista de excepciones del *ruleset* (sin desactivarlo), anota la evidencia y la borra: es un procedimiento destructivo que no estaba en el diseño;
  - la publicación a mano se retiró **al activar**, no después de la primera publicación (#241), y se quitaron `release` y `prerelease` de `ui/package.json`;
  - la regla «Nunca» de `AGENTS.md` §4.5 se amplió (#238); su cambio posterior (confirmación en el chat con los datos de la ejecución) va en el ADR 0043, aceptado (#244, PR #245);
  - quedan sin resolver: la *release* `ui-v0.5.1` marcada «Latest»; el modo local de `release:check` (`npm whoami`, `npm owner`, rama); `gitHead` ausente en npm para las versiones del *workflow* (la evidencia de «este *commit* publicó» es que subió la versión); el riesgo de una etiqueta ajena creada entre `publicar` y `etiquetar`; y que el Node de `etiquetar` es el del *runner*.
- 2026-11-19 — Cambios al integrar la R2 y construir la R3, con el visto bueno de la persona el 2026-11-19 (anotados en #232 y #233):
  - **002-01 pasa de la R2 a la R3**, por decisión de la persona: se comprueba con la primera publicación real, que pasa por la misma espera de aprobación con el mismo resumen, y así no hace falta una versión de prueba que nunca se publicaría. 002-05 se comprobó en la R2 con la ejecución 44625271457 (un cambio de `main` que no subió la versión terminó en «nada», sin pedir aprobación ni quedar en espera);
  - la fila «ya publicada, este *commit* subió la versión» de la tabla de salidas de `release-decide` da `etiquetar` también cuando la etiqueta ya está en este *commit*, y no `nada`. Así «Re-run all jobs» crea la *release* que falte (002-06, «se crea lo que falte»); `release-tag` es idempotente en la etiqueta y en la *release*. Cambia el código de `release-decide` en el PR de `ui/` de la R3;
  - **las notas de la *release* las genera `decidir`**, no `verificar`, y `decidir` sube los *scripts* y las notas tanto con `publicar` como con `etiquetar`, con un manifiesto de huellas de todos los *scripts* que `publicar` y `etiquetar` comprueban en el mismo paso que los ejecutan. Motivo: con la salida `etiquetar`, `verificar` no corre y no habría notas; además, así las notas no vienen de un trabajo que ejecutó código de las dependencias. Visto bueno de la persona el 2026-11-19, en el chat;
  - **002-02 agrega a su prueba**, al cerrar la feature, la configuración posterior a la primera publicación (npm sin *tokens* y con 2FA, y el *ruleset* de `ui-v*`), para que esos controles tengan evidencia. Visto bueno de la persona el 2026-11-19, en el chat;
  - la tabla de salidas de `release-decide` aclara sus filas: «la etiqueta existe en otro *commit*» es un error solo si este *commit* subió la versión, y se agrega la fila del `gitHead` de otro *commit*. No cambia el comportamiento del código;
  - **requisito del PR 3:** antes de integrarlo, la versión de `ui/projects/liv-components/package.json` en `main` debe estar publicada en npm (`npm view liv-components version`). Una versión integrada mientras el *workflow* está en `--dry-run` no se podría publicar después: «Re-run» usa el *workflow* de su propio *commit*, y la publicación a mano ya no estaría vigente;
  - **la regla «Nunca» de `AGENTS.md` §4.5 se amplía**, por la revisión adversarial del PR 1 de la R3: ningún agente, aunque la persona lo pida, aprueba, rechaza, cancela, dispara ni vuelve a ejecutar una publicación o un despliegue, ni cambia lo que los protege, ni crea, mueve o borra las etiquetas `ui-v*`, ni usa comandos de npm que cambien el paquete, ni publica con un comando. El ADR 0042 solo dice que un agente nunca aprueba; la persona aprueba la ampliación en la H3 del PR 1;
  - **la guía de `ui/README.md`** que trae el PR 2 conserva, hasta la primera publicación del *workflow*, una subsección transitoria «Publicación a mano» (la que `AGENTS.md` cita durante la transición); un ajuste de `ui/` la retira después, y 002-09 aplica «ya no hay publicación a mano» desde la activación;
  - **el PR 3 retira las notas de transición** de `AGENTS.md` (§4.5 y §4.6), de los docs. 01 §11.1, 02 §11 y 04 §9, de `spec/constitution/tech-stack.md`, de `docs/guia-del-desarrollador.md`, de `.claude/skills/liv-orquestador/SKILL.md` y del comentario de `ui-pr.yml`. Para la R3 queda por comprobar si las versiones que publica el *workflow* llevan `gitHead` en npm; si no, la evidencia de «este *commit* publicó» es solo que subió la versión, y se acepta por escrito en el cierre;
  - la R3 se entrega en tres PR, en orden, y el de las reglas lleva además la nota de que **hasta el PR que activa la publicación real sigue vigente la publicación a mano**, que ese último PR retira de `AGENTS.md`.
- 2026-11-19 — Dos cambios de la tabla de salidas de `release-decide`, de la revisión adversarial de la R1 (PR #234), con el visto bueno de la persona el 2026-11-19 (anotado en #231):
  - «este *commit* subió la versión» pasa de «distinta» a «mayor» que la del padre, para que un *revert* que baja la versión no publique ni etiquete;
  - el aviso de `nada` ya no nombra el *commit* que subió la versión, porque con `fetch-depth: 2` `git log` señala siempre al padre.
- 2026-11-18 — Reemplaza la propuesta del PR #113, cerrado sin integrar, con la plantilla del ADR 0040. Se mantienen sus criterios; las versiones previas pasan a «Después».
- 2026-11-18 — Segunda ronda B: solo publica el *commit* que subió la versión; etiquetas remotas con `git ls-remote`; prueba de 002-07 sin la condición `if`; publicación a mano vigente hasta activar la automática; `deny` ampliado y prohibición para todo agente; lo que se verifica, por rebanada.
- 2026-11-18 — Segunda ronda de la revisión: los *scripts* de `publicar` y `etiquetar` vienen de `decidir`, no del artefacto de `verificar`; salida `etiquetar` para volver a ejecutar todo; `release-tag.mjs` con pruebas; versiones de Node y npm validadas por `decidir`; la regla `deny`, declarada como mitigación parcial; el *ruleset* de etiquetas no bloquea la creación.
- 2026-11-18 — El diseño técnico se rehízo con la revisión adversarial: tres controles contra la publicación desde otra rama, etiqueta solo de la misma ejecución, publicación idempotente, lógica en *scripts* con pruebas, verificación en el PR (002-10), entorno creado antes del *workflow* y reglas cambiadas antes de publicar.

## Decisiones de la H1

Aprobada por la persona el 2026-11-19:

1. **Disparo:** integrar en `main` el *commit* que sube la versión, más la aprobación en el entorno `npm`.
2. **Credencial:** publicación de confianza de npm (OIDC), con el entorno `npm` exigido. Antes de la H1 se comprobó en la documentación de npm que el publicador de confianza tiene el campo «Environment name».
3. **Quién aprueba:** el responsable y los colaboradores que él agregue como revisores del entorno `npm`.
4. **Sin publicación a mano**, ni de emergencia: si GitHub Actions no está disponible, se espera. Hasta integrar el PR que activa la publicación automática, sigue la publicación a mano de hoy.
5. **Sin regla `deny`** en `.claude/settings.json`. El control es la regla de `AGENTS.md`: ningún agente aprueba una publicación.
6. **ADR 0042 aceptado.**
7. **Orden:** la 002 se construye antes que la 008, que queda en pausa.
8. **Sin tercera ronda de revisión:** las correcciones de la segunda ronda se aceptan como están. Cada rebanada que toque `.github/` o la publicación tendrá su propia revisión adversarial.
