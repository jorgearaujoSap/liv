#!/bin/sh
# Verifica que el entorno local del backend esté listo (tarea T09 de la feature 001).
# Uso, con el compose levantado y desde Git Bash en Windows:  sh dev/check.sh   (desde backend/)
# Sale con 0 si todo está bien y con 1 si alguna verificación falla.
set -u
cd "$(dirname "$0")"

# Lee solo las variables que necesita, sin ejecutar .env como script.
leer() { [ -f .env ] && sed -n "s/^$1=//p" .env | tr -d '\r' | tail -1; }
pg_port=$(leer POSTGRES_PORT); pg_port=${pg_port:-5433}
kc_port=$(leer KEYCLOAK_PORT); kc_port=${kc_port:-8180}
kc_mgmt=$(leer KEYCLOAK_MANAGEMENT_PORT); kc_mgmt=${kc_mgmt:-9100}

fallas=0
ok()    { printf '  ok         %s\n' "$1"; }
falla() { printf '  FALLA      %s\n' "$1"; fallas=$((fallas + 1)); }
psql_q() { docker compose exec -T postgres psql -U postgres -d "$1" -tAc "$2" 2>/dev/null; }
es() { if [ "$(psql_q "$1" "$2")" = "$3" ]; then ok "$4"; else falla "$4"; fi; }

if [ -z "$(docker compose ps -q postgres 2>/dev/null)" ]; then
  echo "El contenedor de PostgreSQL no existe. Levanta el entorno primero (ver dev/README.md)."
  exit 1
fi

echo "PostgreSQL"
if docker compose exec -T postgres pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then ok "responde"; else falla "no responde"; fi
es postgres "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'liv'" liv_owner "base liv, de liv_owner"
es postgres "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname = 'keycloak'" keycloak "base keycloak, de keycloak"

# Formato rol:login. Ninguno con BYPASSRLS, SUPERUSER, CREATEDB ni CREATEROLE (doc. 02 §5.2, ADR 0025).
for spec in liv_owner:true liv_app:true liv_bff_owner:true liv_bff:true keycloak:true \
            liv_identity_lookup:false liv_audit_platform:false; do
  rol=${spec%%:*}
  login=${spec##*:}
  es postgres "SELECT rolcanlogin::text || '/' || (rolbypassrls OR rolsuper OR rolcreatedb OR rolcreaterole)::text FROM pg_roles WHERE rolname = '$rol'" \
    "$login/false" "rol $rol (login=$login, sin privilegios de administración)"
done

# Cada rol con inicio de sesión entra con su contraseña de .env. Por el nombre del servicio y no por 127.0.0.1,
# porque la imagen de PostgreSQL confía (trust) en las conexiones locales sin pedir contraseña.
for spec in liv_owner:LIV_OWNER_PASSWORD:liv liv_app:LIV_APP_PASSWORD:liv liv_bff_owner:LIV_BFF_OWNER_PASSWORD:liv \
            liv_bff:LIV_BFF_PASSWORD:liv keycloak:KEYCLOAK_DB_PASSWORD:keycloak; do
  rol=${spec%%:*}; resto=${spec#*:}; var=${resto%%:*}; db=${resto##*:}
  if docker compose exec -T -e PGPASSWORD="$(leer "$var")" postgres psql -h postgres -U "$rol" -d "$db" -tAc 'SELECT 1' >/dev/null 2>&1; then
    ok "inicio de sesión de $rol en $db"
  else
    falla "inicio de sesión de $rol en $db"
  fi
done

for db in liv keycloak postgres; do
  es liv "SELECT has_database_privilege('public', '$db', 'CONNECT') OR has_database_privilege('public', '$db', 'TEMP')" f "PUBLIC sin CONNECT ni TEMP en $db"
done
for rol in liv_app liv_bff liv_bff_owner; do
  es liv "SELECT has_database_privilege('$rol', 'liv', 'TEMP')" f "$rol sin TEMP"
done
es liv "SELECT has_database_privilege('liv_app', 'keycloak', 'CONNECT')" f "liv_app sin acceso a la base keycloak"
es liv "SELECT has_schema_privilege('public', 'public', 'USAGE')" f "PUBLIC sin acceso al esquema public de liv"
es liv "SELECT nspowner::regrole::text FROM pg_namespace WHERE nspname = 'bff'" liv_bff_owner "esquema bff de liv_bff_owner"
es liv "SELECT has_schema_privilege('liv_bff', 'bff', 'USAGE')" t "liv_bff con acceso al esquema bff"
es liv "SELECT has_schema_privilege('liv_app', 'bff', 'USAGE')" f "liv_app sin acceso al esquema bff"
es liv "SELECT count(*) FROM pg_default_acl d JOIN pg_roles r ON r.oid = d.defaclrole WHERE d.defaclobjtype = 'f' AND d.defaclnamespace = 0 AND r.rolname IN ('liv_owner', 'liv_bff_owner')" 2 \
  "funciones nuevas sin EXECUTE para PUBLIC"

echo "Keycloak"
if curl -fsS "http://127.0.0.1:$kc_mgmt/health/ready" >/dev/null 2>&1; then ok "listo (health/ready)"; else falla "no está listo en el puerto $kc_mgmt"; fi
# El realm liv llega con la tarea T07 (backend/identity). Mientras no haya archivo de realm, la verificación queda pendiente.
if ls keycloak/realm/*.json >/dev/null 2>&1; then
  if curl -fsS "http://127.0.0.1:$kc_port/realms/liv/.well-known/openid-configuration" >/dev/null 2>&1; then ok "realm liv importado"; else falla "realm liv importado"; fi
else
  printf '  pendiente  realm liv (llega con la tarea T07)\n'
fi

if [ "$fallas" -eq 0 ]; then
  echo "Entorno local listo."
  exit 0
fi
echo "$fallas verificaciones fallaron."
exit 1
