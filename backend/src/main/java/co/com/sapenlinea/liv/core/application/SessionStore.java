package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.TokenUser;
import java.util.UUID;

/**
 * Users and sessions of LIV (plan step 8). Each operation is one transaction in the administration
 * it names, opened before the transaction (doc. 02 §5.1), and publishes the events of what it
 * changed in that transaction: they arrive only if the change commits. A start that does not start
 * rolls back entirely, so a refused first login leaves no user behind.
 */
public interface SessionStore {

  /**
   * Starts the session of a registered user, checking again inside the transaction that it is still
   * active and in its administration, and updates its last login.
   */
  StartOutcome start(DirectoryUser user, TokenUser token, String sid);

  /**
   * Registers the user in the administration the token claims (001-21) and starts its session.
   * Concurrent first logins register one user: the others read it back and use it if it is active
   * and in that administration.
   */
  StartOutcome provisionAndStart(
      UUID administrationId, String fullName, String email, TokenUser token, String sid);

  /**
   * Ends the open session {@code sid} of the user with that identity; does nothing otherwise.
   *
   * @return whether a session was ended (and {@code SessionEnded} published)
   */
  boolean end(DirectoryUser user, String issuer, String subject, String sid, String reason);

  /** Publishes a refusal in a transaction of its own, whatever happens to the start. */
  void denied(SessionDenied refusal);

  /**
   * What a start did.
   *
   * @param result the outcome
   * @param userId the LIV id of the user, if the outcome has one
   */
  record StartOutcome(Result result, UUID userId) {}

  /**
   * The outcomes of a start. Only {@code STARTED} keeps its changes: everything else rolls back, so
   * a retry does not move the last login either.
   */
  enum Result {
    /** A new session. */
    STARTED,
    /** A retry: the open session of this user with this sid already exists. */
    RETRIED,
    /** The sid belongs to another user's session (or one this administration cannot see). */
    SID_TAKEN,
    /** The sid is this user's, but the session already ended: it is not reopened. */
    SESSION_CLOSED,
    /** The user is no longer (or not) active in the administration this start expected. */
    USER_CHANGED,
    /** The administration does not exist or is not active (001-17). */
    ADMINISTRATION_UNAVAILABLE
  }
}
