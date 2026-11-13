package co.com.sapenlinea.liv.tax.domain;

/**
 * The nine points of the preparation checklist of a fiscal year (Doc. 01 §6.1). The {@link #name()}
 * is the {@code code} that the contract (tax.yaml) documents for each point.
 *
 * <p>Whether a point is required to enable the assessments is a rule of the functional document
 * (not of a jurisdiction), so it lives here and not in the jurisdiction configuration.
 */
public enum PreparationPoint {
  /** The fiscal year of the administration is in preparation. Comes from its status. */
  FISCAL_YEAR_IN_PREPARATION(1, true, "1.4"),
  VALUATION_RESOLUTION(2, true, "1.3"),
  VALUATION_CATALOG_PUBLISHED(3, true, "1.5.2"),
  TAX_RATES(4, true, "1.5.5"),
  TAX_CALENDAR(5, true, "1.5.6"),
  TAX_BENEFITS(6, false, "1.5.7"),
  PENALTY_RULES(7, false, "1.5.8"),
  SAMPLE_ASSESSMENT(8, true, "3.3"),
  JURISDICTION_CONFIG_PUBLISHED(9, true, "1.5.9");

  private final int number;
  private final boolean required;
  private final String option;

  PreparationPoint(int number, boolean required, String option) {
    this.number = number;
    this.required = required;
    this.option = option;
  }

  /** The number of the point in Doc. 01 §6.1. */
  public int number() {
    return number;
  }

  /** Whether the point must be met to enable the assessments (otherwise it is optional). */
  public boolean required() {
    return required;
  }

  /** The number of the menu option where the point is attended, for example {@code 1.5.2}. */
  public String option() {
    return option;
  }
}
