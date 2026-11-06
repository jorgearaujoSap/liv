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
   * The same for a session read straight from its table, that nobody is using (T19): one that
   * expired ({@link ExpiredSessions}) or one the provider closed ({@link ProviderLogout}). Deleting
   * it is up to the caller.
   *
   * @param tokens the attribute with the encrypted tokens of the session
   * @return {@code false} only if trying again may succeed. Tokens that cannot be read can never be
   *     revoked: they count as done, with an error in the log
   */
  public boolean closeStored(SessionAuthentication person, Object tokens, EndReason reason) {
    OAuth2AuthorizedClient client = null;
    try {
      client = clients.loadFrom(tokens, person);
    } catch (RuntimeException e) {
      LOG.warn("No se pudieron leer los tokens de la sesión: {}", e.getClass().getSimpleName());
    }
    boolean ended = api.endSession(person.getPrincipal(), reason);
    if (client == null) {
      LOG.error("Una sesión terminada sin petición no tiene tokens legibles: no se puede revocar");
      return ended;
    }
    return ended && revoker.revoke(client);
  }

  /**
   * Only tells LIV that a session ended, for one whose session of the BFF is already gone (an end
   * the provider ordered that LIV did not accept at the time).
   *
   * @return {@code false} only if trying again may succeed
   */
  public boolean endInLiv(SessionIdentity identity, EndReason reason) {
    return api.endSession(identity, reason);
  }

  private void endAndRevoke(
      SessionAuthentication session, OAuth2AuthorizedClient client, EndReason reason) {
    api.endSession(session.getPrincipal(), reason);
    if (client != null) {
      revoker.revoke(client);
    }
  }
}
