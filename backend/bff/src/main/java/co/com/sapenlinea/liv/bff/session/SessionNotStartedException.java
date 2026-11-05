package co.com.sapenlinea.liv.bff.session;

/**
 * LIV did not start the session: the API refused it (401, 403), failed or did not answer. The
 * message says what happened without any token or personal data.
 */
public final class SessionNotStartedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public SessionNotStartedException(String message) {
    super(message);
  }
}
