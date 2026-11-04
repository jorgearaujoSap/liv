package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.SessionEnded;
import co.com.sapenlinea.liv.core.SessionStarted;
import co.com.sapenlinea.liv.core.UserProvisioned;
import co.com.sapenlinea.liv.platform.OpenUseCase;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The audit of the sessions of LIV (001-18, plan step 9): every start, end, refusal and first
 * login, with its user, date, result and correlation id. The listeners of {@code core} call it
 * after the commit, without a user or a request: it needs no permission.
 *
 * <p>A refusal goes to the administration LIV registered for the user; for a user LIV does not
 * know, to the one the token claims if it exists; otherwise it is a platform row with no actor
 * (decision of the project lead: nothing that identifies who tried).
 */
@Service
public class RecordSessionEvents {

  private static final String SOURCE = "LIV";
  private static final String SESSION = "session";

  private final AuditLog log;

  RecordSessionEvents(AuditLog log) {
    this.log = log;
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición")
  public void started(SessionStarted event) {
    log.append(
        new Entry(
            SOURCE,
            id(event.eventId()),
            event.administrationId(),
            event.userId(),
            SESSION,
            null,
            "SESSION_STARTED",
            event.startedAt(),
            event.correlationId(),
            null,
            Map.of("result", "SUCCESS")));
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición")
  public void ended(SessionEnded event) {
    log.append(
        new Entry(
            SOURCE,
            id(event.eventId()),
            event.administrationId(),
            event.userId(),
            SESSION,
            null,
            "SESSION_ENDED",
            event.endedAt(),
            event.correlationId(),
            null,
            Map.of("result", "SUCCESS", "reason", event.reason())));
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición")
  public void denied(SessionDenied event) {
    boolean registered = event.registeredAdministrationId() != null;
    log.appendSessionDenied(
        new Entry(
            SOURCE,
            id(event.eventId()),
            registered ? event.registeredAdministrationId() : event.claimedAdministrationId(),
            registered ? event.userId() : null,
            SESSION,
            null,
            "SESSION_DENIED",
            event.deniedAt(),
            event.correlationId(),
            null,
            Map.of("result", "DENIED", "reason", event.code())));
  }

  @OpenUseCase("Oyente de los eventos de core: corre sin usuario ni petición")
  public void provisioned(UserProvisioned event) {
    log.append(
        new Entry(
            SOURCE,
            id(event.eventId()),
            event.administrationId(),
            event.userId(),
            "user",
            event.userId(),
            "USER_PROVISIONED",
            event.provisionedAt(),
            event.correlationId(),
            null,
            Map.of("result", "SUCCESS")));
  }

  /** Events of LIV carry a random UUID: no one can guess it to block a row (V202611010915). */
  private static String id(UUID eventId) {
    return eventId.toString();
  }
}
