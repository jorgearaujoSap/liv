#!/bin/sh
# Prueba del tema liv y de la imagen de Keycloak (tarea T08 de la feature 001; criterios 001-03, 001-05 y 001-19).
# Levanta un entorno propio y desechable con dev/compose.yaml (proyecto liv-identity-check y otros puertos, así que
# no toca el entorno local), recorre la página de inicio de sesión con curl y lo borra al terminar.
# La prueba visual contra los frames de Figma y la de accesibilidad con axe son de las e2e (tarea T23).
# Uso, desde backend/ y en Windows desde Git Bash:  sh identity/theme/check.sh
# Sale con 0 si todo está bien y con 1 si alguna verificación falla.
set -u
cd "$(dirname "$0")/../.."

export POSTGRES_PORT=15433 KEYCLOAK_PORT=18180 KEYCLOAK_MANAGEMENT_PORT=19100
compose() { docker compose -p liv-identity-check -f dev/compose.yaml --env-file dev/.env.example "$@"; }
leer() { sed -n "s/^$1=//p" dev/.env.example | tr -d '\r' | tail -1; }

fallas=0
ok()    { printf '  ok         %s\n' "$1"; }
falla() { printf '  FALLA      %s\n' "$1"; fallas=$((fallas + 1)); }

tmp=$(mktemp -d)
limpiar() { compose down -v >/dev/null 2>&1; rm -rf "$tmp"; }
trap limpiar EXIT
trap 'exit 1' INT TERM

echo "Tema (fuentes)"
estilos=identity/theme/liv/styles
if [ -d "$estilos" ] && ! grep -rEnq '#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(' "$estilos"; then
  ok "colores y sombras solo por tokens de liv-components"
else
  falla "colores y sombras solo por tokens de liv-components (sin hexadecimales, rgb ni hsl en $estilos)"
fi
if [ -d identity/theme/liv/login ] && ! grep -rEq '[[:space:]]style=' identity/theme/liv/login --include='*.ftl'; then
  ok "plantillas sin atributo style"
else
  falla "plantillas sin atributo style"
fi

echo "Entorno desechable (puede tardar unos minutos)"
if compose up -d --build --wait postgres keycloak >"$tmp/up.log" 2>&1 \
   && compose run --rm --no-deps keycloak-config >"$tmp/config.log" 2>&1; then
  ok "Keycloak listo y realm aplicado"
else
  falla "Keycloak listo y realm aplicado (ver docker compose -p liv-identity-check logs)"
  tail -20 "$tmp/up.log" "$tmp/config.log" 2>/dev/null
  echo "$fallas verificaciones fallaron."
  exit 1
fi

kc="http://127.0.0.1:$KEYCLOAK_PORT"
base_bff=$(leer LIV_BFF_BASE_URL)
[ -n "$base_bff" ] || { echo "Falta LIV_BFF_BASE_URL en dev/.env.example."; exit 1; }
redirect="$base_bff/login/oauth2/code/liv"
# PKCE es obligatorio para liv-bff; el reto solo tiene que tener un formato válido, la prueba no canjea el código.
reto=Ebq4c7fn9Ra0oX9cyb6pW3NtX0Kd3ZkhWjYxYHeaYM8

# pagina_login COOKIES: abre el inicio de sesión de liv-bff y escribe el HTML en la salida.
pagina_login() {
  curl -fsS -c "$1" -b "$1" -G "$kc/realms/liv/protocol/openid-connect/auth" \
    --data-urlencode client_id=liv-bff --data-urlencode response_type=code --data-urlencode scope=openid \
    --data-urlencode "redirect_uri=$redirect" --data-urlencode "code_challenge=$reto" \
    --data-urlencode code_challenge_method=S256 2>/dev/null
}
# accion: URL del formulario de inicio de sesión en el HTML de la entrada.
accion() { tr -d '\r\n' | sed -n 's/.*<form[^>]*id="kc-form-login"[^>]*action="\([^"]*\)".*/\1/p' | sed 's/&amp;/\&/g'; }
# mensaje_alerta: texto del mensaje de la alerta del tema, sin etiquetas ni espacios sobrantes.
mensaje_alerta() {
  tr -d '\r\n' | sed -n 's/.*id="liv-alert-message"[^>]*>\([^<]*\)<.*/\1/p' | sed 's/^ *//; s/ *$//'
}
# intento USUARIO CONTRASEÑA: intenta iniciar sesión y escribe el mensaje que muestra la página.
intento() {
  jar="$tmp/cookies-$1"
  url=$(pagina_login "$jar" | accion)
  [ -n "$url" ] || return 0
  curl -sS -c "$jar" -b "$jar" --data-urlencode "username=$1" --data-urlencode "password=$2" "$url" 2>/dev/null \
    | mensaje_alerta
}

echo "Página de inicio de sesión (001-19)"
if pagina_login "$tmp/cookies" >"$tmp/login.html" && [ -n "$(accion <"$tmp/login.html")" ]; then
  ok "responde con el formulario de inicio de sesión"
else
  falla "responde con el formulario de inicio de sesión"
fi
css=$(tr -d '\r\n' <"$tmp/login.html" | grep -o '/resources/[^"]*/login/liv/css/liv\.css' | head -1)
if [ -n "$css" ]; then ok "usa el tema liv"; else falla "usa el tema liv (realm con loginTheme: liv e imagen con el tema)"; fi
if grep -q '<html[^>]*lang="es"' "$tmp/login.html"; then ok "en español (lang=es)"; else falla "en español (lang=es)"; fi
for texto in 'Inicia sesión' 'Usa tu usuario y contraseña de LIV.' '>Usuario<' '>Contraseña<' 'Ingresar' \
             'Comunícate con el administrador de tu entidad.' 'Conexión cifrada'; do
  if grep -qF "$texto" "$tmp/login.html"; then ok "texto: $texto"; else falla "texto: $texto"; fi
done
if [ -n "$css" ] && curl -fsS "$kc$css" >"$tmp/liv.css" 2>/dev/null \
   && grep -q -- '--color-action-primary:' "$tmp/liv.css" && ! grep -Eq -- '--color-(red|blue|slate|gray)-[0-9]' "$tmp/liv.css"; then
  ok "hoja de estilos compilada con los tokens de liv-components y sin la paleta de Tailwind"
else
  falla "hoja de estilos compilada con los tokens de liv-components y sin la paleta de Tailwind"
fi

echo "Mensaje único de fallo (001-03, 001-04, 001-05)"
esperado='Usuario o contraseña incorrectos. Tras varios intentos fallidos el acceso se bloquea por 15 minutos.'
# Contraseña de los usuarios ficticios de dev/keycloak/liv-realm-dev.yaml.
clave='Liv-Dev-2026!'
# mensaje_unico USUARIO CONTRASEÑA CASO: el intento debe mostrar exactamente el mensaje esperado.
mensaje_unico() {
  obtenido=$(intento "$1" "$2")
  if [ "$obtenido" = "$esperado" ]; then ok "$3: mensaje genérico"; else falla "$3: mensaje genérico (obtuvo: '$obtenido')"; fi
}
mensaje_unico dev.liquidador clave-equivocada 'contraseña incorrecta'
mensaje_unico dev.no.existe "$clave" 'usuario inexistente'
mensaje_unico dev.desactivado "$clave" 'usuario desactivado con la contraseña correcta'

if [ "$fallas" -eq 0 ]; then
  echo "Tema liv verificado."
  exit 0
fi
echo "$fallas verificaciones fallaron."
exit 1
