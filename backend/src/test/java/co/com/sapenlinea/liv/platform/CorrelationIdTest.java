package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code X-Correlation-Id} is accepted only as a UUID (plan, log injection): any other value is
 * replaced, so nothing the client sends reaches the logs as is.
 */
@DisplayName("Correlación de las peticiones (T12)")
class CorrelationIdTest extends PlatformWebTest {

  private static final String HEADER = "X-Correlation-Id";
  private static final String PROBE = "/api/v1/probe";

  @Test
  @DisplayName("Doc. 02 §9 · Un UUID recibido se conserva en la respuesta y en los logs")
  void keepsAValidUuid() {
    String id = UUID.randomUUID().toString();
    Map<String, String> headers = bearer(OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A));
    headers.put(HEADER, id);

    HttpResponse<String> response = get(PROBE, headers);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(HEADER)).hasValue(id);
    assertThat(response.body()).contains("\"correlationId\":\"" + id + "\"");
  }

  @Test
  @DisplayName("Doc. 02 §8 · Un valor que no es UUID se reemplaza por uno nuevo")
  void replacesAnythingElse() {
    String injected = "abc ${jndi:x} [ERROR] falso";

    HttpResponse<String> response = get(PROBE, Map.of(HEADER, injected));

    assertThat(response.headers().firstValue(HEADER))
        .hasValueSatisfying(value -> {
          assertThat(value).isNotEqualTo(injected);
          assertThat(UUID.fromString(value)).isNotNull();
        });
  }

  @Test
  @DisplayName("Doc. 02 §9 · Sin cabecera se genera un UUID, también en los rechazos")
  void generatesOne() {
    HttpResponse<String> response = get(PROBE, Map.of());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue(HEADER))
        .hasValueSatisfying(value -> assertThat(UUID.fromString(value)).isNotNull());
  }
}
