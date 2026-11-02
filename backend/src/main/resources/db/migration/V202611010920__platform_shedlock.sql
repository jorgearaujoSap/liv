-- Bloqueo de tareas programadas con ShedLock (JDBC; 001 T10). Esquema estándar de ShedLock para PostgreSQL:
-- las horas van en UTC y sin zona (se usa la hora de la base de datos), por eso TIMESTAMP y no TIMESTAMPTZ.
-- Configurar el proveedor con el nombre de tabla platform.shedlock.

CREATE TABLE platform.shedlock (
  name       VARCHAR(64)  NOT NULL PRIMARY KEY,
  lock_until TIMESTAMP    NOT NULL,
  locked_at  TIMESTAMP    NOT NULL,
  locked_by  VARCHAR(255) NOT NULL
);

GRANT SELECT, INSERT, UPDATE ON platform.shedlock TO liv_app;
