#!/bin/sh
# Publica los contratos OpenAPI como releases de GitHub (plan de la feature 001, paso 6; ADR 0027). Una release
# por contrato y versión, con la etiqueta contract-<nombre>-v<info.version>, que lleva <nombre>.yaml y
# <nombre>.yaml.sha256. El frontend las descarga por URL fija y fija la versión y el SHA-256 en
# api-spec/versions.json (paso 11).
#   - Publica el contenido del commit GITHUB_SHA, no el del árbol de trabajo.
#   - Antes de publicar una versión, la juzga con compare.sh contra la release publicada más alta de su contrato,
#     con la etiqueta del PR integrado (LIV_CONTRACTS_BREAKING_APPROVED). Así no se publica nada que no cumpla Q08
#     frente a lo que el frontend ya usa, aunque un PR anterior se haya integrado con el check en rojo o un
#     contrato se haya quitado y vuelto a agregar. Un contrato sin releases se valida como contrato nuevo.
#   - La release se crea como borrador, se suben los dos archivos, se verifican descargados y solo entonces se
#     publica: nadie ve una release incompleta, y funciona también con releases inmutables.
#   - Se puede correr de nuevo: un borrador que quedó de una corrida cortada se juzga y se completa, y una versión
#     ya publicada con el mismo contenido no se toca.
#   - Si la versión ya está publicada con otro contenido (se compara el SHA-256 del YAML descargado, no solo el
#     del archivo .sha256), falla: lo publicado no cambia, el contrato debe subir de versión.
#   - Las releases no se marcan como la más reciente: esa queda para las versiones del backend (backend-vX.Y.Z).
# Necesita gh con GH_TOKEN (permiso contents: write), GH_REPO (dueño/repositorio) y GITHUB_SHA (el commit que se
# publica); en GitHub Actions los pone el workflow. Las pruebas cambian gh por uno falso con LIV_GH, y usan
# LIV_CONTRACTS_ROOT y LIV_CONTRACTS_WORK como compare.sh.
# Uso, desde backend/:
#   sh contracts/scripts/publish.sh             publica el commit GITHUB_SHA
#   sh contracts/scripts/publish.sh --dry-run   solo dice qué versiones hay en el árbol de trabajo
# Sale con 0 si todos los contratos quedaron publicados y con 1 si alguno no.
set -u
scripts=$(cd "$(dirname "$0")" && pwd)
proyecto=$(cd "$scripts/../.." && pwd)
. "$scripts/lib.sh"

gh() { ${LIV_GH:-command gh} "$@"; }

case "${1:-}" in
  --dry-run) simulacion=true ;;
  '') simulacion=false ;;
  *)
    echo "Uso: sh contracts/scripts/publish.sh [--dry-run]" >&2
    exit 2
    ;;
esac
if [ "$simulacion" = false ] && { [ -z "${GH_REPO:-}" ] || [ -z "${GITHUB_SHA:-}" ]; }; then
  echo "Faltan GH_REPO o GITHUB_SHA. Para ver qué se publicaría: sh contracts/scripts/publish.sh --dry-run" >&2
  exit 2
fi

fallas=0
prepare_workdir "${LIV_CONTRACTS_WORK:-$proyecto/target/contracts-publish}" "${LIV_CONTRACTS_ROOT:-$proyecto}"
cd "${LIV_CONTRACTS_ROOT:-$proyecto}" || exit 2
contratos="$trabajo/contratos"
mkdir -p "$contratos"
error="$trabajo/error.txt"

# Descarga los dos archivos de la release y comprueba que el YAML y el .sha256 publicados coinciden con el SHA
# dado. Sale con 1 si no coinciden o falta alguno, y con 2 si no se pudo descargar. Deja el YAML en $descargados.
published_matches() { # published_matches ETIQUETA NOMBRE SHA
  descargados="$trabajo/descargados/$1"
  rm -rf "$descargados"
  mkdir -p "$descargados"
  for archivo in "$2.yaml" "$2.yaml.sha256"; do
    if ! gh release download "$1" --pattern "$archivo" --dir "$descargados" --clobber 2> "$error"; then
      grep -q 'no assets' "$error" && return 1
      return 2
    fi
  done
  [ "$(sha256sum < "$descargados/$2.yaml" | cut -d ' ' -f 1)" = "$3" ] &&
    [ "$(cut -d ' ' -f 1 "$descargados/$2.yaml.sha256")" = "$3" ]
}

# Versión más alta con etiqueta contract-<nombre>-vX.Y.Z (en GitHub, una etiqueta así es de una release publicada
# o se creó a mano). El filtro exacto deja fuera a otro contrato con el mismo prefijo, como muestra-v2. No escribe
# nada si no hay ninguna, y sale con 1 si no se pudieron consultar.
latest_published() { # latest_published NOMBRE
  gh api "repos/$GH_REPO/git/matching-refs/tags/contract-$1-v" --paginate --jq '.[].ref' \
    > "$trabajo/etiquetas.txt" 2> "$error" || return 1
  sed -n "s|^refs/tags/contract-$1-v\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)\$|\1|p" "$trabajo/etiquetas.txt" |
    sort -t . -k 1,1n -k 2,2n -k 3,3n | tail -n 1
}

# Juzga el contrato con compare.sh contra la release publicada más alta, o como contrato nuevo si no tiene.
judge_against_release() { # judge_against_release ARCHIVO NOMBRE
  juicio="$trabajo/juicio/$2"
  mkdir -p "$juicio/base" "$juicio/head"
  cp "$1" "$juicio/head/$2.yaml"
  if ! publicada=$(latest_published "$2"); then
    falla PUBLISH_ERROR "$2" "no se pudieron consultar sus releases publicadas: $(cat "$error")"
    return 1
  fi
  if [ -n "$publicada" ]; then
    etiqueta_base="contract-$2-v$publicada"
    # La release de la base también se verifica: su YAML debe coincidir con su .sha256.
    gh release download "$etiqueta_base" --pattern "$2.yaml.sha256" --dir "$juicio" --clobber 2> "$error" &&
      published_matches "$etiqueta_base" "$2" "$(cut -d ' ' -f 1 "$juicio/$2.yaml.sha256")"
    if [ $? -ne 0 ]; then
      falla PUBLISH_ERROR "$2" "no se pudo leer, o no coincide con su SHA-256, la release publicada $etiqueta_base: $(cat "$error")"
      return 1
    fi
    cp "$descargados/$2.yaml" "$juicio/base/$2.yaml"
    echo "  $2: se juzga contra la release publicada $etiqueta_base"
  else
    echo "  $2: no tiene releases publicadas; se juzga como contrato nuevo"
  fi
  if ! LIV_CONTRACTS_WORK="$juicio/trabajo" sh "$scripts/compare.sh" --dirs "$juicio/base" "$juicio/head"; then
    falla REJECTED "$2" "no cumple Q08 frente a lo publicado; no se publica."
    return 1
  fi
}

notes() { # notes NOMBRE VERSION SHA
  printf 'Contrato OpenAPI `%s` %s de LIV, publicado desde %s. SHA-256 de `%s.yaml`: `%s`.' \
    "$1" "$2" "$GITHUB_SHA" "$1" "$3"
}

# Publica un borrador que ya tiene los dos archivos, después de verificarlos.
publish_draft() { # publish_draft ETIQUETA NOMBRE VERSION SHA
  published_matches "$1" "$2" "$4"
  case $? in
    0) ;;
    1) falla PUBLISH_ERROR "$2" "el borrador $1 no quedó con los dos archivos correctos; no se publicó."; return ;;
    *) falla PUBLISH_ERROR "$2" "no se pudo verificar el borrador $1: $(cat "$error")"; return ;;
  esac
  if gh release edit "$1" --draft=false --latest=false --target "$GITHUB_SHA" \
      --notes "$(notes "$2" "$3" "$4")" 2> "$error"; then
    ok "$2: publicada $1"
  else
    falla PUBLISH_ERROR "$2" "no se pudo publicar el borrador $1: $(cat "$error")"
  fi
}

publish_contract() { # publish_contract ARCHIVO NOMBRE VERSION ETIQUETA SHA
  # gh distingue «no existe» de un error de red o de permisos, que no se debe tomar como «no existe».
  if es_borrador=$(gh release view "$4" --json isDraft --jq .isDraft 2> "$error"); then
    if [ "$es_borrador" != true ]; then
      published_matches "$4" "$2" "$5"
      case $? in
        0) ok "$2: $4 ya está publicada" ;;
        1) falla PUBLISHED_CHANGED "$2" "$4 ya está publicada con otro contenido o incompleta; el contrato debe subir de versión." ;;
        *) falla PUBLISH_ERROR "$2" "no se pudo descargar la release $4 para verificarla: $(cat "$error")" ;;
      esac
      return
    fi
  elif grep -q 'release not found' "$error"; then
    es_borrador=false
  else
    falla PUBLISH_ERROR "$2" "no se pudo consultar la release $4: $(cat "$error")"
    return
  fi

  judge_against_release "$1" "$2" || return

  if [ "$es_borrador" = true ]; then
    # Borrador de una corrida que se cortó: ya juzgado, se completa con este contenido.
    if gh release upload "$4" "$1" "$1.sha256" --clobber 2> "$error"; then
      publish_draft "$4" "$2" "$3" "$5"
    else
      falla PUBLISH_ERROR "$2" "no se pudieron subir los archivos al borrador $4: $(cat "$error")"
    fi
  elif gh api "repos/$GH_REPO/git/ref/tags/$4" > /dev/null 2> "$error"; then
    falla PUBLISH_ERROR "$2" "la etiqueta $4 existe sin release; revísala antes de publicar."
  elif ! grep -q 'HTTP 404' "$error"; then
    falla PUBLISH_ERROR "$2" "no se pudo consultar la etiqueta $4: $(cat "$error")"
  elif gh release create "$4" "$1" "$1.sha256" --draft --latest=false --target "$GITHUB_SHA" \
      --title "Contrato $2 $3" --notes "$(notes "$2" "$3" "$5")" 2> "$error"; then
    publish_draft "$4" "$2" "$3" "$5"
  else
    falla PUBLISH_ERROR "$2" "no se pudo crear el borrador $4: $(cat "$error")"
  fi
}

if [ "$simulacion" = true ]; then
  collect_worktree "$contratos" || exit 1
else
  collect_ref "$GITHUB_SHA" "$contratos" || exit 1
fi
if ! ls "$contratos"/*.yaml > /dev/null 2>&1; then
  falla NO_CONTRACTS "$CONTRACT_DIR" "el proyecto no tiene contratos que publicar."
  exit 1
fi

for archivo in "$contratos"/*.yaml; do
  nombre=$(basename "$archivo" .yaml)
  version=$(contract_version "$archivo")
  if ! is_semver "$version"; then
    falla INVALID_VERSION "$nombre" "info.version debe ser X.Y.Z y estar una sola vez; es '$version'."
    continue
  fi
  etiqueta="contract-$nombre-v$version"
  sha=$(sha256sum < "$archivo" | cut -d ' ' -f 1)
  printf '%s  %s\n' "$sha" "$nombre.yaml" > "$archivo.sha256"

  if [ "$simulacion" = true ]; then
    ok "$nombre: $etiqueta (SHA-256 $sha)"
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
