package co.com.sapenlinea.liv.platform.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.PlatformWebTest;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A misspelt permission hides an option or refuses a use case to everyone, silently. Every
 * permission the menu and the use cases name must exist in {@code platform.app_permission}.
 */
@DisplayName("Nombres de permisos (T14)")
class PermissionNamesTest extends PlatformWebTest {

  @Autowired JdbcClient jdbc;

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Cada permiso del menú existe en el catálogo de permisos")
  void menuPermissionsExist() {
    assertThat(knownPermissions())
        .containsAll(MenuCatalog.load(MenuCatalog.LIV_CATALOG).permissions());
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Cada permiso de @RequiresPermission existe en el catálogo de permisos")
  void useCasePermissionsExist() {
    Set<String> required =
        new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("co.com.sapenlinea.liv")
                .stream()
                .flatMap(type -> type.getMethods().stream())
                .filter(method -> method.isAnnotatedWith(RequiresPermission.class))
                .map(method -> method.getAnnotationOfType(RequiresPermission.class).value())
                .collect(Collectors.toSet());

    assertThat(knownPermissions()).containsAll(required);
  }

  private Set<String> knownPermissions() {
    return Set.copyOf(
        jdbc.sql("SELECT code FROM platform.app_permission").query(String.class).list());
  }
}
