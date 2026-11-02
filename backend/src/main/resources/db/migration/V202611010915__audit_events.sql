-- Bitácora inmutable y marca de agua del importador (001 T10; ERD "AUDIT" + ajustes del plan; ADR 0025).
-- Los eventos de LIV (source = 'LIV') llevan la administración del contexto. Los eventos del proveedor de
-- identidad (source = 'IDENTITY_PROVIDER') los importa un trabajo de plataforma, y los rechazos sin
-- administración válida los guarda audit.record_platform_session_denied. Son los únicos dos caminos que
-- escriben filas de plataforma (administration_id NULL).

CREATE TABLE audit.audit_event (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID,
  administration_id UUID REFERENCES core.tax_administration (id),
  entity_name       VARCHAR(80) NOT NULL,
  entity_id         UUID,
  operation         VARCHAR(40) NOT NULL,
  previous_values   JSONB,
  new_values        JSONB,
  occurred_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  correlation_id    VARCHAR(80),
  source            VARCHAR(30)  NOT NULL CHECK (source IN ('LIV', 'IDENTITY_PROVIDER')),
  -- Idempotencia del consumidor: el mismo evento de la misma fuente no se guarda dos veces.
  source_event_id   VARCHAR(100) NOT NULL,
  -- Referencia opaca al actor cuando no hay un usuario de LIV (por ejemplo, un administrador del proveedor).
  actor_ref         VARCHAR(200),
  CONSTRAINT audit_event_source_key UNIQUE (source, source_event_id),
  -- Un usuario del evento pertenece a la administración del evento (ERD, hallazgo 2).
  FOREIGN KEY (user_id, administration_id) REFERENCES core.admin_user (id, administration_id),
  CHECK (user_id IS NULL OR administration_id IS NOT NULL)
);
CREATE INDEX audit_event_administration_idx ON audit.audit_event (administration_id, occurred_at);

CREATE TABLE audit.import_cursor (
  source         VARCHAR(30) PRIMARY KEY CHECK (source IN ('IDENTITY_PROVIDER')),
  last_event_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  -- Ids vistos dentro de la ventana de superposición (5 minutos), para no repetir eventos.
  seen_event_ids TEXT[] NOT NULL DEFAULT '{}',
  updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Solo inserción: ni siquiera el dueño modifica o borra (liv_app ya no tiene el permiso).
CREATE FUNCTION audit.reject_modification() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'audit.audit_event es de solo inserción (append-only)' USING ERRCODE = 'restrict_violation';
END
$$;
CREATE TRIGGER audit_event_append_only BEFORE UPDATE OR DELETE ON audit.audit_event
  FOR EACH ROW EXECUTE FUNCTION audit.reject_modification();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON audit.audit_event
  FOR EACH STATEMENT EXECUTE FUNCTION audit.reject_modification();

ALTER TABLE audit.audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.import_cursor ENABLE ROW LEVEL SECURITY;

-- Lectura: solo la administración del contexto. Las filas de plataforma no las lee liv_app hasta el menú 9.
CREATE POLICY administration_read ON audit.audit_event FOR SELECT TO liv_app
  USING (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid);
-- Inserción: la administración del contexto o, solo en un trabajo de plataforma marcado, ninguna.
CREATE POLICY administration_insert ON audit.audit_event FOR INSERT TO liv_app
  WITH CHECK (
    administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid
    OR (administration_id IS NULL AND current_setting('app.platform_job', true) = 'on'));
-- La marca de agua es de plataforma: solo un trabajo marcado la lee o la escribe.
CREATE POLICY platform_job ON audit.import_cursor FOR ALL TO liv_app
  USING (current_setting('app.platform_job', true) = 'on')
  WITH CHECK (current_setting('app.platform_job', true) = 'on');

GRANT SELECT, INSERT ON audit.audit_event TO liv_app;
GRANT SELECT, INSERT, UPDATE ON audit.import_cursor TO liv_app;

-- Idempotencia de las filas de plataforma: usar siempre ON CONFLICT DO NOTHING *sin* objetivo de conflicto. Con
-- objetivo (ON CONFLICT (source, source_event_id)), PostgreSQL exige que la fila nueva pase una política de lectura,
-- y las filas de plataforma no tienen ninguna para liv_app ni para el dueño de la función: fallaría con un error de RLS.

-- Rechazos de inicio de sesión sin administración válida (no existe, o falta el claim): no se pueden guardar con
-- el contexto de una administración. El dueño (liv_audit_platform, sin inicio de sesión ni BYPASSRLS) solo puede
-- insertar esta clase de fila: la política lo exige, aunque alguien cambie el cuerpo de la función.
CREATE FUNCTION audit.record_platform_session_denied(
  p_source_event_id text, p_correlation_id text, p_new_values jsonb, p_actor_ref text)
RETURNS void
LANGUAGE sql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, audit, pg_temp
AS $$
  INSERT INTO audit.audit_event (administration_id, entity_name, operation, source, source_event_id,
                                 correlation_id, new_values, actor_ref)
  VALUES (NULL, 'session', 'SESSION_DENIED', 'LIV', p_source_event_id, p_correlation_id, p_new_values,
          p_actor_ref)
  ON CONFLICT DO NOTHING
$$;

CREATE POLICY platform_session_denied ON audit.audit_event FOR INSERT TO liv_audit_platform
  WITH CHECK (administration_id IS NULL AND source = 'LIV' AND operation = 'SESSION_DENIED');

-- Los permisos se fijan antes del cambio de dueño, para no tener que asumir el rol después.
REVOKE ALL ON FUNCTION audit.record_platform_session_denied(text, text, jsonb, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.record_platform_session_denied(text, text, jsonb, text) TO liv_app;
GRANT USAGE ON SCHEMA audit TO liv_audit_platform;
GRANT INSERT ON audit.audit_event TO liv_audit_platform;

-- Igual que en V202611010905: CREATE en el esquema solo mientras se cambia el dueño.
GRANT CREATE ON SCHEMA audit TO liv_audit_platform;
ALTER FUNCTION audit.record_platform_session_denied(text, text, jsonb, text) OWNER TO liv_audit_platform;
REVOKE CREATE ON SCHEMA audit FROM liv_audit_platform;
