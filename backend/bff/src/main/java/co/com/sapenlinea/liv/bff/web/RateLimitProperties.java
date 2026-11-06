package co.com.sapenlinea.liv.bff.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How many requests the BFF lets through (doc. 02 §8; T19): per person, per address and, more
 * strictly, for the sign in. The values are in {@code application.yml}; they are not rules of the
 * tax, and they can change without touching the code.
 *
 * @param perUser the requests of a person with a session
 * @param perIp the requests from one address without a session
 * @param signIn the sign ins ({@code /bff/login}) from one address
 * @param replicas how many replicas of the BFF share the traffic: each one keeps its buckets in
 *     memory, so each lets through its share of every limit (plan, step 10)
 */
@ConfigurationProperties("liv.bff.rate-limits")
public record RateLimitProperties(Limit perUser, Limit perIp, Limit signIn, int replicas) {

  public RateLimitProperties {
    if (perUser == null || perIp == null || signIn == null) {
      throw new IllegalArgumentException("Faltan liv.bff.rate-limits.per-user, per-ip o sign-in");
    }
    if (replicas < 1) {
      throw new IllegalArgumentException("liv.bff.rate-limits.replicas debe ser al menos 1");
    }
  }

  /** The share of a limit of one replica: the limit divided by the replicas, at least 1. */
  Limit ofOneReplica(Limit limit) {
    return new Limit(Math.max(1, Math.ceilDiv(limit.requests(), replicas)), limit.per());
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
