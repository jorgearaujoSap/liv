package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every error is Problem Details with a code and without internal details; the technical detail
 * goes to the log with the correlation id (doc. 02 §8, plan step 7).
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(ErrorResponsesTest.BusinessErrorProbe.class)
@DisplayName("Respuestas de error y su registro (T12)")
class ErrorResponsesTest extends PlatformWebTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  @DisplayName(
      "Doc. 02 §8 · Una falla inesperada es un 500 sin detalles, registrado con la correlación")
  void unexpectedFailure(CapturedOutput output) {
    String correlationId = UUID.randomUUID().toString();
    HttpResponse<String> response = get("/api/v1/probe/failure", user(correlationId));

    assertProblem(response, 500, ErrorCodes.INTERNAL_ERROR);
    assertThat(response.body()).doesNotContain("detalle interno");
    assertThat(output).contains("detalle interno de la falla").contains(correlationId);
  }

  @Test
  @DisplayName("Doc. 02 §8 · Una dependencia caída es un 503 sin detalles")
  void unavailableDependency(CapturedOutput output) {
    String correlationId = UUID.randomUUID().toString();
    HttpResponse<String> response = get("/api/v1/probe/unavailable", user(correlationId));

    assertProblem(response, 503, ErrorCodes.SERVICE_UNAVAILABLE);
    assertThat(response.body()).doesNotContain("base de datos");
    assertThat(output).contains("base de datos caída").contains(correlationId);
  }

  @Test
  @DisplayName("Doc. 02 §5.1 · Una violación de integridad es un 500, no una dependencia caída")
  void integrityViolationIsNotUnavailable(CapturedOutput output) {
    String correlationId = UUID.randomUUID().toString();
    HttpResponse<String> response = get("/api/v1/probe/integrity", user(correlationId));

    assertProblem(response, 500, ErrorCodes.INTERNAL_ERROR);
    assertThat(output).contains("violación de la FK").doesNotContain("Dependency unavailable");
  }

  @Test
  @DisplayName("Doc. 02 §8 · Una ruta inexistente es un 404 con Problem Details")
  void unknownRoute() {
    HttpResponse<String> response = get("/api/v1/no-existe", user(UUID.randomUUID().toString()));

    assertProblem(response, 404, ErrorCodes.NOT_FOUND);
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un rechazo por otra administración queda en el log sin el token")
  void rejectionIsLogged(CapturedOutput output) {
    String correlationId = UUID.randomUUID().toString();
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_B);
    Map<String, String> headers = bearer(token);
    headers.put("X-Correlation-Id", correlationId);

    assertThat(get("/api/v1/probe", headers).statusCode()).isEqualTo(401);

    String line =
        output.getAll().lines().filter(l -> l.contains(correlationId)).findFirst().orElse("");
    assertThat(line).contains(ErrorCodes.ADMINISTRATION_MISMATCH);
    assertThat(line).containsPattern("user [0-9a-f]{8}-[0-9a-f]{4}-");
    assertThat(output).doesNotContain(token.substring(token.lastIndexOf('.') + 1));
  }

  @Test
  @DisplayName("ADR 0035 · Una regla de tipo conflicto es un 409 con su código y sus extensiones")
  void ruleConflictIs409() {
    HttpResponse<String> response = get("/api/v1/probe/rule/conflict", user("c-409"));

    assertProblem(response, 409, "V-01");
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("year").asInt()).isEqualTo(2026);
    assertThat(body.get("status").asInt()).isEqualTo(409);
  }

  @Test
  @DisplayName(
      "ADR 0035 · Una regla de tipo no procesable es un 422 con su código y sus extensiones")
  void ruleUnprocessableIs422() {
    HttpResponse<String> response = get("/api/v1/probe/rule/unprocessable", user("c-422"));

    assertProblem(response, 422, "V-02");
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("reason").asString()).isEqualTo("GAP");
    assertThat(body.get("requiredYear").asInt()).isEqualTo(2027);
  }

  @Test
  @DisplayName(
      "ADR 0035 · Un rechazo de regla se registra con su código, sin valores y sin nivel de falla")
  void ruleViolationIsLoggedWithoutItsValues(CapturedOutput output) {
    String correlationId = UUID.randomUUID().toString();

    get("/api/v1/probe/rule/conflict", user(correlationId));

    String line =
        output.getAll().lines().filter(l -> l.contains("Rule V-01")).findFirst().orElse("");
    assertThat(line).contains(" INFO ").contains(correlationId).contains("[year]");
    // The values of the details may become personal data in later rules: only their names are
    // logged.
    assertThat(line).doesNotContain("year=").doesNotContain("=2026");
    assertThat(output).doesNotContain("Unexpected error");
  }

  @Test
  @Tag("CA-12")
  @DisplayName("CA-12 · «No existe» es un 404 SYS-404 con el mismo cuerpo sea cual sea el motivo")
  void notFoundIsTheSameWhateverTheReason() {
    HttpResponse<String> missing = get("/api/v1/probe/not-found?reason=missing", user("c-404a"));
    HttpResponse<String> foreign = get("/api/v1/probe/not-found?reason=foreign", user("c-404b"));

    assertProblem(missing, 404, ErrorCodes.NOT_FOUND);
    assertProblem(foreign, 404, ErrorCodes.NOT_FOUND);
    assertThat(foreign.body()).isEqualTo(missing.body());
    assertThat(missing.body()).doesNotContain("missing").doesNotContain("foreign");
  }

  private static Map<String, String> user(String correlationId) {
    Map<String, String> headers = bearer(OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A));
    headers.put("X-Correlation-Id", correlationId);
    return headers;
  }

  private static void assertProblem(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("code").asString()).isEqualTo(code);
    assertThat(body.has("detail")).isFalse();
    assertThat(body.has("trace")).isFalse();
  }

  /** Business errors the way a use case throws them, for the handler to translate. */
  @RestController
  static class BusinessErrorProbe {

    @GetMapping("/api/v1/probe/rule/conflict")
    Map<String, String> conflict() {
      throw new RuleViolation("V-01", RuleViolation.Type.CONFLICT, Map.of("year", 2026));
    }

    @GetMapping("/api/v1/probe/rule/unprocessable")
    Map<String, String> unprocessable() {
      throw new RuleViolation(
          "V-02", RuleViolation.Type.UNPROCESSABLE, Map.of("reason", "GAP", "requiredYear", 2027));
    }

    /** The same route for both reasons: the body must not tell them apart. */
    @GetMapping("/api/v1/probe/not-found")
    Map<String, String> notFound(@RequestParam String reason) {
      throw new NotFound("fiscal year, reason " + reason);
    }
  }
}
