package co.com.sapenlinea.liv.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real para las pruebas de base de datos: crea los roles con el mismo script de {@code
 * dev/initdb}, aplica las migraciones como {@code liv_owner} (igual que el despliegue) y entrega
 * conexiones con el rol que pida cada prueba. Un solo contenedor por ejecución de pruebas.
 */
final class LivDatabase {

  static final String OWNER = "liv_owner";
  static final String APP = "liv_app";
  static final String BFF = "liv_bff";
  static final String OWNER_PASSWORD = "owner-test-pw";

  static final UUID ADMIN_A = UUID.fromString("10000000-0000-0000-0000-00000000000a");
  static final UUID ADMIN_B = UUID.fromString("10000000-0000-0000-0000-00000000000b");
  static final UUID USER_A = UUID.fromString("20000000-0000-0000-0000-00000000000a");
  static final UUID USER_B = UUID.fromString("20000000-0000-0000-0000-00000000000b");
  static final String ISSUER = "https://idp.test/realms/liv";
  static final String SUBJECT_A = "subject-a";
  static final String SUBJECT_B = "subject-b";

  private static final String IMAGE =
      "postgres:18.6@sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81";
  private static final String INIT_SCRIPT = "dev/initdb/01-roles-y-bases.sh";

  private static final PostgreSQLContainer POSTGRES = startPostgres();

  static {
    migrate();
    seedAdministrations();
  }

  private LivDatabase() {}

  @FunctionalInterface
  interface SqlWork<T> {
    T apply(Connection connection) throws SQLException;
  }

  static String jdbcUrl() {
    return "jdbc:postgresql://%s:%d/liv"
        .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432));
  }

  static String password(String role) {
    return OWNER.equals(role) ? OWNER_PASSWORD : role + "-test-pw";
  }

  /** Una transacción con el rol dado y, si hay administración, su contexto RLS local. */
  static <T> T inTx(String role, UUID administration, SqlWork<T> work) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), role, password(role))) {
      connection.setAutoCommit(false);
      try {
        if (administration != null) {
          setContext(connection, administration);
        }
        T result = work.apply(connection);
        connection.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        connection.rollback();
        throw e;
      }
    }
  }

  static void setContext(Connection connection, UUID administration) throws SQLException {
    update(
        connection,
        "SELECT set_config('app.administration_id', ?, true)",
        administration.toString());
  }

  static int update(String role, UUID administration, String sql, Object... args)
      throws SQLException {
    return inTx(role, administration, c -> update(c, sql, args));
  }

  static int update(Connection connection, String sql, Object... args) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, args);
      statement.execute();
      return Math.max(statement.getUpdateCount(), 0);
    }
  }

  static List<Object[]> rows(Connection connection, String sql, Object... args)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, args);
      List<Object[]> rows = new ArrayList<>();
      try (ResultSet resultSet = statement.executeQuery()) {
        int columns = resultSet.getMetaData().getColumnCount();
        while (resultSet.next()) {
          Object[] row = new Object[columns];
          for (int i = 0; i < columns; i++) {
            row[i] = resultSet.getObject(i + 1);
          }
          rows.add(row);
        }
      }
      return rows;
    }
  }

  static List<Object[]> rows(String role, UUID administration, String sql, Object... args)
      throws SQLException {
    return inTx(role, administration, c -> rows(c, sql, args));
  }

  static long count(String role, UUID administration, String sql, Object... args)
      throws SQLException {
    return ((Number) rows(role, administration, sql, args).get(0)[0]).longValue();
  }

  static SQLException failureOf(ThrowingCallable action) {
    Throwable thrown = catchThrowable(action);
    assertThat(thrown).as("la operación debía fallar").isInstanceOf(SQLException.class);
    return (SQLException) thrown;
  }

  private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
    for (int i = 0; i < args.length; i++) {
      statement.setObject(i + 1, args[i]);
    }
  }

  private static PostgreSQLContainer startPostgres() {
    PostgreSQLContainer container =
        new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withUsername("postgres")
            .withPassword("postgres-test-pw");
    container.withEnv("LIV_OWNER_PASSWORD", OWNER_PASSWORD);
    container.withEnv("LIV_APP_PASSWORD", password(APP));
    container.withEnv("LIV_BFF_OWNER_PASSWORD", password("liv_bff_owner"));
    container.withEnv("LIV_BFF_PASSWORD", password(BFF));
    container.withEnv("KEYCLOAK_DB_PASSWORD", password("keycloak"));
    container.withCopyToContainer(
        Transferable.of(initScript(), 0755), "/docker-entrypoint-initdb.d/01-roles-y-bases.sh");
    container.start();
    return container;
  }

  /** Mismo script que el entorno local, con fin de línea LF aunque el checkout sea CRLF. */
  private static byte[] initScript() {
    try {
      String script = Files.readString(Path.of(INIT_SCRIPT), StandardCharsets.UTF_8);
      return script.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo leer " + INIT_SCRIPT, e);
    }
  }

  private static void migrate() {
    Flyway.configure()
        .dataSource(jdbcUrl(), OWNER, OWNER_PASSWORD)
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  /**
   * Dos administraciones ficticias con un usuario cada una; las crea el dueño (no está sujeto a
   * RLS).
   */
  private static void seedAdministrations() {
    try {
      update(
          OWNER,
          null,
          """
          INSERT INTO core.territorial_entity (id, dane_code, name, entity_type) VALUES
            ('30000000-0000-0000-0000-00000000000a', 'T-A', 'Territorio A', 'MUNICIPIO'),
            ('30000000-0000-0000-0000-00000000000b', 'T-B', 'Territorio B', 'MUNICIPIO')
          """);
      update(
          OWNER,
          null,
          """
          INSERT INTO core.tax_administration (id, territorial_entity_id, name) VALUES
            ('10000000-0000-0000-0000-00000000000a', '30000000-0000-0000-0000-00000000000a', 'Admin A'),
            ('10000000-0000-0000-0000-00000000000b', '30000000-0000-0000-0000-00000000000b', 'Admin B')
          """);
      String user =
          """
          INSERT INTO core.admin_user (id, administration_id, full_name, email, status,
                                       identity_issuer, identity_subject)
          VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
          """;
      update(OWNER, null, user, USER_A, ADMIN_A, "Usuario A", "a@test.invalid", ISSUER, SUBJECT_A);
      update(OWNER, null, user, USER_B, ADMIN_B, "Usuario B", "b@test.invalid", ISSUER, SUBJECT_B);
    } catch (SQLException e) {
      throw new IllegalStateException("No se pudieron sembrar las administraciones de prueba", e);
    }
  }
}
