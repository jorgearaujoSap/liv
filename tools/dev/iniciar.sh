#!/usr/bin/env bash
# Levanta LIV completo en local con un solo comando: entorno (PostgreSQL y Keycloak), API, BFF y frontend.
# Es un atajo de los pasos 1 a 4 de README.md › «Ejecutar LIV en local»: cada proyecto se levanta con sus propios
# comandos y ninguno depende de este script. Se ejecuta desde Git Bash (Windows), macOS o Linux:
#
#   sh tools/dev/iniciar.sh                 # todo
#   sh tools/dev/iniciar.sh --sesion-corta  # el BFF con una sesión de 150 s, para la prueba e2e de 001-22
#
# Los registros de la API, el BFF y el frontend quedan en tools/dev/registros/. Para detener todo:
#   sh tools/dev/detener.sh
set -eu

RAIZ=$(cd "$(dirname "$0")/../.." && pwd)
REGISTROS="$RAIZ/tools/dev/registros"
SESION_CORTA=no
for argumento in "$@"; do
  case "$argumento" in
    --sesion-corta) SESION_CORTA=si ;;
    *) printf 'Opción desconocida: %s\n' "$argumento" >&2; exit 2 ;;
  esac
done

. "$RAIZ/tools/dev/comun.sh"

paso "Requisitos"
command -v docker >/dev/null || falla "Falta Docker. Instala Docker Desktop y ábrelo."
docker info >/dev/null 2>&1 || falla "Docker no responde. Abre Docker Desktop y vuelve a intentarlo."
command -v java >/dev/null || falla "Falta Java 21 (JDK)."
java -version 2>&1 | grep -q 'version "21' || falla "Hace falta el JDK 21; java -version dice otra versión."
command -v node >/dev/null || falla "Falta Node (22.12 o superior dentro de la línea 22, o 24 o superior)."
for puerto in 4200 8080 8081; do
  if pid_en_puerto "$puerto" >/dev/null; then
    falla "El puerto $puerto ya está en uso. Si es LIV, detenlo con sh tools/dev/detener.sh; si es otro programa, ciérralo."
  fi
done
mkdir -p "$REGISTROS"
if [ ! -f "$RAIZ/backend/dev/.env" ]; then
  cp "$RAIZ/backend/dev/.env.example" "$RAIZ/backend/dev/.env"
  printf 'Se creó backend/dev/.env a partir de .env.example.\n'
fi

paso "1/4 · Entorno local: PostgreSQL y Keycloak"
(cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env up -d --build)
(cd "$RAIZ/backend" && sh dev/check.sh) || falla "El entorno no quedó listo: mira la salida de check.sh arriba."

paso "2/4 · API (puerto 8080)"
(
  cd "$RAIZ/backend"
  set -a; . dev/.env; set +a
  exec ./mvnw -B -ntp -q spring-boot:run -Dspring-boot.run.profiles=dev
) > "$REGISTROS/api.log" 2>&1 &
esperar "la API" "$REGISTROS/api.log" "Started LivApplication" 8080 600

paso "3/4 · BFF (puerto 8081)"
(
  cd "$RAIZ/backend/bff"
  set -a; . ../dev/.env; set +a
  if [ "$SESION_CORTA" = si ]; then
    export SPRING_SESSION_TIMEOUT=150s
  fi
  exec ./mvnw -B -ntp -q spring-boot:run -Dspring-boot.run.profiles=dev
) > "$REGISTROS/bff.log" 2>&1 &
esperar "el BFF" "$REGISTROS/bff.log" "Started LivBffApplication" 8081 600

paso "4/4 · Frontend (puerto 4200)"
if [ ! -d "$RAIZ/frontend/node_modules" ]; then
  (cd "$RAIZ/frontend" && npm install)
fi
(cd "$RAIZ/frontend" && exec npm start) > "$REGISTROS/frontend.log" 2>&1 &
esperar "el frontend" "$REGISTROS/frontend.log" "Watch mode enabled" 4200 300

printf '\n✓ LIV está listo en http://localhost:4200\n'
printf '  Usuario de prueba: dev.consulta, contraseña Liv-Dev-2026! (los demás, en backend/dev/README.md).\n'
if [ "$SESION_CORTA" = si ]; then
  printf '  El BFF tiene una sesión de 150 s: para la prueba e2e de 001-22, no para trabajar.\n'
fi
printf '  Registros: tools/dev/registros/. Para detener todo: sh tools/dev/detener.sh\n'
