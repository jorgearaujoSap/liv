package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ISSUER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.SUBJECT_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.SUBJECT_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.count;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.inTx;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 001-17: con el rol real {@code liv_app}, cada administración solo ve y escribe lo suyo. */
class AdministrationIsolationTest {

  private static final String SESSION_SQL =
      """
      INSERT INTO core.user_session (sid, user_id, administration_id, identity_issuer,
                                     identity_subject, started_at)
      VALUES (?, ?, ?, ?, ?, now())
      """;

  private static final UUID SID_A = UUID.randomUUID();
  private static final UUID SID_B = UUID.randomUUID();

  @BeforeAll
  static void createSessions() throws SQLException {
    update(OWNER, null, SESSION_SQL, SID_A.toString(), USER_A, ADMIN_A, ISSUER, SUBJECT_A);
    update(OWNER, null, SESSION_SQL, SID_B.toString(), USER_B, ADMIN_B, ISSUER, SUBJECT_B);
  }

  @Test
  @DisplayName(
      "001-17 · Sin contexto, liv_app no ve ninguna fila de administraciones, usuarios ni sesiones")
  void noContextSeesNothing() throws SQLException {
    for (String table : new String[] {"tax_administration", "admin_user", "user_session"}) {
      assertThat(count(APP, null, "SELECT count(*) FROM core." + table)).as(table).isZero();
    }
  }

  @Test
  @DisplayName("001-17 · Un contexto vacío (resto de una conexión del pool) tampoco ve filas")
  void emptyContextSeesNothing() throws SQLException {
    long visible =
        inTx(
            APP,
            null,
            c -> {
              update(c, "SELECT set_config('app.administration_id', '', true)");
              return (Long) rows(c, "SELECT count(*) FROM core.admin_user").get(0)[0];
            });
    assertThat(visible).isZero();
  }

  @Test
  @DisplayName("001-17 · Con el contexto de A solo se ven las filas de A")
  void contextSeesOnlyItsOwnRows() throws SQLException {
    assertThat(rows(APP, ADMIN_A, "SELECT id FROM core.tax_administration"))
        .extracting(row -> row[0])
        .containsExactly(ADMIN_A);
    assertThat(rows(APP, ADMIN_A, "SELECT id FROM core.admin_user"))
        .extracting(row -> row[0])
        .containsExactly(USER_A);
    assertThat(rows(APP, ADMIN_A, "SELECT sid FROM core.user_session"))
        .extracting(row -> row[0].toString())
        .containsExactly(SID_A.toString());
  }

  @Test
  @DisplayName("001-17 · Con el contexto de A no se puede crear ni modificar nada de B")
  void contextCannotWriteOtherAdministration() throws SQLException {
    String insert =
        "INSERT INTO core.admin_user (id, administration_id, full_name, email, status,"
            + " identity_issuer, identity_subject) VALUES (?, ?, 'X', 'x@test.invalid', 'ACTIVE', ?, ?)";
    SQLException denied =
        failureOf(
            () -> update(APP, ADMIN_A, insert, UUID.randomUUID(), ADMIN_B, ISSUER, "intruso-b"));
    assertThat(denied.getMessage()).contains("row-level security");

    int updated =
        update(
            APP, ADMIN_A, "UPDATE core.admin_user SET last_login_at = now() WHERE id = ?", USER_B);
    assertThat(updated).as("filas de B modificadas desde A").isZero();
  }

  @Test
  @DisplayName(
      "001-17 · Con el contexto de A se puede dar de alta y registrar el último ingreso de un usuario de A")
  void contextCanWriteOwnAdministration() throws SQLException {
    String subject = "nuevo-" + UUID.randomUUID();
    update(
        APP,
        ADMIN_A,
        "INSERT INTO core.admin_user (id, administration_id, full_name, email, status,"
            + " identity_issuer, identity_subject) VALUES (?, ?, 'Nuevo', 'n@test.invalid', 'ACTIVE', ?, ?)",
        UUID.randomUUID(),
        ADMIN_A,
        ISSUER,
        subject);
    assertThat(
            update(
                APP,
                ADMIN_A,
                "UPDATE core.admin_user SET last_login_at = now() WHERE id = ?",
                USER_A))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "001-17 · Una sesión no puede apuntar a un usuario de otra administración (FK compuesta)")
  void sessionCannotMixAdministrations() {
    SQLException violation =
        failureOf(() ->
            update(
                OWNER,
                null,
                SESSION_SQL,
                UUID.randomUUID().toString(),
                USER_A,
                ADMIN_B,
                ISSUER,
                SUBJECT_A));
    assertThat(violation.getSQLState()).isEqualTo("23503");
  }

  @Test
  @DisplayName(
      "001-17 · El contexto es local a la transacción: no queda nada en la conexión al terminar")
  void contextDoesNotSurviveTheTransaction() throws Exception {
    try (var connection =
        java.sql.DriverManager.getConnection(
            LivDatabase.jdbcUrl(), APP, LivDatabase.password(APP))) {
      connection.setAutoCommit(false);
      LivDatabase.setContext(connection, ADMIN_A);
      assertThat(rows(connection, "SELECT count(*) FROM core.admin_user").get(0)[0]).isEqualTo(1L);
      connection.commit();
      assertThat(rows(connection, "SELECT count(*) FROM core.admin_user").get(0)[0]).isEqualTo(0L);
    }
  }
}
