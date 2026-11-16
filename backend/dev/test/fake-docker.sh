#!/bin/sh
# docker falso para probar limpiar-vigencias.sh sin Docker (lo pone primero en el PATH limpiar-vigencias-escenarios.sh,
# con el nombre docker). Imita solo lo que usa el script: «docker compose -f dev/compose.yaml -p liv-backend-dev ps -q postgres» y
# «docker compose -f dev/compose.yaml -p liv-backend-dev exec -T postgres psql …». Cualquier otra orden la rechaza con el código 99 y
# queda anotada, para que la prueba falle. Guarda en $FAKE_DOCKER_STORE:
#   llamadas   cada llamada recibida, una por línea (los saltos de línea de los argumentos, como espacios)
#   entorno    el valor de COMPOSE_PROJECT_NAME y de COMPOSE_FILE en cada llamada
# Interruptores (archivos vacíos en $FAKE_DOCKER_STORE) para simular fallos:
#   sin-contenedor   «ps -q» no encuentra el contenedor de PostgreSQL (el entorno no está levantado)
#   ps-falla         «ps» falla, como cuando falta dev/.env
#   psql-niega       psql falla, como cuando la base no es la de desarrollo (la guarda del script la rechaza)
set -u
almacen=$FAKE_DOCKER_STORE
printf '%s\n' "$*" | tr '\n' ' ' | sed 's/ $//' >> "$almacen/llamadas"
echo >> "$almacen/llamadas"
printf 'COMPOSE_PROJECT_NAME=%s COMPOSE_FILE=%s\n' "${COMPOSE_PROJECT_NAME-sin-definir}" "${COMPOSE_FILE-sin-definir}" >> "$almacen/entorno"

rechazar() { echo "fake-docker: $1" >&2; exit 99; }

[ "${1:-}" = compose ] || rechazar "orden no imitada: $*"
[ "${2:-}" = -f ] && [ "${3:-}" = dev/compose.yaml ] || rechazar "falta «-f dev/compose.yaml»: $*"
[ "${4:-}" = -p ] && [ "${5:-}" = liv-backend-dev ] || rechazar "falta «-p liv-backend-dev»: $*"
subcomando=${6:-}
shift 6

case "$subcomando" in
  ps)
    [ "$*" = '-q postgres' ] || rechazar "ps no imitado: $*"
    if [ -e "$almacen/ps-falla" ]; then
      echo 'required variable POSTGRES_PASSWORD is missing a value: Copia dev/.env.example a dev/.env' >&2
      exit 1
    fi
    [ -e "$almacen/sin-contenedor" ] || echo 3f9a1c2b7d4e
    ;;
  exec)
    [ "${1:-} ${2:-} ${3:-}" = '-T postgres psql' ] || rechazar "exec no imitado (¿falta -T?): $*"
    if [ -e "$almacen/psql-niega" ]; then
      echo 'ERROR:  No es la base de desarrollo de LIV: no se borra nada' >&2
      exit 1
    fi
    echo 'DELETE 2'
    ;;
  *)
    rechazar "subcomando no imitado: $subcomando"
    ;;
esac
