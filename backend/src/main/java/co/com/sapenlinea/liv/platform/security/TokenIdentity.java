package co.com.sapenlinea.liv.platform.security;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What LIV reads from a validated token. The administration is only a claim to compare. */
record TokenIdentity(
    String issuer, String subject, Optional<UUID> administrationId, Set<String> roles) {}
