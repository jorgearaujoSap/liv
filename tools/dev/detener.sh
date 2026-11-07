#!/bin/sh
# Detiene lo que levantó tools/dev/iniciar.sh: el frontend, el BFF, la API y el entorno local. Nunca toca otro
# programa, aunque use el mismo puerto, ni un entorno que se levantó a mano.
#
#   sh tools/dev/detener.sh                      # conserva los datos de PostgreSQL y Keycloak
#   sh tools/dev/detener.sh --borrar-datos       # además borra los volúmenes (pide confirmación)
#   sh tools/dev/detener.sh --borrar-datos --si  # lo mismo, sin preguntar
set -eu

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
REGISTROS="$RAIZ/tools/dev/registros"
BORRAR=no
SIN_PREGUNTAR=no
for argumento in "$@"; do
  case "$argumento" in
    --borrar-datos) BORRAR=si ;;
    --si) SIN_PREGUNTAR=si ;;
    *) printf 'Opción desconocida: %s\n' "$argumento" >&2; exit 2 ;;
  esac
done

. "$RAIZ/tools/dev/comun.sh"
requiere_herramientas

paso "Frontend, BFF y API"
detener_servicio 4200 frontend
detener_servicio 8081 BFF
detener_servicio 8080 API

paso "Entorno local"
if [ ! -f "$REGISTROS/entorno.lanzado" ]; then
  printf '   No lo levantó iniciar.sh, no se toca.\n'
elif [ "$BORRAR" = si ]; then
  if [ "$SIN_PREGUNTAR" = no ]; then
    if [ ! -t 0 ]; then
      falla "Para borrar los datos sin una terminal, agrega --si."
    fi
    printf '   Se borrarán los datos de PostgreSQL y Keycloak: la próxima vez todo empieza de cero. ¿Seguir? [s/N] '
    read -r respuesta
    case "$respuesta" in
      s | S | si | Si | SI | sí | Sí) ;;
      *) printf '   No se borró nada.\n'; exit 0 ;;
    esac
  fi
  (cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env down -v)
  rm -f "$REGISTROS/entorno.lanzado"
  printf '   Datos borrados.\n'
else
  (cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env down)
  rm -f "$REGISTROS/entorno.lanzado"
  printf '   Datos conservados.\n'
fi
printf '\n✓ LIV detenido.\n'
