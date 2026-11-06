package co.com.sapenlinea.liv.bff.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.web.CsrfRejection;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The contract of the BFF with the frontend, written before the code (ADR 0005): the frontend keeps
 * a pinned copy of {@code contracts/openapi/bff.yaml} and generates its client from it (T20). The
 * only paths are the BFF's own; {@code /api/**} is the API's contract and {@code /internal/**} is
 * not published to the browser.
 */
@Tag("001-20")
class BffContractTest {

  private static final Path CONTRACT = Path.of("contracts", "openapi", "bff.yaml");

  @SuppressWarnings("unchecked")
  private static Map<String, Object> contract() throws IOException {
    try (Reader reader = Files.newBufferedReader(CONTRACT, StandardCharsets.UTF_8)) {
      return new Yaml().load(reader);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> operation(String path, String method) throws IOException {
    Map<String, Object> paths = (Map<String, Object>) contract().get("paths");
    return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
  }

  @SuppressWarnings("unchecked")
  private static java.util.Set<String> statuses(String path, String method) throws IOException {
    Map<String, Object> responses = (Map<String, Object>) operation(path, method).get("responses");
    return new TreeSet<>(responses.keySet());
  }

  @Test
  @DisplayName(
      "001-20 · El contrato es OpenAPI 3.0, con versión X.Y.Z, y solo publica las rutas propias del BFF")
  @SuppressWarnings("unchecked")
  void onlyTheOwnRoutesArePublished() throws IOException {
    Map<String, Object> contract = contract();

    assertThat((String) contract.get("openapi")).startsWith("3.0.");
    assertThat((String) ((Map<String, Object>) contract.get("info")).get("version"))
        .matches("\\d+\\.\\d+\\.\\d+");
    assertThat(((Map<String, Object>) contract.get("paths")).keySet())
        .containsExactlyInAnyOrder(
            "/bff/login", "/bff/logout", "/bff/session", "/bff/session/keepalive");
  }

  @Test
  @DisplayName("001-20 · Cada ruta declara su método y las respuestas que el BFF da")
  void eachRouteDeclaresItsAnswers() throws IOException {
    assertThat(statuses("/bff/login", "get")).containsExactly("302");
    assertThat(statuses("/bff/logout", "post")).containsExactly("200", "403");
    assertThat(statuses("/bff/session", "get")).containsExactly("200", "401");
    assertThat(statuses("/bff/session/keepalive", "post"))
        .containsExactly("200", "401", "403", "502");
  }

  @Test
  @Tag("001-12")
  @DisplayName(
      "001-12 · El rechazo anti-CSRF fija code 001-12, también en /api/**, para que el frontend lo distinga")
  @SuppressWarnings("unchecked")
  void theCsrfRefusalIsDeclaredWithItsCode() throws IOException {
    Map<String, Object> contract = contract();
    Map<String, Object> components = (Map<String, Object>) contract.get("components");
    Map<String, Object> refusal =
        (Map<String, Object>)
            ((Map<String, Object>) components.get("responses")).get("CsrfRejected");
    Map<String, Object> media =
        (Map<String, Object>)
            ((Map<String, Object>) refusal.get("content")).get("application/problem+json");
    assertThat(((Map<String, Object>) media.get("schema")).get("$ref"))
        .isEqualTo("#/components/schemas/CsrfProblem");

    Map<String, Object> problem =
        (Map<String, Object>) ((Map<String, Object>) components.get("schemas")).get("CsrfProblem");
    Map<String, Object> properties = (Map<String, Object>) problem.get("properties");
    assertThat(problem.get("required")).isEqualTo(java.util.List.of("status", "code"));
    // The code the contract shows is the one the BFF writes. An enum would be the strict way, but
    // openapi-diff takes adding one to a response as an incompatible change (ADR 0027).
    assertThat(((Map<String, Object>) properties.get("code")).get("example"))
        .isEqualTo(CsrfRejection.CODE);

    // Every 403 of the BFF's own routes is this refusal.
    Map<String, Object> paths = (Map<String, Object>) contract.get("paths");
    for (Map.Entry<String, Object> path : paths.entrySet()) {
      for (Object operation : ((Map<String, Object>) path.getValue()).values()) {
        Map<String, Object> responses =
            (Map<String, Object>) ((Map<String, Object>) operation).get("responses");
        Object forbidden = responses.get("403");
        if (forbidden != null) {
          assertThat(((Map<String, Object>) forbidden).get("$ref"))
              .as(path.getKey())
              .isEqualTo("#/components/responses/CsrfRejected");
        }
      }
    }
  }

  @Test
  @DisplayName("001-20 · Cada operación tiene operationId, para el cliente que genera el frontend")
  @SuppressWarnings("unchecked")
  void everyOperationHasAnId() throws IOException {
    for (String[] route :
        new String[][] {
          {"/bff/login", "get"},
          {"/bff/logout", "post"},
          {"/bff/session", "get"},
          {"/bff/session/keepalive", "post"}
        }) {
      assertThat(operation(route[0], route[1]).get("operationId"))
          .as(route[0])
          .isInstanceOf(String.class);
    }
  }
}
