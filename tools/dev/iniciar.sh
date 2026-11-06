#!/bin/sh
# Levanta LIV completo en local con un solo comando: entorno (PostgreSQL y Keycloak), API, BFF y frontend.
# Es un atajo de los pasos 1 a 4 de README.md › «Ejecutar LIV en local»: cada proyecto se levanta con sus propios
# comandos y ninguno depende de este script. Desde la raíz del repositorio, en Git Bash (Windows), macOS o Linux:
#
#   sh tools/dev/iniciar.sh                 # todo
#   sh tools/dev/iniciar.sh --sesion-corta  # el BFF con una sesión de 150 s, para la prueba e2e de 001-22
#
# Los servicios siguen corriendo al cerrar la terminal. Sus registros quedan en tools/dev/registros/. Si algo falla
# o se interrumpe con Ctrl+C, se detiene lo que ya había arrancado. Para detener todo: sh tools/dev/detener.sh
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

# Si algo falla o se interrumpe antes de terminar, se detiene lo que ya había arrancado (el entorno se queda).
LISTO=no
limpiar() {
  if [ "$LISTO" = no ] && ls "$REGISTROS"/*.lanzado >/dev/null 2>&1; then
    printf '\nSe detiene lo que ya había arrancado…\n' >&2
    for servicio in "4200 frontend" "8081 BFF" "8080 API"; do
      if [ -f "$REGISTROS/${servicio%% *}.lanzado" ]; then
        detener_servicio "${servicio%% *}" "${servicio#* }" >&2
      fi
    done
  fi
}
trap limpiar EXIT
trap 'exit 130' INT TERM

paso "Requisitos"
requiere_herramientas
command -v docker >/dev/null 2>&1 || falla "Falta Docker. Instala Docker Desktop y ábrelo."
docker info >/dev/null 2>&1 || falla "Docker no responde. Abre Docker Desktop y vuelve a intentarlo."
command -v java >/dev/null 2>&1 || falla "Falta Java 21 (JDK)."
java -version 2>&1 | grep -q 'version "21' || falla "Hace falta el JDK 21; java -version dice otra versión."
command -v node >/dev/null 2>&1 || falla "Falta Node (22.12 o superior dentro de la línea 22, o 24 o superior)."
for puerto in $PUERTOS; do
  if puerto_ocupado "$puerto"; then
    # Nada de esto lo levantó este script: no hay que detener nada al salir.
    LISTO=si
    falla "El puerto $puerto ya está en uso. Si es LIV, detenlo con sh tools/dev/detener.sh; si es otro programa, ciérralo."
  fi
done
mkdir -p "$REGISTROS"
# Los puertos están libres: las marcas de una vez anterior ya no valen y no deben llevar a terminar nada.
rm -f "$REGISTROS"/[0-9]*.lanzado
if [ ! -f "$RAIZ/backend/dev/.env" ]; then
  cp "$RAIZ/backend/dev/.env.example" "$RAIZ/backend/dev/.env"
  printf 'Se creó backend/dev/.env a partir de .env.example.\n'
fi

paso "1/4 · Entorno local: PostgreSQL y Keycloak"
# Solo es nuestro si no estaba ya levantado: si la persona lo levantó a mano, detener.sh no lo baja.
YA_ESTABA=$(cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env ps -q 2>/dev/null || true)
(cd "$RAIZ/backend" && docker compose -f dev/compose.yaml --env-file dev/.env up -d --build)
if [ -z "$YA_ESTABA" ]; then
  touch "$REGISTROS/entorno.lanzado"
fi
(cd "$RAIZ/backend" && sh dev/check.sh) || falla "El entorno no quedó listo: mira la salida de check.sh arriba."

paso "2/4 · API (puerto 8080)"
nohup sh -c 'cd "$1/backend" && set -a && . dev/.env && set +a && exec ./mvnw -B -ntp -q spring-boot:run -Dspring-boot.run.profiles=dev' \
  api "$RAIZ" > "$REGISTROS/api.log" 2>&1 &
printf '%s\n' "$!" > "$REGISTROS/8080.lanzado"
esperar "la API" "$REGISTROS/api.log" "Started LivApplication" 8080 600 "$!"

paso "3/4 · BFF (puerto 8081)"
SESION=""
if [ "$SESION_CORTA" = si ]; then
  SESION=150s
fi
nohup sh -c 'cd "$1/backend/bff" && set -a && . ../dev/.env && set +a && if [ -n "$2" ]; then export SPRING_SESSION_TIMEOUT="$2"; fi && exec ./mvnw -B -ntp -q spring-boot:run -Dspring-boot.run.profiles=dev' \
  bff "$RAIZ" "$SESION" > "$REGISTROS/bff.log" 2>&1 &
printf '%s\n' "$!" > "$REGISTROS/8081.lanzado"
esperar "el BFF" "$REGISTROS/bff.log" "Started LivBffApplication" 8081 600 "$!"

paso "4/4 · Frontend (puerto 4200)"
if [ ! -d "$RAIZ/frontend/node_modules" ]; then
  (cd "$RAIZ/frontend" && npm install)
fi
nohup sh -c 'cd "$1/frontend" && exec npm start' frontend "$RAIZ" > "$REGISTROS/frontend.log" 2>&1 &
printf '%s\n' "$!" > "$REGISTROS/4200.lanzado"
esperar "el frontend" "$REGISTROS/frontend.log" "Watch mode enabled" 4200 300 "$!"

LISTO=si
printf '\n✓ LIV está listo en http://localhost:4200\n'
printf '  Usuario de prueba: dev.consulta, contraseña Liv-Dev-2026! (los demás, en backend/dev/README.md).\n'
if [ "$SESION_CORTA" = si ]; then
  printf '  El BFF tiene una sesión de 150 s: para la prueba e2e de 001-22, no para trabajar.\n'
fi
printf '  Registros: tools/dev/registros/. Para detener todo: sh tools/dev/detener.sh\n'
