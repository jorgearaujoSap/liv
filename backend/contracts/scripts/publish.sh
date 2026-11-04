#!/bin/sh
# Publica los contratos OpenAPI como releases de GitHub (plan de la feature 001, paso 6; ADR 0027). Una release
# por contrato y versión, con la etiqueta contract-<nombre>-v<info.version>, que lleva <nombre>.yaml y
# <nombre>.yaml.sha256. El frontend las descarga por URL fija y fija la versión y el SHA-256 en
# api-spec/versions.json (paso 11).
#   - Si la versión ya tiene release con el mismo contenido, no hace nada: se puede correr de nuevo.
#   - Si la tiene con otro contenido, falla: lo publicado no cambia, el contrato debe subir de versión.
#   - Las releases no se marcan como la más reciente: esa queda para las versiones del backend (backend-vX.Y.Z).
# Necesita gh con GH_TOKEN (permiso contents: write), GH_REPO (dueño/repositorio) y GITHUB_SHA (el commit que se
# publica); en GitHub Actions los pone el workflow.
# Uso, desde backend/:  sh contracts/scripts/publish.sh [--dry-run]   (--dry-run solo dice qué publicaría)
# Sale con 0 si todos los contratos quedaron publicados y con 1 si alguno no.
set -u
cd "$(dirname "$0")/../.."
. contracts/scripts/lib.sh

simulacion=false
[ "${1:-}" = --dry-run ] && simulacion=true
if [ "$simulacion" = false ] && { [ -z "${GH_REPO:-}" ] || [ -z "${GITHUB_SHA:-}" ]; }; then
  echo "Faltan GH_REPO o GITHUB_SHA. Para ver qué se publicaría: sh contracts/scripts/publish.sh --dry-run" >&2
  exit 2
fi

trabajo=target/contracts-publish
rm -rf "$trabajo"
mkdir -p "$trabajo/publicados"
collect_worktree "$trabajo" || exit 1
fallas=0

for archivo in "$trabajo"/*.yaml; do
  [ -f "$archivo" ] || continue
  nombre=$(basename "$archivo" .yaml)
  version=$(contract_version "$archivo")
  if ! is_semver "$version"; then
    falla INVALID_VERSION "$nombre" "info.version debe ser X.Y.Z y es '$version'."
    continue
  fi
  etiqueta="contract-$nombre-v$version"
  sha=$(sha256sum "$archivo" | cut -d ' ' -f 1)
  printf '%s  %s\n' "$sha" "$nombre.yaml" > "$archivo.sha256"

  if [ "$simulacion" = true ]; then
    ok "$nombre: publicaría $etiqueta (SHA-256 $sha)"
    continue
  fi

  # gh release view distingue "no existe" de un error de red o de permisos, que no debe tomarse como "no existe".
  if salida=$(gh release view "$etiqueta" --json tagName 2>&1); then
    if ! gh release download "$etiqueta" --pattern "$nombre.yaml.sha256" --dir "$trabajo/publicados" --clobber; then
      falla PUBLISH_ERROR "$nombre" "no se pudo leer el SHA-256 de la release $etiqueta."
    elif [ "$(cut -d ' ' -f 1 "$trabajo/publicados/$nombre.yaml.sha256")" = "$sha" ]; then
      ok "$nombre: $etiqueta ya está publicada"
    else
      falla PUBLISHED_CHANGED "$nombre" "$etiqueta ya está publicada con otro contenido; el contrato debe subir de versión."
    fi
  elif printf '%s' "$salida" | grep -q 'release not found'; then
    if git ls-remote --exit-code --tags origin "refs/tags/$etiqueta" > /dev/null; then
      falla PUBLISH_ERROR "$nombre" "la etiqueta $etiqueta existe sin release; revísala antes de publicar."
    elif gh release create "$etiqueta" "$archivo" "$archivo.sha256" --target "$GITHUB_SHA" --latest=false \
        --title "Contrato $nombre $version" \
        --notes "Contrato OpenAPI \`$nombre\` $version del backend de LIV, publicado desde $GITHUB_SHA. SHA-256 de \`$nombre.yaml\`: \`$sha\`."; then
      ok "$nombre: publicada $etiqueta"
    else
      falla PUBLISH_ERROR "$nombre" "no se pudo crear la release $etiqueta."
    fi
  else
    falla PUBLISH_ERROR "$nombre" "no se pudo consultar la release $etiqueta: $salida"
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
