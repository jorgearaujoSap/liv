package co.com.sapenlinea.liv.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

/**
 * A failure inside the filters (the user directory, the token decoder) never reaches the container:
 * it is answered like an exception of a controller (doc. 02 §8).
 */
@DisplayName("Errores de los filtros como Problem Details (T12)")
class ProblemFallbackFilterTest {

  private final AtomicReference<Exception> resolved = new AtomicReference<>();

  private final ProblemFallbackFilter filter =
      new ProblemFallbackFilter((request, response, handler, exception) -> {
        resolved.set(exception);
        return new ModelAndView();
      });

  @Test
  @DisplayName("Doc. 02 §8 · Una falla de un filtro se resuelve con el manejador de errores")
  void resolvesFilterFailures() throws Exception {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("base de datos caída");

    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (request, response) -> {
          throw failure;
        });

    assertThat(resolved.get()).isSameAs(failure);
  }

  @Test
  @DisplayName("Doc. 02 §8 · Una falla que el servlet envolvió se resuelve por su causa")
  void unwrapsServletExceptions() throws Exception {
    IllegalStateException cause = new IllegalStateException("falla");

    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (request, response) -> {
          throw new ServletException("envuelta", cause);
        });

    assertThat(resolved.get()).isSameAs(cause);
  }
}
