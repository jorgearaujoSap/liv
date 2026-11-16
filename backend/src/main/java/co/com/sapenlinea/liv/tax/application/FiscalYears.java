package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import co.com.sapenlinea.liv.tax.domain.RegisteredFiscalYear;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port of the persistence of the fiscal years of the administrations (feature 007). Everything it
 * reads or writes belongs to the administration the transaction runs for: the database applies the
 * row-level security, and the administration the caller passes is the one of its user.
 */
public interface FiscalYears {

  /**
   * Serializes the creations of an administration until the end of the transaction, so two
   * creations never decide from the same list of years. Call it first, in the transaction of the
   * creation, before reading the years.
   */
  void lockForCreation(UUID administrationId);

  /** The fiscal years of the administration, the most recent first. */
  List<RegisteredFiscalYear> findAll(UUID administrationId);

  /** The fiscal year of the administration, if it exists. */
  Optional<RegisteredFiscalYear> find(UUID administrationId, int year);

  /** The national table: the years that can exist, with their dates. */
  List<NationalFiscalYear> nationalYears();

  /**
   * Stores a new fiscal year. The adapter takes any unique violation of this insert for V-01, so
   * the caller must not leave other writes of its transaction pending (it flushes them first).
   *
   * @throws RuleViolation V-01 if the administration already has that year, also when another
   *     creation did not go through {@link #lockForCreation}
   */
  RegisteredFiscalYear create(AdministrationFiscalYear fiscalYear, UUID createdBy);

  /**
   * Stores the opening of the preparation that the domain approved for {@code current}, which was
   * read in this transaction.
   *
   * @throws RuleViolation V-06 with the state it has now, if another request changed the fiscal
   *     year after {@code current} was read
   * @throws NotFound if the fiscal year no longer exists in the administration
   */
  RegisteredFiscalYear persistOpened(RegisteredFiscalYear current, UUID openedBy);
}
