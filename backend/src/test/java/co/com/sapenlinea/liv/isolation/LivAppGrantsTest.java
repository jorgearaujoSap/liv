package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Permisos de {@code liv_app} objeto por objeto (doc. 02 §5.2): lo que no está en esta lista no
 * está concedido. Una tabla nueva obliga a tocar esta prueba, y con ella al revisor de seguridad.
 */
@Tag("001-17")
class LivAppGrantsTest {

  private static final String SCHEMAS = "ARRAY['core', 'platform', 'audit', 'tax']";

  private static final Map<String, String> TABLE_GRANTS =
      Map.ofEntries(
          Map.entry("core.territorial_entity", "SELECT"),
          Map.entry("core.tax_administration", "SELECT"),
          Map.entry("core.admin_user", "INSERT,SELECT"),
          Map.entry("core.user_session", "INSERT,SELECT"),
          // DELETE to purge the completed deliveries (T15, migration V202611031000).
          Map.entry("platform.event_publication", "DELETE,INSERT,SELECT"),
          Map.entry("platform.app_role", "SELECT"),
          Map.entry("platform.app_permission", "SELECT"),
          Map.entry("platform.role_permission", "SELECT"),
          Map.entry("platform.shedlock", "INSERT,SELECT,UPDATE"),
          Map.entry("audit.audit_event", "INSERT,SELECT"),
          Map.entry("audit.import_cursor", "INSERT,SELECT,UPDATE"),
          // 007: el año es nacional y solo se lee; la vigencia de la administración se crea
          // y cambia de estado, pero nunca se borra.
          Map.entry("tax.fiscal_year", "SELECT"),
          Map.entry("tax.administration_fiscal_year", "INSERT,SELECT"));

  private static final Map<String, String> COLUMN_UPDATES =
      Map.of(
          "core.admin_user", "last_login_at",
          "core.user_session", "end_reason,ended_at",
          "platform.event_publication",
              "completion_attempts,completion_date,last_resubmission_date,status",
          // Nunca assessments_enabled: la liquidación no se habilita en la 007.
          "tax.administration_fiscal_year", "status,updated_at,updated_by");

  @Test
  @DisplayName(
      "001-17 · Las tablas concedidas a liv_app son exactamente las esperadas, sin UPDATE ni TRUNCATE de tabla y con DELETE solo en el outbox")
  void tableGrantsAreExactlyTheExpectedOnes() throws SQLException {
    assertThat(grants("'liv_app'::regrole::oid")).containsExactlyInAnyOrderEntriesOf(TABLE_GRANTS);
  }

  @Test
  @DisplayName("001-17 · Las columnas que liv_app puede actualizar son exactamente las esperadas")
  void columnUpdatesAreExactlyTheExpectedOnes() throws SQLException {
    List<Object[]> updatable =
        rows(
            OWNER,
            null,
            """
            SELECT n.nspname || '.' || c.relname,
                   string_agg(att.attname, ',' ORDER BY att.attname)
              FROM pg_attribute att
              JOIN pg_class c ON c.oid = att.attrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
              CROSS JOIN LATERAL aclexplode(att.attacl) a
             WHERE a.grantee = 'liv_app'::regrole::oid AND a.privilege_type = 'UPDATE'
               AND n.nspname = ANY (%s)
             GROUP BY 1
            """
                .formatted(SCHEMAS));

    Map<String, String> byTable = new TreeMap<>();
    updatable.forEach(row -> byTable.put((String) row[0], (String) row[1]));
    assertThat(byTable).containsExactlyInAnyOrderEntriesOf(COLUMN_UPDATES);
  }

  @Test
  @DisplayName(
      "001-17 · PUBLIC no tiene permisos sobre ninguna tabla ni función de los esquemas de LIV")
  void publicHasNoGrants() throws SQLException {
    assertThat(grants("0")).isEmpty();
    assertThat(
            rows(
                OWNER,
                null,
                """
                SELECT p.oid::regprocedure::text
                  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = ANY (%s) AND has_function_privilege('public', p.oid, 'EXECUTE')
                """
                    .formatted(SCHEMAS)))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "001-17 · liv_bff y liv_identity_lookup no tienen permisos de tabla en los esquemas de LIV")
  void otherRolesHaveNoTableGrants() throws SQLException {
    assertThat(grants("'liv_bff'::regrole::oid")).isEmpty();
    assertThat(grants("'liv_identity_lookup'::regrole::oid")).isEmpty();
  }

  @Test
  @DisplayName(
      "001-17 · liv_app no tiene BYPASSRLS, TEMP, CREATE ni acceso al esquema bff, y no es miembro de ningún rol")
  void roleIsConfined() throws SQLException {
    Object[] role =
        rows(
                OWNER,
                null,
                """
                SELECT r.rolbypassrls OR r.rolsuper,
                       has_database_privilege('liv_app', 'liv', 'TEMP'),
                       has_schema_privilege('liv_app', 'bff', 'USAGE'),
                       has_schema_privilege('liv_app', 'core', 'CREATE')
                         OR has_schema_privilege('liv_app', 'audit', 'CREATE')
                         OR has_schema_privilege('liv_app', 'platform', 'CREATE')
                         OR has_schema_privilege('liv_app', 'tax', 'CREATE'),
                       has_schema_privilege('liv_app', 'core', 'USAGE'),
                       EXISTS (SELECT 1 FROM pg_auth_members m WHERE m.member = r.oid),
                       has_schema_privilege('liv_app', 'tax', 'USAGE')
                  FROM pg_roles r WHERE r.rolname = 'liv_app'
                """)
            .get(0);

    assertThat(role).containsExactly(false, false, false, false, true, false, true);
  }

  @Test
  @DisplayName(
      "001-17 · Solo liv_owner puede asumir los dueños de las funciones SECURITY DEFINER, sin heredar sus permisos")
  void onlyTheMigrationOwnerCanAssumeTheFunctionOwners() throws SQLException {
    List<Object[]> memberships =
        rows(
            OWNER,
            null,
            """
            SELECT r.rolname || ' <- ' || m.rolname || ' inherit=' || am.inherit_option
                   || ' set=' || am.set_option
              FROM pg_auth_members am
              JOIN pg_roles r ON r.oid = am.roleid
              JOIN pg_roles m ON m.oid = am.member
             WHERE r.rolname LIKE 'liv\\_%' OR m.rolname LIKE 'liv\\_%'
             ORDER BY 1
            """);

    assertThat(memberships)
        .extracting(row -> row[0])
        .containsExactly(
            "liv_audit_platform <- liv_owner inherit=false set=true",
            "liv_identity_lookup <- liv_owner inherit=false set=true");
  }

  @Test
  @DisplayName(
      "001-17 · Toda tabla de LIV con administration_id tiene RLS y una política para liv_app")
  void everyAdministrationTableHasRlsAndAPolicy() throws SQLException {
    String administrationTables =
        """
        SELECT n.nspname || '.' || c.relname
          FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE c.relkind IN ('r', 'p') AND n.nspname = ANY (%s)
           AND (c.relname = 'tax_administration'
                OR EXISTS (SELECT 1 FROM pg_attribute a
                            WHERE a.attrelid = c.oid AND a.attname = 'administration_id'
                              AND NOT a.attisdropped))
        """
            .formatted(SCHEMAS);

    assertThat(rows(OWNER, null, administrationTables + " ORDER BY 1"))
        .extracting(row -> row[0])
        .as("tablas de una administración")
        .contains(
            "core.tax_administration",
            "core.admin_user",
            "core.user_session",
            "audit.audit_event",
            "tax.administration_fiscal_year");

    assertThat(
            rows(
                OWNER,
                null,
                """
                SELECT t.name FROM (%s) t (name)
                 WHERE NOT EXISTS (
                   SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                     JOIN pg_policies p ON p.schemaname = n.nspname AND p.tablename = c.relname
                    WHERE n.nspname || '.' || c.relname = t.name AND c.relrowsecurity
                      AND 'liv_app' = ANY (p.roles))
                """
                    .formatted(administrationTables)))
        .as("tablas sin RLS o sin política para liv_app")
        .isEmpty();
  }

  private static Map<String, String> grants(String grantee) throws SQLException {
    Map<String, String> byTable = new TreeMap<>();
    rows(
            OWNER,
            null,
            """
            SELECT n.nspname || '.' || c.relname,
                   string_agg(a.privilege_type, ',' ORDER BY a.privilege_type)
              FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              CROSS JOIN LATERAL aclexplode(c.relacl) a
             WHERE a.grantee = %s AND n.nspname = ANY (%s) AND c.relkind IN ('r', 'p', 'v', 'S')
             GROUP BY 1
            """
                .formatted(grantee, SCHEMAS))
        .forEach(row -> byTable.put((String) row[0], (String) row[1]));
    return byTable;
  }
}
