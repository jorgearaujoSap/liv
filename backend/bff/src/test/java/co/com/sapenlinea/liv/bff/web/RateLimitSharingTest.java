package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * How the limits are shared (T19): a person with a session does not use the bucket of the address,
 * and each of the replicas lets through its share. Here there are 2 replicas, 10 requests per hour
 * per address (5 for this one) and 100 per person (50).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(
    properties = {
      "liv.bff.rate-limits.replicas=2",
      "liv.bff.rate-limits.per-ip.requests=10",
      "liv.bff.rate-limits.per-ip.per=1h",
      "liv.bff.rate-limits.per-user.requests=100",
      "liv.bff.rate-limits.per-user.per=1h"
    })
class RateLimitSharingTest extends BffFlowTest {

  @Test
  @DisplayName(
      "T19 · Una persona con sesión no gasta el balde de su dirección; cada réplica deja pasar su parte")
  void aPersonDoesNotUseTheBucketOfTheAddress() {
    Browser person = browser();
    person.signIn("/");
    for (int i = 0; i < 20; i++) {
      assertThat(person.get(bffBase() + "/api/v1/me").status()).isEqualTo(200);
    }

    Browser anyone = browser();
    // /bff/login of the sign in took 1 of the 5 of this replica.
    for (int i = 0; i < 4; i++) {
      assertThat(anyone.get(bffBase() + "/api/v1/me").status())
          .as("sin sesión %d", i)
          .isEqualTo(401);
    }
    assertThat(anyone.get(bffBase() + "/api/v1/me").status())
        .as("la mitad de 10, por haber 2 réplicas")
        .isEqualTo(429);
  }
}
