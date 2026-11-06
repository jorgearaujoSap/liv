package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The BFF limits how many requests a person and an address may make, and more strictly the sign in
 * (doc. 02 §8; T19). Past the limit it answers 429 with {@code Retry-After}, and the request goes
 * no further. Here the limits are a few requests per minute, and every test starts with a new
 * context, so with full buckets.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(
    properties = {
      "liv.bff.rate-limits.per-user.requests=3",
      "liv.bff.rate-limits.per-user.per=1h",
      "liv.bff.rate-limits.per-ip.requests=40",
      "liv.bff.rate-limits.per-ip.per=1h",
      "liv.bff.rate-limits.sign-in.requests=3",
      "liv.bff.rate-limits.sign-in.per=1h"
    })
class RateLimitTest extends BffFlowTest {

  @Test
  @DisplayName(
      "T19 · Pasado el límite por persona, /api responde 429 con Retry-After y la petición no llega a la API")
  void aPersonPastTheLimitGets429() {
    Browser person = browser();
    person.signIn("/");
    BACKEND.reset();

    for (int i = 0; i < 3; i++) {
      assertThat(person.get(bffBase() + "/api/v1/me").status()).isEqualTo(200);
    }
    Reply limited = person.get(bffBase() + "/api/v1/me");

    assertThat(limited.status()).isEqualTo(429);
    assertThat(Long.parseLong(limited.header("Retry-After"))).isBetween(1L, 3600L);
    assertThat(limited.header("Cache-Control")).contains("no-store");
    assertThat(BACKEND.apiCalls()).hasSize(3);
  }

  @Test
  @DisplayName("T19 · El inicio de sesión tiene su propio límite, más estricto, por dirección")
  void theSignInHasItsOwnLimit() {
    Browser anyone = browser();

    for (int i = 0; i < 3; i++) {
      assertThat(anyone.get(bffBase() + "/bff/login").status()).isEqualTo(302);
    }

    Reply limited = anyone.get(bffBase() + "/bff/login");
    assertThat(limited.status()).isEqualTo(429);
    assertThat(limited.header("Content-Type"))
        .as("la persona llegó navegando")
        .startsWith("text/html");
    assertThat(limited.body()).contains("Espera");
    assertThat(anyone.get(bffBase() + "/bff/%6Cogin").status())
        .as("escrita de otra forma, es la misma ruta")
        .isEqualTo(429);
  }

  @Test
  @DisplayName("T19 · La vuelta del proveedor no cuenta para el límite del inicio de sesión")
  void theReturnFromTheProviderDoesNotCount() {
    for (int i = 0; i < 3; i++) {
      Browser person = browser();
      person.signIn("/");
      assertThat(person.get(bffBase() + "/api/v1/me").status()).as("inicio %d", i).isEqualTo(200);
    }
  }

  @Test
  @DisplayName(
      "T19 · Salir nunca tiene límite: la sesión de un equipo compartido siempre se cierra")
  void signingOutIsNeverLimited() {
    Browser person = browser();
    person.signIn("/");
    for (int i = 0; i < 3; i++) {
      person.get(bffBase() + "/api/v1/me");
    }
    assertThat(person.get(bffBase() + "/api/v1/me").status()).isEqualTo(429);

    assertThat(person.post(bffBase() + "/bff/logout", "").status()).isEqualTo(200);
    assertThat(person.get(bffBase() + "/bff/session").status()).isEqualTo(401);
  }

  @Test
  @DisplayName(
      "T19 · /bff/session, que se responde antes de la seguridad, también tiene límite por dirección")
  void theSessionStatusIsLimitedByAddress() {
    Browser anyone = browser();

    for (int i = 0; i < 40; i++) {
      anyone.get(bffBase() + "/bff/session");
    }

    assertThat(anyone.get(bffBase() + "/bff/session").status()).isEqualTo(429);
  }

  @Test
  @DisplayName("T19 · El límite por dirección vale también sin sesión")
  void anAddressPastTheLimitGets429() {
    Browser anyone = browser();

    for (int i = 0; i < 40; i++) {
      anyone.get(bffBase() + "/api/v1/me");
    }

    assertThat(anyone.get(bffBase() + "/api/v1/me").status()).isEqualTo(429);
  }

  @Test
  @DisplayName("T19 · El cierre por canal trasero no cuenta para el límite por dirección")
  void theBackChannelIsNotLimitedByAddress() {
    Browser provider = browser();

    for (int i = 0; i < 45; i++) {
      assertThat(
              provider
                  .postWithoutCsrfToken(bffBase() + "/bff/backchannel-logout", "logout_token=x")
                  .status())
          .isEqualTo(400);
    }
  }
}
