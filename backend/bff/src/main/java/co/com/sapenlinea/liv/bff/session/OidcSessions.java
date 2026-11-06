package co.com.sapenlinea.liv.bff.session;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Which session of the BFF belongs to which session of the provider ({@code sid}), in {@code
 * bff.oidc_session} (plan, step 10; T19): the provider names its session when it closes it on the
 * back channel, and with several replicas any of them must find ours. A row is written when LIV
 * lets the person in, with the id the session has from then on, and goes when the session of the
 * BFF is deleted. When the end of a session the provider closed is still to be told to LIV, the row
 * stays as pending, and the sweep tells it ({@link ProviderLogout#tellThePendingEnds}).
 */
public final class OidcSessions {

  /** A row this new may belong to a session not saved yet: the sweep leaves it. */
  private static final Duration GRACE = Duration.ofMinutes(5);

  private final JdbcOperations jdbc;
  private final Clock clock;
  private final String sessionTable;

  /**
   * @param sessionTable the table of Spring Session, {@code spring.session.jdbc.table-name}
   */
  public OidcSessions(DataSource dataSource, Clock clock, String sessionTable) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.clock = clock;
    this.sessionTable = sessionTable;
  }

  /** A session of the BFF and the one of the provider it belongs to. */
  public record OidcSession(String sessionId, SessionIdentity identity, Long pendingEndSince) {}

  /** The session of the BFF {@code sessionId} is the one of the provider in {@code identity}. */
  public void record(String sessionId, SessionIdentity identity) {
    jdbc.update(
        "INSERT INTO bff.oidc_session (session_id, issuer, sid, subject, created_at)"
            + " VALUES (?, ?, ?, ?, ?) ON CONFLICT (session_id) DO UPDATE SET issuer ="
            + " EXCLUDED.issuer, sid = EXCLUDED.sid, subject = EXCLUDED.subject, created_at ="
            + " EXCLUDED.created_at, pending_end_since = NULL",
        sessionId,
        identity.issuer(),
        identity.sessionId(),
        identity.subject(),
        clock.millis());
  }

  /** The open sessions of the BFF of a session of the provider. */
  public List<OidcSession> sessionsOf(String issuer, String sid) {
    return jdbc.query(
        "SELECT session_id, issuer, subject, sid FROM bff.oidc_session"
            + " WHERE issuer = ? AND sid = ? AND pending_end_since IS NULL",
        (row, i) ->
            new OidcSession(
                row.getString(1),
                new SessionIdentity(row.getString(2), row.getString(3), row.getString(4)),
                null),
        issuer,
        sid);
  }

  /** Up to {@code limit} ends still to be told to LIV, the oldest first. */
  public List<OidcSession> pendingEnds(int limit) {
    return jdbc.query(
        "SELECT session_id, issuer, subject, sid, pending_end_since FROM bff.oidc_session"
            + " WHERE pending_end_since IS NOT NULL ORDER BY pending_end_since LIMIT ?",
        (row, i) ->
            new OidcSession(
                row.getString(1),
                new SessionIdentity(row.getString(2), row.getString(3), row.getString(4)),
                row.getLong(5)),
        limit);
  }

  /**
   * The end of that session is still to be told to LIV.
   *
   * @throws IllegalStateException if the row is not there: the end would be lost
   */
  public void markEndPending(String sessionId) {
    int marked =
        jdbc.update(
            "UPDATE bff.oidc_session SET pending_end_since = ? WHERE session_id = ?",
            clock.millis(),
            sessionId);
    if (marked != 1) {
      throw new IllegalStateException("La sesión del proveedor no está registrada");
    }
  }

  public void forget(String sessionId) {
    jdbc.update("DELETE FROM bff.oidc_session WHERE session_id = ?", sessionId);
  }

  /** Forgets the row unless its end is still to be told to LIV (someone else marked it). */
  public void forgetUnlessPending(String sessionId) {
    jdbc.update(
        "DELETE FROM bff.oidc_session WHERE session_id = ? AND pending_end_since IS NULL",
        sessionId);
  }

  /** Whether another session of the BFF, not ended, belongs to that session of the provider. */
  public boolean anotherOpenSession(OidcSession session) {
    return !jdbc.queryForList(
            "SELECT 1 FROM bff.oidc_session WHERE issuer = ? AND sid = ? AND session_id <> ?"
                + " AND pending_end_since IS NULL",
            Integer.class,
            session.identity().issuer(),
            session.identity().sessionId(),
            session.sessionId())
        .isEmpty();
  }

  /**
   * Removes up to {@code limit} rows whose session of the BFF is gone and that have no end to tell;
   * how many.
   */
  public int forgetTheGone(int limit) {
    return jdbc.update(
        "DELETE FROM bff.oidc_session WHERE session_id IN (SELECT o.session_id FROM"
            + " bff.oidc_session o WHERE o.pending_end_since IS NULL AND o.created_at < ? AND NOT"
            + " EXISTS (SELECT 1 FROM "
            + sessionTable
            + " s WHERE s.session_id = o.session_id) LIMIT ?)",
        clock.millis() - GRACE.toMillis(),
        limit);
  }
}
