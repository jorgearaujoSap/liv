-- Vigencias fiscales (007, B3b): el INSERT de liv_app por columnas y la coherencia de la traza de cambio. Corrige dos
-- hallazgos de la revisión de V202611131000 (decisión H2-4: permisos mínimos de liv_app), que ya está aplicada y no se
-- edita. La tabla todavía no la usa ninguna versión de la aplicación: el cambio no rompe la que está en ejecución.

-- liv_app crea la vigencia con tres columnas: administración, año y autor. El estado nace en PLANEADA, la habilitación en
-- falso y el id y created_at los fija la base (uuidv7() y now()); updated_by y updated_at los escribe el cambio de estado.
-- Con el INSERT de tabla, un defecto podía fijar el id, fechar hacia atrás created_at o crear la fila con una traza de
-- cambio inventada. La política de INSERT (solo PLANEADA) sigue siendo la segunda barrera. Se quita primero el permiso de
-- tabla porque REVOKE de tabla también quita los de columna.
REVOKE INSERT ON tax.administration_fiscal_year FROM liv_app;
GRANT INSERT (administration_id, fiscal_year, created_by) ON tax.administration_fiscal_year TO liv_app;

-- Una vigencia que se abrió siempre dice quién y cuándo: la política de UPDATE admite pasar a EN_PREPARACION, pero no
-- exige la traza, y esa traza es la que necesitará la doble firma de la opción 1.5.1. Solo cubre EN_PREPARACION, el único
-- estado que la 007 escribe; la 1.5.1 la amplía a los suyos con su migración.
ALTER TABLE tax.administration_fiscal_year
  ADD CONSTRAINT administration_fiscal_year_opened_trace_check
  CHECK (status <> 'EN_PREPARACION' OR (updated_by IS NOT NULL AND updated_at IS NOT NULL));
