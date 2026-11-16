package co.com.sapenlinea.liv.audit.application;

import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The audit of the fiscal years of an administration (007-20, plan step 7): every creation and
 * change of state, on the entity {@code tax.administration_fiscal_year} and the id of its row, with
 * the state before (never when it is created: the event of a creation has none), the state after, the year, its user, administration,
 * instant and correlation id. The listener of {@code tax} calls it after the commit, without a user
 * or a request: it needs no permission.
 *
 * <p>The whitelist of the row is the year and the state; nothing else of the event is copied.
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
    append(
        "FISCAL_YEAR_CREATED",
        event.eventId(),
        event.administrationId(),
        event.userId(),
        event.id(),
        event.year(),
        null,
        event.newStatus(),
        event.occurredAt(),
        event.correlationId());
  }

  @OpenUseCase("Oyente de los eventos de tax: corre sin usuario ni petición")
  public void statusChanged(FiscalYearStatusChanged event) {
    append(
        "FISCAL_YEAR_STATUS_CHANGED",
        event.eventId(),
        event.administrationId(),
        event.userId(),
        event.id(),
        event.year(),
        Objects.requireNonNull(event.previousStatus(), "previousStatus"),
        event.newStatus(),
        event.occurredAt(),
        event.correlationId());
  }

  private void append(
      String operation,
      UUID eventId,
      UUID administrationId,
      UUID userId,
      UUID rowId,
      int year,
      String previousStatus,
      String newStatus,
      Instant occurredAt,
      String correlationId) {
    log.append(
        new Entry(
            SOURCE,
            // Events of LIV carry a random UUID: no one can guess it to block a row.
            eventId.toString(),
            administrationId,
            userId,
            ENTITY,
            rowId,
            operation,
            occurredAt,
            correlationId,
            null,
            previousStatus == null ? null : Map.of("status", previousStatus),
            Map.of("year", year, "status", Objects.requireNonNull(newStatus, "newStatus"))));
  }
}
