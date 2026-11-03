package co.com.sapenlinea.liv.core.api;

import co.com.sapenlinea.liv.core.api.contract.CurrentUserApi;
import co.com.sapenlinea.liv.core.api.contract.model.AdministrationSummary;
import co.com.sapenlinea.liv.core.api.contract.model.CurrentUser;
import co.com.sapenlinea.liv.core.application.GetCurrentUser;
import co.com.sapenlinea.liv.core.application.GetCurrentUser.Me;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/v1/me} (core.yaml, 001-13). */
@RestController
class CurrentUserController implements CurrentUserApi {

  private final GetCurrentUser getCurrentUser;

  CurrentUserController(GetCurrentUser getCurrentUser) {
    this.getCurrentUser = getCurrentUser;
  }

  @Override
  public ResponseEntity<CurrentUser> getCurrentUser() {
    Me me = getCurrentUser.get();
    return ResponseEntity.ok(
        new CurrentUser(
            me.id(),
            me.fullName(),
            me.email(),
            new AdministrationSummary(me.administrationId(), me.administrationName()),
            List.copyOf(me.roles().stream().sorted().toList()),
            List.copyOf(me.permissions().stream().sorted().toList())));
  }
}
