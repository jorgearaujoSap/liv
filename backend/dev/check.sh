#!/bin/sh
# Verifica que el entorno local del backend esté listo (tarea T09 de la feature 001).
# Uso, con el compose levantado:  sh dev/check.sh   (desde backend/)
# Sale con 0 si todo está bien y con 1 si alguna verificación falla.
set -u
cd "$(dirname "$0")"
[ -f .env ] && . ./.env

fallas=0
ok()    { printf '  ok     %s\n' "$1"; }
falla() { printf '  FALLA  %s\n' "$1"; fallas=$((fallas + 1)); }
psql_q() { docker compose exec -T postgres psql -U postgres -d "$1" -tAc "$2" 2>/dev/null; }

echo "PostgreSQL"
if docker compose exec -T postgres pg_isready -U postgres >/dev/null 2>&1; then ok "responde"; else falla "no responde"; fi

for db in liv keycloak; do
  if [ "$(psql_q postgres "SELECT 1 FROM pg_database WHERE datname = '$db'")" = "1" ]; then ok "base $db"; else falla "base $db"; fi
done

# Formato rol:login. Ningún rol puede saltarse la RLS (doc. 02 §5.2, ADR 0025).
for spec in liv_owner:true liv_app:true liv_bff_owner:true liv_bff:true keycloak:true \
            liv_identity_lookup:false liv_audit_platform:false; do
  rol=${spec%%:*}
  login=${spec##*:}
  fila=$(psql_q postgres "SELECT rolcanlogin::text || '/' || rolbypassrls::text FROM pg_roles WHERE rolname = '$rol'")
  if [ "$fila" = "$login/false" ]; then ok "rol $rol"; else falla "rol $rol (se esperaba login=$login y sin BYPASSRLS; hay '$fila')"; fi
done

if [ "$(psql_q liv "SELECT has_database_privilege('liv_app', 'liv', 'TEMP')")" = "f" ]; then ok "liv_app sin TEMP"; else falla "liv_app sin TEMP"; fi
if [ "$(psql_q liv "SELECT nspowner::regrole::text FROM pg_namespace WHERE nspname = 'bff'")" = "liv_bff_owner" ]; then ok "esquema bff de liv_bff_owner"; else falla "esquema bff de liv_bff_owner"; fi
if [ "$(psql_q liv "SELECT has_schema_privilege('liv_app', 'bff', 'USAGE')")" = "f" ]; then ok "liv_app sin acceso al esquema bff"; else falla "liv_app sin acceso al esquema bff"; fi

echo "Keycloak"
puerto=${KEYCLOAK_MANAGEMENT_PORT:-9100}
if curl -fsS "http://localhost:$puerto/health/ready" >/dev/null 2>&1; then ok "listo (health/ready, puerto $puerto)"; else falla "no está listo en el puerto $puerto"; fi

if [ "$fallas" -eq 0 ]; then
  echo "Entorno local listo."
  exit 0
fi
echo "$fallas verificaciones fallaron."
exit 1
