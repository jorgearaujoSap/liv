package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.web.RateLimitProperties.Limit;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The buckets themselves, without the server (T19). */
class RateLimiterTest {

  private static final Limit TWO_PER_HOUR = new Limit(2, Duration.ofHours(1));

  @Test
  @DisplayName("T19 · Un balde que se recargó hasta su capacidad se olvida: es igual a uno nuevo")
  void forgetsABucketRefilledToItsCapacity() {
    RateLimiter limiter = new RateLimiter();
    // A bucket refilled to its capacity is full, whatever the kind of key.
    limiter.takeForAnAddress("sign-in:1", new Limit(20, Duration.ofMillis(1)));
    limiter.takeForAnAddress("ip:1", new Limit(600, Duration.ofMillis(1)));
    pause(50);

    limiter.forgetTheFull();

    assertThat(limiter.addressKeys()).isZero();
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  @DisplayName("T19 · Cada llave tiene su balde; agotado, dice cuántos segundos esperar")
  void eachKeyHasItsBucket() {
    RateLimiter limiter = new RateLimiter();

    assertThat(limiter.takeForAPerson("a", TWO_PER_HOUR)).isZero();
    assertThat(limiter.takeForAPerson("a", TWO_PER_HOUR)).isZero();
    assertThat(limiter.takeForAPerson("a", TWO_PER_HOUR)).isBetween(1L, 1800L);
    assertThat(limiter.takeForAPerson("b", TWO_PER_HOUR)).as("otra persona").isZero();
  }

  @Test
  @DisplayName("T19 · Se olvidan los baldes llenos, no los que tienen peticiones pendientes")
  void forgetsOnlyTheFullBuckets() {
    RateLimiter limiter = new RateLimiter();
    limiter.takeForAPerson("usada", TWO_PER_HOUR);
    limiter.takeForAnAddress("ip:1", TWO_PER_HOUR);

    limiter.takeForAPerson("agotada", TWO_PER_HOUR);
    limiter.takeForAPerson("agotada", TWO_PER_HOUR);
    limiter.takeForAnAddress("ip:sin-usar", new Limit(1, Duration.ofHours(1)));
    limiter.takeForAnAddress("ip:sin-usar", new Limit(1, Duration.ofHours(1)));

    limiter.forgetTheFull();

    assertThat(limiter.peopleKeys()).as("las dos tienen peticiones pendientes").isEqualTo(2);
    assertThat(limiter.addressKeys()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "T19 · Pasado el tope de direcciones se olvidan algunas, nunca los baldes de las personas")
  void theCapNeverTouchesThePeople() {
    RateLimiter limiter = new RateLimiter();
    limiter.takeForAPerson("persona", TWO_PER_HOUR);
    limiter.takeForAPerson("persona", TWO_PER_HOUR);

    for (int i = 0; i <= RateLimiter.MAX_KEYS; i++) {
      limiter.takeForAnAddress("ip:" + i, TWO_PER_HOUR);
    }

    assertThat(limiter.addressKeys()).isLessThanOrEqualTo(RateLimiter.MAX_KEYS);
    assertThat(limiter.takeForAPerson("persona", TWO_PER_HOUR))
        .as("su balde sigue agotado")
        .isPositive();
  }

  @Test
  @DisplayName("T19 · El límite se reparte entre las réplicas, y cada una deja pasar al menos 1")
  void theLimitIsSharedByTheReplicas() {
    Limit limit = new Limit(600, Duration.ofMinutes(1));
    RateLimitProperties three =
        new RateLimitProperties(limit, limit, new Limit(20, limit.per()), 3);

    assertThat(three.ofOneReplica(limit).requests()).isEqualTo(200);
    assertThat(three.ofOneReplica(new Limit(20, limit.per())).requests()).isEqualTo(7);
    assertThat(three.ofOneReplica(new Limit(1, limit.per())).requests()).isEqualTo(1);
  }

  @Test
  @DisplayName("T19 · Una dirección IPv6 cuenta por su red /64; una IPv4, tal cual")
  void anIpv6AddressCountsByItsNetwork() {
    assertThat(RateLimitFilter.networkOf("2001:db8:1:2:aaaa::1"))
        .isEqualTo(RateLimitFilter.networkOf("2001:db8:1:2:bbbb::9"));
    assertThat(RateLimitFilter.networkOf("2001:db8:1:3::1"))
        .isNotEqualTo(RateLimitFilter.networkOf("2001:db8:1:2::1"));
    assertThat(RateLimitFilter.networkOf("10.0.0.7")).isEqualTo("10.0.0.7");
  }
}
