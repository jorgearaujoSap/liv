package co.com.sapenlinea.liv.bff.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * On signing in, Spring Security deletes the anti-CSRF token (a new session must not keep the one
 * of the old). The frontend would have no token until some request of its own got one back. This
 * handler issues a new one with the same response that lets the person in, so the first change of
 * data after signing in already has it (001-12).
 */
final class CsrfTokenAfterLogin implements AuthenticationSuccessHandler {

  private final AuthenticationSuccessHandler delegate;
  private final CsrfTokenRepository tokens;

  CsrfTokenAfterLogin(AuthenticationSuccessHandler delegate, CsrfTokenRepository tokens) {
    this.delegate = delegate;
    this.tokens = tokens;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    CsrfToken token = tokens.generateToken(request);
    tokens.saveToken(token, request, response);
    delegate.onAuthenticationSuccess(request, response, authentication);
  }
}
