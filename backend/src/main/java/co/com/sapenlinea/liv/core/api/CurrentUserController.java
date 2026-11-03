package co.com.sapenlinea.liv.core.api;

import co.com.sapenlinea.liv.core.api.contract.CurrentUserApi;
import co.com.sapenlinea.liv.core.api.contract.model.AdministrationSummary;
import co.com.sapenlinea.liv.core.api.contract.model.CurrentUser;
import co.com.sapenlinea.liv.core.application.GetCurrentUser;
import co.com.sapenlinea.liv.core.application.GetCurrentUser.Me;
import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/me} (core.yaml, 001-13): the user the security filter resolved, with the roles
 * and permissions in alphabetical order.
 */
@RestController
class CurrentUserController implements CurrentUserApi {

  private final GetCurrentUser getCurrentUser;

  CurrentUserController(GetCurrentUser getCurrentUser) {
    this.getCurrentUser = getCurrentUser;
  }

  @Override
  public ResponseEntity<CurrentUser> getCurrentUser() {
    co.com.sapenlinea.liv.platform.CurrentUser user =
        co.com.sapenlinea.liv.platform.CurrentUser.current()
            .orElseThrow(() ->
                new AuthenticationRejectedException(
                    ErrorCodes.NOT_AUTHENTICATED, "No current user"));
    Me me = getCurrentUser.get(user);
    return ResponseEntity.ok(
        new CurrentUser(
            me.id(),
            me.fullName(),
            me.email(),
            new AdministrationSummary(me.administrationId(), me.administrationName()),
            me.roles().stream().sorted().toList(),
            me.permissions().stream().sorted().toList()));
  }
}
