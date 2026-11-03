package co.com.sapenlinea.liv.core;

import java.util.UUID;

/**
 * A session start refused (001-05, 001-17), published in a transaction of its own so the refusal is
 * audited even though the start rolls back (plan step 8). The user may not exist in LIV yet: then
 * only the identity of the provider is known.
 *
 * @param code the criterion that refused it
 * @param issuer the issuer of the user token
 * @param subject the subject of the provider, an opaque reference
 * @param userId the LIV id of the user, or null if it is not registered
 * @param administrationId the administration involved (registered or claimed), or null
 */
public record SessionDenied(
    String code, String issuer, String subject, UUID userId, UUID administrationId) {}
