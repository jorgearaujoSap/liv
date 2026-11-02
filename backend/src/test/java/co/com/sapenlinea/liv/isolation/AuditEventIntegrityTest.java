package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Restricciones de integridad de {@code audit.audit_event}, probadas con el dueño (sin RLS). */
@Tag("001-18")
class AuditEventIntegrityTest {

  private static final String INSERT =
      """
      INSERT INTO audit.audit_event (user_id, administration_id, entity_name, operation, source,
                                     source_event_id, new_values)
      VALUES (?, ?, 'session', 'SESSION_STARTED', ?, ?, CAST(? AS jsonb))
      """;

  private static String eventId() {
    return "integrity-" + UUID.randomUUID();
  }

  @Test
  @DisplayName("001-18 · El origen del evento solo puede ser LIV o IDENTITY_PROVIDER")
  void sourceIsRestricted() {
    SQLException invalid =
        failureOf(() -> update(OWNER, null, INSERT, null, ADMIN_A, "OTRO", eventId(), "{}"));
    assertThat(invalid.getSQLState()).isEqualTo("23514");
  }

  @Test
  @DisplayName(
      "001-18 · Un evento no puede atribuirse a un usuario de otra administración (FK compuesta)")
  void userMustBelongToTheEventAdministration() {
    SQLException otherAdministration =
        failureOf(() -> update(OWNER, null, INSERT, USER_B, ADMIN_A, "LIV", eventId(), "{}"));
    assertThat(otherAdministration.getSQLState()).isEqualTo("23503");

    SQLException withoutAdministration =
        failureOf(() -> update(OWNER, null, INSERT, USER_B, null, "LIV", eventId(), "{}"));
    assertThat(withoutAdministration.getSQLState()).isEqualTo("23514");
  }

  @Test
  @DisplayName("001-18 · Un evento de B con su usuario se guarda")
  void userOfTheSameAdministrationIsAccepted() throws SQLException {
    assertThat(update(OWNER, null, INSERT, USER_B, ADMIN_B, "LIV", eventId(), "{}")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "001-18 · new_values tiene un tamaño máximo: la bitácora no es un almacén de volcados")
  void newValuesHaveAMaximumSize() {
    String huge = "{\"reason\":\"" + "x".repeat(9000) + "\"}";
    SQLException tooBig =
        failureOf(() -> update(OWNER, null, INSERT, null, ADMIN_A, "LIV", eventId(), huge));
    assertThat(tooBig.getSQLState()).isEqualTo("23514");
  }
}
