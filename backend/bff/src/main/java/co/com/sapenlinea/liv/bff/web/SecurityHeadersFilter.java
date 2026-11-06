package co.com.sapenlinea.liv.bff.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The security headers of doc. 02 §8 (T19) on every answer of the BFF, also the ones given before
 * the security filters ({@code /bff/session}), the errors and what the API answers through the BFF.
 * The BFF answers JSON and redirects, never a page: its policy lets nothing load and nothing frame
 * it. The policy of the pages of the frontend is the frontend's own.
 *
 * <ul>
 *   <li>{@code Strict-Transport-Security}: always, because TLS ends at the load balancer and the
 *       BFF sees plain HTTP;
 *   <li>{@code Content-Security-Policy} with {@code default-src 'none'} and {@code frame-ancestors
 *       'none'}, and {@code X-Frame-Options: DENY} for the browsers that do not read it;
 *   <li>{@code X-Content-Type-Options: nosniff} and {@code Referrer-Policy: no-referrer}.
 * </ul>
 */
final class SecurityHeadersFilter extends OncePerRequestFilter {

  static final String HSTS = "max-age=31536000; includeSubDomains";
  static final String CSP =
      "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Strict-Transport-Security", HSTS);
    response.setHeader("Content-Security-Policy", CSP);
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    chain.doFilter(request, response);
  }
}
