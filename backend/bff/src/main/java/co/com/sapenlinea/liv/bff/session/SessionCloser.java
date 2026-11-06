package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;

/**
 * Ends what a session of a person still holds outside the BFF (plan, step 10): LIV is told
 * (EndSession) and the refresh token is revoked in the identity provider, which closes the person's
 * session there. Each step goes on if the previous one fails, so it never throws. Deleting the
 * session of the BFF itself is up to the caller, who always does it, whatever happens here.
 */
public final class SessionCloser {

  private static final Logger LOG = LoggerFactory.getLogger(SessionCloser.class);

  /** Where Spring Security keeps the authentication in the session. */
  private static final String SECURITY_CONTEXT =
      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

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
   * The same for a session read from the repository, that nobody is using (T19): the sweep and a
   * session found expired when it is read. A session without a person of LIV (a sign in that was
   * never finished) has nothing to end outside the BFF. Deleting it is up to the caller.
   */
  public void close(Session stored, EndReason reason) {
    if (!(stored.getAttribute(SECURITY_CONTEXT) instanceof SecurityContext context)
        || !(context.getAuthentication() instanceof SessionAuthentication session)) {
      return;
    }
    OAuth2AuthorizedClient client = null;
    try {
      client = clients.loadFrom(stored, session);
    } catch (RuntimeException e) {
      LOG.warn("No se pudieron leer los tokens de la sesión: {}", e.getClass().getSimpleName());
    }
    endAndRevoke(session, client, reason);
  }

  private void endAndRevoke(
      SessionAuthentication session, OAuth2AuthorizedClient client, EndReason reason) {
    api.endSession(session.getPrincipal(), reason);
    if (client != null) {
      revoker.revoke(client);
    }
  }
}
