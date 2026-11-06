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
      "liv.bff.rate-limits.per-ip.requests=40",
      "liv.bff.rate-limits.sign-in.requests=3"
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
    assertThat(Long.parseLong(limited.header("Retry-After"))).isBetween(1L, 60L);
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

    assertThat(anyone.get(bffBase() + "/bff/login").status()).isEqualTo(429);
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
