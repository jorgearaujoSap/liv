package co.com.sapenlinea.liv.bff.session;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * Ends the sessions that are over, inactivity or maximum duration, whether or not the person comes
 * back (001-10, 001-18; T19). Each one is ended as at the sign out: LIV is told (EndSession with
 * {@code IDLE} or {@code ABSOLUTE}), the refresh token is revoked in the provider and then the
 * session is deleted. Spring Session would delete them without telling anyone, both its own clean
 * up (turned off, {@code spring.session.jdbc.cleanup-cron}) and when a session is read once
 * expired; that is why the repository asks here first ({@link ClosingSessionRepository}).
 *
 * <p>The sessions are read from the table of Spring Session, {@code bff.spring_session}. Its {@code
 * expiry_time} is the end by inactivity; the maximum duration counts from the moment LIV let the
 * person in, which is never before the session was created, so a session created less than the
 * limit ago cannot be past it.
 */
public final class ExpiredSessions {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiredSessions.class);

  /** Sessions ended per query of the sweep; it goes on while there are more. */
  static final int BATCH = 200;

  private static final String PRINCIPAL =
      FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

  private final JdbcOperations jdbc;
  private final FindByIndexNameSessionRepository<? extends Session> sessions;
  private final SessionCloser closer;
  private final Duration absolute;
  private final Clock clock;
  private final String table;

  /**
   * @param sessions the repository of the sessions; only its search by person and its deletion are
   *     used, which do not ask here first
   * @param table the table of Spring Session, {@code spring.session.jdbc.table-name}
   */
  public ExpiredSessions(
      JdbcOperations jdbc,
      FindByIndexNameSessionRepository<? extends Session> sessions,
      SessionCloser closer,
      Duration absolute,
      Clock clock,
      String table) {
    this.jdbc = jdbc;
    this.sessions = sessions;
    this.closer = closer;
    this.absolute = absolute;
    this.clock = clock;
    this.table = table;
  }

  /**
   * Ends every session that is over and deletes the expired sign ins that were never finished. It
   * never throws for one session: a failure is logged and the sweep goes on with the next one.
   *
   * @return how many sessions of a person it ended
   */
  public int sweep() {
    long now = clock.millis();
    jdbc.update("DELETE FROM " + table + " WHERE principal_name IS NULL AND expiry_time < ?", now);
    int ended = 0;
    int skipped = 0;
    List<Candidate> batch;
    do {
      batch = candidates(now, skipped);
      for (Candidate candidate : batch) {
        if (end(candidate, now)) {
          ended++;
        } else {
          skipped++;
        }
      }
    } while (batch.size() == BATCH);
    return ended;
  }

  /**
   * Before a session is read: if it is already expired by inactivity, it is ended here, so that
   * reading it does not delete it silently.
   *
   * @return whether it ended it
   */
  public boolean endIfExpired(String sessionId) {
    long now = clock.millis();
    List<Candidate> found =
        jdbc.query(
            "SELECT session_id, principal_name FROM "
                + table
                + " WHERE session_id = ? AND principal_name IS NOT NULL AND expiry_time < ?",
            (row, i) -> new Candidate(row.getString(1), row.getString(2)),
            sessionId,
            now);
    return !found.isEmpty() && end(found.get(0), now);
  }

  /**
   * Sessions of a person expired by inactivity, or created longer ago than the maximum duration.
   * The ones that are not ended (still in use, or a failure) stay in the table, so the next query
   * starts after them.
   */
  private List<Candidate> candidates(long now, int skipped) {
    return jdbc.query(
        "SELECT session_id, principal_name FROM "
            + table
            + " WHERE principal_name IS NOT NULL AND (expiry_time < ? OR creation_time < ?)"
            + " ORDER BY creation_time, session_id LIMIT ? OFFSET ?",
        (row, i) -> new Candidate(row.getString(1), row.getString(2)),
        now,
        now - absolute.toMillis(),
        BATCH,
        skipped);
  }

  private boolean end(Candidate candidate, long now) {
    try {
      Map<String, ? extends Session> ofThePerson =
          sessions.findByIndexNameAndIndexValue(PRINCIPAL, candidate.principal());
      Session session = ofThePerson.get(candidate.sessionId());
      if (session == null) {
        return false;
      }
      EndReason reason = reasonIfOver(session, now);
      if (reason == null) {
        return false;
      }
      closer.close(session, reason);
      sessions.deleteById(session.getId());
      return true;
    } catch (RuntimeException e) {
      LOG.warn("No se pudo terminar una sesión vencida: {}", e.getClass().getSimpleName());
      return false;
    }
  }

  /**
   * {@code IDLE} or {@code ABSOLUTE}, whichever limit came first; {@code null} if the session is
   * not over. A session without the moment LIV let the person in is past the maximum duration, as
   * for {@link SessionLifetime}, but only once it is also expired: it is a sign in still going on.
   */
  private EndReason reasonIfOver(Session session, long now) {
    long idleEnd =
        session.getLastAccessedTime().plus(session.getMaxInactiveInterval()).toEpochMilli();
    long absoluteEnd =
        session.getAttribute(SessionLifetime.STARTED_AT_ATTRIBUTE) instanceof Long startedAt
            ? startedAt + absolute.toMillis()
            : idleEnd;
    if (now < Math.min(idleEnd, absoluteEnd)) {
      return null;
    }
    return absoluteEnd <= idleEnd ? EndReason.ABSOLUTE : EndReason.IDLE;
  }

  private record Candidate(String sessionId, String principal) {}
}
