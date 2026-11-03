package co.com.sapenlinea.liv.core;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Start and end of LIV sessions, called by the BFF (plan step 8, core-internal.yaml): the user
 * comes from the validated user token, the registry of LIV is the authority, and every outcome is
 * an event for the audit.
 */
@Import(SessionEventsRecorder.class)
@DisplayName("Inicio y fin de sesión de LIV (T13)")
class InternalSessionsTest extends PlatformWebTest {

  private static final String START = "/internal/v1/sessions";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired SessionEventsRecorder.Recorded events;

  @Test
  @Tag("001-21")
  @DisplayName("001-21 · En el primer ingreso LIV crea su identificador, vinculado a (emisor, sub)")
  void firstLoginCreatesTheLivIdentity() {
    String subject = "nuevo-" + UUID.randomUUID();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);

    HttpResponse<String> response = start(token);

    assertThat(response.statusCode()).isEqualTo(201);
    UUID userId = userIdOf(response);
    List<Object[]> users =
        LivDatabase.readAsOwner(
            "SELECT id, identity_issuer, administration_id, status, full_name, email"
                + " FROM core.admin_user WHERE identity_subject = ?",
            subject);
    assertThat(users).hasSize(1);
    assertThat(users.getFirst())
        .containsExactly(
            userId,
            OidcTestProvider.issuer(),
            LivDatabase.ADMIN_A,
            "ACTIVE",
            "Usuario de prueba " + subject,
            subject + "@test.invalid");
    assertThat(userId.toString()).isNotEqualTo(subject);
    assertThat(sessionsOf(OidcTestProvider.sessionIdOf(token))).hasSize(1);
    assertThat(events.of(UserProvisioned.class))
        .contains(new UserProvisioned(userId, LivDatabase.ADMIN_A));
    assertThat(events.of(SessionStarted.class))
        .anyMatch(e -> e.userId().equals(userId) && e.sid().equals(sid(token)));
  }

  @Test
  @Tag("001-21")
  @DisplayName("001-21 · Un usuario registrado inicia sesión con su identificador de LIV")
  void registeredUserStartsASession() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    HttpResponse<String> response = start(token);

    assertThat(response.statusCode()).isEqualTo(201);
    UUID userId = userIdOf(response);
    assertThat(events.of(UserProvisioned.class)).noneMatch(e -> e.userId().equals(userId));
    assertThat(
            LivDatabase.readAsOwner(
                "SELECT last_login_at FROM core.admin_user WHERE id = ?", userId))
        .allMatch(row -> row[0] != null);
  }

  @Test
  @Tag("001-14")
  @DisplayName("ADR 0025 · Un reintento con el mismo sid devuelve 200 con la misma sesión")
  void retryIsIdempotent() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    HttpResponse<String> first = start(token);
    HttpResponse<String> retry = start(token);

    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(retry.statusCode()).isEqualTo(200);
    assertThat(userIdOf(retry)).isEqualTo(userIdOf(first));
    assertThat(sessionsOf(sid(token))).hasSize(1);
    assertThat(events.of(SessionStarted.class))
        .filteredOn(e -> e.sid().equals(sid(token)))
        .hasSize(1);
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Un sid distinto del del token responde 401")
  void sidOfTheBodyMustBeTheTokens() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    assertProblem(start(token, "otro-sid"), 401, ErrorCodes.NOT_AUTHENTICATED);
    assertThat(sessionsOf(sid(token))).isEmpty();
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · El sid de la sesión de otro usuario responde 401")
  void sidOfAnotherUser() {
    String first = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    assertThat(start(first).statusCode()).isEqualTo(201);
    String other = "otro-" + UUID.randomUUID();
    String second =
        OidcTestProvider.userToken(
            other, LivDatabase.ADMIN_A, claims -> claims.claim("sid", sid(first)));

    assertProblem(start(second), 401, ErrorCodes.NOT_AUTHENTICATED);
  }

  @Test
  @Tag("001-05")
  @DisplayName("001-05 · Un usuario desactivado no inicia sesión y el rechazo queda auditado")
  void inactiveUserIsDenied() {
    String token = OidcTestProvider.userToken(INACTIVE, LivDatabase.ADMIN_A);

    assertProblem(start(token), 403, ErrorCodes.USER_INACTIVE);
    assertThat(sessionsOf(sid(token))).isEmpty();
    assertThat(events.of(SessionDenied.class))
        .anyMatch(e -> e.code().equals(ErrorCodes.USER_INACTIVE) && INACTIVE.equals(e.subject()));
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un token con otra administración que la registrada no inicia sesión")
  void administrationMismatchIsDenied() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_B);

    assertProblem(start(token), 403, ErrorCodes.ADMINISTRATION_MISMATCH);
    assertThat(sessionsOf(sid(token))).isEmpty();
    assertThat(events.of(SessionDenied.class))
        .anyMatch(
            e -> e.code().equals(ErrorCodes.ADMINISTRATION_MISMATCH) && ACTIVE.equals(e.subject()));
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un primer ingreso con una administración inexistente no crea el usuario")
  void unknownAdministrationIsDenied() {
    String subject = "sin-administracion-" + UUID.randomUUID();
    String unknown = OidcTestProvider.userToken(subject, UUID.randomUUID());
    String missing =
        OidcTestProvider.userToken(
            subject, LivDatabase.ADMIN_A, claims -> claims.claim("administration_id", null));

    assertProblem(start(unknown), 403, ErrorCodes.ADMINISTRATION_MISMATCH);
    assertProblem(start(missing), 403, ErrorCodes.ADMINISTRATION_MISMATCH);
    assertThat(
            LivDatabase.readAsOwner(
                "SELECT id FROM core.admin_user WHERE identity_subject = ?", subject))
        .isEmpty();
  }

  @Test
  @Tag("001-21")
  @DisplayName("001-21 · En un primer ingreso concurrente se crea un solo usuario")
  void concurrentFirstLogin() {
    String subject = "concurrente-" + UUID.randomUUID();
    List<String> tokens =
        IntStream.range(0, 6)
            .mapToObj(i -> OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A))
            .toList();

    List<HttpResponse<String>> responses;
    try (ExecutorService pool = Executors.newFixedThreadPool(tokens.size())) {
      List<CompletableFuture<HttpResponse<String>>> calls =
          tokens.stream()
              .map(token -> CompletableFuture.supplyAsync(() -> start(token), pool))
              .toList();
      responses = calls.stream().map(CompletableFuture::join).toList();
    }

    assertThat(responses).allMatch(response -> response.statusCode() == 201);
    assertThat(responses.stream().map(InternalSessionsTest::userIdOf).distinct()).hasSize(1);
    List<Object[]> users =
        LivDatabase.readAsOwner(
            "SELECT id FROM core.admin_user WHERE identity_subject = ?", subject);
    assertThat(users).hasSize(1);
    assertThat(events.of(UserProvisioned.class))
        .filteredOn(e -> e.userId().equals(users.getFirst()[0]))
        .hasSize(1);
  }

  @Test
  @Tag("001-21")
  @DisplayName("ADR 0025 · Cerrar una sesión es idempotente: un solo fin y un solo evento")
  void endIsIdempotent() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    assertThat(start(token).statusCode()).isEqualTo(201);

    assertThat(end(sid(token), ACTIVE, "LOGOUT").statusCode()).isEqualTo(204);
    assertThat(end(sid(token), ACTIVE, "PROVIDER").statusCode()).isEqualTo(204);

    List<Object[]> session = sessionsOf(sid(token));
    assertThat(session.getFirst()[1]).isNotNull();
    assertThat(session.getFirst()[2]).isEqualTo("LOGOUT");
    assertThat(events.of(SessionEnded.class))
        .filteredOn(e -> e.sid().equals(sid(token)))
        .hasSize(1);
  }

  @Test
  @Tag("001-14")
  @DisplayName(
      "ADR 0025 · El cierre se verifica contra el sid: otro sujeto o un sid ajeno no cierran")
  void endIsVerifiedAgainstTheSid() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    assertThat(start(token).statusCode()).isEqualTo(201);

    assertThat(end(sid(token), INACTIVE, "LOGOUT").statusCode()).isEqualTo(204);
    assertThat(end("sid-inexistente", ACTIVE, "LOGOUT").statusCode()).isEqualTo(204);

    assertThat(sessionsOf(sid(token)).getFirst()[1]).isNull();
    assertThat(events.of(SessionEnded.class)).noneMatch(e -> e.sid().equals(sid(token)));
  }

  @Test
  @DisplayName("T11 · Un cierre mal formado con Accept: application/json responde 400, no 406")
  void endWithJsonAccept() {
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("Accept", "application/json");

    HttpResponse<String> response =
        post("/internal/v1/sessions/sid-1/end", headers, "{\"subject\":\"x\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    // Problem Details are JSON: application/problem+json satisfies a client that accepts JSON.
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).matches("application/(problem[+])?json.*"));
    assertThat(JSON.readTree(response.body()).has("code")).isTrue();
  }

  private HttpResponse<String> start(String userToken) {
    return start(userToken, sid(userToken));
  }

  private HttpResponse<String> start(String userToken, String sid) {
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("X-User-Token", userToken);
    return post(START, headers, "{\"sid\":\"" + sid + "\"}");
  }

  private HttpResponse<String> end(String sid, String subject, String reason) {
    return post(
        START + "/" + sid + "/end",
        bearer(OidcTestProvider.serviceToken()),
        "{\"issuer\":\"%s\",\"subject\":\"%s\",\"reason\":\"%s\"}"
            .formatted(OidcTestProvider.issuer(), subject, reason));
  }

  private static String sid(String token) {
    return OidcTestProvider.sessionIdOf(token);
  }

  private static List<Object[]> sessionsOf(String sid) {
    return LivDatabase.readAsOwner(
        "SELECT user_id, ended_at, end_reason FROM core.user_session WHERE sid = ?", sid);
  }

  private static UUID userIdOf(HttpResponse<String> response) {
    return UUID.fromString(JSON.readTree(response.body()).get("userId").asString());
  }

  private static void assertProblem(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).isEqualTo(status);
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("code").asString()).isEqualTo(code);
    assertThat(body.has("detail")).isFalse();
  }
}
