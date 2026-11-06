package co.com.sapenlinea.liv.bff.login;

import co.com.sapenlinea.liv.bff.session.EndReason;
import co.com.sapenlinea.liv.bff.session.ExpiredSessions;
import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Starts the sign in at {@code /bff/login}: the authorization request goes to the identity provider
 * with the code flow, PKCE, a nonce and {@code prompt=login}, so the provider always asks for the
 * credentials and never reopens a session by itself (001-01, plan, step 10). The {@code returnTo}
 * of the link is validated and kept in the session of the server, never in the URL of the provider.
 * Any other path is not a sign in: the filter lets it pass.
 *
 * <p>If the browser still has an open session, it is ended first, in LIV and in the provider, and
 * deleted: a new sign in must not overwrite its tokens and leave it open and unaudited.
 */
public final class LoginAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  /** Session attribute with the route of LIV to go back to once the person is authenticated. */
  static final String RETURN_TO_ATTRIBUTE = "LIV_RETURN_TO";

  public static final String LOGIN_PATH = "/bff/login";

  private final RequestMatcher login =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, LOGIN_PATH);
  private final DefaultOAuth2AuthorizationRequestResolver delegate;
  private final SessionCloser closer;
  private final ExpiredSessions expired;

  public LoginAuthorizationRequestResolver(
      ClientRegistrationRepository registrations, SessionCloser closer, ExpiredSessions expired) {
    this.closer = closer;
    this.expired = expired;
    delegate =
        new DefaultOAuth2AuthorizationRequestResolver(
            registrations,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    // The default resolver only adds PKCE for public clients: liv-bff is confidential and uses it
    // too.
    delegate.setAuthorizationRequestCustomizer(
        OAuth2AuthorizationRequestCustomizers.withPkce()
            .andThen(
                request -> request.additionalParameters(extra -> extra.put("prompt", "login"))));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    if (!login.matches(request) || !isANavigation(request)) {
      return null;
    }
    // First the request, which fails if the provider cannot be reached: the open session is only
    // ended once the new sign in can start.
    OAuth2AuthorizationRequest authorization = delegate.resolve(request, OidcRegistrations.USER);
    endTheOpenSession(request);
    request
        .getSession()
        .setAttribute(RETURN_TO_ATTRIBUTE, ReturnTo.sanitize(request.getParameter("returnTo")));
    return authorization;
  }

  /**
   * This GET ends the open session, so only a person who navigates to it may cause that: not an
   * image, a script, a prefetch or any other request a page can make without the person asking.
   * Browsers say what a request is in {@code Sec-Fetch-*}; a client that does not send them is not
   * a browser page and is treated as a navigation (the cookie is {@code SameSite=Strict} anyway).
   */
  private static boolean isANavigation(HttpServletRequest request) {
    String destination = request.getHeader("Sec-Fetch-Dest");
    String mode = request.getHeader("Sec-Fetch-Mode");
    return request.getHeader("Sec-Purpose") == null
        && (destination == null || destination.equals("document"))
        && (mode == null || mode.equals("navigate"));
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return delegate.resolve(request, clientRegistrationId);
  }

  private void endTheOpenSession(HttpServletRequest request) {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof SessionAuthentication open)) {
      // A session that expired is not read (ClosingSessionRepository): it ends here, with IDLE or
      // ABSOLUTE, before the person signs in again.
      String requested = request.getRequestedSessionId();
      if (requested != null) {
        expired.endBeforeANewSignIn(requested);
      }
      return;
    }
    try {
      closer.close(request, open, EndReason.LOGOUT);
    } finally {
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      SecurityContextHolder.clearContext();
    }
  }
}
