package co.com.sapenlinea.liv.core;

import java.time.Instant;
import java.util.UUID;

/**
 * A session start refused (001-05, 001-14, 001-17), published in a transaction of its own so the
 * refusal is audited even though the start rolls back (plan step 8). The user may not exist in LIV
 * yet. It carries no identity of the provider (issuer, subject): the event waits in the outbox, and
 * the audit keeps no trace of who tried (decision of the project lead, 2026-11-03).
 *
 * <p>The administration comes in two fields, because only one is trustworthy: the registered one is
 * verified by LIV; the claimed one is only what the token says and may not exist (audit writes it
 * as a platform row, ADR 0025).
 *
 * @param code the criterion that refused it
 * @param sid the session the start asked for
 * @param userId the LIV id of the user, or null if it is not registered
 * @param registeredAdministrationId the registered administration of the user, or null
 * @param claimedAdministrationId the administration the token claims, or null
 * @param deniedAt when it was refused
 * @param eventId the id of this event: the audit stores it once (idempotent listener, T15)
 * @param correlationId the correlation id of the request that caused it, if any (001-18)
 */
public record SessionDenied(
    String code,
    String sid,
    UUID userId,
    UUID registeredAdministrationId,
    UUID claimedAdministrationId,
    Instant deniedAt,
    UUID eventId,
    String correlationId) {}
