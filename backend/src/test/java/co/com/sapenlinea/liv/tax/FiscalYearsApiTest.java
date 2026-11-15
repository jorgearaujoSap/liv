package co.com.sapenlinea.liv.tax;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API of the fiscal years (tax.yaml 1.0.0) end to end, with the production configuration and
 * PostgreSQL with {@code liv_app}: the answers and errors of the contract, the permissions by role
 * (the screen only hides), the isolation between administrations and a smoke test of the concurrent
 * requests (the interleavings are forced in {@code JpaFiscalYearsRacesTest}). Every test works with
 * administrations of its own, created by the owner, because {@code liv_app} cannot delete a fiscal
 * year and the container is shared.
 */
@DisplayName("API de las vigencias fiscales (007)")
class FiscalYearsApiTest extends PlatformWebTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String ADMIN = "ADMIN_SISTEMA";
  private static final String READER = "CONSULTA";

  private final ExecutorService threads = Executors.newCachedThreadPool();

  @AfterEach
  void stopThreads() {
    threads.shutdownNow();
  }

  /** The year the system is in, in the official zone of Colombia (the one of the real clock). */
  private static int currentYear() {
    return LocalDate.now(ZoneId.of("America/Bogota")).getYear();
  }

  /** An administration with a registered user; its tokens carry the role the test asks for. */
  private record Tenant(UUID administrationId, String subject) {

    String token(String... roles) {
      return OidcTestProvider.userToken(
          subject, administrationId, claims -> claims.claim("roles", List.of(roles)));
    }

    Map<String, String> as(String... roles) {
      return bearer(token(roles));
    }
  }

  private static Tenant newTenant() {
    UUID administration = UUID.randomUUID();
    LivDatabase.registerAdministration(administration, "Administración API " + administration);
    String subject = "api-fy-" + administration;
    LivDatabase.registerUser(administration, OidcTestProvider.issuer(), subject, "ACTIVE");
    return new Tenant(administration, subject);
  }

  private static JsonNode body(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }

  private HttpResponse<String> create(Tenant tenant, int year) {
    return post("/api/v1/fiscal-years", tenant.as(ADMIN), "{\"year\":" + year + "}");
  }

  private HttpResponse<String> open(Tenant tenant, int year) {
    return post("/api/v1/fiscal-years/" + year + "/preparation", tenant.as(ADMIN));
  }

  private HttpResponse<String> detail(Tenant tenant, int year) {
    return get("/api/v1/fiscal-years/" + year, tenant.as(ADMIN));
  }

  private void assertProblem(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(body(response).get("code").asString()).isEqualTo(code);
    assertThat(body(response).has("detail")).isFalse();
  }

  // ---- 007-01 y 007-03: la lista

  @Test
  @Tag("007-03")
  @DisplayName(
      "007-03 · Sin vigencias, la lista viene vacía y ofrece el año en curso y el siguiente con sus fechas")
  void emptyListOffersTheCreatableYears() {
    Tenant tenant = newTenant();

    HttpResponse<String> response = get("/api/v1/fiscal-years", tenant.as(ADMIN));

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode list = body(response);
    assertThat(list.get("items")).isEmpty();
    assertThat(list.get("canCreate").asBoolean()).isTrue();
    assertThat(list.has("createBlocked") && !list.get("createBlocked").isNull()).isFalse();
    JsonNode offered = list.get("creatableYears");
    assertThat(offered).hasSize(2);
    assertThat(offered.get(0).get("year").asInt()).isEqualTo(currentYear());
    assertThat(offered.get(0).get("relation").asString()).isEqualTo("CURRENT");
    assertThat(offered.get(0).get("startsOn").asString()).isEqualTo(currentYear() + "-01-01");
    assertThat(offered.get(0).get("endsOn").asString()).isEqualTo(currentYear() + "-12-31");
    assertThat(offered.get(1).get("year").asInt()).isEqualTo(currentYear() + 1);
    assertThat(offered.get(1).get("relation").asString()).isEqualTo("NEXT");
  }

  @Test
  @Tag("007-01")
  @DisplayName(
      "007-01 · Las vigencias van de la más reciente a la más antigua, y sin años por crear se bloquea con su motivo")
  void listIsNewestFirstAndBlockedAtTheLimit() {
    Tenant tenant = newTenant();
    int year = currentYear();
    assertThat(create(tenant, year).statusCode()).isEqualTo(201);
    assertThat(create(tenant, year + 1).statusCode()).isEqualTo(201);

    JsonNode list = body(get("/api/v1/fiscal-years", tenant.as(ADMIN)));

    assertThat(list.get("items")).hasSize(2);
    assertThat(list.get("items").get(0).get("year").asInt()).isEqualTo(year + 1);
    assertThat(list.get("items").get(1).get("year").asInt()).isEqualTo(year);
    assertThat(list.get("items").get(0).get("status").asString()).isEqualTo("PLANEADA");
    assertThat(list.get("items").get(0).get("assessmentsEnabled").asBoolean()).isFalse();
    assertThat(list.get("creatableYears")).isEmpty();
    assertThat(list.get("createBlocked").get("reason").asString()).isEqualTo("FUTURE_LIMIT");
    assertThat(list.get("createBlocked").get("maxYear").asInt()).isEqualTo(year + 1);
  }

  // ---- 007-02 y 007-09: permisos

  @Test
  @Tag("007-02")
  @DisplayName("007-02 · Sin token, 401; sin el permiso de lectura (o con un rol desconocido), 403")
  void authenticationAndReadPermission() {
    Tenant tenant = newTenant();

    assertProblem(get("/api/v1/fiscal-years", Map.of()), 401, "001-14");
    assertProblem(get("/api/v1/fiscal-years/" + currentYear(), Map.of()), 401, "001-14");
    for (String[] roles : List.of(new String[] {"INVENTADO"}, new String[] {})) {
      assertProblem(get("/api/v1/fiscal-years", tenant.as(roles)), 403, "001-16");
      assertProblem(get("/api/v1/fiscal-years/" + currentYear(), tenant.as(roles)), 403, "001-16");
    }
  }

  @Test
  @Tag("007-09")
  @DisplayName(
      "007-09 · Con el rol de consulta se lee sin acciones; crear y abrir dan 403 y no cambian nada")
  void aReaderReadsButCannotCreateNorOpen() {
    Tenant tenant = newTenant();
    int year = currentYear();
    assertThat(create(tenant, year).statusCode()).isEqualTo(201);

    JsonNode list = body(get("/api/v1/fiscal-years", tenant.as(READER)));
    assertThat(list.get("canCreate").asBoolean()).isFalse();
    assertThat(list.get("creatableYears")).isEmpty();
    assertThat(list.has("createBlocked") && !list.get("createBlocked").isNull()).isFalse();
    assertThat(list.get("items")).hasSize(1);
    JsonNode one = body(get("/api/v1/fiscal-years/" + year, tenant.as(READER)));
    assertThat(one.get("availableActions")).isEmpty();

    assertProblem(
        post("/api/v1/fiscal-years", tenant.as(READER), "{\"year\":" + (year + 1) + "}"),
        403,
        "001-16");
    assertProblem(
        post("/api/v1/fiscal-years/" + year + "/preparation", tenant.as(READER)), 403, "001-16");
    assertThat(body(get("/api/v1/fiscal-years", tenant.as(ADMIN))).get("items")).hasSize(1);
    assertThat(body(detail(tenant, year)).get("status").asString()).isEqualTo("PLANEADA");
  }

  // ---- V-01, V-02, V-05: la creación

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName("V-01 · 007-05 · Crear un año que ya existe da 409 con su año")
  void creatingAnExistingYear() {
    Tenant tenant = newTenant();
    int year = currentYear();
    HttpResponse<String> created = create(tenant, year);

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(body(created).get("year").asInt()).isEqualTo(year);
    assertThat(body(created).get("status").asString()).isEqualTo("PLANEADA");
    assertThat(body(created).get("assessmentsEnabled").asBoolean()).isFalse();
    assertThat(body(created).get("startsOn").asString()).isEqualTo(year + "-01-01");
    HttpResponse<String> again = create(tenant, year);
    assertProblem(again, 409, "V-01");
    assertThat(body(again).get("year").asInt()).isEqualTo(year);
  }

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName(
      "V-05 · 007-08 · La primera vigencia solo puede ser la del año en curso o la siguiente")
  void theFirstYear() {
    Tenant tenant = newTenant();

    HttpResponse<String> response = create(tenant, currentYear() - 3);

    assertProblem(response, 422, "V-05");
    assertThat(body(response).get("reason").asString()).isEqualTo("FIRST_YEAR");
    assertThat(body(response).get("currentYear").asInt()).isEqualTo(currentYear());
    assertThat(body(response).get("nextYear").asInt()).isEqualTo(currentYear() + 1);
  }

  @Test
  @Tag("V-02")
  @Tag("007-07")
  @DisplayName("V-02 · 007-07 · Un año anterior a la vigencia más antigua da 422 HISTORICAL")
  void aHistoricalYear() {
    Tenant tenant = newTenant();
    assertThat(create(tenant, currentYear()).statusCode()).isEqualTo(201);

    HttpResponse<String> response = create(tenant, currentYear() - 1);

    assertProblem(response, 422, "V-02");
    assertThat(body(response).get("reason").asString()).isEqualTo("HISTORICAL");
    assertThat(body(response).get("oldestYear").asInt()).isEqualTo(currentYear());
  }

  @Test
  @Tag("V-02")
  @Tag("V-05")
  @Tag("007-06")
  @DisplayName(
      "V-02 · V-05 · 007-06 · Saltarse un año da GAP con el año que falta, y pasar del siguiente al en curso da FUTURE_LIMIT")
  void aGapAndTheFutureLimit() {
    Tenant tenant = newTenant();
    int year = currentYear();
    assertThat(create(tenant, year).statusCode()).isEqualTo(201);

    HttpResponse<String> gap = create(tenant, year + 2);
    assertProblem(gap, 422, "V-02");
    assertThat(body(gap).get("reason").asString()).isEqualTo("GAP");
    assertThat(body(gap).get("requiredYear").asInt()).isEqualTo(year + 1);

    assertThat(create(tenant, year + 1).statusCode()).isEqualTo(201);
    HttpResponse<String> limit = create(tenant, year + 2);
    assertProblem(limit, 422, "V-05");
    assertThat(body(limit).get("reason").asString()).isEqualTo("FUTURE_LIMIT");
    assertThat(body(limit).get("maxYear").asInt()).isEqualTo(year + 1);
  }

  // ---- 007-10, 007-11 y 007-12: abrir la preparación y la lista

  @Test
  @Tag("007-10")
  @Tag("007-11")
  @Tag("007-12")
  @DisplayName(
      "007-10 · 007-11 · 007-12 · La vigencia PLANEADA ofrece abrir; abierta, el punto 1 se cumple y la liquidación sigue sin habilitarse")
  void openingThePreparation() {
    Tenant tenant = newTenant();
    int year = currentYear();
    assertThat(create(tenant, year).statusCode()).isEqualTo(201);

    JsonNode planned = body(detail(tenant, year));
    assertThat(planned.get("status").asString()).isEqualTo("PLANEADA");
    assertThat(planned.get("availableActions")).hasSize(1);
    assertThat(planned.get("availableActions").get(0).asString()).isEqualTo("OPEN_PREPARATION");
    JsonNode plannedPoints = planned.get("preparation").get("items");
    assertThat(plannedPoints).hasSize(9);
    assertThat(plannedPoints.get(0).get("code").asString()).isEqualTo("FISCAL_YEAR_IN_PREPARATION");
    assertThat(plannedPoints.get(0).get("status").asString()).isEqualTo("PENDING");
    assertThat(plannedPoints.get(0).get("reason").asString()).isEqualTo("FISCAL_YEAR_PLANNED");
    assertThat(planned.get("preparation").get("missing").toString()).isEqualTo("[1,2,3,4,5,8,9]");

    HttpResponse<String> opened = open(tenant, year);
    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(body(opened).get("status").asString()).isEqualTo("EN_PREPARACION");
    assertThat(body(opened).get("assessmentsEnabled").asBoolean()).isFalse();

    JsonNode inPreparation = body(detail(tenant, year));
    assertThat(inPreparation.get("status").asString()).isEqualTo("EN_PREPARACION");
    assertThat(inPreparation.get("availableActions")).isEmpty();
    JsonNode points = inPreparation.get("preparation").get("items");
    assertThat(points.get(0).get("status").asString()).isEqualTo("MET");
    assertThat(points.get(0).get("reason").asString()).isEqualTo("MET");
    assertThat(points.get(0).get("required").asBoolean()).isTrue();
    assertThat(points.get(0).get("option").asString()).isEqualTo("1.4");
    for (int i = 1; i < 9; i++) {
      assertThat(points.get(i).get("status").asString()).isEqualTo("PENDING");
      assertThat(points.get(i).get("reason").asString()).isEqualTo("OPTION_NOT_AVAILABLE");
    }
    assertThat(inPreparation.get("preparation").get("canEnable").asBoolean()).isFalse();
    assertThat(inPreparation.get("preparation").get("missing").toString())
        .isEqualTo("[2,3,4,5,8,9]");
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Abrir una vigencia que ya no está PLANEADA da 409 V-06 con su estado actual")
  void openingTwice() {
    Tenant tenant = newTenant();
    int year = currentYear();
    assertThat(create(tenant, year).statusCode()).isEqualTo(201);
    assertThat(open(tenant, year).statusCode()).isEqualTo(200);

    HttpResponse<String> again = open(tenant, year);

    assertProblem(again, 409, "V-06");
    assertThat(body(again).get("year").asInt()).isEqualTo(year);
    assertThat(body(again).get("currentStatus").asString()).isEqualTo("EN_PREPARACION");
  }

  // ---- CA-12, 007-18 y 007-19: aislamiento

  @Test
  @Tag("CA-12")
  @Tag("007-18")
  @Tag("007-19")
  @DisplayName(
      "CA-12 · 007-18 · 007-19 · Cada administración ve solo las suyas, y la de otra da el mismo 404 que una inexistente")
  void isolationBetweenAdministrations() {
    Tenant owner = newTenant();
    Tenant stranger = newTenant();
    Tenant nobody = newTenant();
    int year = currentYear();
    assertThat(create(owner, year).statusCode()).isEqualTo(201);
    // Each one has its own year of the same number (007-19).
    assertThat(create(stranger, year).statusCode()).isEqualTo(201);

    assertThat(body(get("/api/v1/fiscal-years", nobody.as(ADMIN))).get("items")).isEmpty();
    assertThat(body(get("/api/v1/fiscal-years", owner.as(ADMIN))).get("items")).hasSize(1);

    // The year exists for the owner but not for the others: a stranger that has none of that year.
    int otherYear = year + 1;
    assertThat(create(owner, otherYear).statusCode()).isEqualTo(201);
    HttpResponse<String> ofAnother = detail(stranger, otherYear);
    HttpResponse<String> missing = detail(nobody, otherYear);
    assertProblem(ofAnother, 404, "SYS-404");
    assertThat(ofAnother.body()).isEqualTo(missing.body());
  }

  @Test
  @Tag("CA-12")
  @DisplayName(
      "CA-12 · Abrir la vigencia de otra administración, también si ya no está PLANEADA, da el mismo 404 y nunca 409")
  void openingTheYearOfAnotherAdministrationIsAlways404() {
    Tenant owner = newTenant();
    Tenant stranger = newTenant();
    Tenant nobody = newTenant();
    int year = currentYear();
    assertThat(create(owner, year).statusCode()).isEqualTo(201);

    // Planned: the stranger gets the same answer as for a year nobody created.
    HttpResponse<String> planned = open(stranger, year);
    assertProblem(planned, 404, "SYS-404");
    assertThat(planned.body()).isEqualTo(open(nobody, year).body());

    // Already in preparation: still 404, never the 409 V-06 that would reveal its state.
    assertThat(open(owner, year).statusCode()).isEqualTo(200);
    HttpResponse<String> inPreparation = open(stranger, year);
    assertProblem(inPreparation, 404, "SYS-404");
    assertThat(inPreparation.body()).isEqualTo(open(nobody, year).body());
    assertThat(body(detail(owner, year)).get("status").asString()).isEqualTo("EN_PREPARACION");
  }

  // ---- 400: lo que el contrato rechaza antes del caso de uso

  @Test
  @DisplayName(
      "400 · Un año fuera de 1999 a 2100, o que no es un entero, se rechaza antes del caso de uso")
  void badYearsAreRejectedByTheContract() {
    Tenant tenant = newTenant();
    for (String year : List.of("1998", "2101", "abc", "2026.5")) {
      assertProblem(get("/api/v1/fiscal-years/" + year, tenant.as(ADMIN)), 400, "SYS-400");
      assertProblem(
          post("/api/v1/fiscal-years/" + year + "/preparation", tenant.as(ADMIN)), 400, "SYS-400");
    }
    for (String request :
        List.of(
            "{\"year\":1998}",
            "{\"year\":2101}",
            "{\"year\":\"2026\"}",
            "{\"year\":2026.5}",
            "{\"year\":null}",
            "{}",
            "[]",
            "{\"year\":",
            "no es json")) {
      assertProblem(post("/api/v1/fiscal-years", tenant.as(ADMIN), request), 400, "SYS-400");
    }
    assertThat(body(get("/api/v1/fiscal-years", tenant.as(ADMIN))).get("items")).isEmpty();
  }

  // ---- humo de la concurrencia

  @Test
  @Tag("007-05")
  @Tag("007-10")
  @DisplayName(
      "007-05 · 007-10 · Dos creaciones y dos aperturas a la vez: una gana, la otra recibe 409, nunca 500 ni 503")
  void concurrentRequestsNeverFail() throws Exception {
    Tenant tenant = newTenant();
    int year = currentYear();

    List<Future<HttpResponse<String>>> creations = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      creations.add(threads.submit(() -> create(tenant, year)));
    }
    assertThat(statuses(creations)).containsExactlyInAnyOrder(201, 409);

    List<Future<HttpResponse<String>>> openings = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      openings.add(threads.submit(() -> open(tenant, year)));
    }
    assertThat(statuses(openings)).containsExactlyInAnyOrder(200, 409);
    assertThat(body(detail(tenant, year)).get("status").asString()).isEqualTo("EN_PREPARACION");
  }

  private static List<Integer> statuses(List<Future<HttpResponse<String>>> requests)
      throws Exception {
    List<Integer> statuses = new ArrayList<>();
    for (Future<HttpResponse<String>> request : requests) {
      statuses.add(request.get(30, TimeUnit.SECONDS).statusCode());
    }
    return statuses;
  }
}
