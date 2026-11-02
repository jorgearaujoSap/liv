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
 * The OpenAPI contracts of the feature 001 match the table "Contratos" of its plan (platform
 * repository, "Datos, API y seguridad") and the decisions of 2026-11-02. The build also validates
 * each file when it generates the interfaces. Paths are relative to backend/, where Maven runs the
 * tests.
 */
@DisplayName("Contratos OpenAPI de la feature 001 (T11)")
class OpenApiContractTest {

  private static final Path CONTRACTS = Path.of("contracts", "openapi");
  private static final List<String> FILES =
      List.of("core.yaml", "platform.yaml", "core-internal.yaml");
  private static final String PROBLEM_JSON = "application/problem+json";

  /** Length of core.user_session.sid (migration V202611010905). */
  private static final int SID_MAX_LENGTH = 100;

  /** Visible ASCII, no spaces or control characters: identifiers that end up in logs. */
  private static final String VISIBLE_ASCII = "^[!-~]+$";

  @Test
  @DisplayName("001-13 · GET /api/v1/me devuelve el usuario, su administración, roles y permisos")
  void currentUser() {
    Map<String, Object> contract = load("core.yaml");
    Map<String, Object> get = operation(contract, "/api/v1/me", "get");
    assertThat(get).containsEntry("operationId", "getCurrentUser");
    assertThat(responseCodes(get)).containsExactlyInAnyOrder("200", "401");
    assertThat(successSchema(get, "200")).isEqualTo("#/components/schemas/CurrentUser");
    Map<String, Object> user = schema(contract, "CurrentUser");
    assertThat(strings(user.get("required")))
        .containsExactlyInAnyOrder(
            "id", "fullName", "email", "administration", "roles", "permissions");
    assertThat(property(user, "id")).containsEntry("format", "uuid");
    assertThat(property(user, "administration"))
        .containsEntry("$ref", "#/components/schemas/AdministrationSummary");
    assertThat(map(property(user, "roles").get("items"))).containsEntry("type", "string");
    assertThat(map(property(user, "permissions").get("items"))).containsEntry("type", "string");
    Map<String, Object> administration = schema(contract, "AdministrationSummary");
    assertThat(strings(administration.get("required"))).containsExactlyInAnyOrder("id", "name");
    assertThat(property(administration, "id")).containsEntry("format", "uuid");
    assertErrorsAreProblems(contract, get);
  }

  @Test
  @DisplayName("001-15 · GET /api/v1/me/capabilities devuelve el árbol de opciones con su estado")
  void capabilities() {
    Map<String, Object> contract = load("platform.yaml");
    Map<String, Object> get = operation(contract, "/api/v1/me/capabilities", "get");
    assertThat(get).containsEntry("operationId", "getCapabilities");
    assertThat(responseCodes(get)).containsExactlyInAnyOrder("200", "401");
    assertThat(successSchema(get, "200")).isEqualTo("#/components/schemas/Capabilities");
    Map<String, Object> capability = schema(contract, "Capability");
    assertThat(strings(capability.get("required")))
        .containsExactlyInAnyOrder("code", "status", "children");
    assertThat(map(property(capability, "children").get("items")))
        .containsEntry("$ref", "#/components/schemas/Capability");
    assertThat(property(capability, "status"))
        .containsEntry("$ref", "#/components/schemas/CapabilityStatus");
    assertThat(strings(schema(contract, "CapabilityStatus").get("enum")))
        .containsExactly("AVAILABLE", "COMING_SOON");
    assertErrorsAreProblems(contract, get);
  }

  @Test
  @DisplayName(
      "ADR 0025 · POST /internal/v1/sessions recibe el sid de la sesión y el token del usuario")
  void startSession() {
    Map<String, Object> contract = load("core-internal.yaml");
    Map<String, Object> post = operation(contract, "/internal/v1/sessions", "post");
    assertThat(post).containsEntry("operationId", "startSession");
    // 201 crea la sesión; 200 es el reintento idempotente con el mismo sid (decisión 2026-11-02).
    assertThat(responseCodes(post)).containsExactlyInAnyOrder("200", "201", "400", "401", "403");
    assertThat(successSchema(post, "201")).isEqualTo("#/components/schemas/StartSessionResponse");
    assertThat(successSchema(post, "200")).isEqualTo("#/components/schemas/StartSessionResponse");
    assertThat(list(post.get("parameters")))
        .anySatisfy(p ->
            assertThat(p)
                .containsEntry("name", "X-User-Token")
                .containsEntry("in", "header")
                .containsEntry("required", true)
                .satisfies(header ->
                    assertThat(map(header.get("schema")))
                        .containsEntry("minLength", 1)
                        .containsEntry("maxLength", 8192)));
    Map<String, Object> request = schema(contract, "StartSessionRequest");
    assertThat(strings(request.get("required"))).containsExactly("sid");
    assertSid(property(request, "sid"));
    // Sin sessionAuditId: la sesión se identifica por su sid (decisión 2026-11-02).
    Map<String, Object> response = schema(contract, "StartSessionResponse");
    assertThat(strings(response.get("required"))).containsExactly("userId");
    assertThat(map(response.get("properties")).keySet()).containsExactly("userId");
    assertThat(property(response, "userId")).containsEntry("format", "uuid");
    assertErrorsAreProblems(contract, post);
  }

  @Test
  @DisplayName(
      "ADR 0025 · POST /internal/v1/sessions/{sid}/end cierra la sesión y siempre responde 204")
  void endSession() {
    Map<String, Object> contract = load("core-internal.yaml");
    Map<String, Object> post = operation(contract, "/internal/v1/sessions/{sid}/end", "post");
    assertThat(post).containsEntry("operationId", "endSession");
    // Un sid inexistente o de otro usuario también responde 204, sin efecto (decisión 2026-11-02).
    assertThat(responseCodes(post)).containsExactlyInAnyOrder("204", "400", "401");
    assertThat(list(post.get("parameters")))
        .singleElement()
        .satisfies(p -> assertSid(map(p.get("schema"))));
    Map<String, Object> request = schema(contract, "EndSessionRequest");
    assertThat(strings(request.get("required")))
        .containsExactlyInAnyOrder("issuer", "subject", "reason");
    // issuer es texto: format uri genera java.net.URI y @Size sobre URI rompe la validación (500).
    assertThat(property(request, "issuer"))
        .containsEntry("type", "string")
        .doesNotContainKey("format")
        .containsEntry("minLength", 1)
        .containsEntry("maxLength", 255)
        .containsEntry("pattern", "^https?://[!-~]+$");
    assertThat(property(request, "subject"))
        .containsEntry("minLength", 1)
        .containsEntry("maxLength", 255)
        .containsEntry("pattern", VISIBLE_ASCII);
    assertThat(strings(schema(contract, "SessionEndReason").get("enum")))
        .containsExactly("LOGOUT", "IDLE", "ABSOLUTE", "PROVIDER");
    assertErrorsAreProblems(contract, post);
  }

  @Test
  @DisplayName(
      "ADR 0025 · Las rutas públicas exigen el token del usuario y las internas el de servicio")
  void securityPerContract() {
    assertSecurity("core.yaml", "bearerAuth", "/api/v1/");
    assertSecurity("platform.yaml", "bearerAuth", "/api/v1/");
    assertSecurity("core-internal.yaml", "serviceToken", "/internal/v1/");
  }

  @Test
  @DisplayName("01 §10 · Los errores son Problem Details iguales en los tres contratos")
  void problemCarriesTheRuleCode() {
    Map<String, Object> first = schema(load(FILES.get(0)), "Problem");
    assertThat(map(first.get("properties")).keySet())
        .contains("type", "title", "status", "detail", "instance", "code");
    assertThat(strings(first.get("required"))).containsExactlyInAnyOrder("status", "code");
    // RFC 9457: instance es una referencia URI (Spring pone la ruta relativa de la petición).
    assertThat(property(first, "instance")).containsEntry("format", "uri-reference");
    for (String file : FILES) {
      assertThat(schema(load(file), "Problem")).as(file).isEqualTo(first);
    }
  }

  @Test
  @DisplayName(
      "ADR 0025 · Los errores internos también se sirven como JSON, para que el cierre no dé 406")
  void internalErrorsAlsoServeJson() {
    Map<String, Object> contract = load("core-internal.yaml");
    for (String name : List.of("BadRequest", "Unauthorized", "SessionRejected")) {
      assertThat(map(response(contract, name).get("content")))
          .as(name)
          .containsOnlyKeys(PROBLEM_JSON, "application/json");
    }
  }

  @Test
  @DisplayName("001-14 · Un sid que no es el del token del usuario es 401 con 001-14, no 403")
  void sidMismatchIsUnauthorized() {
    Map<String, Object> contract = load("core-internal.yaml");
    assertThat((String) response(contract, "Unauthorized").get("description"))
        .contains("001-14")
        .contains("sid");
    assertThat((String) response(contract, "SessionRejected").get("description"))
        .doesNotContain("001-14");
  }

  @Test
  @DisplayName("001-14 · Un usuario sin sesión o no registrado recibe 401 con el código 001-14")
  void unregisteredUserIsNotASessionlessCodeOf00105() {
    for (String file : List.of("core.yaml", "platform.yaml")) {
      String description = (String) response(load(file), "Unauthorized").get("description");
      assertThat(description).as(file).contains("001-14").contains("no registrado");
      assertThat(description.substring(description.indexOf("001-05")))
          .as(file)
          .doesNotContain("no registrado");
    }
  }

  private static void assertSid(Map<String, Object> sid) {
    assertThat(sid)
        .containsEntry("type", "string")
        .containsEntry("minLength", 1)
        .containsEntry("maxLength", SID_MAX_LENGTH)
        .containsEntry("pattern", VISIBLE_ASCII);
  }

  private static void assertSecurity(String file, String scheme, String prefix) {
    Map<String, Object> contract = load(file);
    assertThat(list(contract.get("security"))).as(file).containsExactly(Map.of(scheme, List.of()));
    Map<String, Object> declared =
        map(map(map(contract.get("components")).get("securitySchemes")).get(scheme));
    assertThat(declared).containsEntry("type", "http").containsEntry("scheme", "bearer");
    map(contract.get("paths"))
        .forEach((path, item) -> {
          assertThat(path).as(file).startsWith(prefix);
          // Ninguna operación anula la seguridad del contrato (por ejemplo, security: []).
          map(item).values().forEach(op -> assertThat(map(op)).doesNotContainKey("security"));
        });
  }

  /** ADR 0005 and doc. 01 §10: every 4xx answer is a Problem with the rule code. */
  private static void assertErrorsAreProblems(
      Map<String, Object> contract, Map<String, Object> operation) {
    List<String> errors =
        responseCodes(operation).stream().filter(code -> code.startsWith("4")).toList();
    assertThat(errors).isNotEmpty();
    for (String code : errors) {
      Map<String, Object> resolved =
          resolve(contract, map(map(operation.get("responses")).get(code)));
      assertThat(map(resolved.get("content"))).as("respuesta %s", code).containsKey(PROBLEM_JSON);
      Map<String, Object> media = map(map(resolved.get("content")).get(PROBLEM_JSON));
      assertThat(map(media.get("schema"))).containsEntry("$ref", "#/components/schemas/Problem");
    }
  }

  private static String successSchema(Map<String, Object> operation, String code) {
    Map<String, Object> content =
        map(map(map(operation.get("responses")).get(code)).get("content"));
    return (String) map(map(content.get("application/json")).get("schema")).get("$ref");
  }

  /** Follows a {@code $ref} to components/responses, if the response is one. */
  private static Map<String, Object> resolve(
      Map<String, Object> contract, Map<String, Object> response) {
    Object ref = response.get("$ref");
    if (ref == null) {
      return response;
    }
    return response(contract, ((String) ref).substring("#/components/responses/".length()));
  }

  private static Map<String, Object> response(Map<String, Object> contract, String name) {
    return map(map(map(contract.get("components")).get("responses")).get(name));
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

  private static Map<String, Object> property(Map<String, Object> schema, String name) {
    Map<String, Object> property = map(map(schema.get("properties")).get(name));
    assertThat(property).as("propiedad %s", name).isNotNull();
    return property;
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
