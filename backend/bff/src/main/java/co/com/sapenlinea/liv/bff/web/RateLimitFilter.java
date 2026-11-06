package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the requests (doc. 02 §8; T19): every request counts for its address; a request of a
 * person with a session counts also for the person; the sign in ({@code /bff/login} and the return
 * from the provider) counts also in its own, stricter, limit per address. Past any of them the
 * answer is 429, with {@code Retry-After} in seconds, and the request goes no further.
 *
 * <p>The address is the one of the client as Tomcat sees it: behind the load balancer, the one it
 * forwards ({@code server.forward-headers-strategy: native}, which trusts only the internal
 * network). The back channel of the provider does not count: it calls from the internal network,
 * one address for every session it closes, and has its own limit ({@code
 * BackchannelLogoutController}).
 */
final class RateLimitFilter extends OncePerRequestFilter {

  static final String PER_IP = "ip:";
  static final String PER_USER = "user:";
  static final String SIGN_IN = "sign-in:";

  private final RateLimiter limiter;
  private final RateLimitProperties limits;

  RateLimitFilter(RateLimiter limiter, RateLimitProperties limits) {
    this.limiter = limiter;
    this.limits = limits;
  }

  /** Which limit each kind of key has, for forgetting the full buckets. */
  Map<String, RateLimitProperties.Limit> limitsByPrefix() {
    return Map.of(PER_IP, limits.perIp(), PER_USER, limits.perUser(), SIGN_IN, limits.signIn());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().equals("/bff/backchannel-logout");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String address = request.getRemoteAddr();
    long wait = limiter.take(PER_IP + address, limits.perIp());
    if (wait == 0 && isASignIn(request)) {
      wait = limiter.take(SIGN_IN + address, limits.signIn());
    }
    if (wait == 0
        && SecurityContextHolder.getContext().getAuthentication()
            instanceof SessionAuthentication person) {
      wait =
          limiter.take(
              PER_USER + person.getPrincipal().issuer() + " " + person.getName(), limits.perUser());
    }
    if (wait > 0) {
      tooMany(response, wait);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isASignIn(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.equals("/bff/login") || path.startsWith("/login/oauth2/code/");
  }

  private static void tooMany(HttpServletResponse response, long seconds) throws IOException {
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(seconds));
    response.setHeader("Cache-Control", "no-store");
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"status\":429,\"title\":\"Demasiadas solicitudes. Intenta de nuevo en un momento.\"}");
  }
}
