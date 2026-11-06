#!/usr/bin/env bash
# Detiene lo que levantó tools/dev/iniciar.sh: el frontend, el BFF, la API y el entorno local.
#
#   sh tools/dev/detener.sh                 # conserva los datos de PostgreSQL y Keycloak
#   sh tools/dev/detener.sh --borrar-datos  # además borra los volúmenes: la próxima vez todo empieza de cero
#
# Solo termina los procesos que iniciar.sh dejó anotados en tools/dev/registros/ y que siguen escuchando en su
# puerto: nunca otro programa que use el mismo puerto.
set -eu

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
REGISTROS="$RAIZ/tools/dev/registros"
BORRAR=no
for argumento in "$@"; do
  case "$argumento" in
    --borrar-datos) BORRAR=si ;;
    *) printf 'Opción desconocida: %s\n' "$argumento" >&2; exit 2 ;;
  esac
done

. "$RAIZ/tools/dev/comun.sh"

paso "Frontend, BFF y API"
for servicio in "4200 frontend" "8081 BFF" "8080 API"; do
  puerto=${servicio%% *}
  nombre=${servicio#* }
  anotado="$REGISTROS/$puerto.pid"
  if [ ! -f "$anotado" ]; then
    printf '   %s: no lo levantó iniciar.sh, no se toca.\n' "$nombre"
    continue
  fi
  pid=$(cat "$anotado")
  if [ "$(pid_en_puerto "$puerto" || true)" = "$pid" ]; then
    terminar "$pid"
    printf '   %s detenido.\n' "$nombre"
  else
    printf '   %s ya no estaba corriendo.\n' "$nombre"
  fi
  rm -f "$anotado"
done

paso "Entorno local"
if [ "$BORRAR" = si ]; then
  (cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env down -v)
  printf '   Datos borrados: la próxima vez todo empieza de cero.\n'
else
  (cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env down)
  printf '   Datos conservados.\n'
fi
printf '\n✓ LIV detenido.\n'
