package co.com.sapenlinea.liv.tax;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The API of the fiscal years (tax.yaml 1.0.0) end to end, with the production configuration and
 * PostgreSQL with {@code liv_app}: the answers and errors of the contract, the permissions by role
 * (the screen only hides), the isolation between administrations and a smoke test of the concurrent
 * requests (the interleavings are forced in {@code JpaFiscalYearsRacesTest}). Every test works with
 * administrations of its own, created by the owner, because {@code liv_app} cannot delete a fiscal
 * year and the container is shared.
 *
 * <p>The year is the current one of the real clock in the zone of the business, so the tests do not
 * cover the border of 31 December: {@code CreateFiscalYearTest} and {@code ListFiscalYearsTest} do,
 * with a fixed clock.
 */
@DisplayName("API de las vigencias fiscales (007)")
class FiscalYearsApiTest extends PlatformWebTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String ADMIN = "ADMIN_SISTEMA";

  /** The roles that read the fiscal years but do not create or open them (Doc. 01 §4). */
  private static final List<String> READERS =
      List.of("CONSULTA", "CARGADOR_TABLAS", "APROBADOR_TABLAS", "AUDITOR");

  /** The nine points of the checklist (Doc. 01 §6.1): number, code, required and option. */
  private static final List<List<Object>> POINTS =
      List.of(
          List.of(1, "FISCAL_YEAR_IN_PREPARATION", true, "1.4"),
          List.of(2, "VALUATION_RESOLUTION", true, "1.3"),
          List.of(3, "VALUATION_CATALOG_PUBLISHED", true, "1.5.2"),
          List.of(4, "TAX_RATES", true, "1.5.5"),
          List.of(5, "TAX_CALENDAR", true, "1.5.6"),
          List.of(6, "TAX_BENEFITS", false, "1.5.7"),
          List.of(7, "PENALTY_RULES", false, "1.5.8"),
          List.of(8, "SAMPLE_ASSESSMENT", true, "3.3"),
          List.of(9, "JURISDICTION_CONFIG_PUBLISHED", true, "1.5.9"));

  private final Clock clock;
  private final ZoneId zone;
  private final ExecutorService threads = Executors.newCachedThreadPool();

  @Autowired
  FiscalYearsApiTest(Clock clock, @Value("${liv.business-zone}") ZoneId zone) {
    this.clock = clock;
    this.zone = zone;
  }

  @AfterEach
  void stopThreads() throws InterruptedException {
    threads.shutdownNow();
    threads.awaitTermination(30, TimeUnit.SECONDS);
  }

  /** The year the system is in, in the zone of the business (the one of the real clock). */
  private int currentYear() {
    return LocalDate.now(clock.withZone(zone)).getYear();
  }

  /** An administration with a registered user; its tokens carry the role the test asks for. */
  private record Tenant(UUID administrationId, String subject) {

    Map<String, String> as(String... roles) {
      return bearer(
          OidcTestProvider.userToken(
              subject, administrationId, claims -> claims.claim("roles", List.of(roles))));
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

  /** A precondition: the year is created, whatever else the test checks. */
  private void givenTheYear(Tenant tenant, int year) {
    HttpResponse<String> created = create(tenant, year);
    assertThat(created.statusCode()).as("crear %d: %s", year, created.body()).isEqualTo(201);
  }

  private static void assertProblem(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(body(response).get("code").asString()).isEqualTo(code);
    assertThat(body(response).has("detail")).isFalse();
  }

  /**
   * The Problem of a 404 without the route it was asked on: what must be the same for every cause.
   */
  private static JsonNode withoutInstance(HttpResponse<String> response) {
    ObjectNode problem = (ObjectNode) body(response);
    problem.remove("instance");
    return problem;
  }

  // ---- 007-01 y 007-03: la lista

  @Test
  @Tag("007-03")
  @DisplayName(
      "007-03 · Sin vigencias, la lista viene vacía y ofrece el año en curso y el siguiente con sus fechas")
  void emptyListOffersTheCreatableYears() {
    Tenant tenant = newTenant();
    int year = currentYear();

    HttpResponse<String> response = get("/api/v1/fiscal-years", tenant.as(ADMIN));

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode list = body(response);
    assertThat(list.get("items")).isEmpty();
    assertThat(list.get("canCreate").asBoolean()).isTrue();
    assertThat(list.has("createBlocked")).as("se omite, no viene null").isFalse();
    JsonNode offered = list.get("creatableYears");
    assertThat(offered).hasSize(2);
    assertThat(offered.get(0).get("year").asInt()).isEqualTo(year);
    assertThat(offered.get(0).get("relation").asString()).isEqualTo("CURRENT");
    assertThat(offered.get(0).get("startsOn").asString()).isEqualTo(year + "-01-01");
    assertThat(offered.get(0).get("endsOn").asString()).isEqualTo(year + "-12-31");
    assertThat(offered.get(1).get("year").asInt()).isEqualTo(year + 1);
    assertThat(offered.get(1).get("relation").asString()).isEqualTo("NEXT");
    assertThat(offered.get(1).get("startsOn").asString()).isEqualTo((year + 1) + "-01-01");
    assertThat(offered.get(1).get("endsOn").asString()).isEqualTo((year + 1) + "-12-31");
  }

  @Test
  @Tag("007-01")
  @DisplayName(
      "007-01 · Las vigencias van de la más reciente a la más antigua, y sin años por crear se bloquea con su motivo")
  void listIsNewestFirstAndBlockedAtTheLimit() {
    Tenant tenant = newTenant();
    int year = currentYear();
    givenTheYear(tenant, year);
    givenTheYear(tenant, year + 1);

    JsonNode list = body(get("/api/v1/fiscal-years", tenant.as(ADMIN)));

    assertThat(list.get("items")).hasSize(2);
    for (int i = 0; i < 2; i++) {
      JsonNode item = list.get("items").get(i);
      int expected = year + 1 - i;
      assertThat(item.get("year").asInt()).isEqualTo(expected);
      assertThat(item.get("startsOn").asString()).isEqualTo(expected + "-01-01");
      assertThat(item.get("endsOn").asString()).isEqualTo(expected + "-12-31");
      assertThat(item.get("status").asString()).isEqualTo("PLANEADA");
      assertThat(item.get("assessmentsEnabled").asBoolean()).isFalse();
    }
    assertThat(list.get("creatableYears")).isEmpty();
    assertThat(list.get("createBlocked").get("reason").asString()).isEqualTo("FUTURE_LIMIT");
    assertThat(list.get("createBlocked").get("maxYear").asInt()).isEqualTo(year + 1);
  }

  // ---- 007-02 y 007-09: permisos

  @Test
  @Tag("007-02")
  @DisplayName(
      "007-02 · Sin token, 401 en las cuatro operaciones; sin el permiso de lectura o con un rol desconocido, 403 y no cambia nada")
  void authenticationAndReadPermission() {
    Tenant tenant = newTenant();
    int year = currentYear();
    String createBody = "{\"year\":" + year + "}";

    assertProblem(get("/api/v1/fiscal-years", Map.of()), 401, "001-14");
    assertProblem(get("/api/v1/fiscal-years/" + year, Map.of()), 401, "001-14");
    assertProblem(post("/api/v1/fiscal-years", Map.of(), createBody), 401, "001-14");
    assertProblem(post("/api/v1/fiscal-years/" + year + "/preparation", Map.of()), 401, "001-14");
    for (String[] roles :
        List.of(new String[] {"INVENTADO"}, new String[] {}, new String[] {"LIQUIDADOR"})) {
      assertProblem(get("/api/v1/fiscal-years", tenant.as(roles)), 403, "001-16");
      assertProblem(get("/api/v1/fiscal-years/" + year, tenant.as(roles)), 403, "001-16");
      assertProblem(post("/api/v1/fiscal-years", tenant.as(roles), createBody), 403, "001-16");
      assertProblem(
          post("/api/v1/fiscal-years/" + year + "/preparation", tenant.as(roles)), 403, "001-16");
    }
    assertThat(body(get("/api/v1/fiscal-years", tenant.as(ADMIN))).get("items")).isEmpty();
  }

  @Test
  @Tag("007-09")
  @DisplayName(
      "007-09 · Con un rol que solo lee se lee sin acciones; crear y abrir dan 403 y no cambian nada")
  void aReaderReadsButCannotCreateNorOpen() {
    Tenant tenant = newTenant();
    int year = currentYear();
    givenTheYear(tenant, year);

    for (String role : READERS) {
      JsonNode list = body(get("/api/v1/fiscal-years", tenant.as(role)));
      assertThat(list.get("canCreate").asBoolean()).as(role).isFalse();
      assertThat(list.get("creatableYears")).as(role).isEmpty();
      assertThat(list.has("createBlocked")).as(role).isFalse();
      assertThat(list.get("items")).as(role).hasSize(1);
      assertThat(body(get("/api/v1/fiscal-years/" + year, tenant.as(role))).get("availableActions"))
          .as(role)
          .isEmpty();
      assertProblem(
          post("/api/v1/fiscal-years", tenant.as(role), "{\"year\":" + (year + 1) + "}"),
          403,
          "001-16");
      assertProblem(
          post("/api/v1/fiscal-years/" + year + "/preparation", tenant.as(role)), 403, "001-16");
    }
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
    assertThat(body(created).get("endsOn").asString()).isEqualTo(year + "-12-31");
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
    givenTheYear(tenant, currentYear());

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
    givenTheYear(tenant, year);

    HttpResponse<String> gap = create(tenant, year + 2);
    assertProblem(gap, 422, "V-02");
    assertThat(body(gap).get("reason").asString()).isEqualTo("GAP");
    assertThat(body(gap).get("requiredYear").asInt()).isEqualTo(year + 1);

    givenTheYear(tenant, year + 1);
    HttpResponse<String> limit = create(tenant, year + 2);
    assertProblem(limit, 422, "V-05");
    assertThat(body(limit).get("reason").asString()).isEqualTo("FUTURE_LIMIT");
    assertThat(body(limit).get("maxYear").asInt()).isEqualTo(year + 1);
  }

  // ---- 007-10, 007-11 y 007-12: abrir la preparación y la lista

  /** The nine points as the API answers them, with the table of the document for what is fixed. */
  private static void assertPoints(JsonNode points, boolean preparationOpen) {
    assertThat(points).hasSize(9);
    for (int i = 0; i < 9; i++) {
      JsonNode point = points.get(i);
      List<Object> expected = POINTS.get(i);
      assertThat(point.get("number").asInt()).isEqualTo(expected.get(0));
      assertThat(point.get("code").asString()).isEqualTo(expected.get(1));
      assertThat(point.get("required").asBoolean()).isEqualTo(expected.get(2));
      assertThat(point.get("option").asString()).isEqualTo(expected.get(3));
      boolean met = i == 0 && preparationOpen;
      assertThat(point.get("status").asString()).isEqualTo(met ? "MET" : "PENDING");
      String reason = met ? "MET" : i == 0 ? "FISCAL_YEAR_PLANNED" : "OPTION_NOT_AVAILABLE";
      assertThat(point.get("reason").asString()).isEqualTo(reason);
    }
  }

  @Test
  @Tag("007-10")
  @Tag("007-11")
  @Tag("007-12")
  @DisplayName(
      "007-10 · 007-11 · 007-12 · La vigencia PLANEADA ofrece abrir; abierta, el punto 1 se cumple y la liquidación sigue sin habilitarse")
  void openingThePreparation() {
    Tenant tenant = newTenant();
    int year = currentYear();
    givenTheYear(tenant, year);

    JsonNode planned = body(detail(tenant, year));
    assertThat(planned.get("year").asInt()).isEqualTo(year);
    assertThat(planned.get("startsOn").asString()).isEqualTo(year + "-01-01");
    assertThat(planned.get("endsOn").asString()).isEqualTo(year + "-12-31");
    assertThat(planned.get("status").asString()).isEqualTo("PLANEADA");
    assertThat(planned.get("assessmentsEnabled").asBoolean()).isFalse();
    assertThat(planned.get("availableActions")).hasSize(1);
    assertThat(planned.get("availableActions").get(0).asString()).isEqualTo("OPEN_PREPARATION");
    assertPoints(planned.get("preparation").get("items"), false);
    assertThat(planned.get("preparation").get("canEnable").asBoolean()).isFalse();
    assertThat(planned.get("preparation").get("missing").toString()).isEqualTo("[1,2,3,4,5,8,9]");

    HttpResponse<String> opened = open(tenant, year);
    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(body(opened).get("year").asInt()).isEqualTo(year);
    assertThat(body(opened).get("startsOn").asString()).isEqualTo(year + "-01-01");
    assertThat(body(opened).get("endsOn").asString()).isEqualTo(year + "-12-31");
    assertThat(body(opened).get("status").asString()).isEqualTo("EN_PREPARACION");
    assertThat(body(opened).get("assessmentsEnabled").asBoolean()).isFalse();

    JsonNode inPreparation = body(detail(tenant, year));
    assertThat(inPreparation.get("status").asString()).isEqualTo("EN_PREPARACION");
    assertThat(inPreparation.get("assessmentsEnabled").asBoolean()).isFalse();
    assertThat(inPreparation.get("availableActions")).isEmpty();
    assertPoints(inPreparation.get("preparation").get("items"), true);
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
    givenTheYear(tenant, year);
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
      "CA-12 · 007-18 · 007-19 · Cada administración ve y cambia solo las suyas, y la de otra da el mismo 404 que una inexistente")
  void isolationBetweenAdministrations() {
    Tenant owner = newTenant();
    Tenant stranger = newTenant();
    Tenant nobody = newTenant();
    int year = currentYear();
    givenTheYear(owner, year);
    // Each one has its own year of the same number (007-19): changing one does not change the
    // other.
    givenTheYear(stranger, year);
    assertThat(open(owner, year).statusCode()).isEqualTo(200);
    assertThat(body(detail(stranger, year)).get("status").asString()).isEqualTo("PLANEADA");

    assertThat(body(get("/api/v1/fiscal-years", nobody.as(ADMIN))).get("items")).isEmpty();
    assertThat(body(get("/api/v1/fiscal-years", owner.as(ADMIN))).get("items")).hasSize(1);

    // The same route, before and after the owner creates the year: for the stranger the answer
    // is exactly the same, body and headers (only the correlation changes).
    int onlyOwners = year + 1;
    HttpResponse<String> neverCreated = detail(stranger, onlyOwners);
    assertProblem(neverCreated, 404, "SYS-404");
    givenTheYear(owner, onlyOwners);
    HttpResponse<String> ofAnother = detail(stranger, onlyOwners);
    assertThat(ofAnother.body()).isEqualTo(neverCreated.body());
    assertThat(sameHeaders(ofAnother)).isEqualTo(sameHeaders(neverCreated));
  }

  /** The headers of an answer except the ones that are different on every request. */
  private static Map<String, List<String>> sameHeaders(HttpResponse<String> response) {
    Map<String, List<String>> headers = new java.util.TreeMap<>(response.headers().map());
    headers.remove("date");
    headers.remove("x-correlation-id");
    return headers;
  }

  @Test
  @Tag("CA-12")
  @DisplayName(
      "CA-12 · Abrir la vigencia de otra administración, también si ya no está PLANEADA, da el mismo 404 que una inexistente y nunca 409")
  void openingTheYearOfAnotherAdministrationIsAlways404() {
    Tenant owner = newTenant();
    Tenant stranger = newTenant();
    int year = currentYear();
    givenTheYear(owner, year);

    // A year nobody created, asked on the same route: the answer to compare with.
    HttpResponse<String> neverCreated = open(stranger, 2099);
    assertProblem(neverCreated, 404, "SYS-404");

    // Planned, and in preparation: the stranger gets the same answer, never the 409 V-06 that would
    // reveal the state.
    HttpResponse<String> planned = open(stranger, year);
    assertProblem(planned, 404, "SYS-404");
    assertThat(withoutInstance(planned)).isEqualTo(withoutInstance(neverCreated));
    assertThat(sameHeaders(planned)).isEqualTo(sameHeaders(neverCreated));
    assertThat(open(owner, year).statusCode()).isEqualTo(200);
    HttpResponse<String> inPreparation = open(stranger, year);
    assertProblem(inPreparation, 404, "SYS-404");
    assertThat(withoutInstance(inPreparation)).isEqualTo(withoutInstance(neverCreated));
    assertThat(sameHeaders(inPreparation)).isEqualTo(sameHeaders(neverCreated));
    assertThat(inPreparation.body()).isEqualTo(planned.body());
    assertThat(body(detail(owner, year)).get("status").asString()).isEqualTo("EN_PREPARACION");
  }

  // ---- 400: lo que el contrato rechaza antes del caso de uso

  @Test
  @DisplayName(
      "400 · Un año fuera de 1999 a 2100, o que no es un entero, se rechaza antes del caso de uso")
  void badYearsAreRejectedByTheContract() {
    Tenant tenant = newTenant();
    for (String year : List.of("1998", "2101", "abc", "2026.5", "99999999999")) {
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
            "{\"year\":2026.0}",
            "{\"year\":true}",
            "{\"year\":99999999999}",
            "{\"year\":null}",
            "{}",
            "[]",
            "",
            "{\"year\":",
            "no es json")) {
      assertProblem(post("/api/v1/fiscal-years", tenant.as(ADMIN), request), 400, "SYS-400");
    }
    assertThat(body(get("/api/v1/fiscal-years", tenant.as(ADMIN))).get("items")).isEmpty();
  }

  @Test
  @DisplayName(
      "400 · Los límites 1999 y 2100 sí pasan la validación del contrato: llegan a la regla (422), no a un 400")
  void theBoundsOfTheContractAreAccepted() {
    Tenant tenant = newTenant();

    for (int year : new int[] {1999, 2100}) {
      assertProblem(create(tenant, year), 422, "V-05");
      assertThat(detail(tenant, year).statusCode()).isEqualTo(404);
      assertThat(open(tenant, year).statusCode()).isEqualTo(404);
    }
  }

  // ---- humo de la concurrencia

  private List<HttpResponse<String>> allAtOnce(
      List<java.util.concurrent.Callable<HttpResponse<String>>> requests) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<HttpResponse<String>>> futures = new ArrayList<>();
    for (java.util.concurrent.Callable<HttpResponse<String>> request : requests) {
      futures.add(
          threads.submit(() -> {
            start.await();
            return request.call();
          }));
    }
    start.countDown();
    List<HttpResponse<String>> responses = new ArrayList<>();
    for (Future<HttpResponse<String>> future : futures) {
      responses.add(future.get(30, TimeUnit.SECONDS));
    }
    return responses;
  }

  @Test
  @Tag("V-01")
  @Tag("V-06")
  @Tag("007-05")
  @Tag("007-10")
  @DisplayName(
      "007-05 · 007-10 · Dos creaciones y dos aperturas a la vez: una gana, la otra recibe 409 con su código, nunca 500 ni 503")
  void concurrentRequestsNeverFail() throws Exception {
    Tenant tenant = newTenant();
    int year = currentYear();

    List<HttpResponse<String>> creations =
        allAtOnce(List.of(() -> create(tenant, year), () -> create(tenant, year)));
    assertThat(creations).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);
    assertThat(
            creations.stream()
                .filter(r -> r.statusCode() == 409)
                .map(r -> body(r).get("code").asString()))
        .containsExactly("V-01");
    assertThat(body(get("/api/v1/fiscal-years", tenant.as(ADMIN))).get("items")).hasSize(1);

    List<HttpResponse<String>> openings =
        allAtOnce(List.of(() -> open(tenant, year), () -> open(tenant, year)));
    assertThat(openings).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
    assertThat(
            openings.stream()
                .filter(r -> r.statusCode() == 409)
                .map(r -> body(r).get("code").asString()))
        .containsExactly("V-06");
    assertThat(body(detail(tenant, year)).get("status").asString()).isEqualTo("EN_PREPARACION");
  }
}
