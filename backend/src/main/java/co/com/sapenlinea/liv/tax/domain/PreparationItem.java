package co.com.sapenlinea.liv.tax.domain;

import java.util.Objects;

/** One evaluated point of the preparation checklist of a fiscal year. */
public record PreparationItem(PreparationPoint point, PreparationItemStatus status, String reason) {

  public PreparationItem {
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(reason, "reason");
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

  /** Whether the point is a required one that is not met. */
  boolean missing() {
    return point.required() && status != PreparationItemStatus.MET;
  }
}
