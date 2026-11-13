package co.com.sapenlinea.liv.tax.domain;

import java.util.UUID;

/**
 * Port that verifies one point of the preparation checklist (points 2 to 9 of Doc. 01 §6.1). Each
 * feature of the menu option that attends a point provides its implementation. Feature 007 has
 * none: the points then answer "option not available", so the enabling is impossible.
 *
 * <p>Contract for the implementations:
 *
 * <ul>
 *   <li>They answer for one point only and never for point 1, which comes from the state of the
 *       fiscal year. Two implementations for one point are rejected.
 *   <li>{@code administrationId} is the one of the authenticated user and the implementation reads
 *       under the row-level security of that administration: the parameter is not what isolates the
 *       data. An implementation never takes the administration from another source.
 *   <li>They read results already stored (for example, the last sample assessment of point 8) and
 *       do not compute them on each query.
 *   <li>They answer {@code PENDING} with a code when the data is missing. An exception (a database
 *       or a module that fails) is not turned into an answer: it fails the whole checklist, so a
 *       point is never shown as met by mistake.
 *   <li>Where an implementation lives, and which module it depends on, is a decision of its feature
 *       (a facade or an event of the module that owns the data, checked by {@code ModularityTest});
 *       a new dependency between modules needs its own ADR.
 * </ul>
 */
public interface PreparationItemCheck {

  /** The point this implementation verifies. It is never {@code FISCAL_YEAR_IN_PREPARATION}. */
  PreparationPoint point();

  /**
   * Verifies the point for the fiscal year of the administration.
   *
   * @param administrationId the administration of the authenticated user, owner of the fiscal year
   * @param year the fiscal year
   */
  PreparationCheckResult check(UUID administrationId, int year);
}
