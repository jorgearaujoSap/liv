# 002 · Publicación automática de liv-components

**Estado:** aprobada (H1, 2026-11-19); en construcción antes que la 008, que queda en pausa
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

- [ ] **002-01** — Dado un PR que sube la versión de `liv-components` por encima de la publicada, cuando se integra en `main`, entonces corren las verificaciones de publicación y la publicación queda **esperando la aprobación** en el entorno `npm`. Las verificaciones son *lint*, formato, pruebas, Storybook, empaquetado, sección del `CHANGELOG.md`, número mayor y `repository` dentro del paquete. El resumen de la ejecución muestra `nombre@versión`, el *commit*, el SHA‑256 y la lista de archivos del `.tgz`. _Rebanada:_ R2. _Prueba:_ la ejecución de la primera versión después de R2 (en `--dry-run`) queda en «Waiting» con ese resumen.
- [ ] **002-02** — Dado que la publicación espera la aprobación, cuando una persona autorizada la aprueba, entonces la versión queda en npm con su procedencia, y el repositorio no guarda ninguna clave de npm. _Rebanada:_ R3. _Prueba:_ `npm audit signatures` en `frontend/` con la versión nueva; `gh secret list` y `gh secret list --env npm` sin claves de npm.
- [ ] **002-03** — Dada una versión publicada por una ejecución, cuando termina, entonces existen la etiqueta `ui-vX.Y.Z` **en el *commit* de esa misma ejecución** y una *release* con la sección de esa versión del `CHANGELOG.md`. Si la etiqueta ya existe en otro *commit*, la ejecución falla y no la mueve. _Rebanada:_ R3. _Prueba:_ las pruebas de `release-decide` (etiqueta ajena → error) y `git rev-parse ui-vX.Y.Z` igual al *commit* publicado.
- [ ] **002-04** — Dado un PR que sube la versión, cuando falla una verificación, entonces no se pide la aprobación ni se publica ni se etiqueta, y el registro dice qué paso falló con el enlace a la sección de la guía que lo resuelve. Las verificaciones del `CHANGELOG.md`, la versión y el paquete fallan **ya en el PR** (002-10), antes de llegar a `main`. _Rebanada:_ R1, R2 y R3 (los enlaces a la guía existen desde la R3). _Prueba:_ las pruebas de `release-decide` y de `check-release --ci` con cada fallo; los enlaces, en la R3.
- [ ] **002-05** — Dado un cambio en `main` que no sube la versión (o que no toca `ui/`), cuando corre el *workflow*, entonces termina en «nada que publicar» sin pedir la aprobación, sin etiquetar y sin quedar en espera detrás de otra ejecución. Una versión igual a una ya publicada es «nada que publicar». Una versión menor que la última publicada, y no publicada, es un error. _Rebanada:_ R1 y R2. _Prueba:_ las pruebas de `release-decide`; una ejecución con un cambio de `ui/` sin subir la versión.
- [ ] **002-06** — Dada una ejecución cuyo `publicar` subió el paquete pero falló después, cuando se vuelve a ejecutar esa misma ejecución («Re-run failed jobs» o «Re-run all jobs»), entonces no se publica otra vez y se crea lo que falte. `publicar` compara la integridad del `.tgz` con la de npm: sigue si coinciden y falla si no. Si se vuelve a ejecutar todo, `decidir` ve la versión publicada **por el *commit* de esta ejecución** y sin etiqueta, y salta directo a `etiquetar`. _Rebanada:_ R3. _Prueba:_ las pruebas de `release-publish` con un `npm` falso que ya tiene la versión, con la misma integridad, con otra y sin respuesta; y las de `release-decide` con la salida `etiquetar`.
- [ ] **002-07** — Dada cualquier ejecución que no sea de `main` (un PR, un *fork* o **una rama del propio repositorio con el *workflow* modificado**), cuando corre, entonces nunca puede publicar. Lo impiden tres controles a la vez: npm solo acepta el *workflow* `ui-release.yml` **con el entorno `npm`**; ese entorno solo admite la rama `main` y pide la aprobación; y cada trabajo exige `github.ref == 'refs/heads/main'`. _Rebanada:_ R2 y R3. _Prueba:_
  - **en la R2:** `gh api` del entorno (revisores y regla de ramas) y una ejecución en `--dry-run` desde una rama de prueba **sin la condición `if`**, que el entorno rechaza;
  - **en la R3:** la evidencia de la configuración del publicador de confianza, con el entorno. No se prueba publicando desde otra rama.
- [ ] **002-08** — Dado el trabajo que tiene el permiso de publicar, cuando se ejecuta, entonces no hace `checkout`, no instala las dependencias del proyecto y no ejecuta nada que haya pasado por un trabajo que las instaló. Sus *scripts* vienen de `decidir`, que no instala nada. Usa Node y npm en las versiones exactas que valida `decidir`, sin caché, y publica con `--ignore-scripts` el `.tgz` que construyó y verificó `verificar`. _Rebanada:_ R3. _Prueba:_ una prueba de «sistema · scripts de CI» que lee `ui-release.yml` y falla si `publicar` tiene `checkout`, `npm ci`, caché, un *script* que venga del artefacto de `verificar`, o le falta `--ignore-scripts`.
- [ ] **002-09** — Dada la guía de `ui/README.md` › «Publicar una versión», cuando se lee, entonces tiene pasos numerados para preparar la versión en el PR, aprobar la publicación (quién y dónde), resolver cada fallo de 002-04 y adoptar la versión en el frontend. Ya no hay publicación a mano: si GitHub Actions no está disponible, se espera. _Rebanada:_ R3. _Prueba:_ la revisión de la guía en la H3.
- [ ] **002-10** — Dado un PR de `ui/` que sube la versión, cuando corre su CI, entonces `release:check --ci` verifica la sección del `CHANGELOG.md`, que el número sea mayor que el publicado y el campo `repository` del paquete. Si falla, el PR no se puede integrar. _Rebanada:_ R2. _Prueba:_ un PR de prueba sin la sección del `CHANGELOG.md` queda en rojo.

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
    | Versión no publicada que **no** subió este *commit* (la subió otro que espera, se rechazó o venció) | `nada`, con el aviso «la versión X está pendiente en la ejecución del *commit* que la subió». No nombra el *commit*: con `fetch-depth: 2` no se puede saber cuál es |
    | Ya publicada, este *commit* subió la versión y falta la etiqueta (volver a ejecutar todo después de publicar) | `etiquetar` |
    | Ya publicada en cualquier otro caso | `nada` |
    | Menor que la última y no publicada | `error` |
    | La etiqueta existe en otro *commit* | `error` |
    | npm no responde | `error` |
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
     - sube `release-publish.mjs` y `release-tag.mjs` como artefacto propio, que usarán `publicar` y `etiquetar`;
     - con `nada` o `error`, la ejecución termina ahí: `nada` en verde, `error` en rojo con su mensaje; con `etiquetar`, salta a `etiquetar`;
     - no tiene `concurrency`, así que un cambio que no publica nunca queda en espera (002-05).
  2. **`verificar`** (`contents: read`, sin `id-token`):
     - `npm ci` (con `--ignore-scripts` si el *build* de Angular lo tolera; se comprueba en la R1), *lint*, formato, pruebas, `build-storybook`, `check-release --ci` y `npm pack`;
     - genera el archivo de notas;
     - escribe en el resumen `nombre@versión`, el *commit*, el SHA‑256 y la lista de archivos del `.tgz`;
     - sube el `.tgz`, su SHA‑256 y las notas como artefacto.
  3. **`publicar`:**
     - permisos `id-token: write` y `contents: read`, en el entorno **`npm`**;
     - `concurrency` propia (`ui-release-publicar`), sin cancelar la ejecución en curso;
     - no hace `checkout`;
     - `setup-node` con la versión exacta de Node que dio `decidir`, sin caché ni `registry-url`;
     - instala `npm` en la versión exacta que dio `decidir` (la de `packageManager`), en un paso anterior a cualquier uso del permiso;
     - descarga el `.tgz` de `verificar` y los *scripts* de `decidir`, compara el nombre, la versión y el SHA‑256 del `.tgz` con las salidas de `decidir` y `verificar`, y ejecuta `release-publish.mjs` **del artefacto de `decidir`**, nunca del de `verificar` (002-06, 002-08);
     - mientras dure R2, ejecuta solo `npm publish --dry-run`, y el resumen dice «DRY-RUN: no publica».
  4. **`etiquetar`** (`contents: write`):
     - sin `checkout`;
     - el *token* solo en el paso que lo usa;
     - ejecuta `release-tag.mjs` del artefacto de `decidir` con el `github.sha` **de esta ejecución** y las notas de `verificar` (002-03).
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
    - **en la R3:** si `--provenance` es implícito y el nombre de la opción «no permitir *tokens*».
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
| R2 | Esqueleto del *workflow* en `--dry-run`. **Antes:** la persona crea el entorno `npm` protegido. Trae `ui-release.yml` con `decidir`, `verificar` y `publicar` en `--dry-run`, y `release:check --ci` en `ui-pr.yml` | `.github/` (raíz) | 002-01, 002-04, 002-05, 002-07, 002-10 | #232 |
| R3 | Publicación real. **Antes:** el publicador de confianza en npm y las reglas, en dos PR: 1) raíz: `AGENTS.md` (con la prohibición de aprobar para todo agente), docs. 00 §6, 01, 02 y 04, constitución, estado del ADR 0022 y comentario de `ui-pr.yml`; 2) `ui/`: `README.md`, `AGENTS.md` y `release-tag.mjs` con sus pruebas. **Después**, 3) raíz: `publicar` sin `--dry-run`, más `etiquetar` y la prueba de 002-08. Se prueba con la siguiente versión real | raíz, `ui/` y raíz | 002-02, 002-03, 002-06, 002-07, 002-08, 002-09 | #233 |

## Cambios

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
