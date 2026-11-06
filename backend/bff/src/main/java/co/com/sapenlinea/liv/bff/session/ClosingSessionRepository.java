package co.com.sapenlinea.liv.bff.session;

import java.util.Map;
import java.util.function.Supplier;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * The repository of Spring Session, except that a session read once expired is ended as it should
 * (T19, {@link ExpiredSessions}) instead of being deleted without telling anyone. Everything else
 * goes to the repository of Spring Session as it is.
 *
 * <p>The check is one query by the primary key of the session. Between it and the read there are
 * milliseconds: a session that expires right then is still deleted by Spring Session, and the
 * person finds it closed as always; only its EndSession is missing.
 */
public final class ClosingSessionRepository<S extends Session>
    implements FindByIndexNameSessionRepository<S> {

  private final FindByIndexNameSessionRepository<S> sessions;
  private final Supplier<ExpiredSessions> expired;

  /**
   * @param expired what ends the expired sessions; a supplier, because it needs this repository and
   *     is built after it
   */
  public ClosingSessionRepository(
      FindByIndexNameSessionRepository<S> sessions, Supplier<ExpiredSessions> expired) {
    this.sessions = sessions;
    this.expired = expired;
  }

  @Override
  public S findById(String id) {
    if (expired.get().endIfExpired(id)) {
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

  @Override
  public void deleteById(String id) {
    sessions.deleteById(id);
  }

  @Override
  public Map<String, S> findByIndexNameAndIndexValue(String indexName, String indexValue) {
    return sessions.findByIndexNameAndIndexValue(indexName, indexValue);
  }
}
