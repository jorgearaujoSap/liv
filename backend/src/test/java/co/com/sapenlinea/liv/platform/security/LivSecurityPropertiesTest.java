package co.com.sapenlinea.liv.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Decision of the project lead (plan › Decisiones, T12): the issuer is https outside dev/test. */
@DisplayName("Configuración del emisor (T12)")
class LivSecurityPropertiesTest {

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
  @DisplayName("Doc. 02 §3 · Un emisor http se rechaza si no se permite de forma explícita")
  void httpIssuerNeedsTheFlag() {
    assertThat(validator.validate(properties("http://idp.test/realms/liv", false))).isNotEmpty();
    assertThat(validator.validate(properties("http://localhost:8180/realms/liv", true))).isEmpty();
  }

  @Test
  @DisplayName("Doc. 02 §3 · Un emisor https es válido siempre")
  void httpsIssuer() {
    assertThat(validator.validate(properties("https://idp.test/realms/liv", false))).isEmpty();
  }

  private static LivSecurityProperties properties(String issuer, boolean allowHttpIssuer) {
    return new LivSecurityProperties(
        issuer,
        allowHttpIssuer,
        "liv-api",
        "liv-bff",
        "liv-internal",
        "liv-bff-service",
        new LivSecurityProperties.Claims("administration_id", "roles"));
  }
}
