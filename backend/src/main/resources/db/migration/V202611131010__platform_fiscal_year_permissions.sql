-- Permisos de acción de las vigencias fiscales (007, T6; decisión H2-3 y Doc. 01 v0.5, §5): crear una vigencia y abrir
-- su preparación, solo para ADMIN_SISTEMA. El permiso de lectura (tax.fiscal-year.read) ya existe (V202611010910).
-- Un permiso nuevo es una migración nueva, como cualquier dato de configuración; la matriz completa la fija
-- PlatformTablesTest.permissionMatrixIsTheApprovedOne, escrita aparte de esta migración.

INSERT INTO platform.app_permission (code, description) VALUES
  ('tax.fiscal-year.create', 'Crear una vigencia fiscal (1.4)'),
  ('tax.fiscal-year.prepare', 'Abrir la preparación de una vigencia fiscal (1.4)');

INSERT INTO platform.role_permission (role_code, permission_code) VALUES
  ('ADMIN_SISTEMA', 'tax.fiscal-year.create'),
  ('ADMIN_SISTEMA', 'tax.fiscal-year.prepare');
