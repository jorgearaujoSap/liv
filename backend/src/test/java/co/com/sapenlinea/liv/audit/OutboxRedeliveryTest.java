package co.com.sapenlinea.liv.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import co.com.sapenlinea.liv.audit.application.AuditLog;
import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import co.com.sapenlinea.liv.platform.persistence.OutboxResubmissionJob;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 001-18 after a failure: if the audit cannot be written when the event arrives (the database
 * down), the outbox keeps the event and the scheduled redelivery writes it later. The audit log is
 * the real one, failing only on its first call.
 */
@DisplayName("Reentrega del outbox a la auditoría (T15)")
@TestPropertySource(properties = "liv.outbox.resubmit-older-than=PT0S")
class OutboxRedeliveryTest extends PlatformWebTest {

  @MockitoSpyBean AuditLog log;
  @Autowired OutboxResubmissionJob redelivery;

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · Un evento que no se pudo auditar se audita en la reentrega")
  void failedDeliveryIsRedelivered() {
    doThrow(new DataAccessResourceFailureException("base de datos caída"))
        .doCallRealMethod()
        .when(log)
        .append(any());
    String correlation = UUID.randomUUID().toString();
    Map<String, String> headers = bearer(OidcTestProvider.serviceToken());
    String token = OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
    headers.put("X-User-Token", token);
    headers.put("X-Correlation-Id", correlation);

    post(
        "/internal/v1/sessions",
        headers,
        "{\"sid\":\"" + OidcTestProvider.sessionIdOf(token) + "\"}");
    // The first delivery fails: the event waits in the outbox, not in the audit.
    AuditRows.awaitCount(
        "SELECT count(*) FROM platform.event_publication"
            + " WHERE serialized_event LIKE ? AND completion_date IS NULL AND status = 'FAILED'",
        "%" + correlation + "%");
    assertThat(AuditRows.byCorrelationNow(correlation)).isEmpty();

    redelivery.run();

    assertThat(AuditRows.await("SESSION_STARTED", correlation).userId())
        .isEqualTo(AuditRows.userId(ACTIVE));
  }
}
