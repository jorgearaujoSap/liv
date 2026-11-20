#!/usr/bin/env bash
# Pruebas de la caché de Maven del CI (issue 239): .github/workflows/maven-cache-main.yml y los workflows que la usan.
#
# Un solo productor de la caché de Maven en main: solo maven-cache-main.yml la escribe, los verify de los PR la
# restauran con la misma clave y los demás trabajos la leen con cache-read-only o no la usan. Esta prueba lee los
# workflows y falla si alguna de esas reglas deja de cumplirse. No ejecuta nada: para comprobar que cada regla muerde,
# arma copias de los workflows con un defecto y exige que la regla las rechace (modelo: ui-release.test.sh).
#
# La ejecuta sistema-pr.yml desde la punta de la base (HEAD^1), para que un PR no pueda debilitarla y relajar un workflow
# a la vez. Lee los workflows del árbol de trabajo, que son los del PR.
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
  leer "$1" | awk '/^jobs:/ { d = 1; next } d && /^  [A-Za-z0-9_-]+:[ ]*$/ { sub(/^  /, ""); sub(/:.*/, ""); print }'
}

# Líneas del trabajo $2 del workflow $1 (entre su encabezado de dos espacios y el siguiente), sin los comentarios.
bloque() {
  leer "$1" | awk -v job="  $2:" '
    /^jobs:/ { enjobs = 1; next }
    enjobs && $0 == job { dentro = 1; next }
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
  for f in "$d"/*.yml; do
    base=$(basename "$f")
    for job in $(trabajos "$f"); do
      contenido=$(bloque "$f" "$job")
      # 239-02: un trabajo con permisos de escritura no ejecuta nada que otro trabajo haya podido escribir en la caché.
      if grep -Eq ': *write$|write-all' <<<"$contenido" && grep -Eq '^ +(cache|cache-dependency-path):|actions/cache@' <<<"$contenido"; then
        echo "$base · $job: tiene permisos de escritura y usa caché"
      fi
      while IFS= read -r -d $'\036' paso; do
        grep -q 'actions/setup-java@' <<<"$paso" || continue
        grep -Eq '^ +cache:' <<<"$paso" || continue
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
  local par p pr cmd dir vb cmds rp ps sha filler comprobar fb cb id pasoj
  for par in 'backend|backend-pr.yml|sh mvnw -B -ntp verify|backend' 'bff|bff-pr.yml|./mvnw -B -ntp verify|backend/bff'; do
    IFS='|' read -r p pr cmd dir <<<"$par"
    if [ ! -f "$d/$pr" ]; then echo "falta $pr"; continue; fi
    vb=$(bloque "$d/$pr" verify)
    cmds=$(comando <<<"$vb")
    [ "$cmds" = "$cmd" ] || echo "$pr · verify: el comando no es exactamente «$cmd» (es «${cmds:-ninguno}»)"
    grep -Eq 'continue-on-error|retry|nick-fields|wretry' <<<"$vb" && echo "$pr · verify: puede seguir tras un fallo o reintentar"
    grep -E '^ +(- )?run: ' <<<"$vb" | grep -Eq '[|][|]|;' && echo "$pr · verify: un run con || o ; puede seguir tras un fallo"
    grep -q 'maven.test.failure.ignore' <<<"$vb" && echo "$pr · verify: ignora los fallos de las pruebas"
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
    pasoj=$(pasos_con 'mvnw' <<<"$filler")
    cmds=$(comando <<<"$pasoj")
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
    cmds=$(comando <<<"$comprobar")
    [ "${cmds/ -o / }" = "$cmd" ] && [ "$cmds" != "$cmd" ] || echo "maven-cache-main.yml · $p-comprobar: su verify no es el de $pr sin conexión (-o) (es «$cmds»)"
    grep -q 'continue-on-error' <<<"$comprobar" && echo "maven-cache-main.yml · $p-comprobar: tiene continue-on-error"
  done

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

# Reemplaza en el trabajo $2 del archivo $1 (con «-» para todo el archivo) el texto $3 por $4 (literal; todas las apariciones).
muta() {
  local archivo=$1 job=$2 desde=$3 hasta=$4 destino="$tmp/mutado.yml"
  leer "$archivo" | awk -v job="  $job:" -v todo="$([ "$job" = - ] && echo 1 || echo 0)" -v desde="$desde" -v hasta="$hasta" '
    /^jobs:/ { enjobs = 1 }
    enjobs && $0 == job { dentro = 1; print; next }
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
  leer "$archivo" | awk -v job="  $job:" -v texto="$texto" '
    /^jobs:/ { enjobs = 1 }
    enjobs && $0 == job { dentro = 1 }
    dentro && /^    steps:$/ { print; print texto; dentro = 0; hecho = 1; next }
    { print }
    END { if (!hecho) exit 3 }' > "$destino" || { echo "ERROR del arnés: no hay «steps:» en $job de $archivo" >&2; exit 2; }
  cp "$destino" "$archivo"
}

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

# 239-02: los trabajos con permisos de escritura no usan la caché; la leen o no la usan, nunca la escriben.
rechaza "publicar contratos con caché de Maven" "backend-main.yml · contracts: tiene permisos de escritura y usa caché" \
  agrega backend-main.yml contracts $'      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad\n        with:\n          cache: maven\n          cache-read-only: true'
rechaza "un trabajo de escritura con actions/cache" "backend-main.yml · schemas: tiene permisos de escritura y usa caché" \
  agrega backend-main.yml schemas '      - uses: actions/cache@0000000000000000000000000000000000000000'
rechaza "pruebas del comparador guarda caché" "backend-contracts-pr.yml · test: setup-java con caché de Maven sin cache-read-only: true" \
  reemplaza backend-contracts-pr.yml test '          cache-read-only: true' ''
rechaza "pruebas del comparador con cache-read-only: false" "backend-contracts-pr.yml · test: setup-java con caché de Maven sin cache-read-only: true" \
  reemplaza backend-contracts-pr.yml test 'cache-read-only: true' 'cache-read-only: false'
rechaza "otro workflow fuera de PR con caché que escribe" "backend-main.yml · schemas: setup-java con caché de Maven sin cache-read-only: true" \
  agrega backend-main.yml schemas $'      - uses: actions/setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad\n        with:\n          cache: maven'
rechaza "el trabajo que llena la caché en solo lectura" "maven-cache-main.yml · backend: el trabajo que escribe la caché no puede ser de solo lectura" \
  reemplaza maven-cache-main.yml backend '          cache: maven' $'          cache: maven\n          cache-read-only: true'

# 239-03: el verify de los PR no cambia (salvo su clave) y los checks obligatorios conservan su nombre.
rechaza "verify de bff-pr con otro comando" "bff-pr.yml · verify: el comando no es exactamente" \
  reemplaza bff-pr.yml verify './mvnw -B -ntp verify' './mvnw -B -ntp -DskipTests verify'
rechaza "verify de backend-pr con otro comando" "backend-pr.yml · verify: el comando no es exactamente" \
  reemplaza backend-pr.yml verify 'sh mvnw -B -ntp verify' 'sh mvnw -B -ntp -o verify'
rechaza "verify de bff-pr con continue-on-error en el paso" "bff-pr.yml · verify: puede seguir tras un fallo o reintentar" \
  reemplaza bff-pr.yml verify '        run: ./mvnw -B -ntp verify' $'        continue-on-error: true\n        run: ./mvnw -B -ntp verify'
rechaza "verify de backend-pr con continue-on-error en el trabajo" "backend-pr.yml · verify: puede seguir tras un fallo o reintentar" \
  reemplaza backend-pr.yml verify '    timeout-minutes: 20' $'    timeout-minutes: 20\n    continue-on-error: true'
rechaza "verify de bff-pr con reintento" "bff-pr.yml · verify: puede seguir tras un fallo o reintentar" \
  reemplaza bff-pr.yml verify './mvnw -B -ntp verify' './mvnw -B -ntp verify # retry'
rechaza "verify de backend-pr con || true" "backend-pr.yml · verify: un run con || o ; puede seguir tras un fallo" \
  reemplaza backend-pr.yml verify 'sh mvnw -B -ntp verify' 'sh mvnw -B -ntp verify || true'
rechaza "verify de bff-pr con las pruebas ignoradas" "bff-pr.yml · verify: ignora los fallos de las pruebas" \
  reemplaza bff-pr.yml verify './mvnw -B -ntp verify' './mvnw -B -ntp -Dmaven.test.failure.ignore=true verify'
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
  reemplaza maven-cache-main.yml bff 'setup-java@b843ebf9b913146b38da2eaa7aeddf76074ae1ad' 'setup-java@0000000000000000000000000000000000000000'
rechaza "caché de bff con otro comando" "maven-cache-main.yml · bff: su verify no es el de bff-pr.yml" \
  reemplaza maven-cache-main.yml bff './mvnw -B -ntp -Dmaven.test.failure.ignore=true verify' './mvnw -B -ntp -Dmaven.test.failure.ignore=true install'
rechaza "caché de backend sin ignorar las pruebas" "maven-cache-main.yml · backend: su verify no usa -Dmaven.test.failure.ignore=true" \
  reemplaza maven-cache-main.yml backend ' -Dmaven.test.failure.ignore=true' ''
rechaza "caché de backend sin omitir el verify con cache-hit" "maven-cache-main.yml · backend: su verify no se omite con if: steps" \
  reemplaza maven-cache-main.yml backend "!= 'true'" "== 'true'"
rechaza "caché de bff sin id de setup-java" "maven-cache-main.yml · bff: su verify no se omite con if: steps" \
  reemplaza maven-cache-main.yml bff '      - id: java' '      - name: java'
rechaza "comprobar caché de backend con conexión" "maven-cache-main.yml · backend-comprobar: su verify no es el de backend-pr.yml sin conexión" \
  reemplaza maven-cache-main.yml backend-comprobar ' -o verify' ' verify'
rechaza "comprobar caché de bff que guarda caché" "maven-cache-main.yml · bff-comprobar: no restaura con cache-read-only: true" \
  reemplaza maven-cache-main.yml bff-comprobar '          cache-read-only: true' ''
rechaza "comprobar caché de bff sin needs" "maven-cache-main.yml · bff-comprobar: no depende de bff (needs)" \
  reemplaza maven-cache-main.yml bff-comprobar '    needs: bff' ''
rechaza "comprobar caché de backend con otra clave" "maven-cache-main.yml · backend-comprobar: su cache-dependency-path no es el del verify de backend-pr.yml" \
  reemplaza maven-cache-main.yml backend-comprobar '            backend/pom.xml' '            backend/otro.xml'
rechaza "comprobar caché de bff con continue-on-error" "maven-cache-main.yml · bff-comprobar: tiene continue-on-error" \
  reemplaza maven-cache-main.yml bff-comprobar '        run: ./mvnw -B -ntp -o verify' $'        continue-on-error: true\n        run: ./mvnw -B -ntp -o verify'

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
  reemplaza maven-cache-main.yml bff '    timeout-minutes: 30' $'    timeout-minutes: 30\n    permissions:\n      contents: read\n      actions: write'
rechaza "maven-cache-main con id-token: write en un trabajo" "maven-cache-main.yml: tiene permisos de escritura" \
  reemplaza maven-cache-main.yml backend-comprobar '    timeout-minutes: 20' $'    timeout-minutes: 20\n    permissions:\n      id-token: write'
rechaza "maven-cache-main sin timeout" "maven-cache-main.yml · backend: no tiene timeout-minutes" \
  reemplaza maven-cache-main.yml backend '    timeout-minutes: 30' ''
rechaza "maven-cache-main que cancela la ejecución en curso" "maven-cache-main.yml: la concurrency cancela la ejecución en curso" \
  reemplaza maven-cache-main.yml - 'cancel-in-progress: false' 'cancel-in-progress: true'
rechaza "maven-cache-main sin grupo de concurrency propio" "maven-cache-main.yml: no tiene su propio grupo de concurrency" \
  reemplaza maven-cache-main.yml - 'group: maven-cache-main' 'group: otro'
rechaza "otro workflow con el grupo de maven-cache-main" "backend-main.yml: comparte el grupo de concurrency de maven-cache-main.yml" \
  reemplaza backend-main.yml - 'group: backend-main' 'group: maven-cache-main'
rechaza "maven-cache-main con una acción sin SHA" "maven-cache-main.yml: una acción no está fijada por SHA" \
  reemplaza maven-cache-main.yml backend 'actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1' 'actions/checkout@v7'
rechaza "maven-cache-main sin el trabajo backend" "maven-cache-main.yml: falta el trabajo backend" \
  reemplaza maven-cache-main.yml - '  backend:' '  principal:'

# ---------------------------------------------------------------------------------------------------------------
echo
if [ "$fallos" -ne 0 ]; then
  echo "$fallos de $total pruebas fallaron."
  exit 1
fi
echo "$total pruebas en verde."
