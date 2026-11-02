-- Esquemas de LIV que usa la feature 001 y la tabla del outbox de Spring Modulith (001 T10; plan, "Datos, API y seguridad").
-- Los demás esquemas del ERD (documents, valuation, tax, ledger...) llegan con sus features.
-- Corre como liv_owner (dueño de los objetos). liv_app no tiene BYPASSRLS, TEMP ni acceso al esquema bff (initdb);
-- cada migración le concede, objeto por objeto, solo lo que necesita.

CREATE SCHEMA platform;
CREATE SCHEMA core;
CREATE SCHEMA audit;

GRANT USAGE ON SCHEMA platform, core, audit TO liv_app;

-- Outbox (spring-modulith-events-jdbc 2.1.1, esquema v2 de PostgreSQL, sin la tabla de archivo: los eventos
-- completados se marcan, no se archivan). Los eventos no pertenecen a una administración, así que no llevan RLS.
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

GRANT SELECT, INSERT, UPDATE, DELETE ON platform.event_publication TO liv_app;
