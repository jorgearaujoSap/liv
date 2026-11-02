-- Bitácora de solo inserción y marca de agua del importador (001 T10; ERD "AUDIT" + ajustes del plan; ADR 0025).
-- Los eventos de LIV (source = 'LIV') llevan la administración del contexto. Los eventos del proveedor de
-- identidad (source = 'IDENTITY_PROVIDER') los importa un trabajo de plataforma, y los rechazos sin
-- administración válida los guarda audit.record_platform_session_denied. Son los únicos dos caminos que
-- escriben filas de plataforma (administration_id NULL).
--
-- Los identificadores de evento (source_event_id) deben ser UUID o ids del proveedor que nadie pueda adivinar: la
-- unicidad es global y un repetido en otra administración se descartaría en silencio.

CREATE TABLE audit.audit_event (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID,
  administration_id UUID REFERENCES core.tax_administration (id),
  entity_name       VARCHAR(80) NOT NULL,
  entity_id         UUID,
  operation         VARCHAR(40) NOT NULL,
  previous_values   JSONB CHECK (length(previous_values::text) <= 4096),
  -- {result, reason, truncated_ip, masked_attempted_username}: el tamaño máximo evita volcados de datos personales.
  new_values        JSONB CHECK (length(new_values::text) <= 4096),
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
CREATE INDEX audit_event_user_idx ON audit.audit_event (administration_id, user_id) WHERE user_id IS NOT NULL;

CREATE TABLE audit.import_cursor (
  source         VARCHAR(30) PRIMARY KEY CHECK (source IN ('IDENTITY_PROVIDER')),
  last_event_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  -- Ids vistos dentro de la ventana de superposición (5 minutos), para no repetir eventos.
  seen_event_ids TEXT[] NOT NULL DEFAULT '{}',
  updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Solo inserción: liv_app no tiene UPDATE, DELETE ni TRUNCATE, y este disparador rechaza el cambio de datos incluso
-- al dueño. No protege contra el DDL del dueño (DISABLE TRIGGER, DROP TABLE): eso lo contienen la revisión de las
-- migraciones y, más adelante, la cadena de hashes de doc. 02 §9 (requiere un ADR).
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
-- Inserción: la administración del contexto o, solo en un trabajo de plataforma marcado y solo para eventos del
-- proveedor de identidad, ninguna. La marca es una convención de la aplicación (cualquier sesión de liv_app puede
-- fijarla): debe fijarse con alcance de transacción (set_config(..., true)); una marca de sesión sobreviviría en
-- una conexión del pool. Riesgo residual registrado en el PR; los rechazos de LIV pasan por la función de abajo.
CREATE POLICY administration_insert ON audit.audit_event FOR INSERT TO liv_app
  WITH CHECK (
    administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid
    OR (administration_id IS NULL AND source = 'IDENTITY_PROVIDER'
        AND current_setting('app.platform_job', true) = 'on'));
-- La marca de agua es de plataforma: solo un trabajo marcado la lee o la escribe.
CREATE POLICY platform_job ON audit.import_cursor FOR ALL TO liv_app
  USING (current_setting('app.platform_job', true) = 'on')
  WITH CHECK (current_setting('app.platform_job', true) = 'on');

GRANT SELECT, INSERT ON audit.audit_event TO liv_app;
GRANT SELECT, INSERT, UPDATE ON audit.import_cursor TO liv_app;

-- Idempotencia de las filas de plataforma: usar siempre INSERT ... ON CONFLICT DO NOTHING *sin* objetivo de
-- conflicto y sin RETURNING, con id y occurred_at generados por la aplicación. Con objetivo (ON CONFLICT (source,
-- source_event_id)) o con RETURNING, PostgreSQL exige que la fila pase una política de lectura, y las filas de
-- plataforma no tienen ninguna para liv_app ni para el dueño de la función: fallaría con un error de RLS.

-- Rechazos de inicio de sesión sin administración válida (no existe, o falta el claim): no se pueden guardar con
-- el contexto de una administración. El dueño (liv_audit_platform, sin inicio de sesión ni BYPASSRLS) solo puede
-- insertar esta clase de fila: la política lo exige, aunque alguien cambie el cuerpo de la función. La función
-- además valida lo que guarda: lista blanca de claves de new_values (plan 001, paso 9), longitudes y fecha.
CREATE FUNCTION audit.record_platform_session_denied(
  p_source_event_id text, p_occurred_at timestamptz, p_correlation_id text, p_new_values jsonb,
  p_actor_ref text)
RETURNS void
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, audit, pg_temp
AS $$
BEGIN
  IF length(p_source_event_id) > 100 OR length(p_correlation_id) > 80 OR length(p_actor_ref) > 200 THEN
    RAISE EXCEPTION 'Identificador demasiado largo';
  END IF;
  IF p_occurred_at > now() + interval '5 minutes' THEN
    RAISE EXCEPTION 'La fecha del evento es futura';
  END IF;
  IF jsonb_typeof(p_new_values) <> 'object' THEN
    RAISE EXCEPTION 'new_values fuera de la lista blanca: debe ser un objeto';
  END IF;
  IF EXISTS (SELECT 1 FROM jsonb_object_keys(p_new_values) k
              WHERE k NOT IN ('result', 'reason', 'truncated_ip', 'masked_attempted_username')) THEN
    RAISE EXCEPTION 'new_values fuera de la lista blanca: result, reason, truncated_ip, masked_attempted_username';
  END IF;
  INSERT INTO audit.audit_event (administration_id, entity_name, operation, source, source_event_id,
                                 occurred_at, correlation_id, new_values, actor_ref)
  VALUES (NULL, 'session', 'SESSION_DENIED', 'LIV', p_source_event_id, p_occurred_at, p_correlation_id,
          p_new_values, p_actor_ref)
  ON CONFLICT DO NOTHING;
END
$$;

CREATE POLICY platform_session_denied ON audit.audit_event FOR INSERT TO liv_audit_platform
  WITH CHECK (administration_id IS NULL AND source = 'LIV' AND operation = 'SESSION_DENIED');

-- Los permisos se fijan antes del cambio de dueño, para no tener que asumir el rol después.
REVOKE ALL ON FUNCTION audit.record_platform_session_denied(text, timestamptz, text, jsonb, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.record_platform_session_denied(text, timestamptz, text, jsonb, text) TO liv_app;
GRANT USAGE ON SCHEMA audit TO liv_audit_platform;
GRANT INSERT ON audit.audit_event TO liv_audit_platform;

-- Igual que en V202611010905: CREATE en el esquema solo mientras se cambia el dueño.
GRANT CREATE ON SCHEMA audit TO liv_audit_platform;
ALTER FUNCTION audit.record_platform_session_denied(text, timestamptz, text, jsonb, text)
  OWNER TO liv_audit_platform;
REVOKE CREATE ON SCHEMA audit FROM liv_audit_platform;
