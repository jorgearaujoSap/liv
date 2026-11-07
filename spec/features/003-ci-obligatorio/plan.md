# 003 · CI obligatorio en main — Plan

_Cómo se implementa lo descrito en `spec.md`. Debe respetar la `constitution/` y la arquitectura (`docs/arquitectura/`)._

**Estado:** aprobado (H2) — 2026-11-07, responsable del proyecto
_(borrador → aprobado (H2))_

## Enfoque

Los *workflows* de PR dejan de filtrar por rutas en el disparador. Cada uno empieza con un trabajo corto, `<proyecto> · cambios`, que obtiene los archivos del *commit* que se verifica y decide si el proyecto aplica. La regla es una **lista de lo que se sabe que no afecta**, no de lo que afecta: el proyecto solo se omite si todos los archivos están en otros proyectos o en la documentación. Cualquier archivo desconocido o de la raíz lo hace aplicar.

La verificación se omite **solo** si `cambios` terminó bien y dijo `false`. Un fallo, una salida vacía o una cancelación nunca la omiten.

La decisión vive en un *script* con pruebas (`docs/arquitectura/04` §9: los *workflows* solo orquestan), no copiada en cada *workflow*.

## Implementación

Un PR de la raíz (`.github/`, `docs/`, `ESTADO.md`; `AGENTS.md` §4.6). Después, el cambio de la protección de `main`, con la aprobación explícita del responsable (§4.5), y la demostración.

1. **Prueba primero: `.github/scripts/test/aplica.test.sh`.** Crea un repositorio temporal con un *commit* de *merge* real y llama al *script* sobre él, sin red. Casos:
   - un archivo del proyecto aplica, y uno de otro proyecto no;
   - un archivo de `backend/bff/` aplica al BFF y no al backend; uno de `backend/src/` aplica al backend y no al BFF;
   - un archivo que se mueve de un proyecto a `docs/`, y uno que se borra: el proyecto aplica;
   - `.github/`, `.gitattributes` o una ruta desconocida de la raíz: aplican todos;
   - solo documentación (`docs/`, `spec/`, `e2e/`, `tools/`, `.claude/`, los `.md` de la raíz, `.gitignore` y `.mcp.json`): no aplica ninguno;
   - nombres con tilde, «ñ», espacios, comillas y saltos de línea;
   - un error de `git` (sin padre, `HEAD` inválido): `aplica=true`;
   - contratos: aplica con `backend/contracts/`, `backend/bff/contracts/`, los dos `pom.xml` o el *wrapper* de Maven de cualquiera de los dos, y no con código Java;
   - `.github/x.md` aplica a todos y `backend/README.md` aplica al backend: `*.md` solo es neutro en la raíz;
   - una lista de archivos vacía: `aplica=true`;
   - la salida es exactamente una línea, `aplica=true` o `aplica=false`.

   Corre en CI con un trabajo nuevo, `sistema · scripts de CI`, en `sistema-pr`.
2. **`.github/scripts/aplica.sh <proyecto>`** (`backend`, `bff`, `frontend`, `ui` o `contratos`):
   - la lista sale de `git -c core.quotePath=false diff -z --name-only --no-renames HEAD^1 HEAD` y se lee separada por NUL (`read -r -d ''`). Así funciona con cualquier nombre, y un archivo movido aparece con su ruta anterior y con la nueva;
   - la comparación es con `case` de `bash`, sin expresiones regulares ni tuberías:
     - **del proyecto:** `backend/*` salvo `backend/bff/*`; `backend/bff/*`; `frontend/*`; `ui/*`;
     - **neutros**, que no afectan a ningún proyecto: `docs/*`, `spec/*`, `e2e/*`, `tools/*`, `.claude/*`, `.gitignore`, `.mcp.json` y los `*.md` de la raíz (un nombre sin `/`: en `case`, `*` también acepta `/`, así que primero se descarta `*/*`);
     - **contratos:** `backend/contracts/*`, `backend/bff/contracts/*`, `backend/pom.xml`, `backend/bff/pom.xml` y el *wrapper* de Maven de los dos (`mvnw*` y `.mvn/*`), porque el comparador corre con ese Maven;
   - para un proyecto, `aplica=false` solo si ningún archivo es suyo y todos son de otro proyecto o neutros;
   - para los contratos, `aplica=false` solo si ningún archivo es de contratos y todos están dentro de un proyecto o son neutros;
   - con `set -euo pipefail`, cualquier error escribe `aplica=true`, y una lista de archivos vacía también. Escribe `aplica` una sola vez, al final, y nunca imprime nombres de archivo.
3. **Trabajo `<proyecto> · cambios`** en `backend-pr`, `bff-pr`, `frontend-pr`, `ui-pr` y `backend-contracts-pr`:
   - *checkout* del *merge* con `fetch-depth: 2` y `persist-credentials: false`, permisos `contents: read`, `timeout-minutes: 2` y un solo paso: el *script*;
   - llama al *script* **de la rama base** (`git show HEAD^1:.github/scripts/aplica.sh`), igual que `backend-contracts-pr` juzga con la versión de la base. Si la base todavía no lo tiene (el propio PR que lo crea), escribe `aplica=true`.
4. **Verificación** (`backend · verify`, `bff · verify`, `frontend · verify` y `ui · verify`):
   - `needs: cambios` e `if: ${{ always() && (needs.cambios.result != 'success' || needs.cambios.outputs.aplica != 'false') }}`;
   - con `always()`, ni un fallo de `cambios` ni una cancelación la dejan omitida. Con una cancelación normal, GitHub vuelve a evaluar el `if` y la verificación corre igual. Con una cancelación forzada (la segunda), queda `cancelled`, que bloquea. Solo un `success` con `false` la omite. No se usa `!cancelled()`: con esa condición, la verificación de una ejecución cancelada quedaría omitida, y un omitido cuenta como aprobado.
   - Los pasos no cambian.
5. **Contratos** (`backend-contracts-pr`):
   - `test` y `compare` llevan la misma condición del paso 4;
   - un trabajo de matriz omitido publica su *check* con el nombre sin expandir. Por eso se agrega `backend · contratos`, con `if: ${{ always() }}`, que es el *check* obligatorio. Aprueba solo en dos casos:
     - `cambios` terminó bien con `false`, y `test` y `compare` se omitieron;
     - `test` y `compare` terminaron bien, sea cual sea el resultado de `cambios`.

     Cualquier `failure`, `cancelled` u otra combinación lo deja en rojo.
   - Igual que en el paso 4, un fallo de `cambios` no bloquea por sí solo: hace correr todo.
6. **Protección de `main`** (*ruleset* `main`, id 26341460):
   - a los *checks* obligatorios se suman `backend · verify`, `bff · verify`, `frontend · verify`, `ui · verify`, `backend · contratos` y `sistema · scripts de CI`, todos de GitHub Actions (15368);
   - la lista de actores que pueden saltarse la protección queda vacía;
   - la rama no tiene que estar al día con `main` (`strict_required_status_checks_policy: false`, como hoy; decisión del responsable en la H1);
   - el JSON resultante (`gh api repos/{repo}/rulesets/26341460`) se guarda en `spec.md` como prueba de 003-06;
   - los PR que ya estén abiertos necesitan «Update branch» para recibir los *checks* nuevos.
7. **Demostración, después del paso 6.** Cada PR de prueba se cierra sin integrar. Su enlace y su `gh pr view --json mergeStateStatus,statusCheckRollup` quedan en `spec.md`:
   - **solo `docs/`:** los cuatro `verify`, `test` y `compare` en `skipped`, y `backend · contratos` en verde (003-02);
   - **`ui/` con una prueba rota:** `ui · verify` en rojo y el PR en `BLOCKED` (003-01, 003-06);
   - **solo `backend/bff/`:** corre `bff · verify` y `backend · verify` en `skipped` (003-01);
   - **un archivo movido de `frontend/` a `docs/`:** corre `frontend · verify` (003-01);
   - **un contrato incompatible sin la etiqueta:** `backend · contratos` en rojo y el PR en `BLOCKED` (003-03);
   - **solo `backend/pom.xml`:** corre Q08 (003-03);
   - **cancelar la ejecución mientras corre `cambios`:** la verificación corre igual (003-05);
   - **forzar la cancelación mientras corre `verify`:** el *check* queda `cancelled` y el PR en `BLOCKED` (003-05);
   - **solo `.gitattributes`:** corren los cuatro `verify` y Q08 (003-04);
   - **003-07:** la salida de `git diff main -- .github/ | grep -E '^\+.*uses:'`, que solo debe mostrar las acciones que ya se usaban.
   - **Límite:** un fallo de `cambios` (003-05) no se puede provocar en un PR de prueba sin tocar `.github/`, que ya hace correr todo. Esa parte la cubren el arnés del paso 1 y la condición del paso 4, revisada en la H2.
8. **Documentación:**
   - `docs/arquitectura/04` §9: los *workflows* corren siempre y los trabajos se omiten cuando no aplican; se sube su versión;
   - `ESTADO.md`: F0‑07 y la bitácora;
   - comentarios en cada *workflow*: los nombres de los trabajos son los de la protección y no se cambian sin cambiarla también.

## Datos, API y seguridad

- **Permisos:** `cambios` solo lee el repositorio (`contents: read`, sin credenciales guardadas). Ningún trabajo nuevo recibe secretos.
- **Amenazas (doc. 02, cadena de suministro):**
  - no se agregan acciones de terceros;
  - los nombres de archivo son datos: se comparan con `case` y nunca se ejecutan, se interpolan en una orden ni se escriben en el registro, donde un nombre como `::error::` se interpretaría como una orden del *workflow*.
- **Riesgo residual declarado:** un PR que cambia `.github/workflows/` controla su propio CI. Puede:
  - cambiar la condición o el *script* que se llama;
  - crear otro trabajo con el mismo nombre de *check*;
  - con `permissions: checks: write`, publicar un *check run* en verde con ese nombre.

  La protección lo acepta, porque viene de GitHub Actions. **La única barrera es la revisión humana (H3) de todo *diff* de `.github/`**, que esta feature no cambia. El *script* de la base solo protege a los PR que no tocan `.github/`.

## Decisiones

- **La lista sale de `git diff -z` sobre el *commit* de *merge*, no de la API de GitHub.** Es justo el *diff* del *commit* que se verifica, no tiene el tope de 3000 archivos de la API, con `--no-renames` muestra las dos rutas de un archivo movido, con `-z` no altera los nombres y no necesita permisos extra.
- **Lista de lo que no afecta, no de lo que afecta.** Un archivo nuevo en la raíz o una carpeta nueva hacen correr todo hasta que alguien los declare neutros en el *script*, con su prueba.
- **Se omite solo con `success` y `false`.** Un fallo de `cambios` hace correr todo en lugar de bloquear el PR, igual en los cinco *checks*.
- **`backend · verify` ya no corre por cambios del BFF.** Son proyectos Maven separados, y el *verify* del backend no compila el BFF (`bff-pr.yml`).
- **Un agregado solo para los contratos.** En los demás *workflows* el trabajo omitido conserva su nombre, porque no es de matriz.

## Riesgos

- **Una ruta mal clasificada omite una verificación que debía correr.** Mitigación: la clasificación vive en un solo *script* con su arnés, y la demostración del paso 7.
- **Más trabajos de CI:** cada *workflow* arranca en todos los PR. El repositorio es público y los minutos no cuestan; `cambios` dura segundos y no instala nada.
- **Un *push* nuevo espera la verificación anterior:** con `always()`, la cancelación por concurrencia ya no detiene una verificación en curso. La ejecución nueva espera a que termine. Se acepta: es el costo de que una cancelación nunca deje un *check* omitido.
- **Cambiar un nombre de trabajo rompe la protección:** el *check* obligatorio quedaría esperando. Hay un comentario en cada *workflow*, y el paso 6 lista los nombres.

## Revisión

- [x] **Ronda 1** del plan (dos revisores ciegos de nivel alto, 2026-11-07).
  - **Crítico:** si `cambios` fallaba, la verificación se omitía.
  - **Altos:**
    - los archivos movidos no se veían;
    - la lista no estaba atada al *commit* verificado;
    - la detección podía fallar en silencio;
    - no había pruebas que se escribieran primero.
  - Además, medios y bajos. Todos resueltos.
- [x] **Ronda 2** sobre lo corregido (nivel alto, 2026-11-07).
  - **Altos:**
    - una cancelación dejaba el PR integrable; ahora se usa `always()` y se exige un resultado;
    - Git escribía entre comillas los nombres con tildes; ahora se usan `-z` y `core.quotePath=false`, con un arnés sobre un repositorio real.
  - **Medios:**
    - la condición no exigía `success` de `cambios`;
    - `.gitattributes` y otros archivos de la raíz quedaban fuera; ahora la regla es la lista de lo que no afecta;
    - la demostración no mostraba el bloqueo; ahora se hace después de cambiar la protección.
  - **Bajos:** el agregado de contratos, el riesgo residual reescrito y la prueba de 003-07.
- [x] **Ronda 3** sobre lo corregido (nivel alto, 2026-11-07).
  - **Medios:**
    - el comportamiento real de `always()` ante una cancelación, la demostración separada en dos casos y el costo de la concurrencia;
    - el *wrapper* de Maven entre los archivos de contratos.
  - **Bajos:**
    - un caso real de `.gitattributes` en la demostración;
    - una lista vacía da `aplica=true`;
    - `*.md` solo es neutro en la raíz.
  - Todos resueltos. No quedan hallazgos críticos ni altos.
- [x] Aprobación H2: el responsable del proyecto, 2026-11-07 (equipo de una persona, `AGENTS.md` §4.3).

## Ajustes durante la implementación

_Salieron de la revisión adversarial y de las cuatro lentes de la implementación (2026-11-07); el resumen está en la descripción del PR._

- **Archivos ocultos de `backend/`:** un archivo oculto directamente en `backend/` (`.gitattributes`, `.gitignore`) también configura al BFF que vive dentro, así que `aplica.sh` lo trata como desconocido y hace aplicar a todos. Los de subcarpetas (`.mvn/`) siguen siendo del backend.
- **Nombre del trabajo de contratos:** en `backend-contracts-pr` es `contratos · cambios` (el proyecto de `aplica.sh` es `contratos`), para no repetir `backend · cambios` de `backend-pr`. No es un check obligatorio.
- **Agregado:** el paso de `backend · contratos` lleva `if: always()`, para que corra también en una ejecución cancelada.
- **`git diff … --`:** el `--` evita que un archivo llamado `HEAD` en la raíz haga fallar la consulta.
- **Arnés:** además de `aplica.sh`, comprueba por texto que los cinco *workflows* conservan la condición de omisión, `needs: cambios` y los nombres de los trabajos; aborta si falla la preparación de un caso; compara la salida estándar y la de error juntas.
- **`sistema · scripts de CI`:** `timeout-minutes: 10`.
- **Pendiente para T6:** demostrar, antes de T5, el camino de omisión en GitHub (PR de solo `docs/`), y confirmar qué conclusión queda en una cancelación forzada de `cambios` con `verify` aún sin empezar.
