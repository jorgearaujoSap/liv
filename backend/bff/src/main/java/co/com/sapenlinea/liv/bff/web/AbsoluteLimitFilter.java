package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.EndReason;
import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import co.com.sapenlinea.liv.bff.session.SessionLifetime;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * No session lasts more than 8 hours, however much it is used (001-10). The limit is checked on
 * every request of a person with a session: past it, the session ends (EndSession with {@code
 * ABSOLUTE}, revocation of the refresh token, deletion) and the answer is 401.
 *
 * <p>The sign in and the sign out are not answered with a 401, because they are what the frontend
 * does next when it finds the session over: the session still ends here, as {@code ABSOLUTE}
 * (otherwise the audit would say the person signed out), and the request goes on, so the sign out
 * answers as always and the sign in starts a new one.
 */
final class AbsoluteLimitFilter extends OncePerRequestFilter {

  private final SessionLifetime lifetime;
  private final SessionCloser closer;

  AbsoluteLimitFilter(SessionLifetime lifetime, SessionCloser closer) {
    this.lifetime = lifetime;
    this.closer = closer;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/login/oauth2/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof SessionAuthentication person) {
      HttpSession session = request.getSession(false);
      if (session == null || lifetime.absoluteExpired(session)) {
        closer.closeAndDelete(request, person, EndReason.ABSOLUTE);
        if (!startsOrEndsASession(request)) {
          response.setHeader("Cache-Control", "no-store");
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          return;
        }
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean startsOrEndsASession(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.equals("/bff/login") || path.equals("/bff/logout");
  }
}
