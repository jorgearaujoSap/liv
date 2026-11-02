package co.com.sapenlinea.liv.isolation;

import static co.com.sapenlinea.liv.isolation.LivDatabase.APP;
import static co.com.sapenlinea.liv.isolation.LivDatabase.OWNER;
import static co.com.sapenlinea.liv.isolation.LivDatabase.rows;
import static co.com.sapenlinea.liv.isolation.LivDatabase.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Roles, permisos (matriz inicial D-001-5 del plan 001) y bloqueo de tareas programadas. */
class PlatformTablesTest {

  /** Los cinco roles con acceso a las tablas base del Módulo 1 (doc. 01 §4). */
  private static final String TABLE_ROLES =
      "ADMIN_SISTEMA,APROBADOR_TABLAS,AUDITOR,CARGADOR_TABLAS,CONSULTA";

  private static final String ALL_ROLES =
      "ADMIN_SISTEMA,APROBADOR_LIQUIDACION,APROBADOR_RECAUDO,APROBADOR_TABLAS,AUDITOR,"
          + "CARGADOR_TABLAS,CONSULTA,JURIDICO,LIQUIDADOR,RECAUDO,TESORERIA";

  /** Escrita aparte de la migración, tal como la aprobó el responsable funcional en H2. */
  private static final Map<String, String> MATRIX =
      Map.ofEntries(
          Map.entry("core.administration.read", "ADMIN_SISTEMA,AUDITOR"),
          Map.entry("core.user.read", "ADMIN_SISTEMA,AUDITOR"),
          Map.entry("core.parameter.read", "ADMIN_SISTEMA,AUDITOR"),
          Map.entry("audit.event.read", "ADMIN_SISTEMA,AUDITOR"),
          Map.entry("documents.legal-instrument.read", TABLE_ROLES),
          Map.entry("tax.fiscal-year.read", TABLE_ROLES),
          Map.entry("valuation.batch.read", TABLE_ROLES),
          Map.entry("tax.rate.read", TABLE_ROLES),
          Map.entry("valuation.catalog.read", TABLE_ROLES),
          Map.entry("valuation.vehicle-reference.read", TABLE_ROLES),
          Map.entry(
              "jurisdiction.config.read",
              "ADMIN_SISTEMA,APROBADOR_TABLAS,AUDITOR,CARGADOR_TABLAS,JURIDICO"),
          Map.entry(
              "core.vehicle.read", "APROBADOR_LIQUIDACION,AUDITOR,CONSULTA,LIQUIDADOR,RECAUDO"),
          Map.entry("tax.assessment.read", "APROBADOR_LIQUIDACION,AUDITOR,CONSULTA,LIQUIDADOR"),
          Map.entry(
              "ledger.obligation.read", "APROBADOR_RECAUDO,AUDITOR,CONSULTA,RECAUDO,TESORERIA"),
          Map.entry(
              "portfolio.balance.read",
              "APROBADOR_RECAUDO,AUDITOR,CONSULTA,JURIDICO,RECAUDO,TESORERIA"),
          Map.entry("collection.case.read", "AUDITOR,JURIDICO"),
          Map.entry("enforcement.measure.read", "AUDITOR,JURIDICO"),
          Map.entry("documents.document.read", ALL_ROLES));

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · La matriz de permisos sembrada es exactamente la aprobada en H2")
  void permissionMatrixIsTheApprovedOne() throws SQLException {
    Map<String, String> stored = new TreeMap<>();
    rows(
            OWNER,
            null,
            """
            SELECT p.code, string_agg(rp.role_code, ',' ORDER BY rp.role_code)
              FROM platform.app_permission p
              LEFT JOIN platform.role_permission rp ON rp.permission_code = p.code
             GROUP BY p.code
            """)
        .forEach(row -> stored.put((String) row[0], (String) row[1]));

    assertThat(stored).containsExactlyInAnyOrderEntriesOf(MATRIX);
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Los roles son los 11 de doc. 02 §4.2 y todos tienen algún permiso")
  void rolesAreTheConsolidatedOnes() throws SQLException {
    assertThat(
            rows(OWNER, null, "SELECT string_agg(code, ',' ORDER BY code) FROM platform.app_role")
                .get(0)[0])
        .isEqualTo(ALL_ROLES);
    assertThat(
            rows(
                    OWNER,
                    null,
                    "SELECT string_agg(DISTINCT role_code, ',' ORDER BY role_code)"
                        + " FROM platform.role_permission")
                .get(0)[0])
        .isEqualTo(ALL_ROLES);
  }

  @Test
  @DisplayName(
      "T10 · Un bloqueo de ShedLock se toma, se respeta mientras está vigente y se retoma al vencer")
  void shedLockCanBeAcquiredAndReacquired() throws SQLException {
    String acquire =
        """
        INSERT INTO platform.shedlock (name, lock_until, locked_at, locked_by)
        VALUES (?, timezone('utc', now()) + interval '1 minute', timezone('utc', now()), 'host-a')
        ON CONFLICT (name) DO UPDATE
          SET lock_until = timezone('utc', now()) + interval '1 minute',
              locked_at = timezone('utc', now()), locked_by = 'host-b'
          WHERE platform.shedlock.name = ? AND platform.shedlock.lock_until <= timezone('utc', now())
        """;
    assertThat(update(APP, null, acquire, "job-1", "job-1")).isEqualTo(1);
    assertThat(update(APP, null, acquire, "job-1", "job-1")).as("bloqueo vigente").isZero();

    update(
        APP,
        null,
        "UPDATE platform.shedlock SET lock_until = timezone('utc', now()) WHERE name = 'job-1'");
    assertThat(update(APP, null, acquire, "job-1", "job-1")).as("bloqueo vencido").isEqualTo(1);
  }
}
