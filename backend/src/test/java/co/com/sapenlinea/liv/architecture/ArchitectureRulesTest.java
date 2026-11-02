package co.com.sapenlinea.liv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Architecture fitness functions (docs/arquitectura/04 §3.1). The modules with business rules (tax,
 * ledger, valuation, jurisdiction) follow full hexagonal architecture; the registry modules may use
 * JPA entities in their domain. Rules over packages that are still empty allow an empty "should" so
 * the build stays green until the first classes arrive.
 */
@AnalyzeClasses(
    packages = "co.com.sapenlinea.liv",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  private static final String[] HEXAGONAL_DOMAINS = {
    "..liv.tax.domain..", "..liv.ledger.domain..",
    "..liv.valuation.domain..", "..liv.jurisdiction.domain..",
    "..liv.shared.."
  };

  private static final String[] HEXAGONAL_APPLICATIONS = {
    "..liv.tax.application..", "..liv.ledger.application..",
    "..liv.valuation.application..", "..liv.jurisdiction.application.."
  };

  private static final String[] MONEY_PACKAGES = {
    "..liv.tax.domain..", "..liv.tax.application..",
    "..liv.ledger.domain..", "..liv.ledger.application..",
    "..liv.valuation.domain..", "..liv.valuation.application..",
    "..liv.jurisdiction.domain..", "..liv.jurisdiction.application.."
  };

  @ArchTest
  static final ArchRule restApiOnlyTalksToApplication =
      noClasses()
          .that()
          .resideInAPackage("..liv.*.api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..liv.*.infrastructure..")
          .because("controllers translate HTTP and call use cases only (CC-07)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule hexagonalDomainsArePlainJava =
      noClasses()
          .that()
          .resideInAnyPackage(HEXAGONAL_DOMAINS)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.jooq..")
          .because(
              "the rule engine and the ledger are pure functions (docs/arquitectura/01 §4.1, §5)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule hexagonalApplicationsUsePorts =
      noClasses()
          .that()
          .resideInAnyPackage(HEXAGONAL_APPLICATIONS)
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..liv.*.infrastructure..")
          .because(
              "use cases depend on domain ports, never on adapters (docs/arquitectura/01 §4.1)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule moneyIsNeverFloatingPoint =
      noFields()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage(MONEY_PACKAGES)
          .should()
          .haveRawType(double.class)
          .orShould()
          .haveRawType(float.class)
          .orShould()
          .haveRawType(Double.class)
          .orShould()
          .haveRawType(Float.class)
          .because("money is exact: BigDecimal through the Money type (CC-02, ADR 0016)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule systemClockIsInjected =
      noClasses()
          .that()
          .resideInAnyPackage("..liv.*.domain..", "..liv.*.application..")
          .should()
          .callMethod(LocalDate.class, "now")
          .orShould()
          .callMethod(LocalDateTime.class, "now")
          .orShould()
          .callMethod(ZonedDateTime.class, "now")
          .orShould()
          .callMethod(Instant.class, "now")
          .because("time comes from an injected Clock (CC-03)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule constructorInjectionOnly =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .because("dependencies are injected through constructors (CC-08)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule noIdentityProviderLibraries =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.keycloak..")
          .because("authentication uses only standard OIDC and OAuth 2.0 (D-001-6, 001-20)");

  @ArchTest
  static final ArchRule administrationContextComesFromTheRegistry =
      noClasses()
          .that()
          .resideOutsideOfPackages("..liv.platform..", "..liv.*.infrastructure..")
          .should()
          .callMethod(AdministrationContext.class, "open", UUID.class)
          .because(
              "the administration comes from the LIV registry (security filter) or from the event"
                  + " of a listener or job, never from a request (001-17, doc. 02 §5.1)");

  @ArchTest
  static final ArchRule generatedModelsStayInTheApiLayer =
      noClasses()
          .that()
          .resideOutsideOfPackage("..liv.*.api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..api.contract..model")
          .because(
              "the generated models carry personal data in toString; only controllers map them"
                  + " (T11)")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule controllersDoNotLog =
      noClasses()
          .that()
          .resideInAPackage("..liv.*.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.slf4j..", "java.util.logging..", "org.apache.commons.logging..")
          .orShould()
          .dependOnClassesThat()
          .areAssignableTo(System.Logger.class)
          .because(
              "a generated model must never reach a logger: its toString has personal data (T11)")
          .allowEmptyShould(true);
}
