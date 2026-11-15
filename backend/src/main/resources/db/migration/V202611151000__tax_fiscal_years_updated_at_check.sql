-- Vigencias fiscales (007, B6): updated_at nunca es anterior a created_at. Complementa V202611141000, que ya está
-- aplicada y no se edita. liv_app conserva el UPDATE de updated_by y updated_at (el cambio de estado los escribe), así
-- que sin este CHECK una fila podía quedar con un cambio fechado antes de su propia creación. El adaptador de la
-- aplicación escribe updated_at con la hora de la base en el momento de la sentencia (clock_timestamp(), no now(): now()
-- es la hora de inicio de la transacción y una fila que otra transacción creó después de empezar esta tendría un
-- created_at posterior); el CHECK cubre el defecto o el abuso que escriba otro valor.
-- No es una garantía de autoría ni de la hora exacta del cambio: updated_by y updated_at los escribe la aplicación
-- (véase V202611141000). El registro de auditoría es el que da fe de quién y cuándo. Como la tabla todavía no la usa
-- ninguna versión de la aplicación, no hay filas que validar fuera de las pruebas.
ALTER TABLE tax.administration_fiscal_year
  ADD CONSTRAINT administration_fiscal_year_updated_at_check
  CHECK (updated_at IS NULL OR updated_at >= created_at);
