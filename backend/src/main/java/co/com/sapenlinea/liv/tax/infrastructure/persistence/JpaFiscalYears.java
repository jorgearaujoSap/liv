package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.application.FiscalYears;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The adapter of {@link FiscalYears} with JPA. Every method joins the transaction of the use case
 * ({@code MANDATORY}): the lock of the creation lives as long as that transaction, and the
 * row-level security context is set when it begins.
 *
 * <p>It never locks the rows of {@code tax.administration_fiscal_year} ({@code FOR UPDATE}): the
 * update policy hides a year that is no longer {@code PLANEADA}, so such a lock would not see it.
 * The opening is a conditional {@code UPDATE} that counts rows instead.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaFiscalYears implements FiscalYears {

  /** First argument of the advisory lock: the space of the creation of fiscal years. */
  private static final int CREATION_LOCK_SPACE = 7_001;

  private static final String UNIQUE_VIOLATION = "23505";

  /** The unique constraint on {@code (administration_id, fiscal_year)} (V-01). */
  private static final String YEAR_KEY = "administration_fiscal_year_year_key";

  private final AdministrationFiscalYearRepository rows;
  private final NationalFiscalYearRepository national;

  JpaFiscalYears(AdministrationFiscalYearRepository rows, NationalFiscalYearRepository national) {
    this.rows = rows;
    this.national = national;
  }

  @Override
  public void lockForCreation(UUID administrationId) {
    rows.lockForCreation(CREATION_LOCK_SPACE, administrationId.toString());
  }

  @Override
  public List<Stored> findAll(UUID administrationId) {
    return rows.findByAdministrationIdOrderByFiscalYearDesc(administrationId).stream()
        .map(this::toStored)
        .toList();
  }

  @Override
  public Optional<Stored> find(UUID administrationId, int year) {
    // A year that does not fit the SMALLINT cannot exist, and a cast would wrap it into another
    // year.
    if (year < Short.MIN_VALUE || year > Short.MAX_VALUE) {
      return Optional.empty();
    }
    return rows.findByAdministrationIdAndFiscalYear(administrationId, (short) year)
        .map(this::toStored);
  }

  @Override
  public List<NationalFiscalYear> nationalYears() {
    return national.findAllByOrderByYearAsc().stream()
        .map(year -> new NationalFiscalYear(year.year(), year.startsOn(), year.endsOn()))
        .toList();
  }

  @Override
  public Stored create(AdministrationFiscalYear fiscalYear, UUID createdBy) {
    if (!fitsSmallint(fiscalYear.year())) {
      throw new IllegalArgumentException("The year does not fit the column: " + fiscalYear.year());
    }
    try {
      return toStored(
          rows.saveAndFlush(
              new AdministrationFiscalYearEntity(
                  fiscalYear.administrationId(), (short) fiscalYear.year(), createdBy)));
    } catch (DataIntegrityViolationException e) {
      if (breaksTheYearKey(e)) {
        throw new RuleViolation(
            "V-01", RuleViolation.Type.CONFLICT, Map.of("year", fiscalYear.year()));
      }
      throw e;
    }
  }

  @Override
  public Stored persistOpened(Stored current, UUID openedBy) {
    int changed = rows.openPreparation(current.id(), openedBy);
    // The persistence context was cleared by the update, so this read comes from the database.
    Stored now =
        find(current.fiscalYear().administrationId(), current.fiscalYear().year())
            .orElseThrow(() -> new NotFound("fiscal year " + current.fiscalYear().year()));
    if (changed == 1) {
      return now;
    }
    // 0 rows: another request opened it first. The domain answers V-06 with the state it has now:
    // openPreparation() always throws here, because the fiscal year is no longer PLANEADA.
    now.fiscalYear().openPreparation();
    throw new IllegalStateException(
        "The update changed no row but the fiscal year is still " + FiscalYearStatus.PLANEADA);
  }

  private static boolean fitsSmallint(int year) {
    return year >= Short.MIN_VALUE && year <= Short.MAX_VALUE;
  }

  private Stored toStored(AdministrationFiscalYearEntity row) {
    NationalFiscalYearEntity year =
        national
            .findById((short) row.fiscalYear())
            .orElseThrow(() ->
                new IllegalStateException(
                    "The year " + row.fiscalYear() + " is not in the national table"));
    return new Stored(
        row.id(),
        AdministrationFiscalYear.restore(
            row.administrationId(),
            row.fiscalYear(),
            FiscalYearStatus.valueOf(row.status()),
            row.assessmentsEnabled()),
        year.startsOn(),
        year.endsOn());
  }

  /**
   * Whether the failure is the {@code UNIQUE (administration_id, fiscal_year)}: the SQLState 23505
   * and the name of the constraint that the server reports. The name comes from the fields of the
   * error of the PostgreSQL driver ({@code getServerErrorMessage().getConstraint()}), not from its
   * text, so it does not depend on the language of the server. The driver is a runtime dependency,
   * so it is read by reflection; if it cannot be read, the failure is not taken for V-01.
   */
  static boolean breaksTheYearKey(DataIntegrityViolationException failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
        return YEAR_KEY.equals(constraintOf(sql));
      }
    }
    return false;
  }

  /** {@code PSQLException.getServerErrorMessage().getConstraint()}, or null if not available. */
  private static String constraintOf(SQLException sql) {
    try {
      Object message = sql.getClass().getMethod("getServerErrorMessage").invoke(sql);
      return message == null
          ? null
          : (String) message.getClass().getMethod("getConstraint").invoke(message);
    } catch (ReflectiveOperationException | ClassCastException e) {
      return null;
    }
  }
}
