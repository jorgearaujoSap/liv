-- Entidades territoriales, administraciones, usuarios y sesiones (001 T10; ERD "CORE" + ajustes del plan).
-- RLS: ENABLE y no FORCE; el dueño (Flyway) no pasa por las políticas, la aplicación (liv_app) sí.
-- La política lee la administración registrada del contexto de la transacción (doc. 02 §5.1); sin contexto,
-- o con el contexto vacío que deja una conexión del pool, NULLIF lo vuelve NULL y no se ve ninguna fila.

CREATE TABLE core.territorial_entity (
  id          UUID PRIMARY KEY,
  dane_code   VARCHAR(20)  NOT NULL UNIQUE,
  name        VARCHAR(150) NOT NULL,
  entity_type VARCHAR(20)  NOT NULL CHECK (entity_type IN ('DEPARTAMENTO', 'DISTRITO', 'MUNICIPIO')),
  parent_id   UUID REFERENCES core.territorial_entity (id),
  active      BOOLEAN      NOT NULL DEFAULT true
);

CREATE TABLE core.tax_administration (
  id                    UUID PRIMARY KEY,
  territorial_entity_id UUID         NOT NULL REFERENCES core.territorial_entity (id),
  name                  VARCHAR(150) NOT NULL,
  nit                   VARCHAR(20),
  address               VARCHAR(200),
  active                BOOLEAN      NOT NULL DEFAULT true
);
CREATE INDEX tax_administration_territorial_entity_idx ON core.tax_administration (territorial_entity_id);

CREATE TABLE core.admin_user (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  administration_id UUID         NOT NULL REFERENCES core.tax_administration (id),
  full_name         VARCHAR(150) NOT NULL,
  email             VARCHAR(200) NOT NULL,
  status            VARCHAR(30)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
  identity_issuer   VARCHAR(255) NOT NULL,
  identity_subject  VARCHAR(255) NOT NULL,
  last_login_at     TIMESTAMP WITH TIME ZONE,
  CONSTRAINT admin_user_identity_key UNIQUE (identity_issuer, identity_subject),
  -- Destino de las FK compuestas: una fila hija no puede apuntar a un usuario de otra administración.
  CONSTRAINT admin_user_id_administration_key UNIQUE (id, administration_id)
);
CREATE INDEX admin_user_administration_idx ON core.admin_user (administration_id);

CREATE TABLE core.user_session (
  sid               VARCHAR(100) PRIMARY KEY,
  user_id           UUID         NOT NULL,
  administration_id UUID         NOT NULL,
  identity_issuer   VARCHAR(255) NOT NULL,
  identity_subject  VARCHAR(255) NOT NULL,
  started_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  ended_at          TIMESTAMP WITH TIME ZONE,
  end_reason        VARCHAR(20) CHECK (end_reason IN ('LOGOUT', 'IDLE', 'ABSOLUTE', 'PROVIDER')),
  FOREIGN KEY (user_id, administration_id) REFERENCES core.admin_user (id, administration_id),
  CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);
CREATE INDEX user_session_user_idx ON core.user_session (user_id, administration_id);

ALTER TABLE core.tax_administration ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.admin_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE core.user_session ENABLE ROW LEVEL SECURITY;

CREATE POLICY administration_isolation ON core.tax_administration FOR SELECT TO liv_app
  USING (id = NULLIF(current_setting('app.administration_id', true), '')::uuid);
CREATE POLICY administration_isolation ON core.admin_user FOR ALL TO liv_app
  USING (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid)
  WITH CHECK (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid);
CREATE POLICY administration_isolation ON core.user_session FOR ALL TO liv_app
  USING (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid)
  WITH CHECK (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid);

-- La entidad territorial es un catálogo nacional: la lee toda la aplicación y solo la escribe la carga de catálogos.
GRANT SELECT ON core.territorial_entity, core.tax_administration TO liv_app;
GRANT SELECT, INSERT ON core.admin_user, core.user_session TO liv_app;
GRANT UPDATE (last_login_at) ON core.admin_user TO liv_app;
GRANT UPDATE (ended_at, end_reason) ON core.user_session TO liv_app;

-- Búsqueda de identidad antes de conocer la administración (001-17; ADR 0025). Necesita ver a todos los usuarios,
-- por eso es SECURITY DEFINER de un rol propio sin inicio de sesión y sin BYPASSRLS, con una política solo para él.
-- search_path fijo con pg_temp al final (documentación de PostgreSQL sobre SECURITY DEFINER).
CREATE FUNCTION core.find_user_by_identity(p_issuer text, p_subject text)
RETURNS TABLE (user_id uuid, administration_id uuid, status varchar)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = pg_catalog, core, pg_temp
AS $$
  SELECT u.id, u.administration_id, u.status
    FROM core.admin_user u
   WHERE u.identity_issuer = p_issuer AND u.identity_subject = p_subject
$$;

-- Los permisos se fijan antes de cambiar el dueño: después, liv_owner ya no puede modificar la función.
REVOKE ALL ON FUNCTION core.find_user_by_identity(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.find_user_by_identity(text, text) TO liv_app;

GRANT USAGE ON SCHEMA core TO liv_identity_lookup;
GRANT SELECT (id, administration_id, status, identity_issuer, identity_subject)
  ON core.admin_user TO liv_identity_lookup;
CREATE POLICY identity_lookup ON core.admin_user FOR SELECT TO liv_identity_lookup USING (true);

-- Cambiar el dueño exige poder asumir el rol (initdb lo concede a liv_owner) y que el rol pueda crear en el esquema.
GRANT CREATE ON SCHEMA core TO liv_identity_lookup;
ALTER FUNCTION core.find_user_by_identity(text, text) OWNER TO liv_identity_lookup;
REVOKE CREATE ON SCHEMA core FROM liv_identity_lookup;
