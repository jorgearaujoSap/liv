package co.com.sapenlinea.liv.core;

import java.time.Instant;
import java.util.UUID;

/**
 * A user registered in LIV on its first session (001-21): LIV gives it its own id, linked to the
 * pair (issuer, subject) of the provider.
 *
 * @param userId the LIV id of the user
 * @param administrationId its administration
 * @param provisionedAt when it was registered
 * @param eventId the id of this event: the audit stores it once (idempotent listener, T15)
 * @param correlationId the correlation id of the request that caused it, if any (001-18)
 */
public record UserProvisioned(
    UUID userId,
    UUID administrationId,
    Instant provisionedAt,
    UUID eventId,
    String correlationId) {}
