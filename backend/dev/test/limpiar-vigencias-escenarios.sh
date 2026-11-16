#!/bin/sh
# Pruebas de escenarios de dev/limpiar-vigencias.sh (feature 007, T32, H2-9) con un docker falso (fake-docker.sh),
# sin Docker ni base de datos. Comprueban lo que el script promete:
#   - solo ejecuta «docker compose -f dev/compose.yaml ps -q postgres» y «… exec -T postgres psql …», nada más;
#   - toca únicamente tax.administration_fiscal_year (una sola sentencia que escribe: un DELETE, sin TRUNCATE ni DDL);
#   - se niega, con código distinto de 0 y sin ejecutar el borrado, si el contenedor no es el de desarrollo: sin
#     contenedor, sin dev/.env, o con un Docker remoto; y la base se comprueba (guarda) en la misma sentencia que borra;
#   - no depende de un puerto fijo ni de dónde se ejecute.
# La comprobación contra una base real (la guarda y el borrado) no cabe aquí: la hace quien revisa el PR a mano.
# Uso, desde backend/ y en Windows desde Git Bash:  sh dev/test/limpiar-vigencias-escenarios.sh
# Sale con 0 si todo da lo esperado y con 1 si algo no.
set -u
cd "$(dirname "$0")/../.."

backend=$(pwd)
script="$backend/dev/limpiar-vigencias.sh"
trabajo="$backend/target/limpiar-vigencias-test"
rm -rf "$trabajo"
mkdir -p "$trabajo/bin"
cp "$backend/dev/test/fake-docker.sh" "$trabajo/bin/docker"
chmod +x "$trabajo/bin/docker"
fallas=0

ok()    { printf '  ok         %s\n' "$1"; }
falla() { printf '  FALLA      %s\n' "$1"; fallas=$((fallas + 1)); }
verificar() { # verificar DESCRIPCION CONDICION...
  descripcion=$1
  shift
  if "$@"; then ok "$descripcion"; else falla "$descripcion"; fi
}

# Corre el script con el docker falso y un almacén limpio. Deja en $salida lo que escribió, en $codigo el código de
# salida y en $llamadas / $entorno lo que recibió el docker falso. Las opciones del escenario son los archivos que
# se crean en el almacén (los interruptores de fake-docker.sh).
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
  PATH="$trabajo/bin:$PATH" FAKE_DOCKER_STORE="$almacen" sh "$script" "$@" > "$salida" 2>&1
  codigo=$?
}

contiene() { grep -qF -- "$2" "$1"; }
no_contiene() { ! grep -qF -- "$2" "$1"; }
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
verificar "toda llamada es «docker compose -f dev/compose.yaml …»" \
  test "$(grep -c '^compose -f dev/compose.yaml ' "$llamadas")" -eq 2
verificar "ps busca el contenedor postgres" contiene "$llamadas" 'compose -f dev/compose.yaml ps -q postgres'
verificar "la ejecución es «exec -T postgres psql»" contiene "$llamadas" 'compose -f dev/compose.yaml exec -T postgres psql '
verificar "psql se detiene en el primer error" contiene "$llamadas" 'ON_ERROR_STOP=1'
verificar "no fija ninguna variable de Compose" contiene "$entorno" 'COMPOSE_PROJECT_NAME=sin-definir COMPOSE_FILE=sin-definir'

linea=$(grep ' exec ' "$llamadas")
sql=${linea#* -c }
verificar "pasa una sentencia con -c" test "$sql" != "$linea"
verificar "borra solo de tax.administration_fiscal_year" \
  test "$(printf '%s' "$sql" | grep -oF 'DELETE FROM tax.administration_fiscal_year' | wc -l)" -eq 1
verificar "tiene un solo DELETE" test "$(printf '%s' "$sql" | grep -oiw 'DELETE' | wc -l)" -eq 1
for prohibida in TRUNCATE DROP ALTER INSERT UPDATE CREATE GRANT REVOKE COPY VACUUM; do
  verificar "no usa $prohibida" test "$(printf '%s' "$sql" | grep -ciw "$prohibida")" -eq 0
done
tablas=$(printf '%s' "$sql" | grep -oE '[a-z_]+\.[a-z_]+' | sort -u | tr '\n' ' ')
verificar "solo nombra tax.administration_fiscal_year y core.tax_administration (la guarda): $tablas" \
  test "$tablas" = 'core.tax_administration tax.administration_fiscal_year '
verificar "la guarda va antes del borrado, en la misma sentencia" \
  test "$(printf '%s' "${sql%%DELETE FROM*}" | grep -c 'RAISE EXCEPTION')" -ge 1
verificar "la guarda comprueba la base liv" contiene "$llamadas" "current_database()"
verificar "la guarda comprueba la administración de prueba B de la semilla" \
  contiene "$llamadas" '10000000-0000-0000-0000-0000000b0001'

echo "Se niega:"
escenario sin-contenedor sin-contenedor
verificar "sin contenedor de desarrollo, sale distinto de 0" test "$codigo" -ne 0
verificar "sin contenedor, no ejecuta nada" test "$(ejecuciones)" -eq 0
verificar "sin contenedor, explica cómo levantarlo" contiene "$salida" 'no está levantado'

escenario ps-falla ps-falla
verificar "sin dev/.env (ps falla), sale distinto de 0" test "$codigo" -ne 0
verificar "sin dev/.env, no ejecuta nada" test "$(ejecuciones)" -eq 0
verificar "sin dev/.env, lo dice" contiene "$salida" 'dev/.env'

escenario base-ajena psql-niega
verificar "si la guarda de la base falla, sale distinto de 0" test "$codigo" -ne 0
verificar "si la guarda de la base falla, deja el mensaje de la base" contiene "$salida" 'No es la base de desarrollo'
verificar "si la guarda de la base falla, no hace más llamadas" test "$(numero_de_llamadas)" -eq 2

escenario docker-remoto --
verificar "(control) sin DOCKER_HOST el escenario pasa" test "$codigo" -eq 0
almacen="$trabajo/docker-remoto-tcp"
mkdir -p "$almacen"
: > "$almacen/llamadas"
: > "$almacen/entorno"
DOCKER_HOST=tcp://10.0.0.5:2375 PATH="$trabajo/bin:$PATH" FAKE_DOCKER_STORE="$almacen" sh "$script" > "$almacen/salida" 2>&1
verificar "con un Docker remoto (DOCKER_HOST=tcp://…), sale distinto de 0" test "$?" -ne 0
verificar "con un Docker remoto, no llama a docker" test "$(grep -c . "$almacen/llamadas")" -eq 0
verificar "con un Docker remoto, lo dice" contiene "$almacen/salida" 'DOCKER_HOST'

escenario con-argumentos -- gestion.vigencias
verificar "con argumentos, sale distinto de 0" test "$codigo" -ne 0
verificar "con argumentos, no llama a docker" test "$(numero_de_llamadas)" -eq 0

echo "Robustez:"
almacen="$trabajo/otro-directorio"
mkdir -p "$almacen"
: > "$almacen/llamadas"
: > "$almacen/entorno"
(cd / && COMPOSE_PROJECT_NAME=otro COMPOSE_FILE=otro.yaml PATH="$trabajo/bin:$PATH" FAKE_DOCKER_STORE="$almacen" \
  sh "$script" > "$almacen/salida" 2>&1)
verificar "desde otro directorio y con COMPOSE_* en el entorno, funciona" test "$?" -eq 0
verificar "ignora COMPOSE_PROJECT_NAME y COMPOSE_FILE del entorno" \
  test "$(grep -c 'COMPOSE_PROJECT_NAME=sin-definir COMPOSE_FILE=sin-definir' "$almacen/entorno")" -eq 2
verificar "sin puerto fijo: el script no nombra puertos ni direcciones" \
  test "$(grep -cE '543[0-9]|localhost|127\.0\.0\.1|POSTGRES_PORT| -h | -p ' "$script")" -eq 0
verificar "el script no lee ni imprime contraseñas" test "$(grep -ciE 'password|PGPASSWORD' "$script")" -eq 0

if [ "$fallas" -eq 0 ]; then
  echo "Todo lo esperado."
  exit 0
fi
echo "$fallas comprobaciones fallaron."
exit 1
