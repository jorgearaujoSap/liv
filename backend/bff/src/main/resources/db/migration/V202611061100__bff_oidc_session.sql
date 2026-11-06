-- Sesiones del proveedor y del BFF (001 T19; plan, paso 10): qué sesión del BFF corresponde a qué sesión del
-- proveedor (sid), para el cierre por canal trasero. El proveedor nombra su sesión al cerrarla, y con varias réplicas
-- cualquiera debe encontrar la nuestra. Corre como liv_bff_owner; liv_bff recibe DML por los permisos por defecto
-- de la primera migración (V202611032100) y liv_app no ve el esquema (ADR 0025).
--
-- La fila se escribe cuando LIV deja entrar a la persona, con el id que la sesión tiene desde entonces, antes de que
-- Spring Session guarde ese id: por eso no hay llave foránea a bff.spring_session. Se borra cuando se borra la sesión
-- del BFF, por cualquier camino; el barrido quita las que quedaron sin sesión.
--
-- Si el proveedor cierra la sesión y LIV no acepta el fin (EndSession), la sesión del BFF se borra igual, y la fila
-- queda con pending_end_since: el barrido reintenta el aviso a LIV con issuer, sid y subject hasta lograrlo o hasta
-- liv.bff.session.sweep-give-up-after. Las horas son milisegundos desde 1970 (epoch), como en bff.spring_session.
-- issuer, sid y subject tienen los largos de bff.spring_session y de EndSession.

CREATE TABLE bff.oidc_session (
  session_id        CHAR(36)      NOT NULL,
  issuer            VARCHAR(2048) NOT NULL,
  sid               VARCHAR(255)  NOT NULL,
  subject           VARCHAR(255)  NOT NULL,
  created_at        BIGINT        NOT NULL,
  pending_end_since BIGINT,
  CONSTRAINT oidc_session_pk PRIMARY KEY (session_id)
);

CREATE INDEX oidc_session_ix1 ON bff.oidc_session (issuer, sid);
CREATE INDEX oidc_session_ix2 ON bff.oidc_session (pending_end_since) WHERE pending_end_since IS NOT NULL;
