package co.com.sapenlinea.liv.bff.persistence;

import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.API;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.APP;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.OWNER;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.execute;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.migrate;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.newLocalDatabase;
import static co.com.sapenlinea.liv.bff.persistence.BffDatabase.queryValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase.Database;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The sessions of the BFF live in the {@code bff} schema (plan, step 10): the tables of Spring
 * Session JDBC belong to {@code liv_bff_owner}, {@code liv_bff} only does DML on them and {@code
 * liv_app} does not see them (ADR 0025).
 */
@Tag("001-11")
class SessionTablesTest {

  @Test
  @DisplayName("001-11 · Las tablas de sesión están en el esquema bff y son del dueño del esquema")
  void tablesBelongToTheSchemaOwner() throws SQLException {
    Database database = newLocalDatabase();
    migrate(database.url());

    for (String table : new String[] {"spring_session", "spring_session_attributes"}) {
      assertThat(
              queryValue(
                  database.url(),
                  OWNER,
                  "SELECT tableowner FROM pg_tables WHERE schemaname = 'bff' AND tablename = '"
                      + table
                      + "'"))
          .as(table)
          .isEqualTo(OWNER);
    }
  }

  @Test
  @DisplayName(
      "001-11 · liv_bff hace DML en las tablas de sesión, y borrar una sesión borra sus atributos")
  void applicationRoleDoesDmlAndTheDeleteCascades() throws SQLException {
    Database database = newLocalDatabase();
    migrate(database.url());

    execute(
        database.url(),
        APP,
        """
        INSERT INTO bff.spring_session
          (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time)
        VALUES ('p1', 's1', 1, 1, 900, 2);
        INSERT INTO bff.spring_session_attributes (session_primary_id, attribute_name, attribute_bytes)
        VALUES ('p1', 'a', '\\x00');
        UPDATE bff.spring_session SET expiry_time = 3 WHERE primary_id = 'p1';
        """);
    assertThat(
            queryValue(database.url(), APP, "SELECT count(*) FROM bff.spring_session_attributes"))
        .isEqualTo("1");

    execute(database.url(), APP, "DELETE FROM bff.spring_session WHERE primary_id = 'p1'");
    assertThat(
            queryValue(database.url(), APP, "SELECT count(*) FROM bff.spring_session_attributes"))
        .isEqualTo("0");
  }

  @Test
  @DisplayName("001-11 · liv_bff no crea ni cambia tablas, y liv_app no ve las de sesión")
  void otherRolesGetNothingMore() throws SQLException {
    Database database = newLocalDatabase();
    migrate(database.url());

    assertThatThrownBy(() -> execute(database.url(), APP, "DROP TABLE bff.spring_session CASCADE"))
        .hasMessageContaining("must be owner");
    assertThatThrownBy(
            () -> execute(database.url(), APP, "ALTER TABLE bff.spring_session ADD COLUMN x int"))
        .hasMessageContaining("must be owner");
    assertThatThrownBy(
            () -> queryValue(database.url(), API, "SELECT count(*) FROM bff.spring_session"))
        .hasMessageContaining("permission denied");
  }
}
