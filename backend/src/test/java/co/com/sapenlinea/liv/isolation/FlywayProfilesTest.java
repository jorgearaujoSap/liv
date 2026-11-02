package co.com.sapenlinea.liv.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flyway solo corre en los perfiles dev y de pruebas, y con liv_owner; la aplicación usa liv_app.
 */
class FlywayProfilesTest {

  @Nested
  @SpringBootTest
  class DefaultProfile {

    @Autowired ObjectProvider<Flyway> flyway;

    @Test
    @DisplayName(
        "Sin perfil, Flyway está desactivado: en staging y producción migra la imagen liv-migrations")
    void flywayIsOffByDefault() {
      assertThat(flyway.getIfAvailable()).isNull();
    }
  }

  @Nested
  @SpringBootTest
  @ActiveProfiles("test")
  class TestProfile {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
      registry.add("spring.datasource.username", () -> LivDatabase.APP);
      registry.add("spring.datasource.password", () -> LivDatabase.password(LivDatabase.APP));
      registry.add("spring.flyway.url", LivDatabase::jdbcUrl);
      registry.add("spring.flyway.password", () -> LivDatabase.OWNER_PASSWORD);
    }

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName(
        "Con el perfil test, Flyway migra con liv_owner y la aplicación se conecta como liv_app")
    void flywayUsesTheOwnerAndTheAppUsesLivApp() throws Exception {
      assertThat(flyway.info().pending()).isEmpty();
      try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
        assertThat(connection.getMetaData().getUserName()).isEqualTo(LivDatabase.OWNER);
      }
      try (var connection = dataSource.getConnection()) {
        assertThat(connection.getMetaData().getUserName()).isEqualTo(LivDatabase.APP);
      }
    }
  }
}
