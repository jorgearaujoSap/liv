#!/bin/sh
# gh falso para probar publish.sh sin GitHub (lo carga run.sh con LIV_GH). Imita solo lo que usa publish.sh, con
# los mismos mensajes de error que gh 2.x, y exige las opciones de las que depende la publicación: si publish.sh
# deja de pasar --draft, --clobber, --latest=false, --target o --notes, la prueba falla. Guarda las releases en
# carpetas de $FAKE_GH_STORE:
#   releases/<etiqueta>/estado   draft o published
#   releases/<etiqueta>/assets/  los archivos de la release
#   tags/<etiqueta>              la etiqueta existe en el repositorio (como en GitHub, solo la de una release
#                                publicada o una creada a mano)
#   llamadas                     cada llamada recibida, una por línea
# Interruptores (archivos vacíos en $FAKE_GH_STORE) para simular fallos:
#   view-falla       «release view» falla como un error de red
#   api-falla        «api» falla con un error 502
#   upload-falla     «release upload» falla
#   download-altera  «release download» entrega un archivo alterado
set -u
almacen=$FAKE_GH_STORE
printf '%s\n' "$*" >> "$almacen/llamadas"

rechazar() { echo "fake-gh: $1" >&2; exit 1; }
exigir() { # exigir OPCION ARGUMENTOS...
  opcion=$1
  shift
  case " $* " in *" $opcion "*) ;; *) rechazar "falta $opcion en: $*" ;; esac
}
# Archivos de la línea de órdenes: los argumentos que siguen a la etiqueta, hasta la primera opción.
archivos() { for arg in "$@"; do case "$arg" in -*) return ;; *) printf '%s\n' "$arg" ;; esac; done; }

case "$1 ${2:-}" in
  'release view')
    exigir --json "$@"
    exigir isDraft "$@"
    if [ -e "$almacen/view-falla" ]; then echo 'HTTP 502: Bad Gateway' >&2; exit 1; fi
    [ -d "$almacen/releases/$3" ] || { echo 'release not found' >&2; exit 1; }
    if [ "$(cat "$almacen/releases/$3/estado")" = draft ]; then echo true; else echo false; fi
    ;;
  'release create')
    etiqueta=$3
    shift 3
    exigir --draft "$@"
    exigir --latest=false "$@"
    exigir --target "$@"
    [ -d "$almacen/releases/$etiqueta" ] && rechazar "la release $etiqueta ya existe"
    mkdir -p "$almacen/releases/$etiqueta/assets"
    echo draft > "$almacen/releases/$etiqueta/estado"
    archivos "$@" | while read -r archivo; do cp "$archivo" "$almacen/releases/$etiqueta/assets/"; done
    ;;
  'release upload')
    etiqueta=$3
    shift 3
    if [ -e "$almacen/upload-falla" ]; then echo 'HTTP 502: Bad Gateway' >&2; exit 1; fi
    for archivo in $(archivos "$@"); do
      if [ -e "$almacen/releases/$etiqueta/assets/$(basename "$archivo")" ]; then
        case " $* " in *' --clobber '*) ;; *) echo 'asset under the same name already exists' >&2; exit 1 ;; esac
      fi
      cp "$archivo" "$almacen/releases/$etiqueta/assets/"
    done
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
    [ -e "$almacen/download-altera" ] && echo alterado >> "$destino/$patron"
    exit 0
    ;;
  'release edit')
    etiqueta=$3
    exigir --draft=false "$@"
    exigir --latest=false "$@"
    exigir --target "$@"
    exigir --notes "$@"
    echo published > "$almacen/releases/$etiqueta/estado"
    mkdir -p "$almacen/tags"
    touch "$almacen/tags/$etiqueta"
    ;;
  api\ *)
    if [ -e "$almacen/api-falla" ]; then echo 'gh: Bad Gateway (HTTP 502)' >&2; exit 1; fi
    case "$2" in
      */git/matching-refs/tags/*)
        # Las etiquetas que empiezan por el prefijo, como las da --jq '.[].ref'. Sin --paginate, el gh real solo
        # devuelve la primera página.
        exigir --paginate "$@"
        prefijo=${2##*/git/matching-refs/tags/}
        for etiqueta in "$almacen"/tags/"$prefijo"*; do
          [ -e "$etiqueta" ] && printf 'refs/tags/%s\n' "$(basename "$etiqueta")"
        done
        exit 0
        ;;
      */git/ref/tags/*)
        etiqueta=${2##*/}
        [ -e "$almacen/tags/$etiqueta" ] || { echo 'gh: Not Found (HTTP 404)' >&2; exit 1; }
        echo '{}'
        ;;
      *) rechazar "ruta de la API no imitada: $2" ;;
    esac
    ;;
  *)
    rechazar "orden no imitada: $*"
    ;;
esac
