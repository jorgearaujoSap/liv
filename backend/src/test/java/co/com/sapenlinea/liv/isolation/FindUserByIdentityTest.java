package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.BFF;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ISSUER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.SUBJECT_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.SUBJECT_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.inTx;
import static co.com.sapenlinea.liv.isolation.LivDatabase.insertUser;
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
 * {@code core.find_user_by_identity}: la única forma en que {@code liv_app} localiza a un usuario
 * antes de conocer su administración (001-17). Es {@code SECURITY DEFINER}, así que se prueba su
 * resultado y también su endurecimiento (ADR 0025).
 */
@Tag("001-17")
class FindUserByIdentityTest {

  private static final String FIND = "SELECT * FROM core.find_user_by_identity(?, ?)";

  @Test
  @DisplayName("001-17 · Sin contexto devuelve solo id, administración y estado del usuario")
  void findsUserWithoutContext() throws SQLException {
    List<Object[]> found = rows(APP, null, FIND, ISSUER, SUBJECT_A);

    assertThat(found).hasSize(1);
    assertThat(found.get(0)).containsExactly(USER_A, ADMIN_A, "ACTIVE");
  }

  @Test
  @DisplayName(
      "001-17 · Con el contexto de otra administración devuelve la registrada, para detectar el conflicto")
  void reportsRegisteredAdministrationOnConflict() throws SQLException {
    List<Object[]> found = rows(APP, ADMIN_A, FIND, ISSUER, SUBJECT_B);

    assertThat(found).hasSize(1);
    assertThat(found.get(0)).containsExactly(USER_B, ADMIN_B, "ACTIVE");
  }

  @Test
  @DisplayName(
      "001-17 · El alta con la identidad de otra administración no inserta nada y la función revela el conflicto")
  void provisioningAnExistingIdentityOfAnotherAdministrationDoesNothing() throws SQLException {
    int inserted =
        update(
            APP,
            ADMIN_A,
            """
            INSERT INTO core.admin_user (administration_id, full_name, email, status,
                                         identity_issuer, identity_subject)
            VALUES (?, 'Duplicado', 'd@test.invalid', 'ACTIVE', ?, ?)
            ON CONFLICT ON CONSTRAINT admin_user_identity_key DO NOTHING
            """,
            ADMIN_A,
            ISSUER,
            SUBJECT_B);

    assertThat(inserted).isZero();
    assertThat(rows(APP, ADMIN_A, FIND, ISSUER, SUBJECT_B).get(0)[1]).isEqualTo(ADMIN_B);
  }

  @Test
  @DisplayName("001-17 · Una identidad desconocida no devuelve filas")
  void unknownIdentityReturnsNothing() throws SQLException {
    assertThat(rows(APP, ADMIN_A, FIND, ISSUER, "no-existe")).isEmpty();
    assertThat(rows(APP, ADMIN_A, FIND, "https://otro-emisor.test", SUBJECT_A)).isEmpty();
  }

  @Test
  @Tag("001-05")
  @DisplayName(
      "001-05 · Un usuario desactivado se encuentra con su estado, para que la aplicación lo rechace")
  void inactiveUserIsReportedAsInactive() throws SQLException {
    String subject = "inactivo-" + UUID.randomUUID();
    insertUser(OWNER, ADMIN_A, UUID.randomUUID(), subject, "INACTIVE");

    List<Object[]> found = rows(APP, ADMIN_A, FIND, ISSUER, subject);

    assertThat(found).hasSize(1);
    assertThat(found.get(0)[2]).isEqualTo("INACTIVE");
  }

  @Test
  @DisplayName(
      "001-17 · Solo liv_app puede ejecutarla: otro rol, ni el dueño de las tablas, la ejecuta")
  void otherRolesCannotExecuteIt() {
    SQLException owner = failureOf(() -> rows(OWNER, null, FIND, ISSUER, SUBJECT_A));
    assertThat(owner.getMessage()).contains("permission denied for function find_user_by_identity");

    SQLException bff = failureOf(() -> rows(BFF, null, FIND, ISSUER, SUBJECT_A));
    assertThat(bff.getSQLState()).isEqualTo("42501");
  }

  @Test
  @DisplayName(
      "001-17 · Está endurecida: dueño sin inicio de sesión, search_path fijo con pg_temp al final, sin PUBLIC")
  void isHardened() throws SQLException {
    List<Object[]> catalog =
        rows(
            OWNER,
            null,
            """
            SELECT p.prosecdef, r.rolname, r.rolcanlogin, r.rolbypassrls,
                   array_to_string(p.proconfig, ','),
                   has_function_privilege('public', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_bff', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_owner', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_audit_platform', p.oid, 'EXECUTE'),
                   has_function_privilege('liv_app', p.oid, 'EXECUTE')
              FROM pg_proc p
              JOIN pg_roles r ON r.oid = p.proowner
             WHERE p.oid = 'core.find_user_by_identity(text, text)'::regprocedure
            """);

    assertThat(catalog.get(0))
        .containsExactly(
            true,
            "liv_identity_lookup",
            false,
            false,
            "search_path=pg_catalog, core, pg_temp",
            false,
            false,
            false,
            false,
            true);
  }

  @Test
  @DisplayName("001-17 · Su dueño solo lee las columnas de identidad de admin_user")
  void ownerReadsOnlyIdentityColumns() throws SQLException {
    List<Object[]> privileges =
        rows(
            OWNER,
            null,
            """
            SELECT c, has_column_privilege('liv_identity_lookup', 'core.admin_user', c, 'SELECT')
              FROM unnest(ARRAY['id', 'administration_id', 'status', 'identity_issuer',
                                'identity_subject', 'full_name', 'email', 'last_login_at']) c
             ORDER BY c
            """);

    assertThat(privileges)
        .extracting(row -> row[0] + "=" + row[1])
        .containsExactly(
            "administration_id=true",
            "email=false",
            "full_name=false",
            "id=true",
            "identity_issuer=true",
            "identity_subject=true",
            "last_login_at=false",
            "status=true");
  }

  /**
   * Un operador {@code =(varchar, text)} en un esquema que el llamante antepone a su search_path
   * ganaría la resolución de operadores frente al {@code =(text, text)} del catálogo. La función de
   * control, sin search_path fijo, cae en la trampa; la real no.
   */
  @Test
  @DisplayName("001-17 · Un operador falso en el search_path del llamante no cambia el resultado")
  void callerSearchPathCannotRedirectIt() throws SQLException {
    try {
      createHijackedOperatorSchema();
      String attack = "SET LOCAL search_path = evil, public";

      List<Object[]> control =
          inTx(
              APP,
              null,
              c -> {
                update(c, attack);
                return rows(c, "SELECT * FROM evil.unprotected_lookup(?, ?)", ISSUER, "no-existe");
              });
      assertThat(control).as("la trampa funciona contra una función sin search_path").isNotEmpty();

      List<Object[]> protectedLookup =
          inTx(
              APP,
              null,
              c -> {
                update(c, attack);
                return rows(c, FIND, ISSUER, "no-existe");
              });
      assertThat(protectedLookup).isEmpty();
    } finally {
      update(OWNER, null, "DROP SCHEMA IF EXISTS evil CASCADE");
    }
  }

  private static void createHijackedOperatorSchema() throws SQLException {
    update(OWNER, null, "DROP SCHEMA IF EXISTS evil CASCADE");
    update(OWNER, null, "CREATE SCHEMA evil");
    update(OWNER, null, "GRANT USAGE ON SCHEMA evil TO PUBLIC");
    update(
        OWNER,
        null,
        "CREATE FUNCTION evil.always_true(varchar, text) RETURNS boolean"
            + " LANGUAGE sql IMMUTABLE AS 'SELECT true'");
    update(OWNER, null, "GRANT EXECUTE ON FUNCTION evil.always_true(varchar, text) TO PUBLIC");
    update(
        OWNER,
        null,
        "CREATE OPERATOR evil.= (LEFTARG = varchar, RIGHTARG = text, FUNCTION = evil.always_true)");
    update(
        OWNER,
        null,
        """
        CREATE FUNCTION evil.unprotected_lookup(p_issuer text, p_subject text)
        RETURNS TABLE (user_id uuid) LANGUAGE sql SECURITY DEFINER AS $$
          SELECT u.id FROM core.admin_user u
           WHERE u.identity_issuer = p_issuer AND u.identity_subject = p_subject
        $$
        """);
    update(OWNER, null, "GRANT EXECUTE ON FUNCTION evil.unprotected_lookup(text, text) TO PUBLIC");
    update(OWNER, null, "GRANT CREATE ON SCHEMA evil TO liv_identity_lookup");
    update(
        OWNER,
        null,
        "ALTER FUNCTION evil.unprotected_lookup(text, text) OWNER TO liv_identity_lookup");
  }
}
