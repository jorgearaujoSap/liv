package co.com.sapenlinea.liv.bff.login;

import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * The provider's answer cannot be used: the person cancelled, the state does not match a sign in
 * that started here, or the code could not be exchanged. The session that held the authorization
 * request is deleted and the browser goes back to {@code /bff/login}, without a reason (001-01).
 * Only the error code is logged, never the answer, and only if it looks like one (the provider's
 * parameter is not trusted).
 *
 * <p>A session that is already open is left alone: a stale or repeated answer must not drop it
 * without ending it in LIV and in the provider. It is the sign out, or the next {@code /bff/login},
 * that ends it.
 */
public final class LoginFailureHandler implements AuthenticationFailureHandler {

  private static final Logger LOG = LoggerFactory.getLogger(LoginFailureHandler.class);
  private static final Pattern ERROR_CODE = Pattern.compile("[A-Za-z0-9_]{1,64}");

  private final SecurityContextRepository contexts;

  public LoginFailureHandler(SecurityContextRepository contexts) {
    this.contexts = contexts;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
    String code =
        exception instanceof OAuth2AuthenticationException oauth2
            ? oauth2.getError().getErrorCode()
            : exception.getClass().getSimpleName();
    LOG.warn(
        "El regreso del proveedor no se pudo usar: {}",
        ERROR_CODE.matcher(code).matches() ? code : "otro");
    if (!hasAnOpenSession(request)) {
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
    }
    LoginRejection.redirect(response, LoginAuthorizationRequestResolver.LOGIN_PATH);
  }

  /** The filter clears the context before calling this handler: it is read from the session. */
  private boolean hasAnOpenSession(HttpServletRequest request) {
    return contexts.loadDeferredContext(request).get().getAuthentication()
        instanceof SessionAuthentication;
  }
}
