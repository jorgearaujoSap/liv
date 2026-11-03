package co.com.sapenlinea.liv.platform.navigation;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The menu options the user of the request sees (001-15): the permissions of the roles of its token
 * that LIV knows, applied to the menu catalog. The backend decides what shows; the frontend only
 * decides how (plan, «Menú»). It needs no permission of its own: every user with a session gets its
 * menu, even an empty one (plan, step 7).
 */
@Service
public class GetCapabilities {

  private final PermissionCatalog permissions;
  private final MenuCatalog menu;

  GetCapabilities(PermissionCatalog permissions) {
    this.permissions = permissions;
    // Loaded and validated here on purpose: a malformed catalog stops the application at startup.
    this.menu = MenuCatalog.load(MenuCatalog.LIV_CATALOG);
  }

  public List<Capability> of(CurrentUser user) {
    return menu.visibleTo(permissions.permissionsOf(user.roles()));
  }
}
