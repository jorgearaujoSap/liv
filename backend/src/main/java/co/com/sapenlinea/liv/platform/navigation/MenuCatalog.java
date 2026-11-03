package co.com.sapenlinea.liv.platform.navigation;

import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/** The menu of LIV (001-15). Pending: T14. */
public final class MenuCatalog {

  static final Resource LIV_CATALOG = new ClassPathResource("platform/menu-catalog.yaml");

  private MenuCatalog() {}

  static MenuCatalog load(Resource resource) {
    throw new UnsupportedOperationException("T14");
  }

  List<Capability> visibleTo(Set<String> permissions) {
    throw new UnsupportedOperationException("T14");
  }
}
