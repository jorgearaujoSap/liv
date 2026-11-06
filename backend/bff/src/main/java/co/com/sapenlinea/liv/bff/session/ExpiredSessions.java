package co.com.sapenlinea.liv.bff.session;

import co.com.sapenlinea.liv.bff.session.StoredSessions.Candidate;
import co.com.sapenlinea.liv.bff.session.StoredSessions.Row;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends the sessions that are over, inactivity or maximum duration, whether or not the person comes
 * back (001-10, 001-18; T19). Each one ends as at the sign out: LIV is told (EndSession with {@code
 * IDLE} or {@code ABSOLUTE}), the refresh token is revoked in the provider, and only then the
 * session is deleted. If LIV or the provider fail, the session stays and the next sweep tries
 * again; after {@code liv.bff.session.sweep-give-up-after} it is deleted anyway, with an error in
 * the log. Spring Session would delete them without telling anyone, both its own clean up (turned
 * off, {@code spring.session.jdbc.cleanup-cron}) and when a session is read once expired; that is
 * why the repository asks here first ({@link ClosingSessionRepository}).
 *
 * <p>Only the sweep calls LIV and the provider, and {@code /bff/login} before a new sign in: a
 * request that finds its session expired leaves it here and gets no session, so no request waits on
 * them holding a connection. Each session is ended locked ({@link StoredSessions}), so two replicas
 * never end the same one at once. The sign out and the maximum duration checked on a request
 * ({@code SessionCloser.closeAndDelete}) do not take that lock: if one of them meets the sweep, LIV
 * hears the end twice, which it accepts (EndSession is idempotent). The maximum duration counts
 * from the moment LIV let the person in, never before the session was created.
 */
public final class ExpiredSessions {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiredSessions.class);

  /** Sessions read per query of the sweep; it goes on with the next ones while there are more. */
  static final int BATCH = 200;

  private final StoredSessions stored;
  private final OidcSessions oidc;
  private final SessionCloser closer;
  private final Duration absolute;
  private final Duration budget;
  private final Duration giveUpAfter;
  private final Clock clock;

  /**
   * @param budget how long one sweep may last; less than its lock, so that no other replica starts
   *     on the same sessions while this one is still at it
   * @param giveUpAfter how long a session whose end LIV or the provider keep failing is kept to try
   *     again; then it is deleted, with an error in the log
   */
  public ExpiredSessions(
      StoredSessions stored,
      OidcSessions oidc,
      SessionCloser closer,
      Duration absolute,
      Duration budget,
      Duration giveUpAfter,
      Clock clock) {
    this.stored = stored;
    this.oidc = oidc;
    this.closer = closer;
    this.absolute = absolute;
    this.budget = budget;
    this.giveUpAfter = giveUpAfter;
    this.clock = clock;
  }

  /**
   * Ends every session that is over and deletes the expired sign ins that were never finished,
   * until there are no more or the budget runs out (the next sweep goes on). A failure with one
   * session is logged and the sweep goes on with the next one.
   *
   * @return how many sessions it ended
   */
  public int sweep() {
    long now = clock.millis();
    long deadline = now + budget.toMillis();
    deleteUnfinishedSignIns(now, deadline);
    int ended = 0;
    long afterCreation = Long.MIN_VALUE;
    String afterId = "";
    List<Candidate> batch;
    do {
      batch = stored.candidates(now, now - absolute.toMillis(), afterCreation, afterId, BATCH);
      for (Candidate candidate : batch) {
        if (clock.millis() >= deadline) {
          LOG.warn("El barrido de sesiones vencidas agotó su tiempo; sigue en el próximo");
          return ended;
        }
        if (end(candidate.sessionId(), clock.millis()) == Outcome.ENDED) {
          ended++;
        }
        afterCreation = candidate.creationTime();
        afterId = candidate.sessionId();
      }
    } while (batch.size() == BATCH);
    return ended;
  }

  /**
   * Before a session is read: whether it is already expired by inactivity. Then it must not be read
   * (Spring Session would delete it without telling anyone) and the request gets no session; the
   * sweep ends it, without the request waiting on LIV or the provider.
   */
  public boolean isExpired(String sessionId) {
    return stored.isExpired(sessionId, clock.millis());
  }

  /**
   * A new sign in from a browser whose session expired: that session is ended now, before the
   * person signs in again in the same session of the provider. Otherwise the sweep would find the
   * new one with the same {@code sid} and delete the old one without telling LIV. A sign in is a
   * navigation of the person, one at a time, so it can wait on LIV and the provider.
   */
  public void endBeforeANewSignIn(String sessionId) {
    if (isExpired(sessionId)) {
      end(sessionId, clock.millis());
    }
  }

  private void deleteUnfinishedSignIns(long now, long deadline) {
    try {
      int deleted;
      do {
        deleted = stored.deleteUnfinishedSignIns(now, BATCH);
      } while (deleted == BATCH && clock.millis() < deadline);
    } catch (RuntimeException e) {
      LOG.warn(
          "No se pudieron borrar los inicios de sesión vencidos: {}", e.getClass().getSimpleName());
    }
  }

  private Outcome end(String sessionId, long now) {
    try {
      Outcome outcome = stored.inTransaction(() -> endLocked(sessionId, now));
      return outcome == null ? Outcome.KEPT : outcome;
    } catch (RuntimeException e) {
      LOG.warn("No se pudo terminar una sesión vencida: {}", e.getClass().getSimpleName());
      return Outcome.KEPT;
    }
  }

  /** Inside a transaction that holds the session locked until it ends. */
  private Outcome endLocked(String sessionId, long now) {
    Optional<Row> locked = stored.lock(sessionId);
    if (locked.isEmpty()) {
      // Already gone, or someone else is ending it right now.
      return Outcome.ELSEWHERE;
    }
    Row row = locked.get();
    Map<String, byte[]> attributes = stored.attributesOf(row.primaryId());
    SessionAuthentication person;
    Object startedAt;
    try {
      person = stored.personOf(attributes);
      startedAt = stored.read(attributes, SessionLifetime.STARTED_AT_ATTRIBUTE);
    } catch (RuntimeException unreadable) {
      if (row.idleEnd() >= now) {
        return Outcome.KEPT;
      }
      LOG.error(
          "Una sesión vencida no se puede leer ({}): se borra sin avisar a LIV ni revocarla"
              + " (sesión {})",
          unreadable.getClass().getSimpleName(),
          StoredSessions.fingerprint(sessionId));
      return delete(row, sessionId);
    }
    // Without the moment LIV let the person in, a session of a person is past the limit, as for
    // SessionLifetime; without a person, it is a sign in still going on.
    long absoluteEnd =
        startedAt instanceof Long started
            ? started + absolute.toMillis()
            : person != null ? 0 : row.idleEnd();
    long endedAt = Math.min(row.idleEnd(), absoluteEnd);
    if (now < endedAt) {
      return Outcome.KEPT;
    }
    if (person == null) {
      return delete(row, sessionId);
    }
    if (sharesTheProviderSession(row, person, now)) {
      // The person signed in again in the same session of the provider: ending this one there,
      // or in LIV, would end the one in use. Only the session of the BFF goes. Its end is not in
      // LIV (/bff/login could not end it before the new sign in): the log says so, to match it.
      LOG.error(
          "Una sesión vencida comparte el sid {} con otra abierta: se borra sin avisar a LIV",
          StoredSessions.fingerprint(person.getPrincipal().sessionId()));
      return delete(row, sessionId);
    }
    EndReason reason = absoluteEnd <= row.idleEnd() ? EndReason.ABSOLUTE : EndReason.IDLE;
    if (closer.closeStored(person, stored.tokensOf(attributes), reason).done()) {
      return delete(row, sessionId);
    }
    if (now - endedAt >= giveUpAfter.toMillis()) {
      LOG.error(
          "LIV o el proveedor no aceptan el fin de una sesión vencida hace más de {}: se borra"
              + " (sid {})",
          giveUpAfter,
          StoredSessions.fingerprint(person.getPrincipal().sessionId()));
      return delete(row, sessionId);
    }
    return Outcome.KEPT;
  }

  /**
   * Another session of the BFF, still open, with the same session of the provider ({@code sid}).
   */
  private boolean sharesTheProviderSession(Row row, SessionAuthentication person, long now) {
    String sid = person.getPrincipal().sessionId();
    for (String other : stored.otherOpenSessions(row, now)) {
      try {
        SessionAuthentication theOther = stored.personOf(stored.attributesOf(other));
        if (theOther != null && theOther.getPrincipal().sessionId().equals(sid)) {
          return true;
        }
      } catch (RuntimeException unreadable) {
        // A session that cannot be read is not one in use.
      }
    }
    return false;
  }

  private Outcome delete(Row row, String sessionId) {
    if (!stored.delete(row.primaryId())) {
      return Outcome.ELSEWHERE;
    }
    oidc.forget(sessionId);
    return Outcome.ENDED;
  }

  private enum Outcome {
    /** Ended and deleted here. */
    ENDED,
    /** Not over yet, or its end failed and is tried again later. */
    KEPT,
    /** Gone, or being ended by someone else. */
    ELSEWHERE
  }
}
