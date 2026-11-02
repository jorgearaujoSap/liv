-- Roles y permisos de la aplicación (001 T10; plan 001, matriz inicial D-001-5 aprobada en H2; doc. 02 §4.2).
-- Son datos de referencia globales (no pertenecen a una administración): sin RLS, liv_app solo los lee.
-- Un permiso nuevo o un cambio de la matriz es una migración nueva, como cualquier dato de configuración.

CREATE TABLE platform.app_role (
  code        VARCHAR(40)  PRIMARY KEY,
  description VARCHAR(200) NOT NULL
);

CREATE TABLE platform.app_permission (
  -- <modulo>.<recurso>.<accion>
  code        VARCHAR(80)  PRIMARY KEY,
  description VARCHAR(200) NOT NULL
);

CREATE TABLE platform.role_permission (
  role_code       VARCHAR(40) NOT NULL REFERENCES platform.app_role (code),
  permission_code VARCHAR(80) NOT NULL REFERENCES platform.app_permission (code),
  PRIMARY KEY (role_code, permission_code)
);
CREATE INDEX role_permission_permission_idx ON platform.role_permission (permission_code);

INSERT INTO platform.app_role (code, description) VALUES
  ('ADMIN_SISTEMA', 'Administración del sistema; no aprueba lotes por defecto'),
  ('CARGADOR_TABLAS', 'Carga las tablas base anuales'),
  ('APROBADOR_TABLAS', 'Aprueba lo que otro cargó'),
  ('LIQUIDADOR', 'Crea corridas y liquidaciones individuales'),
  ('APROBADOR_LIQUIDACION', 'Aprueba corridas de liquidación; distinto del creador'),
  ('RECAUDO', 'Registra y concilia el recaudo'),
  ('TESORERIA', 'Aprueba lotes de recaudo y conciliación'),
  ('APROBADOR_RECAUDO', 'Reversos, reimputaciones y devoluciones'),
  ('JURIDICO', 'Aprueba configuraciones de jurisdicción'),
  ('CONSULTA', 'Solo lectura de su administración'),
  ('AUDITOR', 'Lectura total de su administración, bitácora y archivos originales');

INSERT INTO platform.app_permission (code, description) VALUES
  ('core.administration.read', 'Entidades y administraciones (1.1)'),
  ('core.user.read', 'Usuarios, roles y permisos (1.2)'),
  ('documents.legal-instrument.read', 'Normas y soportes (1.3)'),
  ('tax.fiscal-year.read', 'Vigencias fiscales (1.4)'),
  ('valuation.batch.read', 'Cargue de tablas base (1.5)'),
  ('tax.rate.read', 'Tarifas del cargue de tablas base (1.5)'),
  ('jurisdiction.config.read', 'Configuración de jurisdicción (1.5.9)'),
  ('valuation.catalog.read', 'Consultas y exportación (1.6)'),
  ('valuation.vehicle-reference.read', 'Referencias vehiculares (1.7)'),
  ('core.parameter.read', 'Parámetros generales (1.8)'),
  ('core.vehicle.read', 'Registro automotor y contribuyentes (2)'),
  ('tax.assessment.read', 'Liquidación (3)'),
  ('ledger.obligation.read', 'Cuenta corriente y recaudo (4)'),
  ('portfolio.balance.read', 'Cartera (5)'),
  ('collection.case.read', 'Cobro (6)'),
  ('enforcement.measure.read', 'Medidas cautelares (7)'),
  ('documents.document.read', 'Documentos y notificaciones (8)'),
  ('audit.event.read', 'Auditoría e integraciones (9)');

-- Los cinco roles con acceso al Módulo 1 de tablas base (doc. 01 §4).
INSERT INTO platform.role_permission (role_code, permission_code)
SELECT r.code, p.code
  FROM (VALUES ('ADMIN_SISTEMA'), ('CARGADOR_TABLAS'), ('APROBADOR_TABLAS'), ('CONSULTA'), ('AUDITOR')) r (code)
  CROSS JOIN (VALUES ('documents.legal-instrument.read'), ('tax.fiscal-year.read'),
                     ('valuation.batch.read'), ('tax.rate.read'), ('valuation.catalog.read'),
                     ('valuation.vehicle-reference.read')) p (code);

INSERT INTO platform.role_permission (role_code, permission_code) VALUES
  ('ADMIN_SISTEMA', 'core.administration.read'), ('AUDITOR', 'core.administration.read'),
  ('ADMIN_SISTEMA', 'core.user.read'), ('AUDITOR', 'core.user.read'),
  ('ADMIN_SISTEMA', 'core.parameter.read'), ('AUDITOR', 'core.parameter.read'),
  ('ADMIN_SISTEMA', 'audit.event.read'), ('AUDITOR', 'audit.event.read'),
  ('ADMIN_SISTEMA', 'jurisdiction.config.read'), ('CARGADOR_TABLAS', 'jurisdiction.config.read'),
  ('APROBADOR_TABLAS', 'jurisdiction.config.read'), ('JURIDICO', 'jurisdiction.config.read'),
  ('AUDITOR', 'jurisdiction.config.read'),
  ('LIQUIDADOR', 'core.vehicle.read'), ('APROBADOR_LIQUIDACION', 'core.vehicle.read'),
  ('RECAUDO', 'core.vehicle.read'), ('CONSULTA', 'core.vehicle.read'), ('AUDITOR', 'core.vehicle.read'),
  ('LIQUIDADOR', 'tax.assessment.read'), ('APROBADOR_LIQUIDACION', 'tax.assessment.read'),
  ('CONSULTA', 'tax.assessment.read'), ('AUDITOR', 'tax.assessment.read'),
  ('RECAUDO', 'ledger.obligation.read'), ('TESORERIA', 'ledger.obligation.read'),
  ('APROBADOR_RECAUDO', 'ledger.obligation.read'), ('CONSULTA', 'ledger.obligation.read'),
  ('AUDITOR', 'ledger.obligation.read'),
  ('RECAUDO', 'portfolio.balance.read'), ('TESORERIA', 'portfolio.balance.read'),
  ('APROBADOR_RECAUDO', 'portfolio.balance.read'), ('JURIDICO', 'portfolio.balance.read'),
  ('CONSULTA', 'portfolio.balance.read'), ('AUDITOR', 'portfolio.balance.read'),
  ('JURIDICO', 'collection.case.read'), ('AUDITOR', 'collection.case.read'),
  ('JURIDICO', 'enforcement.measure.read'), ('AUDITOR', 'enforcement.measure.read');

-- "Documentos y notificaciones" lo ven todos los roles.
INSERT INTO platform.role_permission (role_code, permission_code)
SELECT code, 'documents.document.read' FROM platform.app_role;

GRANT SELECT ON platform.app_role, platform.app_permission, platform.role_permission TO liv_app;
