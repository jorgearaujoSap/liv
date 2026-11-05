package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Ends what a session of a person still holds outside the BFF (plan, step 10): LIV is told
 * (EndSession) and the refresh token is revoked in the identity provider, which closes the person's
 * session there. Each step goes on if the previous one fails, so it never throws. Deleting the
 * session of the BFF itself is up to the caller, who always does it, whatever happens here.
 */
public final class SessionCloser {

  private static final Logger LOG = LoggerFactory.getLogger(SessionCloser.class);

  private final OAuth2AuthorizedClientRepository clients;
  private final InternalApiClient api;
  private final RefreshTokenRevoker revoker;

  public SessionCloser(
      OAuth2AuthorizedClientRepository clients,
      InternalApiClient api,
      RefreshTokenRevoker revoker) {
    this.clients = clients;
    this.api = api;
    this.revoker = revoker;
  }

  public void close(HttpServletRequest request, SessionAuthentication session, EndReason reason) {
    OAuth2AuthorizedClient client = null;
    try {
      client = clients.loadAuthorizedClient(OidcRegistrations.USER, session, request);
    } catch (RuntimeException e) {
      LOG.warn("No se pudieron leer los tokens de la sesión: {}", e.getClass().getSimpleName());
    }
    api.endSession(session.getPrincipal(), reason);
    if (client != null) {
      revoker.revoke(client);
    }
  }
}
