package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The audit of the fiscal years of an administration (007-20, plan step 7): every creation and
 * change of state, on the entity {@code tax.administration_fiscal_year} and the id of its row, with
 * the state before, the state after, the year, its user, administration, instant and correlation
 * id. The listener of {@code tax} calls it after the commit, without a user or a request: it needs
 * no permission.
 *
 * <p>The whitelist of the row is the year and the state; nothing else of the event is copied. A
 * creation never has a state before, whatever its event says; a change needs both states, and
 * without them the event is not audited (it stays in the outbox) instead of being written halfway.
 */
@Service
public class RecordFiscalYearEvents {

  private static final String SOURCE = "LIV";
  private static final String ENTITY = "tax.administration_fiscal_year";

  private final AuditLog log;

  RecordFiscalYearEvents(AuditLog log) {
    this.log = log;
  }

  @OpenUseCase("Oyente de los eventos de tax: corre sin usuario ni petición")
  public void created(FiscalYearCreated event) {
    log.append(
        new Entry(
            SOURCE,
            // The id of the event: the outbox may deliver it again and the row is stored once.
            event.eventId().toString(),
            event.administrationId(),
            event.userId(),
            ENTITY,
            event.id(),
            "FISCAL_YEAR_CREATED",
            event.occurredAt(),
            event.correlationId(),
            null,
            null,
            newValues(event.year(), event.newStatus())));
  }

  @OpenUseCase("Oyente de los eventos de tax: corre sin usuario ni petición")
  public void statusChanged(FiscalYearStatusChanged event) {
    String previousStatus = Objects.requireNonNull(event.previousStatus(), "previousStatus");
    log.append(
        new Entry(
            SOURCE,
            event.eventId().toString(),
            event.administrationId(),
            event.userId(),
            ENTITY,
            event.id(),
            "FISCAL_YEAR_STATUS_CHANGED",
            event.occurredAt(),
            event.correlationId(),
            null,
            Map.of("status", previousStatus),
            newValues(event.year(), event.newStatus())));
  }

  private static Map<String, Object> newValues(int year, String status) {
    return Map.of("year", year, "status", Objects.requireNonNull(status, "newStatus"));
  }
}
