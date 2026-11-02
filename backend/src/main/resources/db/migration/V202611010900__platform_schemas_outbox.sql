-- Esquemas de LIV que usa la feature 001 y la tabla del outbox de Spring Modulith (001 T10; plan, "Datos, API y seguridad").
-- Los demás esquemas del ERD (documents, valuation, tax, ledger...) llegan con sus features.
-- Corre como liv_owner (dueño de los objetos y de la base). Esta migración no depende del script de desarrollo
-- (backend/dev/initdb): en AWS los roles los crea la infraestructura como código, y aquí se comprueba que
-- existan como se necesitan y se fija el endurecimiento que le corresponde al dueño.

-- Contrato de roles (doc. 02 §5.2, ADR 0025): si la infraestructura no lo cumple, la migración se detiene con un
-- mensaje claro en lugar de fallar más adelante. Requiere PostgreSQL 16 o superior (GRANT ... WITH SET).
DO $$
BEGIN
  IF (SELECT count(*) FROM pg_roles
       WHERE rolname IN ('liv_app', 'liv_identity_lookup', 'liv_audit_platform')) <> 3 THEN
    RAISE EXCEPTION 'Faltan roles de base de datos: liv_app, liv_identity_lookup y liv_audit_platform (doc. 02 §5.2)';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'liv_app' AND (rolbypassrls OR rolsuper)) THEN
    RAISE EXCEPTION 'liv_app no puede tener BYPASSRLS ni ser superusuario (doc. 02 §5.1)';
  END IF;
  IF to_regnamespace('bff') IS NOT NULL AND has_schema_privilege('liv_app', 'bff', 'USAGE') THEN
    RAISE EXCEPTION 'liv_app no puede tener acceso al esquema bff (ADR 0025)';
  END IF;
  IF NOT pg_has_role(current_user, 'liv_identity_lookup', 'SET')
     OR NOT pg_has_role(current_user, 'liv_audit_platform', 'SET') THEN
    RAISE EXCEPTION '% debe poder asumir liv_identity_lookup y liv_audit_platform: '
      'GRANT liv_identity_lookup, liv_audit_platform TO % WITH INHERIT FALSE, SET TRUE (backend/dev/README.md)',
      current_user, current_user;
  END IF;
END
$$;

-- La aplicación no crea tablas temporales ni objetos en public, y las funciones nacen sin EXECUTE para PUBLIC.
DO $$
BEGIN
  EXECUTE format('REVOKE TEMPORARY ON DATABASE %I FROM PUBLIC, liv_app', current_database());
END
$$;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE liv_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE SCHEMA platform;
CREATE SCHEMA core;
CREATE SCHEMA audit;

GRANT USAGE ON SCHEMA platform, core, audit TO liv_app;

-- Outbox (spring-modulith-events-jdbc 2.1.1, esquema v2 de PostgreSQL, sin la tabla de archivo: los eventos
-- completados se marcan, no se archivan). Al agregar spring-modulith-starter-jdbc (T12) hay que configurar
-- spring.modulith.events.jdbc.schema=platform.
-- Excepción a la RLS de doc. 02 §5.1: no tiene administration_id y guarda eventos de todas las administraciones
-- en serialized_event, por eso los eventos solo deben llevar identificadores (ni nombres ni correos). liv_app no
-- borra ni reescribe eventos: solo los inserta y actualiza las columnas de finalización y reenvío.
CREATE TABLE platform.event_publication (
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx
  ON platform.event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx
  ON platform.event_publication (completion_date);

GRANT SELECT, INSERT ON platform.event_publication TO liv_app;
GRANT UPDATE (completion_date, status, completion_attempts, last_resubmission_date)
  ON platform.event_publication TO liv_app;
