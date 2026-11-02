package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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
  @DisplayName("T11 · El valor de X-User-Token no aparece en la salida aunque no sea un JWT")
  void masksTheUserTokenHeader(CapturedOutput output) {
    LOG.warn("prueba de cabecera X-User-Token: valor-secreto-123, siguiente=visible");

    assertThat(output)
        .contains("prueba de cabecera X-User-Token:")
        .contains("siguiente=visible")
        .doesNotContain("valor-secreto-123");
  }
}
