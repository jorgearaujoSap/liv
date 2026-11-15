package co.com.sapenlinea.liv.tax;

import java.time.Instant;
import java.util.UUID;

/**
 * The state of the fiscal year of an administration changed (feature 007: {@code PLANEADA} to
 * {@code EN_PREPARACION}; audit listens, ADR 0035). Published in the transaction that changes it.
 * The states are the codes of Doc. 01 §5 as text.
 *
 * @param eventId the id of this event: the audit stores it once (idempotent listener)
 * @param administrationId the administration the fiscal year belongs to
 * @param userId the LIV id of the user who changed it
 * @param id the id of the row {@code tax.administration_fiscal_year}
 * @param year the fiscal year
 * @param previousStatus the state before
 * @param newStatus the state after
 * @param occurredAt when it happened, by the clock of the application
 * @param correlationId the correlation id of the request that caused it, if any
 */
public record FiscalYearStatusChanged(
    UUID eventId,
    UUID administrationId,
    UUID userId,
    UUID id,
    int year,
    String previousStatus,
    String newStatus,
    Instant occurredAt,
    String correlationId) {}
