package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The OpenAPI contracts of the feature 001 match the table "Contratos" of its plan
 * (spec/features/001-inicio-de-sesion/plan.md, "Datos, API y seguridad"). The build also validates
 * each file when it generates the interfaces. Paths are relative to backend/, where Maven runs the
 * tests.
 */
@DisplayName("Contratos OpenAPI de la feature 001 (T11)")
class OpenApiContractTest {

  private static final Path CONTRACTS = Path.of("contracts", "openapi");
  private static final String PROBLEM_JSON = "application/problem+json";

  @Test
  @DisplayName("001-02 · GET /api/v1/me devuelve el usuario, su administración, roles y permisos")
  void currentUser() {
    Map<String, Object> contract = load("core.yaml");
    Map<String, Object> get = operation(contract, "/api/v1/me", "get");
    assertThat(get).containsEntry("operationId", "getCurrentUser");
    assertThat(responseCodes(get)).containsExactlyInAnyOrder("200", "401");
    Map<String, Object> user = schema(contract, "CurrentUser");
    assertThat(strings(user.get("required")))
        .containsExactlyInAnyOrder(
            "id", "fullName", "email", "administration", "roles", "permissions");
    assertThat(strings(schema(contract, "AdministrationSummary").get("required")))
        .containsExactlyInAnyOrder("id", "name");
    assertErrorsAreProblems(contract, get);
  }

  @Test
  @DisplayName("001-15 · GET /api/v1/me/capabilities devuelve el árbol de opciones con su estado")
  void capabilities() {
    Map<String, Object> contract = load("platform.yaml");
    Map<String, Object> get = operation(contract, "/api/v1/me/capabilities", "get");
    assertThat(get).containsEntry("operationId", "getCapabilities");
    assertThat(responseCodes(get)).containsExactlyInAnyOrder("200", "401");
    Map<String, Object> capability = schema(contract, "Capability");
    assertThat(strings(capability.get("required")))
        .containsExactlyInAnyOrder("code", "status", "children");
    assertThat(strings(schema(contract, "CapabilityStatus").get("enum")))
        .containsExactly("AVAILABLE", "COMING_SOON");
    assertErrorsAreProblems(contract, get);
  }

  @Test
  @DisplayName("ADR 0025 · POST /internal/v1/sessions exige el token de servicio y el del usuario")
  void startSession() {
    Map<String, Object> contract = load("core-internal.yaml");
    Map<String, Object> post = operation(contract, "/internal/v1/sessions", "post");
    assertThat(post).containsEntry("operationId", "startSession");
    assertThat(responseCodes(post)).containsExactlyInAnyOrder("201", "401", "403");
    assertThat(list(post.get("parameters")))
        .anySatisfy(p ->
            assertThat(p)
                .containsEntry("name", "X-User-Token")
                .containsEntry("in", "header")
                .containsEntry("required", true));
    assertThat(strings(schema(contract, "StartSessionRequest").get("required")))
        .containsExactly("sid");
    assertThat(strings(schema(contract, "StartSessionResponse").get("required")))
        .containsExactlyInAnyOrder("userId", "sessionAuditId");
    assertErrorsAreProblems(contract, post);
  }

  @Test
  @DisplayName("ADR 0025 · POST /internal/v1/sessions/{sid}/end cierra la sesión con su motivo")
  void endSession() {
    Map<String, Object> contract = load("core-internal.yaml");
    Map<String, Object> post = operation(contract, "/internal/v1/sessions/{sid}/end", "post");
    assertThat(post).containsEntry("operationId", "endSession");
    assertThat(responseCodes(post)).containsExactlyInAnyOrder("204", "401", "403");
    assertThat(strings(schema(contract, "EndSessionRequest").get("required")))
        .containsExactlyInAnyOrder("issuer", "subject", "reason");
    assertThat(strings(schema(contract, "SessionEndReason").get("enum")))
        .containsExactly("LOGOUT", "IDLE", "ABSOLUTE", "PROVIDER");
    assertErrorsAreProblems(contract, post);
  }

  @Test
  @DisplayName(
      "ADR 0025 · Las rutas internas solo están en core-internal.yaml y las públicas usan /api/v1")
  void internalAndPublicPathsAreSeparated() {
    assertThat(paths("core.yaml")).allMatch(p -> p.startsWith("/api/v1/"));
    assertThat(paths("platform.yaml")).allMatch(p -> p.startsWith("/api/v1/"));
    assertThat(paths("core-internal.yaml")).allMatch(p -> p.startsWith("/internal/v1/"));
  }

  @Test
  @DisplayName("01 §10 · Los errores son Problem Details con el código de la regla")
  void problemCarriesTheRuleCode() {
    for (String file : List.of("core.yaml", "platform.yaml", "core-internal.yaml")) {
      Map<String, Object> problem = schema(load(file), "Problem");
      assertThat(map(problem.get("properties")).keySet())
          .as(file)
          .contains("type", "title", "status", "detail", "code");
      assertThat(strings(problem.get("required"))).as(file).contains("status", "code");
    }
  }

  private static void assertErrorsAreProblems(
      Map<String, Object> contract, Map<String, Object> operation) {
    map(operation.get("responses"))
        .forEach((code, response) -> {
          if (code.startsWith("4")) {
            Map<String, Object> resolved = resolve(contract, map(response));
            assertThat(map(resolved.get("content")))
                .as("respuesta %s", code)
                .containsOnlyKeys(PROBLEM_JSON);
            Map<String, Object> media = map(map(resolved.get("content")).get(PROBLEM_JSON));
            assertThat(map(media.get("schema")))
                .containsEntry("$ref", "#/components/schemas/Problem");
          }
        });
  }

  /** Follows a {@code $ref} to components/responses, if the response is one. */
  private static Map<String, Object> resolve(
      Map<String, Object> contract, Map<String, Object> response) {
    Object ref = response.get("$ref");
    if (ref == null) {
      return response;
    }
    String name = ((String) ref).substring("#/components/responses/".length());
    return map(map(map(contract.get("components")).get("responses")).get(name));
  }

  private static List<String> paths(String file) {
    return List.copyOf(map(load(file).get("paths")).keySet());
  }

  private static Map<String, Object> operation(
      Map<String, Object> contract, String path, String method) {
    Map<String, Object> item = map(map(contract.get("paths")).get(path));
    assertThat(item).as("ruta %s", path).isNotNull();
    return map(item.get(method));
  }

  private static List<String> responseCodes(Map<String, Object> operation) {
    return List.copyOf(map(operation.get("responses")).keySet());
  }

  private static Map<String, Object> schema(Map<String, Object> contract, String name) {
    Map<String, Object> schema = map(map(map(contract.get("components")).get("schemas")).get(name));
    assertThat(schema).as("esquema %s", name).isNotNull();
    return schema;
  }

  private static Map<String, Object> load(String file) {
    try {
      return new Yaml().load(Files.readString(CONTRACTS.resolve(file), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new AssertionError("no se pudo leer el contrato " + file, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    return (List<Map<String, Object>>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<String> strings(Object value) {
    return (List<String>) value;
  }
}
