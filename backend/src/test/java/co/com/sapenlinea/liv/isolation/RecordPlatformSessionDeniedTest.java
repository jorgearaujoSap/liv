package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.BFF;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.assertRlsDenied;
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
 * {@code audit.record_platform_session_denied}: el segundo camino de las filas de plataforma (ADR
 * 0025). Solo puede guardar un {@code SESSION_DENIED} de origen LIV sin administración, con los
 * campos de la lista blanca del plan.
 */
@Tag("001-18")
class RecordPlatformSessionDeniedTest {

  private static final String DENIED =
      "SELECT audit.record_platform_session_denied(?, CAST(? AS timestamptz), ?, CAST(? AS jsonb), ?)";

  private static final String VALUES =
      "{\"result\":\"DENIED\",\"reason\":\"NO_ADMINISTRATION\",\"truncated_ip\":\"10.0.0.0\","
          + "\"masked_attempted_username\":\"ju***\"}";

  private static String eventId() {
    return "denied-" + UUID.randomUUID();
  }

  private static void record(String id, String occurredAt, String correlationId, String values)
      throws SQLException {
    update(APP, null, DENIED, id, occurredAt, correlationId, values, "ref-1");
  }

  @Test
  @DisplayName(
      "001-18 · Guarda solo una fila SESSION_DENIED de LIV sin administración, con su fecha y correlación, sin contexto")
  void writesOnlyThePlatformRow() throws SQLException {
    String id = eventId();
    record(id, "2026-11-01T10:15:30Z", "corr-1", VALUES);
    record(id, "2026-11-01T11:00:00Z", "corr-2", "{\"result\":\"OTRO\"}");

    List<Object[]> stored =
        rows(
            OWNER,
            null,
            """
            SELECT administration_id, user_id, entity_name, operation, source, actor_ref,
                   correlation_id, to_char(occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS'),
                   new_values ->> 'reason', new_values ->> 'masked_attempted_username'
              FROM audit.audit_event WHERE source_event_id = ?
            """,
            id);
    assertThat(stored).as("idempotente").hasSize(1);
    assertThat(stored.get(0))
        .containsExactly(
            null,
            null,
            "session",
            "SESSION_DENIED",
            "LIV",
            "ref-1",
            "corr-1",
            "2026-11-01T10:15:30",
            "NO_ADMINISTRATION",
            "ju***");
  }

  @Test
  @DisplayName("001-18 · Rechaza claves fuera de la lista blanca, textos largos y fechas futuras")
  void rejectsDataOutsideTheAllowList() {
    for (String values :
        new String[] {
          "{\"result\":\"DENIED\",\"username\":\"juan\"}",
          "{\"email\":\"juan@test.invalid\"}",
          "[\"DENIED\"]"
        }) {
      SQLException rejected =
          failureOf(() -> record(eventId(), "2026-11-01T10:00:00Z", "c", values));
      assertThat(rejected.getMessage()).as(values).contains("lista blanca");
    }

    SQLException longId =
        failureOf(() -> record("x".repeat(101), "2026-11-01T10:00:00Z", "c", VALUES));
    assertThat(longId.getMessage()).contains("demasiado largo");

    SQLException future = failureOf(() -> record(eventId(), "2999-01-01T00:00:00Z", "c", VALUES));
    assertThat(future.getMessage()).contains("futura");
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
      assertRlsDenied(() -> asFunctionOwner(insert, row[0], row[1], row[2], eventId()));
    }
  }

  @Test
  @DisplayName(
      "001-18 · La función es SECURITY DEFINER de liv_audit_platform con search_path fijo y solo la ejecuta liv_app")
  void functionIsHardened() throws SQLException {
    assertThat(
            failureOf(() ->
                    rows(BFF, null, DENIED, eventId(), "2026-11-01T10:00:00Z", "c", "{}", "r"))
                .getSQLState())
        .isEqualTo("42501");

    List<Object[]> catalog =
        rows(
            OWNER,
            null,
            """
            SELECT p.prosecdef, r.rolname, array_to_string(p.proconfig, ','),
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
            "search_path=pg_catalog, audit, pg_temp",
            false,
            false,
            false,
            true);
  }

  @Test
  @DisplayName(
      "001-18 · liv_audit_platform no inicia sesión, no salta la RLS y solo tiene INSERT sobre audit.audit_event")
  void functionOwnerHasExactlyTheMinimumPrivileges() throws SQLException {
    assertThat(
            rows(
                    OWNER,
                    null,
                    """
                    SELECT r.rolcanlogin OR r.rolbypassrls OR r.rolsuper,
                           has_schema_privilege('liv_audit_platform', 'audit', 'CREATE'),
                           has_schema_privilege('liv_audit_platform', 'audit', 'USAGE'),
                           has_schema_privilege('liv_audit_platform', 'core', 'USAGE')
                      FROM pg_roles r WHERE r.rolname = 'liv_audit_platform'
                    """)
                .get(0))
        .containsExactly(false, false, true, false);

    assertThat(
            rows(
                    OWNER,
                    null,
                    """
                    SELECT n.nspname || '.' || c.relname || ':' || string_agg(a.privilege_type, ',')
                      FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                      CROSS JOIN LATERAL aclexplode(c.relacl) a
                     WHERE a.grantee = 'liv_audit_platform'::regrole::oid
                     GROUP BY n.nspname, c.relname
                    """)
                .stream()
                .map(row -> row[0]))
        .containsExactly("audit.audit_event:INSERT");

    assertThat(
            rows(
                    OWNER,
                    null,
                    "SELECT count(*) FROM information_schema.column_privileges"
                        + " WHERE grantee = 'liv_audit_platform' AND privilege_type <> 'INSERT'")
                .get(0)[0])
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
