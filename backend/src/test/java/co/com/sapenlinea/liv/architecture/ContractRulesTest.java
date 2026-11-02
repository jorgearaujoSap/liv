package co.com.sapenlinea.liv.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.architecture.fixture.api.contracts.LookalikeApi;
import co.com.sapenlinea.liv.core.api.contract.CurrentUserApi;
import co.com.sapenlinea.liv.core.api.contract.model.CurrentUser;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The contract rule over the production code, plus fixtures that prove it can fail: only a
 * controller that implements a generated interface and declares no routes of its own passes.
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
    assertThat(evaluate(ContractController.class).hasViolation()).isFalse();
  }

  @Test
  @DisplayName("ADR 0005 · Un controlador escrito sin la interfaz generada viola la regla")
  void handWrittenControllerFails() {
    assertViolation(HandWrittenController.class);
  }

  @Test
  @DisplayName("ADR 0005 · Un @Controller con @ResponseBody también tiene que cumplir la regla")
  void plainControllerWithResponseBodyFails() {
    assertViolation(ResponseBodyController.class);
  }

  @Test
  @DisplayName("ADR 0005 · Una interfaz de un paquete parecido no cuenta como contrato generado")
  void lookalikePackageFails() {
    assertViolation(LookalikeController.class);
  }

  @Test
  @DisplayName("ADR 0005 · Un controlador no agrega rutas propias a las del contrato")
  void extraRouteFails() {
    assertViolation(ExtraRouteController.class);
  }

  @Test
  @DisplayName("ADR 0005 · Un controlador no antepone un prefijo propio a las rutas del contrato")
  void classLevelMappingFails() {
    assertViolation(PrefixedController.class);
  }

  @Test
  @DisplayName("ADR 0005 · Nadie escribe a mano en los paquetes de contratos generados")
  void noHandWrittenCodeInGeneratedPackages() throws IOException {
    try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
      assertThat(files.map(Path::toString).map(p -> p.replace('\\', '/')))
          .noneMatch(p -> p.contains("/api/contract/"));
    }
  }

  private static void assertViolation(Class<?> controller) {
    EvaluationResult result = evaluate(controller);
    assertThat(result.hasViolation()).as(controller.getSimpleName()).isTrue();
    assertThat(result.getFailureReport().getDetails())
        .anyMatch(detail -> detail.contains(controller.getName()));
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

  @Controller
  @ResponseBody
  static class ResponseBodyController {
    ResponseEntity<String> me() {
      return ResponseEntity.ok("plain controller");
    }
  }

  @RestController
  static class LookalikeController implements LookalikeApi {
    @Override
    public ResponseEntity<String> me() {
      return ResponseEntity.ok("lookalike");
    }
  }

  @RestController
  static class ExtraRouteController implements CurrentUserApi {
    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
      return ResponseEntity.ok().build();
    }

    @GetMapping("/api/v1/me/extra")
    ResponseEntity<String> extra() {
      return ResponseEntity.ok("outside the contract");
    }
  }

  @RestController
  @RequestMapping("/v2")
  static class PrefixedController implements CurrentUserApi {
    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
      return ResponseEntity.ok().build();
    }
  }
}
