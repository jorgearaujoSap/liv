package co.com.sapenlinea.liv.bff.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL real para las pruebas del BFF. Prepara lo que entrega la infraestructura (doc. 02
 * §5.2): los roles y, por base, el esquema {@code bff} de {@code liv_bff_owner}, con los mismos
 * permisos por defecto que fija el entorno local del backend. El BFF no lee ese script: es un
 * proyecto aparte, así que lo repite aquí. Un solo contenedor por ejecución de pruebas.
 */
public final class BffDatabase {

  static final String OWNER = "liv_bff_owner";
  static final String APP = "liv_bff";
  static final String API = "liv_app";

  private static final String IMAGE =
      "postgres:18.6@sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81";

  private static final PostgreSQLContainer POSTGRES = startPostgres();

  static {
    try {
      for (String role : new String[] {OWNER, APP, API}) {
        asSuperuser(
            "postgres",
            "CREATE ROLE %s LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '%s'"
                .formatted(role, password(role)));
      }
      createDatabase("liv");
    } catch (SQLException e) {
      throw new IllegalStateException("No se pudo preparar la base de pruebas", e);
    }
  }

  private BffDatabase() {}

  /** La base {@code liv} vacía, como la deja la infraestructura, para arrancar la aplicación. */
  public static String jdbcUrl() {
    return jdbcUrl("liv");
  }

  public static String password(String role) {
    return role + "-test-pw";
  }

  static String jdbcUrl(String database) {
    return "jdbc:postgresql://%s:%d/%s"
        .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), database);
  }

  /** Una base nueva como la entrega la infraestructura, con el esquema bff y sin migrar. */
  static String createDatabase(String name) throws SQLException {
    asSuperuser("postgres", "CREATE DATABASE " + name);
    asSuperuser(
        name,
        """
        REVOKE ALL ON DATABASE %1$s FROM PUBLIC;
        GRANT CONNECT ON DATABASE %1$s TO liv_app, liv_bff_owner, liv_bff;
        REVOKE ALL ON SCHEMA public FROM PUBLIC;
        CREATE SCHEMA bff AUTHORIZATION liv_bff_owner;
        GRANT USAGE ON SCHEMA bff TO liv_bff;
        ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff
          GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO liv_bff;
        ALTER DEFAULT PRIVILEGES FOR ROLE liv_bff_owner IN SCHEMA bff
          GRANT USAGE, SELECT ON SEQUENCES TO liv_bff;
        """
            .formatted(name));
    return jdbcUrl(name);
  }

  /** Aplica las migraciones como el despliegue: con liv_bff_owner y el historial en bff. */
  static void migrate(String url) {
    Flyway.configure()
        .dataSource(url, OWNER, password(OWNER))
        .schemas("bff")
        .defaultSchema("bff")
        .createSchemas(false)
        .load()
        .migrate();
  }

  /** DDL con el superusuario del contenedor: el papel de la infraestructura. */
  static void asSuperuser(String database, String sql) throws SQLException {
    try (Connection superuser =
            DriverManager.getConnection(jdbcUrl(database), "postgres", "postgres-test-pw");
        var statement = superuser.createStatement()) {
      statement.execute(sql);
    }
  }

  /** Ejecuta una sentencia con el rol dado. */
  static void execute(String url, String role, String sql) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url, role, password(role));
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /** El primer valor de una consulta con el rol dado. */
  static String queryValue(String url, String role, String select) throws SQLException {
    try (Connection connection = DriverManager.getConnection(url, role, password(role));
        var statement = connection.createStatement();
        ResultSet result = statement.executeQuery(select)) {
      return result.next() ? result.getString(1) : null;
    }
  }

  private static PostgreSQLContainer startPostgres() {
    PostgreSQLContainer postgres =
        new PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres-test-pw");
    postgres.start();
    return postgres;
  }
}
