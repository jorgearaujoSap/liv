package co.com.sapenlinea.liv.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The correlation id travels from the browser to the logs and the audit (doc. 02 §9); the log
 * pattern prints it from the MDC ({@code logging.pattern.correlation}). It is accepted only as a
 * UUID in canonical form, so a client cannot write into the logs through it (plan, log injection);
 * any other value is replaced by a new one.
 */
final class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-Id";
  static final String MDC_KEY = "correlation_id";

  private static final Pattern CANONICAL_UUID =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String received = request.getHeader(HEADER);
    String id =
        received != null && CANONICAL_UUID.matcher(received).matches()
            ? received.toLowerCase(Locale.ROOT)
            : UUID.randomUUID().toString();
    response.setHeader(HEADER, id);
    MDC.put(MDC_KEY, id);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
