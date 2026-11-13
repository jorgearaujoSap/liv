package co.com.sapenlinea.liv.tax.domain;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The fiscal year of one administration (menu 1.4): its state and whether the assessment is
 * enabled. Immutable: an action returns the new value. The year is national; its state is of each
 * administration (Doc. 01 §5).
 *
 * <p>Whether the year can be created at all is decided by {@link FiscalYearOpeningPolicy}.
 *
 * @param administrationId administration the year belongs to
 * @param year the year
 * @param status state of the year in the administration
 * @param assessmentsEnabled never changes in feature 007 (V-03): no action sets it
 */
public record AdministrationFiscalYear(
    UUID administrationId, int year, FiscalYearStatus status, boolean assessmentsEnabled) {

  public AdministrationFiscalYear {
    Objects.requireNonNull(administrationId, "administrationId");
    Objects.requireNonNull(status, "status");
    // V-03: the database also fixes it (CHECK); the domain holds no state that cannot exist.
    // The feature that adds the signed enabling (spec 007, «Trasladado a la 1.5.1») replaces it.
    if (assessmentsEnabled) {
      throw new IllegalArgumentException("assessmentsEnabled must be false in this version");
    }
  }

  /** A new year: {@code PLANEADA}, with the assessment disabled (V-01). */
  public static AdministrationFiscalYear plan(UUID administrationId, int year) {
    return new AdministrationFiscalYear(administrationId, year, FiscalYearStatus.PLANEADA, false);
  }

  /** A year as the persistence stores it. */
  public static AdministrationFiscalYear restore(
      UUID administrationId, int year, FiscalYearStatus status, boolean assessmentsEnabled) {
    return new AdministrationFiscalYear(administrationId, year, status, assessmentsEnabled);
  }

  /**
   * Opens the preparation: {@code PLANEADA} to {@code EN_PREPARACION}.
   *
   * @throws RuleViolation V-06 (conflict) from any other state, with the year and the current state
   */
  public AdministrationFiscalYear openPreparation() {
    if (status != FiscalYearStatus.PLANEADA) {
      throw new RuleViolation(
          "V-06",
          RuleViolation.Type.CONFLICT,
          Map.of("year", year, "currentStatus", status.name()));
    }
    return new AdministrationFiscalYear(
        administrationId, year, FiscalYearStatus.EN_PREPARACION, assessmentsEnabled);
  }
}
