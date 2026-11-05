package co.com.sapenlinea.liv.bff.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real para las pruebas del BFF. Prepara lo que debe entregar la infraestructura
 * (README, "Base de datos"): los roles y, por base, el esquema {@code bff} de {@code
 * liv_bff_owner}, sin permisos para nadie más. Una base "local" agrega además los permisos que
 * concede el entorno de desarrollo del backend; el BFF no lee ese script porque es un proyecto
 * aparte, así que los repite aquí. Un solo contenedor por ejecución de pruebas.
 */
public final class BffDatabase {

  public static final String OWNER = "liv_bff_owner";
  public static final String APP = "liv_bff";
  public static final String API = "liv_app";

  /** Una base y su URL de JDBC. */
  record Database(String name, String url) {}

  private static final String IMAGE =
      "postgres:18.6@sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81";

  private static final String SUPERUSER = "postgres";
  private static final String SUPERUSER_PASSWORD = "postgres-test-pw";

  private static final AtomicInteger DATABASES = new AtomicInteger();

  private static final PostgreSQLContainer POSTGRES = startPostgres();

  private static final Database LIV;

  static {
    try {
      for (String role : new String[] {OWNER, APP, API}) {
        asSuperuser(
            SUPERUSER,
            "CREATE ROLE %s LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '%s'"
                .formatted(role, password(role)));
      }
      LIV = newLocalDatabase();
    } catch (SQLException e) {
      throw new IllegalStateException("No se pudo preparar la base de pruebas", e);
    }
  }

  private BffDatabase() {}

  /** Una base como la del entorno local, sin migrar, para arrancar la aplicación. */
  public static String jdbcUrl() {
    return LIV.url();
  }

  public static String password(String role) {
    return role + "-test-pw";
  }

  /**
   * Una base nueva con solo lo que debe entregar la infraestructura: sin permisos para PUBLIC y el
   * esquema bff de liv_bff_owner, sin migrar.
   */
  static Database newDatabase() throws SQLException {
    String name = "bff_test_" + DATABASES.incrementAndGet();
    asSuperuser(SUPERUSER, "CREATE DATABASE " + name);
    asSuperuser(
        name,
        """
        REVOKE ALL ON DATABASE %1$s FROM PUBLIC;
        GRANT CONNECT ON DATABASE %1$s TO liv_app, liv_bff_owner, liv_bff;
        REVOKE ALL ON SCHEMA public FROM PUBLIC;
        CREATE SCHEMA bff AUTHORIZATION liv_bff_owner;
        """
            .formatted(name));
    return new Database(name, jdbcUrl(name));
  }

  /** Una base nueva con además los permisos que concede el entorno local del backend. */
  static Database newLocalDatabase() throws SQLException {
    Database database = newDatabase();
    asSuperuser(
        database.name(),
        """
        GRANT USAGE ON SCHEMA bff TO liv_bff;
        ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff
          GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO liv_bff;
        ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff
          GRANT USAGE, SELECT ON SEQUENCES TO liv_bff;
        ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
        """);
    return database;
  }

  /**
   * Aplica las migraciones como el despliegue, con liv_bff_owner y el historial en bff. Repite la
   * configuración de Flyway de application.yml; MigrationsOnStartupTest prueba la de verdad.
   */
  static void migrate(String url) {
    migrate(url, OWNER);
  }

  /** Aplica las migraciones con otro rol, para probar que solo corren como liv_bff_owner. */
  static void migrate(String url, String role) {
    Flyway.configure()
        .dataSource(url, role, password(role))
        .schemas("bff")
        .defaultSchema("bff")
        .createSchemas(false)
        .load()
        .migrate();
  }

  /** DDL con el superusuario del contenedor: el papel de la infraestructura. */
  static void asSuperuser(String database, String sql) throws SQLException {
    try (Connection superuser =
            DriverManager.getConnection(jdbcUrl(database), SUPERUSER, SUPERUSER_PASSWORD);
        var statement = superuser.createStatement()) {
      statement.execute(sql);
    }
  }

  /** Ejecuta una sentencia con el rol dado. */
  public static void execute(String url, String role, String sql) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url, role, password(role));
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /** El primer valor de una consulta con el rol dado. */
  public static String queryValue(String url, String role, String select) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url, role, password(role));
        var statement = connection.createStatement();
        ResultSet result = statement.executeQuery(select)) {
      return result.next() ? result.getString(1) : null;
    }
  }

  private static String jdbcUrl(String database) {
    return "jdbc:postgresql://%s:%d/%s"
        .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
  }

  private static PostgreSQLContainer startPostgres() {
    PostgreSQLContainer postgres =
        new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName(SUPERUSER)
            .withUsername(SUPERUSER)
            .withPassword(SUPERUSER_PASSWORD);
    postgres.start();
    return postgres;
  }
}
