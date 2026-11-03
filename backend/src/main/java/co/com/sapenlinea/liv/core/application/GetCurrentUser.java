package co.com.sapenlinea.liv.core.application;

import co.com.sapenlinea.liv.core.application.UserProfiles.Profile;
import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Who the user of the request is (001-13), from the server only: the LIV registry for the user and
 * its administration — the registered one, which the security filter resolved (001-17) — and the
 * roles of the token that LIV knows, with their permissions. Nothing comes from what the browser
 * sends. A user without known roles gets empty lists: it still reaches Inicio, which needs no
 * permission (plan, «Permisos»).
 */
@Service
public class GetCurrentUser {

  private final UserProfiles profiles;
  private final PermissionCatalog catalog;

  GetCurrentUser(UserProfiles profiles, PermissionCatalog catalog) {
    this.profiles = profiles;
    this.catalog = catalog;
  }

  /**
   * The user of the session.
   *
   * @param id the LIV id of the user
   * @param fullName its name in the registry
   * @param email its email in the registry
   * @param administrationId its registered administration
   * @param administrationName the name of that administration
   * @param roles the roles of the token that LIV knows
   * @param permissions the permissions of those roles
   */
  public record Me(
      UUID id,
      String fullName,
      String email,
      UUID administrationId,
      String administrationName,
      Set<String> roles,
      Set<String> permissions) {

    public Me {
      roles = Set.copyOf(roles);
      permissions = Set.copyOf(permissions);
    }
  }

  public Me get(CurrentUser user) {
    Set<String> roles = catalog.knownRoles(user.roles());
    Set<String> granted = catalog.permissionsOf(roles);
    Profile profile =
        profiles
            .find(user.userId(), user.administrationId())
            // The filter resolved it a moment ago: if it is gone, there is no LIV session.
            .orElseThrow(() ->
                new AuthenticationRejectedException(
                    ErrorCodes.NOT_AUTHENTICATED, "User no longer in its administration"));
    return new Me(
        user.userId(),
        profile.fullName(),
        profile.email(),
        user.administrationId(),
        profile.administrationName(),
        roles,
        granted);
  }
}
