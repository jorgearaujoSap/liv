package co.com.sapenlinea.liv.tax.domain;

import java.util.UUID;

/**
 * Port that verifies one point of the preparation checklist (points 2 to 9 of Doc. 01 §6.1). Each
 * feature of the menu option that attends a point provides its implementation, in {@code
 * tax/infrastructure}. Feature 007 has none: the points then answer "option not available".
 *
 * <p>An implementation reads data of the administration it is given (never of another one) and
 * answers for one point only.
 */
public interface PreparationItemCheck {

  /** The point this implementation verifies. It is never {@code FISCAL_YEAR_IN_PREPARATION}. */
  PreparationPoint point();

  /**
   * Verifies the point for the fiscal year of the administration.
   *
   * @param administrationId the administration of the fiscal year
   * @param year the fiscal year
   */
  PreparationCheckResult check(UUID administrationId, int year);
}
