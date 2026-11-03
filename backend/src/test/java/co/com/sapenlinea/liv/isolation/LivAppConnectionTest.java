package co.com.sapenlinea.liv.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The application only works as {@code liv_app} without any way past RLS (doc. 02 §5.2, T10 › T12):
 * every new connection is checked, and a deployment that changes the user, the role or the check
 * itself gets no connection. The check is imposed in code, so no property removes it.
 */
@DisplayName("La aplicación solo se conecta como liv_app (T12)")
class LivAppConnectionTest {

  private static final String REFUSED =
      "LIV solo se conecta como liv_app sin superusuario, BYPASSRLS, membresías ni contexto";

  @Nested
  @SpringBootTest(properties = "liv.security.issuer-uri=http://localhost:1/sin-proveedor")
  @DisplayName("Con liv_app")
  class AsLivApp {

    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
      registry.add("spring.datasource.password", () -> LivDatabase.password(LivDatabase.APP));
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Con liv_app la conexión se abre")
    void connects() throws Exception {
      try (Connection connection = dataSource.getConnection();
          ResultSet user = connection.createStatement().executeQuery("SELECT current_user")) {
        user.next();
        assertThat(user.getString(1)).isEqualTo(LivDatabase.APP);
      }
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · liv_app con BYPASSRLS, superusuario o miembro del dueño no se conecta")
    void refusesAPrivilegedLivApp() throws SQLException {
      assertRefusedWhile("ALTER ROLE liv_app BYPASSRLS", "ALTER ROLE liv_app NOBYPASSRLS");
      assertRefusedWhile("ALTER ROLE liv_app SUPERUSER", "ALTER ROLE liv_app NOSUPERUSER");
      assertRefusedWhile("GRANT liv_owner TO liv_app", "REVOKE liv_owner FROM liv_app");
      // The owner of a table with ENABLE (not FORCE) row level security skips its policies. A
      // table of its own for the test: changing the owner of a real one loses liv_app's grants.
      assertRefusedWhile(
          "CREATE TABLE public.t12_owned (id int); ALTER TABLE public.t12_owned OWNER TO liv_app",
          "DROP TABLE public.t12_owned");
      assertThat(freshConnectionFailure()).as("liv_app restaurado").isNull();
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Un contexto fijado en el rol o en la base no se acepta")
    void refusesADefaultContext() throws SQLException {
      assertRefusedWhile(
          "ALTER ROLE liv_app SET app.administration_id = '" + LivDatabase.ADMIN_A + "'",
          "ALTER ROLE liv_app RESET app.administration_id");
      assertRefusedWhile(
          "ALTER ROLE liv_app SET app.platform_job = 'on'",
          "ALTER ROLE liv_app RESET app.platform_job");
      assertRefusedWhile(
          "ALTER DATABASE liv SET app.administration_id = '" + LivDatabase.ADMIN_A + "'",
          "ALTER DATABASE liv RESET app.administration_id");
      assertRefusedWhile(
          "ALTER ROLE liv_app IN DATABASE liv SET app.administration_id = '"
              + LivDatabase.ADMIN_A
              + "'",
          "ALTER ROLE liv_app IN DATABASE liv RESET app.administration_id");
      assertThat(freshConnectionFailure()).as("liv_app restaurado").isNull();
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Otro usuario que asume liv_app (session_user distinto) no se conecta")
    void refusesAnotherSessionUser() {
      Throwable failure =
          catchThrowable(() -> {
            try (HikariDataSource pool =
                pool("postgres", "postgres-test-pw", "?options=-c%20role%3Dliv_app")) {
              pool.getConnection().close();
            }
          });
      assertThat(failure).hasStackTraceContaining(REFUSED);
    }

    /** A pool of its own, with the check the application has, so each case opens a new one. */
    private HikariDataSource pool(String user, String password, String urlSuffix) {
      HikariDataSource pool = new HikariDataSource();
      pool.setJdbcUrl(LivDatabase.jdbcUrl() + urlSuffix);
      pool.setUsername(user);
      pool.setPassword(password);
      pool.setMaximumPoolSize(1);
      pool.setConnectionInitSql(((HikariDataSource) dataSource).getConnectionInitSql());
      return pool;
    }

    private Throwable freshConnectionFailure() {
      return catchThrowable(() -> {
        try (HikariDataSource pool =
            pool(LivDatabase.APP, LivDatabase.password(LivDatabase.APP), "")) {
          pool.getConnection().close();
        }
      });
    }

    private void assertRefusedWhile(String change, String revert) throws SQLException {
      LivDatabase.asSuperuser("liv", change);
      try {
        assertThat(freshConnectionFailure()).as(change).hasStackTraceContaining(REFUSED);
      } finally {
        LivDatabase.asSuperuser("liv", revert);
      }
    }
  }

  @Nested
  @SpringBootTest(
      properties = {
        "liv.security.issuer-uri=http://localhost:1/sin-proveedor",
        // The check is imposed in code: a property cannot remove it.
        "spring.datasource.hikari.connection-init-sql=SELECT 1"
      })
  @DirtiesContext
  @DisplayName("Con otro rol")
  class AsAnotherRole {

    @Autowired DataSource dataSource;
    @Autowired HealthEndpoint health;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
      registry.add("spring.datasource.username", () -> LivDatabase.OWNER);
      registry.add("spring.datasource.password", () -> LivDatabase.OWNER_PASSWORD);
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Con el dueño de las tablas no se obtiene ninguna conexión")
    void refusesTheOwner() {
      Throwable failure = catchThrowable(() -> dataSource.getConnection().close());

      assertThat(failure).hasStackTraceContaining(REFUSED + ", no como liv_owner");
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Con el rol equivocado la instancia no queda lista para recibir tráfico")
    void notReady() {
      assertThat(health.healthForPath("readiness").getStatus()).isEqualTo(Status.DOWN);
    }
  }
}
