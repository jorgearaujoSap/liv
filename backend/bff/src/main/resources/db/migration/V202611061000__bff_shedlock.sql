-- Bloqueo del barrido de las sesiones vencidas (001 T19; plan, paso 10): con varias réplicas del BFF, una sola
-- barre a la vez. Esquema estándar de ShedLock para PostgreSQL, como platform.shedlock en la API: las horas van en
-- UTC y sin zona (se usa la hora de la base de datos), por eso TIMESTAMP y no TIMESTAMPTZ. Corre como
-- liv_bff_owner; liv_bff recibe DML por los permisos por defecto de la primera migración (V202611032100) y
-- liv_app no ve el esquema (ADR 0025).

CREATE TABLE bff.shedlock (
  name       VARCHAR(64)  NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP    NOT NULL,
  locked_at  TIMESTAMP    NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);
