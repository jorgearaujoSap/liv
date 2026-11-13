package co.com.sapenlinea.liv.shared;

/**
 * Something does not exist in the administration of the user (ADR 0035). It is not a rule, so it
 * has no rule code. Unchecked, like {@link RuleViolation}. {@code platform} answers it with the 404
 * {@code SYS-404} and no detail, so what does not exist and what belongs to another administration
 * look the same (CA-12).
 */
public final class NotFound extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param what what was not found, for the log only (it never reaches the answer): the kind of
   *     thing and a technical identifier, never personal data such as a plate or a document number
   */
  public NotFound(String what) {
    super("Not found: " + what);
  }
}
