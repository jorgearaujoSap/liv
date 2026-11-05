package co.com.sapenlinea.liv.bff.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * The anti-CSRF token is read from the {@code X-XSRF-TOKEN} header only (001-12). The default
 * handler also takes a {@code _csrf} request parameter, which a plain HTML form can send without a
 * CORS preflight: a header can only be set by the scripts of the page, which is the proof the token
 * is there for.
 */
final class HeaderOnlyCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    return request.getHeader(csrfToken.getHeaderName());
  }
}
