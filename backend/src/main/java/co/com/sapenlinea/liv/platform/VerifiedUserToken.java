package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The user token of {@code POST /internal/v1/sessions} (header {@code X-User-Token}), already
 * validated by the security filter with the same decoder as {@code /api/**}.
 */
public final class VerifiedUserToken {

  /** Request attribute where the security filter leaves the validated token. */
  public static final String ATTRIBUTE = VerifiedUserToken.class.getName();

  private VerifiedUserToken() {}

  public static Optional<Jwt> current() {
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    return request == null
        ? Optional.empty()
        : Optional.ofNullable(
            (Jwt) request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
  }
}
