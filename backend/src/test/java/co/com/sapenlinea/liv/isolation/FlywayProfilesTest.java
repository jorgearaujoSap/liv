package co.com.sapenlinea.liv.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flyway solo corre en los perfiles dev y test, con liv_owner; la aplicación usa liv_app. Cada
 * perfil con Flyway migra una base vacía propia: así la prueba demuestra que lo hizo Spring y que
 * las migraciones parten de cero. (Los campos {@code @Autowired} son la forma de Spring en las
 * pruebas {@code @Nested}; la regla de inyección por constructor es del código de producción.)
 */
@Tag("001-17")
class FlywayProfilesTest {

  @Nested
  @SpringBootTest(
      properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/sin-base",
        "liv.security.issuer-uri=http://localhost:1/sin-proveedor",
        "spring.datasource.password=sin-uso"
      })
  class DefaultProfile {

    @Autowired ObjectProvider<Flyway> flyway;

    @Test
    @DisplayName(
        "T10 · Sin perfil, Flyway está desactivado: en staging y producción migra la imagen liv-migrations")
    void flywayIsOffByDefault() {
      assertThat(flyway.getIfAvailable()).isNull();
    }
  }

  @Nested
  @SpringBootTest
  @ActiveProfiles("test")
  class TestProfile {

    private static final String DATABASE_URL = freshDatabase("liv_spring_test");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registerDatabase(registry, DATABASE_URL);
    }

    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;

    @Test
    @DisplayName(
        "T10 · En una base vacía la primera migración fija el endurecimiento: sin TEMP, sin CREATE en public y funciones sin PUBLIC")
    void firstMigrationHardensAnEmptyDatabase() throws SQLException {
      try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
        assertThat(
                LivDatabase.rows(
                        connection,
                        """
                        SELECT has_database_privilege('liv_app', current_database(), 'TEMP'),
                               has_database_privilege('public', current_database(), 'TEMP'),
                               has_schema_privilege('public', 'public', 'USAGE'),
                               EXISTS (SELECT 1 FROM pg_default_acl d
                                        WHERE d.defaclrole = 'liv_owner'::regrole
                                          AND d.defaclobjtype = 'f'
                                          AND NOT EXISTS (SELECT 1 FROM aclexplode(d.defaclacl) a
                                                           WHERE a.grantee = 0))
                        """)
                    .get(0))
            .containsExactly(false, false, false, true);
      }
    }

    @Test
    @DisplayName(
        "T10 · Con el perfil test, Flyway migra una base vacía con liv_owner y la aplicación se conecta como liv_app")
    void flywayMigratesAnEmptyDatabaseAsTheOwner() throws SQLException {
      assertThat(appliedVersions(flyway))
          .containsExactly(
              "202611010900",
              "202611010905",
              "202611010910",
              "202611010915",
              "202611010920",
              "202611031000",
              "202611031010",
              "202611131000",
              "202611131010",
              "202611141000");
      assertThat(userOf(flyway.getConfiguration().getDataSource())).isEqualTo(LivDatabase.OWNER);
      assertThat(userOf(dataSource)).isEqualTo(LivDatabase.APP);
      try (Connection connection = dataSource.getConnection()) {
        assertThat(
                LivDatabase.rows(connection, "SELECT count(*) FROM core.tax_administration")
                    .get(0)[0])
            .isEqualTo(0L);
      }
    }
  }

  @Nested
  @SpringBootTest
  @ActiveProfiles("dev")
  class DevProfile {

    private static final String DATABASE_URL = freshDatabase("liv_spring_dev");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
      registerDatabase(registry, DATABASE_URL);
      registry.add("LIV_OWNER_PASSWORD", () -> LivDatabase.OWNER_PASSWORD);
      registry.add("LIV_APP_PASSWORD", () -> LivDatabase.password(LivDatabase.APP));
    }

    @Autowired Flyway flyway;

    @Test
    @DisplayName("T10 · Con el perfil dev, Flyway también aplica la semilla ficticia de dev/seed")
    void devProfileAppliesTheSeed() throws SQLException {
      assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getDescription))
          .contains("core dev seed");
      try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
        assertThat(
                LivDatabase.rows(
                        connection,
                        "SELECT count(*) FROM core.tax_administration WHERE name = 'Municipio de Ipiales'")
                    .get(0)[0])
            .isEqualTo(1L);
      }
    }
  }

  private static String freshDatabase(String name) {
    try {
      return LivDatabase.createEmptyDatabase(name);
    } catch (SQLException e) {
      throw new IllegalStateException("No se pudo crear la base " + name, e);
    }
  }

  private static void registerDatabase(DynamicPropertyRegistry registry, String url) {
    registry.add("spring.datasource.url", () -> url);
    registry.add("liv.security.issuer-uri", () -> "http://localhost:1/sin-proveedor");
    registry.add("spring.datasource.password", () -> LivDatabase.password(LivDatabase.APP));
    registry.add("spring.flyway.url", () -> url);
    registry.add("spring.flyway.password", () -> LivDatabase.OWNER_PASSWORD);
  }

  private static String[] appliedVersions(Flyway flyway) {
    return Arrays.stream(flyway.info().applied())
        .filter(migration -> migration.getVersion() != null)
        .map(migration -> migration.getVersion().getVersion())
        .toArray(String[]::new);
  }

  private static String userOf(DataSource dataSource) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      return connection.getMetaData().getUserName();
    }
  }
}
