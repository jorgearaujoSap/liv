package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * A slow or stuck API does not hold the threads of the BFF forever: the gateway gives up after the
 * read timeout ({@code spring.http.clients.read-timeout}) and the person gets an error, not a wait.
 */
@TestPropertySource(properties = "spring.http.clients.read-timeout=500ms")
class SlowApiForwardingTest extends BffFlowTest {

  @Test
  @Tag("001-14")
  @DisplayName(
      "001-14 · Si la API tarda más que el tiempo de espera, /api responde un error y no queda esperando")
  void aSlowApiTimesOut() {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();
    BACKEND.apiAnswersAfter(3000);

    long start = System.currentTimeMillis();
    Reply reply = browser.get(bffBase() + "/api/v1/me");
    long elapsed = System.currentTimeMillis() - start;

    assertThat(reply.status()).isGreaterThanOrEqualTo(500);
    assertThat(elapsed).as("ms esperando a la API").isLessThan(2500);
  }
}
