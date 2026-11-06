package co.com.sapenlinea.liv.bff.session;

import co.com.sapenlinea.liv.bff.session.OidcSessions.OidcSession;
import co.com.sapenlinea.liv.bff.session.StoredSessions.Row;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ends at once the sessions of the BFF of a session that the provider closed (OpenID Connect
 * Back-Channel Logout 1.0, 001-09, T19): an administrator closed it, disabled the person or changed
 * what the person may do. Each one ends as at the sign out, with {@code PROVIDER}, but in another
 * order: the session of the BFF is deleted first, in a short transaction, so it cannot be used
 * again whatever happens next; then LIV is told and the refresh token is revoked, with no
 * connection held. If LIV does not accept the end, the sweep tells it later ({@link
 * #tellThePendingEnds}), with what {@code bff.oidc_session} keeps of the session.
 */
public final class ProviderLogout {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderLogout.class);

  /** How long the end waits for a session that someone else has locked, such as a request. */
  static final Duration LOCK_WAIT = Duration.ofSeconds(5);

  /** Pending ends told to LIV per sweep. */
  static final int BATCH = 200;

  private final StoredSessions stored;
  private final OidcSessions oidc;
  private final SessionCloser closer;
  private final Duration giveUpAfter;
  private final Clock clock;

  /**
   * @param giveUpAfter how long an end that LIV does not accept is tried again
   */
  public ProviderLogout(
      StoredSessions stored,
      OidcSessions oidc,
      SessionCloser closer,
      Duration giveUpAfter,
      Clock clock) {
    this.stored = stored;
    this.oidc = oidc;
    this.closer = closer;
    this.giveUpAfter = giveUpAfter;
    this.clock = clock;
  }

  /**
   * Ends the sessions of the BFF of that session of the provider.
   *
   * @return whether all of them ended; {@code false} if one could not be taken (still locked, or
   *     the database failed), and the provider should be told to try again
   */
  public boolean end(String issuer, String sid) {
    boolean all = true;
    for (OidcSession session : oidc.sessionsOf(issuer, sid)) {
      try {
        endOne(session);
      } catch (RuntimeException e) {
        LOG.error(
            "No se pudo terminar una sesión que el proveedor cerró ({}): sid {}",
            e.getClass().getSimpleName(),
            StoredSessions.fingerprint(sid));
        all = false;
      }
    }
    return all;
  }

  private void endOne(OidcSession session) {
    Taken taken = stored.inTransaction(() -> take(session.sessionId()));
    if (taken == null) {
      // Gone already: there is nothing to end.
      oidc.forget(session.sessionId());
      return;
    }
    SessionAuthentication person =
        taken.person() != null ? taken.person() : new SessionAuthentication(session.identity());
    if (closer.closeStored(person, taken.tokens(), EndReason.PROVIDER)) {
      oidc.forget(session.sessionId());
    } else {
      oidc.markEndPending(session.sessionId());
    }
  }

  /** In a transaction: the session locked, read and deleted; {@code null} if it is gone. */
  private Taken take(String sessionId) {
    Optional<Row> locked = stored.lockWaiting(sessionId, LOCK_WAIT);
    if (locked.isEmpty()) {
      return null;
    }
    Row row = locked.get();
    Map<String, byte[]> attributes = stored.attributesOf(row.primaryId());
    SessionAuthentication person = null;
    try {
      person = stored.personOf(attributes);
    } catch (RuntimeException unreadable) {
      // It is told to LIV with what bff.oidc_session keeps; the tokens cannot be revoked.
      LOG.error(
          "Una sesión que el proveedor cerró no se puede leer (sesión {})",
          StoredSessions.fingerprint(sessionId));
    }
    Object tokens = person == null ? null : stored.tokensOf(attributes);
    stored.delete(row.primaryId());
    return new Taken(person, tokens);
  }

  /**
   * Tells LIV the ends of sessions the provider closed that it did not accept at the time, up to
   * {@link #BATCH}; after {@code giveUpAfter} it gives up, with an error in the log.
   */
  public void tellThePendingEnds() {
    for (OidcSession pending : oidc.pendingEnds(BATCH)) {
      if (closer.endInLiv(pending.identity(), EndReason.PROVIDER)) {
        oidc.forget(pending.sessionId());
      } else if (clock.millis() - pending.pendingEndSince() >= giveUpAfter.toMillis()) {
        LOG.error(
            "LIV no acepta el fin de una sesión que el proveedor cerró hace más de {}: se deja"
                + " (sid {})",
            giveUpAfter,
            StoredSessions.fingerprint(pending.identity().sessionId()));
        oidc.forget(pending.sessionId());
      }
    }
  }

  private record Taken(SessionAuthentication person, Object tokens) {}
}
