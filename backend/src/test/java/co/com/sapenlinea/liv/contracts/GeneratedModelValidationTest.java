package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.core.api.contract.internal.model.EndSessionRequest;
import co.com.sapenlinea.liv.core.api.contract.internal.model.SessionEndReason;
import co.com.sapenlinea.liv.core.api.contract.internal.model.StartSessionRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bean validation that openapi-generator writes on the models works with the validator that
 * Spring MVC uses: a valid body passes and an invalid one gives violations (a 400 in the API),
 * never an exception (a 500).
 */
@DisplayName("Validación de los modelos generados (T11)")
class GeneratedModelValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void createValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @Test
  @DisplayName("ADR 0025 · Un cierre de sesión válido no tiene violaciones")
  void validEndSession() {
    EndSessionRequest request =
        new EndSessionRequest(
            "http://localhost:8180/realms/liv",
            "6f1c0b0e-2d4b-4c1e-9b1a-0d7e5f3a2c11",
            SessionEndReason.LOGOUT);
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  @DisplayName("ADR 0025 · Un cierre con emisor o sujeto inválidos da violaciones, no un error")
  void invalidEndSession() {
    EndSessionRequest request =
        new EndSessionRequest("no es una url", "con espacios", SessionEndReason.IDLE);
    assertThat(validator.validate(request))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactlyInAnyOrder("issuer", "subject");
  }

  @Test
  @DisplayName("ADR 0025 · Un sid vacío o de más de 100 caracteres da violaciones")
  void invalidSid() {
    assertThat(validator.validate(new StartSessionRequest(""))).isNotEmpty();
    assertThat(validator.validate(new StartSessionRequest("a".repeat(101)))).isNotEmpty();
    assertThat(validator.validate(new StartSessionRequest("a".repeat(100)))).isEmpty();
  }
}
