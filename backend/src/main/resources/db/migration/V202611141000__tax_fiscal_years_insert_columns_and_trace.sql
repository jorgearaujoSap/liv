-- Vigencias fiscales (007, B3b): el INSERT de liv_app por columnas y la coherencia de la traza de cambio. Ajusta
-- V202611131000, que ya está aplicada y no se edita, dentro de la decisión H2-4 (permisos mínimos de liv_app). La tabla
-- todavía no la usa ninguna versión de la aplicación y no tiene filas fuera de las pruebas: el cambio no rompe la
-- versión en ejecución, y el CHECK de abajo no tiene nada que validar. Cualquier PR que escriba en la tabla se
-- integra después de esta migración.

-- liv_app crea la vigencia con tres columnas: administración, año y autor. El estado nace en PLANEADA, la habilitación en
-- falso y el id y created_at los fija la base (uuidv7() y now()); updated_by y updated_at los escribe el cambio de estado.
-- La entidad que inserta la fila tiene que dejar fuera de su INSERT todas las demás columnas, también updated_by y
-- updated_at (PostgreSQL exige el permiso de cada columna nombrada, aunque el valor sea NULL).
-- Con el INSERT de tabla, un defecto podía fijar el id, fechar hacia atrás created_at o crear la fila con una traza de
-- cambio inventada. La política de INSERT (solo PLANEADA) queda como segunda barrera: con este permiso la aplicación no
-- puede ni nombrar status, y esa condición solo la cubre la comparación textual de las políticas en
-- FiscalYearSchemaTest. Se quita primero el permiso de tabla porque REVOKE de tabla también quita los de columna.
REVOKE INSERT ON tax.administration_fiscal_year FROM liv_app;
GRANT INSERT (administration_id, fiscal_year, created_by) ON tax.administration_fiscal_year TO liv_app;

-- Una vigencia que se abrió dice quién y cuándo hizo el último cambio: la política de UPDATE admite pasar a
-- EN_PREPARACION, pero no exige esas dos columnas. Solo cubre EN_PREPARACION, el único estado que la 007 escribe. La
-- feature de la opción 1.5.1 reemplaza este CHECK (DROP CONSTRAINT) por el suyo, para los estados que escriba, y decide
-- si el disparador BEFORE UPDATE que propone V202611131000 (OLD.status y NEW.status; todavía no existe) exige además
-- que cada transición vuelva a escribir la traza. No es una garantía de autoría: updated_by y updated_at los escribe
-- la aplicación (liv_app conserva su UPDATE) y no se comprueba su valor. El registro de auditoría es el que da fe de
-- quién hizo cada cambio; la doble firma de la 1.5.1 no puede apoyarse en estas columnas, porque cada cambio las
-- sobrescribe.
ALTER TABLE tax.administration_fiscal_year
  ADD CONSTRAINT administration_fiscal_year_opened_trace_check
  CHECK (status <> 'EN_PREPARACION' OR (updated_by IS NOT NULL AND updated_at IS NOT NULL));
