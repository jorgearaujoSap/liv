#!/bin/sh
# Prueba del tema liv y de la imagen de Keycloak (tarea T08 de la feature 001; criterios 001-03 a 001-07 y
# 001-19). Levanta un entorno propio y desechable con dev/compose.yaml (proyecto liv-identity-check, otros puertos y
# otra etiqueta de imagen, así que no toca el entorno local), recorre las páginas con curl y lo borra al terminar.
# La prueba visual contra los frames de Figma y la de accesibilidad con axe son de las e2e (tarea T23).
# Uso, desde backend/ y en Windows desde Git Bash:  sh identity/theme/check.sh
# Sale con 0 si todo está bien y con 1 si alguna verificación falla.
set -u
cd "$(dirname "$0")/../.."

proyecto=liv-identity-check
export POSTGRES_PORT=15433 KEYCLOAK_PORT=18180 KEYCLOAK_MANAGEMENT_PORT=19100
export LIV_IDENTITY_IMAGE=liv-identity-keycloak:check
puerto_optimizado=18181
optimizado="$proyecto-optimizado"
compose() { docker compose -p "$proyecto" -f dev/compose.yaml --env-file dev/.env.example "$@"; }
leer() { sed -n "s/^$1=//p" dev/.env.example | tr -d '\r' | tail -1; }

fallas=0
ok()    { printf '  ok         %s\n' "$1"; }
falla() { printf '  FALLA      %s\n' "$1"; fallas=$((fallas + 1)); }

# Dos corridas a la vez se borrarían el entorno una a otra: un candado atómico (mkdir) deja pasar solo una. Lo que
# quede de una corrida que se cortó sin limpiar (candado o contenedores) se borra a mano.
candado="${TMPDIR:-/tmp}/$proyecto.lock"
if ! mkdir "$candado" 2>/dev/null; then
  echo "Hay otra corrida de esta prueba. Si no la hay (una anterior se cortó sin limpiar), borra el candado:"
  echo "  rmdir $candado"
  exit 1
fi
if [ -n "$(compose ps -aq 2>/dev/null)" ] || docker container inspect "$optimizado" >/dev/null 2>&1; then
  rmdir "$candado"
  echo "Quedó el entorno $proyecto de una corrida que se cortó sin limpiar. Bórralo con:"
  echo "  docker rm -f $optimizado; docker compose -p $proyecto -f dev/compose.yaml --env-file dev/.env.example down -v"
  exit 1
fi

tmp=$(mktemp -d)
limpiar() {
  docker rm -f "$optimizado" >/dev/null 2>&1
  compose down -v >/dev/null 2>&1
  docker image rm "$LIV_IDENTITY_IMAGE" >/dev/null 2>&1
  rm -rf "$tmp"
  rmdir "$candado" 2>/dev/null
}
trap limpiar EXIT
trap 'exit 1' INT TERM

echo "Tema (fuentes, 001-19)"
# Colores literales: hexadecimales, funciones de color y los nombres que usaría una hoja escrita a mano.
color_literal='#[0-9a-fA-F]{3,8}\b|(rgba?|hsla?|oklch|oklab|lab|lch|hwb|color-mix)\(|:[[:space:]]*(white|black)\b'
if [ -d identity/theme/liv ] && ! grep -rEq "$color_literal" identity/theme/liv/styles identity/theme/liv/login; then
  ok "colores solo por tokens de liv-components (estilos, plantillas y scripts)"
else
  falla "colores solo por tokens de liv-components (sin colores literales en identity/theme/liv)"
fi
if [ -d identity/theme/liv/login ] && ! grep -rEq '[[:space:]]style=|<style|\.style\b' identity/theme/liv/login; then
  ok "sin estilos en línea en plantillas ni scripts"
else
  falla "sin estilos en línea en plantillas ni scripts"
fi

echo "Entorno desechable (puede tardar unos minutos)"
# El realm se aplica dos veces: reaplicar debe ser seguro (identity/README.md).
if compose up -d --build --wait --wait-timeout 300 postgres keycloak >"$tmp/up.log" 2>&1 \
   && compose run --rm --no-deps keycloak-config >"$tmp/config.log" 2>&1 \
   && compose run --rm --no-deps keycloak-config >>"$tmp/config.log" 2>&1; then
  ok "Keycloak listo y realm aplicado y reaplicado"
else
  falla "Keycloak listo y realm aplicado y reaplicado (ver docker compose -p $proyecto logs)"
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

# pagina_login COOKIES [URL_BASE]: abre el inicio de sesión de liv-bff y escribe el HTML en la salida.
pagina_login() {
  curl -fsS -m 30 -c "$1" -b "$1" -G "${2:-$kc}/realms/liv/protocol/openid-connect/auth" \
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
# compacto: el HTML en una línea y sin espacios alrededor de las etiquetas, para buscar el texto de un elemento.
compacto() { tr -d '\r\n' | sed 's/>[[:space:]]*/>/g; s/[[:space:]]*</</g'; }
# main_normalizado USUARIO: el <main> de la página sin lo que cambia en cada petición (los parámetros de sesión de la
# URL del formulario) y con el usuario escrito reemplazado por USUARIO. Lo demás, campos ocultos incluidos, debe ser
# idéntico en todos los fallos; si Keycloak devolviera el usuario en un caso y no en otro, también se notaría.
main_normalizado() {
  tr -d '\r\n' | sed -n 's/.*\(<main.*<\/main>\).*/\1/p' \
    | sed 's/session_code=[^&"]*/session_code=/g; s/tab_id=[^&"]*/tab_id=/g; s/client_data=[^&"]*/client_data=/g' \
    | sed "s/value=\"$1\"/value=\"USUARIO\"/g"
}
# intento ARCHIVO USUARIO CONTRASEÑA: intenta iniciar sesión con cookies propias (sin la sesión de otro intento),
# guarda la respuesta en ARCHIVO y escribe su código HTTP y, si redirige, su destino.
intento() {
  jar="$1.cookies"
  url=$(pagina_login "$jar" | accion)
  [ -n "$url" ] || { : >"$1"; echo "sin-formulario"; return 0; }
  curl -sS -m 30 -o "$1" -w '%{http_code} %{redirect_url}' -c "$jar" -b "$jar" \
    --data-urlencode "username=$2" --data-urlencode "password=$3" "$url" 2>/dev/null
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
for texto in '<title>Inicia sesión · LIV</title>' '>Inicia sesión</h1>' 'Usa tu usuario y contraseña de LIV.' \
             '>Usuario</label>' '>Contraseña</label>' '"liv-button__label">Ingresar<' \
             'Comunícate con el administrador de tu entidad.' 'Conexión cifrada · Los accesos quedan registrados'; do
  if compacto <"$tmp/login.html" | grep -qF "$texto"; then
    ok "texto: $texto"
  else
    falla "texto: $texto"
  fi
done
# liv.js importa authChecker.js de la plantilla base (parent=base): si dejara de existir o de exportar sus funciones,
# el módulo del tema no se cargaría.
js=$(tr -d '\r\n' <"$tmp/login.html" | grep -o '/resources/[^"]*/login/liv/js/liv\.js' | head -1)
auth_checker="${js%liv.js}authChecker.js"
if [ -n "$js" ] && curl -fsS -m 30 "$kc$js" >/dev/null 2>&1 \
   && curl -fsS -m 30 "$kc$auth_checker" >"$tmp/authChecker.js" 2>/dev/null \
   && grep -q 'export function startSessionPolling' "$tmp/authChecker.js" \
   && grep -q 'export function checkAuthSession' "$tmp/authChecker.js"; then
  ok "sirve liv.js y el authChecker.js de la plantilla base con sus dos funciones"
else
  falla "sirve liv.js y el authChecker.js de la plantilla base con sus dos funciones"
fi
# La hoja compilada solo define colores de los grupos de tokens de liv-components, nunca la paleta de Tailwind.
if [ -n "$css" ] && curl -fsS -m 30 "$kc$css" >"$tmp/liv.css" 2>/dev/null && grep -q -- '--color-action-primary:' "$tmp/liv.css" \
   && [ -z "$(grep -o -- '--color-[a-z0-9-]*:' "$tmp/liv.css" | grep -Ev -- '^--color-(text|bg|border|action|status)-')" ]; then
  ok "hoja de estilos compilada solo con los colores de los tokens de liv-components"
else
  falla "hoja de estilos compilada solo con los colores de los tokens de liv-components"
fi

echo "Mensaje único de fallo (001-03, 001-04, 001-05)"
esperado='Usuario o contraseña incorrectos. Tras varios intentos fallidos el acceso se bloquea por 15 minutos.'
# Contraseña de los usuarios ficticios de dev/keycloak/liv-realm-dev.yaml.
clave='Liv-Dev-2026!'

# Control positivo: con la contraseña correcta un usuario activo sí entra (redirige al BFF con un código).
respuesta=$(intento "$tmp/correcto.html" dev.liquidador "$clave")
case "$respuesta" in
  "302 $redirect?"*code=*) ok "usuario activo con la contraseña correcta: entra" ;;
  *) falla "usuario activo con la contraseña correcta: entra (obtuvo: '$respuesta')" ;;
esac

# Cuenta bloqueada: 5 fallos seguidos, espaciados más de 1 s para no activar la protección contra intentos
# automáticos (60 s), y luego la contraseña correcta.
for n in 1 2 3 4 5; do
  intento "$tmp/fallo-$n.html" dev.recaudo clave-equivocada >/dev/null
  sleep 2
done

# mensaje_unico CASO USUARIO CONTRASEÑA DESCRIPCIÓN: el intento no entra (no redirige), responde con el mismo código
# HTTP, muestra exactamente el mensaje esperado y la misma página que la contraseña incorrecta, salvo lo que escribió
# el usuario. El primer caso debe ser "incorrecta".
estado_incorrecta=
usuario_incorrecta=
mensaje_unico() {
  respuesta=$(intento "$tmp/$1.html" "$2" "$3")
  obtenido=$(mensaje_alerta <"$tmp/$1.html")
  if [ "$1" = incorrecta ]; then estado_incorrecta=${respuesta%% *}; usuario_incorrecta=$2; fi
  if [ "${respuesta%% *}" = "$estado_incorrecta" ] && [ "${respuesta#3}" = "$respuesta" ] && [ "$obtenido" = "$esperado" ]; then
    ok "$4: no entra y ve el mensaje genérico"
  else
    falla "$4: no entra y ve el mensaje genérico (obtuvo: '$respuesta' '$obtenido')"
  fi
  if [ "$1" != incorrecta ]; then
    if [ -n "$(main_normalizado "$2" <"$tmp/$1.html")" ] \
       && [ "$(main_normalizado "$2" <"$tmp/$1.html")" = "$(main_normalizado "$usuario_incorrecta" <"$tmp/incorrecta.html")" ]; then
      ok "$4: página idéntica a la de contraseña incorrecta"
    else
      falla "$4: página idéntica a la de contraseña incorrecta"
    fi
  fi
}
mensaje_unico incorrecta dev.liquidador clave-equivocada 'contraseña incorrecta (001-03)'
mensaje_unico inexistente dev.no.existe "$clave" 'usuario inexistente (001-03)'
mensaje_unico bloqueado dev.recaudo "$clave" 'cuenta bloqueada con la contraseña correcta (001-04)'
mensaje_unico desactivado dev.desactivado "$clave" 'usuario desactivado con la contraseña correcta (001-05)'

echo "Página de error (001-19)"
codigo=$(curl -sS -m 30 -o "$tmp/error.html" -w '%{http_code}' -G "$kc/realms/liv/protocol/openid-connect/auth" \
  --data-urlencode client_id=cliente-inexistente --data-urlencode response_type=code 2>/dev/null)
if [ "$codigo" = 400 ] && grep -q '<html[^>]*lang="es"' "$tmp/error.html" && grep -q '/login/liv/css/liv\.css' "$tmp/error.html" \
   && compacto <"$tmp/error.html" | grep -qF '>No pudimos continuar</h1>'&& [ -n "$(mensaje_alerta <"$tmp/error.html")" ]; then
  ok "cliente desconocido: página de error del tema, en español"
else
  falla "cliente desconocido: página de error del tema, en español (HTTP $codigo)"
fi

echo "Contraseña nueva y segundo factor (001-06, 001-07, 001-19)"
# Con la API de administración del entorno desechable: dev.consulta debe cambiar su contraseña y un usuario de
# prueba con rol de MFA ya tiene TOTP con un secreto conocido. Todo es ficticio y desaparece con el entorno.
admin_token=$(curl -fsS -m 30 -d grant_type=password -d client_id=admin-cli -d "username=$(leer KEYCLOAK_ADMIN_USER)" \
  -d "password=$(leer KEYCLOAK_ADMIN_PASSWORD)" "$kc/realms/master/protocol/openid-connect/token" 2>/dev/null \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
# admin MÉTODO RUTA [JSON]: llama a la API de administración del realm liv y escribe la respuesta; falla si la API falla.
admin() {
  if [ $# -ge 3 ]; then set -- "$1" "$2" --data "$3"; fi
  metodo=$1 ruta=$2; shift 2
  curl -fsS -m 30 -X "$metodo" -H "Authorization: Bearer $admin_token" -H 'Content-Type: application/json' \
    "$@" "$kc/admin/realms/liv$ruta" 2>/dev/null
}
id_de() { admin GET "/users?exact=true&username=$1" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4; }
secreto_otp=secreto-de-prueba-liv
if [ -n "$admin_token" ] && [ -n "$(id_de dev.consulta)" ] \
   && admin PUT "/users/$(id_de dev.consulta)" '{"requiredActions":["UPDATE_PASSWORD"]}' >/dev/null \
   && admin POST /users '{"username":"dev.otp.prueba","enabled":true,"email":"dev.otp.prueba@example.test",
        "firstName":"Otto","lastName":"Prueba","attributes":{"administration_id":["10000000-0000-0000-0000-000000052356"]},
        "credentials":[{"type":"password","value":"'"$clave"'","temporary":false},
        {"type":"otp","secretData":"{\"value\":\"'"$secreto_otp"'\"}",
         "credentialData":"{\"subType\":\"totp\",\"digits\":6,\"counter\":0,\"period\":30,\"algorithm\":\"HmacSHA1\"}"}]}' >/dev/null \
   && id_otp=$(id_de dev.otp.prueba) && [ -n "$id_otp" ] \
   && admin POST "/users/$id_otp/role-mappings/realm" "[$(admin GET /roles/AUDITOR)]" >/dev/null; then
  ok "preparación con la API de administración (acción requerida y usuario con TOTP)"
else
  falla "preparación con la API de administración (acción requerida y usuario con TOTP)"
fi

# totp SECRETO: código TOTP de ahora (RFC 6238 con la política del realm: SHA-1, 6 dígitos, 30 s). La clave HMAC son
# los bytes del texto del secreto; el contador va en 8 bytes, escritos en octal para printf.
totp() {
  contador=$(printf '%016x' $(( $(date +%s) / 30 )))
  binario= i=1
  while [ $i -le 16 ]; do
    binario="$binario$(printf '\\%03o' $(( 0x$(printf '%s' "$contador" | cut -c$i-$((i + 1))) )))"
    i=$((i + 2))
  done
  clave_hex=$(printf '%s' "$1" | od -An -tx1 | tr -d ' \n')
  mac=$(printf "$binario" | openssl dgst -sha1 -mac HMAC -macopt "hexkey:$clave_hex" | sed 's/.*= //')
  desplazamiento=$(( 0x$(printf '%s' "$mac" | cut -c40) ))
  parte=$(printf '%s' "$mac" | cut -c$((desplazamiento * 2 + 1))-$((desplazamiento * 2 + 8)))
  printf '%06d' $(( (0x$parte & 0x7fffffff) % 1000000 ))
}
# siguiente_paso ARCHIVO USUARIO: inicia sesión con la contraseña correcta y guarda la página del paso siguiente. Sigue
# las redirecciones: Keycloak lleva a las acciones requeridas (contraseña nueva, configurar TOTP) con una.
siguiente_paso() {
  jar="$1.cookies"
  url=$(pagina_login "$jar" | accion)
  curl -sS -m 30 -L -o "$1" -c "$jar" -b "$jar" --data-urlencode "username=$2" --data-urlencode "password=$clave" \
    "$url" 2>/dev/null
}
# enviar_paso ARCHIVO_PREVIO ARCHIVO FORMULARIO CAMPO [CAMPO]: envía el formulario FORMULARIO de la página previa, con
# sus cookies, guarda la respuesta y escribe su código HTTP y, si redirige, su destino. Cada CAMPO es nombre=valor.
# Como un navegador, envía también el campo oculto logout-sessions si la página lo tiene.
enviar_paso() {
  previo=$1 archivo=$2 formulario=$3; shift 3
  url=$(tr -d '\r\n' <"$previo" | sed -n "s/.*<form[^>]*id=\"$formulario\"[^>]*action=\"\([^\"]*\)\".*/\1/p" | sed 's/&amp;/\&/g')
  if [ $# -ge 2 ]; then set -- --data-urlencode "$1" --data-urlencode "$2"; else set -- --data-urlencode "$1"; fi
  if grep -q 'type="hidden" name="logout-sessions" value="on"' "$previo"; then set -- "$@" --data logout-sessions=on; fi
  cp "$previo.cookies" "$archivo.cookies"
  curl -sS -m 30 -o "$archivo" -w '%{http_code} %{redirect_url}' -c "$archivo.cookies" -b "$archivo.cookies" \
    "$@" "$url" 2>/dev/null
}
# pagina ARCHIVO DESCRIPCIÓN PAGEID TEXTO…: la página es la de Keycloak indicada, con el tema y cada texto (o atributo).
pagina() {
  archivo=$1 descripcion=$2 page_id=$3; shift 3
  faltan=
  grep -q "data-page-id=\"login-$page_id\"" "$archivo" || faltan="$faltan [página $page_id]"
  grep -q '/login/liv/css/liv\.css' "$archivo" || faltan="$faltan [tema]"
  for texto in "$@"; do compacto <"$archivo" | grep -qF -- "$texto" || faltan="$faltan [$texto]"; done
  if [ -z "$faltan" ]; then ok "$descripcion"; else falla "$descripcion (falta:$faltan)"; fi
}
# sin_aviso ARCHIVO DESCRIPCIÓN: ni alerta general ni cuenta intentada, como en los frames.
sin_aviso() {
  if [ -s "$1" ] && ! grep -q 'class="liv-alert' "$1" && ! grep -q 'liv-attempted-user' "$1"; then
    ok "$2: sin alerta general ni cuenta intentada"
  else
    falla "$2: sin alerta general ni cuenta intentada"
  fi
}
# error_en_campo ARCHIVO DESCRIPCIÓN CAMPO MENSAJE: el error está en la ayuda de ese campo, con su icono, el campo queda
# aria-invalid y no hay alerta general que lo repita (State=Error de TextField y CodeInput).
error_en_campo() {
  if compacto <"$1" | grep -o "id=\"$3-help\"><svg.*" | sed 's#</p>.*##' | grep -qF "<span>$4</span>" \
     && tr -d '\r\n' <"$1" | grep -q "id=\"$3\"[^>]*aria-invalid=\"true\"" && ! grep -q 'class="liv-alert' "$1"; then
    ok "$2"
  else
    falla "$2"
  fi
}
# entra RESPUESTA DESCRIPCIÓN: la respuesta de enviar_paso redirige al BFF con un código (el paso terminó bien).
entra() {
  case "$1" in
    "302 $redirect?"*code=*) ok "$2" ;;
    *) falla "$2 (obtuvo: '$1')" ;;
  esac
}
codigo_invalido='El código no es válido. Los intentos fallidos cuentan para el bloqueo de la cuenta.'

siguiente_paso "$tmp/clave-nueva.html" dev.consulta
pagina "$tmp/clave-nueva.html" 'actualizar contraseña: página del frame, con los campos de Keycloak' login-update-password \
  '>Crea una contraseña nueva</h1>' 'Antes de continuar, crea una contraseña nueva para tu cuenta.' \
  '>Contraseña nueva</label>' '>Confirma la contraseña</label>' 'Debe cumplir la política de contraseñas de tu entidad.' \
  '"liv-button__label">Guardar y continuar<' 'Conexión cifrada · Los accesos quedan registrados' \
  'name="password-new"' 'name="password-confirm"' 'type="hidden" name="logout-sessions" value="on"'
sin_aviso "$tmp/clave-nueva.html" 'actualizar contraseña'
enviar_paso "$tmp/clave-nueva.html" "$tmp/clave-distinta.html" kc-passwd-update-form \
  'password-new=Clave-Nueva-Prueba-2026!' 'password-confirm=Otra-Clave-Prueba-2026!' >/dev/null
error_en_campo "$tmp/clave-distinta.html" 'actualizar contraseña: confirmación distinta, error en su campo' \
  password-confirm 'Las contraseñas no coinciden.'
# Keycloak manda los errores de la política como mensaje general: la alerta lo explica y marca el campo nuevo.
enviar_paso "$tmp/clave-distinta.html" "$tmp/clave-corta.html" kc-passwd-update-form \
  'password-new=Corta-1!' 'password-confirm=Corta-1!' >/dev/null
if mensaje_alerta <"$tmp/clave-corta.html" | grep -qF 'longitud mínima 12' \
   && tr -d '\r\n' <"$tmp/clave-corta.html" \
      | grep -q 'id="password-new"[^>]*aria-invalid="true"[^>]*aria-describedby="liv-alert-message"'; then
  ok "actualizar contraseña: política no cumplida, en la alerta y con el campo marcado"
else
  falla "actualizar contraseña: política no cumplida, en la alerta y con el campo marcado"
fi
entra "$(enviar_paso "$tmp/clave-corta.html" "$tmp/clave-valida.html" kc-passwd-update-form \
  'password-new=Clave-Nueva-Prueba-2026!' 'password-confirm=Clave-Nueva-Prueba-2026!')" \
  'actualizar contraseña: con una contraseña válida, entra'

siguiente_paso "$tmp/configurar.html" dev.admin.sistema
pagina "$tmp/configurar.html" 'configurar segundo factor: página del frame, con los campos de Keycloak (001-06)' login-config-totp \
  '>Activa la verificación en dos pasos</h1>' 'Tu rol exige un segundo factor. Solo lo configuras una vez.' \
  '<ol class="liv-steps" role="list">' '>Instala una aplicación de autenticación<' '>Escanea el código QR<' \
  '>Escribe el código de 6 dígitos<' 'src="data:image/png;base64,' 'Escribe esta clave en la aplicación:' \
  'id="kc-totp-secret-key"' '>Código de verificación</label>' 'inputmode="numeric"' 'autocomplete="one-time-code"' \
  'maxlength="6"' 'Escribe los 6 dígitos sin espacios.' '"liv-button__label">Activar y continuar<' \
  'name="totp"' 'name="totpSecret"' 'type="hidden" name="logout-sessions" value="on"'
sin_aviso "$tmp/configurar.html" 'configurar segundo factor'
# Aquí el foco no salta los pasos ni el QR: el campo del código no tiene autofocus.
if tr -d '\r\n' <"$tmp/configurar.html" | grep -q 'id="totp"[^>]*autofocus'; then
  falla "configurar segundo factor: el código sin autofocus"
else
  ok "configurar segundo factor: el código sin autofocus"
fi
# Keycloak recibe el secreto en un campo oculto junto con el código; en esta página el fallo no cuenta para el bloqueo.
secreto=$(tr -d '\r\n' <"$tmp/configurar.html" | sed -n 's/.*name="totpSecret" value="\([^"]*\)".*/\1/p')
enviar_paso "$tmp/configurar.html" "$tmp/configurar-invalido.html" kc-totp-settings-form totp=000000 "totpSecret=$secreto" >/dev/null
error_en_campo "$tmp/configurar-invalido.html" 'configurar segundo factor: código inválido, error en el campo y sin aviso de bloqueo' \
  totp 'El código no es válido. Escribe el que muestra ahora la aplicación.'
# "Escribe el que muestra ahora la aplicación" supone que el secreto no cambia al volver a mostrar la página.
secreto_nuevo=$(tr -d '\r\n' <"$tmp/configurar-invalido.html" | sed -n 's/.*name="totpSecret" value="\([^"]*\)".*/\1/p')
if [ -n "$secreto_nuevo" ] && [ "$secreto_nuevo" = "$secreto" ]; then
  ok "configurar segundo factor: tras un código inválido, el mismo secreto"
else
  falla "configurar segundo factor: tras un código inválido, el mismo secreto"
fi
entra "$(enviar_paso "$tmp/configurar-invalido.html" "$tmp/configurar-valido.html" kc-totp-settings-form \
  "totp=$(totp "$secreto_nuevo")" "totpSecret=$secreto_nuevo")" 'configurar segundo factor: con el código correcto, entra (001-06)'

siguiente_paso "$tmp/codigo.html" dev.otp.prueba
pagina "$tmp/codigo.html" 'código del segundo factor: página del frame, con el campo de Keycloak (001-07)' login-otp \
  '>Verificación en dos pasos</h1>' 'Escribe el código de 6 dígitos que muestra tu aplicación de autenticación.' \
  '>Código de verificación</label>' 'El código cambia cada 30 segundos.' '"liv-button__label">Verificar<' \
  'login-actions/restart' '>Volver a iniciar sesión<' 'name="otp"'
sin_aviso "$tmp/codigo.html" 'código del segundo factor'
enviar_paso "$tmp/codigo.html" "$tmp/codigo-invalido.html" kc-otp-login-form otp=000000 >/dev/null
error_en_campo "$tmp/codigo-invalido.html" 'código del segundo factor: código inválido, error en el campo (001-07)' \
  otp "$codigo_invalido"
paso_usado=$(( $(date +%s) / 30 ))
entra "$(enviar_paso "$tmp/codigo-invalido.html" "$tmp/codigo-valido.html" kc-otp-login-form "otp=$(totp "$secreto_otp")")" \
  'código del segundo factor: con el código correcto, entra (001-07)'

# Cuenta como intento fallido (001-07): 5 códigos inválidos seguidos, espaciados más de 1 s, bloquean la cuenta; con
# el código correcto sigue el mismo mensaje.
siguiente_paso "$tmp/bloqueo-0.html" dev.otp.prueba
previo="$tmp/bloqueo-0.html"
for n in 1 2 3 4 5; do
  enviar_paso "$previo" "$tmp/bloqueo-$n.html" kc-otp-login-form otp=000000 >/dev/null
  previo="$tmp/bloqueo-$n.html"
  sleep 2
done
# Keycloak puede registrar el último fallo un instante después de responder: se espera a que confirme el bloqueo.
bloqueada=no
for _ in $(seq 1 10); do
  if admin GET "/attack-detection/brute-force/users/$id_otp" | grep -q '"disabled":true'; then bloqueada=si; break; fi
  sleep 1
done
if [ "$bloqueada" = si ]; then
  ok "código del segundo factor: 5 códigos inválidos bloquean la cuenta (001-07)"
else
  falla "código del segundo factor: 5 códigos inválidos bloquean la cuenta (001-07)"
fi
# Keycloak rechaza un código ya usado (otpPolicyCodeReusable: false): se espera la siguiente ventana de 30 s para que
# el rechazo se deba al bloqueo y no a la reutilización.
while [ $(( $(date +%s) / 30 )) -le "$paso_usado" ]; do sleep 1; done
respuesta=$(enviar_paso "$previo" "$tmp/bloqueo-correcto.html" kc-otp-login-form "otp=$(totp "$secreto_otp")")
if [ "${respuesta#3}" = "$respuesta" ] && compacto <"$tmp/bloqueo-correcto.html" | grep -qF "<span>$codigo_invalido</span>"; then
  ok "código del segundo factor: con la cuenta bloqueada, el código correcto no entra y ve el mismo mensaje (001-04)"
else
  falla "código del segundo factor: con la cuenta bloqueada, el código correcto no entra y ve el mismo mensaje (001-04) (obtuvo: '$respuesta')"
fi

echo "Imagen"
# MSYS_NO_PATHCONV: Git Bash convertiría la ruta del contenedor en una de Windows.
if MSYS_NO_PATHCONV=1 docker run --rm --entrypoint cat "$LIV_IDENTITY_IMAGE" /opt/keycloak/themes/liv/THIRD_PARTY_NOTICES.md \
     2>/dev/null | grep -q 'ISC License'; then
  ok "incluye los avisos de licencia de los iconos (THIRD_PARTY_NOTICES.md)"
else
  falla "incluye los avisos de licencia de los iconos (THIRD_PARTY_NOTICES.md)"
fi

echo "Arranque de producción de la imagen (start --optimized)"
# La misma imagen con su comando por defecto, sobre la base ya configurada y con caché local para no formar clúster
# con el Keycloak de desarrollo.
docker run -d --name "$optimizado" --network "${proyecto}_default" -p "127.0.0.1:$puerto_optimizado:8080" \
  -e KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak -e KC_DB_USERNAME=keycloak \
  -e "KC_DB_PASSWORD=$(leer KEYCLOAK_DB_PASSWORD)" -e "KC_HOSTNAME=http://localhost:$puerto_optimizado" \
  -e KC_HTTP_ENABLED=true -e KC_CACHE=local "$LIV_IDENTITY_IMAGE" >/dev/null 2>&1
listo=no
for _ in $(seq 1 60); do
  if curl -fsS -m 5 "http://127.0.0.1:$puerto_optimizado/realms/liv/.well-known/openid-configuration" >/dev/null 2>&1; then
    listo=si; break
  fi
  sleep 3
done
if [ "$listo" = si ] && pagina_login "$tmp/cookies-optimizado" "http://127.0.0.1:$puerto_optimizado" \
     | grep -q '/login/liv/css/liv\.css'; then
  ok "start --optimized sirve la página con el tema liv"
else
  falla "start --optimized sirve la página con el tema liv (ver docker logs $optimizado)"
fi

if [ "$fallas" -eq 0 ]; then
  echo "Tema liv verificado."
  exit 0
fi
echo "$fallas verificaciones fallaron."
exit 1
