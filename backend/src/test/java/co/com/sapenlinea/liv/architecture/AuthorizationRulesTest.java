package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * The authorization rules over the production code, plus fixtures that prove they can fail. The
 * fixtures play the role of use cases from this package, since a test class cannot live in a
 * module's {@code application}.
 */
@AnalyzeClasses(
    packages = "co.com.sapenlinea.liv",
    importOptions = ImportOption.DoNotIncludeTests.class)
class AuthorizationRulesTest {

  private static final String FIXTURES = "..liv.architecture..";

  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("co.com.sapenlinea.liv");

  @ArchTest
  static final ArchRule useCasesAreInterceptableServices =
      AuthorizationRules.useCasesAreInterceptableServices(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule useCaseMethodsAreMarked =
      AuthorizationRules.useCaseMethodsAreMarked(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule marksOnlyOnUseCases =
      AuthorizationRules.marksOnlyOnUseCases(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule noSelfCallsToGuardedMethods =
      AuthorizationRules.noSelfCallsToGuardedMethods(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule noSpringMethodSecurityAnnotations =
      AuthorizationRules.noSpringMethodSecurityAnnotations();

  @Test
  @Tag("001-16")
  @DisplayName(
      "001-16 · El paquete de los casos de uso es el de los servicios reales de application")
  void useCasesPackageSelectsTheRealServices() {
    Set<String> services =
        PRODUCTION.stream()
            .filter(c ->
                c.getPackageName().matches("co\\.com\\.sapenlinea\\.liv\\.\\w+\\.application.*"))
            .filter(c -> c.isAnnotatedWith(Service.class))
            .map(JavaClass::getSimpleName)
            .collect(Collectors.toSet());

    assertThat(services).contains("GetCurrentUser", "StartSession", "EndSession");
    assertThat(
            AuthorizationRules.useCaseMethodsAreMarked(AuthorizationRules.USE_CASES)
                .evaluate(PRODUCTION.that(JavaClass.Predicates.simpleName("GetCurrentUser")))
                .hasViolation())
        .isFalse();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Los casos de uso abiertos son solo los de la lista cerrada, por método")
  void openUseCasesAreTheClosedList() {
    Set<String> open =
        PRODUCTION.stream()
            .flatMap(c -> c.getMethods().stream())
            .filter(m -> m.isAnnotatedWith(OpenUseCase.class))
            .map(m -> m.getOwner().getName() + "." + m.getName())
            .collect(Collectors.toSet());

    assertThat(open).isEqualTo(AuthorizationRules.OPEN_USE_CASES);
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un caso de uso con sus métodos públicos marcados cumple todas las reglas")
  void markedUseCasePasses() {
    for (ArchRule rule : fixtureRules()) {
      assertThat(rule.evaluate(classes(MarkedUseCase.class)).hasViolation())
          .as(rule.getDescription())
          .isFalse();
    }
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un método público sin marca, o con las dos, viola la regla")
  void unmarkedOrDoublyMarkedMethodFails() {
    EvaluationResult half = methodsMarked(HalfGuardedUseCase.class);
    assertThat(half.getFailureReport().getDetails())
        .anyMatch(detail -> detail.contains(".unguarded()"))
        .noneMatch(detail -> detail.contains(".guarded()"));
    assertThat(methodsMarked(DoublyMarkedUseCase.class).hasViolation()).isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un método static o final no lo intercepta el proxy y viola la regla")
  void staticOrFinalMethodFails() {
    assertThat(methodsMarked(StaticMethodUseCase.class).hasViolation()).isTrue();
    assertThat(methodsMarked(FinalMethodUseCase.class).hasViolation()).isTrue();
    assertThat(marksOnly(FinalMethodUseCase.class).hasViolation()).isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un caso de uso @Component, final o con superclase viola la regla")
  void notInterceptableServiceFails() {
    for (Class<?> fixture :
        List.of(ComponentUseCase.class, FinalUseCase.class, InheritingUseCase.class)) {
      assertThat(
              AuthorizationRules.useCasesAreInterceptableServices(FIXTURES)
                  .evaluate(classes(fixture))
                  .hasViolation())
          .as(fixture.getSimpleName())
          .isTrue();
    }
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Una marca fuera de un caso de uso, o en un método privado, viola la regla")
  void markOutsideUseCasesFails() {
    assertThat(marksOnly(ComponentUseCase.class).hasViolation()).isTrue();
    assertThat(marksOnly(PrivatelyGuardedUseCase.class).hasViolation()).isTrue();
    // A @Service outside the packages of use cases.
    assertThat(
            AuthorizationRules.marksOnlyOnUseCases(AuthorizationRules.USE_CASES)
                .evaluate(classes(MarkedUseCase.class))
                .hasViolation())
        .isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Llamar con this a un método protegido de la misma clase viola la regla")
  void selfCallToGuardedMethodFails() {
    assertThat(
            AuthorizationRules.noSelfCallsToGuardedMethods(FIXTURES)
                .evaluate(classes(SelfCallingUseCase.class))
                .hasViolation())
        .isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · @PreAuthorize y sus parecidos no se usan: no se comprobarían")
  void springMethodSecurityAnnotationsFail() {
    assertThat(
            AuthorizationRules.noSpringMethodSecurityAnnotations()
                .evaluate(classes(PreAuthorizedUseCase.class))
                .hasViolation())
        .isTrue();
  }

  private static List<ArchRule> fixtureRules() {
    return List.of(
        AuthorizationRules.useCasesAreInterceptableServices(FIXTURES),
        AuthorizationRules.useCaseMethodsAreMarked(FIXTURES),
        AuthorizationRules.marksOnlyOnUseCases(FIXTURES),
        AuthorizationRules.noSelfCallsToGuardedMethods(FIXTURES),
        AuthorizationRules.noSpringMethodSecurityAnnotations());
  }

  private static EvaluationResult methodsMarked(Class<?> fixture) {
    return AuthorizationRules.useCaseMethodsAreMarked(FIXTURES).evaluate(classes(fixture));
  }

  private static EvaluationResult marksOnly(Class<?> fixture) {
    return AuthorizationRules.marksOnlyOnUseCases(FIXTURES).evaluate(classes(fixture));
  }

  private static JavaClasses classes(Class<?> fixture) {
    return new ClassFileImporter().importClasses(fixture);
  }

  @Service
  static class MarkedUseCase {
    @RequiresPermission("core.user.read")
    public String run() {
      return helper();
    }

    @OpenUseCase("Fixture")
    public String open() {
      return "";
    }

    private String helper() {
      return "";
    }
  }

  @Service
  static class HalfGuardedUseCase {
    @RequiresPermission("core.user.read")
    public String guarded() {
      return "";
    }

    public String unguarded() {
      return "";
    }
  }

  @Service
  static class DoublyMarkedUseCase {
    @RequiresPermission("core.user.read")
    @OpenUseCase("Fixture")
    public String run() {
      return "";
    }
  }

  @Service
  static class StaticMethodUseCase {
    public static String run() {
      return "";
    }
  }

  @Service
  static class FinalMethodUseCase {
    @RequiresPermission("core.user.read")
    public final String run() {
      return "";
    }
  }

  /** Not a @Service: the runtime check would not see it. */
  @Component
  static class ComponentUseCase {
    @RequiresPermission("core.user.read")
    public String run() {
      return "";
    }
  }

  @Service
  static final class FinalUseCase {
    @RequiresPermission("core.user.read")
    public String run() {
      return "";
    }
  }

  static class UseCaseBase {
    public String inherited() {
      return "";
    }
  }

  @Service
  static class InheritingUseCase extends UseCaseBase {
    @RequiresPermission("core.user.read")
    public String run() {
      return "";
    }
  }

  /** Spring proxies call only public methods: a guard on a private one would never run. */
  @Service
  static class PrivatelyGuardedUseCase {
    @OpenUseCase("Fixture")
    public String run() {
      return check();
    }

    @RequiresPermission("core.user.read")
    private String check() {
      return "";
    }
  }

  @Service
  static class SelfCallingUseCase {
    @RequiresPermission("core.user.read")
    public String read() {
      return approve();
    }

    @RequiresPermission("core.user.approve")
    public String approve() {
      return "";
    }
  }

  @Service
  static class PreAuthorizedUseCase {
    @PreAuthorize("hasRole('ADMIN')")
    public String run() {
      return "";
    }
  }
}
