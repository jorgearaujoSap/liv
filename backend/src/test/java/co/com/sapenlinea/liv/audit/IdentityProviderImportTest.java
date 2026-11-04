package co.com.sapenlinea.liv.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.sapenlinea.liv.audit.application.AuditLog;
import co.com.sapenlinea.liv.audit.application.AuditProperties;
import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents;
import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents.ImportResult;
import co.com.sapenlinea.liv.audit.infrastructure.IdentityProviderEventsJob;
import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.EventsPage;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.Kind;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.ProviderEvent;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 001-18 for the events of the identity provider: the importer copies failed logins, lockouts and
 * changes of administrators to the audit, masked (the attempted username as an HMAC with its
 * length, the address truncated, the author as an opaque reference), in the administration of the
 * user when LIV knows it, and once. The provider is replaced by a fake port; the adapter against a
 * real Keycloak is tested apart.
 */
@DisplayName("Importador de eventos del proveedor de identidad (T15)")
@TestPropertySource(
    properties = {
      "liv.audit.importer.enabled=true",
      // The schedule must not run during the test: the tests call the importer themselves.
      "liv.audit.importer.interval=PT24H",
      "liv.audit.importer.initial-delay=PT24H",
      "liv.audit.importer.client-secret=sin-uso",
      "liv.audit.hmac-key=" + IdentityProviderImportTest.KEY
    })
class IdentityProviderImportTest extends PlatformWebTest {

  static final String KEY = "clave-hmac-de-prueba-con-mas-de-32-caracteres";

  @MockitoBean IdentityProviderEvents provider;
  @MockitoSpyBean AuditLog log;
  @Autowired MeterRegistry metrics;
  @Autowired ImportIdentityProviderEvents importer;
  @Autowired IdentityProviderEventsJob job;
  @Autowired AuditProperties properties;

  @BeforeEach
  void freshCursorAndQuietProvider() {
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofHours(1)));
    LivDatabase.releaseLock(IdentityProviderEventsJob.LOCK_NAME);
    userEvents();
    adminEvents();
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un intento fallido de un usuario desconocido queda enmascarado, como fila de plataforma")
  void failedLoginOfAnUnknownUser() {
    Instant time = recent();
    String id = UUID.randomUUID().toString();
    userEvents(failedLogin(id, time, null, " Juan.Perez@Example.com", "203.0.113.77"));

    importer.importNew();

    AuditRows.Row row = only(AuditRows.ofProviderEvent("user:" + id));
    assertThat(row.administrationId()).isNull();
    assertThat(row.userId()).isNull();
    assertThat(row.entityName()).isEqualTo("identity_provider");
    assertThat(row.operation()).isEqualTo("LOGIN_ERROR");
    assertThat(row.occurredAt()).isCloseTo(time, within(1, ChronoUnit.MILLIS));
    assertThat(row.correlationId()).isNull();
    assertThat(row.newValues())
        .isEqualTo(
            Map.of(
                "result", "FAILURE",
                "reason", "invalid_user_credentials",
                "truncated_ip", "203.0.113.0/24",
                "masked_attempted_username",
                    Map.of("hmac", hmac("juan.perez@example.com"), "length", 22)));
    assertThat(row.text().toLowerCase())
        .doesNotContain("juan.perez", "example.com", "203.0.113.77");
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Un intento fallido de un usuario de LIV queda en su administración")
  void failedLoginOfAKnownUser() {
    String id = UUID.randomUUID().toString();
    userEvents(failedLogin(id, recent(), ACTIVE, "usuario", "2001:db8:abcd:12:0:0:0:1"));

    importer.importNew();

    AuditRows.Row row = only(AuditRows.ofProviderEvent("user:" + id));
    assertThat(row.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(row.userId()).isEqualTo(AuditRows.userId(ACTIVE));
    assertThat(row.newValues()).containsEntry("truncated_ip", "2001:db8:abcd::/48");
    assertThat(row.text()).doesNotContain(ACTIVE, "2001:db8:abcd:12");
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Un bloqueo del proveedor queda auditado como bloqueo")
  void lockoutIsAudited() {
    String id = UUID.randomUUID().toString();
    userEvents(
        new ProviderEvent(
            id,
            Kind.USER,
            "USER_DISABLED_BY_TEMPORARY_LOCKOUT",
            recent(),
            ACTIVE,
            null,
            null,
            null,
            null));

    importer.importNew();

    AuditRows.Row row = only(AuditRows.ofProviderEvent("user:" + id));
    assertThat(row.operation()).isEqualTo("USER_DISABLED_BY_TEMPORARY_LOCKOUT");
    assertThat(row.newValues()).isEqualTo(Map.of("result", "LOCKED"));
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un cambio de rol en la consola queda con su usuario, el autor opaco y solo la lista blanca")
  void administratorChangesKeepOnlyTheWhitelist() {
    String id = UUID.randomUUID().toString();
    adminEvents(
        new ProviderEvent(
            id,
            Kind.ADMIN,
            "REALM_ROLE_MAPPING_CREATE",
            recent(),
            ACTIVE,
            null,
            "198.51.100.20",
            null,
            "administrador-del-proveedor"));

    importer.importNew();

    AuditRows.Row grant = only(AuditRows.ofProviderEvent("admin:" + id));
    assertThat(grant.operation()).isEqualTo("REALM_ROLE_MAPPING_CREATE");
    assertThat(grant.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(grant.userId()).isEqualTo(AuditRows.userId(ACTIVE));
    assertThat(grant.newValues())
        .isEqualTo(Map.of("result", "SUCCESS", "truncated_ip", "198.51.100.0/24"));
    assertThat(grant.actorRef()).isEqualTo(hmac("administrador-del-proveedor"));
    assertThat(grant.text()).doesNotContain("administrador-del-proveedor");
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Un evento importado dos veces queda una sola vez")
  void importIsIdempotent() {
    String id = UUID.randomUUID().toString();
    userEvents(failedLogin(id, recent(), null, "repetido", "203.0.113.1"));

    importer.importNew();
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofHours(1)));
    importer.importNew();

    assertThat(AuditRows.ofProviderEvent("user:" + id)).hasSize(1);
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El cursor avanza y la consulta siguiente se superpone 5 minutos")
  void cursorAdvancesWithAnOverlap() {
    Instant newest = recent();
    userEvents(failedLogin(UUID.randomUUID().toString(), newest, null, "uno", "203.0.113.2"));

    ImportResult first = importer.importNew();
    importer.importNew();

    assertThat(first.cursor()).isCloseTo(newest, within(1, ChronoUnit.MILLIS));
    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    verify(provider, atLeastOnce()).userEvents(from.capture(), anyInt());
    assertThat(from.getAllValues().getLast())
        .isCloseTo(newest.minus(Duration.ofMinutes(5)), within(1, ChronoUnit.MILLIS));
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · La primera importación empieza un día antes de que el proveedor borre (29 de 30 días)")
  void firstImportStartsAtTheRetention() {
    LivDatabase.setImportCursor(null);
    // Keycloak deletes events after 30 days: reading from its very edge, it could delete some
    // between two pages and shift the offsets.
    assertThat(properties.importer().retention()).isLessThan(Duration.ofDays(30));

    importer.importNew();

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    verify(provider, atLeastOnce()).userEvents(from.capture(), anyInt());
    assertThat(from.getValue())
        .isCloseTo(
            Instant.now().minus(properties.importer().retention()), within(1, ChronoUnit.MINUTES));
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El importador avisa si se atrasa más de un día")
  void importerReportsWhenBehind() {
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofDays(2)));
    Instant stuck = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MILLIS);
    // The provider has more than a run reads, all two days old: the watermark stays there.
    when(provider.userEvents(any(), anyInt()))
        .thenReturn(
            new EventsPage(
                IntStream.range(0, properties.importer().pageSize())
                    .mapToObj(
                        i -> failedLogin(UUID.randomUUID().toString(), stuck, null, "u" + i, null))
                    .toList(),
                true));

    ImportResult result = importer.importNew();

    assertThat(result.behind()).isTrue();
    assertThat(result.cursor()).isCloseTo(stuck, within(1, ChronoUnit.MILLIS));
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Sin eventos nuevos el importador está al día, aunque el último sea viejo")
  void quietProviderIsNotBehind() {
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofDays(2)));

    assertThat(importer.importNew().behind()).isFalse();
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Una ráfaga con más eventos de los que lee una corrida se importa entera en las siguientes")
  void denseBurstIsImportedInTheFollowingRuns() {
    Instant start = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS);
    LivDatabase.setImportCursor(start.minus(Duration.ofMinutes(1)));
    int perRun = properties.importer().pageSize() * properties.importer().maxPages();
    // More than a run reads, all within less than the overlap of 5 minutes.
    List<ProviderEvent> burst =
        IntStream.range(0, perRun + perRun / 4)
            .mapToObj(i ->
                failedLogin(
                    UUID.randomUUID().toString(),
                    start.plusMillis(100L * i),
                    null,
                    "rafaga" + i,
                    null))
            .toList();
    when(provider.userEvents(any(), anyInt()))
        .thenAnswer(call -> pageOf(burst, call.getArgument(0), call.getArgument(1)));

    ImportResult first = importer.importNew();
    ImportResult second = importer.importNew();

    assertThat(first.cursor()).isBefore(burst.getLast().time());
    assertThat(second.cursor()).isAfterOrEqualTo(burst.getLast().time());
    assertThat(
            AuditRows.ofProviderEvent("user:" + burst.getFirst().id()).size()
                + AuditRows.ofProviderEvent("user:" + burst.getLast().id()).size())
        .isEqualTo(2);
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Si la base falla al guardar un evento, la corrida se detiene y no deja una fila incompleta")
  void databaseFailureStopsTheRunWithoutAMinimalRow() {
    String id = UUID.randomUUID().toString();
    userEvents(failedLogin(id, recent(), null, "sin-base", "203.0.113.5"));
    doThrow(new DataAccessResourceFailureException("base de datos caída"))
        .doCallRealMethod()
        .when(log)
        .append(argThat(entry -> ("user:" + id).equals(entry.sourceEventId())));

    assertThatThrownBy(importer::importNew).isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(AuditRows.ofProviderEvent("user:" + id)).isEmpty();
  }

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Un evento que la auditoría rechaza queda como fila mínima de plataforma, y la corrida sigue")
  void rejectedEventIsKeptAsAMinimalPlatformRow() {
    String rejected = UUID.randomUUID().toString();
    String next = UUID.randomUUID().toString();
    userEvents(
        failedLogin(rejected, recent(), ACTIVE, "rechazado", "203.0.113.6"),
        failedLogin(next, recent().plusMillis(1), null, "siguiente", "203.0.113.7"));
    doThrow(new DataIntegrityViolationException("viola una restricción"))
        .doCallRealMethod()
        .when(log)
        .append(argThat(entry -> ("user:" + rejected).equals(entry.sourceEventId())));

    importer.importNew();

    AuditRows.Row minimal = only(AuditRows.ofProviderEvent("user:" + rejected));
    assertThat(minimal.administrationId()).isNull();
    assertThat(minimal.userId()).isNull();
    assertThat(minimal.newValues()).isEqualTo(Map.of("result", "FAILURE"));
    assertThat(AuditRows.ofProviderEvent("user:" + next)).hasSize(1);
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El atraso se mide desde la marca de agua compartida, en cualquier réplica")
  void lagIsMeasuredFromTheSharedWatermark() {
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofDays(2)));

    double lag = metrics.get("liv.audit.importer.lag").gauge().value();

    assertThat(lag).isGreaterThan(Duration.ofDays(1).toSeconds());
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El reloj adelantado del proveedor no adelanta la marca de agua")
  void providerClockAheadDoesNotMoveTheWatermarkPastNow() {
    Instant ahead = Instant.now().plus(Duration.ofHours(1));
    userEvents(failedLogin(UUID.randomUUID().toString(), ahead, null, "futuro", "203.0.113.4"));

    ImportResult result = importer.importNew();

    assertThat(result.cursor()).isBefore(ahead).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El trabajo programado toma el candado de ShedLock antes de importar")
  void jobTakesTheLock() {
    job.run();

    assertThat(
            LivDatabase.readAsOwner(
                "SELECT name FROM platform.shedlock WHERE name = ? AND lock_until > locked_at",
                IdentityProviderEventsJob.LOCK_NAME))
        .hasSize(1);
    verify(provider, atLeastOnce()).userEvents(any(), anyInt());
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Con el candado tomado por otra réplica, el trabajo no importa")
  void jobDoesNotRunWhileAnotherReplicaHoldsTheLock() {
    LivDatabase.holdLock(IdentityProviderEventsJob.LOCK_NAME, Duration.ofMinutes(5));

    job.run();

    verify(provider, never()).userEvents(any(), anyInt());
    verify(provider, never()).adminEvents(any(), anyInt());
  }

  /** What a provider with {@code all} answers: from {@code from} on, at most {@code limit}. */
  private static EventsPage pageOf(List<ProviderEvent> all, Instant from, int limit) {
    List<ProviderEvent> after = all.stream().filter(e -> !e.time().isBefore(from)).toList();
    return new EventsPage(after.subList(0, Math.min(limit, after.size())), after.size() > limit);
  }

  /** Everything the provider has: no more after these. */
  private void userEvents(ProviderEvent... events) {
    when(provider.userEvents(any(), anyInt())).thenReturn(new EventsPage(List.of(events), false));
  }

  private void adminEvents(ProviderEvent... events) {
    when(provider.adminEvents(any(), anyInt())).thenReturn(new EventsPage(List.of(events), false));
  }

  private static ProviderEvent failedLogin(
      String id, Instant time, String subject, String username, String ip) {
    return new ProviderEvent(
        id,
        Kind.USER,
        "LOGIN_ERROR",
        time,
        subject,
        username,
        ip,
        "invalid_user_credentials",
        null);
  }

  /** A time a minute ago, at the precision the database keeps. */
  private static Instant recent() {
    return Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
  }

  private static AuditRows.Row only(List<AuditRows.Row> rows) {
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  /** HMAC-SHA256 with the key of the test, in hexadecimal, written apart from the code. */
  private static String hmac(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
