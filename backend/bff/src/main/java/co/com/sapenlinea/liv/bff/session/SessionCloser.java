package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

/**
 * Ends what a session of a person still holds outside the BFF (plan, step 10): LIV is told
 * (EndSession) and the refresh token is revoked in the identity provider, which closes the person's
 * session there. Each step goes on if the previous one fails, so it never throws. Deleting the
 * session of the BFF itself is up to the caller, who always does it, whatever happens here.
 */
public final class SessionCloser {

  private static final Logger LOG = LoggerFactory.getLogger(SessionCloser.class);

  private final EncryptedAuthorizedClientRepository clients;
  private final InternalApiClient api;
  private final RefreshTokenRevoker revoker;

  public SessionCloser(
      EncryptedAuthorizedClientRepository clients,
      InternalApiClient api,
      RefreshTokenRevoker revoker) {
    this.clients = clients;
    this.api = api;
    this.revoker = revoker;
  }

  /**
   * Ends the session in LIV and in the provider and deletes the session of the BFF, whatever
   * happens on the way: the session is over (it passed its limit or the provider refused to renew
   * it).
   */
  public void closeAndDelete(
      HttpServletRequest request, SessionAuthentication session, EndReason reason) {
    try {
      close(request, session, reason);
    } finally {
      HttpSession http = request.getSession(false);
      if (http != null) {
        http.invalidate();
      }
      SecurityContextHolder.clearContext();
    }
  }

  public void close(HttpServletRequest request, SessionAuthentication session, EndReason reason) {
    OAuth2AuthorizedClient client = null;
    try {
      client = clients.loadAuthorizedClient(OidcRegistrations.USER, session, request);
    } catch (RuntimeException e) {
      LOG.warn("No se pudieron leer los tokens de la sesión: {}", e.getClass().getSimpleName());
    }
    endAndRevoke(session, client, reason);
  }

  /**
   * The same for a session read straight from its table, that nobody is using (T19): the sweep and
   * a session found expired when it is read. Deleting it is up to the caller, who does it only if
   * this succeeded, so that a failure is retried.
   *
   * @param tokens the attribute with the encrypted tokens of the session
   * @return whether LIV recorded the end and nothing is left alive in the provider. Tokens that
   *     cannot be read can never be revoked: they count as done, with an error in the log
   */
  public boolean closeExpired(SessionAuthentication person, Object tokens, EndReason reason) {
    OAuth2AuthorizedClient client = null;
    try {
      client = clients.loadFrom(tokens, person);
    } catch (RuntimeException e) {
      LOG.warn("No se pudieron leer los tokens de la sesión: {}", e.getClass().getSimpleName());
    }
    boolean ended = api.endSession(person.getPrincipal(), reason);
    if (client == null) {
      LOG.error("Una sesión vencida no tiene tokens legibles: no se puede revocar en el proveedor");
      return ended;
    }
    return ended && revoker.revoke(client);
  }

  private void endAndRevoke(
      SessionAuthentication session, OAuth2AuthorizedClient client, EndReason reason) {
    api.endSession(session.getPrincipal(), reason);
    if (client != null) {
      revoker.revoke(client);
    }
  }
}
