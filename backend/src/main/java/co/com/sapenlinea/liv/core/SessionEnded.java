package co.com.sapenlinea.liv.core;

import java.time.Instant;
import java.util.UUID;

/**
 * A LIV session ended, once (EndSession is idempotent; plan step 8).
 *
 * @param sid the session of the provider
 * @param userId the LIV id of the user
 * @param administrationId the registered administration
 * @param reason LOGOUT, IDLE, ABSOLUTE or PROVIDER
 * @param endedAt when it ended, by the clock of the database
 * @param eventId the id of this event: the audit stores it once (idempotent listener, T15)
 * @param correlationId the correlation id of the request that caused it, if any (001-18)
 */
public record SessionEnded(
    String sid,
    UUID userId,
    UUID administrationId,
    String reason,
    Instant endedAt,
    UUID eventId,
    String correlationId) {}
