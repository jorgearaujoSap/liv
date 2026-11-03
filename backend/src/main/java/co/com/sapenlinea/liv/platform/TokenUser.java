package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a validated user token says about its user (plan step 8: issuer, subject, administration,
 * name and email come from the token). The administration is only what the token claims: the
 * registered one is the authority (001-17). The custom claims use the configured names; {@code
 * sid}, {@code name} and {@code email} are standard OIDC claims (001-20).
 *
 * @param issuer the issuer of the token
 * @param subject the subject of the provider ({@code sub}); LIV links its own id to it (001-21)
 * @param sessionId the session of the provider ({@code sid}), if the token has one
 * @param administrationId the administration the token claims, if it is a valid UUID
 * @param fullName the {@code name} claim, if any
 * @param email the {@code email} claim, if any
 * @param roles the roles of the token
 */
public record TokenUser(
    String issuer,
    String subject,
    Optional<String> sessionId,
    Optional<UUID> administrationId,
    Optional<String> fullName,
    Optional<String> email,
    Set<String> roles) {}
