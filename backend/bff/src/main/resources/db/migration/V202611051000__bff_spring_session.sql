-- Sesiones del BFF (001 T17; plan, paso 10): tablas de Spring Session JDBC en el esquema bff. Corre como
-- liv_bff_owner; liv_bff recibe DML por los permisos por defecto que fijó la primera migración (V202611032100) y
-- liv_app no ve el esquema (ADR 0025). Es el script schema-postgresql.sql de spring-session-jdbc 4.1.1 con el
-- esquema por delante: la aplicación las nombra bff.SPRING_SESSION (spring.session.jdbc.table-name), porque
-- liv_bff no tiene un esquema propio en su search_path.
--
-- Los datos de la sesión (el contexto de seguridad y los tokens cifrados) van en SPRING_SESSION_ATTRIBUTES. La
-- columna PRINCIPAL_NAME guarda el sub de la persona (hasta 100 caracteres: el de Keycloak es un UUID).

CREATE TABLE bff.spring_session (
  primary_id            CHAR(36)     NOT NULL,
  session_id            CHAR(36)     NOT NULL,
  creation_time         BIGINT       NOT NULL,
  last_access_time      BIGINT       NOT NULL,
  max_inactive_interval INT          NOT NULL,
  expiry_time           BIGINT       NOT NULL,
  principal_name        VARCHAR(100),
  CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON bff.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON bff.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON bff.spring_session (principal_name);

CREATE TABLE bff.spring_session_attributes (
  session_primary_id CHAR(36)     NOT NULL,
  attribute_name     VARCHAR(200) NOT NULL,
  attribute_bytes    BYTEA        NOT NULL,
  CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
  CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
    REFERENCES bff.spring_session (primary_id) ON DELETE CASCADE
);
