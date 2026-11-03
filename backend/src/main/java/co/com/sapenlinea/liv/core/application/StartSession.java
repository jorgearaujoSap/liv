package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.core.application.SessionStore.AdministrationUnavailableException;
import co.com.sapenlinea.liv.core.application.SessionStore.StartedSession;
import co.com.sapenlinea.liv.core.application.SessionStore.UserRegisteredElsewhereException;
import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.RequestRefusedException;
import co.com.sapenlinea.liv.platform.TokenUser;
import co.com.sapenlinea.liv.platform.UserDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Starts the LIV session of a user who just authenticated (StartSession, plan step 8). The data
 * comes from the validated user token; the registry of LIV decides:
 *
 * <ul>
 *   <li>the {@code sid} of the body must be the token's, and not another user's (401, 001-14);
 *   <li>a registered user must be active (001-05) and in the administration the token claims
 *       (001-17);
 *   <li>a new user is registered in the claimed administration if it exists and is active (001-17),
 *       with its own LIV id (001-21).
 * </ul>
 *
 * <p>A refusal is a 403 with its code and a {@code SessionDenied} event that survives the refusal.
 * A retry with the same {@code sid} returns the session already started.
 */
@Service
public class StartSession {

  private final UserDirectory directory;
  private final SessionStore store;

  StartSession(UserDirectory directory, SessionStore store) {
    this.directory = directory;
    this.store = store;
  }

  public StartedSession start(TokenUser token, String sid) {
    if (!token.sessionId().filter(sid::equals).isPresent()) {
      throw new AuthenticationRejectedException(
          ErrorCodes.NOT_AUTHENTICATED, "The sid of the body is not the token's");
    }
    Optional<DirectoryUser> registered = directory.resolve(token.issuer(), token.subject());
    Optional<StartedSession> session =
        registered.isPresent()
            ? startRegistered(registered.get(), token, sid)
            : provision(token, sid);
    return session.orElseThrow(() ->
        new AuthenticationRejectedException(
            ErrorCodes.NOT_AUTHENTICATED, "The sid belongs to another session"));
  }

  private Optional<StartedSession> startRegistered(
      DirectoryUser user, TokenUser token, String sid) {
    checkRegistered(user, token);
    try {
      return store.start(user, token, sid);
    } catch (AdministrationUnavailableException e) {
      throw refuse(ErrorCodes.ADMINISTRATION_MISMATCH, token, user.id(), user.administrationId());
    }
  }

  private Optional<StartedSession> provision(TokenUser token, String sid) {
    UUID administration =
        token
            .administrationId()
            .orElseThrow(() -> refuse(ErrorCodes.ADMINISTRATION_MISMATCH, token, null, null));
    // admin_user requires both: without an email the provider did not say who the user is.
    String email =
        token
            .email()
            .orElseThrow(() -> refuse(ErrorCodes.NOT_AUTHENTICATED, token, null, administration));
    String fullName = token.fullName().orElse(email);
    try {
      return store.provisionAndStart(administration, fullName, email, token, sid);
    } catch (AdministrationUnavailableException e) {
      throw refuse(ErrorCodes.ADMINISTRATION_MISMATCH, token, null, administration);
    } catch (UserRegisteredElsewhereException e) {
      checkRegistered(e.user(), token);
      throw refuse(
          ErrorCodes.ADMINISTRATION_MISMATCH, token, e.user().id(), e.user().administrationId());
    }
  }

  private void checkRegistered(DirectoryUser user, TokenUser token) {
    if (!user.active()) {
      throw refuse(ErrorCodes.USER_INACTIVE, token, user.id(), user.administrationId());
    }
    if (!token.administrationId().filter(user.administrationId()::equals).isPresent()) {
      throw refuse(ErrorCodes.ADMINISTRATION_MISMATCH, token, user.id(), user.administrationId());
    }
  }

  private RequestRefusedException refuse(
      String code, TokenUser token, UUID userId, UUID administrationId) {
    store.denied(code, token, userId, administrationId);
    return new RequestRefusedException(code, "Session start refused with " + code);
  }
}
