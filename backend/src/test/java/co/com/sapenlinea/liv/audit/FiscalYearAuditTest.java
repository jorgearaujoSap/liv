package co.com.sapenlinea.liv.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import co.com.sapenlinea.liv.audit.application.AuditLog;
import co.com.sapenlinea.liv.audit.application.RecordFiscalYearEvents;
import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import co.com.sapenlinea.liv.platform.persistence.OutboxResubmissionJob;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 007-20: every creation and change of state of a fiscal year ends in the immutable audit, on the
 * entity {@code tax.administration_fiscal_year} and the id of its row, with the states before and
 * after, the user, the administration, the instant and the correlation id of the operation. The
 * audit is written after the commit by a listener fed by the outbox, as the audit of the sessions;
 * the API tests go through the real endpoints, the others publish the events of {@code tax}.
 */
@DisplayName("Auditoría de las vigencias fiscales (007-20)")
@TestPropertySource(properties = "liv.outbox.resubmit-older-than=PT0S")
class FiscalYearAuditTest extends PlatformWebTest {

  private static final String ENTITY = "tax.administration_fiscal_year";
  private static final Instant OCCURRED_AT = Instant.parse("2026-03-04T05:06:07.123456Z");

  @MockitoSpyBean AuditLog log;
  @Autowired RecordFiscalYearEvents record;
  @Autowired OutboxResubmissionJob redelivery;
  @Autowired ApplicationEventPublisher events;
  @Autowired PlatformTransactionManager transactions;
  @Autowired Clock clock;

  @Value("${liv.business-zone}")
  ZoneId zone;

  @Test
  @Tag("007-20")
  @DisplayName(
      "007-20 · Crear una vigencia por la API queda auditado con su fila, usuario y correlación")
  void creationThroughTheApiIsAudited() {
    Tenant tenant = newTenant();
    int year = LocalDate.now(clock.withZone(zone)).getYear();
    String correlation = UUID.randomUUID().toString();

    HttpResponse<String> created =
        post("/api/v1/fiscal-years", tenant.headers(correlation), "{\"year\":" + year + "}");

    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    AuditRows.Row row = AuditRows.await("FISCAL_YEAR_CREATED", correlation);
    assertThat(row.entityName()).isEqualTo(ENTITY);
    assertThat(row.entityId()).isEqualTo(rowId(tenant, year));
    assertThat(row.administrationId()).isEqualTo(tenant.administrationId());
    assertThat(row.userId()).isEqualTo(AuditRows.userId(tenant.subject()));
    assertThat(row.previousValues()).isNull();
    assertThat(row.newValues()).isEqualTo(Map.of("year", year, "status", "PLANEADA"));
    assertThat(row.occurredAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    assertThat(row.source()).isEqualTo("LIV");
    assertThat(row.actorRef()).isNull();
  }

  @Test
  @Tag("007-20")
  @DisplayName(
      "007-20 · Abrir la preparación por la API queda auditado con el estado anterior y el nuevo")
  void openingThroughTheApiIsAudited() {
    Tenant tenant = newTenant();
    int year = LocalDate.now(clock.withZone(zone)).getYear();
    assertThat(
            post("/api/v1/fiscal-years", tenant.headers(null), "{\"year\":" + year + "}")
                .statusCode())
        .isEqualTo(201);
    String correlation = UUID.randomUUID().toString();

    HttpResponse<String> opened =
        post("/api/v1/fiscal-years/" + year + "/preparation", tenant.headers(correlation));

    assertThat(opened.statusCode()).as(opened.body()).isEqualTo(200);
    AuditRows.Row row = AuditRows.await("FISCAL_YEAR_STATUS_CHANGED", correlation);
    assertThat(row.entityName()).isEqualTo(ENTITY);
    assertThat(row.entityId()).isEqualTo(rowId(tenant, year));
    assertThat(row.administrationId()).isEqualTo(tenant.administrationId());
    assertThat(row.userId()).isEqualTo(AuditRows.userId(tenant.subject()));
    assertThat(row.previousValues()).isEqualTo(Map.of("status", "PLANEADA"));
    assertThat(row.newValues()).isEqualTo(Map.of("year", year, "status", "EN_PREPARACION"));
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Cada campo de la fila sale del evento de creación, sin estado anterior")
  void createdEventFillsEveryColumn() {
    String correlation = UUID.randomUUID().toString();
    UUID rowId = UUID.randomUUID();
    UUID userId = AuditRows.userId(ACTIVE);
    UUID eventId = UUID.randomUUID();

    inTransaction(
        new FiscalYearCreated(
            eventId,
            LivDatabase.ADMIN_A,
            userId,
            rowId,
            2026,
            null,
            "PLANEADA",
            OCCURRED_AT,
            correlation));

    AuditRows.Row row = AuditRows.await("FISCAL_YEAR_CREATED", correlation);
    assertThat(row.source()).isEqualTo("LIV");
    assertThat(row.sourceEventId()).isEqualTo(eventId.toString());
    assertThat(row.entityName()).isEqualTo(ENTITY);
    assertThat(row.entityId()).isEqualTo(rowId);
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isEqualTo(userId);
    assertThat(row.previousValues()).isNull();
    assertThat(row.newValues()).isEqualTo(Map.of("year", 2026, "status", "PLANEADA"));
    assertThat(row.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(row.correlationId()).isEqualTo(correlation);
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Cada campo de la fila sale del evento de cambio de estado")
  void statusChangedEventFillsEveryColumn() {
    String correlation = UUID.randomUUID().toString();
    UUID rowId = UUID.randomUUID();
    UUID userId = AuditRows.userId(ACTIVE);
    UUID eventId = UUID.randomUUID();

    inTransaction(
        new FiscalYearStatusChanged(
            eventId,
            LivDatabase.ADMIN_A,
            userId,
            rowId,
            2027,
            "PLANEADA",
            "EN_PREPARACION",
            OCCURRED_AT,
            correlation));

    AuditRows.Row row = AuditRows.await("FISCAL_YEAR_STATUS_CHANGED", correlation);
    assertThat(row.sourceEventId()).isEqualTo(eventId.toString());
    assertThat(row.entityName()).isEqualTo(ENTITY);
    assertThat(row.entityId()).isEqualTo(rowId);
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isEqualTo(userId);
    assertThat(row.previousValues()).isEqualTo(Map.of("status", "PLANEADA"));
    assertThat(row.newValues()).isEqualTo(Map.of("year", 2027, "status", "EN_PREPARACION"));
    assertThat(row.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(row.correlationId()).isEqualTo(correlation);
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Un evento que llega dos veces se audita una sola vez, por su eventId")
  void listenerIsIdempotentByEventId() {
    String correlation = UUID.randomUUID().toString();
    UUID userId = AuditRows.userId(ACTIVE);
    UUID rowId = UUID.randomUUID();
    var created =
        new FiscalYearCreated(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            userId,
            rowId,
            2026,
            null,
            "PLANEADA",
            OCCURRED_AT,
            correlation);
    var changed =
        new FiscalYearStatusChanged(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            userId,
            rowId,
            2026,
            "PLANEADA",
            "EN_PREPARACION",
            OCCURRED_AT,
            correlation);

    for (int attempt = 0; attempt < 2; attempt++) {
      record.created(created);
      record.statusChanged(changed);
    }

    assertThat(AuditRows.byCorrelation(correlation))
        .extracting(AuditRows.Row::operation)
        .containsExactlyInAnyOrder("FISCAL_YEAR_CREATED", "FISCAL_YEAR_STATUS_CHANGED");
  }

  @Test
  @Tag("007-20")
  @DisplayName(
      "007-20 · Dos cambios de la misma fila con distinto eventId son dos filas de auditoría")
  void sameRowAndOperationWithAnotherEventIdIsAnotherAuditRow() {
    String correlation = UUID.randomUUID().toString();
    UUID rowId = UUID.randomUUID();
    UUID userId = AuditRows.userId(ACTIVE);

    for (int change = 0; change < 2; change++) {
      record.statusChanged(
          new FiscalYearStatusChanged(
              UUID.randomUUID(),
              LivDatabase.ADMIN_A,
              userId,
              rowId,
              2026,
              "PLANEADA",
              "EN_PREPARACION",
              OCCURRED_AT,
              correlation));
    }

    assertThat(AuditRows.byCorrelation(correlation)).hasSize(2);
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Una creación nunca lleva estado anterior, aunque el evento lo traiga")
  void aCreationNeverHasAPreviousState() {
    String correlation = UUID.randomUUID().toString();

    record.created(
        new FiscalYearCreated(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            AuditRows.userId(ACTIVE),
            UUID.randomUUID(),
            2026,
            "PLANEADA",
            "PLANEADA",
            OCCURRED_AT,
            correlation));

    assertThat(AuditRows.await("FISCAL_YEAR_CREATED", correlation).previousValues()).isNull();
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Un cambio sin estado anterior o sin estado nuevo no se audita a medias")
  void aChangeNeedsBothStates() {
    UUID userId = AuditRows.userId(ACTIVE);
    var withoutPrevious =
        new FiscalYearStatusChanged(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            userId,
            UUID.randomUUID(),
            2026,
            null,
            "EN_PREPARACION",
            OCCURRED_AT,
            UUID.randomUUID().toString());
    var withoutNew =
        new FiscalYearStatusChanged(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            userId,
            UUID.randomUUID(),
            2026,
            "PLANEADA",
            null,
            OCCURRED_AT,
            UUID.randomUUID().toString());

    assertThatThrownBy(() -> record.statusChanged(withoutPrevious))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("previousStatus");
    assertThatThrownBy(() -> record.statusChanged(withoutNew))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("newStatus");
    assertThat(AuditRows.byCorrelationNow(withoutPrevious.correlationId())).isEmpty();
    assertThat(AuditRows.byCorrelationNow(withoutNew.correlationId())).isEmpty();
  }

  @Test
  @Tag("007-20")
  @DisplayName(
      "007-20 · Un evento de vigencia sin administración no se guarda como fila de plataforma")
  void anEventWithoutAdministrationIsNotAPlatformRow() {
    String correlation = UUID.randomUUID().toString();

    assertThatThrownBy(() ->
            record.created(
                new FiscalYearCreated(
                    UUID.randomUUID(),
                    null,
                    null,
                    UUID.randomUUID(),
                    2026,
                    null,
                    "PLANEADA",
                    OCCURRED_AT,
                    correlation)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(AuditRows.byCorrelationNow(correlation)).isEmpty();
  }

  @Test
  @Tag("007-20")
  @DisplayName("007-20 · Una creación cuya auditoría falla una vez se audita en la reentrega")
  void failedCreationIsRedelivered() {
    String correlation = UUID.randomUUID().toString();

    redeliveredAfterOneFailure(
        new FiscalYearCreated(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            AuditRows.userId(ACTIVE),
            UUID.randomUUID(),
            2026,
            null,
            "PLANEADA",
            OCCURRED_AT,
            correlation),
        correlation);

    assertThat(AuditRows.await("FISCAL_YEAR_CREATED", correlation).userId())
        .isEqualTo(AuditRows.userId(ACTIVE));
  }

  @Test
  @Tag("007-20")
  @DisplayName(
      "007-20 · Un cambio de estado cuya auditoría falla una vez se audita en la reentrega")
  void failedStatusChangeIsRedelivered() {
    String correlation = UUID.randomUUID().toString();

    redeliveredAfterOneFailure(
        new FiscalYearStatusChanged(
            UUID.randomUUID(),
            LivDatabase.ADMIN_A,
            AuditRows.userId(ACTIVE),
            UUID.randomUUID(),
            2026,
            "PLANEADA",
            "EN_PREPARACION",
            OCCURRED_AT,
            correlation),
        correlation);

    assertThat(AuditRows.await("FISCAL_YEAR_STATUS_CHANGED", correlation).previousValues())
        .isEqualTo(Map.of("status", "PLANEADA"));
  }

  /** The audit fails once for this event: it waits in the outbox, and the redelivery writes it. */
  private void redeliveredAfterOneFailure(Object event, String correlation) {
    // Only the event of this test fails, once: others still in flight are left alone.
    doThrow(new DataAccessResourceFailureException("base de datos caída"))
        .doCallRealMethod()
        .when(log)
        .append(argThat(entry -> correlation.equals(entry.correlationId())));

    inTransaction(event);
    // The first delivery fails: the event waits in the outbox, not in the audit.
    assertThat(
            AuditRows.awaitCount(
                "SELECT count(*) FROM platform.event_publication"
                    + " WHERE serialized_event LIKE ? AND status = 'FAILED'",
                "%" + correlation + "%"))
        .isPositive();
    assertThat(AuditRows.byCorrelationNow(correlation)).isEmpty();

    redelivery.run();
  }

  private void inTransaction(Object event) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(status -> events.publishEvent(event));
  }

  /** The id of the row of the year of the administration, as its owner reads it. */
  private static UUID rowId(Tenant tenant, int year) {
    return (UUID)
        LivDatabase.readAsOwner(
                "SELECT id FROM tax.administration_fiscal_year"
                    + " WHERE administration_id = ? AND fiscal_year = ?",
                tenant.administrationId(),
                year)
            .getFirst()[0];
  }

  /** An administration of its own with a registered user who administers the fiscal years. */
  private record Tenant(UUID administrationId, String subject) {

    Map<String, String> headers(String correlation) {
      Map<String, String> headers =
          bearer(
              OidcTestProvider.userToken(
                  subject,
                  administrationId,
                  claims -> claims.claim("roles", List.of("ADMIN_SISTEMA"))));
      if (correlation != null) {
        headers.put("X-Correlation-Id", correlation);
      }
      return headers;
    }
  }

  private static Tenant newTenant() {
    UUID administration = UUID.randomUUID();
    LivDatabase.registerAdministration(
        administration, "Administración auditoría " + administration);
    String subject = "audit-fy-" + administration;
    LivDatabase.registerUser(administration, OidcTestProvider.issuer(), subject, "ACTIVE");
    return new Tenant(administration, subject);
  }
}
