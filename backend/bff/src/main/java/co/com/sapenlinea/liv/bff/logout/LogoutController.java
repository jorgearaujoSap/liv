package co.com.sapenlinea.liv.bff.logout;

import co.com.sapenlinea.liv.bff.session.EndReason;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing out (001-09, plan, step 10). In this order: LIV is told (EndSession with {@code LOGOUT}),
 * the refresh token is revoked in the identity provider (RFC 7009, which closes the person's
 * session there) and the session of the BFF is deleted. The first two are {@link SessionCloser}'s
 * and go on if the previous one fails; the session of the BFF is deleted whatever happens, so the
 * person always ends up signed out. The answer is the LIV route that starts a new sign in, with no
 * token and no reason; the browser navigates to it. Without a session it answers the same and calls
 * nobody, so a second click or an old tab does no harm.
 */
@RestController
final class LogoutController {

  private static final String LOGIN_ROUTE = "/bff/login";

  private final SessionCloser closer;

  LogoutController(SessionCloser closer) {
    this.closer = closer;
  }

  @PostMapping(path = "/bff/logout", produces = MediaType.APPLICATION_JSON_VALUE)
  Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof SessionAuthentication session) {
        closer.close(request, session, EndReason.LOGOUT);
      }
    } finally {
      HttpSession http = request.getSession(false);
      if (http != null) {
        http.invalidate();
      }
      SecurityContextHolder.clearContext();
    }
    response.setHeader("Cache-Control", "no-store");
    return Map.of("redirectTo", LOGIN_ROUTE);
  }
}
