package co.com.sapenlinea.liv.bff.session;

/**
 * The access token of a session could not be renewed. {@link #sessionIsOver()} says whether the
 * session is over (the provider refused the renewal with {@code invalid_grant}: the user was
 * disabled or the session was closed there, or there are no tokens left) or the failure may be
 * passing (the provider did not answer): then the session stays and the person can try again.
 */
public final class TokenRenewalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final boolean sessionIsOver;

  public TokenRenewalException(String message, boolean sessionIsOver) {
    super(message);
    this.sessionIsOver = sessionIsOver;
  }

  public boolean sessionIsOver() {
    return sessionIsOver;
  }
}
