package co.com.sapenlinea.liv.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

@DisplayName("Respuestas de los rechazos de seguridad (T12)")
class ProblemResponsesTest {

  /** A resolver that cannot write the response. */
  private final ProblemResponses problems =
      new ProblemResponses((request, response, handler, exception) -> null);

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Si el cuerpo no se puede escribir, el rechazo sigue siendo 401")
  void unauthenticatedFallsBackTo401() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    problems.commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Si el cuerpo no se puede escribir, el rechazo sigue siendo 403")
  void forbiddenFallsBackTo403() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    problems.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

    assertThat(response.getStatus()).isEqualTo(403);
  }
}
