#!/bin/sh
# Pruebas de escenarios de dev/limpiar-vigencias.sh (feature 007, T32, H2-9) con un docker falso (fake-docker.sh),
# sin Docker ni base de datos. Comprueban lo que el script promete:
#   - solo ejecuta «docker compose -f dev/compose.yaml -p liv-backend-dev ps -q postgres» y «… exec -T postgres psql …», nada más;
#   - toca únicamente tax.administration_fiscal_year: una sola sentencia que escribe, un DELETE sin condición, sin
#     TRUNCATE ni DDL, y sin BEGIN/COMMIT propios que partan la transacción;
#   - se niega, con código distinto de 0 y sin ejecutar el borrado, si el contenedor no es el de desarrollo: sin
#     contenedor, sin Docker o sin dev/.env, con un Docker remoto o con argumentos; y la base se comprueba (guarda) en
#     la misma sentencia que borra;
#   - no depende de un puerto fijo ni de dónde se ejecute.
# Lo que NO cabe aquí: el docker falso no evalúa el SQL. Que la guarda se evalúe bien y que borre solo con la
# administración de prueba B se comprueba a mano contra un PostgreSQL desechable (la descripción del PR da el resultado).
# Uso, desde backend/ y en Windows desde Git Bash:  sh dev/test/limpiar-vigencias-escenarios.sh
# Sale con 0 si todo da lo esperado y con 1 si algo no.
set -u
cd "$(dirname "$0")/../.." || exit 1

backend=$(pwd)
script="$backend/dev/limpiar-vigencias.sh"
trabajo="$backend/target/limpiar-vigencias-test"
rm -rf "$trabajo"
mkdir -p "$trabajo/bin"
cp "$backend/dev/test/fake-docker.sh" "$trabajo/bin/docker"
chmod +x "$trabajo/bin/docker"
fallas=0

# Un docker de verdad nunca debe llegar a correr: si el falso no es el primero del PATH, la prueba no sigue. Y, por si
# acaso, el entorno de la prueba apunta a un socket que no existe (el script lo acepta como local).
PATH="$trabajo/bin:$PATH"
[ "$(command -v docker)" = "$trabajo/bin/docker" ] || { echo "El docker falso no es el primero del PATH: no sigo." >&2; exit 1; }
unset DOCKER_CONTEXT COMPOSE_PROJECT_NAME COMPOSE_FILE
DOCKER_HOST=unix:///liv-prueba-sin-docker.sock
export DOCKER_HOST

ok()    { printf '  ok         %s\n' "$1"; }
falla() { printf '  FALLA      %s\n' "$1"; fallas=$((fallas + 1)); }
verificar() { # verificar DESCRIPCION CONDICION...
  descripcion=$1
  shift
  if "$@"; then ok "$descripcion"; else falla "$descripcion"; fi
}

# Corre el script con el docker falso en un almacén limpio. Antes de llamarla, un escenario puede poner en
# $variables_extra variables de entorno para esa corrida (VARIABLE=valor, separadas por espacios) y en $directorio el
# directorio desde el que se ejecuta; la función los borra. Deja en $salida lo que escribió el script, en $codigo su
# código de salida y en $llamadas / $entorno lo que recibió el docker falso. Los interruptores del escenario son los
# archivos que se crean en el almacén (los de fake-docker.sh).
variables_extra=
directorio=
escenario() { # escenario NOMBRE [INTERRUPTOR...] -- [ARGUMENTO...]
  nombre=$1
  shift
  almacen="$trabajo/$nombre"
  mkdir -p "$almacen"
  while [ $# -gt 0 ] && [ "$1" != -- ]; do : > "$almacen/$1"; shift; done
  [ $# -gt 0 ] && shift
  salida="$almacen/salida"
  llamadas="$almacen/llamadas"
  entorno="$almacen/entorno"
  : > "$llamadas"
  : > "$entorno"
  # shellcheck disable=SC2086  # las variables extra se separan a propósito
  (cd "${directorio:-$backend}" && env $variables_extra FAKE_DOCKER_STORE="$almacen" sh "$script" "$@") > "$salida" 2>&1
  codigo=$?
  variables_extra=
  directorio=
}

contiene() { grep -qF -- "$2" "$1"; }
numero_de_llamadas() { grep -c . "$llamadas"; }
ejecuciones() { grep -c ' exec ' "$llamadas"; }

echo "Limpiar las vigencias:"
verificar "el script existe" test -f "$script"

echo "Camino normal:"
escenario normal
verificar "sale con 0" test "$codigo" -eq 0
verificar "dice cuántas vigencias borró" contiene "$salida" 'DELETE 2'
verificar "solo hace dos llamadas: ps y exec" test "$(numero_de_llamadas)" -eq 2
verificar "una sola ejecución" test "$(ejecuciones)" -eq 1
verificar "toda llamada es «docker compose -f dev/compose.yaml -p liv-backend-dev …»" \
  test "$(grep -c '^compose -f dev/compose.yaml -p liv-backend-dev ' "$llamadas")" -eq 2
verificar "ps busca el contenedor postgres" contiene "$llamadas" 'compose -f dev/compose.yaml -p liv-backend-dev ps -q postgres'
verificar "la ejecución es «exec -T postgres psql -U postgres -d liv»" \
  contiene "$llamadas" 'compose -f dev/compose.yaml -p liv-backend-dev exec -T postgres psql -U postgres -d liv '
verificar "psql se detiene en el primer error" contiene "$llamadas" 'ON_ERROR_STOP=1'
verificar "no fija ninguna variable de Compose" contiene "$entorno" 'COMPOSE_PROJECT_NAME=sin-definir COMPOSE_FILE=sin-definir'
verificar "no se conecta por puerto ni por dirección" \
  test "$(sed 's/ -p liv-backend-dev / /' "$llamadas" | grep -cE ' (-h|-p|--host|--port)( |$)|PGPORT|PGHOST')" -eq 0

linea=$(grep ' exec ' "$llamadas")
sql=$(printf '%s' "${linea#* -c }" | tr -s ' ')
verificar "pasa una sola sentencia con -c" test "$sql" != "$linea"
verificar "termina en el borrado, sin condición (DELETE FROM tax.administration_fiscal_year;)" \
  test "${sql%DELETE FROM tax.administration_fiscal_year;}" != "$sql"
verificar "tiene un solo DELETE" test "$(printf '%s' "$sql" | grep -oiw 'DELETE' | wc -l)" -eq 1
for prohibida in TRUNCATE DROP ALTER INSERT UPDATE CREATE GRANT REVOKE COPY VACUUM COMMIT ROLLBACK; do
  verificar "no usa $prohibida" test "$(printf '%s' "$sql" | grep -ciw "$prohibida")" -eq 0
done
verificar "el único BEGIN es el del bloque de la guarda (no parte la transacción)" \
  test "$(printf '%s' "$sql" | grep -oiw 'BEGIN' | wc -l)" -eq 1
tablas=$(printf '%s' "$sql" | grep -oE '[a-z_]+\.[a-z_]+' | sort -u | tr '\n' ' ')
verificar "solo nombra tax.administration_fiscal_year y core.tax_administration (la guarda): $tablas" \
  test "$tablas" = 'core.tax_administration tax.administration_fiscal_year '
verificar "la guarda de la base: IF current_database() <> 'liv' THEN RAISE EXCEPTION" \
  test "${sql#*"IF current_database() <> 'liv' THEN RAISE EXCEPTION"}" != "$sql"
verificar "la guarda de la semilla: IF NOT EXISTS (… administración de prueba B) THEN RAISE EXCEPTION" \
  test "${sql#*"IF NOT EXISTS (SELECT 1 FROM core.tax_administration WHERE id = '10000000-0000-0000-0000-0000000b0001') THEN RAISE EXCEPTION"}" != "$sql"
verificar "la guarda va antes del borrado, en la misma sentencia" \
  test "$(printf '%s' "${sql%%DELETE FROM*}" | grep -c 'END IF; END')" -ge 1
verificar "el borrado espera como mucho 5 s por un bloqueo" contiene "$llamadas" "SET LOCAL lock_timeout = '5s';"

echo "Se niega:"
escenario sin-contenedor sin-contenedor
verificar "sin contenedor de desarrollo, sale distinto de 0" test "$codigo" -ne 0
verificar "sin contenedor, no ejecuta nada" test "$(ejecuciones)" -eq 0
verificar "sin contenedor, explica cómo levantarlo" contiene "$salida" 'no está levantado'

escenario ps-falla ps-falla
verificar "sin Docker o sin dev/.env (ps falla), sale distinto de 0" test "$codigo" -ne 0
verificar "sin Docker o sin dev/.env, no ejecuta nada" test "$(ejecuciones)" -eq 0
verificar "sin Docker o sin dev/.env, lo dice" contiene "$salida" 'dev/.env'

escenario base-ajena psql-niega
verificar "si la guarda de la base falla, sale distinto de 0" test "$codigo" -ne 0
verificar "si la guarda de la base falla, deja el mensaje de la base" contiene "$salida" 'No es la base de desarrollo'
verificar "si la guarda de la base falla, no hace más llamadas" test "$(numero_de_llamadas)" -eq 2

for remoto in tcp://10.0.0.5:2375 ssh://admin@servidor npipe:////servidor/pipe/docker_engine; do
  variables_extra="DOCKER_HOST=$remoto"
  escenario "remoto-$(printf '%s' "$remoto" | tr -c 'a-z0-9' '-')"
  verificar "con DOCKER_HOST=$remoto, sale distinto de 0" test "$codigo" -ne 0
  verificar "con DOCKER_HOST=$remoto, no llama a docker" test "$(numero_de_llamadas)" -eq 0
  verificar "con DOCKER_HOST=$remoto, lo dice" contiene "$salida" 'DOCKER_HOST'
done
for propio in unix:///var/run/docker.sock npipe:////./pipe/docker_engine ''; do
  variables_extra="DOCKER_HOST=$propio"
  escenario "propio-$(printf '%s' "${propio:-vacio}" | tr -c 'a-z0-9' '-')"
  verificar "con DOCKER_HOST='$propio' (el de este equipo), funciona" test "$codigo" -eq 0
done

variables_extra=DOCKER_CONTEXT=remoto
escenario contexto-remoto
verificar "con DOCKER_CONTEXT definida, sale distinto de 0" test "$codigo" -ne 0
verificar "con DOCKER_CONTEXT definida, no llama a docker" test "$(numero_de_llamadas)" -eq 0
verificar "con DOCKER_CONTEXT definida, lo dice" contiene "$salida" 'DOCKER_CONTEXT'

escenario con-argumentos -- gestion.vigencias
verificar "con argumentos, sale distinto de 0" test "$codigo" -ne 0
verificar "con argumentos, no llama a docker" test "$(numero_de_llamadas)" -eq 0

echo "Robustez:"
variables_extra='COMPOSE_PROJECT_NAME=otro COMPOSE_FILE=otro.yaml'
directorio=/
escenario otro-directorio
verificar "desde otro directorio y con COMPOSE_* en el entorno, funciona" test "$codigo" -eq 0
verificar "ignora COMPOSE_PROJECT_NAME y COMPOSE_FILE del entorno" \
  test "$(grep -c 'COMPOSE_PROJECT_NAME=sin-definir COMPOSE_FILE=sin-definir' "$entorno")" -eq 2
verificar "sin puerto fijo: el script no nombra puertos ni direcciones" \
  test "$(grep -v '^ *#' "$script" | sed 's/ -p liv-backend-dev / /' | grep -cE '543[0-9]|localhost|127\.0\.0\.1|POSTGRES_PORT|PGPORT|PGHOST| -h | -p |--port|--host')" -eq 0
verificar "el script no lee ni imprime contraseñas" \
  test "$(grep -v '^ *#' "$script" | grep -ciE 'password|PGPASSWORD')" -eq 0

if [ "$fallas" -eq 0 ]; then
  echo "Todo lo esperado."
  exit 0
fi
echo "$fallas comprobaciones fallaron."
exit 1
