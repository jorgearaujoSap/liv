package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.OpenUseCase;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

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
  static final ArchRule noBeanMethodsForUseCases =
      AuthorizationRules.noBeanMethodsForUseCases(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule controllersUseOnlyUseCases =
      AuthorizationRules.controllersUseOnlyUseCases(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule useCaseMethodsArePublicOrPrivate =
      AuthorizationRules.useCaseMethodsArePublicOrPrivate(AuthorizationRules.USE_CASES);

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
            .map(JavaMethod::getFullName)
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

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un caso de uso creado con @Bean, sin @Service, viola la regla")
  void beanMethodForUseCaseFails() {
    assertThat(
            AuthorizationRules.noBeanMethodsForUseCases(FIXTURES)
                .evaluate(classes(UseCaseConfiguration.class))
                .hasViolation())
        .isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName(
      "001-16 · Un controlador que usa un puerto de application en lugar del caso de uso viola la regla")
  void controllerUsingAPortFails() {
    assertThat(
            AuthorizationRules.controllersUseOnlyUseCases(FIXTURES)
                .evaluate(classes(PortController.class))
                .hasViolation())
        .isTrue();
    assertThat(
            AuthorizationRules.controllersUseOnlyUseCases(FIXTURES)
                .evaluate(classes(UseCaseController.class))
                .hasViolation())
        .isFalse();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un método de caso de uso package-private o protected viola la regla")
  void packagePrivateMethodFails() {
    assertThat(
            AuthorizationRules.useCaseMethodsArePublicOrPrivate(FIXTURES)
                .evaluate(classes(PackagePrivateUseCase.class))
                .hasViolation())
        .isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Una referencia this::metodo a un método protegido viola la regla")
  void selfReferenceToGuardedMethodFails() {
    assertThat(
            AuthorizationRules.noSelfCallsToGuardedMethods(FIXTURES)
                .evaluate(classes(SelfReferencingUseCase.class))
                .hasViolation())
        .isTrue();
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un método asíncrono u oyente con @RequiresPermission viola la regla")
  void detachedGuardedMethodFails() {
    assertThat(marksOnly(DetachedUseCase.class).hasViolation()).isTrue();
  }

  private static List<ArchRule> fixtureRules() {
    return List.of(
        AuthorizationRules.useCasesAreInterceptableServices(FIXTURES),
        AuthorizationRules.useCaseMethodsArePublicOrPrivate(FIXTURES),
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

  /** Not a @Service: a port or helper of application, made by a @Bean method. */
  static class PlainUseCase {
    public String run() {
      return "";
    }
  }

  @Configuration
  static class UseCaseConfiguration {
    @Bean
    PlainUseCase plainUseCase() {
      return new PlainUseCase();
    }
  }

  @RestController
  static class PortController {
    private final PlainUseCase port;

    PortController(PlainUseCase port) {
      this.port = port;
    }

    String read() {
      return port.run();
    }
  }

  @RestController
  static class UseCaseController {
    private final MarkedUseCase useCase;

    UseCaseController(MarkedUseCase useCase) {
      this.useCase = useCase;
    }

    String read() {
      return useCase.run();
    }
  }

  @Service
  static class PackagePrivateUseCase {
    @RequiresPermission("core.user.read")
    public String run() {
      return "";
    }

    String unchecked() {
      return "";
    }
  }

  @Service
  static class SelfReferencingUseCase {
    @RequiresPermission("core.user.read")
    public Supplier<String> read() {
      return this::approve;
    }

    @RequiresPermission("core.user.approve")
    public String approve() {
      return "";
    }
  }

  @Service
  static class DetachedUseCase {
    @Async
    @RequiresPermission("core.user.read")
    public void run() {}
  }

  @Service
  static class PreAuthorizedUseCase {
    @PreAuthorize("hasRole('ADMIN')")
    public String run() {
      return "";
    }
  }
}
