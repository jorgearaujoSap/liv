package co.com.sapenlinea.liv.bff.session;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.session.Session;

/**
 * How long a session may last, whatever its activity (001-10, plan, «Sesión y tiempos»): 8 hours
 * from the moment LIV accepted it. The inactivity limit is Spring Session's own expiry (15
 * minutes). The moment is kept in the session itself, not taken from its creation: the session
 * exists from {@code /bff/login}, before the person typed the credentials. A session without it is
 * treated as expired, so nothing outlives the limit by a missing attribute.
 */
public final class SessionLifetime {

  static final String STARTED_AT_ATTRIBUTE = "LIV_STARTED_AT";

  private final Duration absolute;
  private final Clock clock;

  public SessionLifetime(Duration absolute, Clock clock) {
    if (absolute.isZero() || absolute.isNegative()) {
      throw new IllegalArgumentException("La duración máxima de la sesión debe ser positiva");
    }
    this.absolute = absolute;
    this.clock = clock;
  }

  /** The person was let in now: the maximum duration starts counting. */
  public void markStarted(HttpSession session) {
    session.setAttribute(STARTED_AT_ATTRIBUTE, clock.millis());
  }

  /** Seconds left until the maximum duration; 0 if it is over or the session has no start. */
  long secondsLeftFrom(Object startedAtAttribute) {
    if (!(startedAtAttribute instanceof Long startedAt)) {
      return 0;
    }
    Instant end = Instant.ofEpochMilli(startedAt).plus(absolute);
    return ceilSeconds(Duration.between(clock.instant(), end));
  }

  public long absoluteSecondsLeft(HttpSession session) {
    return secondsLeftFrom(session.getAttribute(STARTED_AT_ATTRIBUTE));
  }

  public boolean absoluteExpired(HttpSession session) {
    return absoluteSecondsLeft(session) <= 0;
  }

  /**
   * Whole seconds, rounded up, never negative: a second still running is not over. Rounding down
   * would call a session expired up to a second early.
   */
  public static long ceilSeconds(Duration left) {
    return left.isNegative() ? 0 : (left.toMillis() + 999) / 1000;
  }

  /** The same for a session read from the repository, without going through the request. */
  public long absoluteSecondsLeft(Session session) {
    return secondsLeftFrom(session.getAttribute(STARTED_AT_ATTRIBUTE));
  }
}
