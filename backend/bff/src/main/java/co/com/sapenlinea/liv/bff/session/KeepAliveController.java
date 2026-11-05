package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The person chose to stay connected (001-22). The token is always renewed, even if it is not about
 * to expire, which also restarts the inactivity of the identity provider; the request itself
 * extends the session of the BFF, which is Spring Session's job. The answer is what is left of the
 * session. It does not pass the maximum duration (the filter before it ends the session first). If
 * the provider refuses the renewal the session is over: it ends with {@code PROVIDER} and the
 * answer is 401; if the provider does not answer, the session stays and the answer is 502.
 */
@RestController
final class KeepAliveController {

  private final TokenRenewal renewal;
  private final SessionCloser closer;
  private final SessionLifetime lifetime;

  KeepAliveController(TokenRenewal renewal, SessionCloser closer, SessionLifetime lifetime) {
    this.renewal = renewal;
    this.closer = closer;
    this.lifetime = lifetime;
  }

  @PostMapping(path = "/bff/session/keepalive", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SessionStatus> keepAlive(
      HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof SessionAuthentication person)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      renewal.renewNow(request, response, person);
    } catch (TokenRenewalException e) {
      if (!e.sessionIsOver()) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
      }
      closer.closeAndDelete(request, person, EndReason.PROVIDER);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    HttpSession session = request.getSession(false);
    long idle = session == null ? 0 : session.getMaxInactiveInterval();
    long absolute = session == null ? 0 : lifetime.absoluteSecondsLeft(session);
    return ResponseEntity.ok(new SessionStatus(idle, absolute));
  }
}
