package co.com.sapenlinea.liv.bff.session;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ends the sessions that are over, inactivity or maximum duration, whether or not the person comes
 * back (001-10, 001-18; T19). Each one ends as at the sign out: LIV is told (EndSession with {@code
 * IDLE} or {@code ABSOLUTE}), the refresh token is revoked in the provider, and only then the
 * session is deleted. If LIV or the provider fail, the session stays and the next sweep tries
 * again; after {@link #GIVE_UP_AFTER} it is deleted anyway, with an error in the log. Spring
 * Session would delete them without telling anyone, both its own clean up (turned off, {@code
 * spring.session.jdbc.cleanup-cron}) and when a session is read once expired; that is why the
 * repository asks here first ({@link ClosingSessionRepository}).
 *
 * <p>The sessions are read straight from the tables of Spring Session, each one locked while it is
 * ended ({@code FOR UPDATE SKIP LOCKED}): neither two replicas nor two requests end the same
 * session at once, and one that cannot be deserialized (a class changed in a deployment) is still
 * deleted. Its {@code expiry_time} is the end by inactivity; the maximum duration counts from the
 * moment LIV let the person in, never before the session was created.
 */
public final class ExpiredSessions {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiredSessions.class);

  /** Sessions read per query of the sweep; it goes on with the next ones while there are more. */
  static final int BATCH = 200;

  /** How long a session whose end LIV or the provider keep failing is kept to try again. */
  static final Duration GIVE_UP_AFTER = Duration.ofDays(1);

  private static final String SECURITY_CONTEXT =
      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

  private final JdbcOperations jdbc;
  private final TransactionOperations transaction;
  private final SessionCloser closer;
  private final Duration absolute;
  private final Duration budget;
  private final Clock clock;
  private final String table;
  private final DeserializingConverter deserializer =
      new DeserializingConverter(ExpiredSessions.class.getClassLoader());

  /**
   * @param budget how long one sweep may last; less than its lock, so that no other replica starts
   *     on the same sessions while this one is still at it
   * @param table the table of Spring Session, {@code spring.session.jdbc.table-name}; its
   *     attributes are in the one with the suffix {@code _ATTRIBUTES}
   */
  public ExpiredSessions(
      DataSource dataSource,
      SessionCloser closer,
      Duration absolute,
      Duration budget,
      Clock clock,
      String table) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    this.closer = closer;
    this.absolute = absolute;
    this.budget = budget;
    this.clock = clock;
    this.table = table;
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
      // By key, not by position: the sessions ended leave the table, the others stay behind.
      batch = candidates(now, afterCreation, afterId);
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
   * Before a session is read: if it is already expired by inactivity, it is ended here (or left to
   * the sweep, if this fails or someone else is ending it), so that reading it does not delete it
   * silently.
   *
   * @return whether it is expired: then it must not be read
   */
  public boolean endIfExpired(String sessionId) {
    long now = clock.millis();
    List<Integer> expired =
        jdbc.query(
            "SELECT 1 FROM "
                + table
                + " WHERE session_id = ? AND principal_name IS NOT NULL AND expiry_time < ?",
            (row, i) -> 1,
            sessionId,
            now);
    if (expired.isEmpty()) {
      return false;
    }
    end(sessionId, now);
    return true;
  }

  private void deleteUnfinishedSignIns(long now, long deadline) {
    try {
      int deleted;
      do {
        deleted =
            jdbc.update(
                "DELETE FROM "
                    + table
                    + " WHERE primary_id IN (SELECT primary_id FROM "
                    + table
                    + " WHERE principal_name IS NULL AND expiry_time < ? LIMIT ?)",
                now,
                BATCH);
      } while (deleted == BATCH && clock.millis() < deadline);
    } catch (RuntimeException e) {
      LOG.warn(
          "No se pudieron borrar los inicios de sesión vencidos: {}", e.getClass().getSimpleName());
    }
  }

  /**
   * Sessions of a person expired by inactivity, or created longer ago than the maximum duration,
   * after the given one in the order of creation.
   */
  private List<Candidate> candidates(long now, long afterCreation, String afterId) {
    return jdbc.query(
        "SELECT session_id, creation_time FROM "
            + table
            + " WHERE principal_name IS NOT NULL AND (expiry_time < ? OR creation_time < ?)"
            + " AND (creation_time, session_id) > (?, ?)"
            + " ORDER BY creation_time, session_id LIMIT ?",
        (row, i) -> new Candidate(row.getString(1), row.getLong(2)),
        now,
        now - absolute.toMillis(),
        afterCreation,
        afterId,
        BATCH);
  }

  private Outcome end(String sessionId, long now) {
    try {
      Outcome outcome = transaction.execute(status -> endLocked(sessionId, now));
      return outcome == null ? Outcome.KEPT : outcome;
    } catch (RuntimeException e) {
      LOG.warn("No se pudo terminar una sesión vencida: {}", e.getClass().getSimpleName());
      return Outcome.KEPT;
    }
  }

  /** Inside a transaction that holds the session locked until it ends. */
  private Outcome endLocked(String sessionId, long now) {
    List<Row> rows =
        jdbc.query(
            "SELECT primary_id, principal_name, expiry_time FROM "
                + table
                + " WHERE session_id = ? FOR UPDATE SKIP LOCKED",
            (row, i) -> new Row(row.getString(1), row.getString(2), row.getLong(3)),
            sessionId);
    if (rows.isEmpty()) {
      // Already gone, or someone else is ending it right now.
      return Outcome.ELSEWHERE;
    }
    Row row = rows.get(0);
    Map<String, byte[]> attributes = attributesOf(row.primaryId());
    SessionAuthentication person;
    Object startedAt;
    try {
      person = personOf(attributes);
      startedAt = read(attributes, SessionLifetime.STARTED_AT_ATTRIBUTE);
    } catch (RuntimeException unreadable) {
      if (row.idleEnd() >= now) {
        return Outcome.KEPT;
      }
      LOG.error(
          "Una sesión vencida no se puede leer ({}): se borra sin avisar a LIV ni revocarla",
          unreadable.getClass().getSimpleName());
      return delete(row);
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
      return delete(row);
    }
    if (sharesTheProviderSession(row, person, now)) {
      // The person signed in again in the same session of the provider: ending this one there,
      // or in LIV, would end the one in use. Only the session of the BFF goes.
      return delete(row);
    }
    EndReason reason = absoluteEnd <= row.idleEnd() ? EndReason.ABSOLUTE : EndReason.IDLE;
    Object tokens = read(attributes, EncryptedAuthorizedClientRepository.ATTRIBUTE);
    if (closer.closeExpired(person, tokens, reason)) {
      return delete(row);
    }
    if (now - endedAt >= GIVE_UP_AFTER.toMillis()) {
      LOG.error(
          "LIV o el proveedor no aceptan el fin de una sesión vencida hace más de {}: se borra",
          GIVE_UP_AFTER);
      return delete(row);
    }
    return Outcome.KEPT;
  }

  /**
   * Another session of the BFF, still open, with the same session of the provider ({@code sid}).
   */
  private boolean sharesTheProviderSession(Row row, SessionAuthentication person, long now) {
    List<String> others =
        jdbc.query(
            "SELECT primary_id FROM "
                + table
                + " WHERE principal_name = ? AND primary_id <> ? AND expiry_time >= ?",
            (other, i) -> other.getString(1),
            row.principal(),
            row.primaryId(),
            now);
    String sid = person.getPrincipal().sessionId();
    for (String other : others) {
      try {
        SessionAuthentication theOther = personOf(attributesOf(other));
        if (theOther != null && theOther.getPrincipal().sessionId().equals(sid)) {
          return true;
        }
      } catch (RuntimeException unreadable) {
        // A session that cannot be read is not one in use.
      }
    }
    return false;
  }

  private Map<String, byte[]> attributesOf(String primaryId) {
    Map<String, byte[]> attributes = new HashMap<>();
    jdbc.query(
        "SELECT attribute_name, attribute_bytes FROM "
            + table
            + "_ATTRIBUTES WHERE session_primary_id = ? AND attribute_name IN (?, ?, ?)",
        row -> {
          attributes.put(row.getString(1), row.getBytes(2));
        },
        primaryId,
        SECURITY_CONTEXT,
        SessionLifetime.STARTED_AT_ATTRIBUTE,
        EncryptedAuthorizedClientRepository.ATTRIBUTE);
    return attributes;
  }

  private SessionAuthentication personOf(Map<String, byte[]> attributes) {
    return read(attributes, SECURITY_CONTEXT) instanceof SecurityContext context
            && context.getAuthentication() instanceof SessionAuthentication person
        ? person
        : null;
  }

  /** An attribute as Spring Session stores it, serialized; {@code null} if the session lacks it. */
  private Object read(Map<String, byte[]> attributes, String name) {
    byte[] bytes = attributes.get(name);
    return bytes == null ? null : deserializer.convert(bytes);
  }

  private Outcome delete(Row row) {
    int deleted = jdbc.update("DELETE FROM " + table + " WHERE primary_id = ?", row.primaryId());
    return deleted == 1 ? Outcome.ENDED : Outcome.ELSEWHERE;
  }

  private enum Outcome {
    /** Ended and deleted here. */
    ENDED,
    /** Not over yet, or its end failed and is tried again later. */
    KEPT,
    /** Gone, or being ended by someone else. */
    ELSEWHERE
  }

  private record Candidate(String sessionId, long creationTime) {}

  private record Row(String primaryId, String principal, long idleEnd) {}
}
