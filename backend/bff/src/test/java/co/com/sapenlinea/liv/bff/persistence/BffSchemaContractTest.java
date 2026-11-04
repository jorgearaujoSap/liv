package co.com.sapenlinea.liv.bff.persistence;

import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.API;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.APP;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.OWNER;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.asSuperuser;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.createDatabase;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.execute;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.migrate;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.queryValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.stream.Stream;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Las migraciones del BFF corren como liv_bff_owner y dejan a liv_bff con DML en el esquema bff y
 * nada más (doc. 02 §5.2, ADR 0025). Si la infraestructura no entrega los roles y el esquema como
 * se necesitan, la primera migración se detiene con un mensaje claro.
 */
class BffSchemaContractTest {

  @Test
  @DisplayName("Las migraciones corren como liv_bff_owner y el historial queda en el esquema bff")
  void migrationsRunAsTheSchemaOwner() throws SQLException {
    String url = createDatabase("bff_contract_history");
    migrate(url);

    assertThat(
            queryValue(
                url,
                OWNER,
                "SELECT installed_by FROM bff.flyway_schema_history WHERE success LIMIT 1"))
        .isEqualTo(OWNER);
    assertThat(queryValue(url, OWNER, "SELECT to_regclass('public.flyway_schema_history')"))
        .isNull();
  }

  @Test
  @DisplayName("liv_bff usa el esquema bff, no crea objetos y no puede tocar el historial")
  void applicationRoleOnlyUsesTheSchema() throws SQLException {
    String url = createDatabase("bff_contract_app");
    migrate(url);

    assertThat(queryValue(url, APP, "SELECT has_schema_privilege('bff', 'USAGE')")).isEqualTo("t");
    assertThat(queryValue(url, APP, "SELECT has_schema_privilege('bff', 'CREATE')")).isEqualTo("f");
    assertThatThrownBy(() -> execute(url, APP, "DELETE FROM bff.flyway_schema_history"))
        .hasMessageContaining("permission denied");
    assertThatThrownBy(() ->
            execute(url, APP, "UPDATE bff.flyway_schema_history SET checksum = 0 WHERE false"))
        .hasMessageContaining("permission denied");
  }

  @Test
  @DisplayName("Las tablas que creen las migraciones siguientes las usa liv_bff con DML")
  void laterTablesAreUsableByTheApplication() throws SQLException {
    String url = createDatabase("bff_contract_tables");
    migrate(url);
    execute(url, OWNER, "CREATE TABLE bff.probe (id BIGSERIAL PRIMARY KEY, value TEXT)");

    execute(url, APP, "INSERT INTO bff.probe (value) VALUES ('a')");
    execute(url, APP, "UPDATE bff.probe SET value = 'b'");
    execute(url, APP, "DELETE FROM bff.probe");
    assertThatThrownBy(() -> execute(url, APP, "DROP TABLE bff.probe"))
        .hasMessageContaining("must be owner");
  }

  @Test
  @DisplayName("liv_app no tiene acceso al esquema bff (ADR 0025)")
  void apiRoleHasNoAccess() throws SQLException {
    String url = createDatabase("bff_contract_api");
    migrate(url);

    assertThat(queryValue(url, API, "SELECT has_schema_privilege('bff', 'USAGE')")).isEqualTo("f");
  }

  /**
   * Cada caso: escenario, cambio en la base nueva ({@code %s} es su nombre), cambio de un rol con
   * cómo deshacerlo (los roles son de todo el servidor) y el mensaje esperado.
   */
  static Stream<Arguments> brokenContracts() {
    return Stream.of(
        Arguments.of(
            "el esquema bff es de otro rol",
            "ALTER SCHEMA bff OWNER TO postgres; GRANT ALL ON SCHEMA bff TO liv_bff_owner",
            null,
            null,
            "debe ser el dueño del esquema bff"),
        Arguments.of(
            "liv_bff tiene BYPASSRLS",
            null,
            "ALTER ROLE liv_bff BYPASSRLS",
            "ALTER ROLE liv_bff NOBYPASSRLS",
            "no pueden ser superusuarios ni tener BYPASSRLS"),
        Arguments.of(
            "liv_bff_owner puede crear roles",
            null,
            "ALTER ROLE liv_bff_owner CREATEROLE",
            "ALTER ROLE liv_bff_owner NOCREATEROLE",
            "no pueden ser superusuarios ni tener BYPASSRLS"),
        Arguments.of(
            "liv_bff es miembro de liv_bff_owner",
            null,
            "GRANT liv_bff_owner TO liv_bff",
            "REVOKE liv_bff_owner FROM liv_bff",
            "liv_bff no puede ser miembro de otros roles"),
        Arguments.of(
            "liv_app tiene acceso al esquema bff",
            "GRANT USAGE ON SCHEMA bff TO liv_app",
            null,
            null,
            "Solo liv_bff_owner y liv_bff pueden tener permisos sobre el esquema bff"),
        Arguments.of(
            "liv_bff tiene acceso a un esquema de LIV",
            "CREATE SCHEMA core; GRANT USAGE ON SCHEMA core TO liv_bff",
            null,
            null,
            "liv_bff no puede tener acceso a los esquemas de LIV"),
        Arguments.of(
            "liv_bff puede crear objetos en public",
            "GRANT USAGE, CREATE ON SCHEMA public TO liv_bff",
            null,
            null,
            "liv_bff no puede crear objetos"),
        Arguments.of(
            "liv_bff puede crear tablas temporales",
            "GRANT TEMPORARY ON DATABASE %s TO liv_bff",
            null,
            null,
            "liv_bff no puede crear tablas temporales"));
  }

  @ParameterizedTest(name = "Si {0}, la migración se detiene")
  @MethodSource("brokenContracts")
  void brokenContractStopsTheMigration(
      String scenario, String databaseSetup, String roleSetup, String roleUndo, String message)
      throws SQLException {
    String database = "bff_broken_" + Integer.toUnsignedString(scenario.hashCode());
    String url = createDatabase(database);
    if (databaseSetup != null) {
      asSuperuser(database, databaseSetup.formatted(database));
    }
    if (roleSetup != null) {
      asSuperuser("postgres", roleSetup);
    }
    try {
      assertThatThrownBy(() -> migrate(url))
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining(message);
    } finally {
      if (roleUndo != null) {
        asSuperuser("postgres", roleUndo);
      }
    }
  }
}
