package co.com.sapenlinea.liv.platform.web;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * No request runs with an administration context left open on its thread by a listener, a job or an
 * earlier request: it could show another administration's data (001-17). The request fails closed
 * (500), the context is discarded so the thread serves the next request, and the leak is logged. It
 * runs on every route, before security.
 */
final class LeakedContextFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(LeakedContextFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<UUID> leaked = AdministrationContext.discardLeaked();
    if (leaked.isPresent()) {
      LOG.error("Administration context {} was left open on this thread", leaked.get());
      throw new IllegalStateException("An administration context was left open on this thread");
    }
    chain.doFilter(request, response);
  }
}
