package co.com.sapenlinea.liv.tax.domain;

import java.util.Objects;

/**
 * Why no year can be created although the user may create: the screen disables the action with this
 * reason.
 *
 * @param reason why
 * @param minYear the first year that can be created
 * @param maxYear the last year that can be created
 */
public record CreateBlocked(Reason reason, int minYear, int maxYear) {

  /** Reasons a creation is blocked. More may come with other features. */
  public enum Reason {
    /** The administration already has every year from the first one to the last (V-05). */
    ALL_CREATED
  }

  public CreateBlocked {
    Objects.requireNonNull(reason, "reason");
    if (minYear > maxYear) {
      throw new IllegalArgumentException(
          "The range is inverted: " + minYear + " is after " + maxYear);
    }
  }
}
