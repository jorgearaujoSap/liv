package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Permisos de {@code liv_app} objeto por objeto (doc. 02 §5.2): lo que no está en esta lista no
 * está concedido. Una tabla nueva obliga a tocar esta prueba, y con ella al revisor de seguridad.
 */
class LivAppGrantsTest {

  private static final String SCHEMAS = "ARRAY['core', 'platform', 'audit']";

  private static final Map<String, String> TABLE_GRANTS =
      Map.of(
          "core.territorial_entity", "SELECT",
          "core.tax_administration", "SELECT",
          "core.admin_user", "INSERT,SELECT",
          "core.user_session", "INSERT,SELECT",
          "platform.event_publication", "DELETE,INSERT,SELECT,UPDATE");

  private static final Map<String, String> COLUMN_UPDATES =
      Map.of(
          "core.admin_user", "last_login_at",
          "core.user_session", "end_reason,ended_at");

  @Test
  @DisplayName(
      "Las tablas concedidas a liv_app son exactamente las esperadas, sin DELETE ni TRUNCATE")
  void tableGrantsAreExactlyTheExpectedOnes() throws SQLException {
    assertThat(grants("'liv_app'::regrole::oid")).containsExactlyInAnyOrderEntriesOf(TABLE_GRANTS);
  }

  @Test
  @DisplayName("Las columnas que liv_app puede actualizar son exactamente las esperadas")
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
  @DisplayName("PUBLIC no tiene permisos sobre ninguna tabla ni función de los esquemas de LIV")
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
      "liv_app no tiene BYPASSRLS, TEMP, CREATE ni acceso al esquema bff; toda tabla de LIV tiene RLS")
  void roleIsConfinedAndEveryTableHasRls() throws SQLException {
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
                         OR has_schema_privilege('liv_app', 'platform', 'CREATE'),
                       has_schema_privilege('liv_app', 'core', 'USAGE')
                  FROM pg_roles r WHERE r.rolname = 'liv_app'
                """)
            .get(0);
    assertThat(role).containsExactly(false, false, false, false, true);

    assertThat(
            rows(
                OWNER,
                null,
                """
                SELECT n.nspname || '.' || c.relname
                  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE c.relkind IN ('r', 'p') AND n.nspname = ANY (%s) AND NOT c.relrowsecurity
                   AND c.relname IN ('tax_administration', 'admin_user', 'user_session')
                """
                    .formatted(SCHEMAS)))
        .as("tablas de una administración sin RLS")
        .isEmpty();
  }

  private static Map<String, String> grants(String grantee) throws SQLException {
    Map<String, String> byTable = new TreeMap<>();
    rows(
            OWNER,
            null,
            """
            SELECT n.nspname || '.' || c.relname, string_agg(a.privilege_type, ',' ORDER BY a.privilege_type)
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
