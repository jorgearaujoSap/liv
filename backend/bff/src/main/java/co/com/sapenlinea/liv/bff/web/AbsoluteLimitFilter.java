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
 * ABSOLUTE}, revocation of the refresh token, deletion) and the answer is 401. The sign in and the
 * sign out are left alone: they end the session by their own means.
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
    String path = request.getRequestURI();
    return path.equals("/bff/login")
        || path.equals("/bff/logout")
        || path.startsWith("/login/oauth2/");
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
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
