#!/bin/sh
# Comparador de cambios incompatibles de los contratos OpenAPI (compuerta Q08, doc. 04 §8; doc. 01 §10; ADR 0027).
# Compara cada contrato de contracts/openapi/ con su versión base usando openapi-diff (versión fijada en pom.xml)
# y aplica estas reglas:
#   - si el contrato cambia, su info.version (X.Y.Z) sube;
#   - un cambio incompatible, o quitar un contrato, pasa solo si el PR tiene la etiqueta contrato-incompatible
#     (LIV_CONTRACTS_BREAKING_APPROVED=true) y, si el contrato sigue, con la versión mayor subida;
#   - un contrato nuevo pasa si openapi-diff lo puede leer;
#   - los $ref son internos (#/...) y el proyecto tiene al menos un contrato.
# En CI, el que juzga es el comparador de la base (HEAD^1, con su pom.xml), no el del PR: un PR no cambia la regla
# con la que se juzga. Un cambio en estos scripts rige desde la integración siguiente.
# Huecos conocidos de openapi-diff 2.1.7, que revisa una persona (ADR 0027): no detecta una petición más estricta
# por minLength, minimum o similares, ni un campo obligatorio de la respuesta que pasa a nullable.
#
# Uso, desde backend/ y en Windows desde Git Bash:
#   sh contracts/scripts/compare.sh origin/main        el árbol de trabajo contra esa referencia de Git
#   sh contracts/scripts/compare.sh --dirs BASE HEAD   dos carpetas de contratos (lo usan las pruebas)
# Variables opcionales: LIV_CONTRACTS_ROOT, la carpeta con contracts/openapi/ y el repositorio Git (por defecto,
# este proyecto); LIV_CONTRACTS_WORK, la carpeta de trabajo (por defecto, target/contracts-compare). Las pruebas
# las usan para no tocar el proyecto ni compartir archivos entre corridas.
# Sale con 0 si se puede integrar y con 1 si no. Cada falla lleva su código entre corchetes, p. ej. [INCOMPATIBLE].
set -u
scripts=$(cd "$(dirname "$0")" && pwd)
proyecto=$(cd "$scripts/../.." && pwd)
. "$scripts/lib.sh"

uso() {
  echo "Uso: sh contracts/scripts/compare.sh REFERENCIA_GIT | --dirs BASE HEAD" >&2
  exit 2
}
case "${1:-}" in
  --dirs)
    [ $# -eq 3 ] || uso
    base_dir=$(cd "$2" && pwd) || exit 2
    head_dir=$(cd "$3" && pwd) || exit 2
    referencia=
    ;;
  '' | -*) uso ;;
  *) referencia=$1 ;;
esac

aprobado=${LIV_CONTRACTS_BREAKING_APPROVED:-false}
fallas=0
prepare_workdir "${LIV_CONTRACTS_WORK:-$proyecto/target/contracts-compare}" "${LIV_CONTRACTS_ROOT:-$proyecto}"
cd "${LIV_CONTRACTS_ROOT:-$proyecto}" || exit 2

# Resultado de openapi-diff: incompatible, compatible, equivalent o error. Deja el reporte en $trabajo.
# El resultado sale de las frases del reporte de consola de openapi-diff 2.1.7. Si sube su versión en pom.xml y
# cambian, las pruebas (test/run.sh) fallan.
diff_result() { # diff_result BASE HEAD NOMBRE
  reporte="$trabajo/$3.txt"
  rm -f "$reporte"
  if ! (cd "$proyecto" && sh mvnw -B -ntp -q org.openapitools.openapidiff:openapi-diff-maven:diff \
      -DoldSpec="$(java_path "$1")" -DnewSpec="$(java_path "$2")" -DconsoleOutputFileName="$(java_path "$reporte")") \
      > "$trabajo/$3.log" 2>&1 || [ ! -f "$reporte" ]; then
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

compare_error() { # compare_error NOMBRE
  falla COMPARE_ERROR "$1" "openapi-diff no pudo comparar el contrato. Su salida:"
  sed 's/^/             /' "$trabajo/$1.log"
}

# Un $ref que no empieza por # apunta a otro archivo o a una URL. Se busca en cualquier forma de YAML: en bloque,
# en una lista (- $ref: ...), en flujo ({ $ref: ... }) o con la clave entre comillas. Un $ref con el valor en la
# línea siguiente (con o sin comentario tras los dos puntos), o una clave escrita con escapes ($, \x24,
# \U00000024), también cuentan: el valor va en la misma línea.
has_external_ref() {
  grep -Eq -e "[\"']?\\\$ref[\"']?[[:space:]]*:[[:space:]]*[\"']?[^#\"'[:space:]]" \
    -e "\\\$ref[\"']?[[:space:]]*:[[:space:]]*(#.*)?\$" -e "\\\\(u0024|x24|U00000024)" "$1"
}

# Un cambio incompatible pasa solo con la etiqueta y la versión mayor subida.
judge_breaking() { # judge_breaking NOMBRE VERSION_BASE VERSION_HEAD
  if [ "$aprobado" != true ]; then
    falla INCOMPATIBLE "$1" "cambio incompatible; requiere la etiqueta contrato-incompatible y subir la versión mayor."
    sed 's/^/             /' "$trabajo/$1.txt"
  elif is_semver "$2" && [ "$(major "$3")" -gt "$(major "$2")" ]; then
    ok "$1: cambio incompatible aprobado ($2 → $3)"
  else
    falla MAJOR_NOT_BUMPED "$1" "cambio incompatible aprobado; la versión mayor debe subir de $2 (es $3)."
  fi
}

compare_contract() { # compare_contract NOMBRE
  nombre=$1
  base="$base_dir/$nombre.yaml"
  head="$head_dir/$nombre.yaml"

  if [ ! -f "$head" ]; then
    if [ "$aprobado" = true ]; then
      ok "$nombre: se quitó, con la etiqueta contrato-incompatible"
    else
      falla CONTRACT_REMOVED "$nombre" "se quitó el contrato; requiere la etiqueta contrato-incompatible."
    fi
    return
  fi

  v_head=$(contract_version "$head")
  if ! is_semver "$v_head"; then
    falla INVALID_VERSION "$nombre" "info.version debe ser X.Y.Z y estar una sola vez; es '$v_head'."
    return
  fi
  if has_external_ref "$head"; then
    falla EXTERNAL_REF "$nombre" "los \$ref deben ser internos (#/...); un archivo referido quedaría fuera de la comparación y de la publicación."
    return
  fi

  if [ ! -f "$base" ]; then
    # Comparado consigo mismo, openapi-diff solo comprueba que lo puede leer.
    if [ "$(diff_result "$head" "$head" "$nombre")" = error ]; then
      compare_error "$nombre"
    else
      ok "$nombre: contrato nuevo ($v_head)"
    fi
    return
  fi
  if cmp -s "$base" "$head"; then
    ok "$nombre: sin cambios ($v_head)"
    return
  fi

  v_base=$(contract_version "$base")
  if is_semver "$v_base" && ! semver_gt "$v_head" "$v_base"; then
    falla VERSION_NOT_BUMPED "$nombre" "el contrato cambió y info.version debe subir de $v_base (es $v_head)."
  fi
  case "$(diff_result "$base" "$head" "$nombre")" in
    incompatible) judge_breaking "$nombre" "$v_base" "$v_head" ;;
    compatible | equivalent) ok "$nombre: cambio compatible ($v_base → $v_head)" ;;
    *) compare_error "$nombre" ;;
  esac
}

if [ -n "$referencia" ]; then
  base_dir="$trabajo/base"
  head_dir="$trabajo/head"
  mkdir -p "$base_dir" "$head_dir"
  collect_ref "$referencia" "$base_dir" || exit 1
  collect_worktree "$head_dir" || exit 1
fi

si_no=no
[ "$aprobado" = true ] && si_no=sí
echo "Contratos de $CONTRACT_DIR/ (etiqueta contrato-incompatible: $si_no)"
nombres=$(for archivo in "$base_dir"/*.yaml "$head_dir"/*.yaml; do
  [ -f "$archivo" ] && basename "$archivo" .yaml
done | sort -u)
for nombre in $nombres; do
  compare_contract "$nombre"
done

if ! ls "$head_dir"/*.yaml > /dev/null 2>&1; then
  falla NO_CONTRACTS "$CONTRACT_DIR" "el proyecto no tiene contratos; si se movieron, la compuerta no compararía nada."
fi

if [ "$fallas" -gt 0 ]; then
  echo "$fallas fallas en los contratos."
  exit 1
fi
echo "Los contratos se pueden integrar."
