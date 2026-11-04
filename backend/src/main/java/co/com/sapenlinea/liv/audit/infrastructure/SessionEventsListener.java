package co.com.sapenlinea.liv.audit.infrastructure;

import co.com.sapenlinea.liv.audit.application.RecordSessionEvents;
import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.SessionEnded;
import co.com.sapenlinea.liv.core.SessionStarted;
import co.com.sapenlinea.liv.core.UserProvisioned;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to the events of {@code core} (ADR 0025: {@code audit → core}) after their transaction
 * commits, in another thread: the request does not wait for the audit. The JDBC outbox of Spring
 * Modulith keeps each delivery until the listener returns, and delivers it again on restart if it
 * failed, so the audit is not lost after the commit (plan step 8).
 *
 * <p>No transaction here: the audit log opens one per row, in the administration of the row.
 */
@Component
class SessionEventsListener {

  private final RecordSessionEvents record;

  SessionEventsListener(RecordSessionEvents record) {
    this.record = record;
  }

  @Async
  @TransactionalEventListener
  public void on(SessionStarted event) {
    record.started(event);
  }

  @Async
  @TransactionalEventListener
  public void on(SessionEnded event) {
    record.ended(event);
  }

  @Async
  @TransactionalEventListener
  public void on(SessionDenied event) {
    record.denied(event);
  }

  @Async
  @TransactionalEventListener
  public void on(UserProvisioned event) {
    record.provisioned(event);
  }
}
