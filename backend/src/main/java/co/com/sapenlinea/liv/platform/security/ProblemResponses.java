package co.com.sapenlinea.liv.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Hands the rejections of the security filters to Spring MVC, so they get the same Problem Details
 * as an exception thrown in a controller (platform.web.SecurityProblemHandler).
 */
final class ProblemResponses implements AuthenticationEntryPoint, AccessDeniedHandler {

  /** The rejection happens in a filter, before any controller method handles the request. */
  private static final Object NO_HANDLER = null;

  private final HandlerExceptionResolver resolver;

  ProblemResponses(HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
    resolver.resolveException(request, response, NO_HANDLER, exception);
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) {
    resolver.resolveException(request, response, NO_HANDLER, exception);
  }
}
