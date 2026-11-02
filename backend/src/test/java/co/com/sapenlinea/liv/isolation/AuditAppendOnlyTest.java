package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.insertAuditEvent;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code audit.audit_event} es de solo inserción: la aplicación no tiene el permiso y un disparador
 * rechaza el cambio de datos incluso al dueño. (El dueño sigue pudiendo cambiar el DDL, por ejemplo
 * desactivar el disparador: eso lo contiene la revisión de las migraciones, no la base.)
 */
@Tag("001-18")
class AuditAppendOnlyTest {

  private static String newEvent() throws SQLException {
    String id = "append-only-" + UUID.randomUUID();
    insertAuditEvent(APP, ADMIN_A, ADMIN_A, "LIV", "SESSION_STARTED", id, null);
    return id;
  }

  private static Object operationOf(String eventId) throws SQLException {
    return rows(
            APP,
            ADMIN_A,
            "SELECT operation FROM audit.audit_event WHERE source_event_id = ?",
            eventId)
        .get(0)[0];
  }

  @Test
  @DisplayName(
      "001-18 · liv_app no puede modificar, borrar ni vaciar la auditoría: permiso denegado, y el evento queda intacto")
  void appCannotChangeOrDeleteEvents() throws SQLException {
    String id = newEvent();
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
    assertThat(operationOf(id)).isEqualTo("SESSION_STARTED");
  }

  @Test
  @DisplayName(
      "001-18 · El dueño tampoco modifica, borra ni vacía la auditoría mientras exista el disparador")
  void ownerIsStoppedByTheTrigger() throws SQLException {
    String id = newEvent();
    String byEvent = " WHERE source_event_id = '" + id + "'";
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
    assertThat(operationOf(id)).isEqualTo("SESSION_STARTED");
  }

  @Test
  @DisplayName(
      "001-18 · Los disparadores de solo inserción están activos y son de la tabla de auditoría")
  void triggersAreEnabled() throws SQLException {
    assertThat(
            rows(
                    OWNER,
                    null,
                    """
                    SELECT t.tgname || ':' || t.tgenabled::text
                      FROM pg_trigger t
                     WHERE t.tgrelid = 'audit.audit_event'::regclass AND NOT t.tgisinternal
                     ORDER BY 1
                    """)
                .stream()
                .map(row -> row[0]))
        .containsExactly("audit_event_append_only:O", "audit_event_no_truncate:O");
  }

  @Test
  @DisplayName(
      "001-18 · Un evento repetido (misma fuente e id) se rechaza, o se ignora con ON CONFLICT DO NOTHING")
  void duplicateSourceEventIsRejected() throws SQLException {
    String id = newEvent();

    SQLException duplicate =
        failureOf(
            () -> insertAuditEvent(APP, ADMIN_A, ADMIN_A, "LIV", "SESSION_STARTED", id, null));
    assertThat(duplicate.getSQLState()).isEqualTo("23505");

    assertThat(
            update(
                APP,
                ADMIN_A,
                """
                INSERT INTO audit.audit_event (administration_id, entity_name, operation, source,
                                               source_event_id)
                VALUES (?, 'session', 'SESSION_STARTED', 'LIV', ?) ON CONFLICT DO NOTHING
                """,
                ADMIN_A,
                id))
        .isZero();
  }
}
