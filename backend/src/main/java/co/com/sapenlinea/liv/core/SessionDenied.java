package co.com.sapenlinea.liv.core;

import java.util.UUID;

/**
 * A session start refused (001-05, 001-14, 001-17), published in a transaction of its own so the
 * refusal is audited even though the start rolls back (plan step 8). The user may not exist in LIV
 * yet: then only the identity of the provider is known.
 *
 * <p>The administration comes in two fields, because only one is trustworthy: the registered one is
 * verified by LIV; the claimed one is only what the token says and may not exist (audit writes it
 * as a platform row, ADR 0025).
 *
 * @param code the criterion that refused it
 * @param issuer the issuer of the user token
 * @param subject the subject of the provider, an opaque reference
 * @param sid the session the start asked for
 * @param userId the LIV id of the user, or null if it is not registered
 * @param registeredAdministrationId the registered administration of the user, or null
 * @param claimedAdministrationId the administration the token claims, or null
 */
public record SessionDenied(
    String code,
    String issuer,
    String subject,
    String sid,
    UUID userId,
    UUID registeredAdministrationId,
    UUID claimedAdministrationId) {}
