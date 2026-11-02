package co.com.sapenlinea.liv.platform;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The user of the current request to {@code /api/**}, resolved in the LIV registry by the security
 * filter. The administration is the registered one; the roles come from the token.
 */
public record CurrentUser(
    UUID userId, UUID administrationId, String issuer, String subject, Set<String> roles) {

  /** Request attribute where the security filter leaves the user. */
  public static final String ATTRIBUTE = CurrentUser.class.getName();

  public static Optional<CurrentUser> current() {
    RequestAttributes request = RequestContextHolder.getRequestAttributes();
    return request == null
        ? Optional.empty()
        : Optional.ofNullable(
            (CurrentUser) request.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
  }
}
