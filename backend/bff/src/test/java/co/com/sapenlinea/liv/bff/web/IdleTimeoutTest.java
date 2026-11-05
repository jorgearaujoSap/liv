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
 * y tiempos»). Here the time is 6 seconds. A request of the person extends it; asking how much is
 * left ({@code /bff/session}) does not.
 */
@TestPropertySource(properties = "spring.session.timeout=6s")
class IdleTimeoutTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Tras la inactividad, la persona que intenta una acción recibe 401 y la API no se llama")
  void anIdleSessionHasExpired() {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();

    pause(6500);
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

    pause(2000);
    Reply first = browser.get(bffBase() + "/bff/session");
    pause(2000);
    Reply second = browser.get(bffBase() + "/bff/session");
    pause(2500);
    Reply third = browser.get(bffBase() + "/bff/session");

    assertThat(first.status()).isEqualTo(200);
    assertThat(second.status()).isEqualTo(200);
    assertThat(seconds(second.body(), "idleSecondsLeft"))
        .as("lo que queda baja, no se renueva")
        .isLessThan(seconds(first.body(), "idleSecondsLeft"));
    assertThat(third.status())
        .as("6,5 s después del último uso real, con 6 s de inactividad")
        .isEqualTo(401);
  }

  @Test
  @Tag("001-22")
  @DisplayName("001-22 · Una petición de la persona (a /api) sí extiende la sesión")
  void aRequestOfThePersonExtendsTheSession() {
    Browser browser = browser();
    browser.signIn("/");

    pause(4000);
    Reply used = browser.get(bffBase() + "/api/v1/me");
    pause(4000);
    Reply stillThere = browser.get(bffBase() + "/api/v1/me");

    assertThat(used.status()).isEqualTo(200);
    assertThat(stillThere.status())
        .as("8 s después del inicio, 4 s después del uso: sigue abierta")
        .isEqualTo(200);
  }

  @Test
  @Tag("001-22")
  @DisplayName(
      "001-22 · keepalive extiende la sesión y renueva el token contra el proveedor de verdad")
  void keepAliveExtendsTheSession() {
    Browser browser = browser();
    browser.signIn("/");
    String firstToken = BACKEND.startSessionCalls().get(0).header("x-user-token");

    pause(4000);
    Reply keepAlive = browser.post(bffBase() + "/bff/session/keepalive", "");
    pause(4000);
    Reply afterwards = browser.get(bffBase() + "/api/v1/me");

    assertThat(keepAlive.status()).as("la renovación llegó al proveedor").isEqualTo(200);
    assertThat(afterwards.status())
        .as("8 s después del inicio, 4 s después de keepalive")
        .isEqualTo(200);
    assertThat(BACKEND.apiCalls().get(0).header("authorization"))
        .as("el token es el renovado")
        .isNotEqualTo("Bearer " + firstToken);
  }
}
