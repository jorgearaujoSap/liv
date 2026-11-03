package co.com.sapenlinea.liv.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The application only works as {@code liv_app} (doc. 02 §5.2, T10 › T12): a deployment that
 * overrides the user (for example with {@code SPRING_DATASOURCE_USERNAME}) gets no connection, so
 * it can never read past RLS as the owner of the tables.
 */
@DisplayName("La aplicación solo se conecta como liv_app (T12)")
class LivAppConnectionTest {

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
  }

  @Nested
  @SpringBootTest(properties = "liv.security.issuer-uri=http://localhost:1/sin-proveedor")
  @DisplayName("Con otro rol")
  class AsAnotherRole {

    @Autowired DataSource dataSource;

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

      assertThat(failure).isNotNull();
      assertThat(failure).hasStackTraceContaining("liv_app");
    }
  }
}
