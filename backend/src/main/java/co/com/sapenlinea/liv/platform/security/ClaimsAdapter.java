package co.com.sapenlinea.liv.platform.security;

import java.util.Optional;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the LIV claims of a token with the configured names (D-001-6). */
final class ClaimsAdapter {

  private final LivSecurityProperties.Claims names;

  ClaimsAdapter(LivSecurityProperties.Claims names) {
    this.names = names;
  }

  TokenIdentity identityOf(Jwt jwt) {
    return new TokenIdentity(null, null, Optional.empty(), Set.of());
  }
}
