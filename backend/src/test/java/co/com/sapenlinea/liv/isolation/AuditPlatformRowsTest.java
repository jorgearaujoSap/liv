package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.BFF;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.inTx;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Las filas de auditoría de plataforma ({@code administration_id} NULL) solo las escriben dos
 * caminos (ADR 0025): el importador, con {@code app.platform_job = 'on'}, y la función {@code
 * audit.record_platform_session_denied}.
 */
@Tag("001-18")
class AuditPlatformRowsTest {

  private static final String EVENT =
      """
      INSERT INTO audit.audit_event (administration_id, entity_name, operation, source, source_event_id)
      VALUES (?, 'identity_provider', 'LOGIN_ERROR', ?, ?)
      """;

  private static final String DENIED =
      "SELECT audit.record_platform_session_denied(?, ?, CAST(? AS jsonb), ?)";

  private static final String PLATFORM_JOB_ON = "SELECT set_config('app.platform_job', 'on', true)";

  private static String eventId() {
    return "evt-" + UUID.randomUUID();
  }

  /**
   * Una inserción de plataforma en una transacción, con la marca del trabajo tal como se indique.
   */
  private static int platformEvent(String jobFlag) throws SQLException {
    return inTx(
        APP,
        null,
        c -> {
          if (jobFlag != null) {
            update(c, "SELECT set_config('app.platform_job', ?, true)", jobFlag);
          }
          return update(c, EVENT, null, "IDENTITY_PROVIDER", eventId());
        });
  }

  @Test
  @DisplayName("001-18 · Con el contexto de A, liv_app registra eventos de A y no de B ni de nadie")
  void contextAdministrationOnly() throws SQLException {
    assertThat(update(APP, ADMIN_A, EVENT, ADMIN_A, "LIV", eventId())).isEqualTo(1);

    for (UUID other : new UUID[] {ADMIN_B, null}) {
      SQLException denied = failureOf(() -> update(APP, ADMIN_A, EVENT, other, "LIV", eventId()));
      assertThat(denied.getMessage()).contains("row-level security");
    }
    SQLException noContext = failureOf(() -> update(APP, null, EVENT, ADMIN_A, "LIV", eventId()));
    assertThat(noContext.getMessage()).contains("row-level security");
  }

  @Test
  @DisplayName(
      "001-18 · Una fila de plataforma solo entra si la transacción fijó app.platform_job = 'on'")
  void platformRowNeedsThePlatformJobFlag() throws SQLException {
    assertThat(platformEvent("on")).isEqualTo(1);

    for (String flag : new String[] {null, "off", "true", ""}) {
      SQLException denied = failureOf(() -> platformEvent(flag));
      assertThat(denied.getMessage()).as("marca " + flag).contains("row-level security");
    }
  }

  @Test
  @DisplayName(
      "001-18 · liv_app no lee las filas de plataforma, ni siquiera las que acaba de escribir")
  void appCannotReadPlatformRows() throws SQLException {
    String platformRows = "SELECT count(*) FROM audit.audit_event WHERE administration_id IS NULL";
    Object visible =
        inTx(
            APP,
            ADMIN_A,
            c -> {
              update(c, PLATFORM_JOB_ON);
              update(c, EVENT, null, "IDENTITY_PROVIDER", eventId());
              return rows(c, platformRows).get(0)[0];
            });

    assertThat(visible).isEqualTo(0L);
    assertThat(rows(OWNER, null, platformRows).get(0)[0]).isNotEqualTo(0L);
  }

  @Test
  @DisplayName("001-18 · La marca de plataforma tampoco abre la fila de otra administración")
  void flagDoesNotOpenAnotherAdministration() {
    SQLException denied =
        failureOf(() ->
            inTx(
                APP,
                ADMIN_A,
                c -> {
                  update(c, PLATFORM_JOB_ON);
                  return update(c, EVENT, ADMIN_B, "IDENTITY_PROVIDER", eventId());
                }));
    assertThat(denied.getMessage()).contains("row-level security");
  }

  @Test
  @DisplayName(
      "001-18 · La función de rechazos guarda solo una fila SESSION_DENIED de LIV sin administración, sin contexto, y es idempotente")
  void deniedFunctionWritesOnlyThePlatformRow() throws SQLException {
    String id = eventId();
    String values = "{\"result\":\"DENIED\",\"reason\":\"NO_ADMINISTRATION\"}";
    update(APP, null, DENIED, id, "corr-1", values, "ref-1");
    update(APP, null, DENIED, id, "corr-1", "{\"result\":\"OTRO\"}", "ref-1");

    List<Object[]> stored =
        rows(
            OWNER,
            null,
            """
            SELECT administration_id, user_id, entity_name, operation, source, actor_ref,
                   new_values ->> 'reason'
              FROM audit.audit_event WHERE source_event_id = ?
            """,
            id);
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0))
        .containsExactly(
            null, null, "session", "SESSION_DENIED", "LIV", "ref-1", "NO_ADMINISTRATION");
  }

  @Test
  @DisplayName(
      "001-18 · El dueño de la función solo puede insertar SESSION_DENIED de LIV sin administración")
  void functionOwnerIsConfinedByPolicy() throws SQLException {
    String insert =
        "INSERT INTO audit.audit_event (administration_id, entity_name, operation, source,"
            + " source_event_id) VALUES (?, 'session', ?, ?, ?)";
    assertThat(asFunctionOwner(insert, null, "SESSION_DENIED", "LIV", eventId())).isEqualTo(1);

    Object[][] forbidden = {
      {ADMIN_A, "SESSION_DENIED", "LIV"},
      {null, "SESSION_STARTED", "LIV"},
      {null, "SESSION_DENIED", "IDENTITY_PROVIDER"}
    };
    for (Object[] row : forbidden) {
      SQLException denied =
          failureOf(() -> asFunctionOwner(insert, row[0], row[1], row[2], eventId()));
      assertThat(denied.getMessage()).contains("row-level security");
    }
  }

  @Test
  @DisplayName(
      "001-18 · La función solo la ejecuta liv_app y es SECURITY DEFINER de liv_audit_platform con search_path fijo")
  void functionIsHardened() throws SQLException {
    SQLException denied = failureOf(() -> rows(BFF, null, DENIED, eventId(), "c", "{}", "r"));
    assertThat(denied.getSQLState()).isEqualTo("42501");

    List<Object[]> catalog =
        rows(
            OWNER,
            null,
            """
            SELECT p.prosecdef, r.rolname, r.rolcanlogin, array_to_string(p.proconfig, ','),
                   has_function_privilege('public', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_bff', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_owner', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_app', p.oid, 'EXECUTE')
              FROM pg_proc p JOIN pg_roles r ON r.oid = p.proowner
             WHERE p.pronamespace = 'audit'::regnamespace
               AND p.proname = 'record_platform_session_denied'
            """);
    assertThat(catalog).hasSize(1);
    assertThat(catalog.get(0))
        .containsExactly(
            true,
            "liv_audit_platform",
            false,
            "search_path=pg_catalog, audit, pg_temp",
            false,
            false,
            false,
            true);
  }

  @Test
  @DisplayName(
      "001-18 · La marca de agua del importador solo se lee y escribe en un trabajo de plataforma")
  void importCursorNeedsThePlatformJobFlag() throws SQLException {
    String upsert =
        "INSERT INTO audit.import_cursor (source, last_event_at) VALUES ('IDENTITY_PROVIDER', now())"
            + " ON CONFLICT (source) DO UPDATE SET last_event_at = now()";
    SQLException denied = failureOf(() -> update(APP, ADMIN_A, upsert));
    assertThat(denied.getMessage()).contains("row-level security");

    Object visibleToJob =
        inTx(
            APP,
            null,
            c -> {
              update(c, PLATFORM_JOB_ON);
              update(c, upsert);
              return rows(c, "SELECT count(*) FROM audit.import_cursor").get(0)[0];
            });
    assertThat(visibleToJob).isEqualTo(1L);
    assertThat(rows(APP, ADMIN_A, "SELECT count(*) FROM audit.import_cursor").get(0)[0])
        .isEqualTo(0L);
  }

  private static int asFunctionOwner(String sql, Object... args) throws SQLException {
    return inTx(
        OWNER,
        null,
        c -> {
          update(c, "SET LOCAL ROLE liv_audit_platform");
          return update(c, sql, args);
        });
  }
}
