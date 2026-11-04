package co.com.sapenlinea.liv.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.audit.application.AuditLog;
import co.com.sapenlinea.liv.audit.application.AuditLog.Entry;
import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The mark of a platform job ({@code app.platform_job}, doc. 02 §5.1) never outlives the
 * transaction of the audit (T10 › for T15): with a pool of one connection, the transaction right
 * after a platform row, in the same physical connection, sees no mark. And the audit refuses to
 * join a transaction of someone else.
 */
@DisplayName("Marca de trabajo de plataforma en la auditoría (T15)")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class PlatformJobMarkTest extends PlatformWebTest {

  @Autowired AuditLog log;
  @Autowired JdbcClient jdbc;
  @Autowired PlatformTransactionManager transactions;

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · Tras una fila de plataforma, la siguiente transacción en la misma conexión no tiene la marca")
  void markDoesNotOutliveItsTransaction() {
    String id = "user:" + UUID.randomUUID();

    log.append(platformRow(id));

    assertThat(AuditRows.ofProviderEvent(id)).hasSize(1);
    TransactionTemplate next = new TransactionTemplate(transactions);
    String withoutContext = next.execute(status -> mark());
    String inAnAdministration;
    try (var scope = AdministrationContext.open(LivDatabase.ADMIN_A)) {
      inAnAdministration = next.execute(status -> mark());
    }
    assertThat(withoutContext).isEmpty();
    assertThat(inAnAdministration).isEmpty();
    assertThat(mark()).as("fuera de una transacción").isIn("", null);
  }

  @Test
  @Tag("001-18")
  @DisplayName("001-18 · La auditoría no se escribe dentro de la transacción de otro")
  void auditRefusesToJoinATransaction() {
    TransactionTemplate other = new TransactionTemplate(transactions);

    assertThatThrownBy(() ->
            other.executeWithoutResult(
                status -> log.append(platformRow("user:" + UUID.randomUUID()))))
        .isInstanceOf(IllegalStateException.class);
  }

  private String mark() {
    return jdbc.sql("SELECT current_setting('app.platform_job', true)")
        .query(String.class)
        .optional()
        .orElse(null);
  }

  private static Entry platformRow(String sourceEventId) {
    return new Entry(
        "IDENTITY_PROVIDER",
        sourceEventId,
        null,
        null,
        "identity_provider",
        null,
        "LOGIN_ERROR",
        Instant.now(),
        null,
        null,
        Map.of("result", "FAILURE"));
  }
}
