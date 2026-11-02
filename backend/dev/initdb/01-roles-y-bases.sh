#!/bin/bash
# Roles y bases del entorno local (doc. 02 §5.2, ADR 0025). La imagen de PostgreSQL lo ejecuta solo al crear
# el volumen por primera vez; para repetirlo: docker compose -f dev/compose.yaml --env-file dev/.env down -v
# En AWS estos roles los crea la infraestructura como código, no este script.
set -euo pipefail

psql -v ON_ERROR_STOP=1 -U postgres -d postgres \
  -v liv_owner_pw="$LIV_OWNER_PASSWORD" \
  -v liv_app_pw="$LIV_APP_PASSWORD" \
  -v liv_bff_owner_pw="$LIV_BFF_OWNER_PASSWORD" \
  -v liv_bff_pw="$LIV_BFF_PASSWORD" \
  -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
-- Ningún rol tiene BYPASSRLS ni privilegios de administración. (Que los dueños de tablas también queden
-- sujetos a la RLS depende de FORCE ROW LEVEL SECURITY, que deciden las migraciones de T10.)
CREATE ROLE liv_owner           LOGIN   NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'liv_owner_pw';     -- dueño de los objetos; solo Flyway
CREATE ROLE liv_app             LOGIN   NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'liv_app_pw';       -- aplicación (API y worker)
CREATE ROLE liv_bff_owner       LOGIN   NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'liv_bff_owner_pw'; -- dueño del esquema bff; migraciones del BFF
CREATE ROLE liv_bff             LOGIN   NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'liv_bff_pw';       -- aplicación BFF
CREATE ROLE liv_identity_lookup NOLOGIN NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE;                               -- dueño de core.find_user_by_identity (T10)
CREATE ROLE liv_audit_platform  NOLOGIN NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE;                               -- dueño de audit.record_platform_session_denied (T10)
CREATE ROLE keycloak            LOGIN   NOBYPASSRLS NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'keycloak_pw';      -- base propia de Keycloak

CREATE DATABASE liv OWNER liv_owner;
CREATE DATABASE keycloak OWNER keycloak;

-- Las bases del sistema tampoco quedan abiertas a PUBLIC (sin CONNECT ni TEMP).
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;
SQL

psql -v ON_ERROR_STOP=1 -U postgres -d liv <<'SQL'
-- Revoca CONNECT y TEMP a PUBLIC; cada rol recibe solo lo que necesita.
REVOKE ALL ON DATABASE liv FROM PUBLIC;
GRANT CONNECT ON DATABASE liv TO liv_app, liv_bff_owner, liv_bff;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- Toda función nace sin EXECUTE para PUBLIC: las SECURITY DEFINER de T10 solo las ejecuta quien reciba el permiso.
ALTER DEFAULT PRIVILEGES FOR ROLE liv_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- Esquema del BFF: solo sus roles lo ven; liv_app no tiene acceso (ADR 0025). Los permisos de liv_app sobre
-- los esquemas de LIV los conceden las migraciones de T10, objeto por objeto.
CREATE SCHEMA bff AUTHORIZATION liv_bff_owner;
GRANT USAGE ON SCHEMA bff TO liv_bff;
ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO liv_bff;
ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff
  GRANT USAGE, SELECT ON SEQUENCES TO liv_bff;
SQL

psql -v ON_ERROR_STOP=1 -U postgres -d keycloak <<'SQL'
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
