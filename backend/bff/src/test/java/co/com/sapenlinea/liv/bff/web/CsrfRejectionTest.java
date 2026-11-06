package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * The answer with code 001-12 is only for the anti-CSRF refusal: the frontend takes any other 403 of
 * {@code /api/**} as "no permission" (001-16), so a future rule of the BFF that denies something must
 * not look like a missing token.
 */
@Tag("001-12")
class CsrfRejectionTest {

  @Test
  @DisplayName("001-12 · Sin el token anti-CSRF, 403 en problem+json UTF-8 con code 001-12")
  void aMissingTokenGetsTheCode() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    CsrfRejection.handler()
        .handle(new MockHttpServletRequest(), response, new MissingCsrfTokenException(null));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"status\":403,\"code\":\"" + CsrfRejection.CODE + "\"}");
  }

  @Test
  @DisplayName("001-12 · Con un token anti-CSRF que no es el de la cookie, el mismo rechazo")
  void aWrongTokenGetsTheCode() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    CsrfRejection.handler()
        .handle(
            new MockHttpServletRequest(),
            response,
            new InvalidCsrfTokenException(
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "esperado"), "otro"));

    assertThat(response.getContentAsString()).contains("\"code\":\"001-12\"");
  }

  @Test
  @DisplayName("001-12 · Una denegación que no es de CSRF da 403 sin el código 001-12")
  void anyOtherDenialHasNoCode() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    CsrfRejection.handler()
        .handle(new MockHttpServletRequest(), response, new AccessDeniedException("no"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).doesNotContain("001-12");
  }
}
