package co.com.sapenlinea.liv.core;

import java.util.UUID;

/**
 * A user registered in LIV on its first session (001-21): LIV gives it its own id, linked to the
 * pair (issuer, subject) of the provider.
 *
 * @param userId the LIV id of the user
 * @param administrationId its administration
 */
public record UserProvisioned(UUID userId, UUID administrationId) {}
