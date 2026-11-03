package co.com.sapenlinea.liv.core;

import java.time.Instant;
import java.util.UUID;

/**
 * A LIV session started (plan step 8; audit listens, ADR 0025). Published in the transaction that
 * creates it, so it exists only if the session does.
 *
 * @param sid the session of the provider
 * @param userId the LIV id of the user (001-21)
 * @param administrationId the registered administration
 * @param startedAt when it started, by the clock of the database
 */
public record SessionStarted(String sid, UUID userId, UUID administrationId, Instant startedAt) {}
