#!/bin/sh
# gh falso para probar publish.sh sin GitHub (lo carga run.sh con LIV_GH). Imita solo lo que usa publish.sh, con
# los mismos mensajes de error que gh 2.x, y guarda las releases en carpetas de $FAKE_GH_STORE:
#   releases/<etiqueta>/estado   draft o published
#   releases/<etiqueta>/assets/  los archivos de la release
#   tags/<etiqueta>              la etiqueta existe en el repositorio
#   view-falla                   si existe, «release view» falla como un error de red
#   llamadas                     cada llamada recibida, una por línea
set -u
almacen=$FAKE_GH_STORE
printf '%s\n' "$*" >> "$almacen/llamadas"

# Archivos de la línea de órdenes: los argumentos que siguen a la etiqueta, hasta la primera opción.
archivos() { for arg in "$@"; do case "$arg" in -*) return ;; *) printf '%s\n' "$arg" ;; esac; done; }

case "$1 ${2:-}" in
  'release view')
    if [ -e "$almacen/view-falla" ]; then echo 'HTTP 502: Bad Gateway' >&2; exit 1; fi
    [ -d "$almacen/releases/$3" ] || { echo 'release not found' >&2; exit 1; }
    # publish.sh pide --json isDraft --jq .isDraft.
    if [ "$(cat "$almacen/releases/$3/estado")" = draft ]; then echo true; else echo false; fi
    ;;
  'release create')
    etiqueta=$3
    shift 3
    case " $* " in *' --draft '*) ;; *) echo 'fake-gh: publish.sh debe crear la release como borrador' >&2; exit 1 ;; esac
    mkdir -p "$almacen/releases/$etiqueta/assets"
    echo draft > "$almacen/releases/$etiqueta/estado"
    archivos "$@" | while read -r archivo; do cp "$archivo" "$almacen/releases/$etiqueta/assets/"; done
    ;;
  'release upload')
    etiqueta=$3
    shift 3
    archivos "$@" | while read -r archivo; do cp "$archivo" "$almacen/releases/$etiqueta/assets/"; done
    ;;
  'release download')
    etiqueta=$3
    shift 3
    while [ $# -gt 0 ]; do
      case "$1" in --pattern) patron=$2; shift ;; --dir) destino=$2; shift ;; esac
      shift
    done
    [ -f "$almacen/releases/$etiqueta/assets/$patron" ] || { echo 'no assets to download' >&2; exit 1; }
    cp "$almacen/releases/$etiqueta/assets/$patron" "$destino/"
    ;;
  'release edit')
    case " $* " in
      *' --draft=false '*)
        echo published > "$almacen/releases/$3/estado"
        mkdir -p "$almacen/tags"
        touch "$almacen/tags/$3"
        ;;
    esac
    ;;
  api\ *)
    etiqueta=${2##*/}
    [ -e "$almacen/tags/$etiqueta" ] || { echo 'gh: Not Found (HTTP 404)' >&2; exit 1; }
    echo '{}'
    ;;
  *)
    echo "fake-gh: orden no imitada: $*" >&2
    exit 1
    ;;
esac
