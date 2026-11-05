package co.com.sapenlinea.liv.bff.logout;

import co.com.sapenlinea.liv.bff.session.EndReason;
import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing out (001-09, plan, step 10). In this order: LIV is told (EndSession with {@code LOGOUT}),
 * the refresh token is revoked in the identity provider (RFC 7009, which closes the person's
 * session there) and the session of the BFF is deleted. Each step goes on if the previous one
 * fails: the person always ends up signed out of the BFF. The answer is the LIV route that starts a
 * new sign in, with no token and no reason; the browser navigates to it. Without a session it
 * answers the same and calls nobody, so a second click or an old tab does no harm.
 */
@RestController
final class LogoutController {

  private static final String LOGIN_ROUTE = "/bff/login";

  private final OAuth2AuthorizedClientRepository clients;
  private final InternalApiClient api;
  private final RefreshTokenRevoker revoker;

  LogoutController(
      OAuth2AuthorizedClientRepository clients,
      InternalApiClient api,
      RefreshTokenRevoker revoker) {
    this.clients = clients;
    this.api = api;
    this.revoker = revoker;
  }

  @PostMapping(path = "/bff/logout", produces = MediaType.APPLICATION_JSON_VALUE)
  Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof SessionAuthentication session) {
      OAuth2AuthorizedClient client =
          clients.loadAuthorizedClient(OidcRegistrations.USER, authentication, request);
      api.endSession(session.getPrincipal(), EndReason.LOGOUT);
      if (client != null) {
        revoker.revoke(client);
      }
    }
    HttpSession http = request.getSession(false);
    if (http != null) {
      http.invalidate();
    }
    SecurityContextHolder.clearContext();
    response.setHeader("Cache-Control", "no-store");
    return Map.of("redirectTo", LOGIN_ROUTE);
  }
}
