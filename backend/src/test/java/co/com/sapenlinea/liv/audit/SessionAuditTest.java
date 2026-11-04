package co.com.sapenlinea.liv.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import co.com.sapenlinea.liv.audit.application.RecordSessionEvents;
import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.core.SessionStarted;
import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * 001-18 for the events of LIV: every session start, refusal and end, and every first login, ends
 * in the immutable audit with its user (or none), date, result and correlation id. The sessions go
 * through the internal API, as the BFF calls it; the audit is written after the commit, by an
 * asynchronous listener fed by the JDBC outbox.
 */
@DisplayName("Auditoría de las sesiones de LIV (T15)")
class SessionAuditTest extends PlatformWebTest {

  private static final String SESSIONS = "/internal/v1/sessions";

  @Autowired RecordSessionEvents record;
  @Autowired ApplicationContext context;

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un inicio de sesión queda auditado con usuario, fecha, resultado y correlación")
  void sessionStartIsAudited() {
    String correlation = UUID.randomUUID().toString();
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    assertThat(start(token, correlation).statusCode()).isIn(200, 201);

    AuditRows.Row row = AuditRows.await("SESSION_STARTED", correlation);
    assertThat(row.source()).isEqualTo("LIV");
    assertThat(row.entityName()).isEqualTo("session");
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isEqualTo(AuditRows.userId(ACTIVE));
    assertThat(row.newValues()).isEqualTo(Map.of("result", "SUCCESS"));
    assertThat(row.occurredAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    assertThat(row.actorRef()).isNull();
    assertThat(row.text()).doesNotContain(ACTIVE);
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · El primer ingreso deja auditados el registro del usuario y su inicio de sesión")
  void firstLoginIsAudited() {
    String correlation = UUID.randomUUID().toString();
    String subject = "auditado-" + UUID.randomUUID();
    String token = OidcTestProvider.userToken(subject, LivDatabase.ADMIN_A);

    assertThat(start(token, correlation).statusCode()).isEqualTo(201);

    UUID userId = AuditRows.userId(subject);
    AuditRows.Row provisioned = AuditRows.await("USER_PROVISIONED", correlation);
    assertThat(provisioned.entityName()).isEqualTo("user");
    assertThat(provisioned.entityId()).isEqualTo(userId);
    assertThat(provisioned.userId()).isEqualTo(userId);
    assertThat(provisioned.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(provisioned.newValues()).isEqualTo(Map.of("result", "SUCCESS"));
    assertThat(AuditRows.await("SESSION_STARTED", correlation).userId()).isEqualTo(userId);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"LOGOUT", "IDLE", "ABSOLUTE", "PROVIDER"})
  @Tag("001-18")
  @DisplayName("001-18 · El fin de sesión queda auditado con su motivo")
  void sessionEndIsAudited(String reason) {
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    assertThat(start(token, UUID.randomUUID().toString()).statusCode()).isIn(200, 201);
    String correlation = UUID.randomUUID().toString();

    assertThat(end(OidcTestProvider.sessionIdOf(token), ACTIVE, reason, correlation).statusCode())
        .isEqualTo(204);

    AuditRows.Row row = AuditRows.await("SESSION_ENDED", correlation);
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isEqualTo(AuditRows.userId(ACTIVE));
    assertThat(row.newValues()).isEqualTo(Map.of("result", "SUCCESS", "reason", reason));
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un rechazo de un usuario registrado queda en su administración, con el motivo")
  void refusalOfARegisteredUserIsAudited() {
    String correlation = UUID.randomUUID().toString();
    String token = OidcTestProvider.userToken(INACTIVE, LivDatabase.ADMIN_A);

    assertThat(start(token, correlation).statusCode()).isEqualTo(403);

    AuditRows.Row row = AuditRows.await("SESSION_DENIED", correlation);
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isEqualTo(AuditRows.userId(INACTIVE));
    assertThat(row.newValues()).isEqualTo(Map.of("result", "DENIED", "reason", "001-05"));
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un rechazo de un usuario no registrado queda en la administración que dice el token")
  void refusalOfAnUnknownUserInAnExistingAdministration() {
    String correlation = UUID.randomUUID().toString();
    String subject = "largo-" + UUID.randomUUID();
    String token =
        OidcTestProvider.userToken(
            subject, LivDatabase.ADMIN_A, claims -> claims.claim("name", "N".repeat(151)));

    assertThat(start(token, correlation).statusCode()).isEqualTo(401);

    AuditRows.Row row = AuditRows.await("SESSION_DENIED", correlation);
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isNull();
    assertThat(row.actorRef()).isNull();
    assertThat(row.newValues()).isEqualTo(Map.of("result", "DENIED", "reason", "001-14"));
    assertThat(row.text()).doesNotContain(subject);
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un rechazo sin administración válida queda como fila de plataforma, sin actor")
  void refusalWithoutAValidAdministrationIsAPlatformRow() {
    String unknownCorrelation = UUID.randomUUID().toString();
    String missingCorrelation = UUID.randomUUID().toString();
    String subject = "sin-administracion-" + UUID.randomUUID();
    String withUnknown = OidcTestProvider.userToken(subject, UUID.randomUUID());
    String withoutClaim =
        OidcTestProvider.userToken(
            subject, LivDatabase.ADMIN_A, claims -> claims.claim("administration_id", null));

    assertThat(start(withUnknown, unknownCorrelation).statusCode()).isEqualTo(403);
    assertThat(start(withoutClaim, missingCorrelation).statusCode()).isEqualTo(403);

    for (String correlation : List.of(unknownCorrelation, missingCorrelation)) {
      AuditRows.Row row = AuditRows.await("SESSION_DENIED", correlation);
      assertThat(row.administrationId()).isNull();
      assertThat(row.userId()).isNull();
      assertThat(row.actorRef()).isNull();
      assertThat(row.source()).isEqualTo("LIV");
      assertThat(row.newValues()).isEqualTo(Map.of("result", "DENIED", "reason", "001-17"));
      assertThat(row.text()).doesNotContain(subject);
    }
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Un evento que llega dos veces se audita una sola vez")
  void listenerIsIdempotent() {
    String correlation = UUID.randomUUID().toString();
    SessionStarted started =
        new SessionStarted(
            "sid-repetido-" + UUID.randomUUID(),
            AuditRows.userId(ACTIVE),
            LivDatabase.ADMIN_A,
            Instant.now(),
            UUID.randomUUID(),
            correlation);
    SessionDenied denied =
        new SessionDenied(
            "001-17",
            "sid-negado-" + UUID.randomUUID(),
            null,
            null,
            UUID.randomUUID(),
            Instant.now(),
            UUID.randomUUID(),
            correlation);

    for (int attempt = 0; attempt < 2; attempt++) {
      record.started(started);
      record.denied(denied);
    }

    assertThat(AuditRows.byCorrelation(correlation))
        .extracting(AuditRows.Row::operation)
        .containsExactlyInAnyOrder("SESSION_STARTED", "SESSION_DENIED");
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · El outbox borra la entrega una vez auditada: no guarda los eventos para siempre")
  void outboxDeletesTheCompletedDelivery() {
    String correlation = UUID.randomUUID().toString();
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);

    start(token, correlation);
    AuditRows.await("SESSION_STARTED", correlation);

    assertThat(
            AuditRows.awaitZero(
                "SELECT count(*) FROM platform.event_publication WHERE serialized_event LIKE ?",
                "%" + correlation + "%"))
        .isZero();
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El outbox trabaja con PostgreSQL sin consultar la base al arrancar")
  void outboxDatabaseTypeIsFixed() {
    assertThat(context.getBean("databaseType")).hasToString("POSTGRES");
  }

  private HttpResponse<String> start(String userToken, String correlation) {
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("X-User-Token", userToken);
    headers.put("X-Correlation-Id", correlation);
    return post(SESSIONS, headers, "{\"sid\":\"" + OidcTestProvider.sessionIdOf(userToken) + "\"}");
  }

  private HttpResponse<String> end(String sid, String subject, String reason, String correlation) {
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    headers.put("X-Correlation-Id", correlation);
    return post(
        SESSIONS + "/" + sid + "/end",
        headers,
        "{\"issuer\":\"%s\",\"subject\":\"%s\",\"reason\":\"%s\"}"
            .formatted(OidcTestProvider.issuer(), subject, reason));
  }
}
