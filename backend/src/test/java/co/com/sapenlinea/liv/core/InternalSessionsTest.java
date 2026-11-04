package co.com.sapenlinea.liv.core;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * comes from the validated user token, the registry of LIV is the authority, every refusal is
 * audited with {@code SessionDenied}, and a refused start leaves nothing behind.
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
    String subject = newSubject();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);

    HttpResponse<String> response = start(token);

    assertThat(response.statusCode()).isEqualTo(201);
    UUID userId = userIdOf(response);
    List<Object[]> users = usersWith(subject);
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
    assertThat(sessionsOf(sid(token))).hasSize(1);
    assertThat(events.of(UserProvisioned.class))
        .contains(new UserProvisioned(userId, LivDatabase.ADMIN_A, null, null, null));
    assertThat(events.of(SessionStarted.class))
        .anyMatch(e -> e.userId().equals(userId) && e.sid().equals(sid(token)));
  }

  @Test
  @Tag("001-21")
  @DisplayName(
      "001-21 · Un usuario registrado inicia sesión con su identificador y su último ingreso")
  void registeredUserStartsASession() {
    Instant before = lastLoginOf(ACTIVE);
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    HttpResponse<String> response = start(token);

    assertThat(response.statusCode()).isEqualTo(201);
    UUID userId = userIdOf(response);
    assertThat(usersWith(ACTIVE).getFirst()[0]).isEqualTo(userId);
    assertThat(events.of(UserProvisioned.class)).noneMatch(e -> e.userId().equals(userId));
    Instant after = lastLoginOf(ACTIVE);
    assertThat(after).isNotNull();
    if (before != null) {
      assertThat(after).isAfter(before);
    }
  }

  @Test
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
  @DisplayName("001-14 · Un sid distinto del del token responde 401 y queda auditado")
  void sidOfTheBodyMustBeTheTokens() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    String requested = "otro-sid-" + UUID.randomUUID();
    assertProblem(start(token, requested), 401, ErrorCodes.NOT_AUTHENTICATED);

    assertThat(sessionsOf(sid(token))).isEmpty();
    assertThat(deniedFor(requested))
        .containsExactly(
            denied(ErrorCodes.NOT_AUTHENTICATED, ACTIVE, requested, true, LivDatabase.ADMIN_A));
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · El sid de otro usuario: 401 auditado, sin alta ni cambio de su sesión")
  void sidOfAnotherUser() {
    String first = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    assertThat(start(first).statusCode()).isEqualTo(201);
    String other = newSubject();
    String second =
        OidcTestProvider.userToken(
            other, LivDatabase.ADMIN_A, claims -> claims.claim("sid", sid(first)));
    int provisioned = events.of(UserProvisioned.class).size();

    assertProblem(start(second), 401, ErrorCodes.NOT_AUTHENTICATED);

    assertThat(events.of(UserProvisioned.class))
        .as("el alta revertida no publica")
        .hasSize(provisioned);

    assertThat(usersWith(other)).as("el alta se revierte").isEmpty();
    List<Object[]> session = sessionsOf(sid(first));
    assertThat(session).hasSize(1);
    assertThat(session.getFirst()[1]).isNull();
    assertThat(deniedFor(sid(first)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.NOT_AUTHENTICATED,
                OidcTestProvider.issuer(),
                other,
                sid(first),
                null,
                null,
                LivDatabase.ADMIN_A,
                null,
                null,
                null));
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Una sesión cerrada no se reabre con el mismo sid: 401 auditado")
  void closedSessionIsNotReopened() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    assertThat(start(token).statusCode()).isEqualTo(201);
    assertThat(end(sid(token), ACTIVE, "LOGOUT").statusCode()).isEqualTo(204);

    assertProblem(start(token), 401, ErrorCodes.NOT_AUTHENTICATED);

    assertThat(sessionsOf(sid(token)).getFirst()[2]).isEqualTo("LOGOUT");
    assertThat(deniedFor(sid(token)))
        .containsExactly(
            denied(ErrorCodes.NOT_AUTHENTICATED, ACTIVE, sid(token), true, LivDatabase.ADMIN_A));
  }

  @Test
  @Tag("001-05")
  @DisplayName("001-05 · Un usuario desactivado no inicia sesión y el rechazo queda auditado")
  void inactiveUserIsDenied() {
    String token = OidcTestProvider.userToken(INACTIVE, LivDatabase.ADMIN_A);

    assertProblem(start(token), 403, ErrorCodes.USER_INACTIVE);

    assertThat(sessionsOf(sid(token))).isEmpty();
    UUID userId = (UUID) usersWith(INACTIVE).getFirst()[0];
    assertThat(deniedFor(sid(token)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.USER_INACTIVE,
                OidcTestProvider.issuer(),
                INACTIVE,
                sid(token),
                userId,
                LivDatabase.ADMIN_A,
                LivDatabase.ADMIN_A,
                null,
                null,
                null));
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Un token con otra administración que la registrada no inicia sesión")
  void administrationMismatchIsDenied() {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_B);

    assertProblem(start(token), 403, ErrorCodes.ADMINISTRATION_MISMATCH);

    assertThat(sessionsOf(sid(token))).isEmpty();
    UUID userId = (UUID) usersWith(ACTIVE).getFirst()[0];
    assertThat(deniedFor(sid(token)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.ADMINISTRATION_MISMATCH,
                OidcTestProvider.issuer(),
                ACTIVE,
                sid(token),
                userId,
                LivDatabase.ADMIN_A,
                LivDatabase.ADMIN_B,
                null,
                null,
                null));
  }

  @Test
  @Tag("001-17")
  @DisplayName(
      "001-17 · Un primer ingreso con administración inexistente o ausente no crea al usuario")
  void unknownAdministrationIsDenied() {
    String subject = newSubject();
    UUID unknown = UUID.randomUUID();
    String withUnknown = OidcTestProvider.userToken(subject, unknown);
    String withoutAdministration =
        OidcTestProvider.userToken(
            subject, LivDatabase.ADMIN_A, claims -> claims.claim("administration_id", null));

    assertProblem(start(withUnknown), 403, ErrorCodes.ADMINISTRATION_MISMATCH);
    assertProblem(start(withoutAdministration), 403, ErrorCodes.ADMINISTRATION_MISMATCH);

    assertThat(usersWith(subject)).isEmpty();
    assertThat(deniedFor(sid(withUnknown)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.ADMINISTRATION_MISMATCH,
                OidcTestProvider.issuer(),
                subject,
                sid(withUnknown),
                null,
                null,
                unknown,
                null,
                null,
                null));
    assertThat(deniedFor(sid(withoutAdministration))).hasSize(1);
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Una administración inactiva no admite primeros ingresos ni sesiones")
  void inactiveAdministrationIsDenied() {
    UUID administration = UUID.randomUUID();
    LivDatabase.registerAdministration(administration, "Administración inactiva (prueba)");
    String registered = newSubject();
    LivDatabase.registerUser(administration, OidcTestProvider.issuer(), registered, "ACTIVE");
    LivDatabase.setAdministrationActive(administration, false);
    String newcomer = newSubject();

    String ofNewcomer = OidcTestProvider.userToken(newcomer, administration);
    String ofRegistered = OidcTestProvider.userToken(registered, administration);
    assertProblem(start(ofNewcomer), 403, ErrorCodes.ADMINISTRATION_MISMATCH);
    assertProblem(start(ofRegistered), 403, ErrorCodes.ADMINISTRATION_MISMATCH);

    assertThat(usersWith(newcomer)).isEmpty();
    assertThat(sessionsOf(sid(ofRegistered))).isEmpty();
    assertThat(deniedFor(sid(ofNewcomer)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.ADMINISTRATION_MISMATCH,
                OidcTestProvider.issuer(),
                newcomer,
                sid(ofNewcomer),
                null,
                null,
                administration,
                null,
                null,
                null));
    assertThat(deniedFor(sid(ofRegistered)))
        .containsExactly(
            denied(
                ErrorCodes.ADMINISTRATION_MISMATCH,
                registered,
                sid(ofRegistered),
                true,
                administration));
  }

  @Test
  @Tag("001-14")
  @DisplayName(
      "001-14 · Sin correo, o con nombre o correo más largos que el registro, 401 auditado")
  void invalidIdentityDataIsDenied() {
    String withoutEmail = newSubject();
    String longName = newSubject();
    String longEmail = newSubject();

    assertProblem(
        start(
            OidcTestProvider.userToken(
                withoutEmail, LivDatabase.ADMIN_A, claims -> claims.claim("email", null))),
        401,
        ErrorCodes.NOT_AUTHENTICATED);
    assertProblem(
        start(
            OidcTestProvider.userToken(
                longName, LivDatabase.ADMIN_A, claims -> claims.claim("name", "n".repeat(151)))),
        401,
        ErrorCodes.NOT_AUTHENTICATED);
    assertProblem(
        start(
            OidcTestProvider.userToken(
                longEmail,
                LivDatabase.ADMIN_A,
                claims -> claims.claim("email", "e".repeat(190) + "@test.invalid"))),
        401,
        ErrorCodes.NOT_AUTHENTICATED);

    assertThat(usersWith(withoutEmail)).isEmpty();
    assertThat(usersWith(longName)).isEmpty();
    assertThat(usersWith(longEmail)).isEmpty();
    for (String subject : List.of(withoutEmail, longName, longEmail)) {
      assertThat(events.of(SessionDenied.class))
          .filteredOn(e -> subject.equals(e.subject()))
          .singleElement()
          .satisfies(e -> {
            assertThat(e.code()).isEqualTo(ErrorCodes.NOT_AUTHENTICATED);
            assertThat(e.userId()).isNull();
            assertThat(e.registeredAdministrationId()).isNull();
            assertThat(e.claimedAdministrationId()).isEqualTo(LivDatabase.ADMIN_A);
          });
    }
  }

  @Test
  @Tag("001-21")
  @DisplayName("001-21 · Un nombre de 150 y un correo de 200 caracteres sí caben en el registro")
  void identityDataAtTheLimitIsAccepted() {
    String subject = newSubject();
    String email = "e".repeat(187) + "@test.invalid";
    assertThat(email).hasSize(200);
    String token =
        OidcTestProvider.userToken(
            subject,
            LivDatabase.ADMIN_A,
            claims -> claims.claim("name", "n".repeat(150)).claim("email", email));

    assertThat(start(token).statusCode()).isEqualTo(201);
    assertThat(usersWith(subject)).hasSize(1);
  }

  @Test
  @Tag("001-21")
  @DisplayName(
      "001-21 · Si otra transacción registra al mismo usuario a la vez, se usa ese registro")
  void concurrentRegistrationIsReadBack() throws Exception {
    String subject = newSubject();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);
    CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();

    LivDatabase.insertUserUncommittedDuring(
        LivDatabase.ADMIN_A,
        OidcTestProvider.issuer(),
        subject,
        "ACTIVE",
        () -> response.completeAsync(() -> start(token)));

    HttpResponse<String> started = response.get(20, TimeUnit.SECONDS);
    assertThat(started.statusCode()).isEqualTo(201);
    assertThat(usersWith(subject)).hasSize(1);
    assertThat(userIdOf(started)).isEqualTo(usersWith(subject).getFirst()[0]);
    assertThat(events.of(UserProvisioned.class))
        .noneMatch(e -> e.userId().equals(userIdOf(started)));
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Si a la vez lo registran en otra administración, el ingreso se rechaza")
  void concurrentRegistrationElsewhereIsDenied() throws Exception {
    String subject = newSubject();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);
    CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();

    LivDatabase.insertUserUncommittedDuring(
        LivDatabase.ADMIN_B,
        OidcTestProvider.issuer(),
        subject,
        "ACTIVE",
        () -> response.completeAsync(() -> start(token)));

    assertProblem(response.get(20, TimeUnit.SECONDS), 403, ErrorCodes.ADMINISTRATION_MISMATCH);
    assertThat(sessionsOf(sid(token))).isEmpty();
    UUID registered = (UUID) usersWith(subject).getFirst()[0];
    assertThat(deniedFor(sid(token)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.ADMINISTRATION_MISMATCH,
                OidcTestProvider.issuer(),
                subject,
                sid(token),
                registered,
                LivDatabase.ADMIN_B,
                LivDatabase.ADMIN_A,
                null,
                null,
                null));
  }

  @Test
  @Tag("001-05")
  @DisplayName(
      "001-05 · Si a la vez lo registran desactivado, el ingreso se rechaza en la transacción")
  void concurrentRegistrationAsInactiveIsDenied() throws Exception {
    String subject = newSubject();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);
    CompletableFuture<HttpResponse<String>> response = new CompletableFuture<>();

    LivDatabase.insertUserUncommittedDuring(
        LivDatabase.ADMIN_A,
        OidcTestProvider.issuer(),
        subject,
        "INACTIVE",
        () -> response.completeAsync(() -> start(token)));

    assertProblem(response.get(20, TimeUnit.SECONDS), 403, ErrorCodes.USER_INACTIVE);
    assertThat(sessionsOf(sid(token))).isEmpty();
    UUID registered = (UUID) usersWith(subject).getFirst()[0];
    assertThat(deniedFor(sid(token)))
        .containsExactly(
            new SessionDenied(
                ErrorCodes.USER_INACTIVE,
                OidcTestProvider.issuer(),
                subject,
                sid(token),
                registered,
                LivDatabase.ADMIN_A,
                LivDatabase.ADMIN_A,
                null,
                null,
                null));
  }

  @Test
  @Tag("001-21")
  @DisplayName("001-21 · En un primer ingreso concurrente se crea un solo usuario")
  void concurrentFirstLogin() throws Exception {
    String subject = newSubject();
    List<String> tokens =
        IntStream.range(0, 6)
            .mapToObj(i -> OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A))
            .toList();
    CountDownLatch gate = new CountDownLatch(1);

    List<HttpResponse<String>> responses;
    try (ExecutorService pool = Executors.newFixedThreadPool(tokens.size())) {
      List<CompletableFuture<HttpResponse<String>>> calls =
          tokens.stream()
              .map(token ->
                  CompletableFuture.supplyAsync(
                      () -> {
                        await(gate);
                        return start(token);
                      },
                      pool))
              .toList();
      gate.countDown();
      responses = calls.stream().map(CompletableFuture::join).toList();
    }

    assertThat(responses).allMatch(response -> response.statusCode() == 201);
    assertThat(responses.stream().map(InternalSessionsTest::userIdOf).distinct()).hasSize(1);
    List<Object[]> users = usersWith(subject);
    assertThat(users).hasSize(1);
    assertThat(events.of(UserProvisioned.class))
        .filteredOn(e -> e.userId().equals(users.getFirst()[0]))
        .hasSize(1);
  }

  @Test
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
  @DisplayName(
      "T11 · Con Accept: application/json el cierre responde 204, y 400 si está mal formado")
  void endWithJsonAccept() {
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("Accept", "application/json");
    String valid =
        "{\"issuer\":\"%s\",\"subject\":\"%s\",\"reason\":\"LOGOUT\"}"
            .formatted(OidcTestProvider.issuer(), ACTIVE);

    assertThat(post(START + "/sid-1/end", headers, valid).statusCode()).isEqualTo(204);
    HttpResponse<String> invalid = post(START + "/sid-1/end", headers, "{\"subject\":\"x\"}");

    assertThat(invalid.statusCode()).isEqualTo(400);
    // Problem Details are JSON: application/problem+json satisfies a client that accepts JSON.
    assertThat(invalid.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).matches("application/(problem[+])?json.*"));
    assertThat(JSON.readTree(invalid.body()).has("code")).isTrue();
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

  /** A refusal of a registered user (or not), the token claiming {@code claimed}. */
  private static SessionDenied denied(
      String code, String subject, String sid, boolean registered, UUID claimed) {
    Object[] user = registered ? usersWith(subject).getFirst() : null;
    return new SessionDenied(
        code,
        OidcTestProvider.issuer(),
        subject,
        sid,
        user == null ? null : (UUID) user[0],
        user == null ? null : (UUID) user[2],
        claimed,
        null,
        null,
        null);
  }

  private List<SessionDenied> deniedFor(String sid) {
    return events.of(SessionDenied.class).stream().filter(e -> sid.equals(e.sid())).toList();
  }

  private static String newSubject() {
    return "sujeto-" + UUID.randomUUID();
  }

  private static String sid(String token) {
    return OidcTestProvider.sessionIdOf(token);
  }

  private static List<Object[]> usersWith(String subject) {
    return LivDatabase.readAsOwner(
        "SELECT id, identity_issuer, administration_id, status, full_name, email"
            + " FROM core.admin_user WHERE identity_subject = ? AND identity_issuer = ?",
        subject,
        OidcTestProvider.issuer());
  }

  private static Instant lastLoginOf(String subject) {
    Timestamp lastLogin =
        (Timestamp)
            LivDatabase.readAsOwner(
                    "SELECT last_login_at FROM core.admin_user"
                        + " WHERE identity_subject = ? AND identity_issuer = ?",
                    subject,
                    OidcTestProvider.issuer())
                .getFirst()[0];
    return lastLogin == null ? null : lastLogin.toInstant();
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

  private static void await(CountDownLatch gate) {
    try {
      gate.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
