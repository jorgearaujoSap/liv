package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionLifetime;
import co.com.sapenlinea.liv.bff.session.SessionStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code GET /bff/session}: how long the session has left, without extending it (001-22, 001-10).
 * It runs before Spring Session's filter and outside Spring Security's chain, which is why it
 * works: Spring Session renews a session every time a request goes through its filter, so a request
 * that did would keep the session alive by asking. Here the session is read from the repository by
 * the id of the cookie and nothing is written. It tells nothing about the person.
 */
final class SessionStatusFilter extends OncePerRequestFilter {

  static final String PATH = "/bff/session";

  private final SessionRepository<?> sessions;
  private final HttpSessionIdResolver idResolver;
  private final SessionLifetime lifetime;
  private final Clock clock;

  SessionStatusFilter(
      SessionRepository<?> sessions,
      HttpSessionIdResolver idResolver,
      SessionLifetime lifetime,
      Clock clock) {
    this.sessions = sessions;
    this.idResolver = idResolver;
    this.lifetime = lifetime;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !(HttpMethod.GET.matches(request.getMethod()) && PATH.equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Cache-Control", "no-store");
    Optional<SessionStatus> status = statusOf(request);
    if (status.isEmpty()) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(status.get().toJson());
  }

  private Optional<SessionStatus> statusOf(HttpServletRequest request) {
    // Every cookie, as Spring Session's filter does: the first one may be an old one.
    Session session = null;
    for (String id : idResolver.resolveSessionIds(request)) {
      session = sessions.findById(id);
      if (session != null) {
        break;
      }
    }
    if (session == null
        || !(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
            instanceof SecurityContext context)
        || !(context.getAuthentication() instanceof SessionAuthentication)) {
      return Optional.empty();
    }
    long idle =
        SessionLifetime.ceilSeconds(
            Duration.between(
                clock.instant(),
                session.getLastAccessedTime().plus(session.getMaxInactiveInterval())));
    long absolute = lifetime.absoluteSecondsLeft(session);
    return idle > 0 && absolute > 0
        ? Optional.of(new SessionStatus(idle, absolute))
        : Optional.empty();
  }
}
