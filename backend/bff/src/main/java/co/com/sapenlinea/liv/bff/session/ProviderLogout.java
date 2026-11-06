package co.com.sapenlinea.liv.bff.session;

import co.com.sapenlinea.liv.bff.session.StoredSessions.Row;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends at once the sessions of the BFF of a session that the provider closed (OpenID Connect
 * Back-Channel Logout 1.0, 001-09, T19): an administrator closed it, disabled the person or changed
 * what the person may do. Each one ends as at the sign out, with {@code PROVIDER}: LIV is told, the
 * refresh token is revoked and the session is deleted. Unlike an expired one, it is deleted even if
 * LIV or the provider fail: the provider already said it is over, and it must not be used again. A
 * failure is logged with a fingerprint of the {@code sid}, to match it with the audit.
 */
public final class ProviderLogout {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderLogout.class);

  private final StoredSessions stored;
  private final OidcSessions oidc;
  private final SessionCloser closer;

  public ProviderLogout(StoredSessions stored, OidcSessions oidc, SessionCloser closer) {
    this.stored = stored;
    this.oidc = oidc;
    this.closer = closer;
  }

  /**
   * Ends the sessions of the BFF of that session of the provider ({@link OidcSessions#sessionsOf}).
   *
   * @return how many it ended
   */
  public int end(String issuer, String sid, String subject) {
    int ended = 0;
    for (String sessionId : oidc.sessionsOf(issuer, sid, subject)) {
      if (Boolean.TRUE.equals(stored.inTransaction(() -> endLocked(sessionId)))) {
        ended++;
      }
      oidc.forget(sessionId);
    }
    return ended;
  }

  private boolean endLocked(String sessionId) {
    Optional<Row> locked = stored.lock(sessionId);
    if (locked.isEmpty()) {
      // Gone already, or the sweep is ending it right now.
      return false;
    }
    Row row = locked.get();
    Map<String, byte[]> attributes = stored.attributesOf(row.primaryId());
    SessionAuthentication person = null;
    try {
      person = stored.personOf(attributes);
    } catch (RuntimeException unreadable) {
      LOG.error(
          "Una sesión que el proveedor cerró no se puede leer: se borra sin avisar a LIV (sesión {})",
          StoredSessions.fingerprint(sessionId));
    }
    if (person != null
        && !closer.closeStored(person, stored.tokensOf(attributes), EndReason.PROVIDER)) {
      LOG.error(
          "LIV o el proveedor no aceptaron el fin de una sesión que el proveedor cerró: se borra"
              + " igual (sid {})",
          StoredSessions.fingerprint(person.getPrincipal().sessionId()));
    }
    return stored.delete(row.primaryId());
  }
}
