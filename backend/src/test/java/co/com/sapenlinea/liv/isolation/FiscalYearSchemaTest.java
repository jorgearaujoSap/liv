package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.ADMIN_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_A;
import static co.com.sapenlinea.liv.isolation.LivDatabase.USER_B;
import static co.com.sapenlinea.liv.isolation.LivDatabase.assertRlsDenied;
import static co.com.sapenlinea.liv.isolation.LivDatabase.failureOf;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * El esquema {@code tax} de la feature 007 (tablas {@code fiscal_year} y {@code
 * administration_fiscal_year}): sus restricciones, y lo que {@code liv_app} puede hacer con ellas y
 * lo que no. El año 2026 es el único compartido con otras pruebas y solo se lee. Cada prueba que
 * escribe usa sus propios años (desde 2040), porque el contenedor se comparte con las demás pruebas
 * de base de datos y {@code liv_app} no puede borrar lo que crea.
 */
class FiscalYearSchemaTest {

  private static final String INSERT_AS_OWNER =
      """
      INSERT INTO tax.administration_fiscal_year
        (administration_id, fiscal_year, status, assessments_enabled, created_by,
         updated_by, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """;

  private static final String INSERT_AS_APP =
      """
      INSERT INTO tax.administration_fiscal_year (administration_id, fiscal_year, created_by)
      VALUES (?, ?, ?)
      """;

  private static final String OPEN =
      """
      UPDATE tax.administration_fiscal_year
         SET status = 'EN_PREPARACION', updated_by = ?, updated_at = now()
       WHERE administration_id = ? AND fiscal_year = ?
      """;

  private static final String INSUFFICIENT_PRIVILEGE = "42501";
  private static final String CHECK_VIOLATION = "23514";
  private static final String UNIQUE_VIOLATION = "23505";
  private static final String FOREIGN_KEY_VIOLATION = "23503";

  /**
   * Quita lo que crearon las pruebas de esta clase: {@code liv_app} no borra, pero el dueño sí, y
   * así las pruebas de otras clases (B6) parten de las administraciones sin las vigencias de aquí.
   */
  @AfterAll
  static void removeTheFiscalYearsOfThisClass() throws SQLException {
    update(
        OWNER,
        null,
        "DELETE FROM tax.administration_fiscal_year WHERE fiscal_year BETWEEN 2040 AND 2089");
  }

  // --- tax.fiscal_year (nacional) ---

  @Test
  @Tag("007-12")
  @Tag("V-01")
  @DisplayName("007-12 · V-01 · starts_on es el 1 de enero y ends_on el 31 de diciembre del año")
  void fiscalYearDatesMustBeTheCalendarYear() {
    String[][] wrongDates = {
      {"3001-01-02", "3001-12-31"}, // starts_on no es el 1 de enero
      {"3001-01-01", "3001-12-30"}, // ends_on no es el 31 de diciembre
      {"3001-01-01", "3002-12-31"}, // un año distinto del de la clave
      {"3002-01-01", "3001-12-31"} // el 1 de enero de otro año
    };
    for (String[] dates : wrongDates) {
      SQLException invalid =
          failureOf(() ->
              update(
                  OWNER,
                  null,
                  "INSERT INTO tax.fiscal_year (year, starts_on, ends_on) VALUES (3001, ?, ?)",
                  Date.valueOf(dates[0]),
                  Date.valueOf(dates[1])));
      assertThat(invalid.getSQLState()).as(dates[0] + " a " + dates[1]).isEqualTo(CHECK_VIOLATION);
    }
  }

  @Test
  @Tag("007-12")
  @Tag("H2-2")
  @DisplayName(
      "007-12 · H2-2 · Existen los años 1999 a 2100, cada uno con sus fechas, y ninguno más")
  void fiscalYearsFrom1999To2100Exist() throws SQLException {
    Object[] summary =
        rows(OWNER, null, "SELECT min(year), max(year), count(*) FROM tax.fiscal_year").get(0);
    assertThat(summary[0]).isEqualTo(1999);
    assertThat(summary[1]).isEqualTo(2100);
    assertThat(summary[2]).isEqualTo(102L);

    Object[] y2026 =
        rows(OWNER, null, "SELECT starts_on, ends_on FROM tax.fiscal_year WHERE year = 2026")
            .get(0);
    assertThat(((Date) y2026[0]).toLocalDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(((Date) y2026[1]).toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · liv_app lee los años, que son nacionales, pero no los escribe")
  void applicationReadsFiscalYearsButNeverWritesThem() throws SQLException {
    assertThat(rows(APP, null, "SELECT count(*) FROM tax.fiscal_year").get(0)[0]).isEqualTo(102L);
    assertThat(rows(APP, ADMIN_A, "SELECT count(*) FROM tax.fiscal_year").get(0)[0])
        .as("el contexto de una administración no cambia lo que se ve")
        .isEqualTo(102L);

    for (String write :
        List.of(
            "INSERT INTO tax.fiscal_year (year, starts_on, ends_on)"
                + " VALUES (2101, '2101-01-01', '2101-12-31')",
            "UPDATE tax.fiscal_year SET ends_on = ends_on WHERE year = 2026",
            "DELETE FROM tax.fiscal_year WHERE year = 2026")) {
      assertThat(failureOf(() -> update(APP, ADMIN_A, write)).getSQLState())
          .as(write)
          .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
  }

  // --- tax.administration_fiscal_year: restricciones ---

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · assessments_enabled no puede ser verdadero, ni siquiera para el dueño")
  void assessmentsCanNeverBeEnabled() {
    for (String status : List.of("PLANEADA", "HABILITADA")) {
      SQLException enabled = failureOf(() -> insertAsOwner(ADMIN_A, 2040, status, true, USER_A));
      assertThat(enabled.getSQLState()).as(status).isEqualTo(CHECK_VIOLATION);
    }
  }

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · El estado es uno de los cinco códigos del Doc. 01 §5")
  void statusIsOneOfTheFiveCodes() throws SQLException {
    SQLException unknown = failureOf(() -> insertAsOwner(ADMIN_A, 2041, "ABIERTA", false, USER_A));
    assertThat(unknown.getSQLState()).isEqualTo(CHECK_VIOLATION);

    List<String> codes = List.of("PLANEADA", "EN_PREPARACION", "LISTA", "HABILITADA", "CERRADA");
    for (int i = 0; i < codes.size(); i++) {
      assertThat(insertAsOwner(ADMIN_A, 2042 + i, codes.get(i), false, USER_A))
          .as(codes.get(i))
          .isEqualTo(1);
    }
  }

  @Test
  @Tag("007-05")
  @DisplayName("007-05 · V-01 · Una administración no puede tener dos vigencias del mismo año")
  void aYearIsCreatedOnlyOncePerAdministration() throws SQLException {
    assertThat(insertAsOwner(ADMIN_A, 2050, "PLANEADA", false, USER_A)).isEqualTo(1);

    SQLException duplicate =
        failureOf(() -> insertAsOwner(ADMIN_A, 2050, "PLANEADA", false, USER_A));
    assertThat(duplicate.getSQLState()).isEqualTo(UNIQUE_VIOLATION);
    // El adaptador de la aplicación reconoce esta restricción por su nombre (V-01, plan paso 6).
    assertThat(
            ((org.postgresql.util.PSQLException) duplicate).getServerErrorMessage().getConstraint())
        .isEqualTo("administration_fiscal_year_year_key");

    SQLException throughTheApplication =
        failureOf(() -> update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2050, USER_A));
    assertThat(throughTheApplication.getSQLState()).isEqualTo(UNIQUE_VIOLATION);

    assertThat(insertAsOwner(ADMIN_B, 2050, "PLANEADA", false, USER_B))
        .as("otra administración sí puede tener su 2050")
        .isEqualTo(1);
  }

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · La vigencia apunta a un año que existe y a usuarios de su administración")
  void foreignKeysAreEnforced() {
    for (int outsideTheRange : List.of(1998, 2101)) {
      assertThat(
              failureOf(() -> insertAsOwner(ADMIN_A, outsideTheRange, "PLANEADA", false, USER_A))
                  .getSQLState())
          .as("año fuera de tax.fiscal_year: " + outsideTheRange)
          .isEqualTo(FOREIGN_KEY_VIOLATION);
    }
    assertThat(
            failureOf(() -> insertAsOwner(ADMIN_A, 2051, "PLANEADA", false, USER_B)).getSQLState())
        .as("created_by de otra administración")
        .isEqualTo(FOREIGN_KEY_VIOLATION);
    assertThat(
            failureOf(() -> insertAsOwner(UUID.randomUUID(), 2051, "PLANEADA", false, USER_A))
                .getSQLState())
        .as("administración inexistente")
        .isEqualTo(FOREIGN_KEY_VIOLATION);
  }

  @Test
  @Tag("007-12")
  @DisplayName(
      "007-12 · El id es un UUID versión 7 y los valores por omisión son Planeada y sin habilitar")
  void defaultsAreThoseOfTheModel() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2052, USER_A);

    Object[] row =
        rows(
                APP,
                ADMIN_A,
                """
                SELECT uuid_extract_version(id), status, assessments_enabled,
                       created_at IS NOT NULL, updated_by, updated_at
                  FROM tax.administration_fiscal_year WHERE fiscal_year = 2052
                """)
            .get(0);
    assertThat(row).containsExactly(7, "PLANEADA", false, true, null, null);
  }

  // --- tax.administration_fiscal_year: lo que liv_app puede escribir ---

  @Test
  @Tag("007-10")
  @Tag("007-12")
  @DisplayName(
      "007-10 · 007-12 · H2-4 · liv_app solo escribe administración, año y autor al crear:"
          + " ni el estado, ni la habilitación, ni el id, ni las marcas de cambio")
  void applicationInsertsOnlyTheColumnsOfTheUseCase() throws SQLException {
    for (String column :
        List.of("id", "status", "assessments_enabled", "created_at", "updated_by", "updated_at")) {
      String value =
          switch (column) {
            case "id" -> "uuidv7()";
            case "status" -> "'PLANEADA'";
            case "assessments_enabled" -> "false";
            case "updated_by" -> "'" + USER_A + "'::uuid";
            default -> "now()";
          };
      String sql =
          "INSERT INTO tax.administration_fiscal_year"
              + " (administration_id, fiscal_year, created_by, %s) VALUES (?, 2060, ?, %s)"
                  .formatted(column, value);
      SQLException denied = failureOf(() -> update(APP, ADMIN_A, sql, ADMIN_A, USER_A));
      assertThat(denied.getSQLState()).as(column).isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    assertThat(update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2060, USER_A))
        .as("las tres columnas del caso de uso")
        .isEqualTo(1);
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · La traza es coherente: En preparación exige updated_by y updated_at, y los dos van juntos")
  void anOpenedFiscalYearAlwaysHasItsAuthorAndItsTime() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2061, USER_A);
    for (String sql :
        List.of(
            "UPDATE tax.administration_fiscal_year SET status = 'EN_PREPARACION'"
                + " WHERE fiscal_year = 2061",
            "UPDATE tax.administration_fiscal_year SET status = 'EN_PREPARACION', updated_at = now()"
                + " WHERE fiscal_year = 2061",
            "UPDATE tax.administration_fiscal_year SET status = 'EN_PREPARACION',"
                + " updated_by = '"
                + USER_A
                + "' WHERE fiscal_year = 2061")) {
      assertThat(failureOf(() -> update(APP, ADMIN_A, sql)).getSQLState())
          .as(sql)
          .isEqualTo(CHECK_VIOLATION);
    }
    assertThat(statusOf(2061)).isEqualTo("PLANEADA");

    Timestamp now = Timestamp.from(Instant.now());
    for (Object[] trace : new Object[][] {{null, null}, {USER_A, null}, {null, now}}) {
      SQLException incomplete =
          failureOf(() ->
              update(
                  OWNER,
                  null,
                  INSERT_AS_OWNER,
                  ADMIN_A,
                  2062,
                  "EN_PREPARACION",
                  false,
                  USER_A,
                  trace[0],
                  trace[1]));
      assertThat(incomplete.getSQLState()).isEqualTo(CHECK_VIOLATION);
    }
    assertThat(
            update(
                OWNER,
                null,
                INSERT_AS_OWNER,
                ADMIN_A,
                2062,
                "EN_PREPARACION",
                false,
                USER_A,
                USER_A,
                now))
        .isEqualTo(1);
  }

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · liv_app no puede habilitar la liquidación al crear ni cambiarla después")
  void applicationCannotEnableAssessments() throws SQLException {
    SQLException onInsert =
        failureOf(() ->
            update(
                APP,
                ADMIN_A,
                """
                INSERT INTO tax.administration_fiscal_year
                  (administration_id, fiscal_year, assessments_enabled, created_by)
                VALUES (?, 2070, true, ?)
                """,
                ADMIN_A,
                USER_A));
    assertThat(onInsert.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);

    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2071, USER_A);
    for (String sql :
        List.of(
            "UPDATE tax.administration_fiscal_year SET assessments_enabled = true"
                + " WHERE fiscal_year = 2071",
            "UPDATE tax.administration_fiscal_year SET assessments_enabled = false"
                + " WHERE fiscal_year = 2071")) {
      assertThat(failureOf(() -> update(APP, ADMIN_A, sql)).getSQLState())
          .as(sql)
          .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
  }

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · liv_app no cambia la administración, el año ni quién y cuándo la creó")
  void applicationCannotRewriteTheKey() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2072, USER_A);

    for (String column :
        List.of("id", "administration_id", "fiscal_year", "created_by", "created_at")) {
      String sql =
          "UPDATE tax.administration_fiscal_year SET %1$s = %1$s WHERE fiscal_year = 2072"
              .formatted(column);
      assertThat(failureOf(() -> update(APP, ADMIN_A, sql)).getSQLState())
          .as(column)
          .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
  }

  @Test
  @Tag("007-12")
  @DisplayName("007-12 · liv_app no borra vigencias, ni con ni sin contexto")
  void applicationCannotDelete() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2073, USER_A);

    for (String sql :
        List.of(
            "DELETE FROM tax.administration_fiscal_year WHERE fiscal_year = 2073",
            "DELETE FROM tax.administration_fiscal_year",
            "TRUNCATE tax.administration_fiscal_year")) {
      assertThat(failureOf(() -> update(APP, ADMIN_A, sql)).getSQLState())
          .as(sql)
          .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }
    assertThat(
            failureOf(() -> update(APP, null, "DELETE FROM tax.administration_fiscal_year"))
                .getSQLState())
        .as("sin contexto")
        .isEqualTo(INSUFFICIENT_PRIVILEGE);
  }

  // --- tax.administration_fiscal_year: la única transición ---

  @Test
  @Tag("007-10")
  @DisplayName("007-10 · liv_app abre la preparación: Planeada pasa a En preparación (1 fila)")
  void applicationOpensThePreparation() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2080, USER_A);

    assertThat(update(APP, ADMIN_A, OPEN, USER_A, ADMIN_A, 2080)).isEqualTo(1);

    Object[] row =
        rows(
                APP,
                ADMIN_A,
                """
                SELECT status, updated_by, updated_at IS NOT NULL, assessments_enabled
                  FROM tax.administration_fiscal_year WHERE fiscal_year = 2080
                """)
            .get(0);
    assertThat(row).containsExactly("EN_PREPARACION", USER_A, true, false);
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · WITH CHECK rechaza con 42501 pasar de Planeada a Habilitada, Lista, Cerrada"
          + " a un código desconocido o quedarse en Planeada")
  void applicationCannotSkipToAnotherStatus() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2081, USER_A);

    for (String status : List.of("LISTA", "HABILITADA", "CERRADA", "PLANEADA", "ABIERTA")) {
      assertRlsDenied(() ->
          update(
              APP,
              ADMIN_A,
              "UPDATE tax.administration_fiscal_year SET status = ? WHERE fiscal_year = 2081",
              status));
    }
    assertThat(statusOf(2081)).isEqualTo("PLANEADA");
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · USING deja intacta toda vigencia que no está Planeada:"
          + " volver, repetir o tocar updated_at actualizan 0 filas")
  void applicationCannotTouchAFiscalYearNoLongerPlanned() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2082, USER_A);
    update(APP, ADMIN_A, OPEN, USER_A, ADMIN_A, 2082);

    assertThat(
            update(
                APP,
                ADMIN_A,
                "UPDATE tax.administration_fiscal_year SET status = 'PLANEADA'"
                    + " WHERE fiscal_year = 2082"))
        .as("volver de En preparación a Planeada")
        .isZero();
    assertThat(update(APP, ADMIN_A, OPEN, USER_A, ADMIN_A, 2082))
        .as("segunda apertura, ya sin carrera (V-06)")
        .isZero();
    assertThat(
            update(
                APP,
                ADMIN_A,
                "UPDATE tax.administration_fiscal_year SET updated_at = now()"
                    + " WHERE fiscal_year = 2082"))
        .as("tocar solo updated_at de una En preparación")
        .isZero();

    insertAsOwner(ADMIN_A, 2083, "LISTA", false, USER_A);
    insertAsOwner(ADMIN_A, 2084, "HABILITADA", false, USER_A);
    insertAsOwner(ADMIN_A, 2085, "CERRADA", false, USER_A);
    for (int year = 2083; year <= 2085; year++) {
      assertThat(update(APP, ADMIN_A, OPEN, USER_A, ADMIN_A, year))
          .as("abrir la preparación de " + year + ", que ya está más allá de Planeada")
          .isZero();
    }

    assertThat(statusOf(2082)).isEqualTo("EN_PREPARACION");
    assertThat(statusOf(2083)).isEqualTo("LISTA");
    assertThat(statusOf(2084)).isEqualTo("HABILITADA");
    assertThat(statusOf(2085)).isEqualTo("CERRADA");
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Las políticas de la tabla son exactamente las tres aprobadas, todas para liv_app")
  void policiesAreExactlyTheApprovedOnes() throws SQLException {
    String isolated =
        "(administration_id = (NULLIF(current_setting('app.administration_id'::text, true),"
            + " ''::text))::uuid)";
    String planned = "((status)::text = 'PLANEADA'::text)";
    String opened = "((status)::text = 'EN_PREPARACION'::text)";

    List<Object[]> policies =
        rows(
            OWNER,
            null,
            """
            SELECT policyname || '|' || cmd || '|' || roles::text || '|' || permissive,
                   coalesce(trim(qual), ''), coalesce(trim(with_check), '')
              FROM pg_policies
             WHERE schemaname = 'tax' AND tablename = 'administration_fiscal_year'
             ORDER BY policyname
            """);

    assertThat(policies)
        .extracting(row -> List.of(row[0], row[1], row[2]))
        .containsExactly(
            List.of(
                "administration_create|INSERT|{liv_app}|PERMISSIVE",
                "",
                "(" + isolated + " AND " + planned + ")"),
            List.of(
                "administration_open_preparation|UPDATE|{liv_app}|PERMISSIVE",
                "(" + isolated + " AND " + planned + ")",
                "(" + isolated + " AND " + opened + ")"),
            List.of("administration_read|SELECT|{liv_app}|PERMISSIVE", isolated, ""));
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · SELECT FOR UPDATE no ve una vigencia que ya no está Planeada: no se bloquea por fila")
  void rowLockingHidesAFiscalYearNoLongerPlanned() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2088, USER_A);
    String lock =
        "SELECT id FROM tax.administration_fiscal_year WHERE fiscal_year = 2088 FOR UPDATE";

    assertThat(rows(APP, ADMIN_A, lock)).as("Planeada").hasSize(1);
    update(APP, ADMIN_A, OPEN, USER_A, ADMIN_A, 2088);
    assertThat(rows(APP, ADMIN_A, lock)).as("En preparación").isEmpty();
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Dos aperturas a la vez: gana una (1 fila) y la otra, que esperaba el bloqueo de la"
          + " fila, actualiza 0 filas")
  void concurrentOpeningsHaveASingleWinner() throws Exception {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2087, USER_A);

    // El executor se declara primero para cerrarse después de la conexión: si una aserción falla,
    // la conexión se cierra, suelta el bloqueo y la otra apertura termina.
    try (ExecutorService executor = Executors.newSingleThreadExecutor();
        Connection winner = LivDatabase.connect(APP)) {
      winner.setAutoCommit(false);
      LivDatabase.setContext(winner, ADMIN_A);
      assertThat(update(winner, OPEN, USER_A, ADMIN_A, 2087)).as("la que gana").isEqualTo(1);

      Future<Integer> loser =
          executor.submit(() -> update(APP, ADMIN_A, OPEN, USER_A, ADMIN_A, 2087));
      awaitAnApplicationSessionWaitingOnALock();
      winner.commit();

      assertThat(loser.get(20, TimeUnit.SECONDS)).as("la que esperaba el bloqueo").isZero();
    }
    assertThat(statusOf(2087)).isEqualTo("EN_PREPARACION");
  }

  @Test
  @Tag("007-19")
  @DisplayName("007-19 · updated_by tiene que ser un usuario de la misma administración")
  void updatedByMustBelongToTheAdministration() throws SQLException {
    update(APP, ADMIN_A, INSERT_AS_APP, ADMIN_A, 2086, USER_A);

    SQLException mixed = failureOf(() -> update(APP, ADMIN_A, OPEN, USER_B, ADMIN_A, 2086));
    assertThat(mixed.getSQLState()).isEqualTo(FOREIGN_KEY_VIOLATION);
    assertThat(statusOf(2086)).isEqualTo("PLANEADA");
  }

  private static void awaitAnApplicationSessionWaitingOnALock() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    try (Connection superuser =
        DriverManager.getConnection(LivDatabase.jdbcUrl(), "postgres", "postgres-test-pw")) {
      while (System.nanoTime() < deadline) {
        long waiting =
            ((Number)
                    rows(
                            superuser,
                            "SELECT count(*) FROM pg_stat_activity"
                                + " WHERE usename = 'liv_app' AND wait_event_type = 'Lock'"
                                + " AND datname = 'liv' AND query LIKE '%administration_fiscal_year%'")
                        .get(0)[0])
                .longValue();
        if (waiting > 0) {
          return;
        }
        Thread.sleep(50);
      }
    }
    throw new IllegalStateException("La segunda apertura no llegó a esperar el bloqueo de la fila");
  }

  /** Inserta como dueño (que no pasa por la RLS) una vigencia con el estado que se pida. */
  private static int insertAsOwner(
      UUID administration, int year, String status, boolean assessmentsEnabled, UUID createdBy)
      throws SQLException {
    boolean opened = "EN_PREPARACION".equals(status);
    return update(
        OWNER,
        null,
        INSERT_AS_OWNER,
        administration,
        year,
        status,
        assessmentsEnabled,
        createdBy,
        opened ? createdBy : null,
        opened ? Timestamp.from(Instant.now()) : null);
  }

  /** El estado de la vigencia de A de ese año, leído por la aplicación con el contexto de A. */
  private static String statusOf(int year) throws SQLException {
    return (String)
        rows(
                APP,
                ADMIN_A,
                "SELECT status FROM tax.administration_fiscal_year WHERE fiscal_year = ?",
                year)
            .get(0)[0];
  }
}
