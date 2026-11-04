#!/bin/sh
# Comparador de cambios incompatibles de los contratos OpenAPI (compuerta Q08, doc. 04 §8; doc. 01 §10; ADR 0027).
# Compara cada contrato con su versión base usando openapi-diff (versión fijada en pom.xml) y aplica estas reglas:
#   - si el contrato cambia, su info.version (X.Y.Z) sube;
#   - un cambio incompatible, o quitar un contrato, pasa solo si el PR tiene la etiqueta contrato-incompatible
#     (LIV_CONTRACTS_BREAKING_APPROVED=true) y, si el contrato sigue, con la versión mayor subida;
#   - un contrato nuevo pasa.
# Uso, desde backend/ y en Windows desde Git Bash:
#   sh contracts/scripts/compare.sh origin/main        el árbol de trabajo contra esa referencia de Git
#   sh contracts/scripts/compare.sh --dirs BASE HEAD   dos carpetas de contratos, relativas a backend/ (pruebas)
# Sale con 0 si se puede integrar y con 1 si no. Cada falla lleva su código entre corchetes, p. ej. [INCOMPATIBLE].
set -u
cd "$(dirname "$0")/../.."
. contracts/scripts/lib.sh

trabajo=target/contracts-compare
aprobado=${LIV_CONTRACTS_BREAKING_APPROVED:-false}
fallas=0

# Resultado de openapi-diff: incompatible, compatible, equivalent o error. Deja el reporte en $trabajo.
diff_result() { # diff_result BASE HEAD NOMBRE
  reporte="$trabajo/$3.txt"
  rm -f "$reporte"
  if ! sh mvnw -B -ntp -q org.openapitools.openapidiff:openapi-diff-maven:diff \
      -DoldSpec="$1" -DnewSpec="$2" -DconsoleOutputFileName="$reporte" > "$trabajo/$3.log" 2>&1; then
    echo error
  elif grep -q 'API changes broke backward compatibility' "$reporte"; then
    echo incompatible
  elif grep -q 'API changes are backward compatible' "$reporte"; then
    echo compatible
  elif grep -q 'No differences' "$reporte"; then
    echo equivalent
  else
    echo error
  fi
}

compare_contract() { # compare_contract CARPETA_BASE CARPETA_HEAD NOMBRE
  base="$1/$3.yaml"
  head="$2/$3.yaml"

  if [ ! -f "$head" ]; then
    if [ "$aprobado" = true ]; then
      ok "$3: se quitó, con la etiqueta contrato-incompatible"
    else
      falla CONTRACT_REMOVED "$3" "se quitó el contrato; requiere la etiqueta contrato-incompatible."
    fi
    return
  fi

  v_head=$(contract_version "$head")
  if ! is_semver "$v_head"; then
    falla INVALID_VERSION "$3" "info.version debe ser X.Y.Z y es '$v_head'."
    return
  fi
  if [ ! -f "$base" ]; then
    ok "$3: contrato nuevo ($v_head)"
    return
  fi
  if cmp -s "$base" "$head"; then
    ok "$3: sin cambios ($v_head)"
    return
  fi

  v_base=$(contract_version "$base")
  if is_semver "$v_base" && ! semver_gt "$v_head" "$v_base"; then
    falla VERSION_NOT_BUMPED "$3" "el contrato cambió y info.version debe subir de $v_base (es $v_head)."
  fi

  case "$(diff_result "$base" "$head" "$3")" in
    error)
      falla COMPARE_ERROR "$3" "openapi-diff no pudo comparar el contrato. Su salida:"
      sed 's/^/             /' "$trabajo/$3.log"
      ;;
    incompatible)
      if [ "$aprobado" != true ]; then
        falla INCOMPATIBLE "$3" "cambio incompatible; requiere la etiqueta contrato-incompatible y subir la versión mayor."
        sed 's/^/             /' "$trabajo/$3.txt"
      elif is_semver "$v_base" && [ "$(major "$v_head")" -gt "$(major "$v_base")" ]; then
        ok "$3: cambio incompatible aprobado ($v_base → $v_head)"
      else
        falla MAJOR_NOT_BUMPED "$3" "cambio incompatible aprobado; la versión mayor debe subir de $v_base (es $v_head)."
      fi
      ;;
    *)
      ok "$3: cambio compatible ($v_base → $v_head)"
      ;;
  esac
}

case "${1:-}" in
  --dirs)
    [ $# -eq 3 ] || { echo "Uso: sh contracts/scripts/compare.sh --dirs BASE HEAD" >&2; exit 2; }
    base_dir=$2
    head_dir=$3
    ;;
  '' | -*)
    echo "Uso: sh contracts/scripts/compare.sh REFERENCIA_GIT | --dirs BASE HEAD" >&2
    exit 2
    ;;
  *)
    base_dir="$trabajo/base"
    head_dir="$trabajo/head"
    rm -rf "$base_dir" "$head_dir"
    mkdir -p "$base_dir" "$head_dir"
    collect_ref "$1" "$base_dir" && collect_worktree "$head_dir" || exit 1
    ;;
esac
mkdir -p "$trabajo"

echo "Contratos: $head_dir contra $base_dir (etiqueta contrato-incompatible: $aprobado)"
nombres=$(for archivo in "$base_dir"/*.yaml "$head_dir"/*.yaml; do
  [ -f "$archivo" ] && basename "$archivo" .yaml
done | sort -u)
for nombre in $nombres; do
  compare_contract "$base_dir" "$head_dir" "$nombre"
done

if [ "$fallas" -gt 0 ]; then
  echo "$fallas fallas en los contratos."
  exit 1
fi
echo "Los contratos se pueden integrar."
