package co.com.sapenlinea.liv.platform.security;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.DirectoryUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.UserDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * On each request to {@code /api/**}, after the token is validated, resolves the user in the LIV
 * registry (plan, step 7). It answers 401 when the user has no LIV session (001-14), is deactivated
 * (001-05) or the token's administration is not the registered one (001-17). Otherwise it exposes
 * the {@link CurrentUser} and opens the {@link AdministrationContext} with the registered
 * administration for the rest of the request.
 */
final class CurrentUserFilter extends OncePerRequestFilter {

  private final UserDirectory directory;
  private final ClaimsAdapter claims;

  CurrentUserFilter(UserDirectory directory, ClaimsAdapter claims) {
    this.directory = directory;
    this.claims = claims;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      throw rejected(ErrorCodes.NOT_AUTHENTICATED, "No bearer token");
    }
    TokenIdentity identity = claims.identityOf(token.getToken());
    DirectoryUser user =
        directory
            .resolve(identity.issuer(), identity.subject())
            .orElseThrow(() -> rejected(ErrorCodes.NOT_AUTHENTICATED, "No LIV session"));
    if (!user.active()) {
      throw rejected(ErrorCodes.USER_INACTIVE, "Deactivated user " + user.id());
    }
    if (!identity.administrationId().filter(user.administrationId()::equals).isPresent()) {
      throw rejected(
          ErrorCodes.ADMINISTRATION_MISMATCH,
          "Administration of the token is not the registered one for user " + user.id());
    }
    request.setAttribute(
        CurrentUser.ATTRIBUTE,
        new CurrentUser(
            user.id(),
            user.administrationId(),
            identity.issuer(),
            identity.subject(),
            identity.roles()));
    try (var scope = AdministrationContext.open(user.administrationId())) {
      chain.doFilter(request, response);
    }
  }

  private static AuthenticationRejectedException rejected(String code, String reason) {
    return new AuthenticationRejectedException(code, reason);
  }
}
