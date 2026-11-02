package co.com.sapenlinea.liv.architecture;

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
 * Every root package is a declared module and every business module uses only the four standard
 * layers (docs/arquitectura/01 §4). A new module or layer is an architecture decision: it needs an
 * ADR and an update of this catalog.
 */
class ModuleCatalogTest {

  private static final String BASE_PACKAGE = "co.com.sapenlinea.liv";

  /** Technical modules: no business layers. */
  private static final Set<String> TECHNICAL_MODULES = Set.of("shared", "platform");

  private static final Set<String> BUSINESS_MODULES =
      Set.of(
          "core", "documents", "jurisdiction", "valuation", "tax", "ledger", "audit", "reporting");

  private static final Set<String> STANDARD_LAYERS =
      Set.of("api", "application", "domain", "infrastructure");

  private static final List<JavaClass> PRODUCTION_CLASSES =
      new ClassFileImporter()
              .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
              .importPackages(BASE_PACKAGE)
              .stream()
              .filter(javaClass -> javaClass.getPackageName().startsWith(BASE_PACKAGE + "."))
              .toList();

  @Test
  @DisplayName("Cada paquete raíz pertenece al catálogo de módulos")
  void rootPackagesBelongToTheModuleCatalog() {
    Set<String> unknownModules = new TreeSet<>();
    for (JavaClass javaClass : PRODUCTION_CLASSES) {
      String module = relativeSegments(javaClass)[0];
      if (!TECHNICAL_MODULES.contains(module) && !BUSINESS_MODULES.contains(module)) {
        unknownModules.add(module);
      }
    }
    assertThat(unknownModules).as("paquetes raíz fuera del catálogo").isEmpty();
  }

  @Test
  @DisplayName(
      "Los módulos de negocio solo usan las capas api, application, domain e infrastructure")
  void businessModulesUseOnlyStandardLayers() {
    Set<String> unknownLayers = new TreeSet<>();
    for (JavaClass javaClass : PRODUCTION_CLASSES) {
      String[] segments = relativeSegments(javaClass);
      if (BUSINESS_MODULES.contains(segments[0])
          && segments.length > 1
          && !STANDARD_LAYERS.contains(segments[1])) {
        unknownLayers.add(segments[0] + "." + segments[1]);
      }
    }
    assertThat(unknownLayers).as("capas fuera del estándar").isEmpty();
  }

  private static String[] relativeSegments(JavaClass javaClass) {
    return javaClass.getPackageName().substring(BASE_PACKAGE.length() + 1).split("\\.");
  }
}
