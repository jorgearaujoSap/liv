package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The user token of {@code POST /internal/v1/sessions} (header {@code X-User-Token}), already
 * validated by the security filter with the same decoder as {@code /api/**}.
 */
public final class VerifiedUserToken {

  private VerifiedUserToken() {}

  public static Optional<Jwt> current() {
    return Optional.empty();
  }
}
