package co.com.sapenlinea.liv.bff.session;

/**
 * LIV did not start the session: the API refused it (401, 403), failed or did not answer, or the
 * token to call it could not be obtained. The message says what happened without any token or
 * personal data.
 */
public final class SessionNotStartedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean outcomeUnknown;

  /**
   * @param outcomeUnknown whether LIV may have created the session anyway: it failed or did not
   *     answer, so the BFF cannot tell. A clear refusal, or a call that never reached LIV, is not
   *     unknown.
   */
  public SessionNotStartedException(String message, boolean outcomeUnknown) {
    super(message);
    this.outcomeUnknown = outcomeUnknown;
  }

  public boolean outcomeUnknown() {
    return outcomeUnknown;
  }
}
