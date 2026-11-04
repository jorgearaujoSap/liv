#!/bin/sh
# Publica los contratos OpenAPI como releases de GitHub (plan de la feature 001, paso 6; ADR 0027). Una release
# por contrato y versión, con la etiqueta contract-<nombre>-v<info.version>, que lleva <nombre>.yaml y
# <nombre>.yaml.sha256. El frontend las descarga por URL fija y fija la versión y el SHA-256 en
# api-spec/versions.json (paso 11). El workflow corre antes compare.sh contra el commit anterior.
#   - La release se crea como borrador, se suben los dos archivos, se verifican y solo entonces se publica: nadie
#     ve una release incompleta, y funciona también si el repositorio activa las releases inmutables.
#   - Se puede correr de nuevo: un borrador que quedó de una corrida cortada se completa, y una versión ya
#     publicada con el mismo contenido no se toca.
#   - Si la versión ya está publicada con otro contenido (se compara el SHA-256 del YAML descargado, no solo el
#     del archivo .sha256), falla: lo publicado no cambia, el contrato debe subir de versión.
#   - Las releases no se marcan como la más reciente: esa queda para las versiones del backend (backend-vX.Y.Z).
# Necesita gh con GH_TOKEN (permiso contents: write), GH_REPO (dueño/repositorio) y GITHUB_SHA (el commit que se
# publica); en GitHub Actions los pone el workflow. Las pruebas cambian gh por uno falso con LIV_GH, y usan
# LIV_CONTRACTS_ROOT y LIV_CONTRACTS_WORK como compare.sh.
# Uso, desde backend/:  sh contracts/scripts/publish.sh [--dry-run]   (--dry-run solo dice qué publicaría)
# Sale con 0 si todos los contratos quedaron publicados y con 1 si alguno no.
set -u
scripts=$(cd "$(dirname "$0")" && pwd)
proyecto=$(cd "$scripts/../.." && pwd)
. "$scripts/lib.sh"

gh() { ${LIV_GH:-command gh} "$@"; }

simulacion=false
[ "${1:-}" = --dry-run ] && simulacion=true
if [ "$simulacion" = false ] && { [ -z "${GH_REPO:-}" ] || [ -z "${GITHUB_SHA:-}" ]; }; then
  echo "Faltan GH_REPO o GITHUB_SHA. Para ver qué se publicaría: sh contracts/scripts/publish.sh --dry-run" >&2
  exit 2
fi

trabajo=${LIV_CONTRACTS_WORK:-$proyecto/target/contracts-publish}
rm -rf "$trabajo"
mkdir -p "$trabajo/contratos"
trabajo=$(cd "$trabajo" && pwd)
cd "${LIV_CONTRACTS_ROOT:-$proyecto}" || exit 2
fallas=0
error="$trabajo/error.txt"

# Descarga los dos archivos de la release y comprueba que el YAML y el .sha256 publicados coinciden con el
# contrato. Falla si falta alguno o si no coinciden.
published_matches() { # published_matches ETIQUETA NOMBRE SHA
  descargados="$trabajo/descargados/$2"
  rm -rf "$descargados"
  mkdir -p "$descargados"
  gh release download "$1" --pattern "$2.yaml" --dir "$descargados" --clobber 2> "$error" &&
    gh release download "$1" --pattern "$2.yaml.sha256" --dir "$descargados" --clobber 2> "$error" &&
    [ "$(sha256sum "$descargados/$2.yaml" | cut -d ' ' -f 1)" = "$3" ] &&
    [ "$(cut -d ' ' -f 1 "$descargados/$2.yaml.sha256")" = "$3" ]
}

# Publica un borrador que ya tiene los dos archivos, después de verificarlos.
publish_draft() { # publish_draft ETIQUETA NOMBRE SHA
  if ! published_matches "$1" "$2" "$3"; then
    falla PUBLISH_ERROR "$2" "el borrador $1 no quedó con los dos archivos correctos; no se publicó."
  elif ! gh release edit "$1" --draft=false --latest=false --target "$GITHUB_SHA" 2> "$error"; then
    falla PUBLISH_ERROR "$2" "no se pudo publicar el borrador $1: $(cat "$error")"
  else
    ok "$2: publicada $1"
  fi
}

publish_contract() { # publish_contract ARCHIVO NOMBRE VERSION ETIQUETA SHA
  # gh distingue «no existe» de un error de red o de permisos, que no se debe tomar como «no existe».
  if es_borrador=$(gh release view "$4" --json isDraft --jq .isDraft 2> "$error"); then
    if [ "$es_borrador" = true ]; then
      # Borrador de una corrida que se cortó: se completa con este contenido.
      if gh release upload "$4" "$1" "$1.sha256" --clobber 2> "$error"; then
        publish_draft "$4" "$2" "$5"
      else
        falla PUBLISH_ERROR "$2" "no se pudieron subir los archivos al borrador $4: $(cat "$error")"
      fi
    elif published_matches "$4" "$2" "$5"; then
      ok "$2: $4 ya está publicada"
    else
      falla PUBLISHED_CHANGED "$2" "$4 ya está publicada con otro contenido o incompleta; el contrato debe subir de versión."
    fi
  elif ! grep -q 'release not found' "$error"; then
    falla PUBLISH_ERROR "$2" "no se pudo consultar la release $4: $(cat "$error")"
  elif gh api "repos/$GH_REPO/git/ref/tags/$4" > /dev/null 2> "$error"; then
    falla PUBLISH_ERROR "$2" "la etiqueta $4 existe sin release; revísala antes de publicar."
  elif ! grep -q 'HTTP 404' "$error"; then
    falla PUBLISH_ERROR "$2" "no se pudo consultar la etiqueta $4: $(cat "$error")"
  elif gh release create "$4" "$1" "$1.sha256" --draft --latest=false --target "$GITHUB_SHA" \
      --title "Contrato $2 $3" \
      --notes "Contrato OpenAPI \`$2\` $3 de LIV, publicado desde $GITHUB_SHA. SHA-256 de \`$2.yaml\`: \`$5\`." \
      2> "$error"; then
    publish_draft "$4" "$2" "$5"
  else
    falla PUBLISH_ERROR "$2" "no se pudo crear el borrador $4: $(cat "$error")"
  fi
}

collect_worktree "$trabajo/contratos" || exit 1
if ! ls "$trabajo/contratos"/*.yaml > /dev/null 2>&1; then
  falla NO_CONTRACTS "$CONTRACT_DIR" "el proyecto no tiene contratos que publicar."
  exit 1
fi

for archivo in "$trabajo/contratos"/*.yaml; do
  nombre=$(basename "$archivo" .yaml)
  version=$(contract_version "$archivo")
  if ! is_semver "$version"; then
    falla INVALID_VERSION "$nombre" "info.version debe ser X.Y.Z y estar una sola vez; es '$version'."
    continue
  fi
  etiqueta="contract-$nombre-v$version"
  sha=$(sha256sum "$archivo" | cut -d ' ' -f 1)
  printf '%s  %s\n' "$sha" "$nombre.yaml" > "$archivo.sha256"

  if [ "$simulacion" = true ]; then
    ok "$nombre: publicaría $etiqueta (SHA-256 $sha)"
  else
    publish_contract "$archivo" "$nombre" "$version" "$etiqueta" "$sha"
  fi
done

if [ "$fallas" -gt 0 ]; then
  echo "$fallas contratos sin publicar."
  exit 1
fi
if [ "$simulacion" = true ]; then
  echo "Simulación: no se publicó nada."
else
  echo "Contratos publicados."
fi
