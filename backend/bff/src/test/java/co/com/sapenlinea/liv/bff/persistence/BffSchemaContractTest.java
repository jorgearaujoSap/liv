package co.com.sapenlinea.liv.bff.persistence;

import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.API;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.APP;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.OWNER;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.asSuperuser;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.execute;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.migrate;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.newDatabase;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.newLocalDatabase;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.queryValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase.Database;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Las migraciones del BFF corren como liv_bff_owner y dejan a liv_bff con DML en el esquema bff y
 * nada más (doc. 02 §5.2, ADR 0025). Si la infraestructura no entrega los roles y el esquema como
 * se necesitan, la primera migración se detiene con un mensaje claro. Los roles son de todo el
 * servidor: cada caso que cambia uno lo deshace, y las pruebas no corren en paralelo
 * (junit-platform.properties).
 */
class BffSchemaContractTest {

  @Test
  @DisplayName("Con lo mínimo de la infraestructura, liv_bff queda con USAGE en bff y sin CREATE")
  void ownerGrantsTheSchemaUsage() throws SQLException {
    Database database = newDatabase();
    // Permisos de más que la migración debe quitar.
    asSuperuser(
        database.name(),
        "GRANT USAGE ON SCHEMA bff TO PUBLIC; GRANT USAGE, CREATE ON SCHEMA bff TO liv_bff");
    migrate(database.url());

    assertThat(queryValue(database.url(), APP, "SELECT has_schema_privilege('bff', 'USAGE')"))
        .isEqualTo("t");
    assertThat(queryValue(database.url(), APP, "SELECT has_schema_privilege('bff', 'CREATE')"))
        .isEqualTo("f");
    assertThat(queryValue(database.url(), API, "SELECT has_schema_privilege('bff', 'USAGE')"))
        .isEqualTo("f");
  }

  @Test
  @DisplayName(
      "Las tablas y secuencias que creen las migraciones siguientes las usa liv_bff con DML, y sus"
          + " funciones no las ejecuta nadie más")
  void laterObjectsFollowTheOwnerDefaults() throws SQLException {
    Database database = newDatabase();
    migrate(database.url());
    execute(
        database.url(),
        OWNER,
        """
        CREATE TABLE bff.probe (id BIGSERIAL PRIMARY KEY, value TEXT);
        CREATE FUNCTION bff.probe_function() RETURNS int LANGUAGE sql AS 'SELECT 1';
        """);

    execute(database.url(), APP, "INSERT INTO bff.probe (value) VALUES ('a')");
    assertThat(queryValue(database.url(), APP, "SELECT value FROM bff.probe")).isEqualTo("a");
    assertThat(queryValue(database.url(), APP, "SELECT last_value FROM bff.probe_id_seq"))
        .isEqualTo("1");
    execute(database.url(), APP, "UPDATE bff.probe SET value = 'b'");
    execute(database.url(), APP, "DELETE FROM bff.probe");
    assertThatThrownBy(() -> execute(database.url(), APP, "DROP TABLE bff.probe"))
        .hasMessageContaining("must be owner");
    assertThatThrownBy(() -> execute(database.url(), APP, "TRUNCATE bff.probe"))
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() -> execute(database.url(), APP, "SELECT bff.probe_function()"))
        .hasMessageContaining("permission denied for function");
  }

  @Test
  @DisplayName(
      "liv_bff no puede leer ni cambiar el historial de Flyway, aunque los permisos por defecto del"
          + " entorno local le den DML en todo el esquema")
  void applicationRoleCannotTouchTheHistory() throws SQLException {
    Database database = newLocalDatabase();
    migrate(database.url());

    for (String sql :
        new String[] {
          "SELECT count(*) FROM bff.flyway_schema_history",
          "DELETE FROM bff.flyway_schema_history",
          "UPDATE bff.flyway_schema_history SET checksum = 0 WHERE false"
        }) {
      assertThatThrownBy(() -> execute(database.url(), APP, sql))
          .hasMessageContaining("permission denied");
    }
  }

  @Test
  @DisplayName("Una migración detenida no deja permisos a medias y se puede repetir al corregir")
  void stoppedMigrationLeavesNothingBehind() throws SQLException {
    Database database = newDatabase();
    asSuperuser(database.name(), "GRANT USAGE ON SCHEMA bff TO liv_app");
    assertThatThrownBy(() -> migrate(database.url())).isInstanceOf(FlywayException.class);
    assertThat(queryValue(database.url(), APP, "SELECT has_schema_privilege('bff', 'USAGE')"))
        .isEqualTo("f");

    asSuperuser(database.name(), "REVOKE USAGE ON SCHEMA bff FROM liv_app");
    migrate(database.url());

    assertThat(
            queryValue(
                database.url(),
                OWNER,
                "SELECT count(*) FROM bff.flyway_schema_history WHERE success"))
        .isEqualTo("1");
  }

  @Test
  @DisplayName(
      "Un administrador de la infraestructura (CREATEROLE) puede ser miembro de los roles del BFF,"
          + " como quien los crea en RDS")
  void infrastructureAdministratorMayBeAMember() throws SQLException {
    Database database = newDatabase();
    try {
      asSuperuser(
          "postgres",
          """
          CREATE ROLE bff_test_admin CREATEROLE;
          GRANT liv_bff_owner TO bff_test_admin WITH ADMIN TRUE, INHERIT FALSE, SET TRUE;
          GRANT liv_bff TO bff_test_admin WITH ADMIN TRUE, INHERIT FALSE, SET FALSE;
          """);
      migrate(database.url());
    } finally {
      asSuperuser("postgres", "DROP ROLE bff_test_admin");
    }
  }

  @Test
  @DisplayName("Si otro rol corre las migraciones, se detienen aunque sea el dueño del esquema")
  void onlyTheSchemaOwnerRoleMigrates() throws SQLException {
    Database database = newDatabase();
    asSuperuser(database.name(), "ALTER SCHEMA bff OWNER TO liv_app");

    assertThatThrownBy(() -> migrate(database.url(), API))
        .isInstanceOf(FlywayException.class)
        .hasMessageContaining("Las migraciones del BFF corren como liv_bff_owner");
  }

  @ParameterizedTest(name = "Si {0} tiene {1}, la migración se detiene")
  @CsvSource({
    "liv_bff, SUPERUSER",
    "liv_bff, BYPASSRLS",
    "liv_bff, CREATEROLE",
    "liv_bff, CREATEDB",
    "liv_bff, REPLICATION",
    "liv_bff_owner, SUPERUSER",
    "liv_bff_owner, BYPASSRLS",
    "liv_bff_owner, CREATEROLE",
    "liv_bff_owner, CREATEDB",
    "liv_bff_owner, REPLICATION"
  })
  void privilegedAttributeStopsTheMigration(String role, String attribute) throws SQLException {
    Database database = newDatabase();
    try {
      asSuperuser("postgres", "ALTER ROLE %s %s".formatted(role, attribute));
      assertThatThrownBy(() -> migrate(database.url()))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining(
              "liv_bff_owner y liv_bff no pueden ser superusuarios ni tener BYPASSRLS, CREATEROLE,"
                  + " CREATEDB o REPLICATION");
    } finally {
      asSuperuser("postgres", "ALTER ROLE %s NO%s".formatted(role, attribute));
    }
  }

  /**
   * Cada caso: escenario, cambio en la base nueva ({@code %s} es su nombre), cambio de un rol con
   * cómo deshacerlo (los roles son de todo el servidor) y el mensaje esperado.
   */
  private static final String MEMBERSHIP =
      "Solo un administrador ajeno a LIV (CREATEROLE) puede ser miembro de liv_bff_owner o de liv_bff";

  private static final String DEFAULTS =
      "Los permisos por defecto de liv_bff_owner solo pueden dar DML en bff a liv_bff";

  static Stream<Arguments> brokenContracts() {
    return Stream.of(
        broken(
            "el esquema bff es de otro rol",
            "ALTER SCHEMA bff OWNER TO postgres; GRANT ALL ON SCHEMA bff TO liv_bff_owner",
            "liv_bff_owner debe ser el dueño del esquema bff"),
        brokenRole(
            "falta el rol liv_bff",
            "ALTER ROLE liv_bff RENAME TO liv_bff_ausente",
            "ALTER ROLE liv_bff_ausente RENAME TO liv_bff",
            "Falta el rol de base de datos liv_bff"),
        brokenRole(
            "liv_bff es miembro de liv_bff_owner",
            "GRANT liv_bff_owner TO liv_bff",
            "REVOKE liv_bff_owner FROM liv_bff",
            "liv_bff_owner y liv_bff no pueden ser miembros de otros roles"),
        brokenRole(
            "liv_bff_owner es miembro de liv_app",
            "GRANT liv_app TO liv_bff_owner",
            "REVOKE liv_app FROM liv_bff_owner",
            "liv_bff_owner y liv_bff no pueden ser miembros de otros roles"),
        brokenRole(
            "liv_app es miembro de liv_bff",
            "GRANT liv_bff TO liv_app",
            "REVOKE liv_bff FROM liv_app",
            MEMBERSHIP),
        brokenRole(
            "liv_app es miembro de liv_bff_owner",
            "GRANT liv_bff_owner TO liv_app",
            "REVOKE liv_bff_owner FROM liv_app",
            MEMBERSHIP),
        brokenRole(
            "liv_app puede asumir liv_bff sin heredarlo",
            "GRANT liv_bff TO liv_app WITH INHERIT FALSE, SET TRUE",
            "REVOKE liv_bff FROM liv_app",
            MEMBERSHIP),
        brokenRole(
            "liv_app solo puede conceder liv_bff",
            "GRANT liv_bff TO liv_app WITH ADMIN TRUE, INHERIT FALSE, SET FALSE",
            "REVOKE liv_bff FROM liv_app",
            MEMBERSHIP),
        brokenRole(
            "liv_app tiene CREATEROLE y es miembro de liv_bff",
            "ALTER ROLE liv_app CREATEROLE; GRANT liv_bff TO liv_app",
            "REVOKE liv_bff FROM liv_app; ALTER ROLE liv_app NOCREATEROLE",
            MEMBERSHIP),
        brokenRole(
            "un rol sin privilegios hereda liv_bff_owner a través de un administrador",
            "CREATE ROLE bff_test_admin CREATEROLE; CREATE ROLE bff_test_helper;"
                + " GRANT liv_bff_owner TO bff_test_admin; GRANT bff_test_admin TO bff_test_helper",
            "DROP ROLE bff_test_helper; DROP ROLE bff_test_admin",
            MEMBERSHIP),
        broken(
            "liv_app tiene acceso al esquema bff",
            "GRANT USAGE ON SCHEMA bff TO liv_app",
            "Solo liv_bff_owner y liv_bff pueden tener permisos sobre el esquema bff"),
        broken(
            "hay un permiso por defecto de más para liv_bff",
            "ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff"
                + " GRANT TRUNCATE ON TABLES TO liv_bff",
            DEFAULTS),
        broken(
            "liv_bff ejecutaría por defecto las funciones del dueño",
            "ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner GRANT EXECUTE ON FUNCTIONS TO liv_bff",
            DEFAULTS),
        broken(
            "liv_bff tendría DML por defecto fuera de bff",
            "ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner GRANT SELECT ON TABLES TO liv_bff",
            DEFAULTS),
        broken(
            "liv_bff podría cambiar por defecto las secuencias",
            "ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff"
                + " GRANT UPDATE ON SEQUENCES TO liv_bff",
            DEFAULTS),
        broken(
            "hay un permiso por defecto para liv_app",
            "ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner GRANT SELECT ON TABLES TO liv_app",
            DEFAULTS),
        broken(
            "liv_bff tiene acceso a un esquema de LIV",
            "CREATE SCHEMA core; GRANT USAGE ON SCHEMA core TO liv_bff",
            "no pueden tener acceso a otros esquemas"),
        broken(
            "liv_bff_owner tiene acceso a un esquema de LIV",
            "CREATE SCHEMA core; GRANT USAGE ON SCHEMA core TO liv_bff_owner",
            "no pueden tener acceso a otros esquemas"),
        broken(
            "liv_bff tiene acceso al esquema public",
            "GRANT USAGE ON SCHEMA public TO liv_bff",
            "no pueden tener acceso a otros esquemas"),
        broken(
            "liv_bff puede crear objetos en public",
            "GRANT CREATE ON SCHEMA public TO liv_bff",
            "no pueden crear objetos fuera de bff"),
        broken(
            "liv_bff_owner puede crear objetos en public",
            "GRANT CREATE ON SCHEMA public TO liv_bff_owner",
            "no pueden crear objetos fuera de bff"),
        broken(
            "liv_bff puede crear esquemas en la base",
            "GRANT CREATE ON DATABASE %s TO liv_bff",
            "no pueden crear esquemas en la base"),
        broken(
            "liv_bff_owner puede crear esquemas en la base",
            "GRANT CREATE ON DATABASE %s TO liv_bff_owner",
            "no pueden crear esquemas en la base"),
        broken(
            "liv_bff puede crear tablas temporales",
            "GRANT TEMPORARY ON DATABASE %s TO liv_bff",
            "liv_bff no puede crear tablas temporales"));
  }

  @ParameterizedTest(name = "Si {0}, la migración se detiene")
  @MethodSource("brokenContracts")
  void brokenContractStopsTheMigration(
      String scenario, String databaseSetup, String roleSetup, String roleUndo, String message)
      throws SQLException {
    Database database = newDatabase();
    if (databaseSetup != null) {
      asSuperuser(database.name(), databaseSetup.formatted(database.name()));
    }
    try {
      if (roleSetup != null) {
        asSuperuser("postgres", roleSetup);
      }
      assertThatThrownBy(() -> migrate(database.url()))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining(message);
    } finally {
      if (roleUndo != null) {
        asSuperuser("postgres", roleUndo);
      }
    }
  }

  private static Arguments broken(String scenario, String databaseSetup, String message) {
    return Arguments.of(scenario, databaseSetup, null, null, message);
  }

  private static Arguments brokenRole(
      String scenario, String roleSetup, String roleUndo, String message) {
    return Arguments.of(scenario, null, roleSetup, roleUndo, message);
  }
}
