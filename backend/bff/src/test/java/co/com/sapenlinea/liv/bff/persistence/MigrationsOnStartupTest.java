package co.com.sapenlinea.liv.bff.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Con el perfil de pruebas, el BFF migra al arrancar como liv_bff_owner con la configuración de
 * application.yml (historial solo en el esquema bff, que Flyway no crea) y la aplicación se conecta
 * como liv_bff. Las demás pruebas de la migración usan una copia de esa configuración
 * (BffDatabase).
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class MigrationsOnStartupTest {

  @Autowired private DataSource dataSource;

  @Autowired private Flyway flyway;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", BffDatabase::jdbcUrl);
    registry.add("spring.datasource.password", () -> BffDatabase.password(BffDatabase.APP));
    registry.add("spring.flyway.password", () -> BffDatabase.password(BffDatabase.OWNER));
  }

  @Test
  @DisplayName("Al arrancar migra el esquema bff como liv_bff_owner y se conecta como liv_bff")
  void migratesAsOwnerAndConnectsAsTheApplication() throws SQLException {
    assertThat(value("SELECT current_user")).isEqualTo(BffDatabase.APP);
    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT bool_and(success AND installed_by = 'liv_bff_owner')"
                    + " FROM bff.flyway_schema_history"))
        .as("todas las migraciones, aplicadas por el dueño y con éxito")
        .isEqualTo("t");
    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT count(*) FROM pg_tables"
                    + " WHERE tablename = 'flyway_schema_history' AND schemaname <> 'bff'"))
        .as("historial fuera del esquema bff")
        .isEqualTo("0");
  }

  @Test
  @DisplayName("La configuración de Flyway es la misma que usan las pruebas del contrato")
  void flywayConfigurationMatchesTheContractTests() {
    var configuration = flyway.getConfiguration();
    assertThat(configuration.getSchemas()).containsExactly("bff");
    assertThat(configuration.getDefaultSchema()).isEqualTo("bff");
    assertThat(configuration.isCreateSchemas()).isFalse();
    assertThat(configuration.getTable()).isEqualTo("flyway_schema_history");
  }

  private String value(String select) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(select)) {
      result.next();
      return result.getString(1);
    }
  }
}
