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
      List.of("core.yaml", "platform.yaml", "core-internal.yaml", "tax.yaml");
  private static final String YEARS = "/api/v1/fiscal-years";
  private static final String PROBLEM_REF = "#/components/schemas/Problem";
  private static final String FISCAL_YEAR_REF = "#/components/schemas/FiscalYear";
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
    assertSecurity("tax.yaml", "bearerAuth", "/api/v1/");
  }

  @Test
  @DisplayName("007-01 · GET /api/v1/fiscal-years lista las vigencias, más reciente primero")
  void listFiscalYears() {
    Map<String, Object> contract = load("tax.yaml");
    Map<String, Object> get = operation(contract, YEARS, "get");
    assertThat(get).containsEntry("operationId", "listFiscalYears");
    assertThat(responseCodes(get)).containsExactlyInAnyOrder("200", "401", "403");
    assertThat(successSchema(get, "200")).isEqualTo("#/components/schemas/FiscalYearList");
    Map<String, Object> list = schema(contract, "FiscalYearList");
    // createBlocked solo viene con permiso y sin años disponibles: no es obligatorio.
    assertThat(strings(list.get("required")))
        .containsExactlyInAnyOrder("items", "canCreate", "creatableYears");
    assertThat(map(property(list, "items").get("items"))).containsEntry("$ref", FISCAL_YEAR_REF);
    assertThat(property(list, "canCreate")).containsEntry("type", "boolean");
    assertThat(property(list, "createBlocked"))
        .containsEntry("$ref", "#/components/schemas/CreateBlocked");
    assertThat(map(property(list, "creatableYears").get("items")))
        .containsEntry("$ref", "#/components/schemas/CreatableYear");
    assertThat(strings(schema(contract, "FiscalYear").get("required")))
        .containsExactlyInAnyOrder("year", "startsOn", "endsOn", "status", "assessmentsEnabled");
    assertErrorsAreProblems(contract, get);
  }

  @Test
  @DisplayName(
      "007-03 · creatableYears trae cada año con sus fechas y su relación con el año en curso")
  void creatableYearsAndCreateBlocked() {
    Map<String, Object> contract = load("tax.yaml");
    Map<String, Object> creatable = schema(contract, "CreatableYear");
    assertThat(strings(creatable.get("required")))
        .containsExactlyInAnyOrder("year", "startsOn", "endsOn", "relation");
    assertOpenString(property(creatable, "relation"), "PAST", "CURRENT", "NEXT");
    assertThat(property(creatable, "startsOn")).containsEntry("format", "date");
    assertThat(property(creatable, "endsOn")).containsEntry("format", "date");
    Map<String, Object> blocked = schema(contract, "CreateBlocked");
    assertThat(strings(blocked.get("required"))).containsExactlyInAnyOrder("reason", "maxYear");
    assertOpenString(property(blocked, "reason"), "FUTURE_LIMIT");
  }

  @Test
  @DisplayName(
      "V-01 · El año de una vigencia es un entero de 1999 a 2100, en el cuerpo y en la ruta")
  void yearIsBounded() {
    Map<String, Object> contract = load("tax.yaml");
    assertYearRange(property(schema(contract, "FiscalYear"), "year"));
    assertYearRange(property(schema(contract, "CreatableYear"), "year"));
    assertYearRange(property(schema(contract, "CreateFiscalYearRequest"), "year"));
    List<Map<String, Object>> byYear =
        List.of(
            operation(contract, YEARS + "/{year}", "get"),
            operation(contract, YEARS + "/{year}/preparation", "post"));
    for (Map<String, Object> operation : byYear) {
      assertThat(list(operation.get("parameters")))
          .singleElement()
          .satisfies(p -> {
            assertThat(p).containsEntry("name", "year").containsEntry("in", "path");
            assertYearRange(map(p.get("schema")));
          });
    }
  }

  @Test
  @DisplayName("007-11 · GET /api/v1/fiscal-years/{year} devuelve la vigencia con su lista")
  void getFiscalYear() {
    Map<String, Object> contract = load("tax.yaml");
    Map<String, Object> get = operation(contract, YEARS + "/{year}", "get");
    assertThat(get).containsEntry("operationId", "getFiscalYear");
    assertThat(responseCodes(get)).containsExactlyInAnyOrder("200", "400", "401", "403", "404");
    assertThat(successSchema(get, "200")).isEqualTo("#/components/schemas/FiscalYearDetail");
    assertSysNotFound(contract, get);
    Map<String, Object> detail = schema(contract, "FiscalYearDetail");
    assertThat(strings(detail.get("required")))
        .containsExactlyInAnyOrder(
            "year",
            "startsOn",
            "endsOn",
            "status",
            "assessmentsEnabled",
            "availableActions",
            "preparation");
    assertThat(map(property(detail, "availableActions").get("items")))
        .containsEntry("type", "string");
    assertOpenString(property(detail, "availableActions"), "OPEN_PREPARATION");
    Map<String, Object> preparation = schema(contract, "Preparation");
    assertThat(strings(preparation.get("required")))
        .containsExactlyInAnyOrder("items", "canEnable", "missing");
    assertThat(map(property(preparation, "items").get("items")))
        .containsEntry("$ref", "#/components/schemas/PreparationItem");
    assertThat(map(property(preparation, "missing").get("items"))).containsEntry("type", "integer");
    assertErrorsAreProblems(contract, get);
  }

  @Test
  @DisplayName("007-11 · Cada punto de la lista trae número, code, obligatoriedad, opción y estado")
  void preparationItem() {
    Map<String, Object> contract = load("tax.yaml");
    Map<String, Object> item = schema(contract, "PreparationItem");
    assertThat(strings(item.get("required")))
        .containsExactlyInAnyOrder("number", "code", "required", "option", "status", "reason");
    assertThat(property(item, "required")).containsEntry("type", "boolean");
    assertOpenString(
        property(item, "code"),
        "FISCAL_YEAR_IN_PREPARATION",
        "VALUATION_RESOLUTION",
        "VALUATION_CATALOG_PUBLISHED",
        "TAX_RATES",
        "TAX_CALENDAR",
        "TAX_BENEFITS",
        "PENALTY_RULES",
        "SAMPLE_ASSESSMENT",
        "JURISDICTION_CONFIG_PUBLISHED");
    assertOpenString(
        property(item, "reason"), "MET", "FISCAL_YEAR_PLANNED", "OPTION_NOT_AVAILABLE");
    assertThat(strings(schema(contract, "PreparationItemStatus").get("enum")))
        .containsExactly("MET", "PENDING", "OBSERVED");
    assertThat(strings(schema(contract, "FiscalYearStatus").get("enum")))
        .containsExactly("PLANEADA", "EN_PREPARACION", "LISTA", "HABILITADA", "CERRADA");
  }

  @Test
  @DisplayName(
      "007-04 · POST /api/v1/fiscal-years crea la vigencia y rechaza con V-01, V-02 y V-05")
  void createFiscalYear() {
    Map<String, Object> contract = load("tax.yaml");
    Map<String, Object> post = operation(contract, YEARS, "post");
    assertThat(post).containsEntry("operationId", "createFiscalYear");
    assertThat(responseCodes(post))
        .containsExactlyInAnyOrder("201", "400", "401", "403", "409", "422");
    assertThat(successSchema(post, "201")).isEqualTo(FISCAL_YEAR_REF);
    assertThat(strings(schema(contract, "CreateFiscalYearRequest").get("required")))
        .containsExactly("year");
    assertThat(post).containsKey("requestBody");
    assertRejection(contract, post, "409", "V-01", "year");
    assertRejection(
        contract,
        post,
        "422",
        "V-02",
        "V-05",
        "reason",
        "requiredYear",
        "oldestYear",
        "currentYear",
        "nextYear",
        "maxYear");
    assertOpenString(
        property(schema(contract, "CreationRejectionExtensions"), "reason"),
        "GAP",
        "HISTORICAL",
        "FIRST_YEAR",
        "FUTURE_LIMIT");
    assertErrorsAreProblems(contract, post);
  }

  @Test
  @DisplayName(
      "007-10 · POST /api/v1/fiscal-years/{year}/preparation abre la preparación o da V-06")
  void openFiscalYearPreparation() {
    Map<String, Object> contract = load("tax.yaml");
    Map<String, Object> post = operation(contract, YEARS + "/{year}/preparation", "post");
    assertThat(post).containsEntry("operationId", "openFiscalYearPreparation");
    assertThat(post).doesNotContainKey("requestBody");
    assertThat(responseCodes(post))
        .containsExactlyInAnyOrder("200", "400", "401", "403", "404", "409");
    assertThat(successSchema(post, "200")).isEqualTo(FISCAL_YEAR_REF);
    assertSysNotFound(contract, post);
    assertRejection(contract, post, "409", "V-06", "year", "status");
    assertErrorsAreProblems(contract, post);
  }

  @Test
  @DisplayName("007-12 · Ninguna operación habilita la liquidación: solo hay las cuatro de la 007")
  void noOperationEnablesAssessments() {
    Map<String, Object> contract = load("tax.yaml");
    assertThat(map(contract.get("paths")).keySet())
        .containsExactlyInAnyOrder(YEARS, YEARS + "/{year}", YEARS + "/{year}/preparation");
    List<Object> operationIds =
        map(contract.get("paths")).values().stream()
            .flatMap(item -> map(item).values().stream())
            .map(op -> map(op).get("operationId"))
            .toList();
    assertThat(operationIds)
        .containsExactlyInAnyOrder(
            "listFiscalYears", "getFiscalYear", "createFiscalYear", "openFiscalYearPreparation");
    // assessmentsEnabled solo se lee: ningún cuerpo de petición lo recibe.
    assertThat(map(schema(contract, "CreateFiscalYearRequest").get("properties")).keySet())
        .containsExactly("year");
  }

  @Test
  @DisplayName("01 §10 · Los errores son Problem Details iguales en los cuatro contratos")
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

  /**
   * A string that may grow without breaking the contract (openapi-diff, Q08, flags a new value of
   * an enum): no enum, and the documented values are in its description.
   */
  private static void assertOpenString(Map<String, Object> schema, String... documented) {
    Map<String, Object> value =
        "array".equals(schema.get("type")) ? map(schema.get("items")) : schema;
    String description = (String) schema.get("description") + value.get("description");
    assertThat(value).containsEntry("type", "string").doesNotContainKey("enum");
    assertThat(value).containsKey("maxLength");
    assertThat(description).contains(documented);
  }

  private static void assertYearRange(Map<String, Object> year) {
    assertThat(year)
        .containsEntry("type", "integer")
        .containsEntry("minimum", 1999)
        .containsEntry("maximum", 2100);
  }

  private static void assertSysNotFound(
      Map<String, Object> contract, Map<String, Object> operation) {
    Map<String, Object> notFound =
        resolve(contract, map(map(operation.get("responses")).get("404")));
    assertThat((String) notFound.get("description")).contains("SYS-404");
  }

  /**
   * A rejection of a business rule: its response names the rule codes and extends the Problem with
   * the given members (the years the screen quotes). Codes start with "V-"; the rest are members.
   */
  private static void assertRejection(
      Map<String, Object> contract, Map<String, Object> operation, String status, String... items) {
    Map<String, Object> resolved =
        resolve(contract, map(map(operation.get("responses")).get(status)));
    List<Map<String, Object>> parts =
        list(map(map(map(resolved.get("content")).get(PROBLEM_JSON)).get("schema")).get("allOf"));
    assertThat(parts).hasSize(2);
    assertThat(parts.get(0)).containsEntry("$ref", PROBLEM_REF);
    String extensionName =
        ((String) parts.get(1).get("$ref")).substring("#/components/schemas/".length());
    Map<String, Object> members = map(schema(contract, extensionName).get("properties"));
    for (String item : items) {
      if (item.startsWith("V-")) {
        assertThat((String) resolved.get("description")).as("respuesta %s", status).contains(item);
      } else {
        assertThat(members).as("extensión %s", extensionName).containsKey(item);
      }
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
      Map<String, Object> problem = map(media.get("schema"));
      // A rejection extends the Problem with its members: allOf [Problem, extension].
      if (problem.containsKey("allOf")) {
        problem = list(problem.get("allOf")).get(0);
      }
      assertThat(problem).containsEntry("$ref", PROBLEM_REF);
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
