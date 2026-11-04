-- Esquema del BFF (001 T16; plan, paso 10; doc. 02 §5.2). Corre como liv_bff_owner, dueño del esquema bff, con el
-- historial de Flyway en bff.flyway_schema_history. Los roles, la base y el esquema los crea la infraestructura
-- (README, "Base de datos"): aquí se comprueba que estén como se necesitan y se fijan los permisos que le
-- corresponden al dueño del esquema. Las tablas (Spring Session, bff.oidc_session y ShedLock) llegan con las
-- tareas que las usan (T17 y T18).

-- Contrato de los roles: si la infraestructura no lo cumple, la migración se detiene con un mensaje claro.
-- Requiere PostgreSQL 16 o superior (opciones INHERIT y SET de las membresías).
DO $$
BEGIN
  IF current_setting('server_version_num')::int < 160000 THEN
    RAISE EXCEPTION 'Se requiere PostgreSQL 16 o superior';
  END IF;
  IF current_user <> 'liv_bff_owner' THEN
    RAISE EXCEPTION 'Las migraciones del BFF corren como liv_bff_owner, no como % (doc. 02 §5.2)', current_user;
  END IF;
  IF (SELECT nspowner FROM pg_namespace WHERE nspname = 'bff') IS DISTINCT FROM 'liv_bff_owner'::regrole THEN
    RAISE EXCEPTION 'liv_bff_owner debe ser el dueño del esquema bff (doc. 02 §5.2)';
  END IF;
  IF to_regrole('liv_bff') IS NULL THEN
    RAISE EXCEPTION 'Falta el rol de base de datos liv_bff (doc. 02 §5.2)';
  END IF;
  -- En RDS nadie tiene SUPERUSER: el poder llega por membresía (rds_superuser, pg_read_all_data...), así que los
  -- roles del BFF no pueden ser miembros de ningún rol.
  IF EXISTS (SELECT 1 FROM pg_roles
              WHERE rolname IN ('liv_bff_owner', 'liv_bff')
                AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb OR rolreplication)) THEN
    RAISE EXCEPTION 'liv_bff_owner y liv_bff no pueden ser superusuarios ni tener BYPASSRLS, CREATEROLE, CREATEDB '
      'o REPLICATION';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_auth_members WHERE member IN ('liv_bff_owner'::regrole, 'liv_bff'::regrole)) THEN
    RAISE EXCEPTION 'liv_bff_owner y liv_bff no pueden ser miembros de otros roles';
  END IF;
  -- Quien crea los roles en RDS (con CREATEROLE) queda como miembro, y para crear el esquema a nombre de
  -- liv_bff_owner debe poder asumirlo. Cualquier otro rol que los herede o los asuma (liv_app, por ejemplo) vería
  -- el esquema bff.
  IF EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member
              WHERE m.roleid IN ('liv_bff_owner'::regrole, 'liv_bff'::regrole)
                AND (m.inherit_option OR m.set_option)
                AND NOT (r.rolsuper OR r.rolcreaterole)) THEN
    RAISE EXCEPTION 'Solo un administrador (CREATEROLE) puede ser miembro de liv_bff_owner o de liv_bff';
  END IF;
END
$$;

-- liv_bff usa el esquema y hace DML sobre las tablas y secuencias que creen las migraciones; no crea objetos, no
-- ejecuta funciones ni toca el historial de Flyway. Flyway crea el historial antes de esta migración, así que los
-- permisos por defecto que la infraestructura haya dado a liv_bff (en el entorno local, DML en todo el esquema)
-- también le llegan a esa tabla: por eso se le quitan.
REVOKE ALL ON SCHEMA bff FROM PUBLIC, liv_bff;
GRANT USAGE ON SCHEMA bff TO liv_bff;
ALTER DEFAULT PRIVILEGES IN SCHEMA bff GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO liv_bff;
ALTER DEFAULT PRIVILEGES IN SCHEMA bff GRANT USAGE, SELECT ON SEQUENCES TO liv_bff;
-- PostgreSQL da EXECUTE a PUBLIC sobre toda función nueva; las del dueño nacen sin él.
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
REVOKE ALL ON bff.flyway_schema_history FROM PUBLIC, liv_bff;

-- Contrato de los permisos, ya fijados los del dueño: solo los roles del BFF ven el esquema bff (liv_app no, ADR
-- 0025), los permisos por defecto no dan nada más, y ninguno de los dos roles tiene nada fuera de bff (ni los
-- esquemas de LIV ni public, ni crear esquemas, ni TEMP).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace n, aclexplode(n.nspacl) acl
              WHERE n.nspname = 'bff' AND acl.grantee NOT IN (n.nspowner, 'liv_bff'::regrole)) THEN
    RAISE EXCEPTION 'Solo liv_bff_owner y liv_bff pueden tener permisos sobre el esquema bff (ADR 0025)';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_default_acl d, aclexplode(d.defaclacl) acl
              WHERE d.defaclrole = 'liv_bff_owner'::regrole
                AND acl.grantee <> d.defaclrole
                AND NOT (acl.grantee = 'liv_bff'::regrole
                         AND d.defaclnamespace = 'bff'::regnamespace
                         AND ((d.defaclobjtype = 'r' AND acl.privilege_type IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE'))
                           OR (d.defaclobjtype = 'S' AND acl.privilege_type IN ('USAGE', 'SELECT'))))) THEN
    RAISE EXCEPTION 'Los permisos por defecto de liv_bff_owner solo pueden dar DML en bff a liv_bff';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_namespace n, (VALUES ('liv_bff_owner'), ('liv_bff')) AS bff_role(name)
              WHERE n.nspname NOT IN ('bff', 'information_schema')
                AND n.nspname NOT LIKE 'pg\_%'
                AND has_schema_privilege(bff_role.name, n.oid, 'USAGE')) THEN
    RAISE EXCEPTION 'liv_bff_owner y liv_bff no pueden tener acceso a otros esquemas: ni a los de LIV ni a public '
      '(doc. 02 §5.2)';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_namespace n, (VALUES ('liv_bff_owner'), ('liv_bff')) AS bff_role(name)
              WHERE (n.nspname <> 'bff' OR bff_role.name = 'liv_bff')
                AND has_schema_privilege(bff_role.name, n.oid, 'CREATE')) THEN
    RAISE EXCEPTION 'liv_bff_owner y liv_bff no pueden crear objetos fuera de bff, y liv_bff tampoco dentro';
  END IF;
  IF has_database_privilege('liv_bff_owner', current_database(), 'CREATE')
     OR has_database_privilege('liv_bff', current_database(), 'CREATE') THEN
    RAISE EXCEPTION 'liv_bff_owner y liv_bff no pueden crear esquemas en la base %', current_database();
  END IF;
  IF has_database_privilege('liv_bff', current_database(), 'TEMP') THEN
    RAISE EXCEPTION 'liv_bff no puede crear tablas temporales en la base %', current_database();
  END IF;
END
$$;
