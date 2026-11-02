package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.assertRlsDenied;
import static co.com.sapenlinea.liv.isolation.LivDatabase.inTx;
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
 * Las filas de auditoría de plataforma ({@code administration_id} NULL) solo las escriben dos
 * caminos (ADR 0025): el importador, con {@code app.platform_job = 'on'}, y la función {@code
 * audit.record_platform_session_denied} (ver {@link RecordPlatformSessionDeniedTest}).
 */
@Tag("001-18")
class PlatformAuditRowsTest {

  private static final String PLATFORM_ROWS =
      "SELECT count(*) FROM audit.audit_event WHERE administration_id IS NULL";

  private static String eventId() {
    return "evt-" + UUID.randomUUID();
  }

  private static int importerEvent(String jobFlag) throws SQLException {
    return insertAuditEvent(
        APP, null, null, "IDENTITY_PROVIDER", "LOGIN_ERROR", eventId(), jobFlag);
  }

  @Test
  @DisplayName("001-18 · Con el contexto de A, liv_app registra eventos de A y no de B ni de nadie")
  void contextAdministrationOnly() throws SQLException {
    assertThat(insertAuditEvent(APP, ADMIN_A, ADMIN_A, "LIV", "SESSION_STARTED", eventId(), null))
        .isEqualTo(1);

    assertRlsDenied(
        () -> insertAuditEvent(APP, ADMIN_A, ADMIN_B, "LIV", "SESSION_STARTED", eventId(), null));
    assertRlsDenied(
        () -> insertAuditEvent(APP, ADMIN_A, null, "LIV", "SESSION_STARTED", eventId(), null));
    assertRlsDenied(
        () -> insertAuditEvent(APP, null, ADMIN_A, "LIV", "SESSION_STARTED", eventId(), null));
  }

  @Test
  @DisplayName(
      "001-18 · Una fila de plataforma solo entra si la transacción fijó app.platform_job = 'on'")
  void platformRowNeedsThePlatformJobFlag() throws SQLException {
    assertThat(importerEvent("on")).isEqualTo(1);

    for (String flag : new String[] {null, "off", "true", ""}) {
      assertRlsDenied(() -> importerEvent(flag));
    }
  }

  @Test
  @DisplayName("001-18 · El camino del importador solo escribe eventos del proveedor de identidad")
  void platformJobOnlyWritesIdentityProviderEvents() {
    assertRlsDenied(
        () -> insertAuditEvent(APP, null, null, "LIV", "SESSION_DENIED", eventId(), "on"));
    assertRlsDenied(
        () -> insertAuditEvent(APP, null, null, "LIV", "SESSION_STARTED", eventId(), "on"));
  }

  @Test
  @DisplayName("001-18 · La marca de plataforma tampoco abre la fila de otra administración")
  void flagDoesNotOpenAnotherAdministration() {
    assertRlsDenied(() ->
        insertAuditEvent(
            APP, ADMIN_A, ADMIN_B, "IDENTITY_PROVIDER", "LOGIN_ERROR", eventId(), "on"));
  }

  @Test
  @DisplayName(
      "001-18 · liv_app no lee las filas de plataforma, ni con contexto ni sin él, ni las que acaba de escribir")
  void appCannotReadPlatformRows() throws SQLException {
    String id = eventId();
    for (UUID context : new UUID[] {ADMIN_A, null}) {
      Object visible =
          inTx(
              APP,
              context,
              c -> {
                update(c, "SELECT set_config('app.platform_job', 'on', true)");
                update(
                    c,
                    "INSERT INTO audit.audit_event (administration_id, entity_name, operation,"
                        + " source, source_event_id) VALUES (NULL, 'identity_provider', 'LOGIN_ERROR',"
                        + " 'IDENTITY_PROVIDER', ?)",
                    id + context);
                return rows(c, PLATFORM_ROWS).get(0)[0];
              });
      assertThat(visible).as("contexto " + context).isEqualTo(0L);
    }
    assertThat(
            rows(
                    OWNER,
                    null,
                    "SELECT count(*) FROM audit.audit_event WHERE source_event_id LIKE ?",
                    id + "%")
                .get(0)[0])
        .isEqualTo(2L);
  }

  @Test
  @DisplayName(
      "001-18 · El importador es idempotente con ON CONFLICT DO NOTHING sin objetivo; con objetivo o RETURNING la RLS lo rechaza")
  void importerIdempotencyContract() throws SQLException {
    String id = eventId();
    String insert =
        "INSERT INTO audit.audit_event (administration_id, entity_name, operation, source,"
            + " source_event_id) VALUES (NULL, 'identity_provider', 'LOGIN_ERROR',"
            + " 'IDENTITY_PROVIDER', ?)";
    String flag = "SELECT set_config('app.platform_job', 'on', true)";

    int first = inTx(APP, null, c -> update(c, flag) + update(c, insert, id));
    int repeated =
        inTx(APP, null, c -> update(c, flag) + update(c, insert + " ON CONFLICT DO NOTHING", id));
    assertThat(first).isEqualTo(1);
    assertThat(repeated).as("el repetido se ignora").isZero();

    assertRlsDenied(() ->
        inTx(
            APP,
            null,
            c ->
                update(c, flag)
                    + update(c, insert + " ON CONFLICT (source, source_event_id) DO NOTHING", id)));
    assertRlsDenied(() ->
        inTx(
            APP, null, c -> update(c, flag) + rows(c, insert + " RETURNING id", eventId()).size()));
  }

  @Test
  @DisplayName(
      "001-18 · La marca de agua del importador solo se lee y escribe en un trabajo de plataforma")
  void importCursorNeedsThePlatformJobFlag() throws SQLException {
    String upsert =
        "INSERT INTO audit.import_cursor (source, last_event_at) VALUES ('IDENTITY_PROVIDER', now())"
            + " ON CONFLICT (source) DO UPDATE SET last_event_at = now()";
    assertRlsDenied(() -> update(APP, ADMIN_A, upsert));

    Object visibleToJob =
        inTx(
            APP,
            null,
            c -> {
              update(c, "SELECT set_config('app.platform_job', 'on', true)");
              update(c, upsert);
              return rows(c, "SELECT count(*) FROM audit.import_cursor").get(0)[0];
            });
    assertThat(visibleToJob).isEqualTo(1L);
    assertThat(rows(APP, ADMIN_A, "SELECT count(*) FROM audit.import_cursor").get(0)[0])
        .isEqualTo(0L);
  }
}
