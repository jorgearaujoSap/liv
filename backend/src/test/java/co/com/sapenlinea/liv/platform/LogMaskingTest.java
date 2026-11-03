package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;

/**
 * Tokens are credentials (doc. 02 §6): the logging configuration masks them in every message, even
 * if some code logs a header or a token by mistake.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Enmascarado de credenciales en los logs (T12)")
class LogMaskingTest extends PlatformWebTest {

  private static final Logger LOG = LoggerFactory.getLogger(LogMaskingTest.class);

  @Test
  @DisplayName("Doc. 02 §6 · Un JWT en un mensaje de log no aparece en la salida")
  void masksJwts(CapturedOutput output) {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    String signature = token.substring(token.lastIndexOf('.') + 1);

    LOG.warn("prueba de enmascarado Authorization: Bearer {}", token);

    assertThat(output).contains("prueba de enmascarado").doesNotContain(signature);
    assertThat(output).doesNotContain(token.substring(0, token.indexOf('.')));
  }

  @Test
  @DisplayName("Doc. 02 §6 · Un JWT en el mensaje de una excepción no aparece en la traza")
  void masksExceptionMessages(CapturedOutput output) {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    String signature = token.substring(token.lastIndexOf('.') + 1);

    LOG.warn("prueba de traza", new IllegalStateException("token rechazado " + token));

    assertThat(output).contains("token rechazado").doesNotContain(signature);
  }

  @Test
  @DisplayName("Doc. 02 §6 · Las credenciales opacas, entre comillas o en Bearer no aparecen")
  void masksOpaqueAndQuotedCredentials(CapturedOutput output) {
    LOG.warn(
        "prueba de opacos {\"X-User-Token\": \"opaco-json-1\"} x-user-token:\"opaco-cabecera-2\""
            + " Authorization: Bearer opaco-bearer-3 client_secret=opaco-secreto-4"
            + " Authorization: Basic opaco-basic-5 Authorization: DPoP opaco-dpop-6"
            + " {\\\"access_token\\\":\\\"opaco-escapado-7\\\"}");

    assertThat(output)
        .contains("prueba de opacos")
        .doesNotContain("opaco-json-1")
        .doesNotContain("opaco-cabecera-2")
        .doesNotContain("opaco-bearer-3")
        .doesNotContain("opaco-secreto-4")
        .doesNotContain("opaco-basic-5")
        .doesNotContain("opaco-dpop-6")
        .doesNotContain("opaco-escapado-7");
  }

  @Test
  @DisplayName("Doc. 02 §6 · El log estructurado sigue apagado mientras no enmascare")
  void structuredLoggingStaysOff(@Autowired Environment environment) {
    // Spring Boot's JSON formats do not use %m: turning them on needs masking there first (F0-09).
    assertThat(environment.getProperty("logging.structured.format.console")).isNull();
    assertThat(environment.getProperty("logging.structured.format.file")).isNull();
  }

  @Test
  @DisplayName("T11 · El valor de X-User-Token no aparece en la salida aunque no sea un JWT")
  void masksTheUserTokenHeader(CapturedOutput output) {
    LOG.warn("prueba de cabecera X-User-Token: valor-secreto-123, siguiente=visible");

    assertThat(output)
        .contains("prueba de cabecera X-User-Token:")
        .contains("siguiente=visible")
        .doesNotContain("valor-secreto-123");
  }
}
