#!/usr/bin/env bash
# Pruebas de la caché de Maven del CI (issue 239): .github/workflows/maven-cache-main.yml y los workflows que la usan.
#
# Un solo productor de la caché de Maven en main: solo maven-cache-main.yml la escribe, los verify de los PR la
# restauran con la misma clave y los demás trabajos la leen con cache-read-only o no la usan. Esta prueba lee los
# workflows y falla si alguna de esas reglas deja de cumplirse. No ejecuta nada: para comprobar que cada regla muerde,
# arma copias de los workflows con un defecto y exige que la regla las rechace (modelo: ui-release.test.sh).
#
# La ejecuta sistema-pr.yml dos veces: la de la punta de la base (HEAD^1), para que un PR no la debilite sin querer al
# relajar un workflow, y la del PR. Lee los workflows del árbol de trabajo, que son los del PR. No impide que un PR
# quite el paso de sistema-pr.yml: esa barrera es la revisión humana (doc. 04 §9). Los textos que las mutaciones
# buscan en los workflows no son valores que un PR legítimo cambie (SHA, tiempos): salen del propio archivo.
# Un PR que cambia a la vez un workflow y algo que una regla exige de él (el nombre de un trabajo, un trabajo que una
# mutación necesita, el `if` del verify) no pasa la prueba de la base: se divide en dos PR, primero la prueba y después
# el workflow.
#
# Uso: bash .github/scripts/test/maven-cache.test.sh [carpeta-de-workflows]
# Sale con 0 si todo pasa y con 1 si algo falla.
set -u

aqui=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
real=${1:-"$aqui/../../workflows"}
if [ ! -f "$real/maven-cache-main.yml" ]; then
  echo "FALTA $real/maven-cache-main.yml: no hay nada que probar." >&2
  exit 1
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Lee el archivo sin los retornos de carro (en Windows, Git puede dejar CRLF).
leer() { tr -d '\r' < "$1"; }

# Identificadores de los trabajos del workflow $1.
trabajos() {
  leer "$1" | awk '
    /^jobs:/ { d = 1; next }
    { l = $0; sub(/[ \t]+#.*$/, "", l); sub(/[ \t]+$/, "", l); gsub(/["\047]/, "", l) }
    d && l ~ /^  [A-Za-z0-9_-]+:$/ { sub(/^  /, "", l); sub(/:.*/, "", l); print l }'
}

# Líneas del trabajo $2 del workflow $1 (entre su encabezado de dos espacios y el siguiente), sin los comentarios.
bloque() {
  leer "$1" | awk -v job="  $2:" '
    /^jobs:/ { enjobs = 1; next }
    { l = $0; sub(/[ \t]+#.*$/, "", l); sub(/[ \t]+$/, "", l); gsub(/["\047]/, "", l) }
    enjobs && l == job { dentro = 1; next }
    dentro && /^  [A-Za-z0-9_-]+:/ { exit }
    dentro && !/^ *#/ { print }'
}

# Pasos (que empiezan con «      - ») de la entrada estándar, cada uno terminado con el separador de registros (036).
pasos() {
  awk '
    /^      - / { if (buf != "") printf "%s\036", buf; buf = "" }
    { buf = buf $0 "\n" }
    END { if (buf != "") printf "%s\036", buf }'
}

# Pasos de la entrada estándar donde alguna línea cumple la expresión $1.
pasos_con() {
  awk -v re="$1" '
    /^      - / { if (hit) printf "%s", buf; buf = ""; hit = 0 }
    { buf = buf $0 "\n"; if ($0 ~ re) hit = 1 }
    END { if (hit) printf "%s", buf }'
}

# Valor de cache-dependency-path del paso de la entrada estándar, en una línea con las rutas separadas por «,».
ruta_cache() {
  awk '
    /^ +cache-dependency-path:/ {
      sub(/^ +cache-dependency-path: */, "")
      if ($0 != "|" && $0 != "") { out = $0; dentro = 0 } else { dentro = 1 }
      next
    }
    dentro && /^ +[A-Za-z-]+:/ { dentro = 0 }
    dentro { gsub(/^ +| +$/, ""); out = out (out == "" ? "" : ",") $0 }
    END { print out }'
}

# Eventos (las claves de «on:») del workflow $1, ordenados y cada uno seguido de un espacio.
eventos() {
  leer "$1" | awk '/^on:/ { d = 1; next } d && /^[A-Za-z]/ { exit } d && !/^ *#/ { print }' \
    | grep -E '^  [A-Za-z_]+:' | sed -E 's/^  ([A-Za-z_]+):.*/\1/' | sort | tr '\n' ' '
}

# Permisos de escritura en cualquiera de sus formas (también «{ contents: write }»).
ESCRIBE='(^|[^A-Za-z-])write([^A-Za-z-]|$)|write-all'

# Comando de mvnw de la entrada estándar (una línea «run: … mvnw …»).
comando() { grep -E '^ +(- )?run: .*mvnw' | sed -E 's/^ +(- )?run: //'; }

# Los checks obligatorios del ruleset «main» (doc. 04 §9): sus nombres no cambian.
OBLIGATORIOS=(
  'sistema · título Conventional Commits' 'sistema · secretos' 'sistema · scripts de CI'
  'backend · verify' 'bff · verify' 'frontend · verify' 'ui · verify' 'backend · contratos'
)

# Quién escribe la caché de Maven: el productor de main y el verify de cada PR. Cualquier otro setup-java con caché
# la lee con cache-read-only.
escribe_cache() {
  case "$1" in
    maven-cache-main.yml:backend | maven-cache-main.yml:bff | backend-pr.yml:verify | bff-pr.yml:verify) return 0 ;;
    *) return 1 ;;
  esac
}

# Revisa los workflows de la carpeta $1 y escribe una línea por cada regla que incumple. Sin líneas, todo en orden.
revisar() {
  local d=$1 f base job contenido paso
  local mc="$d/maven-cache-main.yml"

  # --- toda la carpeta: quién escribe la caché y quién no ---
  local top limpio escritura pr_wf
  ls "$d"/*.yaml > /dev/null 2>&1 && echo "hay un workflow .yaml: las reglas solo leen los .yml"
  for f in "$d"/*.yml; do
    base=$(basename "$f")
    top=$(leer "$f" | awk '/^permissions:/ { d = 1; print; next } d && /^[A-Za-z]/ { exit } d && !/^ *#/ { print }' \
      | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//' | tr -d "'\"")
    # Un workflow es «de PR» solo si ese es su único evento: con push, schedule o workflow_dispatch también corre fuera de los PR.
    pr_wf=0
    [ "$(eventos "$f")" = "pull_request " ] && pr_wf=1
    for job in $(trabajos "$f"); do
      contenido=$(bloque "$f" "$job")
      # Sin comentarios al final de línea ni comillas: «contents: 'write' # x» también es escritura.
      limpio=$(sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//' <<<"$contenido" | tr -d "'\"")
      # Permisos efectivos: los del trabajo o, si no declara ninguno, los del workflow (y sin ellos, los de escritura por omisión).
      escritura=0
      grep -Eq "$ESCRIBE" <<<"$limpio" && escritura=1
      if ! grep -Eq '^    permissions:' <<<"$limpio"; then
        { [ -z "$top" ] || grep -Eq "$ESCRIBE" <<<"$top"; } && escritura=1
      fi
      # 239-02: un trabajo con permisos de escritura no ejecuta nada que otro trabajo haya podido escribir en la caché.
      if [ "$escritura" -eq 1 ] && grep -Eq '^ +(cache|cache-dependency-path|cache-path):|^ +cache-jdk: *true|^ +package-manager-cache: *true|actions/cache(/restore|/save)?@' <<<"$limpio"; then
        echo "$base · $job: tiene permisos de escritura y usa caché"
      fi
      if [ "$escritura" -eq 1 ] && [ "$(grep -c 'actions/setup-node@' <<<"$limpio")" -ne "$(grep -Ec '^ +package-manager-cache: false$' <<<"$limpio")" ]; then
        echo "$base · $job: tiene permisos de escritura y algún setup-node no desactiva la caché (package-manager-cache: false)"
      fi
      # Fuera de los PR solo maven-cache-main.yml escribe cachés: nada de actions/cache en los demás.
      if [ "$pr_wf" -eq 0 ] && [ "$base" != maven-cache-main.yml ] && grep -Eq 'actions/cache(/restore|/save)?@' <<<"$limpio"; then
        echo "$base · $job: un workflow que no es de PR usa actions/cache"
      fi
      while IFS= read -r -d $'\036' paso; do
        grep -q 'actions/setup-java@' <<<"$paso" || continue
        grep -Eq '^ +cache: |^ +cache-jdk: *true' <<<"$paso" || continue
        if escribe_cache "$base:$job"; then
          grep -q 'cache-read-only' <<<"$paso" && echo "$base · $job: el trabajo que escribe la caché no puede ser de solo lectura"
        else
          grep -Eq '^ +cache-read-only: true$' <<<"$paso" || echo "$base · $job: setup-java con caché de Maven sin cache-read-only: true"
        fi
      done < <(pasos <<<"$contenido")
    done
  done

  # --- los check obligatorios conservan su nombre ---
  local nombre
  for nombre in "${OBLIGATORIOS[@]}"; do
    if ! cat "$d"/*.yml | tr -d '\r' | grep -Fxq "    name: $nombre"; then
      echo "falta el trabajo del check obligatorio «$nombre»"
    fi
  done

  # --- el verify de cada PR y su pareja en maven-cache-main.yml (239-03) ---
  local par p pr cmd dir vb cmds rp ps sha filler comprobar fb cb id pasoj pasov n
  for par in 'backend|backend-pr.yml|sh mvnw -B -ntp verify|backend' 'bff|bff-pr.yml|./mvnw -B -ntp verify|backend/bff'; do
    IFS='|' read -r p pr cmd dir <<<"$par"
    if [ ! -f "$d/$pr" ]; then echo "falta $pr"; continue; fi
    vb=$(bloque "$d/$pr" verify)
    cmds=$(comando <<<"$vb")
    [ "$cmds" = "$cmd" ] || echo "$pr · verify: el comando no es exactamente «$cmd» (es «${cmds:-ninguno}»)"
    grep -Eq 'continue-on-error|retry|nick-fields|wretry' <<<"$vb" && echo "$pr · verify: puede seguir tras un fallo o reintentar"
    grep -E '^ +(- )?run: ' <<<"$vb" | grep -Eq '[|][|]|;' && echo "$pr · verify: un run con || o ; puede seguir tras un fallo"
    grep -q 'maven.test.failure.ignore' <<<"$vb" && echo "$pr · verify: ignora los fallos de las pruebas"
    # El trabajo no se omite ni cambia de comportamiento por fuera del comando: su «if» es el de aplica.sh, el paso del
    # verify no tiene «if» y nada cambia Maven con variables de entorno.
    grep -Fxq "    if: \${{ always() && (needs.cambios.result != 'success' || needs.cambios.outputs.aplica != 'false') }}" <<<"$vb" \
      || echo "$pr · verify: su condición «if» no es la de aplica.sh (un trabajo omitido cuenta como aprobado)"
    pasoj=$(pasos_con 'mvnw' <<<"$vb")
    grep -Eq '^ +if:' <<<"$pasoj" && echo "$pr · verify: el paso del verify tiene un «if»"
    grep -Eiq 'MAVEN_OPTS|MAVEN_ARGS|MAVEN_CONFIG|skipTests|maven[.]test[.]skip|GITHUB_ENV|^ +env:' <<<"$vb" \
      && echo "$pr · verify: cambia Maven con variables de entorno o env"
    leer "$d/$pr" | grep -v '^ *#' | grep -Eq '^env:' && echo "$pr: tiene env a nivel del workflow"
    leer "$d/$pr" | grep -v '^ *#' | grep -Eq '^ +shell:' && echo "$pr: cambia la shell (pierde -e y pipefail)"
    [ "$(eventos "$d/$pr")" = "pull_request " ] || echo "$pr: no corre solo con pull_request, y su verify guardaría la caché de Maven en otros eventos"
    ps=$(pasos_con 'actions/setup-java@' <<<"$vb")
    rp=$(ruta_cache <<<"$ps")
    [ "$rp" = "$dir/pom.xml,$dir/.mvn/wrapper/maven-wrapper.properties" ] \
      || echo "$pr · verify: la clave de la caché no es el pom.xml y el maven-wrapper.properties de $dir (es «$rp»)"
    sha=$(grep -o 'setup-java@[0-9a-f]\{40\}' <<<"$ps")

    filler=$(bloque "$mc" "$p")
    comprobar=$(bloque "$mc" "$p-comprobar")
    [ -n "$filler" ] || echo "maven-cache-main.yml: falta el trabajo $p"
    [ -n "$comprobar" ] || echo "maven-cache-main.yml: falta el trabajo $p-comprobar"

    # El trabajo que llena la caché: misma clave, mismo setup-java y el mismo verify que su PR.
    fb=$(pasos_con 'actions/setup-java@' <<<"$filler")
    [ "$(ruta_cache <<<"$fb")" = "$rp" ] || echo "maven-cache-main.yml · $p: su cache-dependency-path no es el del verify de $pr"
    [ "$(grep -o 'setup-java@[0-9a-f]\{40\}' <<<"$fb")" = "$sha" ] || echo "maven-cache-main.yml · $p: su setup-java no es el de $pr"
    pasoj=$(pasos_con 'verify' <<<"$filler")
    cmds=$(comando <<<"$pasoj")
    # Baja la distribución de Maven del wrapper sin condición, para que su caché se reconstruya sola.
    pasov=$(pasos_con 'mvnw .*-v$' <<<"$filler")
    { grep -Eq '^ +(- )?run: .*mvnw -B -v$' <<<"$pasov" && ! grep -Eq '^ +if:' <<<"$pasov"; } \
      || echo "maven-cache-main.yml · $p: no baja la distribución del wrapper sin condición (mvnw -B -v)"
    grep -q -- '-Dmaven.test.failure.ignore=true' <<<"$cmds" || echo "maven-cache-main.yml · $p: su verify no usa -Dmaven.test.failure.ignore=true"
    [ "${cmds/ -Dmaven.test.failure.ignore=true/}" = "$cmd" ] || echo "maven-cache-main.yml · $p: su verify no es el de $pr (es «$cmds»)"
    id=$(grep -Eo '^ +(- )?id: [A-Za-z0-9_-]+' <<<"$fb" | sed -E 's/.*id: //')
    grep -Eq "^ +if: steps\\.$id\\.outputs\\.cache-hit != 'true'\$" <<<"$pasoj" && [ -n "$id" ] \
      || echo "maven-cache-main.yml · $p: su verify no se omite con if: steps.<id>.outputs.cache-hit != 'true'"
    grep -q 'continue-on-error' <<<"$filler" && echo "maven-cache-main.yml · $p: tiene continue-on-error"

    # El trabajo que comprueba (239-01): misma clave, solo lectura y verify sin conexión.
    cb=$(pasos_con 'actions/setup-java@' <<<"$comprobar")
    grep -Eq "^    needs: $p\$" <<<"$comprobar" || echo "maven-cache-main.yml · $p-comprobar: no depende de $p (needs)"
    [ "$(ruta_cache <<<"$cb")" = "$rp" ] || echo "maven-cache-main.yml · $p-comprobar: su cache-dependency-path no es el del verify de $pr"
    [ "$(grep -o 'setup-java@[0-9a-f]\{40\}' <<<"$cb")" = "$sha" ] || echo "maven-cache-main.yml · $p-comprobar: su setup-java no es el de $pr"
    grep -Eq '^ +cache-read-only: true$' <<<"$cb" || echo "maven-cache-main.yml · $p-comprobar: no restaura con cache-read-only: true"
    # Sin conexión (-o) y con -Dmaven.test.failure.ignore=true: una prueba inestable no se confunde con una caché incompleta.
    cmds=$(comando <<<"$comprobar")
    n=${cmds/ -o / }
    n=${n/ -Dmaven.test.failure.ignore=true/}
    [ "$n" = "$cmd" ] && [[ "$cmds" == *" -o "* ]] && [[ "$cmds" == *"-Dmaven.test.failure.ignore=true"* ]] \
      || echo "maven-cache-main.yml · $p-comprobar: su verify no es el de $pr sin conexión (-o) y con -Dmaven.test.failure.ignore=true (es «$cmds»)"
    pasos_con 'mvnw' <<<"$comprobar" | grep -Eq '^ +MVNW_REPOURL: ' \
      || echo "maven-cache-main.yml · $p-comprobar: su verify no usa MVNW_REPOURL, y mvnw podría bajar Maven de Maven Central"
    # Sin JDK en la caché de main: los ejecutables que restauran los PR no los guarda este workflow.
    [ "$(grep -c 'actions/setup-java@' <<<"$filler")" -eq "$(grep -Ec '^ +cache-jdk: false$' <<<"$filler")" ] \
      || echo "maven-cache-main.yml · $p: algún setup-java no tiene cache-jdk: false"
    [ "$(grep -c 'actions/setup-java@' <<<"$comprobar")" -eq "$(grep -Ec '^ +cache-jdk: false$' <<<"$comprobar")" ] \
      || echo "maven-cache-main.yml · $p-comprobar: algún setup-java no tiene cache-jdk: false"
    grep -q 'continue-on-error' <<<"$comprobar" && echo "maven-cache-main.yml · $p-comprobar: tiene continue-on-error"
  done

  # --- gitleaks sin la caché de su binario (la acción no la comprueba ni tiene opción para apagarla) ---
  local sec linea_tmp linea_gl
  sec=$(bloque "$d/sistema-pr.yml" secrets)
  linea_tmp=$(grep -n 'TMPDIR=.*mktemp -d' <<<"$sec" | head -1 | cut -d: -f1)
  linea_gl=$(grep -n 'gitleaks-action@' <<<"$sec" | head -1 | cut -d: -f1)
  { [ -n "$linea_tmp" ] && [ -n "$linea_gl" ] && [ "$linea_tmp" -lt "$linea_gl" ]; } \
    || echo "sistema-pr.yml · secrets: gitleaks sin un directorio temporal aleatorio (TMPDIR) antes de la acción: restauraría su binario de la caché"

  # --- maven-cache-main.yml solo corre en main, con permisos de lectura (239-04) ---
  local on_block claves perms conc otro
  on_block=$(leer "$mc" | awk '/^on:/ { d = 1; next } d && /^[A-Za-z]/ { exit } d && !/^ *#/ { print }')
  claves=$(grep -E '^  [A-Za-z_]+:' <<<"$on_block" | sed -E 's/^  ([A-Za-z_]+):.*/\1/' | sort | tr '\n' ' ')
  [ "$claves" = "push schedule workflow_dispatch " ] \
    || echo "maven-cache-main.yml: se dispara con «${claves}» y solo puede ser push, schedule y workflow_dispatch"
  grep -Eq '^    branches: \[main\]$' <<<"$on_block" || echo "maven-cache-main.yml: el push no es solo a main"
  perms=$(leer "$mc" | awk '/^permissions:/ { d = 1; print; next } d && /^[A-Za-z]/ { exit } d && !/^ *#/ { print }')
  [ "$perms" = $'permissions:\n  contents: read' ] || echo "maven-cache-main.yml: los permisos del workflow no son solo contents: read"
  leer "$mc" | grep -v '^ *#' | grep -Eq ': *write$|write-all' && echo "maven-cache-main.yml: tiene permisos de escritura"
  for job in $(trabajos "$mc"); do
    contenido=$(bloque "$mc" "$job")
    grep -Eq "^    if: github\\.ref == 'refs/heads/main'\$" <<<"$contenido" \
      || echo "maven-cache-main.yml · $job: su condición «if» no exige github.ref == 'refs/heads/main'"
    grep -Eq '^    timeout-minutes: [0-9]+$' <<<"$contenido" || echo "maven-cache-main.yml · $job: no tiene timeout-minutes"
    # Un permiso a nivel del trabajo solo puede repetir contents: read.
    if awk '/^    permissions:/ { d = 1; next } d && /^      [A-Za-z-]+:/ { print; next } d { d = 0 }' <<<"$contenido" \
      | grep -Fvx '      contents: read' | grep -q .; then
      echo "maven-cache-main.yml · $job: amplía los permisos más allá de contents: read"
    fi
  done
  conc=$(leer "$mc" | awk '/^concurrency:/ { d = 1; next } d && /^[A-Za-z]/ { exit } d && !/^ *#/ { print }')
  grep -Eq '^  group: maven-cache-main$' <<<"$conc" || echo "maven-cache-main.yml: no tiene su propio grupo de concurrency (maven-cache-main)"
  grep -Eq '^  cancel-in-progress: false$' <<<"$conc" || echo "maven-cache-main.yml: la concurrency cancela la ejecución en curso"
  leer "$mc" | grep -v '^ *#' | grep -q 'cancel-in-progress: *true' && echo "maven-cache-main.yml: algún cancel-in-progress es true"
  for f in "$d"/*.yml; do
    [ "$(basename "$f")" = maven-cache-main.yml ] && continue
    otro=$(leer "$f" | grep -v '^ *#' | grep -E 'group: *maven-cache-main' || true)
    [ -z "$otro" ] || echo "$(basename "$f"): comparte el grupo de concurrency de maven-cache-main.yml"
  done
  leer "$mc" | grep -v '^ *#' | grep -E '^ +(- )?uses: ' | grep -Ev '@[0-9a-f]{40}( |$)' | grep -q . \
    && echo "maven-cache-main.yml: una acción no está fijada por SHA"
  return 0
}

# ---------------------------------------------------------------------------------------------------------------
total=0
fallos=0
ok() { total=$((total + 1)); echo "ok    - $1"; }
falla() { total=$((total + 1)); fallos=$((fallos + 1)); echo "FALLA - $1"; echo "        $2"; }

# Reemplaza en el trabajo $2 del archivo $1 (con «-» para todo el archivo) el texto $3 por $4 (literal; todas las
# apariciones). Los textos viajan por el entorno: así no dependen de cómo el awk del ejecutor lee las opciones -v.
muta() {
  local archivo=$1 job=$2 desde=$3 hasta=$4 destino="$tmp/mutado.yml"
  leer "$archivo" | JOB="  $job:" TODO="$([ "$job" = - ] && echo 1 || echo 0)" DESDE="$desde" HASTA="$hasta" awk '
    BEGIN { job = ENVIRON["JOB"]; todo = ENVIRON["TODO"]; desde = ENVIRON["DESDE"]; hasta = ENVIRON["HASTA"] }
    /^jobs:/ { enjobs = 1 }
    { l = $0; sub(/[ \t]+#.*$/, "", l); sub(/[ \t]+$/, "", l) }
    enjobs && l == job { dentro = 1; print; next }
    dentro && /^  [A-Za-z0-9_-]+:/ { dentro = 0 }
    {
      linea = $0
      if (dentro || todo == 1) {
        while ((i = index(linea, desde)) > 0) {
          linea = substr(linea, 1, i - 1) hasta substr(linea, i + length(desde))
          cambios++
          if (index(hasta, desde) > 0) break
        }
      }
      print linea
    }
    END { if (!cambios) exit 3 }' > "$destino" || { echo "ERROR del arnés: no se encontró «$desde» en $archivo ($job)" >&2; exit 2; }
  cp "$destino" "$archivo"
}

# Agrega el texto $3 (líneas completas) justo después de «    steps:» del trabajo $2 del archivo $1.
agrega_paso() {
  local archivo=$1 job=$2 texto=$3 destino="$tmp/mutado.yml"
  leer "$archivo" | JOB="  $job:" TEXTO="$texto" awk '
    BEGIN { job = ENVIRON["JOB"]; texto = ENVIRON["TEXTO"] }
    /^jobs:/ { enjobs = 1 }
    enjobs && $0 == job { dentro = 1 }
    dentro && /^    steps:$/ { print; print texto; dentro = 0; hecho = 1; next }
    { print }
    END { if (!hecho) exit 3 }' > "$destino" || { echo "ERROR del arnés: no hay «steps:» en $job de $archivo" >&2; exit 2; }
  cp "$destino" "$archivo"
}

# Los SHA de las acciones salen del propio workflow: la prueba no congela los que hay hoy, para que subir una acción no
# la rompa.
SHA_JAVA=$(grep -ho 'actions/setup-java@[0-9a-f]\{40\}' "$real/maven-cache-main.yml" | head -1)
SHA_CHECKOUT=$(grep -ho 'actions/checkout@[0-9a-f]\{40\}' "$real/maven-cache-main.yml" | head -1)
SHA_NULO=0000000000000000000000000000000000000000

# Los workflows reales cumplen todas las reglas.
salida=$(revisar "$real")
if [ -z "$salida" ]; then
  ok "los workflows cumplen las reglas de la caché de Maven (239)"
else
  falla "los workflows cumplen las reglas de la caché de Maven (239)" "$salida"
fi

# Cada defecto, aplicado a una copia de los workflows, tiene que ser rechazado por su regla.
# $1: nombre; $2: fragmento que debe aparecer entre los reportes; el resto: la mutación (función y argumentos), a la que
# se le agrega la carpeta de la copia como primer argumento después de su nombre.
rechaza() {
  local nombre=$1 esperado=$2 mutacion=$3
  shift 3
  local copia="$tmp/w"
  rm -rf "$copia"
  mkdir -p "$copia"
  cp "$real"/*.yml "$copia"/
  "$mutacion" "$copia" "$@" || exit 2
  local salida
  salida=$(revisar "$copia")
  if grep -qF -- "$esperado" <<<"$salida"; then
    ok "rechaza: $nombre"
  else
    falla "rechaza: $nombre" "esperaba «$esperado» y revisar() dijo: ${salida:-nada}"
  fi
}

# Mutaciones: reciben la carpeta de la copia y después el archivo, el trabajo y el texto.
reemplaza() { muta "$1/$2" "$3" "$4" "$5"; }
agrega() { agrega_paso "$1/$2" "$3" "$4"; }
quita_archivo() { rm -f "$1/$2"; }
copia_como_yaml() { cp "$1/$2" "$1/otro.yaml"; }
# Los defectos que esconden la escritura: un comentario, comillas o un encabezado con comentario.
CACHE_SOLO_LECTURA=$'      - uses: actions/setup-java@0000000000000000000000000000000000000000\n        with:\n          cache: maven\n          cache-read-only: true'
escritura_con_comentario() { agrega "$1" backend-main.yml contracts "$CACHE_SOLO_LECTURA" && reemplaza "$1" backend-main.yml contracts 'contents: write' 'contents: write # releases'; }
escritura_entre_comillas() { agrega "$1" backend-main.yml contracts "$CACHE_SOLO_LECTURA" && reemplaza "$1" backend-main.yml contracts 'contents: write' "contents: 'write'"; }
escritura_con_espacios() { agrega "$1" backend-main.yml contracts "$CACHE_SOLO_LECTURA" && reemplaza "$1" backend-main.yml contracts 'contents: write' 'contents: write   '; }
encabezado_con_comentario() { agrega "$1" backend-main.yml contracts "$CACHE_SOLO_LECTURA" && reemplaza "$1" backend-main.yml - '  contracts:' '  contracts: # releases'; }
evento_extra_y_cache() { reemplaza "$1" backend-contracts-pr.yml - '  pull_request:' $'  push:\n  pull_request:' && agrega "$1" backend-contracts-pr.yml test "      - uses: actions/cache@$SHA_NULO"; }
escritura_en_linea() {
  agrega "$1" backend-main.yml schemas "$CACHE_SOLO_LECTURA" && reemplaza "$1" backend-main.yml schemas '    permissions:' '    permissions: { contents: write } #' \
    && reemplaza "$1" backend-main.yml schemas '      contents: write' ''
}
escritura_del_workflow() { agrega "$1" backend-pr.yml cambios "$CACHE_SOLO_LECTURA" && reemplaza "$1" backend-pr.yml - '  contents: read' '  contents: write'; }

# 239-02: los trabajos con permisos de escritura no usan la caché; la leen o no la usan, nunca la escriben.
rechaza "publicar contratos con caché de Maven" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  agrega backend-main.yml contracts "$CACHE_SOLO_LECTURA"
rechaza "un trabajo de escritura con actions/cache" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  agrega backend-main.yml contracts "      - uses: actions/cache@$SHA_NULO"
rechaza "un trabajo de escritura con actions/cache/restore" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  agrega backend-main.yml contracts "      - uses: actions/cache/restore@$SHA_NULO"
rechaza "un trabajo de escritura con cache-jdk" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  agrega backend-main.yml contracts $'      - uses: actions/setup-java@0000000000000000000000000000000000000000\n        with:\n          cache-jdk: true\n          cache-read-only: true'
rechaza "escritura con un comentario al final" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  escritura_con_comentario
rechaza "escritura entre comillas" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  escritura_entre_comillas
rechaza "trabajo con encabezado con comentario" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  encabezado_con_comentario
rechaza "escritura heredada del workflow" "backend-pr.yml · cambios: tiene permisos de escritura y usa caché" \
  escritura_del_workflow
rechaza "escritura con espacios al final" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  escritura_con_espacios
rechaza "escritura en un mapa en línea" "backend-main.yml · schemas: tiene permisos de escritura y usa caché" \
  escritura_en_linea
rechaza "workflow de PR que también corre con push y usa actions/cache" "backend-contracts-pr.yml · test: un workflow que no es de PR usa actions/cache" \
  evento_extra_y_cache
rechaza "backend-pr que también corre con push" "backend-pr.yml: no corre solo con pull_request" \
  reemplaza backend-pr.yml - '  pull_request:' $'  push:\n  pull_request:'
rechaza "setup-node con caché en un trabajo de escritura" "ui-release.yml · publicar: tiene permisos de escritura y algún setup-node no desactiva la caché" \
  reemplaza ui-release.yml publicar 'package-manager-cache: false' ''
rechaza "verify de bff-pr con otra shell" "bff-pr.yml: cambia la shell" \
  reemplaza bff-pr.yml verify '        run: ./mvnw' $'        shell: bash {0}\n        run: ./mvnw'
rechaza "gitleaks sin tmpdir aleatorio" "sistema-pr.yml · secrets: gitleaks sin un directorio temporal aleatorio" \
  reemplaza sistema-pr.yml secrets 'TMPDIR=' 'OTRA='
rechaza "caché de backend sin bajar el wrapper" "maven-cache-main.yml · backend: no baja la distribución del wrapper sin condición" \
  reemplaza maven-cache-main.yml backend 'sh mvnw -B -v' 'echo omitido'
rechaza "caché de bff con la distribución del wrapper condicionada" "maven-cache-main.yml · bff: no baja la distribución del wrapper sin condición" \
  reemplaza maven-cache-main.yml bff '      - name: Distribución de Maven del wrapper' $'      - name: Distribución de Maven del wrapper\n        if: false'
rechaza "comprobar caché de backend sin MVNW_REPOURL" "maven-cache-main.yml · backend-comprobar: su verify no usa MVNW_REPOURL" \
  reemplaza maven-cache-main.yml backend-comprobar '          MVNW_REPOURL: ' '          OTRA: '
rechaza "actions/cache en un workflow que no es de PR" "backend-main.yml · schemas: un workflow que no es de PR usa actions/cache" \
  agrega backend-main.yml schemas "      - uses: actions/cache/save@$SHA_NULO"
rechaza "un workflow .yaml" "hay un workflow .yaml" \
  copia_como_yaml backend-main.yml
rechaza "pruebas del comparador guarda caché" "backend-contracts-pr.yml · test: setup-java con caché de Maven sin cache-read-only: true" \
  reemplaza backend-contracts-pr.yml test '          cache-read-only: true' ''
rechaza "pruebas del comparador con cache-read-only: false" "backend-contracts-pr.yml · test: setup-java con caché de Maven sin cache-read-only: true" \
  reemplaza backend-contracts-pr.yml test 'cache-read-only: true' 'cache-read-only: false'
rechaza "otro workflow fuera de PR con caché que escribe" "backend-main.yml · schemas: setup-java con caché de Maven sin cache-read-only: true" \
  agrega backend-main.yml schemas $'      - uses: actions/setup-java@0000000000000000000000000000000000000000\n        with:\n          cache: maven'
rechaza "el trabajo que llena la caché en solo lectura" "maven-cache-main.yml · backend: el trabajo que escribe la caché no puede ser de solo lectura" \
  reemplaza maven-cache-main.yml backend '          cache: maven' $'          cache: maven\n          cache-read-only: true'
rechaza "el verify de un PR en solo lectura" "backend-pr.yml · verify: el trabajo que escribe la caché no puede ser de solo lectura" \
  reemplaza backend-pr.yml verify '          cache: maven' $'          cache: maven\n          cache-read-only: true'

# 239-03: el verify de los PR no cambia (salvo su clave) y los checks obligatorios conservan su nombre.
rechaza "verify de bff-pr con otro comando" "bff-pr.yml · verify: el comando no es exactamente" \
  reemplaza bff-pr.yml verify './mvnw -B -ntp verify' './mvnw -B -ntp -DskipTests verify'
rechaza "verify de backend-pr con otro comando" "backend-pr.yml · verify: el comando no es exactamente" \
  reemplaza backend-pr.yml verify 'sh mvnw -B -ntp verify' 'sh mvnw -B -ntp -o verify'
rechaza "verify de bff-pr con continue-on-error en el paso" "bff-pr.yml · verify: puede seguir tras un fallo o reintentar" \
  reemplaza bff-pr.yml verify '        run: ./mvnw' $'        continue-on-error: true\n        run: ./mvnw'
rechaza "verify de backend-pr con continue-on-error en el trabajo" "backend-pr.yml · verify: puede seguir tras un fallo o reintentar" \
  reemplaza backend-pr.yml verify '    timeout-minutes: ' $'    continue-on-error: true\n    timeout-minutes: '
rechaza "verify de bff-pr con reintento" "bff-pr.yml · verify: puede seguir tras un fallo o reintentar" \
  reemplaza bff-pr.yml verify './mvnw -B -ntp verify' './mvnw -B -ntp verify # retry'
rechaza "verify de backend-pr con || true" "backend-pr.yml · verify: un run con || o ; puede seguir tras un fallo" \
  reemplaza backend-pr.yml verify 'sh mvnw -B -ntp verify' 'sh mvnw -B -ntp verify || true'
rechaza "verify de backend-pr con ; después del comando" "backend-pr.yml · verify: un run con || o ; puede seguir tras un fallo" \
  reemplaza backend-pr.yml verify 'sh mvnw -B -ntp verify' 'sh mvnw -B -ntp verify; true'
rechaza "verify de bff-pr con las pruebas ignoradas" "bff-pr.yml · verify: ignora los fallos de las pruebas" \
  reemplaza bff-pr.yml verify './mvnw -B -ntp verify' './mvnw -B -ntp -Dmaven.test.failure.ignore=true verify'
rechaza "verify de bff-pr con if: false en el paso" "bff-pr.yml · verify: el paso del verify tiene un «if»" \
  reemplaza bff-pr.yml verify '        run: ./mvnw' $'        if: false\n        run: ./mvnw'
rechaza "verify de backend-pr omitido por su condición" "backend-pr.yml · verify: su condición «if» no es la de aplica.sh" \
  reemplaza backend-pr.yml verify 'always() && (needs' 'false && (needs'
rechaza "verify de backend-pr con MAVEN_ARGS" "backend-pr.yml · verify: cambia Maven con variables de entorno o env" \
  reemplaza backend-pr.yml verify '    timeout-minutes: ' $'    env:\n      MAVEN_ARGS: -DskipTests\n    timeout-minutes: '
rechaza "bff-pr con env a nivel del workflow" "bff-pr.yml: tiene env a nivel del workflow" \
  reemplaza bff-pr.yml - 'concurrency:' $'env:\n  MAVEN_ARGS: -DskipTests\nconcurrency:'
rechaza "verify de backend-pr sin el wrapper en la clave" "backend-pr.yml · verify: la clave de la caché no es el pom.xml y el maven-wrapper.properties" \
  reemplaza backend-pr.yml verify '            backend/.mvn/wrapper/maven-wrapper.properties' ''
rechaza "verify de bff-pr con otra clave" "bff-pr.yml · verify: la clave de la caché no es el pom.xml y el maven-wrapper.properties" \
  reemplaza bff-pr.yml verify 'backend/bff/pom.xml' 'backend/pom.xml'
rechaza "cambia el nombre de bff · verify" "falta el trabajo del check obligatorio «bff · verify»" \
  reemplaza bff-pr.yml verify 'name: bff · verify' 'name: bff · construir'
rechaza "cambia el nombre de backend · contratos" "falta el trabajo del check obligatorio «backend · contratos»" \
  reemplaza backend-contracts-pr.yml contratos 'name: backend · contratos' 'name: backend · contratos (Q08)'
rechaza "quita sistema-pr.yml" "falta el trabajo del check obligatorio «sistema · scripts de CI»" \
  quita_archivo sistema-pr.yml

# Las parejas: el trabajo que llena y el que comprueba son los de su PR.
rechaza "caché de backend con otra clave" "maven-cache-main.yml · backend: su cache-dependency-path no es el del verify de backend-pr.yml" \
  reemplaza maven-cache-main.yml backend '            backend/.mvn/wrapper/maven-wrapper.properties' ''
rechaza "caché de bff con otro SHA de setup-java" "maven-cache-main.yml · bff: su setup-java no es el de bff-pr.yml" \
  reemplaza maven-cache-main.yml bff "$SHA_JAVA" "actions/setup-java@$SHA_NULO"
rechaza "comprobar caché de backend con otro SHA de setup-java" "maven-cache-main.yml · backend-comprobar: su setup-java no es el de backend-pr.yml" \
  reemplaza maven-cache-main.yml backend-comprobar "$SHA_JAVA" "actions/setup-java@$SHA_NULO"
rechaza "caché de bff con otro comando" "maven-cache-main.yml · bff: su verify no es el de bff-pr.yml" \
  reemplaza maven-cache-main.yml bff 'ignore=true verify' 'ignore=true install'
rechaza "caché de backend sin ignorar las pruebas" "maven-cache-main.yml · backend: su verify no usa -Dmaven.test.failure.ignore=true" \
  reemplaza maven-cache-main.yml backend ' -Dmaven.test.failure.ignore=true' ''
rechaza "caché de backend sin omitir el verify con cache-hit" "maven-cache-main.yml · backend: su verify no se omite con if: steps" \
  reemplaza maven-cache-main.yml backend "!= 'true'" "== 'true'"
rechaza "caché de bff sin id de setup-java" "maven-cache-main.yml · bff: su verify no se omite con if: steps" \
  reemplaza maven-cache-main.yml bff '      - id: java' '      - name: java'
rechaza "caché de backend con continue-on-error" "maven-cache-main.yml · backend: tiene continue-on-error" \
  reemplaza maven-cache-main.yml backend '    timeout-minutes: ' $'    continue-on-error: true\n    timeout-minutes: '
rechaza "caché de backend con el JDK en la caché" "maven-cache-main.yml · backend: algún setup-java no tiene cache-jdk: false" \
  reemplaza maven-cache-main.yml backend '          cache-jdk: false' ''
rechaza "comprobar caché de bff con el JDK en la caché" "maven-cache-main.yml · bff-comprobar: algún setup-java no tiene cache-jdk: false" \
  reemplaza maven-cache-main.yml bff-comprobar '          cache-jdk: false' ''
rechaza "comprobar caché de backend con conexión" "maven-cache-main.yml · backend-comprobar: su verify no es el de backend-pr.yml sin conexión" \
  reemplaza maven-cache-main.yml backend-comprobar ' -o ' ' '
rechaza "comprobar caché de backend sin ignorar las pruebas" "maven-cache-main.yml · backend-comprobar: su verify no es el de backend-pr.yml sin conexión" \
  reemplaza maven-cache-main.yml backend-comprobar ' -Dmaven.test.failure.ignore=true' ''
rechaza "comprobar caché de bff que guarda caché" "maven-cache-main.yml · bff-comprobar: no restaura con cache-read-only: true" \
  reemplaza maven-cache-main.yml bff-comprobar '          cache-read-only: true' ''
rechaza "comprobar caché de bff sin needs" "maven-cache-main.yml · bff-comprobar: no depende de bff (needs)" \
  reemplaza maven-cache-main.yml bff-comprobar '    needs: bff' ''
rechaza "comprobar caché de backend con otra clave" "maven-cache-main.yml · backend-comprobar: su cache-dependency-path no es el del verify de backend-pr.yml" \
  reemplaza maven-cache-main.yml backend-comprobar '            backend/pom.xml' '            backend/otro.xml'
rechaza "comprobar caché de bff con continue-on-error" "maven-cache-main.yml · bff-comprobar: tiene continue-on-error" \
  reemplaza maven-cache-main.yml bff-comprobar '        run: ./mvnw' $'        continue-on-error: true\n        run: ./mvnw'

# 239-04: maven-cache-main.yml solo corre en main, con contents: read.
rechaza "maven-cache-main con pull_request" "maven-cache-main.yml: se dispara con" \
  reemplaza maven-cache-main.yml - '  workflow_dispatch:' $'  pull_request:\n  workflow_dispatch:'
rechaza "maven-cache-main sin schedule" "maven-cache-main.yml: se dispara con" \
  reemplaza maven-cache-main.yml - '  schedule:' '  # schedule:'
rechaza "maven-cache-main con push a cualquier rama" "maven-cache-main.yml: el push no es solo a main" \
  reemplaza maven-cache-main.yml - 'branches: [main]' "branches: ['**']"
rechaza "maven-cache-main sin la rama main en un trabajo" "maven-cache-main.yml · bff: su condición «if» no exige" \
  reemplaza maven-cache-main.yml bff "github.ref == 'refs/heads/main'" "github.ref != ''"
rechaza "maven-cache-main sin la rama main en la comprobación" "maven-cache-main.yml · backend-comprobar: su condición «if» no exige" \
  reemplaza maven-cache-main.yml backend-comprobar "    if: github.ref == 'refs/heads/main'" ''
rechaza "maven-cache-main con contents: write" "maven-cache-main.yml: los permisos del workflow no son solo contents: read" \
  reemplaza maven-cache-main.yml - '  contents: read' '  contents: write'
rechaza "maven-cache-main con permisos más amplios en un trabajo" "maven-cache-main.yml · bff: amplía los permisos más allá de contents: read" \
  reemplaza maven-cache-main.yml bff '    timeout-minutes: ' $'    permissions:\n      contents: read\n      actions: write\n    timeout-minutes: '
rechaza "maven-cache-main con id-token: write en un trabajo" "maven-cache-main.yml: tiene permisos de escritura" \
  reemplaza maven-cache-main.yml backend-comprobar '    timeout-minutes: ' $'    permissions:\n      id-token: write\n    timeout-minutes: '
rechaza "maven-cache-main sin timeout" "maven-cache-main.yml · backend: no tiene timeout-minutes" \
  reemplaza maven-cache-main.yml backend '    timeout-minutes: ' '    # timeout-minutes: '
rechaza "maven-cache-main que cancela la ejecución en curso" "maven-cache-main.yml: la concurrency cancela la ejecución en curso" \
  reemplaza maven-cache-main.yml - 'cancel-in-progress: false' 'cancel-in-progress: true'
rechaza "maven-cache-main sin grupo de concurrency propio" "maven-cache-main.yml: no tiene su propio grupo de concurrency" \
  reemplaza maven-cache-main.yml - 'group: maven-cache-main' 'group: otro'
rechaza "otro workflow con el grupo de maven-cache-main" "backend-main.yml: comparte el grupo de concurrency de maven-cache-main.yml" \
  reemplaza backend-main.yml - '  group: ' '  group: maven-cache-main # '
rechaza "maven-cache-main con una acción sin SHA" "maven-cache-main.yml: una acción no está fijada por SHA" \
  reemplaza maven-cache-main.yml backend "$SHA_CHECKOUT" 'actions/checkout@v7'
rechaza "maven-cache-main sin el trabajo backend" "maven-cache-main.yml: falta el trabajo backend" \
  reemplaza maven-cache-main.yml - '  backend:' '  principal:'

# ---------------------------------------------------------------------------------------------------------------
echo
if [ "$fallos" -ne 0 ]; then
  echo "$fallos de $total pruebas fallaron."
  exit 1
fi
echo "$total pruebas en verde."
