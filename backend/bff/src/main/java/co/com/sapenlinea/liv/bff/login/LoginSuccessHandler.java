package co.com.sapenlinea.liv.bff.login;

import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.OidcRegistrations;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionIdentity;
import co.com.sapenlinea.liv.bff.session.SessionLifetime;
import co.com.sapenlinea.liv.bff.session.SessionNotStartedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
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
 * asked for. If LIV does not accept the session, or anything fails on the way, the sign in is
 * rejected (see {@link LoginRejection}): a half-opened session is never left in the database.
 */
public final class LoginSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger LOG = LoggerFactory.getLogger(LoginSuccessHandler.class);

  private final OAuth2AuthorizedClientRepository clients;
  private final InternalApiClient api;
  private final SecurityContextRepository contexts;
  private final LoginRejection rejection;
  private final SessionLifetime lifetime;

  public LoginSuccessHandler(
      OAuth2AuthorizedClientRepository clients,
      InternalApiClient api,
      SecurityContextRepository contexts,
      RefreshTokenRevoker revoker,
      SessionRepository<?> sessions,
      SessionLifetime lifetime) {
    this.lifetime = lifetime;
    this.clients = clients;
    this.api = api;
    this.contexts = contexts;
    this.rejection = new LoginRejection(api, revoker, sessions);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    OidcUser user = (OidcUser) authentication.getPrincipal();
    SessionIdentity identity =
        new SessionIdentity(
            user.getIdToken().getClaimAsString("iss"),
            user.getSubject(),
            user.getClaimAsString("sid"));
    OAuth2AuthorizedClient client = null;
    boolean mayExistInLiv = false;
    try {
      client = clients.loadAuthorizedClient(OidcRegistrations.USER, authentication, request);
      if (client == null || identity.sessionId() == null) {
        throw new SessionNotStartedException(
            "La respuesta del proveedor no trae tokens o sid", false);
      }
      api.startSession(client.getAccessToken().getTokenValue(), identity.sessionId());
      mayExistInLiv = true;
      letThePersonIn(request, response, identity);
      return;
    } catch (SessionNotStartedException e) {
      mayExistInLiv = e.outcomeUnknown();
    } catch (RuntimeException e) {
      LOG.error("El inicio de sesión falló: {}", e.getClass().getSimpleName());
    }
    rejection.reject(request, response, client, mayExistInLiv ? identity : null);
  }

  /** The context without tokens, and the browser back to the route it asked for. */
  private void letThePersonIn(
      HttpServletRequest request, HttpServletResponse response, SessionIdentity identity) {
    SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();
    SecurityContext context = holder.createEmptyContext();
    context.setAuthentication(new SessionAuthentication(identity));
    holder.setContext(context);
    contexts.saveContext(context, request, response);
    lifetime.markStarted(request.getSession());
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
