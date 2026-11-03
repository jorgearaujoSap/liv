package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.TokenUser;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of users and sessions (plan step 8). Each operation is one transaction in the
 * administration it names, opened before the transaction (doc. 02 §5.1), and publishes its events
 * in it: they exist only if the change does.
 */
public interface SessionStore {

  /**
   * Starts the session of a registered user: updates its last login and records the session.
   *
   * @return the started session, or empty if the sid is already used by another user
   * @throws AdministrationUnavailableException if the administration is not active
   */
  Optional<StartedSession> start(DirectoryUser user, TokenUser token, String sid);

  /**
   * Registers the user in the administration the token claims (001-21) and starts its session.
   * Concurrent first logins register one user: the others read it back.
   *
   * @return the started session, or empty if the sid is already used by another user
   * @throws AdministrationUnavailableException if the administration does not exist or is not
   *     active
   * @throws UserRegisteredElsewhereException if a concurrent login registered the user otherwise
   */
  Optional<StartedSession> provisionAndStart(
      UUID administrationId, String fullName, String email, TokenUser token, String sid);

  /**
   * Ends the open session {@code sid} of the user with that identity; does nothing otherwise.
   *
   * @return whether a session was ended (and {@code SessionEnded} published)
   */
  boolean end(DirectoryUser user, String issuer, String subject, String sid, String reason);

  /** Publishes {@code SessionDenied} in a transaction of its own, whatever happens to the rest. */
  void denied(String code, TokenUser token, UUID userId, UUID administrationId);

  /**
   * A session of the user.
   *
   * @param userId the LIV id of the user
   * @param created whether this call created it (false: a retry with the same sid)
   */
  record StartedSession(UUID userId, boolean created) {}

  /** The administration does not exist or is not active (001-17). */
  class AdministrationUnavailableException extends RuntimeException {
    public AdministrationUnavailableException(UUID administrationId) {
      super("Administration " + administrationId + " is not available");
    }
  }

  /** The user exists, but not as this login expected (inactive or in another administration). */
  class UserRegisteredElsewhereException extends RuntimeException {
    private final DirectoryUser user;

    public UserRegisteredElsewhereException(DirectoryUser user) {
      super("User " + user.id() + " is registered otherwise");
      this.user = user;
    }

    public DirectoryUser user() {
      return user;
    }
  }
}
