package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.application.SessionStore.StartOutcome;
import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.RequestRefusedException;
import co.com.sapenlinea.liv.platform.TokenUser;
import co.com.sapenlinea.liv.platform.UserDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Starts the LIV session of a user who just authenticated (StartSession, plan step 8). The data
 * comes from the validated user token; the registry of LIV decides:
 *
 * <ul>
 *   <li>the {@code sid} of the body must be the token's, not another user's and not of a session
 *       that ended (401, 001-14);
 *   <li>a registered user must be active (403, 001-05) and in the administration the token claims
 *       (403, 001-17);
 *   <li>a new user is registered, if automatic registration is on, in the claimed administration if
 *       it exists and is active (403, 001-17), with its own LIV id (001-21); its email and name
 *       must fit the registry (401, 001-14).
 * </ul>
 *
 * <p>Every refusal publishes {@code SessionDenied} (decision of the project lead, T13). A retry
 * with the same {@code sid} returns the open session already started.
 */
@Service
@EnableConfigurationProperties(SessionProperties.class)
public class StartSession {

  /** Lengths of {@code core.admin_user.full_name} and {@code email}. */
  private static final int MAX_FULL_NAME = 150;

  private static final int MAX_EMAIL = 200;

  private final UserDirectory directory;
  private final SessionStore store;
  private final SessionProperties properties;

  StartSession(UserDirectory directory, SessionStore store, SessionProperties properties) {
    this.directory = directory;
    this.store = store;
    this.properties = properties;
  }

  /**
   * A started (or retried) session.
   *
   * @param userId the LIV id of the user
   * @param created whether this call created it (false: a retry with the same sid)
   */
  public record StartedSession(UUID userId, boolean created) {}

  public StartedSession start(TokenUser token, String sid) {
    Attempt attempt = new Attempt(token, sid);
    Optional<DirectoryUser> registered = directory.resolve(token.issuer(), token.subject());
    if (!token.sessionId().filter(sid::equals).isPresent()) {
      throw attempt.unauthenticated(registered.orElse(null));
    }
    if (registered.isPresent()) {
      DirectoryUser user = registered.get();
      attempt.checkRegistered(user);
      return attempt.outcome(store.start(user, token, sid), user);
    }
    return attempt.outcome(provision(attempt), null);
  }

  private StartOutcome provision(Attempt attempt) {
    TokenUser token = attempt.token;
    if (!properties.autoProvision()) {
      throw attempt.refused(ErrorCodes.ADMINISTRATION_MISMATCH, null);
    }
    if (token.administrationId().isEmpty()) {
      throw attempt.refused(ErrorCodes.ADMINISTRATION_MISMATCH, null);
    }
    // Without an email the provider did not say who the user is; longer values do not fit.
    String email = token.email().filter(value -> fits(value, MAX_EMAIL)).orElse(null);
    String fullName = token.fullName().orElse(email);
    if (email == null || !fits(fullName, MAX_FULL_NAME)) {
      throw attempt.unauthenticated(null);
    }
    return store.provisionAndStart(
        token.administrationId().get(), fullName, email, token, attempt.sid);
  }

  /** VARCHAR counts characters, not UTF-16 units. */
  private static boolean fits(String value, int maxCharacters) {
    return value.codePointCount(0, value.length()) <= maxCharacters;
  }

  /** One start: it knows how to refuse itself, publishing the refusal first. */
  private final class Attempt {
    private final TokenUser token;
    private final String sid;

    private Attempt(TokenUser token, String sid) {
      this.token = token;
      this.sid = sid;
    }

    private StartedSession outcome(StartOutcome outcome, DirectoryUser user) {
      return switch (outcome.result()) {
        case STARTED -> new StartedSession(outcome.userId(), true);
        case RETRIED -> new StartedSession(outcome.userId(), false);
        case SID_TAKEN, SESSION_CLOSED -> throw unauthenticated(user);
        case ADMINISTRATION_UNAVAILABLE -> throw refused(ErrorCodes.ADMINISTRATION_MISMATCH, user);
        case USER_CHANGED -> throw changed();
      };
    }

    /** The user changed while starting (or a concurrent login registered it otherwise). */
    private RuntimeException changed() {
      Optional<DirectoryUser> now = directory.resolve(token.issuer(), token.subject());
      now.ifPresent(this::checkRegistered);
      return refused(ErrorCodes.ADMINISTRATION_MISMATCH, now.orElse(null));
    }

    private void checkRegistered(DirectoryUser user) {
      if (!user.active()) {
        throw refused(ErrorCodes.USER_INACTIVE, user);
      }
      if (!token.administrationId().filter(user.administrationId()::equals).isPresent()) {
        throw refused(ErrorCodes.ADMINISTRATION_MISMATCH, user);
      }
    }

    private RequestRefusedException refused(String code, DirectoryUser user) {
      publish(code, user);
      return new RequestRefusedException(code, "Session start refused with " + code);
    }

    private AuthenticationRejectedException unauthenticated(DirectoryUser user) {
      publish(ErrorCodes.NOT_AUTHENTICATED, user);
      return new AuthenticationRejectedException(
          ErrorCodes.NOT_AUTHENTICATED, "Session start refused: sid or identity data");
    }

    private void publish(String code, DirectoryUser user) {
      store.denied(
          new SessionDenied(
              code,
              token.issuer(),
              token.subject(),
              sid,
              user == null ? null : user.id(),
              user == null ? null : user.administrationId(),
              token.administrationId().orElse(null)));
    }
  }
}
