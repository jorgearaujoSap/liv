#!/bin/sh
# Vacía las vigencias fiscales (tax.administration_fiscal_year) de la base LOCAL de desarrollo, para repetir las
# pruebas de e2e/ (feature 007, H2-9, T32): una vigencia creada no se borra por la API ni por liv_app.
# Uso, con el compose de dev/ levantado y desde Git Bash en Windows:  sh dev/limpiar-vigencias.sh   (desde backend/)
# Sale con 0 si borró (o no había nada que borrar) y con 1 si se negó o falló.
#
# Solo ejecuta «docker compose -f dev/compose.yaml -p liv-backend-dev ps -q postgres» y «… exec -T postgres psql …»
# (el proyecto es el name: de dev/compose.yaml, fijado aquí para que no lo cambie un COMPOSE_PROJECT_NAME de dev/.env), por el socket del
# contenedor: no usa puertos, direcciones ni contraseñas, así que no depende del POSTGRES_PORT de dev/.env. Toca
# únicamente tax.administration_fiscal_year. Se niega, sin borrar nada, si:
#   - recibe argumentos;
#   - DOCKER_HOST o DOCKER_CONTEXT apuntan a otro equipo;
#   - el contenedor de PostgreSQL del compose de dev/ no está levantado (o Docker no responde, o falta dev/.env);
#   - la base no es la de desarrollo: la guarda va en la misma sentencia que el borrado, así que si falla no se borra
#     nada. La base de desarrollo es la que tiene la administración de prueba B de dev/seed (UUID fijo).
# Límites, para quien lo use: no detecta un contexto de Docker remoto fijado con «docker context use» (solo el de la
# variable DOCKER_CONTEXT), ni un COMPOSE_ENV_FILES; en ese caso la única defensa es la guarda
# de la base, que reconoce cualquier base con la semilla de desarrollo. dev/seed y el perfil dev son solo del equipo
# local: no los uses en ningún otro entorno.
set -u
cd "$(dirname "$0")/.." || exit 1

# Las variables de Compose del entorno cambiarían el proyecto o el archivo al que apunta -f.
unset COMPOSE_PROJECT_NAME COMPOSE_FILE

rechaza() { echo "limpiar-vigencias: $1" >&2; exit 1; }

[ $# -eq 0 ] || rechaza "no recibe argumentos."
[ -z "${DOCKER_CONTEXT:-}" ] \
  || rechaza "DOCKER_CONTEXT está definida ($DOCKER_CONTEXT); solo se limpia el Docker de este equipo, con su contexto por omisión."
case ${DOCKER_HOST:-} in
  '' | unix://* | npipe:////./pipe/*) ;;
  *) rechaza "DOCKER_HOST apunta a otro equipo ($DOCKER_HOST); solo se limpia el Docker de este equipo." ;;
esac

contenedor=$(docker compose -f dev/compose.yaml -p liv-backend-dev ps -q postgres) \
  || rechaza "no pude consultar el compose de desarrollo. ¿Está corriendo Docker? ¿Existe dev/.env? (cp dev/.env.example dev/.env)"
[ -n "$contenedor" ] \
  || rechaza "el contenedor de PostgreSQL de desarrollo no está levantado. Levanta el entorno primero (ver dev/README.md)."

# Una sola sentencia (una transacción): la guarda y el borrado se aplican juntos o no se aplica nada. Sin comentarios
# de SQL (--): el texto se manda como una sola línea.
sql=$(cat <<'SQL'
SET LOCAL lock_timeout = '5s';
DO $guarda$
BEGIN
  IF current_database() <> 'liv' THEN
    RAISE EXCEPTION 'No es la base de desarrollo de LIV (liv): no se borra nada';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM core.tax_administration WHERE id = '10000000-0000-0000-0000-0000000b0001') THEN
    RAISE EXCEPTION 'No es la base de desarrollo de LIV (falta la administración de prueba B de dev/seed; ¿se aplicó?): no se borra nada';
  END IF;
END
$guarda$;
DELETE FROM tax.administration_fiscal_year;
SQL
)

docker compose -f dev/compose.yaml -p liv-backend-dev exec -T postgres psql -U postgres -d liv -v ON_ERROR_STOP=1 -c "$sql" \
  || rechaza "no se borró nada. Lee el error de psql de arriba: la guarda de la base de desarrollo la rechazó, el bloqueo tardó más de 5 s, o falta la tabla (¿migraciones aplicadas?)."
