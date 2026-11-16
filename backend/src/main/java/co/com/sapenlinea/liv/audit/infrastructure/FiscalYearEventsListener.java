package co.com.sapenlinea.liv.audit.infrastructure;

import co.com.sapenlinea.liv.audit.application.RecordFiscalYearEvents;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to the events of {@code tax} (ADR 0035: {@code audit → tax}) after their transaction
 * commits, in the threads of the audit executor, as {@link SessionEventsListener} does with those
 * of {@code core}: the request does not wait for the audit, and the JDBC outbox of Spring Modulith
 * keeps each delivery until the listener returns and delivers it again if it failed. The audit
 * stores each event once, by its id.
 *
 * <p>No transaction here: the audit log opens one per row, in the administration of the row.
 */
@Component
class FiscalYearEventsListener {

  private final RecordFiscalYearEvents record;

  FiscalYearEventsListener(RecordFiscalYearEvents record) {
    this.record = record;
  }

  @Async(AuditExecutorConfiguration.AUDIT_EXECUTOR)
  @TransactionalEventListener
  public void on(FiscalYearCreated event) {
    record.created(event);
  }

  @Async(AuditExecutorConfiguration.AUDIT_EXECUTOR)
  @TransactionalEventListener
  public void on(FiscalYearStatusChanged event) {
    record.statusChanged(event);
  }
}
