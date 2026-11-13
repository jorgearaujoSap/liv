package co.com.sapenlinea.liv.tax.domain;

import java.util.Objects;

/**
 * What a {@link PreparationItemCheck} answers for a point: its state and the reason, a code (not a
 * text) that the screen turns into the message of the design. The reason has at most 64 characters,
 * as the contract says.
 */
public record PreparationCheckResult(PreparationItemStatus status, String reason) {

  /** The reason of a point that is met. */
  public static final String MET_REASON = "MET";

  /** The reason of a point whose menu option does not exist yet. */
  public static final String OPTION_NOT_AVAILABLE = "OPTION_NOT_AVAILABLE";

  private static final int MAX_REASON_LENGTH = 64;

  public PreparationCheckResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(reason, "reason");
    if (reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
      throw new IllegalArgumentException("The reason is a code of 1 to 64 characters");
    }
    if (status == PreparationItemStatus.MET != MET_REASON.equals(reason)) {
      throw new IllegalArgumentException("Only a point that is met has the reason MET");
    }
  }

  public static PreparationCheckResult met() {
    return new PreparationCheckResult(PreparationItemStatus.MET, MET_REASON);
  }

  public static PreparationCheckResult pending(String reason) {
    return new PreparationCheckResult(PreparationItemStatus.PENDING, reason);
  }

  public static PreparationCheckResult observed(String reason) {
    return new PreparationCheckResult(PreparationItemStatus.OBSERVED, reason);
  }

  /** The answer for a point whose menu option does not exist yet: pending, never met. */
  static PreparationCheckResult optionNotAvailable() {
    return pending(OPTION_NOT_AVAILABLE);
  }
}
