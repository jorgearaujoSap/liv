package co.com.sapenlinea.liv.tax;

import java.time.Instant;
import java.util.UUID;

/**
 * The fiscal year of an administration was created (feature 007; audit listens, ADR 0035).
 * Published in the transaction that creates it, so it exists only if the fiscal year does. The
 * states are the codes of Doc. 01 §5 as text: another module reads the event without knowing the
 * domain of {@code tax}.
 *
 * @param eventId the id of this event: the audit stores it once (idempotent listener)
 * @param administrationId the administration the fiscal year belongs to
 * @param userId the LIV id of the user who created it
 * @param id the id of the row {@code tax.administration_fiscal_year}
 * @param year the fiscal year
 * @param previousStatus the state before: always {@code null} when it is created
 * @param newStatus the state after ({@code PLANEADA})
 * @param occurredAt when it happened, by the clock of the application
 * @param correlationId the correlation id of the request that caused it, if any
 */
public record FiscalYearCreated(
    UUID eventId,
    UUID administrationId,
    UUID userId,
    UUID id,
    int year,
    String previousStatus,
    String newStatus,
    Instant occurredAt,
    String correlationId) {}
