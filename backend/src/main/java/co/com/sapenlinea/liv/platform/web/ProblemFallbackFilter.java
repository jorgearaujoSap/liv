package co.com.sapenlinea.liv.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Catches what fails inside the filters (the user directory, the token decoder) or escapes Spring
 * MVC, and answers it like an exception of a controller: Problem Details without internal details,
 * logged while the correlation id is still in the MDC (doc. 02 §8). It runs right after {@link
 * CorrelationIdFilter}.
 */
final class ProblemFallbackFilter extends OncePerRequestFilter {

  /** The failure happened outside any controller method. */
  private static final Object NO_HANDLER = null;

  private final HandlerExceptionResolver resolver;

  ProblemFallbackFilter(HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } catch (ServletException | RuntimeException failure) {
      Exception cause = unwrap(failure);
      // A null result means no handler took it: the container answers it as before.
      if (response.isCommitted()
          || resolver.resolveException(request, response, NO_HANDLER, cause) == null) {
        throw failure;
      }
    }
  }

  /** The servlet wraps what a controller threw and MVC did not handle; resolve the original. */
  private static Exception unwrap(Exception failure) {
    return failure instanceof ServletException && failure.getCause() instanceof Exception cause
        ? cause
        : failure;
  }
}
