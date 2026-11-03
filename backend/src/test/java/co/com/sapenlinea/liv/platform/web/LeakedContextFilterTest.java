package co.com.sapenlinea.liv.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * A request never runs with an administration context that earlier work left open on its thread, on
 * any route, and the thread is usable again for the next request.
 */
@DisplayName("Contexto de administración huérfano (T12)")
class LeakedContextFilterTest {

  private final LeakedContextFilter filter = new LeakedContextFilter();

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Una petición en un hilo con un contexto huérfano falla y lo limpia")
  void rejectsAndCleansALeakedContext() {
    AdministrationContext.open(UUID.randomUUID());
    AtomicBoolean served = new AtomicBoolean();

    assertThatThrownBy(() ->
            filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> served.set(true)))
        .isInstanceOf(IllegalStateException.class);

    assertThat(served).isFalse();
    assertThat(AdministrationContext.current()).isEmpty();
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Una petición en un hilo limpio se atiende")
  void servesACleanThread() throws Exception {
    AtomicBoolean served = new AtomicBoolean();

    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (request, response) -> served.set(true));

    assertThat(served).isTrue();
  }
}
