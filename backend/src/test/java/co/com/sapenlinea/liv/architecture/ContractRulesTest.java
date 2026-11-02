package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.core.api.contract.CurrentUserApi;
import co.com.sapenlinea.liv.core.api.contract.model.CurrentUser;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The contract rule over the production code, plus two fixtures that prove it can fail: a
 * controller that implements a generated interface passes and one written by hand does not.
 */
@AnalyzeClasses(
    packages = "co.com.sapenlinea.liv",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ContractRulesTest {

  @ArchTest
  static final ArchRule productionControllersImplementGeneratedContracts =
      ContractRules.CONTROLLERS_IMPLEMENT_GENERATED_CONTRACTS;

  @Test
  @DisplayName("ADR 0005 · Un controlador que implementa la interfaz generada cumple la regla")
  void generatedContractControllerPasses() {
    EvaluationResult result = evaluate(ContractController.class);
    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  @DisplayName("ADR 0005 · Un controlador escrito sin la interfaz generada viola la regla")
  void handWrittenControllerFails() {
    EvaluationResult result = evaluate(HandWrittenController.class);
    assertThat(result.hasViolation()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(detail -> detail.contains(HandWrittenController.class.getName()));
  }

  private static EvaluationResult evaluate(Class<?> controller) {
    JavaClasses classes = new ClassFileImporter().importClasses(controller);
    return ContractRules.CONTROLLERS_IMPLEMENT_GENERATED_CONTRACTS.evaluate(classes);
  }

  @RestController
  static class ContractController implements CurrentUserApi {
    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
      return ResponseEntity.ok().build();
    }
  }

  @RestController
  static class HandWrittenController {
    @GetMapping("/api/v1/me")
    ResponseEntity<String> me() {
      return ResponseEntity.ok("hand-written");
    }
  }
}
