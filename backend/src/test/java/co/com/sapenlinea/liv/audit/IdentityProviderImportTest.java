package co.com.sapenlinea.liv.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents;
import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents.ImportResult;
import co.com.sapenlinea.liv.audit.infrastructure.IdentityProviderEventsJob;
import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.Kind;
import co.com.sapenlinea.liv.platform.IdentityProviderEvents.ProviderEvent;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
  @Autowired ImportIdentityProviderEvents importer;
  @Autowired IdentityProviderEventsJob job;

  @BeforeEach
  void freshCursor() {
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofHours(1)));
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
      "001-18 · Un cambio de rol o de estado en la consola guarda solo la lista blanca y el autor opaco")
  void administratorChangesKeepOnlyTheWhitelist() {
    String roles = UUID.randomUUID().toString();
    String state = UUID.randomUUID().toString();
    adminEvents(
        new ProviderEvent(
            roles,
            Kind.ADMIN,
            "REALM_ROLE_MAPPING_CREATE",
            recent(),
            ACTIVE,
            null,
            "198.51.100.20",
            null,
            "administrador-del-proveedor",
            List.of("AUDITOR"),
            null),
        new ProviderEvent(
            state,
            Kind.ADMIN,
            "USER_UPDATE",
            recent(),
            ACTIVE,
            null,
            null,
            null,
            "administrador-del-proveedor",
            null,
            false));

    importer.importNew();

    AuditRows.Row grant = only(AuditRows.ofProviderEvent("admin:" + roles));
    assertThat(grant.operation()).isEqualTo("REALM_ROLE_MAPPING_CREATE");
    assertThat(grant.administrationId()).isEqualTo(LivDatabase.ADMIN_A);
    assertThat(grant.userId()).isEqualTo(AuditRows.userId(ACTIVE));
    assertThat(grant.newValues())
        .isEqualTo(
            Map.of(
                "result",
                "SUCCESS",
                "roles",
                List.of("AUDITOR"),
                "truncated_ip",
                "198.51.100.0/24"));
    assertThat(grant.actorRef()).isEqualTo(hmac("administrador-del-proveedor"));
    assertThat(grant.text()).doesNotContain("administrador-del-proveedor");
    AuditRows.Row disabled = only(AuditRows.ofProviderEvent("admin:" + state));
    assertThat(disabled.newValues()).isEqualTo(Map.of("result", "SUCCESS", "enabled", false));
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
  @DisplayName("001-18 · La primera importación empieza en la retención del proveedor (30 días)")
  void firstImportStartsAtTheRetention() {
    LivDatabase.setImportCursor(null);

    importer.importNew();

    ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
    verify(provider, atLeastOnce()).userEvents(from.capture(), anyInt());
    assertThat(from.getValue())
        .isCloseTo(Instant.now().minus(Duration.ofDays(30)), within(1, ChronoUnit.MINUTES));
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El importador avisa si se atrasa más de un día")
  void importerReportsWhenBehind() {
    LivDatabase.setImportCursor(Instant.now().minus(Duration.ofDays(2)));
    assertThat(importer.importNew().behind()).isTrue();

    userEvents(failedLogin(UUID.randomUUID().toString(), recent(), null, "al-dia", "203.0.113.3"));
    assertThat(importer.importNew().behind()).isFalse();
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · El trabajo programado toma el candado de ShedLock antes de importar")
  void jobTakesTheLock() {
    job.run();

    assertThat(
            LivDatabase.readAsOwner(
                "SELECT name FROM platform.shedlock WHERE name = 'identity-provider-events'"))
        .hasSize(1);
  }

  private void userEvents(ProviderEvent... events) {
    when(provider.userEvents(any(), anyInt())).thenReturn(List.of(events));
  }

  private void adminEvents(ProviderEvent... events) {
    when(provider.adminEvents(any(), anyInt())).thenReturn(List.of(events));
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
        null,
        null,
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
