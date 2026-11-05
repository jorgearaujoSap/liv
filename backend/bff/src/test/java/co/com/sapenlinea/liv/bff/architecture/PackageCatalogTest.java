package co.com.sapenlinea.liv.bff.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * All the code of the BFF lives under {@code co.com.sapenlinea.liv.bff}, in the packages that the
 * feature 001 tasks declare (tasks.md). A new package is a design change: it is reviewed and added
 * to this catalog in the same pull request.
 */
class PackageCatalogTest {

  private static final String BASE_PACKAGE = "co.com.sapenlinea.liv.bff";

  private static final Set<String> PACKAGES = Set.of("login", "logout", "session", "web");

  private static final List<JavaClass> PRODUCTION_CLASSES =
      new ClassFileImporter()
              .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
              .importPackages("co.com.sapenlinea.liv")
              .stream()
              .toList();

  @Test
  @DisplayName("Todo el código del BFF está bajo co.com.sapenlinea.liv.bff")
  void everythingLivesUnderTheBffPackage() {
    assertThat(PRODUCTION_CLASSES).as("clases del BFF").isNotEmpty();
    Set<String> outside = new TreeSet<>();
    for (JavaClass javaClass : PRODUCTION_CLASSES) {
      String name = javaClass.getPackageName();
      if (!name.equals(BASE_PACKAGE) && !name.startsWith(BASE_PACKAGE + ".")) {
        outside.add(name);
      }
    }
    assertThat(outside).as("paquetes fuera del BFF").isEmpty();
  }

  @Test
  @DisplayName("Cada paquete del BFF pertenece al catálogo: login, logout, session y web")
  void packagesBelongToTheCatalog() {
    Set<String> unknown = new TreeSet<>();
    for (JavaClass javaClass : PRODUCTION_CLASSES) {
      String name = javaClass.getPackageName();
      if (name.startsWith(BASE_PACKAGE + ".")) {
        String root = name.substring(BASE_PACKAGE.length() + 1).split("\\.")[0];
        if (!PACKAGES.contains(root)) {
          unknown.add(root);
        }
      }
    }
    assertThat(unknown).as("paquetes fuera del catálogo").isEmpty();
  }
}
