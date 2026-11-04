-- Esquema del BFF (001 T16; plan, paso 10; doc. 02 §5.2). Corre como liv_bff_owner, dueño del esquema bff, con el
-- historial de Flyway en bff.flyway_schema_history. Los roles y el esquema los crea la infraestructura (en local,
-- el script de inicio del entorno del backend; en AWS, la infraestructura como código): aquí se comprueba que
-- estén como se necesitan y se fijan los permisos que le corresponden al dueño del esquema. Las tablas (Spring
-- Session, bff.oidc_session y ShedLock) llegan con las tareas que las usan (T17 y T18).

-- Contrato de los roles: si la infraestructura no lo cumple, la migración se detiene con un mensaje claro.
DO $$
BEGIN
  IF (SELECT nspowner FROM pg_namespace WHERE nspname = 'bff') IS DISTINCT FROM current_user::regrole THEN
    RAISE EXCEPTION '% debe ser el dueño del esquema bff (doc. 02 §5.2)', current_user;
  END IF;
  IF to_regrole('liv_bff') IS NULL THEN
    RAISE EXCEPTION 'Falta el rol de base de datos liv_bff (doc. 02 §5.2)';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles
              WHERE rolname IN (current_user, 'liv_bff')
                AND (rolsuper OR rolbypassrls OR rolcreaterole OR rolcreatedb)) THEN
    RAISE EXCEPTION '% y liv_bff no pueden ser superusuarios ni tener BYPASSRLS, CREATEROLE o CREATEDB',
      current_user;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_auth_members WHERE member = 'liv_bff'::regrole) THEN
    RAISE EXCEPTION 'liv_bff no puede ser miembro de otros roles';
  END IF;
END
$$;

-- liv_bff usa el esquema y hace DML sobre las tablas que creen las migraciones; no crea objetos ni toca el
-- historial de Flyway (el script local le da DML por defecto sobre todas las tablas del esquema, también esa).
REVOKE ALL ON SCHEMA bff FROM PUBLIC, liv_bff;
GRANT USAGE ON SCHEMA bff TO liv_bff;
ALTER DEFAULT PRIVILEGES IN SCHEMA bff GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO liv_bff;
ALTER DEFAULT PRIVILEGES IN SCHEMA bff GRANT USAGE, SELECT ON SEQUENCES TO liv_bff;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
REVOKE ALL ON bff.flyway_schema_history FROM PUBLIC, liv_bff;

-- Contrato de los permisos, ya fijados los del dueño: solo los roles del BFF ven el esquema bff (liv_app no, ADR
-- 0025) y liv_bff no tiene nada fuera de él (sin acceso a los esquemas de LIV, sin crear objetos, sin TEMP).
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace n, aclexplode(n.nspacl) acl
              WHERE n.nspname = 'bff' AND acl.grantee NOT IN (n.nspowner, 'liv_bff'::regrole)) THEN
    RAISE EXCEPTION 'Solo liv_bff_owner y liv_bff pueden tener permisos sobre el esquema bff (ADR 0025)';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_namespace n WHERE has_schema_privilege('liv_bff', n.oid, 'CREATE')) THEN
    RAISE EXCEPTION 'liv_bff no puede crear objetos en ningún esquema';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_namespace n
              WHERE n.nspname NOT IN ('bff', 'public', 'information_schema')
                AND n.nspname NOT LIKE 'pg\_%'
                AND has_schema_privilege('liv_bff', n.oid, 'USAGE')) THEN
    RAISE EXCEPTION 'liv_bff no puede tener acceso a los esquemas de LIV (doc. 02 §5.2)';
  END IF;
  IF has_database_privilege('liv_bff', current_database(), 'TEMP') THEN
    RAISE EXCEPTION 'liv_bff no puede crear tablas temporales en la base %', current_database();
  END IF;
END
$$;
