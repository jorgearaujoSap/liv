#!/bin/sh
# Publica los contratos OpenAPI como releases de GitHub (plan de la feature 001, paso 6; ADR 0027). Una release
# por contrato y versión, con la etiqueta contract-<nombre>-v<info.version>, que lleva <nombre>.yaml y
# <nombre>.yaml.sha256. El frontend las descarga por URL fija y fija la versión y el SHA-256 en
# api-spec/versions.json (paso 11).
#   - Publica el contenido del commit GITHUB_SHA, no el del árbol de trabajo.
#   - Solo publica lo que ya se juzgó: la versión que introduce este commit frente a BASE (el workflow corre antes
#     compare.sh contra esa misma base), o un contrato que nunca tuvo release. Si queda una versión sin publicar de
#     un commit anterior (su publicación falló o no corrió), falla con UNPUBLISHED_EARLIER: se resuelve con
#     «Re-run» de la ejecución de ese commit o con una versión nueva.
#   - La release se crea como borrador, se suben los dos archivos, se verifican descargados y solo entonces se
#     publica: nadie ve una release incompleta, y funciona también con releases inmutables.
#   - Se puede correr de nuevo: un borrador que quedó de una corrida cortada se completa, y una versión ya
#     publicada con el mismo contenido no se toca.
#   - Si la versión ya está publicada con otro contenido (se compara el SHA-256 del YAML descargado, no solo el
#     del archivo .sha256), falla: lo publicado no cambia, el contrato debe subir de versión.
#   - Las releases no se marcan como la más reciente: esa queda para las versiones del backend (backend-vX.Y.Z).
# Necesita gh con GH_TOKEN (permiso contents: write), GH_REPO (dueño/repositorio) y GITHUB_SHA (el commit que se
# publica); en GitHub Actions los pone el workflow. Las pruebas cambian gh por uno falso con LIV_GH, y usan
# LIV_CONTRACTS_ROOT y LIV_CONTRACTS_WORK como compare.sh.
# Uso, desde backend/:
#   sh contracts/scripts/publish.sh BASE        p. ej. HEAD^1, el commit anterior de main
#   sh contracts/scripts/publish.sh --dry-run   solo dice qué versiones hay en el árbol de trabajo
# Sale con 0 si todos los contratos quedaron publicados y con 1 si alguno no.
set -u
scripts=$(cd "$(dirname "$0")" && pwd)
proyecto=$(cd "$scripts/../.." && pwd)
. "$scripts/lib.sh"

gh() { ${LIV_GH:-command gh} "$@"; }

case "${1:-}" in
  --dry-run) simulacion=true ;;
  '' | -*)
    echo "Uso: sh contracts/scripts/publish.sh BASE | --dry-run" >&2
    exit 2
    ;;
  *) simulacion=false base_ref=$1 ;;
esac
if [ "$simulacion" = false ] && { [ -z "${GH_REPO:-}" ] || [ -z "${GITHUB_SHA:-}" ]; }; then
  echo "Faltan GH_REPO o GITHUB_SHA. Para ver qué se publicaría: sh contracts/scripts/publish.sh --dry-run" >&2
  exit 2
fi

fallas=0
prepare_workdir "${LIV_CONTRACTS_WORK:-$proyecto/target/contracts-publish}" "${LIV_CONTRACTS_ROOT:-$proyecto}"
cd "${LIV_CONTRACTS_ROOT:-$proyecto}" || exit 2
contratos="$trabajo/contratos"
base_dir="$trabajo/base"
mkdir -p "$contratos" "$base_dir"
error="$trabajo/error.txt"

# Descarga los dos archivos de la release y comprueba que el YAML y el .sha256 publicados coinciden con el
# contrato. Sale con 1 si no coinciden o falta alguno, y con 2 si no se pudo descargar.
published_matches() { # published_matches ETIQUETA NOMBRE SHA
  descargados="$trabajo/descargados/$2"
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

# Verdadero si el contrato nunca tuvo una release publicada, de ninguna versión.
never_published() { # never_published NOMBRE
  gh release list --exclude-drafts --limit 1000 --json tagName --jq '.[].tagName' > "$trabajo/releases.txt" \
    2> "$error" || return 2
  ! grep -q "^contract-$1-v" "$trabajo/releases.txt"
}

publish_contract() { # publish_contract ARCHIVO NOMBRE VERSION ETIQUETA SHA
  # gh distingue «no existe» de un error de red o de permisos, que no se debe tomar como «no existe».
  if es_borrador=$(gh release view "$4" --json isDraft --jq .isDraft 2> "$error"); then
    if [ "$es_borrador" = true ]; then
      # Borrador de una corrida que se cortó: se completa con este contenido.
      if gh release upload "$4" "$1" "$1.sha256" --clobber 2> "$error"; then
        publish_draft "$4" "$2" "$3" "$5"
      else
        falla PUBLISH_ERROR "$2" "no se pudieron subir los archivos al borrador $4: $(cat "$error")"
      fi
      return
    fi
    published_matches "$4" "$2" "$5"
    case $? in
      0) ok "$2: $4 ya está publicada" ;;
      1) falla PUBLISHED_CHANGED "$2" "$4 ya está publicada con otro contenido o incompleta; el contrato debe subir de versión." ;;
      *) falla PUBLISH_ERROR "$2" "no se pudo descargar la release $4 para verificarla: $(cat "$error")" ;;
    esac
    return
  fi
  if ! grep -q 'release not found' "$error"; then
    falla PUBLISH_ERROR "$2" "no se pudo consultar la release $4: $(cat "$error")"
    return
  fi

  # La versión no tiene release. Se publica solo si este commit la introduce o si el contrato nunca se publicó.
  if [ -f "$base_dir/$2.yaml" ] && cmp -s "$base_dir/$2.yaml" "$1"; then
    never_published "$2"
    case $? in
      0) ;;
      1)
        falla UNPUBLISHED_EARLIER "$2" "la versión $3 llegó en un commit anterior cuya publicación no corrió o falló; usa «Re-run» de esa ejecución o sube la versión."
        return
        ;;
      *) falla PUBLISH_ERROR "$2" "no se pudieron listar las releases: $(cat "$error")"; return ;;
    esac
  fi

  if gh api "repos/$GH_REPO/git/ref/tags/$4" > /dev/null 2> "$error"; then
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
  collect_ref "$base_ref" "$base_dir" || exit 1
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
