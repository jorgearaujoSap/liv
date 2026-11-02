package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code audit.audit_event} es inmutable: ni la aplicación ni el dueño pueden cambiar o borrar
 * filas.
 */
@Tag("001-18")
class AuditAppendOnlyTest {

  private static final String INSERT =
      """
      INSERT INTO audit.audit_event (administration_id, entity_name, operation, source, source_event_id)
      VALUES (?, 'session', 'SESSION_STARTED', 'LIV', ?)
      """;

  private static final String EVENT_ID = "append-only-" + UUID.randomUUID();

  @BeforeAll
  static void createEvent() throws SQLException {
    update(APP, ADMIN_A, INSERT, ADMIN_A, EVENT_ID);
  }

  @Test
  @DisplayName(
      "001-18 · liv_app no puede modificar, borrar ni vaciar la auditoría: permiso denegado")
  void appCannotChangeOrDeleteEvents() {
    for (String sql :
        new String[] {
          "UPDATE audit.audit_event SET operation = 'X'",
          "DELETE FROM audit.audit_event",
          "TRUNCATE audit.audit_event"
        }) {
      SQLException denied = failureOf(() -> update(APP, ADMIN_A, sql));
      assertThat(denied.getSQLState()).as(sql).isEqualTo("42501");
      assertThat(denied.getMessage()).as(sql).contains("permission denied");
    }
  }

  @Test
  @DisplayName(
      "001-18 · Ni el dueño puede modificar, borrar ni vaciar la auditoría: la rechaza un disparador")
  void ownerCannotChangeOrDeleteEither() {
    String byEvent = " WHERE source_event_id = '" + EVENT_ID + "'";
    for (String sql :
        new String[] {
          "UPDATE audit.audit_event SET operation = 'X'" + byEvent,
          "DELETE FROM audit.audit_event" + byEvent,
          "TRUNCATE audit.audit_event"
        }) {
      SQLException rejected = failureOf(() -> update(OWNER, null, sql));
      assertThat(rejected.getSQLState()).as(sql).isEqualTo("23001");
      assertThat(rejected.getMessage()).as(sql).contains("append-only");
    }
  }

  @Test
  @DisplayName("001-18 · El evento original sigue intacto tras los intentos de cambio")
  void eventStaysIntact() throws SQLException {
    assertThat(
            rows(
                    APP,
                    ADMIN_A,
                    "SELECT operation FROM audit.audit_event WHERE source_event_id = ?",
                    EVENT_ID)
                .get(0)[0])
        .isEqualTo("SESSION_STARTED");
  }

  @Test
  @DisplayName(
      "001-18 · Un evento repetido (misma fuente e id) se rechaza: el consumidor es idempotente")
  void duplicateSourceEventIsRejected() throws SQLException {
    SQLException duplicate = failureOf(() -> update(APP, ADMIN_A, INSERT, ADMIN_A, EVENT_ID));
    assertThat(duplicate.getSQLState()).isEqualTo("23505");

    assertThat(update(APP, ADMIN_A, INSERT + " ON CONFLICT DO NOTHING", ADMIN_A, EVENT_ID))
        .as("con ON CONFLICT DO NOTHING el consumidor ignora el repetido")
        .isZero();
  }
}
