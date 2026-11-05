package co.com.sapenlinea.liv.bff.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * The provider's answer cannot be used: the person cancelled, the state does not match a sign in
 * that started here, or the code could not be exchanged. The session that held the authorization
 * request is deleted and the browser goes back to {@code /bff/login}, without a reason (001-01).
 * Only the error code is logged, never the answer.
 */
public final class LoginFailureHandler implements AuthenticationFailureHandler {

  private static final Logger LOG = LoggerFactory.getLogger(LoginFailureHandler.class);

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
    String code =
        exception instanceof OAuth2AuthenticationException oauth2
            ? oauth2.getError().getErrorCode()
            : exception.getClass().getSimpleName();
    LOG.warn("El regreso del proveedor no se pudo usar: {}", code);
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    LoginRejection.redirect(response, LoginAuthorizationRequestResolver.LOGIN_PATH);
  }
}
