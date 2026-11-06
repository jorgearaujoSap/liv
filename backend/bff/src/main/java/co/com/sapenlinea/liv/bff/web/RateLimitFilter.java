package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.SessionAuthentication;
import co.com.sapenlinea.liv.bff.web.RateLimitProperties.Limit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the requests (doc. 02 §8; plan, step 10; T19). A request of a person with a session counts
 * for the person; one without a session, for its address. The sign in ({@code /bff/login}) counts
 * also in its own, stricter, limit per address: the return from the provider does not, because it
 * only works with the {@code state} the sign in left, and refusing it would lose a sign in already
 * made. Past a limit the answer is 429 with {@code Retry-After} in seconds, and the request goes no
 * further: a page for the sign in, which is a navigation, and Problem Details for the rest.
 *
 * <p>Two routes never count: the sign out, so that a person can always leave (the session of a
 * shared computer must end), and the back channel of the provider, which has its own limit ({@code
 * BackchannelLogoutController}). The paths are compared as the server reads them ({@code
 * getServletPath}, decoded and normalized), so {@code /bff/%6Cogin} is the sign in too.
 *
 * <p>The address is the one of the client as Tomcat sees it: behind the load balancer, the one it
 * forwards, accepted only from the addresses in {@code LIV_BFF_TRUSTED_PROXIES}. IPv6 addresses
 * count by their /64, the network a single client gets.
 */
final class RateLimitFilter extends OncePerRequestFilter {

  static final String PER_IP = "ip:";
  static final String SIGN_IN = "sign-in:";

  private static final String SIGN_IN_PATH = "/bff/login";
  private static final Set<String> NOT_LIMITED = Set.of("/bff/logout", "/bff/backchannel-logout");

  private final RateLimiter limiter;
  private final RateLimitProperties limits;

  RateLimitFilter(RateLimiter limiter, RateLimitProperties limits) {
    this.limiter = limiter;
    this.limits = limits;
  }

  /** The share of this replica of the limit of a person. */
  Limit perUser() {
    return limits.ofOneReplica(limits.perUser());
  }

  /** Which limit each kind of key of an address has, for forgetting the full buckets. */
  Map<String, Limit> addressLimitsByPrefix() {
    return Map.of(
        PER_IP, limits.ofOneReplica(limits.perIp()),
        SIGN_IN, limits.ofOneReplica(limits.signIn()));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return NOT_LIMITED.contains(request.getServletPath());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String address = networkOf(request.getRemoteAddr());
    boolean signIn = SIGN_IN_PATH.equals(request.getServletPath());
    long wait;
    if (SecurityContextHolder.getContext().getAuthentication()
        instanceof SessionAuthentication person) {
      wait =
          limiter.takeForAPerson(
              person.getPrincipal().issuer() + " " + person.getName(), perUser());
    } else {
      wait = limiter.takeForAnAddress(PER_IP + address, limits.ofOneReplica(limits.perIp()));
    }
    if (wait == 0 && signIn) {
      wait = limiter.takeForAnAddress(SIGN_IN + address, limits.ofOneReplica(limits.signIn()));
    }
    if (wait > 0) {
      tooMany(response, wait, signIn);
      return;
    }
    chain.doFilter(request, response);
  }

  /** The address, or the /64 network of an IPv6 address. */
  static String networkOf(String address) {
    if (address == null || !address.contains(":")) {
      return address;
    }
    try {
      byte[] bytes = InetAddress.getByName(address).getAddress();
      return bytes.length == 16 ? HexFormat.of().formatHex(bytes, 0, 8) + "::/64" : address;
    } catch (UnknownHostException e) {
      return address;
    }
  }

  private static void tooMany(HttpServletResponse response, long seconds, boolean aPage)
      throws IOException {
    response.setStatus(429);
    response.setHeader("Retry-After", Long.toString(seconds));
    response.setHeader("Cache-Control", "no-store");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    if (aPage) {
      response.setContentType(MediaType.TEXT_HTML_VALUE);
      response
          .getWriter()
          .write(
              "<!doctype html><html lang=\"es\"><meta charset=\"utf-8\"><title>LIV</title>"
                  + "<p>Hubo demasiados intentos de iniciar sesión desde esta red. Espera "
                  + seconds
                  + " segundos y vuelve a abrir LIV.</p></html>");
      return;
    }
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"status\":429,\"title\":\"Demasiadas solicitudes. Intenta de nuevo en un"
                + " momento.\"}");
  }
}
