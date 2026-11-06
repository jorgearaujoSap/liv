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
 * lets the person in, with the id the session has from then on; it goes when the session ends, and
 * the sweep removes the ones whose session is gone.
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

  /** The session of the BFF {@code sessionId} is the one of the provider in {@code identity}. */
  public void record(String sessionId, SessionIdentity identity) {
    jdbc.update(
        "INSERT INTO bff.oidc_session (session_id, issuer, sid, subject, created_at)"
            + " VALUES (?, ?, ?, ?, ?)"
            + " ON CONFLICT (session_id) DO UPDATE SET issuer = EXCLUDED.issuer, sid = EXCLUDED.sid,"
            + " subject = EXCLUDED.subject, created_at = EXCLUDED.created_at",
        sessionId,
        identity.issuer(),
        identity.sessionId(),
        identity.subject(),
        clock.millis());
  }

  /**
   * The sessions of the BFF of a session of the provider: by its {@code sid} if the provider gives
   * it, else every one of the person ({@code sub}), as OpenID Connect Back-Channel Logout 1.0 says.
   */
  public List<String> sessionsOf(String issuer, String sid, String subject) {
    if (sid != null) {
      return jdbc.queryForList(
          "SELECT session_id FROM bff.oidc_session WHERE issuer = ? AND sid = ?",
          String.class,
          issuer,
          sid);
    }
    return jdbc.queryForList(
        "SELECT session_id FROM bff.oidc_session WHERE issuer = ? AND subject = ?",
        String.class,
        issuer,
        subject);
  }

  public void forget(String sessionId) {
    jdbc.update("DELETE FROM bff.oidc_session WHERE session_id = ?", sessionId);
  }

  /** Removes the rows whose session of the BFF is gone; how many. */
  public int forgetTheGone() {
    return jdbc.update(
        "DELETE FROM bff.oidc_session o WHERE o.created_at < ? AND NOT EXISTS (SELECT 1 FROM "
            + sessionTable
            + " s WHERE s.session_id = o.session_id)",
        clock.millis() - GRACE.toMillis());
  }
}
