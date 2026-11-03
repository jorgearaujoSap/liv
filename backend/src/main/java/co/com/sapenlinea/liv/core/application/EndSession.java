package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.UserDirectory;
import org.springframework.stereotype.Service;

/**
 * Ends a LIV session (EndSession, plan step 8): only the open session {@code sid} of the user with
 * that issuer and subject, once. Anything else does nothing, and the caller always gets the same
 * answer, so it is idempotent and does not reveal whether a {@code sid} exists.
 */
@Service
public class EndSession {

  private final UserDirectory directory;
  private final SessionStore store;

  EndSession(UserDirectory directory, SessionStore store) {
    this.directory = directory;
    this.store = store;
  }

  @OpenUseCase("Lo llama el BFF con su token de servicio: lo protege el alcance de /internal")
  public void end(String sid, String issuer, String subject, String reason) {
    directory
        .resolve(issuer, subject)
        .ifPresent(user -> store.end(user, issuer, subject, sid, reason));
  }
}
