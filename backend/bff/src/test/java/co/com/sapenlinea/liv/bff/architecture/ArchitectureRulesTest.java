package co.com.sapenlinea.liv.bff.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

/** Architecture fitness functions of the BFF (docs/arquitectura/04 §3.1), as in liv-api. */
@Tag("001-20")
@AnalyzeClasses(
    packages = "co.com.sapenlinea.liv",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule noIdentityProviderLibraries =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.keycloak..")
          .because("authentication uses only standard OIDC and OAuth 2.0 (D-001-6, 001-20)");

  /**
   * The rule above only sees the code of the BFF: a Keycloak adapter that arrived through the
   * pom.xml and configured itself would pass it. No Keycloak class may be on the classpath at all.
   */
  @ArchTest
  static void noIdentityProviderLibrariesOnTheClasspath(JavaClasses bffClasses) {
    assertThat(new ClassFileImporter().importPackages("org.keycloak"))
        .as("clases de Keycloak en el classpath (001-20)")
        .isEmpty();
  }

  @ArchTest
  static final ArchRule constructorInjectionOnly =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .because("dependencies are injected through constructors (CC-08)")
          .allowEmptyShould(true);
}
