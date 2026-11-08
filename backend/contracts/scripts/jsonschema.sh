#!/bin/sh
# Inmutabilidad y publicación de los esquemas JSON del backend (feature 004, paso 2; ADR 0032), el equivalente de
# compare.sh y publish.sh para contracts/jsonschema/. Cada esquema es una carpeta con una pareja de archivos por
# versión: contracts/jsonschema/<nombre>/<MAYOR.MENOR>.json y <MAYOR.MENOR>.casos.json. Una versión nunca cambia:
# un cambio es una versión nueva, en archivos nuevos. El nombre de la carpeta nombra la release, así que no se
# puede repetir con un contrato de contracts/openapi/.
#
#   check <referencia-base>   juzga el árbol de trabajo contra una referencia de Git (rama, etiqueta o commit):
#       - una versión (esquema o casos) que existe en la base no cambia de contenido ni se quita (se comparan sin
#         retornos de carro, como en lib.sh);
#       - todo lo que hay en contracts/jsonschema/ es <nombre>/<MAYOR.MENOR>.json o
#         <nombre>/<MAYOR.MENOR>.casos.json (nombre en minúsculas, dígitos y guiones): ni archivos sueltos, ni
#         subcarpetas, ni archivos ocultos, ni enlaces simbólicos;
#       - ningún nombre se repite con un contrato de contracts/openapi/;
#       - mientras la base o el árbol tengan esquemas, el árbol conserva este script, run.sh y los escenarios de
#         este script: quitarlos no desactiva la comprobación.
#     Lo que ya estaba mal en la base no bloquea al PR que lo corrige: de la base solo cuentan las versiones bien
#     escritas.
#   publish [--dry-run]       publica cada versión como la release jsonschema-<nombre>-v<MAYOR.MENOR>, con
#       <nombre>-<MAYOR.MENOR>.json, <nombre>-<MAYOR.MENOR>.casos.json y el .sha256 de cada uno. Es el protocolo de
#       publish.sh (ADR 0027):
#       - publica el contenido del commit GITHUB_SHA, no el del árbol de trabajo;
#       - la release se crea como borrador, se suben los archivos, se verifican descargados y solo entonces se
#         publica; no se marca como la más reciente;
#       - se puede correr de nuevo: un borrador a medias se completa y una versión ya publicada con el mismo
#         contenido no se toca;
#       - si la versión ya está publicada con otro contenido, o incompleta, falla: lo publicado no cambia;
#       - el prefijo jsonschema- evita que se mezcle con las releases contract-<nombre>-v... de publish.sh.
#     Necesita gh con GH_TOKEN (permiso contents: write), GH_REPO (dueño/repositorio) y GITHUB_SHA. Las pruebas
#     cambian gh por uno falso con LIV_GH, y usan LIV_CONTRACTS_ROOT y LIV_CONTRACTS_WORK como compare.sh.
# Uso, desde backend/:
#   sh contracts/scripts/jsonschema.sh check origin/main
#   sh contracts/scripts/jsonschema.sh publish             publica el commit GITHUB_SHA
#   sh contracts/scripts/jsonschema.sh publish --dry-run   solo dice qué versiones hay en el árbol de trabajo
# Sale con 0 si todo está bien, con 1 si algo falla y con 2 si se usa mal.
set -u
scripts=$(cd "$(dirname "$0")" && pwd)
proyecto=$(cd "$scripts/../.." && pwd)
. "$scripts/lib.sh"

SCHEMA_DIR=contracts/jsonschema
NOMBRE_RE='[a-z][a-z0-9-]*'
VERSION_RE='(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
# Lo que debe existir en el árbol mientras haya esquemas: este script, las pruebas y su lista de escenarios.
GUARDAS="contracts/scripts/jsonschema.sh contracts/scripts/test/run.sh contracts/scripts/test/jsonschema-escenarios.sh"

gh() { ${LIV_GH:-command gh} "$@"; }

uso() {
  echo "Uso: sh contracts/scripts/jsonschema.sh check <referencia-base> | publish [--dry-run]" >&2
  exit 2
}

case "${1:-}:$#" in
  check:2) base_ref=$2 ;;
  publish:1) simulacion=false ;;
  publish:2)
    [ "$2" = --dry-run ] || uso
    simulacion=true
    ;;
  *) uso ;;
esac
if [ "$1" = publish ] && [ "$simulacion" = false ] && { [ -z "${GH_REPO:-}" ] || [ -z "${GITHUB_SHA:-}" ]; }; then
  echo "Faltan GH_REPO o GITHUB_SHA. Para ver qué se publicaría: sh contracts/scripts/jsonschema.sh publish --dry-run" >&2
  exit 2
fi

fallas=0
prepare_workdir "${LIV_CONTRACTS_WORK:-$proyecto/target/jsonschema}" "${LIV_CONTRACTS_ROOT:-$proyecto}"
cd "${LIV_CONTRACTS_ROOT:-$proyecto}" || exit 2
error="$trabajo/error.txt"

# Verdadero si la ruta, relativa a contracts/jsonschema/, es <nombre>/<MAYOR.MENOR>[.casos].json.
form_ok() { printf '%s' "$1" | grep -Eq "^$NOMBRE_RE/$VERSION_RE(\.casos)?\.json\$"; }

FORMA="debe ser <nombre>/<MAYOR.MENOR>.json o <nombre>/<MAYOR.MENOR>.casos.json, con el nombre en minúsculas, dígitos y guiones."

# Copia a la carpeta destino las versiones del árbol de trabajo, sin retornos de carro, y reporta con falla() lo que no
# tiene la forma de una versión. Sale con 1 si hubo algo así; las versiones bien escritas se copian de todos modos.
# Quien la llama puede ignorar el código de salida: cada problema ya quedó contado en fallas.
scan_worktree() { # scan_worktree CARPETA_DESTINO
  [ -e "$SCHEMA_DIR" ] || [ -L "$SCHEMA_DIR" ] || return 0
  if [ -L "$SCHEMA_DIR" ] || [ ! -d "$SCHEMA_DIR" ]; then
    falla UNEXPECTED_FILE "$SCHEMA_DIR" "debe ser una carpeta, no un archivo ni un enlace simbólico."
    return 1
  fi
  # Un nombre con salto de línea partiría la lista de abajo en dos entradas; el patrón es «cualquier cosa, salto, cualquier cosa».
  salto='
'
  if [ -n "$(find "$SCHEMA_DIR" -name "*${salto}*" -print)" ]; then
    falla UNEXPECTED_FILE "$SCHEMA_DIR" "hay un nombre con saltos de línea."
    return 1
  fi
  malo=0
  find "$SCHEMA_DIR" -mindepth 1 | sort > "$1.lista"
  while IFS= read -r entrada; do
    rel=${entrada#"$SCHEMA_DIR"/}
    if [ -L "$entrada" ]; then
      falla UNEXPECTED_FILE "$rel" "en $SCHEMA_DIR/ no van enlaces simbólicos."
      malo=1
    elif [ -d "$entrada" ]; then
      # La carpeta de un esquema; lo que va dentro lo juzga cada archivo.
      if ! printf '%s' "$rel" | grep -Eq "^$NOMBRE_RE\$"; then
        falla UNEXPECTED_FILE "$rel" "la carpeta de un esquema lleva el nombre en minúsculas, dígitos y guiones, sin subcarpetas."
        malo=1
      fi
    elif [ -f "$entrada" ] && form_ok "$rel"; then
      mkdir -p "$1/${rel%/*}"
      tr -d '\r' < "$entrada" > "$1/$rel"
    else
      falla UNEXPECTED_FILE "$rel" "$FORMA"
      malo=1
    fi
  done < "$1.lista"
  return $malo
}

# Lo mismo con las versiones de una referencia de Git. Con "estricto", lo que no tiene la forma de una versión es
# una falla; con "tolerante", se ignora (es la base de check). Cada orden de Git se comprueba: un error no puede
# dejar la base vacía, porque todo pasaría como versión nueva.
scan_ref() { # scan_ref REFERENCIA CARPETA_DESTINO estricto|tolerante
  modo_de_forma=$3
  if ! git rev-parse -q --verify "$1^{commit}" > /dev/null; then
    falla GIT_ERROR "$1" "no existe esa referencia de Git."
    return 1
  fi
  lista="$2.ls-tree"
  if ! git ls-tree -r "$1" -- "$SCHEMA_DIR/" > "$lista"; then
    falla GIT_ERROR "$1" "no se pudo listar $SCHEMA_DIR/."
    return 1
  fi
  malo=0
  # Cada línea de ls-tree: modo, tipo y objeto, y tras un tabulador la ruta. El modo 120000 es un enlace simbólico.
  while read -r modo tipo _objeto ruta; do
    rel=${ruta#"$SCHEMA_DIR"/}
    if [ "$tipo" != blob ] || [ "$modo" = 120000 ] || ! form_ok "$rel"; then
      if [ "$modo_de_forma" = estricto ]; then
        falla UNEXPECTED_FILE "$rel" "$FORMA Tampoco enlaces simbólicos."
        malo=1
      fi
      continue
    fi
    # MSYS_NO_PATHCONV: Git Bash no convierte «referencia:ruta» como si fuera una ruta de Windows.
    if ! MSYS_NO_PATHCONV=1 git show "$1:./$ruta" > "$lista.contenido"; then
      falla GIT_ERROR "$rel" "no se pudo leer en $1."
      return 1
    fi
    mkdir -p "$2/${rel%/*}"
    tr -d '\r' < "$lista.contenido" > "$2/$rel"
  done < "$lista"
  return $malo
}

sha_de() { sha256sum < "$1" | cut -d ' ' -f 1; }

# Rutas, relativas a la carpeta dada, de todos los archivos que tiene.
list_files() { # list_files CARPETA
  [ -d "$1" ] || return 0
  (cd "$1" && find . -type f | sed 's|^\./||' | sort)
}

check_immutability() { # check_immutability BASE CABEZA REFERENCIA
  list_files "$1" > "$trabajo/versiones-base.txt"
  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    if [ ! -f "$2/$rel" ]; then
      falla VERSION_REMOVED "$rel" "existe en $3 y se quitó: una versión no se quita."
    elif ! cmp -s "$1/$rel" "$2/$rel"; then
      falla VERSION_CHANGED "$rel" "cambió frente a $3: un cambio es una versión nueva, en archivos nuevos."
    else
      ok "$rel: igual que en $3"
    fi
  done < "$trabajo/versiones-base.txt"
}

# Un esquema no puede llamarse como un contrato OpenAPI: el nombre nombra la release.
check_name_clashes() { # check_name_clashes CABEZA
  for carpeta in "$1"/*/; do
    [ -d "$carpeta" ] || continue
    nombre=$(basename "$carpeta")
    if [ -e "$CONTRACT_DIR/$nombre.yaml" ] || [ -L "$CONTRACT_DIR/$nombre.yaml" ]; then
      falla NAME_CLASH "$nombre" "también es el nombre del contrato $CONTRACT_DIR/$nombre.yaml."
    fi
  done
}

# Quitar el script o sus pruebas en el mismo PR no puede desactivar la comprobación.
check_guards() {
  for guarda in $GUARDAS; do
    if [ ! -f "$guarda" ] || [ -L "$guarda" ]; then
      falla SCRIPT_MISSING "$guarda" "mientras haya esquemas, el árbol conserva este script, run.sh y los escenarios."
    fi
  done
}

run_check() { # run_check REFERENCIA
  base="$trabajo/base"
  cabeza="$trabajo/cabeza"
  mkdir -p "$base" "$cabeza"
  # Aquí solo falla un error de Git; las entradas mal escritas de la base se ignoran.
  scan_ref "$1" "$base" tolerante || exit 1
  scan_worktree "$cabeza"
  archivos_base=$(list_files "$base")
  archivos_arbol=$(list_files "$cabeza")
  if [ -z "$archivos_base" ]; then
    echo "  $1 no tiene esquemas en $SCHEMA_DIR/: nada que proteger"
  fi
  check_immutability "$base" "$cabeza" "$1"
  check_name_clashes "$cabeza"
  if [ -n "$archivos_base" ] || [ -n "$archivos_arbol" ] || [ -d "$SCHEMA_DIR" ]; then
    check_guards
  fi
  if [ "$fallas" -gt 0 ]; then
    echo "$fallas problemas en los esquemas."
    exit 1
  fi
  echo "Esquemas: ninguna versión cambió y todo tiene la forma esperada."
}

# Descarga los archivos de la release y comprueba que cada uno, y su .sha256, coinciden con los de la carpeta dada.
# Sale con 1 si no coinciden o falta alguno, y con 2 si no se pudo descargar.
published_matches() { # published_matches ETIQUETA CARPETA_DE_LOS_ARCHIVOS_ESPERADOS
  descargados="$trabajo/descargados/$1"
  rm -rf "$descargados"
  mkdir -p "$descargados"
  for archivo in "$2"/*.json; do
    asset=$(basename "$archivo")
    esperado=$(sha_de "$archivo")
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
  published_matches "$4" "$5"
  case $? in
    0) ;;
    1) falla PUBLISH_ERROR "$1" "el borrador $4 no quedó con los archivos correctos; no se publicó."; return ;;
    *) falla PUBLISH_ERROR "$1" "no se pudo verificar el borrador $4: $(cat "$error")"; return ;;
  esac
  if gh release edit "$4" --draft=false --latest=false --target "$GITHUB_SHA" \
      --notes "$(notes "$2" "$3" "$5")" 2> "$error"; then
    ok "$1: publicada $4"
  else
    falla PUBLISH_ERROR "$1" "no se pudo publicar el borrador $4: $(cat "$error")"
  fi
}

publish_version() { # publish_version CLAVE NOMBRE VERSION ETIQUETA CARPETA
  # gh distingue «no existe» de un error de red o de permisos, que no se debe tomar como «no existe»: «release not
  # found» es la respuesta de release view, y «HTTP 404» la de la API al consultar la etiqueta.
  if es_borrador=$(gh release view "$4" --json isDraft --jq .isDraft 2> "$error"); then
    if [ "$es_borrador" != true ]; then
      published_matches "$4" "$5"
      case $? in
        0) ok "$1: $4 ya está publicada" ;;
        1) falla PUBLISHED_CHANGED "$1" "$4 ya está publicada con otro contenido o incompleta; un cambio es una versión nueva." ;;
        *) falla PUBLISH_ERROR "$1" "no se pudo descargar la release $4 para verificarla: $(cat "$error")" ;;
      esac
      return
    fi
  elif grep -q 'release not found' "$error"; then
    es_borrador=false
  else
    falla PUBLISH_ERROR "$1" "no se pudo consultar la release $4: $(cat "$error")"
    return
  fi

  if [ "$es_borrador" = true ]; then
    # Borrador de una corrida que se cortó: se completa con el contenido del commit.
    if gh release upload "$4" "$5"/*.json "$5"/*.sha256 --clobber 2> "$error"; then
      publish_draft "$1" "$2" "$3" "$4" "$5"
    else
      falla PUBLISH_ERROR "$1" "no se pudieron subir los archivos al borrador $4: $(cat "$error")"
    fi
  elif gh api "repos/$GH_REPO/git/ref/tags/$4" > /dev/null 2> "$error"; then
    falla PUBLISH_ERROR "$1" "la etiqueta $4 existe sin release; revísala antes de publicar."
  elif ! grep -q 'HTTP 404' "$error"; then
    falla PUBLISH_ERROR "$1" "no se pudo consultar la etiqueta $4: $(cat "$error")"
  elif gh release create "$4" "$5"/*.json "$5"/*.sha256 --draft --latest=false --target "$GITHUB_SHA" \
      --title "Esquema $2 $3" --notes "$(notes "$2" "$3" "$5")" 2> "$error"; then
    publish_draft "$1" "$2" "$3" "$4" "$5"
  else
    falla PUBLISH_ERROR "$1" "no se pudo crear el borrador $4: $(cat "$error")"
  fi
}

run_publish() {
  versiones="$trabajo/versiones"
  mkdir -p "$versiones"
  if [ "$simulacion" = true ]; then
    scan_worktree "$versiones" || exit 1
  else
    scan_ref "$GITHUB_SHA" "$versiones" estricto || exit 1
  fi
  # <nombre>/<versión>, una vez por pareja de archivos.
  list_files "$versiones" | sed -e 's|\.casos\.json$||' -e 's|\.json$||' | sort -u > "$trabajo/claves.txt"
  if [ ! -s "$trabajo/claves.txt" ]; then
    falla NO_SCHEMAS "$SCHEMA_DIR" "el proyecto no tiene esquemas que publicar."
    exit 1
  fi

  while IFS= read -r clave; do
    nombre=${clave%/*}
    version=${clave#*/}
    if [ ! -f "$versiones/$clave.json" ] || [ ! -f "$versiones/$clave.casos.json" ]; then
      falla INCOMPLETE_VERSION "$clave" "cada versión lleva $version.json y $version.casos.json."
      continue
    fi
    etiqueta="jsonschema-$nombre-v$version"
    # Los archivos de la release, con el nombre que lleva en GitHub y el .sha256 de cada uno.
    carpeta="$trabajo/archivos/$etiqueta"
    mkdir -p "$carpeta"
    for sufijo in .json .casos.json; do
      cp "$versiones/$clave$sufijo" "$carpeta/$nombre-$version$sufijo"
      sha=$(sha_de "$carpeta/$nombre-$version$sufijo")
      printf '%s  %s\n' "$sha" "$nombre-$version$sufijo" > "$carpeta/$nombre-$version$sufijo.sha256"
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
