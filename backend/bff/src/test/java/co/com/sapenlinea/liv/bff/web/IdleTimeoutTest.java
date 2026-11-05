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
 * Fifteen minutes without activity end the session (001-10; the authority is the BFF, plan, «Sesión
 * y tiempos»). Here the time is 4 seconds. A request of the person extends it; asking how much is
 * left ({@code /bff/session}) does not.
 */
@TestPropertySource(properties = "spring.session.timeout=4s")
class IdleTimeoutTest extends BffFlowTest {

  private static void waitMillis(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Tras la inactividad, la persona que intenta una acción recibe 401 y la API no se llama")
  void anIdleSessionHasExpired() {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();

    waitMillis(4500);
    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(BACKEND.apiCalls()).isEmpty();
  }

  @Test
  @Tag("001-22")
  @DisplayName("001-22 · /bff/session no extiende la sesión: preguntar no la mantiene viva")
  void askingDoesNotExtendTheSession() {
    Browser browser = browser();
    browser.signIn("/");

    waitMillis(1500);
    Reply first = browser.get(bffBase() + "/bff/session");
    waitMillis(1500);
    Reply second = browser.get(bffBase() + "/bff/session");
    waitMillis(1500);
    Reply third = browser.get(bffBase() + "/bff/session");

    assertThat(first.status()).isEqualTo(200);
    assertThat(second.status()).isEqualTo(200);
    assertThat(SessionStatusTest.seconds(second.body(), "idleSecondsLeft"))
        .as("lo que queda baja, no se renueva")
        .isLessThan(SessionStatusTest.seconds(first.body(), "idleSecondsLeft"));
    assertThat(third.status())
        .as("4,5 s después del último uso real, con 4 s de inactividad")
        .isEqualTo(401);
  }

  @Test
  @Tag("001-22")
  @DisplayName("001-22 · Una petición de la persona (a /api) sí extiende la sesión")
  void aRequestOfThePersonExtendsTheSession() {
    Browser browser = browser();
    browser.signIn("/");

    waitMillis(2000);
    Reply used = browser.get(bffBase() + "/api/v1/me");
    waitMillis(2000);
    Reply stillThere = browser.get(bffBase() + "/api/v1/me");

    assertThat(used.status()).isEqualTo(200);
    assertThat(stillThere.status())
        .as("4 s después del inicio, 2 s después del uso: sigue abierta")
        .isEqualTo(200);
  }
}
