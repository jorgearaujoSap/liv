package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * With the production configuration and a dependency down, a request inside the security filters
 * gets a 503 Problem Details (doc. 02 §8): the failure never reaches the container.
 */
@DisplayName("Dependencias caídas (T12)")
class UnavailableDependenciesTest {

  private static final String CLOSED = "localhost:1";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Nested
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @DisplayName("Base de datos caída")
  class DatabaseDown {

    @LocalServerPort int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
      registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + CLOSED + "/liv");
      registry.add("spring.datasource.password", () -> "sin-uso");
      registry.add("liv.security.issuer-uri", OidcTestProvider::issuer);
    }

    @Test
    @DisplayName("Doc. 02 §8 · Si el registro de usuarios no responde, /api responde 503")
    void userDirectoryDown() {
      String token = OidcTestProvider.userToken("cualquiera", LivDatabase.ADMIN_A);

      assertUnavailable(get(port, "/api/v1/me", Map.of("Authorization", "Bearer " + token)));
    }

    @Test
    @DisplayName("Doc. 03 · Sin base de datos la instancia no queda lista para recibir tráfico")
    void notReadyWithoutDatabase() {
      assertThat(get(port, "/actuator/health/readiness", Map.of()).statusCode()).isEqualTo(503);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
      properties = {
        "spring.datasource.url=jdbc:postgresql://" + CLOSED + "/liv",
        "spring.datasource.password=sin-uso",
        "liv.security.issuer-uri=http://" + CLOSED + "/realms/liv"
      })
  @DisplayName("Proveedor de identidad caído")
  class ProviderDown {

    @LocalServerPort int port;

    @Test
    @DisplayName("Doc. 02 §8 · Si el proveedor no responde, /api responde 503 y no 401")
    void providerDownOnApi() {
      String token = OidcTestProvider.userToken("cualquiera", LivDatabase.ADMIN_A);

      assertUnavailable(get(port, "/api/v1/me", Map.of("Authorization", "Bearer " + token)));
    }
  }

  private static HttpResponse<String> get(int port, String path, Map<String, String> headers) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20));
    headers.forEach(request::header);
    try {
      return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static void assertUnavailable(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("code").asString()).isEqualTo(ErrorCodes.SERVICE_UNAVAILABLE);
  }
}
