package co.com.sapenlinea.liv.bff.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security loads the anti-CSRF token only when something asks for it, so the cookie would
 * only reach the browser after a request that needs it. The frontend has to have it before its
 * first change of data: reading the token on every response writes the cookie {@code XSRF-TOKEN}.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (token != null) {
      token.getToken();
    }
    chain.doFilter(request, response);
  }
}
