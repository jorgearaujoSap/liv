package co.com.sapenlinea.liv.tax.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The preparation checklist of a fiscal year (Doc. 01 §6.1), read only. It is evaluated once and is
 * immutable.
 *
 * <ul>
 *   <li>Point 1 comes from the state of the fiscal year, which arrives as data: it is met once the
 *       preparation is open.
 *   <li>Points 2 to 9 come from the {@link PreparationItemCheck} port. A point without an
 *       implementation is pending with the reason {@code OPTION_NOT_AVAILABLE}, never met. Feature
 *       007 has no implementations, so the enabling is impossible until a later feature provides
 *       them (V-03, CA-14).
 * </ul>
 */
public final class PreparationChecklist {

  private static final String FISCAL_YEAR_PLANNED = "FISCAL_YEAR_PLANNED";

  private final List<PreparationItem> items;
  private final List<Integer> missing;

  private PreparationChecklist(List<PreparationItem> items) {
    this.items = List.copyOf(items);
    this.missing =
        this.items.stream().filter(PreparationItem::missing).map(PreparationItem::number).toList();
  }

  /**
   * Evaluates the checklist. An exception of an implementation of the port is not caught: it fails
   * the whole evaluation (see {@link PreparationItemCheck}).
   *
   * @param administrationId the administration of the fiscal year
   * @param year the fiscal year, already validated by the caller (this class does not check its
   *     range)
   * @param preparationOpened false only while the fiscal year is {@code PLANEADA}; true in {@code
   *     EN_PREPARACION} and in every later state. The caller must derive it from the state, and its
   *     test must cover the five states
   * @param checks the implementations of the port, at most one per point and none for point 1
   * @throws IllegalArgumentException if a point has two implementations or point 1 has one
   */
  public static PreparationChecklist evaluate(
      UUID administrationId,
      int year,
      boolean preparationOpened,
      List<PreparationItemCheck> checks) {
    Objects.requireNonNull(administrationId, "administrationId");
    Map<PreparationPoint, PreparationItemCheck> byPoint = indexByPoint(checks);

    List<PreparationItem> evaluated = new ArrayList<>(PreparationPoint.values().length);
    for (PreparationPoint point : PreparationPoint.values()) {
      evaluated.add(
          point == PreparationPoint.FISCAL_YEAR_IN_PREPARATION
              ? fiscalYearPoint(preparationOpened)
              : fromPort(point, byPoint.get(point), administrationId, year));
    }
    return new PreparationChecklist(evaluated);
  }

  private static Map<PreparationPoint, PreparationItemCheck> indexByPoint(
      List<PreparationItemCheck> checks) {
    Objects.requireNonNull(checks, "checks");
    Map<PreparationPoint, PreparationItemCheck> byPoint = new EnumMap<>(PreparationPoint.class);
    for (PreparationItemCheck check : checks) {
      Objects.requireNonNull(check, "check");
      PreparationPoint point = Objects.requireNonNull(check.point(), "point of a check");
      if (point == PreparationPoint.FISCAL_YEAR_IN_PREPARATION) {
        throw new IllegalArgumentException("Point 1 comes from the state of the fiscal year");
      }
      if (byPoint.putIfAbsent(point, check) != null) {
        throw new IllegalArgumentException("Two checks for the point " + point.number());
      }
    }
    return byPoint;
  }

  private static PreparationItem fiscalYearPoint(boolean preparationOpened) {
    PreparationPoint point = PreparationPoint.FISCAL_YEAR_IN_PREPARATION;
    return new PreparationItem(
        point,
        preparationOpened
            ? PreparationCheckResult.met()
            : PreparationCheckResult.pending(FISCAL_YEAR_PLANNED));
  }

  private static PreparationItem fromPort(
      PreparationPoint point, PreparationItemCheck check, UUID administrationId, int year) {
    PreparationCheckResult result =
        check == null
            ? PreparationCheckResult.optionNotAvailable()
            : Objects.requireNonNull(
                check.check(administrationId, year), "result of point " + point.number());
    return new PreparationItem(point, result);
  }

  /** The nine points, in the order of Doc. 01 §6.1. */
  public List<PreparationItem> items() {
    return items;
  }

  /**
   * True only when every required point is met (V-03). In feature 007 it is always false because
   * the port has no implementations. It is a reading of the moment, not a permission: enabling the
   * assessments also needs the state of the fiscal year, read again inside its own transaction (a
   * later feature, 1.5.1).
   */
  public boolean canEnable() {
    return missing.isEmpty();
  }

  /** The numbers of the required points that are not met, in ascending order. */
  public List<Integer> missing() {
    return missing;
  }
}
