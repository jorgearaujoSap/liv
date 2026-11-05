package co.com.sapenlinea.liv.bff.login;

import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionIdentity;
import co.com.sapenlinea.liv.bff.session.SessionNotStartedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.SessionRepository;

/**
 * The person authenticated in the identity provider and Spring Security already exchanged the code
 * for tokens, kept them encrypted and changed the id of the session (session fixation, 001-11).
 * What is left is to open the session in LIV (StartSession, with both tokens) and to let the person
 * in: the security context is replaced by one without tokens and the browser goes to the route it
 * asked for. If LIV does not accept the session, the sign in is rejected (see {@link
 * LoginRejection}).
 */
public final class LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final OAuth2AuthorizedClientRepository clients;
  private final InternalApiClient api;
  private final SecurityContextRepository contexts;
  private final LoginRejection rejection;

  public LoginSuccessHandler(
      OAuth2AuthorizedClientRepository clients,
      InternalApiClient api,
      SecurityContextRepository contexts,
      RefreshTokenRevoker revoker,
      SessionRepository<?> sessions) {
    this.clients = clients;
    this.api = api;
    this.contexts = contexts;
    this.rejection = new LoginRejection(revoker, sessions);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    OAuth2AuthorizedClient client =
        clients.loadAuthorizedClient(OidcRegistrations.USER, authentication, request);
    OidcUser user = (OidcUser) authentication.getPrincipal();
    String sessionId = user.getClaimAsString("sid");
    try {
      if (client == null || sessionId == null) {
        throw new SessionNotStartedException("La respuesta del proveedor no trae tokens o sid");
      }
      api.startSession(client.getAccessToken().getTokenValue(), sessionId);
    } catch (SessionNotStartedException e) {
      rejection.reject(request, response, client);
      return;
    }
    SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
    context.setAuthentication(
        new SessionAuthentication(
            new SessionIdentity(
                user.getIdToken().getClaimAsString("iss"), user.getSubject(), sessionId)));
    SecurityContextHolder.getContextHolderStrategy().setContext(context);
    contexts.saveContext(context, request, response);
    LoginRejection.redirect(response, takeReturnTo(request));
  }

  /** The route asked for before the sign in, validated when it was received. It is used once. */
  private static String takeReturnTo(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    Object saved =
        session == null
            ? null
            : session.getAttribute(LoginAuthorizationRequestResolver.RETURN_TO_ATTRIBUTE);
    if (session != null) {
      session.removeAttribute(LoginAuthorizationRequestResolver.RETURN_TO_ATTRIBUTE);
    }
    return saved instanceof String path ? ReturnTo.sanitize(path) : ReturnTo.HOME;
  }
}
