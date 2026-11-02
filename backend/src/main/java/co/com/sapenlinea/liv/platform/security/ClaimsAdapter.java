package co.com.sapenlinea.liv.platform.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/**
 * Reads the LIV claims of a token with the configured names (D-001-6): the realm issues them flat,
 * and another provider may name them differently.
 */
final class ClaimsAdapter {

  private final LivSecurityProperties.Claims names;

  ClaimsAdapter(LivSecurityProperties.Claims names) {
    this.names = names;
  }

  TokenIdentity identityOf(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList(names.roles());
    return new TokenIdentity(
        jwt.getClaimAsString(JwtClaimNames.ISS),
        jwt.getSubject(),
        administrationOf(jwt),
        roles == null ? Set.of() : Set.copyOf(roles));
  }

  /** A missing or malformed administration is empty: it never matches the registered one. */
  private Optional<UUID> administrationOf(Jwt jwt) {
    String value = jwt.getClaimAsString(names.administrationId());
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
