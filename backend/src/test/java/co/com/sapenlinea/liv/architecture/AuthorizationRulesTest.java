package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.RequiresPermission;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

  @ArchTest
  static final ArchRule useCasesRequirePermission =
      AuthorizationRules.useCasesRequirePermission(AuthorizationRules.USE_CASES);

  @ArchTest
  static final ArchRule permissionsOnlyOnUseCases =
      AuthorizationRules.permissionsOnlyOnUseCases(AuthorizationRules.USE_CASES);

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un caso de uso con @RequiresPermission en sus métodos públicos cumple")
  void guardedUseCasePasses() {
    assertThat(requirePermission(GuardedUseCase.class).hasViolation()).isFalse();
    assertThat(onlyOnUseCases(GuardedUseCase.class).hasViolation()).isFalse();
  }

  @Test
  @Tag("001-16")
  @DisplayName(
      "001-16 · Un método público de un caso de uso sin @RequiresPermission viola la regla")
  void unguardedMethodFails() {
    EvaluationResult result = requirePermission(HalfGuardedUseCase.class);

    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(detail -> detail.contains(".unguarded()"))
        .noneMatch(detail -> detail.contains(".guarded()"));
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · @RequiresPermission fuera de un caso de uso viola la regla")
  void permissionOutsideUseCasesFails() {
    assertThat(onlyOnUseCases(GuardedComponent.class).hasViolation()).isTrue();
    assertThat(onlyOnUseCases(PrivatelyGuardedUseCase.class).hasViolation()).isTrue();
  }

  private static EvaluationResult requirePermission(Class<?> fixture) {
    return AuthorizationRules.useCasesRequirePermission(FIXTURES).evaluate(classes(fixture));
  }

  private static EvaluationResult onlyOnUseCases(Class<?> fixture) {
    return AuthorizationRules.permissionsOnlyOnUseCases(FIXTURES).evaluate(classes(fixture));
  }

  private static JavaClasses classes(Class<?> fixture) {
    return new ClassFileImporter().importClasses(fixture);
  }

  @Service
  static class GuardedUseCase {
    @RequiresPermission("core.user.read")
    public String run() {
      return helper();
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

  /** Not a service: a controller or adapter must not decide permissions. */
  @Component
  static class GuardedComponent {
    @RequiresPermission("core.user.read")
    public String run() {
      return "";
    }
  }

  /** Spring proxies call only public methods: a guard on a private one would never run. */
  @Service
  static class PrivatelyGuardedUseCase {
    public String run() {
      return check();
    }

    @RequiresPermission("core.user.read")
    private String check() {
      return "";
    }
  }
}
