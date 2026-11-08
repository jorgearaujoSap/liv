#!/bin/sh
# Inmutabilidad y publicación de los esquemas JSON del backend (feature 004, paso 2; ADR 0032), el equivalente de
# compare.sh y publish.sh para contracts/jsonschema/. Cada esquema es una carpeta con una pareja de archivos por
# versión: contracts/jsonschema/<nombre>/<MAYOR.MENOR>.json y <MAYOR.MENOR>.casos.json. Una versión nunca cambia:
# un cambio es una versión nueva, en archivos nuevos. El nombre de la carpeta nombra la release, así que no se
# puede repetir con un contrato de contracts/openapi/.
#
# Los dos juzgan lo que hay guardado en Git (los blobs del commit, sin conversiones de fin de línea ni de
# codificación), que es lo que se publica; nunca el árbol de trabajo, que un .gitattributes puede hacer distinto.
# Por eso hay que confirmar los cambios antes de juzgarlos. La carpeta del proyecto no puede ser (ni estar dentro
# de) un enlace simbólico: Git buscaría la ruta física, y un PR que mueva backend/ y deje un enlace en su lugar
# esconde la carpeta de la base.
#
#   check <referencia-base>   juzga el commit HEAD contra una referencia de Git (rama, etiqueta o commit):
#       - una versión (esquema o casos) que existe en la base no cambia de contenido ni se quita;
#       - todo lo que hay en contracts/jsonschema/ es <nombre>/<MAYOR.MENOR>.json o
#         <nombre>/<MAYOR.MENOR>.casos.json (nombre en minúsculas, dígitos y guiones): ni archivos sueltos, ni
#         subcarpetas, ni archivos ocultos, ni enlaces simbólicos ni submódulos; y cada versión tiene sus dos
#         archivos;
#       - ningún nombre se repite con un contrato de contracts/openapi/;
#       - mientras la base o HEAD tengan esquemas, HEAD conserva este script, lib.sh, run.sh, fake-gh.sh y los
#         escenarios de este script: quitarlos no desactiva la comprobación.
#     Lo que ya estaba mal en la base no bloquea al PR que lo corrige: de la base solo cuentan las versiones bien
#     escritas.
#   publish [--dry-run]       publica cada versión como la release jsonschema-<nombre>-v<MAYOR.MENOR>, con
#       <nombre>-<MAYOR.MENOR>.json, <nombre>-<MAYOR.MENOR>.casos.json y el .sha256 de cada uno. Es el protocolo de
#       publish.sh (ADR 0027):
#       - publica el commit GITHUB_SHA (con --dry-run, HEAD), con las mismas reglas de forma que check;
#       - la release se crea como borrador, se suben los archivos, se verifican descargados y solo entonces se
#         publica; no se marca como la más reciente;
#       - se puede correr de nuevo: un borrador a medias se completa y una versión ya publicada con el mismo
#         contenido no se toca;
#       - si la versión ya está publicada con otro contenido, o incompleta, falla: lo publicado no cambia;
#       - el prefijo jsonschema- evita que se mezcle con las releases contract-<nombre>-v... de publish.sh.
#     Necesita gh con GH_TOKEN (permiso contents: write), GH_REPO (dueño/repositorio) y GITHUB_SHA (40 dígitos
#     hexadecimales). Las pruebas cambian gh por uno falso con LIV_GH, y usan LIV_CONTRACTS_ROOT y
#     LIV_CONTRACTS_WORK como compare.sh.
# Uso, desde backend/:
#   sh contracts/scripts/jsonschema.sh check origin/main
#   sh contracts/scripts/jsonschema.sh publish             publica el commit GITHUB_SHA
#   sh contracts/scripts/jsonschema.sh publish --dry-run   solo dice qué versiones tiene el commit HEAD
# Sale con 0 si todo está bien, con 1 si algo falla y con 2 si se usa mal.
set -u
scripts=$(cd "$(dirname "$0")" && pwd)
proyecto=$(cd "$scripts/../.." && pwd)
. "$scripts/lib.sh"

SCHEMA_DIR=contracts/jsonschema
NOMBRE_RE='[a-z][a-z0-9-]*'
VERSION_RE='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
# Lo que debe existir en HEAD mientras haya esquemas: este script, sus dependencias, las pruebas y su lista de
# escenarios.
GUARDAS="contracts/scripts/jsonschema.sh contracts/scripts/lib.sh contracts/scripts/test/run.sh
contracts/scripts/test/fake-gh.sh contracts/scripts/test/jsonschema-escenarios.sh"

gh() { ${LIV_GH:-command gh} "$@"; }

uso() {
  echo "Uso: sh contracts/scripts/jsonschema.sh check <referencia-base> | publish [--dry-run]" >&2
  exit 2
}

case "${1:-}:$#" in
  check:2)
    # Una referencia que empieza por «-» la leería Git como una opción.
    case "$2" in -*) uso ;; esac
    base_ref=$2
    ;;
  publish:1) simulacion=false ;;
  publish:2)
    [ "$2" = --dry-run ] || uso
    simulacion=true
    ;;
  *) uso ;;
esac
if [ "$1" = publish ] && [ "$simulacion" = false ]; then
  if [ -z "${GH_REPO:-}" ] || [ -z "${GITHUB_SHA:-}" ]; then
    echo "Faltan GH_REPO o GITHUB_SHA. Para ver qué se publicaría: sh contracts/scripts/jsonschema.sh publish --dry-run" >&2
    exit 2
  fi
  # Sin grep: validaría línea por línea y dejaría pasar un valor con un salto de línea.
  if [ "${#GITHUB_SHA}" -ne 40 ]; then sha_invalido=true; fi
  case "$GITHUB_SHA" in *[!0-9a-f]*) sha_invalido=true ;; esac
  if [ "${sha_invalido:-false}" = true ]; then
    echo "GITHUB_SHA debe ser un commit completo (40 dígitos hexadecimales)." >&2
    exit 2
  fi
fi

fallas=0
if [ ! -d "${LIV_CONTRACTS_ROOT:-$proyecto}" ]; then
  falla PROJECT_MISSING "${LIV_CONTRACTS_ROOT:-$proyecto}" "la carpeta del proyecto no existe."
  exit 1
fi
prepare_workdir "${LIV_CONTRACTS_WORK:-$proyecto/target/jsonschema}" "${LIV_CONTRACTS_ROOT:-$proyecto}"
cd "${LIV_CONTRACTS_ROOT:-$proyecto}" || exit 2
error="$trabajo/error.txt"

# Ninguna parte de la ruta del proyecto que esté dentro del repositorio puede ser un enlace simbólico: Git buscaría
# la ruta física, y un PR que mueva backend/ y deje un enlace en su lugar esconde la carpeta de la base. Lo que hay
# por encima del repositorio (un /tmp enlazado, una carpeta de trabajo enlazada, o el propio repositorio alcanzado
# por un enlace) no importa. Se sube desde la carpeta del proyecto mientras Git diga que sigue dentro del repositorio:
#   - con un subdirectorio del repositorio detrás, un enlace en la ruta es siempre una falla;
#   - al llegar a la raíz del repositorio, un enlace solo es una falla si su carpeta padre es de ese mismo
#     repositorio (backend -> .), y no si el padre es otro lugar (un enlace al repositorio desde fuera).
check_no_symlinks_in_root() {
  actual=$(pwd)
  while :; do
    dentro=$(git -C "$actual" rev-parse --show-prefix 2> /dev/null) || break
    if [ -L "$actual" ]; then
      peligro=true
      if [ -z "$dentro" ]; then
        raiz_actual=$(git -C "$actual" rev-parse --show-toplevel)
        raiz_padre=$(git -C "${actual%/*}" rev-parse --show-toplevel 2> /dev/null) || raiz_padre=
        [ "$raiz_actual" = "$raiz_padre" ] || peligro=false
      fi
      if [ "$peligro" = true ]; then
        falla UNSAFE_ROOT "$actual" "la carpeta del proyecto pasa por un enlace simbólico; Git buscaría otra ruta."
        exit 1
      fi
    fi
    [ -n "$dentro" ] || break
    actual=${actual%/*}
  done
}
check_no_symlinks_in_root

# Verdadero si la ruta, relativa a contracts/jsonschema/, es <nombre>/<MAYOR.MENOR>[.casos].json.
form_ok() { printf '%s' "$1" | grep -Eq "^$NOMBRE_RE/$VERSION_RE(\.casos)?\.json\$"; }

FORMA="debe ser <nombre>/<MAYOR.MENOR>.json o <nombre>/<MAYOR.MENOR>.casos.json, con el nombre en minúsculas, dígitos y guiones."

# Nombre de archivo apto para un mensaje o una anotación de Actions: sin «%», «,», «:» ni saltos de línea, que
# podrían mover las propiedades de la anotación o abrir otra.
safe_name() { printf '%s' "$1" | tr '%,:\r\n' '_____'; }

# Salida de error de gh en una sola línea y sin «%», para que quepa en un mensaje o en una anotación de Actions.
error_text() { tr '\r\n%' '   ' < "$error"; }

# Imprime el SHA-256 del archivo. Si no lo pudo calcular no imprime nada y sale con 1: un hash vacío nunca debe
# coincidir con otro hash vacío y dar por verificado lo que no se verificó.
sha_de() { # sha_de ARCHIVO
  suma=$(sha256sum < "$1" | cut -d ' ' -f 1)
  [ "${#suma}" -eq 64 ] || return 1
  case "$suma" in *[!0-9a-f]*) return 1 ;; esac
  printf '%s\n' "$suma"
}

# Línea de ls-tree («modo tipo objeto<tab>ruta») de una ruta en una referencia, o nada si no existe.
tree_entry() { # tree_entry REFERENCIA RUTA
  git ls-tree "$1" -- "$2"
}

# Verdadero si la ruta es un archivo normal (no enlace simbólico ni submódulo) en la referencia.
is_regular_file() { # is_regular_file REFERENCIA RUTA
  case "$(tree_entry "$1" "$2")" in
    "100644 blob "* | "100755 blob "*) return 0 ;;
  esac
  return 1
}

# Copia a la carpeta destino las versiones de contracts/jsonschema/ de una referencia de Git, tal como están
# guardadas. Con "estricto", lo que no tiene la forma de una versión (otra ruta, un enlace simbólico, un submódulo)
# se reporta con falla(); con "tolerante" se ignora (es la base de check). Sale con 0 si todo está bien, con 1 si
# hubo algo mal escrito (lo bien escrito se copia de todos modos) y con 2 si falla Git: cada orden se comprueba,
# porque un error no puede dejar la base vacía y hacer que todo pase como versión nueva.
scan_ref() { # scan_ref REFERENCIA CARPETA_DESTINO estricto|tolerante
  modo_de_forma=$3
  if ! git rev-parse -q --verify "$1^{commit}" > /dev/null; then
    falla GIT_ERROR "$1" "no existe esa referencia de Git."
    return 2
  fi
  lista="$2.ls-tree"
  if ! git ls-tree -r "$1" -- "$SCHEMA_DIR/" > "$lista" || ! raiz=$(tree_entry "$1" "$SCHEMA_DIR"); then
    falla GIT_ERROR "$1" "no se pudo listar $SCHEMA_DIR/."
    return 2
  fi
  malo=0
  # contracts/jsonschema tiene que ser una carpeta; si es un archivo, un enlace o un submódulo, no hay nada que leer.
  case "$raiz" in
    '' | "040000 tree "*) ;;
    *)
      if [ "$modo_de_forma" = estricto ]; then
        falla UNEXPECTED_FILE "$SCHEMA_DIR" "debe ser una carpeta, no un archivo, un enlace simbólico ni un submódulo."
        malo=1
      fi
      ;;
  esac
  # Cada línea de ls-tree: modo, tipo y objeto, y tras un tabulador la ruta (Git entrecomilla las rutas con
  # caracteres de control o fuera de ASCII, y esas no tienen la forma). Solo el tabulador separa: un nombre con
  # espacios al final no puede perderlos al leerse.
  tabulador=$(printf '\t')
  while IFS=$tabulador read -r meta ruta; do
    modo=${meta%% *}
    resto=${meta#* }
    tipo=${resto%% *}
    objeto=${resto#* }
    rel=${ruta#"$SCHEMA_DIR"/}
    if [ "$tipo" != blob ] || [ "$modo" = 120000 ] || ! form_ok "$rel"; then
      if [ "$modo_de_forma" = estricto ]; then
        falla UNEXPECTED_FILE "$(safe_name "$rel")" "$FORMA Tampoco enlaces simbólicos ni submódulos."
        malo=1
      fi
      continue
    fi
    mkdir -p "$2/${rel%/*}" && git cat-file blob "$objeto" > "$2/$rel" || {
      falla GIT_ERROR "$(safe_name "$rel")" "no se pudo leer en $1."
      return 2
    }
  done < "$lista"
  return $malo
}

# Rutas, relativas a la carpeta dada, de todos los archivos que tiene.
list_files() { # list_files CARPETA
  [ -d "$1" ] || return 0
  (cd "$1" && find . -type f | sed 's|^\./||' | sort)
}

check_immutability() { # check_immutability BASE CABEZA REFERENCIA
  list_files "$1" > "$trabajo/versiones-base.txt"
  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    # Una versión que en la base ya estaba incompleta nunca se pudo publicar: no se exige conservar lo que quedó.
    clave=${rel%.casos.json}
    clave=${clave%.json}
    if [ ! -f "$1/$clave.json" ] || [ ! -f "$1/$clave.casos.json" ]; then
      echo "  $rel: la versión estaba incompleta en $3; no se exige conservarla"
      continue
    fi
    if [ ! -f "$2/$rel" ]; then
      falla VERSION_REMOVED "$(safe_name "$rel")" "existe en $3 y se quitó: una versión no se quita."
    elif ! cmp -s "$1/$rel" "$2/$rel"; then
      falla VERSION_CHANGED "$(safe_name "$rel")" "cambió frente a $3: un cambio es una versión nueva, en archivos nuevos."
    else
      ok "$rel: igual que en $3"
    fi
  done < "$trabajo/versiones-base.txt"
}

# Cada versión lleva su esquema y sus casos: sin uno de los dos no se puede publicar. Deja en claves.txt las
# versiones de la carpeta (<nombre>/<MAYOR.MENOR>), que run_publish lee después, y sale con 1 si a alguna le falta un
# archivo.
check_pairs() { # check_pairs CARPETA
  incompletas=0
  list_files "$1" | sed -e 's|\.casos\.json$||' -e 's|\.json$||' | sort -u > "$trabajo/claves.txt"
  while IFS= read -r clave; do
    [ -n "$clave" ] || continue
    if [ ! -f "$1/$clave.json" ] || [ ! -f "$1/$clave.casos.json" ]; then
      falla INCOMPLETE_VERSION "$(safe_name "$clave")" "cada versión lleva ${clave#*/}.json y ${clave#*/}.casos.json."
      incompletas=1
    fi
  done < "$trabajo/claves.txt"
  return $incompletas
}

# Un esquema no puede llamarse como un contrato OpenAPI: el nombre nombra la release. CABEZA es la copia de las
# versiones de HEAD y BASE la de la base (las carpetas que llenó scan_ref); el contrato se busca en HEAD y en
# la base. Un choque que ya estaba en la base (el esquema y el contrato) no bloquea a los demás PR: solo se avisa;
# falla el que lo introduce.
check_name_clashes() { # check_name_clashes CABEZA BASE REFERENCIA_BASE
  for carpeta in "$1"/*/; do
    [ -d "$carpeta" ] || continue
    nombre=$(basename "$carpeta")
    [ -n "$(tree_entry "$head_sha" "$CONTRACT_DIR/$nombre.yaml")" ] || continue
    if [ -d "$2/$nombre" ] && [ -n "$(tree_entry "$3" "$CONTRACT_DIR/$nombre.yaml")" ]; then
      echo "  $nombre: ya compartía nombre con un contrato en la base; hay que renombrar uno de los dos"
    else
      falla NAME_CLASH "$(safe_name "$nombre")" "también es el nombre del contrato $CONTRACT_DIR/$nombre.yaml."
    fi
  done
}

# Quitar el script o sus pruebas en el mismo PR no puede desactivar la comprobación.
check_guards() {
  for guarda in $GUARDAS; do
    if ! is_regular_file "$head_sha" "$guarda"; then
      falla SCRIPT_MISSING "$guarda" "mientras haya esquemas, HEAD conserva este script, lib.sh, run.sh, fake-gh.sh y los escenarios."
    fi
  done
}

run_check() { # run_check REFERENCIA
  base="$trabajo/base"
  cabeza="$trabajo/cabeza"
  mkdir -p "$base" "$cabeza"
  # La base y HEAD se resuelven una sola vez: todas las comprobaciones juzgan los mismos dos commits aunque alguien
  # confirme o traiga cambios mientras corre.
  if ! base_sha=$(git rev-parse -q --verify "$1^{commit}") || ! head_sha=$(git rev-parse -q --verify 'HEAD^{commit}'); then
    falla GIT_ERROR "$1" "no existe esa referencia de Git, o HEAD no es un commit."
    exit 1
  fi
  # La base se lee sin exigirle la forma: solo importan sus versiones bien escritas. A HEAD sí se le exige. Que
  # scan_ref salga con 1 es normal (hay algo mal escrito y ya quedó reportado); con 2 falló Git y no se puede seguir.
  scan_ref "$base_sha" "$base" tolerante
  [ $? -eq 2 ] && exit 1
  scan_ref "$head_sha" "$cabeza" estricto
  [ $? -eq 2 ] && exit 1
  archivos_base=$(list_files "$base")
  archivos_cabeza=$(list_files "$cabeza")
  if [ -z "$archivos_base" ]; then
    echo "  $1 no tiene esquemas en $SCHEMA_DIR/: nada que proteger"
  fi
  check_immutability "$base" "$cabeza" "$1"
  check_pairs "$cabeza"
  check_name_clashes "$cabeza" "$base" "$base_sha"
  if [ -n "$archivos_base" ] || [ -n "$archivos_cabeza" ]; then
    check_guards
  fi
  if [ "$fallas" -gt 0 ]; then
    echo "$fallas problemas en los esquemas."
    exit 1
  fi
  echo "Esquemas: ninguna versión cambió y todo tiene la forma esperada."
}

# Descarga los archivos de la release y comprueba que cada uno, y su .sha256, coinciden con los de la carpeta dada.
# Sale con 1 si no coinciden o falta alguno, y con 2 si no se pudo descargar o calcular.
published_matches() { # published_matches ETIQUETA CARPETA NOMBRE VERSION
  descargados="$trabajo/descargados/$1"
  rm -rf "$descargados"
  mkdir -p "$descargados"
  for sufijo in .json .casos.json; do
    asset="$3-$4$sufijo"
    esperado=$(sha_de "$2/$asset") || return 2
    for pedido in "$asset" "$asset.sha256"; do
      if ! gh release download "$1" --pattern "$pedido" --dir "$descargados" --clobber 2> "$error"; then
        grep -q 'no assets' "$error" && return 1
        return 2
      fi
    done
    [ "$(sha_de "$descargados/$asset")" = "$esperado" ] || return 1
    [ "$(cut -d ' ' -f 1 "$descargados/$asset.sha256")" = "$esperado" ] || return 1
  done
}

notes() { # notes NOMBRE VERSION CARPETA
  printf 'Esquema JSON `%s` %s de LIV, publicado desde %s. SHA-256 de `%s-%s.json`: `%s`; de `%s-%s.casos.json`: `%s`.' \
    "$1" "$2" "$GITHUB_SHA" "$1" "$2" "$(cut -d ' ' -f 1 "$3/$1-$2.json.sha256")" \
    "$1" "$2" "$(cut -d ' ' -f 1 "$3/$1-$2.casos.json.sha256")"
}

# Publica un borrador que ya tiene los archivos, después de verificarlos.
publish_draft() { # publish_draft CLAVE NOMBRE VERSION ETIQUETA CARPETA
  published_matches "$4" "$5" "$2" "$3"
  case $? in
    0) ;;
    1) falla PUBLISH_ERROR "$1" "el borrador $4 no quedó con los archivos correctos; no se publicó."; return ;;
    *) falla PUBLISH_ERROR "$1" "no se pudo verificar el borrador $4: $(error_text)"; return ;;
  esac
  if gh release edit "$4" --draft=false --latest=false --target "$GITHUB_SHA" \
      --notes "$(notes "$2" "$3" "$5")" 2> "$error"; then
    ok "$1: publicada $4"
  else
    falla PUBLISH_ERROR "$1" "no se pudo publicar el borrador $4: $(error_text)"
  fi
}

publish_version() { # publish_version CLAVE NOMBRE VERSION ETIQUETA CARPETA
  # gh distingue «no existe» de un error de red o de permisos, que no se debe tomar como «no existe»: «release not
  # found» es la respuesta de release view, y «HTTP 404» la de la API al consultar la etiqueta.
  if es_borrador=$(gh release view "$4" --json isDraft --jq .isDraft 2> "$error"); then
    if [ "$es_borrador" != true ]; then
      published_matches "$4" "$5" "$2" "$3"
      case $? in
        0) ok "$1: $4 ya está publicada" ;;
        1) falla PUBLISHED_CHANGED "$1" "$4 ya está publicada con otro contenido o incompleta; un cambio es una versión nueva. Si solo le falta un archivo, revísala a mano." ;;
        *) falla PUBLISH_ERROR "$1" "no se pudo descargar la release $4 para verificarla: $(error_text)" ;;
      esac
      return
    fi
  elif grep -q 'release not found' "$error"; then
    es_borrador=false
  else
    falla PUBLISH_ERROR "$1" "no se pudo consultar la release $4: $(error_text)"
    return
  fi

  if [ "$es_borrador" = true ]; then
    # Borrador de una corrida que se cortó: se completa con el contenido del commit.
    if gh release upload "$4" "$5"/*.json "$5"/*.sha256 --clobber 2> "$error"; then
      publish_draft "$1" "$2" "$3" "$4" "$5"
    else
      falla PUBLISH_ERROR "$1" "no se pudieron subir los archivos al borrador $4: $(error_text)"
    fi
  elif gh api "repos/$GH_REPO/git/ref/tags/$4" > /dev/null 2> "$error"; then
    falla PUBLISH_ERROR "$1" "la etiqueta $4 existe sin release; revísala antes de publicar."
  elif ! grep -q 'HTTP 404' "$error"; then
    falla PUBLISH_ERROR "$1" "no se pudo consultar la etiqueta $4: $(error_text)"
  elif gh release create "$4" "$5"/*.json "$5"/*.sha256 --draft --latest=false --target "$GITHUB_SHA" \
      --title "Esquema $2 $3" --notes "$(notes "$2" "$3" "$5")" 2> "$error"; then
    publish_draft "$1" "$2" "$3" "$4" "$5"
  else
    falla PUBLISH_ERROR "$1" "no se pudo crear el borrador $4: $(error_text)"
  fi
}

run_publish() {
  versiones="$trabajo/versiones"
  mkdir -p "$versiones"
  # Con --dry-run se lee HEAD en lugar de GITHUB_SHA, que puede no estar.
  if [ "$simulacion" = true ]; then commit=HEAD; else commit=$GITHUB_SHA; fi
  scan_ref "$commit" "$versiones" estricto || exit 1
  if [ -z "$(list_files "$versiones")" ]; then
    falla NO_SCHEMAS "$SCHEMA_DIR" "el proyecto no tiene esquemas que publicar."
    exit 1
  fi
  # Una versión a medias detiene todo antes de llamar a GitHub.
  check_pairs "$versiones" || exit 1

  while IFS= read -r clave; do
    nombre=${clave%/*}
    version=${clave#*/}
    etiqueta="jsonschema-$nombre-v$version"
    # Los archivos de la release, con el nombre que lleva en GitHub y el .sha256 de cada uno.
    carpeta="$trabajo/archivos/$etiqueta"
    mkdir -p "$carpeta" || exit 1
    for sufijo in .json .casos.json; do
      cp "$versiones/$clave$sufijo" "$carpeta/$nombre-$version$sufijo" || exit 1
      if ! sha=$(sha_de "$carpeta/$nombre-$version$sufijo"); then
        falla PUBLISH_ERROR "$clave" "no se pudo calcular el SHA-256 de $nombre-$version$sufijo."
        exit 1
      fi
      printf '%s  %s\n' "$sha" "$nombre-$version$sufijo" > "$carpeta/$nombre-$version$sufijo.sha256" || exit 1
    done
    if [ "$simulacion" = true ]; then
      ok "$clave: $etiqueta (SHA-256 del esquema $(cut -d ' ' -f 1 "$carpeta/$nombre-$version.json.sha256"))"
    else
      publish_version "$clave" "$nombre" "$version" "$etiqueta" "$carpeta"
    fi
  done < "$trabajo/claves.txt"

  if [ "$fallas" -gt 0 ]; then
    echo "$fallas versiones sin publicar."
    exit 1
  fi
  if [ "$simulacion" = true ]; then
    echo "Simulación: no se publicó nada."
  else
    echo "Esquemas publicados."
  fi
}

if [ "$1" = check ]; then run_check "$base_ref"; else run_publish; fi
