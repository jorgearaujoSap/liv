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
import static co.com.sapenlinea.liv.isolation.LivDatabase.assertRlsDenied;
import static co.com.sapenlinea.liv.isolation.LivDatabase.count;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.inTx;
import static co.com.sapenlinea.liv.isolation.LivDatabase.insertUser;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** 001-17: con el rol real {@code liv_app}, cada administración solo ve y escribe lo suyo. */
@Tag("001-17")
class AdministrationIsolationTest {

  private static final String[] ISOLATED_TABLES = {
    "core.tax_administration",
    "core.admin_user",
    "core.user_session",
    "tax.administration_fiscal_year"
  };

  private static final String FISCAL_YEAR_SQL =
      """
      INSERT INTO tax.administration_fiscal_year (administration_id, fiscal_year, created_by)
      VALUES (?, ?, ?)
      """;

  private static final String SESSION_SQL =
      """
      INSERT INTO core.user_session (sid, user_id, administration_id, identity_issuer,
                                     identity_subject, started_at)
      VALUES (?, ?, ?, ?, ?, now())
      """;

  private static final String CLOSE_SQL =
      "UPDATE core.user_session SET ended_at = now(), end_reason = ? WHERE sid = ?";

  private static final String SID_A = "sid-a-" + UUID.randomUUID();
  private static final String SID_B = "sid-b-" + UUID.randomUUID();

  @BeforeAll
  static void createSessions() throws SQLException {
    update(OWNER, null, SESSION_SQL, SID_A, USER_A, ADMIN_A, ISSUER, SUBJECT_A);
    update(OWNER, null, SESSION_SQL, SID_B, USER_B, ADMIN_B, ISSUER, SUBJECT_B);
  }

  /** A y B tienen cada una su 2026 (007-18); las crea el dueño, que no pasa por la RLS. */
  @BeforeAll
  static void createFiscalYears() throws SQLException {
    update(
        OWNER,
        null,
        FISCAL_YEAR_SQL + " ON CONFLICT (administration_id, fiscal_year) DO NOTHING",
        ADMIN_A,
        2026,
        USER_A);
    update(
        OWNER,
        null,
        FISCAL_YEAR_SQL + " ON CONFLICT (administration_id, fiscal_year) DO NOTHING",
        ADMIN_B,
        2026,
        USER_B);
  }

  /** Quita las vigencias de esta clase: así las pruebas de otras clases parten sin ellas. */
  @AfterAll
  static void removeTheFiscalYearsOfThisClass() throws SQLException {
    update(
        OWNER,
        null,
        "DELETE FROM tax.administration_fiscal_year WHERE fiscal_year IN (2026, 2031, 2090)"
            + " AND administration_id IN ('"
            + ADMIN_A
            + "', '"
            + ADMIN_B
            + "')");
  }

  private static String newSid() {
    return "sid-" + UUID.randomUUID();
  }

  @Test
  @DisplayName(
      "001-17 · Sin contexto, liv_app no ve ninguna fila de administraciones, usuarios ni sesiones")
  void noContextSeesNothing() throws SQLException {
    for (String table : ISOLATED_TABLES) {
      assertThat(count(OWNER, null, "SELECT count(*) FROM " + table))
          .as("filas de " + table + " que la RLS tiene que ocultar")
          .isPositive();
      assertThat(count(APP, null, "SELECT count(*) FROM " + table)).as(table).isZero();
    }
  }

  @Test
  @DisplayName("001-17 · Un contexto vacío (resto de una conexión del pool) tampoco ve filas")
  void emptyContextSeesNothing() throws SQLException {
    for (String table : ISOLATED_TABLES) {
      Object visible =
          inTx(
              APP,
              null,
              c -> {
                update(c, "SELECT set_config('app.administration_id', '', true)");
                return rows(c, "SELECT count(*) FROM " + table).get(0)[0];
              });
      assertThat(visible).as(table).isEqualTo(0L);
    }
  }

  @Test
  @DisplayName("001-17 · Con el contexto de A solo se ven las filas de A")
  void contextSeesOnlyItsOwnRows() throws SQLException {
    assertThat(rows(APP, ADMIN_A, "SELECT id FROM core.tax_administration"))
        .extracting(row -> row[0])
        .containsExactly(ADMIN_A);
    assertThat(rows(APP, ADMIN_A, "SELECT administration_id FROM core.admin_user"))
        .extracting(row -> row[0])
        .isNotEmpty()
        .containsOnly(ADMIN_A);
    assertThat(rows(APP, ADMIN_A, "SELECT id FROM core.admin_user"))
        .extracting(row -> row[0])
        .contains(USER_A)
        .doesNotContain(USER_B);
    assertThat(rows(APP, ADMIN_A, "SELECT sid FROM core.user_session"))
        .extracting(row -> row[0])
        .contains(SID_A)
        .doesNotContain(SID_B);
  }

  @Test
  @DisplayName("001-17 · Con el contexto de A no se puede crear ni modificar un usuario de B")
  void contextCannotWriteOtherAdministrationUsers() throws SQLException {
    SQLException denied =
        failureOf(() -> insertUser(APP, ADMIN_A, ADMIN_B, UUID.randomUUID(), "intruso", "ACTIVE"));
    assertThat(denied.getMessage()).contains("row-level security");

    int updated =
        update(
            APP, ADMIN_A, "UPDATE core.admin_user SET last_login_at = now() WHERE id = ?", USER_B);
    assertThat(updated).as("filas de B modificadas desde A").isZero();
  }

  @Test
  @DisplayName("001-17 · Sin contexto no se puede crear un usuario")
  void noContextCannotWriteUsers() {
    SQLException denied =
        failureOf(
            () -> insertUser(APP, null, ADMIN_A, UUID.randomUUID(), "sin-contexto", "ACTIVE"));
    assertThat(denied.getMessage()).contains("row-level security");
  }

  @Test
  @DisplayName(
      "001-17 · Con el contexto de A se puede dar de alta y registrar el último ingreso de un usuario de A")
  void contextCanWriteOwnAdministration() throws SQLException {
    insertUser(APP, ADMIN_A, UUID.randomUUID(), "nuevo-" + UUID.randomUUID(), "ACTIVE");

    int updated =
        update(
            APP, ADMIN_A, "UPDATE core.admin_user SET last_login_at = now() WHERE id = ?", USER_A);
    assertThat(updated).isEqualTo(1);
  }

  @Test
  @DisplayName("001-17 · Con el contexto de A no se puede abrir ni cerrar una sesión de B")
  void contextCannotTouchOtherAdministrationSessions() throws SQLException {
    SQLException denied =
        failureOf(
            () -> update(APP, ADMIN_A, SESSION_SQL, newSid(), USER_B, ADMIN_B, ISSUER, SUBJECT_B));
    assertThat(denied.getMessage()).contains("row-level security");

    assertThat(update(APP, ADMIN_A, CLOSE_SQL, "LOGOUT", SID_B))
        .as("sesiones de B cerradas desde A")
        .isZero();
  }

  @Test
  @DisplayName("001-17 · Una sesión cerrada no se puede reabrir ni cambiar de motivo")
  void closedSessionStaysClosed() throws SQLException {
    String sid = newSid();
    update(APP, ADMIN_A, SESSION_SQL, sid, USER_A, ADMIN_A, ISSUER, SUBJECT_A);
    assertThat(update(APP, ADMIN_A, CLOSE_SQL, "LOGOUT", sid)).isEqualTo(1);

    assertThat(update(APP, ADMIN_A, CLOSE_SQL, "IDLE", sid)).as("segundo cierre").isZero();
    assertThat(
            update(
                APP,
                ADMIN_A,
                "UPDATE core.user_session SET ended_at = NULL, end_reason = NULL WHERE sid = ?",
                sid))
        .as("reapertura")
        .isZero();
    assertThat(
            rows(APP, ADMIN_A, "SELECT end_reason FROM core.user_session WHERE sid = ?", sid)
                .get(0)[0])
        .isEqualTo("LOGOUT");
  }

  @Test
  @DisplayName("001-17 · Una sesión nace abierta: liv_app no puede insertar una sesión ya cerrada")
  void sessionCannotBeInsertedAlreadyClosed() {
    SQLException denied =
        failureOf(() ->
            update(
                APP,
                ADMIN_A,
                """
                INSERT INTO core.user_session (sid, user_id, administration_id, identity_issuer,
                                               identity_subject, started_at, ended_at, end_reason)
                VALUES (?, ?, ?, ?, ?, now(), now(), 'LOGOUT')
                """,
                newSid(),
                USER_A,
                ADMIN_A,
                ISSUER,
                SUBJECT_A));
    assertThat(denied.getMessage()).contains("row-level security");
  }

  @Test
  @DisplayName(
      "001-17 · Una sesión no puede apuntar a un usuario de otra administración ni a otra identidad (FK compuesta)")
  void sessionCannotMixAdministrationsOrIdentities() {
    SQLException otherAdministration =
        failureOf(
            () -> update(OWNER, null, SESSION_SQL, newSid(), USER_A, ADMIN_B, ISSUER, SUBJECT_A));
    assertThat(otherAdministration.getSQLState()).isEqualTo("23503");

    SQLException otherIdentity =
        failureOf(
            () -> update(OWNER, null, SESSION_SQL, newSid(), USER_A, ADMIN_A, ISSUER, SUBJECT_B));
    assertThat(otherIdentity.getSQLState()).isEqualTo("23503");
  }

  @Test
  @Tag("007-18")
  @DisplayName(
      "CA-12 · 007-18 · Con el contexto de A solo se ve la vigencia 2026 de A, y con el de B la de B")
  void contextSeesOnlyItsOwnFiscalYears() throws SQLException {
    String select = "SELECT administration_id, fiscal_year FROM tax.administration_fiscal_year";

    assertThat(rows(APP, ADMIN_A, select))
        .extracting(row -> row[0], row -> ((Number) row[1]).intValue())
        .contains(tuple(ADMIN_A, 2026))
        .doesNotContain(tuple(ADMIN_B, 2026));
    assertThat(rows(APP, ADMIN_A, select)).extracting(row -> row[0]).containsOnly(ADMIN_A);

    assertThat(rows(APP, ADMIN_B, select))
        .extracting(row -> row[0], row -> ((Number) row[1]).intValue())
        .contains(tuple(ADMIN_B, 2026))
        .doesNotContain(tuple(ADMIN_A, 2026));
    assertThat(rows(APP, ADMIN_B, select)).extracting(row -> row[0]).containsOnly(ADMIN_B);
  }

  @Test
  @Tag("007-19")
  @DisplayName(
      "CA-12 · 007-19 · Con el contexto de A no se crea ni se cambia una vigencia de B,"
          + " y sin contexto no se escribe")
  void contextCannotWriteOtherAdministrationFiscalYears() throws SQLException {
    assertRlsDenied(() -> update(APP, ADMIN_A, FISCAL_YEAR_SQL, ADMIN_B, 2027, USER_B));
    assertRlsDenied(() -> update(APP, null, FISCAL_YEAR_SQL, ADMIN_A, 2027, USER_A));
    // El 2026 de B ya existe: el rechazo es de la RLS y no de la clave única, y así el mensaje
    // no revela que esa vigencia existe.
    assertRlsDenied(() -> update(APP, ADMIN_A, FISCAL_YEAR_SQL, ADMIN_B, 2026, USER_B));
    assertThat(
            update(
                APP,
                null,
                """
                UPDATE tax.administration_fiscal_year SET status = 'EN_PREPARACION'
                 WHERE fiscal_year = 2026
                """))
        .as("vigencias abiertas sin contexto")
        .isZero();

    assertThat(
            update(
                APP,
                ADMIN_A,
                """
                UPDATE tax.administration_fiscal_year SET status = 'EN_PREPARACION'
                 WHERE administration_id = ? AND fiscal_year = 2026
                """,
                ADMIN_B))
        .as("vigencias de B abiertas desde A")
        .isZero();
    assertThat(
            rows(
                    OWNER,
                    null,
                    """
                    SELECT status FROM tax.administration_fiscal_year
                     WHERE administration_id = ? AND fiscal_year = 2026
                    """,
                    ADMIN_B)
                .get(0)[0])
        .isEqualTo("PLANEADA");
  }

  @Test
  @Tag("007-19")
  @DisplayName(
      "CA-12 · 007-19 · Cada administración abre solo su propia vigencia, aunque la consulta"
          + " no filtre por administración")
  void eachAdministrationOpensOnlyItsOwnFiscalYear() throws SQLException {
    update(APP, ADMIN_A, FISCAL_YEAR_SQL, ADMIN_A, 2090, USER_A);
    update(APP, ADMIN_B, FISCAL_YEAR_SQL, ADMIN_B, 2090, USER_B);

    assertThat(
            update(
                APP,
                ADMIN_B,
                """
                UPDATE tax.administration_fiscal_year
                   SET status = 'EN_PREPARACION', updated_by = ?, updated_at = now()
                 WHERE fiscal_year = 2090
                """,
                USER_B))
        .isEqualTo(1);

    String statusOfA =
        """
        SELECT status FROM tax.administration_fiscal_year
         WHERE administration_id = ? AND fiscal_year = 2090
        """;
    assertThat(rows(OWNER, null, statusOfA, ADMIN_A).get(0)[0]).isEqualTo("PLANEADA");
    assertThat(rows(OWNER, null, statusOfA, ADMIN_B).get(0)[0]).isEqualTo("EN_PREPARACION");
  }

  @Test
  @Tag("007-19")
  @DisplayName(
      "CA-12 · 007-19 · Con el contexto de A se crea una vigencia de A,"
          + " y no se puede firmar con un usuario de B")
  void contextCanCreateOwnFiscalYearOnlyWithOwnUsers() throws SQLException {
    assertThat(update(APP, ADMIN_A, FISCAL_YEAR_SQL, ADMIN_A, 2031, USER_A)).isEqualTo(1);

    SQLException mixed =
        failureOf(() -> update(APP, ADMIN_A, FISCAL_YEAR_SQL, ADMIN_A, 2032, USER_B));
    assertThat(mixed.getSQLState()).as("FK compuesta con created_by de B").isEqualTo("23503");
  }

  @Test
  @DisplayName(
      "001-17 · El contexto es local a la transacción: no queda nada en la conexión al terminar")
  void contextDoesNotSurviveTheTransaction() throws SQLException {
    try (Connection connection = LivDatabase.connect(APP)) {
      connection.setAutoCommit(false);
      LivDatabase.setContext(connection, ADMIN_A);
      assertThat(rows(connection, "SELECT count(*) FROM core.admin_user").get(0)[0])
          .isNotEqualTo(0L);
      connection.commit();
      assertThat(rows(connection, "SELECT count(*) FROM core.admin_user").get(0)[0]).isEqualTo(0L);
    }
  }
}
