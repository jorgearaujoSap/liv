package co.com.sapenlinea.liv.bff.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The BFF only works as {@code liv_bff}, with DML on its schema and no way past it (doc. 02 §5.2,
 * T16 › "para la primera tarea que use la base del BFF"): every new connection is checked, and a
 * deployment that changes the user or gives the role more power gets no connection. The check is
 * imposed in code, so no property removes it. It is the same idea as the one of liv-api.
 */
@Tag("001-11")
@DisplayName("El BFF solo se conecta como liv_bff")
class BffConnectionTest {

  private static final String REFUSED =
      "El BFF solo se conecta como liv_bff sin privilegios, membresías ni objetos propios";

  @Nested
  @ActiveProfiles("test")
  @SpringBootTest(webEnvironment = WebEnvironment.MOCK)
  @DisplayName("Con liv_bff")
  class AsTheApplication {

    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", BffDatabase::jdbcUrl);
      registry.add("spring.datasource.password", () -> BffDatabase.password(BffDatabase.APP));
      registry.add("spring.flyway.password", () -> BffDatabase.password(BffDatabase.OWNER));
    }

    @Test
    @DisplayName("001-11 · Con liv_bff la conexión se abre")
    void connects() throws SQLException {
      try (var connection = dataSource.getConnection()) {
        assertThat(connection.getMetaData().getUserName()).isEqualTo(BffDatabase.APP);
      }
    }

    @Test
    @DisplayName(
        "001-11 · liv_bff con privilegios, miembro del dueño o dueño de objetos no se conecta")
    void refusesAPrivilegedRole() throws SQLException {
      assertRefusedWhile("ALTER ROLE liv_bff SUPERUSER", "ALTER ROLE liv_bff NOSUPERUSER");
      assertRefusedWhile("ALTER ROLE liv_bff BYPASSRLS", "ALTER ROLE liv_bff NOBYPASSRLS");
      assertRefusedWhile("ALTER ROLE liv_bff CREATEDB", "ALTER ROLE liv_bff NOCREATEDB");
      assertRefusedWhile("ALTER ROLE liv_bff CREATEROLE", "ALTER ROLE liv_bff NOCREATEROLE");
      assertRefusedWhile("ALTER ROLE liv_bff REPLICATION", "ALTER ROLE liv_bff NOREPLICATION");
      assertRefusedWhile("GRANT liv_bff_owner TO liv_bff", "REVOKE liv_bff_owner FROM liv_bff");
      assertRefusedWhile(
          "CREATE ROLE bff_test_group; GRANT bff_test_group TO liv_bff",
          "DROP ROLE bff_test_group");
      assertThat(freshConnectionFailure()).as("liv_bff restaurado").isNull();
    }

    @Test
    @DisplayName("001-11 · Otro usuario que asume liv_bff (session_user distinto) no se conecta")
    void refusesAnotherSessionUser() {
      Throwable failure =
          catchThrowable(() -> {
            try (HikariDataSource pool =
                pool("postgres", "postgres-test-pw", "?options=-c%20role%3Dliv_bff")) {
              pool.getConnection().close();
            }
          });
      assertThat(failure).hasStackTraceContaining(REFUSED);
    }

    /** A pool of its own, with the check the application has, so each case opens a new one. */
    private HikariDataSource pool(String user, String password, String urlSuffix) {
      HikariDataSource pool = new HikariDataSource();
      pool.setJdbcUrl(BffDatabase.jdbcUrl() + urlSuffix);
      pool.setUsername(user);
      pool.setPassword(password);
      pool.setMaximumPoolSize(1);
      pool.setConnectionInitSql(((HikariDataSource) dataSource).getConnectionInitSql());
      return pool;
    }

    private Throwable freshConnectionFailure() {
      return catchThrowable(() -> {
        try (HikariDataSource pool =
            pool(BffDatabase.APP, BffDatabase.password(BffDatabase.APP), "")) {
          pool.getConnection().close();
        }
      });
    }

    private void assertRefusedWhile(String change, String revert) throws SQLException {
      BffDatabase.asSuperuser("postgres", change);
      try {
        assertThat(freshConnectionFailure()).as(change).hasStackTraceContaining(REFUSED);
      } finally {
        BffDatabase.asSuperuser("postgres", revert);
      }
    }
  }

  @Nested
  @ActiveProfiles("test")
  @SpringBootTest(
      webEnvironment = WebEnvironment.MOCK,
      properties = {
        // The check is imposed in code: a property cannot remove it.
        "spring.datasource.hikari.connection-init-sql=SELECT 1"
      })
  @DirtiesContext
  @DisplayName("Con otro rol")
  class AsAnotherRole {

    @Autowired DataSource dataSource;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", BffDatabase::jdbcUrl);
      registry.add("spring.datasource.username", () -> BffDatabase.OWNER);
      registry.add("spring.datasource.password", () -> BffDatabase.password(BffDatabase.OWNER));
      registry.add("spring.flyway.password", () -> BffDatabase.password(BffDatabase.OWNER));
    }

    @Test
    @DisplayName("001-11 · Con el dueño del esquema no se obtiene ninguna conexión")
    void refusesTheOwner() {
      Throwable failure = catchThrowable(() -> dataSource.getConnection().close());

      assertThat(failure).hasStackTraceContaining(REFUSED + ", no como liv_bff_owner");
    }
  }
}
