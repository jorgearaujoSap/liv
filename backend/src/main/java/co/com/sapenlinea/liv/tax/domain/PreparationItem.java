package co.com.sapenlinea.liv.tax.domain;

import java.util.Objects;

/**
 * One evaluated point of the preparation checklist of a fiscal year. It is built from a {@link
 * PreparationCheckResult}, so its state and reason are always valid.
 */
public record PreparationItem(PreparationPoint point, PreparationCheckResult result) {

  public PreparationItem {
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(result, "result");
  }

  /** The number of the point in Doc. 01 §6.1. */
  public int number() {
    return point.number();
  }

  /** What the point is, as the contract names it. */
  public String code() {
    return point.name();
  }

  public boolean required() {
    return point.required();
  }

  /** The number of the menu option where the point is attended. */
  public String option() {
    return point.option();
  }

  public PreparationItemStatus status() {
    return result.status();
  }

  /** Why the point has its state, as a code. */
  public String reason() {
    return result.reason();
  }

  /** Whether the point is a required one that is not met. */
  boolean missing() {
    return point.required() && result.status() != PreparationItemStatus.MET;
  }
}
