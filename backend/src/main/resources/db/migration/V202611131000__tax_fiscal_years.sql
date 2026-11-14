-- Esquema tax: el año fiscal nacional y la vigencia de cada administración (007, T5; ERD 2.3, esquema TAX y plan,
-- paso 3). El año y sus fechas son de todos; el estado de la vigencia y la habilitación de la liquidación son de
-- cada administración, así que habilitar la liquidación en una no la habilita en otra.
-- Corre como liv_owner. Las demás tablas de tax (tarifas, calendario, liquidaciones...) llegan con sus features.
-- Autorizada con la lista de aceptación previa a Flyway del ERD abierta (decisión H2-1 de la 007): estas dos tablas
-- no tienen tarifas, montos ni reglas de cálculo.

-- La migración V202611010900 solo exige PostgreSQL 16, y esta necesita 18: las claves se generan con uuidv7()
-- (docs/arquitectura/01 §9). Si falta, se detiene con un mensaje claro y no con "function uuidv7() does not exist".
DO $$
BEGIN
  IF to_regprocedure('pg_catalog.uuidv7()') IS NULL THEN
    RAISE EXCEPTION 'Se requiere PostgreSQL 18 o superior: falta uuidv7()';
  END IF;
END
$$;

CREATE SCHEMA tax;
GRANT USAGE ON SCHEMA tax TO liv_app;

-- Año fiscal: dato nacional de referencia, sin administration_id ni RLS (doc. 02 §5.1; mismo criterio que
-- core.territorial_entity y platform.app_role). liv_app solo la lee: la llena esta migración y la amplía otra.
CREATE TABLE tax.fiscal_year (
  year      SMALLINT PRIMARY KEY,
  starts_on DATE NOT NULL,
  ends_on   DATE NOT NULL,
  -- V-01: el año fiscal es el año calendario (H2-2).
  CONSTRAINT fiscal_year_starts_on_check CHECK (starts_on = make_date(year, 1, 1)),
  CONSTRAINT fiscal_year_ends_on_check CHECK (ends_on = make_date(year, 12, 31))
);

-- Rango técnico y provisional, de 1999 a 2100 (H2-2). No decide qué vigencias históricas se cargarán (pendiente 5
-- del Doc. 01 §11); alcanza mientras el año en curso sea 2099 o anterior, porque V-05 permite crear hasta el año
-- siguiente. Ampliarlo es una migración nueva, y hay que cambiar a la vez el contrato (minimum y maximum de year).
INSERT INTO tax.fiscal_year (year, starts_on, ends_on)
SELECT y, make_date(y, 1, 1), make_date(y, 12, 31) FROM generate_series(1999, 2100) AS y;

-- Vigencia de una administración: una fila por administración y año.
CREATE TABLE tax.administration_fiscal_year (
  id                  UUID PRIMARY KEY DEFAULT uuidv7(),
  administration_id   UUID        NOT NULL REFERENCES core.tax_administration (id),
  fiscal_year         SMALLINT    NOT NULL REFERENCES tax.fiscal_year (year),
  -- Los códigos del Doc. 01 §5. La 007 solo escribe PLANEADA y EN_PREPARACION (lo fuerza la RLS de abajo); los otros
  -- tres los usa la feature de la opción 1.5.1.
  status              VARCHAR(20) NOT NULL DEFAULT 'PLANEADA',
  -- En la 007 la liquidación nunca se habilita. La regla definitiva (con HABILITADA y quizá CERRADA) la pone la
  -- migración de la feature de la opción 1.5.1, cuando se responda el cierre y la reapertura (Doc. 01 §11).
  assessments_enabled BOOLEAN     NOT NULL DEFAULT false,
  created_by          UUID        NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by          UUID,
  updated_at          TIMESTAMPTZ,
  CONSTRAINT administration_fiscal_year_status_check
    CHECK (status IN ('PLANEADA', 'EN_PREPARACION', 'LISTA', 'HABILITADA', 'CERRADA')),
  CONSTRAINT administration_fiscal_year_assessments_check CHECK (NOT assessments_enabled),
  -- V-01: una vigencia por administración y año. También es el destino de las FK futuras de las tablas de una
  -- administración hacia la vigencia (ERD 2.3, regla 6).
  CONSTRAINT administration_fiscal_year_year_key UNIQUE (administration_id, fiscal_year),
  -- Para las FK compuestas de las tablas que vendrán (catálogos, configuración, calendario).
  CONSTRAINT administration_fiscal_year_id_administration_key UNIQUE (id, administration_id),
  -- Quien crea o cambia la vigencia es un usuario de la misma administración.
  CONSTRAINT administration_fiscal_year_created_by_fkey
    FOREIGN KEY (created_by, administration_id) REFERENCES core.admin_user (id, administration_id),
  CONSTRAINT administration_fiscal_year_updated_by_fkey
    FOREIGN KEY (updated_by, administration_id) REFERENCES core.admin_user (id, administration_id)
);
-- Cubren la comprobación de las FK compuestas cuando se cambia o borra una fila de core.admin_user. Las lecturas por
-- administración (la lista de vigencias) usan el UNIQUE (administration_id, fiscal_year).
CREATE INDEX administration_fiscal_year_created_by_idx
  ON tax.administration_fiscal_year (created_by, administration_id);
CREATE INDEX administration_fiscal_year_updated_by_idx
  ON tax.administration_fiscal_year (updated_by, administration_id) WHERE updated_by IS NOT NULL;

-- RLS: ENABLE y no FORCE (el dueño, Flyway, no pasa por las políticas; la aplicación, liv_app, sí). Una política por
-- operación, como core.user_session: la base solo admite la transición PLANEADA -> EN_PREPARACION de la 007, así
-- que ni un defecto del dominio puede saltar a otro estado o volver atrás. Un UPDATE sobre una fila que ya no está
-- en PLANEADA no da error: afecta 0 filas, y quien cambia el estado cuenta las filas y responde V-06.
-- No hay política de DELETE: la RLS lo niega (y liv_app tampoco tiene el permiso). La feature de la opción 1.5.1
-- amplía la política de UPDATE con su migración.
-- Dos límites de este diseño, para quien la amplíe:
--  * Solo sirve para UNA transición. Las políticas permisivas de UPDATE combinan con OR sus USING por un lado y sus
--    WITH CHECK por otro, y WITH CHECK solo ve la fila nueva, nunca la anterior: con varias transiciones, la RLS no
--    puede impedir los saltos. La 1.5.1 tiene que imponer los pares de estados permitidos de otra forma (por ejemplo,
--    un disparador BEFORE UPDATE que compare OLD.status y NEW.status) y dejar la RLS para el aislamiento.
--  * SELECT ... FOR UPDATE (y FOR SHARE) también aplica el USING de UPDATE: una fila que ya no está en PLANEADA no se
--    ve para bloquearla. No hay que bloquear esta tabla por fila (@Lock PESSIMISTIC_WRITE) para decidir una apertura.
ALTER TABLE tax.administration_fiscal_year ENABLE ROW LEVEL SECURITY;

CREATE POLICY administration_read ON tax.administration_fiscal_year FOR SELECT TO liv_app
  USING (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid);
CREATE POLICY administration_create ON tax.administration_fiscal_year FOR INSERT TO liv_app
  WITH CHECK (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid
              AND status = 'PLANEADA');
CREATE POLICY administration_open_preparation ON tax.administration_fiscal_year FOR UPDATE TO liv_app
  USING (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid
         AND status = 'PLANEADA')
  WITH CHECK (administration_id = NULLIF(current_setting('app.administration_id', true), '')::uuid
              AND status = 'EN_PREPARACION');

-- Permisos mínimos (H2-4): el año solo se lee; la vigencia se lee, se crea y cambia de estado (con su autor y su
-- hora), pero nunca cambia assessments_enabled ni se borra. Una vigencia creada no se borra.
GRANT SELECT ON tax.fiscal_year TO liv_app;
GRANT SELECT, INSERT ON tax.administration_fiscal_year TO liv_app;
GRANT UPDATE (status, updated_by, updated_at) ON tax.administration_fiscal_year TO liv_app;
