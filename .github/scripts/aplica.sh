#!/usr/bin/env bash
# Decide si la verificación de un proyecto corre en un PR (feature 003, plan, pasos 2 y 3).
#
# Supone el checkout de un PR (refs/pull/N/merge): HEAD es el commit de merge y HEAD^1 la punta de la rama base, así que
# `git diff HEAD^1 HEAD` es el diff completo del PR. Con otro HEAD (un push, un commit suelto) vería solo parte.
# Uso, desde la raíz de un checkout del merge del PR con fetch-depth 2:
#   bash aplica.sh <backend|bff|frontend|ui|contratos>
# Escribe en la salida estándar exactamente una línea, `aplica=true` o `aplica=false`, lista para `>> "$GITHUB_OUTPUT"`.
#
# La regla es una lista de lo que se sabe que NO afecta, no de lo que afecta. La verificación de un proyecto solo se
# omite (`aplica=false`) si todos los archivos del PR están en otro proyecto o son neutros (documentación y archivos
# que ninguna verificación lee). Un archivo desconocido, de .github/ o de la raíz hace aplicar a todos. Ante cualquier
# error, o si no hay archivos que mirar, escribe `aplica=true`: ante la duda no se omite nada.
#
# Los nombres de archivo son datos: se leen separados por NUL, se comparan con `case` y nunca se ejecutan, se
# interpolan en una orden ni se escriben (un nombre como `::error::` se interpretaría como orden del workflow).
#
# Una carpeta o un archivo nuevo en la raíz hace correr todo hasta que se declare neutro aquí, con su caso en
# .github/scripts/test/aplica.test.sh.
set -euo pipefail

# Deja en $clase la clase de una ruta: el proyecto al que pertenece, `neutro` o `otro` (desconocida o de la raíz,
# que afecta a todos).
clasifica() {
  case "$1" in
    # Un archivo oculto directamente en backend/ (.gitattributes, .gitignore) también configura al BFF que
    # vive dentro: aplica a todos (también frontend y ui, por simplicidad). Los de las subcarpetas (.mvn/) son del backend.
    backend/.*) if [[ "${1#backend/}" == */* ]]; then clase=backend; else clase=otro; fi ;;
    # El BFF vive dentro de backend/ pero es otro proyecto Maven: va antes que backend/*.
    backend/bff/*) clase=bff ;;
    backend/*) clase=backend ;;
    frontend/*) clase=frontend ;;
    ui/*) clase=ui ;;
    docs/* | spec/* | e2e/* | tools/* | .claude/* | .gitignore | .mcp.json) clase=neutro ;;
    # En `case`, `*` también acepta `/`: primero se descarta todo lo que está en una carpeta, y solo después los
    # `.md` de la raíz. Así `.github/x.md` y `nuevo/README.md` no son neutros.
    */*) clase=otro ;;
    *.md) clase=neutro ;;
    *) clase=otro ;;
  esac
}

# ¿Es un archivo que cambia los contratos o la herramienta con la que se comparan? El comparador corre con el
# Maven del proyecto, así que su pom.xml y su wrapper también cuentan.
es_contrato() {
  case "$1" in
    backend/contracts/* | backend/bff/contracts/*) return 0 ;;
    backend/pom.xml | backend/bff/pom.xml) return 0 ;;
    backend/mvnw* | backend/.mvn/* | backend/bff/mvnw* | backend/bff/.mvn/*) return 0 ;;
    *) return 1 ;;
  esac
}

# Deja true o false en $resultado. Cualquier fallo devuelve un código distinto de 0.
# Se llama dentro de un `if`, que desactiva `set -e`: cada paso comprueba su propio error.
decidir() {
  [ "$#" -eq 1 ] || return 1
  local proyecto=$1 archivo hay=0 clase=
  case "$proyecto" in
    backend | bff | frontend | ui | contratos) ;;
    *) return 1 ;;
  esac

  lista=$(mktemp) || return 1
  trap 'rm -f "${lista:-}"' EXIT
  # NUL como separador: sirve para cualquier nombre. --no-renames muestra un archivo movido con su ruta anterior y
  # con la nueva, y core.quotePath=false evita que Git escriba entre comillas los nombres con tildes.
  git -c core.quotePath=false diff -z --name-only --no-renames HEAD^1 HEAD -- > "$lista" 2> /dev/null || return 1

  resultado=false
  while IFS= read -r -d '' archivo || [ -n "$archivo" ]; do
    hay=1
    clasifica "$archivo"
    if [ "$clase" = otro ]; then
      resultado=true
      break
    fi
    if [ "$proyecto" = contratos ]; then
      if es_contrato "$archivo"; then
        resultado=true
        break
      fi
    elif [ "$clase" = "$proyecto" ]; then
      resultado=true
      break
    fi
  done < "$lista"

  # Una lista vacía no prueba que nada cambió.
  [ "$hay" -eq 1 ] || resultado=true
  return 0
}

resultado=true
lista=
if ! decidir "$@" 2> /dev/null; then
  echo "aplica.sh: no se pudo decidir; se asume que aplica." >&2
  resultado=true
fi
printf 'aplica=%s\n' "$resultado"
