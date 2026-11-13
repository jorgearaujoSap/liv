package co.com.sapenlinea.liv.tax.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What a {@link PreparationItemCheck} answers for a point: its state and the reason. The reason is
 * a code in upper case with digits and underscores (never a free text, a message or a datum), 1 to
 * 64 characters long, as {@code contracts/openapi/tax.yaml} documents for {@code
 * PreparationItem.reason}; the screen turns it into the message of the design.
 */
public record PreparationCheckResult(PreparationItemStatus status, String reason) {

  /** The reason of a point that is met. */
  public static final String MET_REASON = "MET";

  /** The reason of a point whose menu option does not exist yet. */
  public static final String OPTION_NOT_AVAILABLE = "OPTION_NOT_AVAILABLE";

  private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

  public PreparationCheckResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(reason, "reason");
    if (!REASON_CODE.matcher(reason).matches()) {
      throw new IllegalArgumentException("The reason is a code of 1 to 64 characters: A-Z, 0-9, _");
    }
    boolean isMet = status == PreparationItemStatus.MET;
    if (isMet != MET_REASON.equals(reason)) {
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
