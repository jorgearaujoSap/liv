package co.com.sapenlinea.liv.bff.login;

import co.com.sapenlinea.liv.bff.session.EndReason;
import co.com.sapenlinea.liv.bff.session.InternalApiClient;
import co.com.sapenlinea.liv.bff.session.RefreshTokenRevoker;
import co.com.sapenlinea.liv.bff.session.SessionIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.session.SessionRepository;

/**
 * What happens when LIV does not accept a sign in (001-05, 001-17), or the provider's answer cannot
 * be used: the refresh token is revoked, so the person's session in the provider closes too, the
 * session of the BFF is deleted and the browser goes back to {@code /bff/login}. There is no reason
 * in the URL or in the page: the login page of the provider does not receive parameters (spike
 * T06). When LIV refused, it already audited the rejection (SessionDenied).
 *
 * <p>If LIV may have created the session anyway (its answer was lost), it is ended with {@code
 * PROVIDER}: EndSession answers 204 even when there is nothing to end.
 *
 * <p>The session id was already changed in this request (session fixation), but Spring Session JDBC
 * only writes the new id when the request ends, and {@code invalidate()} deletes by the new one:
 * the row would stay in the database with the encrypted tokens. So the session is also deleted by
 * the id the database still has, the one the browser sent. Whatever fails on the way, the browser
 * goes back to {@code /bff/login}.
 */
final class LoginRejection {

  private final InternalApiClient api;
  private final RefreshTokenRevoker revoker;
  private final SessionRepository<?> sessions;

  LoginRejection(
      InternalApiClient api, RefreshTokenRevoker revoker, SessionRepository<?> sessions) {
    this.api = api;
    this.revoker = revoker;
    this.sessions = sessions;
  }

  /**
   * @param client the tokens obtained, or null if there are none to revoke
   * @param toEnd the session to end in LIV because it may exist there, or null
   */
  void reject(
      HttpServletRequest request,
      HttpServletResponse response,
      OAuth2AuthorizedClient client,
      SessionIdentity toEnd) {
    try {
      if (toEnd != null) {
        api.endSession(toEnd, EndReason.PROVIDER);
      }
      if (client != null) {
        revoker.revoke(client);
      }
    } finally {
      deleteTheSession(request, response);
    }
  }

  private void deleteTheSession(HttpServletRequest request, HttpServletResponse response) {
    try {
      String persistedId = request.getRequestedSessionId();
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      if (persistedId != null) {
        sessions.deleteById(persistedId);
      }
      SecurityContextHolder.clearContext();
    } finally {
      redirect(response, LoginAuthorizationRequestResolver.LOGIN_PATH);
    }
  }

  /** A relative redirect, with the status and the {@code Location} exactly as given. */
  static void redirect(HttpServletResponse response, String path) {
    response.setStatus(HttpServletResponse.SC_FOUND);
    response.setHeader("Location", path);
  }
}
