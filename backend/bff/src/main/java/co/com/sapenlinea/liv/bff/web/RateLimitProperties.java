package co.com.sapenlinea.liv.bff.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many requests the BFF lets through (doc. 02 §8; T19): per person, per address and, more
 * strictly, for the sign in. The values are in {@code application.yml}; they are not rules of the
 * tax, and they can change without touching the code.
 *
 * @param perUser the requests of a person with a session
 * @param perIp the requests from one address, with or without a session
 * @param signIn the sign ins from one address ({@code /bff/login} and the return from the provider)
 */
@ConfigurationProperties("liv.bff.rate-limits")
public record RateLimitProperties(Limit perUser, Limit perIp, Limit signIn) {

  public RateLimitProperties {
    if (perUser == null || perIp == null || signIn == null) {
      throw new IllegalArgumentException("Faltan liv.bff.rate-limits.per-user, per-ip o sign-in");
    }
  }

  /**
   * @param requests how many requests fit in {@code per}
   * @param per the time in which they fit; they come back little by little
   */
  public record Limit(int requests, Duration per) {

    public Limit {
      if (requests <= 0 || per == null || per.isZero() || per.isNegative()) {
        throw new IllegalArgumentException("Un límite de liv.bff.rate-limits no es válido");
      }
    }
  }
}
