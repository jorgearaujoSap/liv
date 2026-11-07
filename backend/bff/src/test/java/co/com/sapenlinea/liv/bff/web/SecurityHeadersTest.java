package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Every answer of the BFF carries the security headers of doc. 02 §8 (T19), also the ones answered
 * before the security filters ({@code /bff/session}), the errors and what comes from the API.
 */
@Tag("001-24")
class SecurityHeadersTest extends BffFlowTest {

  @Test
  @DisplayName(
      "001-24 · Toda respuesta lleva HSTS, nosniff, Referrer-Policy, CSP con frame-ancestors 'none' y X-Frame-Options")
  void everyAnswerCarriesTheHeaders() {
    Browser person = browser();
    person.signIn("/");

    List<Reply> replies =
        List.of(
            person.get(bffBase() + "/api/v1/me"),
            person.get(bffBase() + "/bff/session"),
            browser().get(bffBase() + "/api/v1/me"),
            browser().get(bffBase() + "/internal/v1/x"),
            browser().get(bffBase() + "/bff/login"),
            browser().get(bffBase() + "/bff/login", Map.of("Sec-Purpose", "prefetch;prerender")),
            person.post(bffBase() + "/bff/logout", ""));

    Reply preload = replies.get(5);
    assertThat(preload.headerValues("Content-Security-Policy"))
        .as("una sola CSP: con dos, el navegador aplicaría las dos y bloquearía el script")
        .hasSize(1);
    for (String directive : SecurityHeadersFilter.CSP.split("; ")) {
      if (!directive.startsWith("default-src")) {
        assertThat(preload.header("Content-Security-Policy")).contains(directive);
      }
    }

    for (Reply reply : replies) {
      assertThat(reply.header("Strict-Transport-Security"))
          .as("HSTS")
          .isEqualTo("max-age=31536000; includeSubDomains");
      assertThat(reply.header("X-Content-Type-Options")).isEqualTo("nosniff");
      assertThat(reply.header("Referrer-Policy")).isEqualTo("no-referrer");
      assertThat(reply.header("Content-Security-Policy"))
          .contains("default-src 'none'")
          .contains("frame-ancestors 'none'");
      assertThat(reply.header("X-Frame-Options")).isEqualTo("DENY");
    }
  }
}
