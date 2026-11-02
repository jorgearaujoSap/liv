package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The user of the current request to {@code /api/**}, resolved in the LIV registry by the security
 * filter. The administration is the registered one; the roles come from the token.
 */
public record CurrentUser(
    UUID userId, UUID administrationId, String issuer, String subject, Set<String> roles) {

  public static Optional<CurrentUser> current() {
    return Optional.empty();
  }
}
