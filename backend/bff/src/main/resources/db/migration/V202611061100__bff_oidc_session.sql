-- Sesiones del proveedor y del BFF (001 T19; plan, paso 10): qué sesión del BFF corresponde a qué sesión del
-- proveedor (sid), para el cierre por canal trasero. El proveedor nombra su sesión al cerrarla, y con varias réplicas
-- cualquiera debe encontrar la nuestra. Corre como liv_bff_owner; liv_bff recibe DML por los permisos por defecto
-- de la primera migración (V202611032100) y liv_app no ve el esquema (ADR 0025).
--
-- No hay llave foránea a bff.spring_session: la fila se escribe cuando LIV deja entrar a la persona, con el id que la
-- sesión tiene desde entonces, antes de que Spring Session guarde ese id. Se borra al terminar la sesión, y el barrido
-- quita las que quedaron sin sesión. issuer, sid y subject tienen los largos de bff.spring_session y de EndSession.

CREATE TABLE bff.oidc_session (
  session_id CHAR(36)      NOT NULL,
  issuer     VARCHAR(2048) NOT NULL,
  sid        VARCHAR(255),
  subject    VARCHAR(255)  NOT NULL,
  created_at BIGINT        NOT NULL,
  CONSTRAINT oidc_session_pk PRIMARY KEY (session_id)
);

CREATE INDEX oidc_session_ix1 ON bff.oidc_session (issuer, sid);
CREATE INDEX oidc_session_ix2 ON bff.oidc_session (issuer, subject);
