package co.com.sapenlinea.liv.tax.domain;

import java.util.Objects;

/**
 * Why no year can be created although the user may create: the screen disables the action with this
 * reason.
 *
 * @param reason why
 * @param maxYear the last year that can be created
 */
public record CreateBlocked(Reason reason, int maxYear) {

  /** Reasons a creation is blocked. More may come with other features. */
  public enum Reason {
    /** The year of the last year that can be created already exists (V-05). */
    FUTURE_LIMIT
  }

  public CreateBlocked {
    Objects.requireNonNull(reason, "reason");
  }
}
