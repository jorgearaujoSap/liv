package co.com.sapenlinea.liv.bff.session;

import java.util.Map;
import java.util.function.Supplier;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.util.function.SingletonSupplier;

/**
 * The repository of Spring Session, except that a session already expired is not read: Spring
 * Session would delete it without telling anyone. The request gets no session, and the sweep ends
 * it as it should (T19, {@link ExpiredSessions}). Everything else goes to the repository of Spring
 * Session as it is.
 *
 * <p>The check is one query by the session id, which has a unique index. Between it and the read
 * there are milliseconds: a session that expires right then is still deleted by Spring Session, and
 * the person finds it closed as always; only its EndSession and its revocation are missing. That is
 * the one case left of a session deleted without telling anyone.
 */
public final class ClosingSessionRepository<S extends Session>
    implements FindByIndexNameSessionRepository<S> {

  private final FindByIndexNameSessionRepository<S> sessions;
  private final Supplier<ExpiredSessions> expired;
  private final Supplier<OidcSessions> oidc;

  /**
   * @param expired what ends the expired sessions; a supplier, because it needs this repository and
   *     is built after it
   */
  public ClosingSessionRepository(
      FindByIndexNameSessionRepository<S> sessions,
      Supplier<ExpiredSessions> expired,
      Supplier<OidcSessions> oidc) {
    this.sessions = sessions;
    this.expired = SingletonSupplier.of(expired);
    this.oidc = SingletonSupplier.of(oidc);
  }

  @Override
  public S findById(String id) {
    if (expired.get().isExpired(id)) {
      return null;
    }
    return sessions.findById(id);
  }

  @Override
  public S createSession() {
    return sessions.createSession();
  }

  @Override
  public void save(S session) {
    sessions.save(session);
  }

  /** Every session of the BFF deleted by Spring Session (sign out, invalidated) forgets its sid. */
  @Override
  public void deleteById(String id) {
    sessions.deleteById(id);
    oidc.get().forget(id);
  }

  @Override
  public Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue) {
    return sessions.findByIndexNameAndIndexValue(indexName, indexValue);
  }
}
