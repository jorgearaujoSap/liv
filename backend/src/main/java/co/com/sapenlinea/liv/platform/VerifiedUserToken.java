package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The user of {@code POST /internal/v1/sessions} (header {@code X-User-Token}): the token is
 * validated by the security filter with the decoder of {@code /api/**}, and this is what it says.
 */
public final class VerifiedUserToken {

  /** Request attribute where the security filter leaves the user of the validated token. */
  public static final String ATTRIBUTE = VerifiedUserToken.class.getName();

  private VerifiedUserToken() {}

  public static Optional<TokenUser> current() {
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    return request == null
        ? Optional.empty()
        : Optional.ofNullable(
            (TokenUser) request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
  }
}
