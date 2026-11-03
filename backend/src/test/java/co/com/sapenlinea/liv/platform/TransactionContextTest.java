package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The RLS context per transaction (doc. 02 §5.1, plan step 7): the transaction manager of {@code
 * platform} sets {@code app.administration_id} at the start of every transaction from {@link
 * AdministrationContext}, with local scope, so nothing stays on the pooled connection. With the
 * real {@code liv_app} role and the policies of the migrations.
 */
@DisplayName("Contexto RLS por transacción (T12)")
class TransactionContextTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", LivDatabase::jdbcUrl);
    registry.add("spring.datasource.password", LivDatabase::appPassword);
    registry.add("liv.security.issuer-uri", () -> "http://localhost:1/sin-proveedor");
  }

  @Nested
  @SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
  @Import(TransactionProbes.class)
  @DisplayName("Con una sola conexión en el pool")
  class SingleConnection {

    @Autowired RlsProbe probe;

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · La transacción de B, en la misma conexión, no ve el contexto de A")
    void requestOfAThenRequestOfB() {
      try (var a = AdministrationContext.open(LivDatabase.ADMIN_A)) {
        assertThat(probe.visibleAdministrations()).containsExactly(LivDatabase.ADMIN_A);
      }
      try (var b = AdministrationContext.open(LivDatabase.ADMIN_B)) {
        assertThat(probe.visibleAdministrations()).containsExactly(LivDatabase.ADMIN_B);
      }
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Sin contexto no se fija nada y las políticas no devuelven filas")
    void withoutContextNothingIsVisible() {
      try (var a = AdministrationContext.open(LivDatabase.ADMIN_A)) {
        probe.visibleAdministrations();
      }

      assertThat(probe.visibleAdministrations()).isEmpty();
      assertThat(probe.setting()).isNullOrEmpty();
    }
  }

  @Nested
  @SpringBootTest
  @Import(TransactionProbes.class)
  @DisplayName("Transacciones nuevas y oyentes")
  class NewTransactions {

    @Autowired RlsProbe probe;
    @Autowired Publisher publisher;
    @Autowired Listener listener;

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Una transacción REQUIRES_NEW también recibe el contexto")
    void requiresNew() {
      try (var a = AdministrationContext.open(LivDatabase.ADMIN_A)) {
        assertThat(probe.visibleInANewTransaction()).containsExactly(LivDatabase.ADMIN_A);
      }
    }

    @Test
    @Tag("001-17")
    @DisplayName("001-17 · Un oyente asíncrono usa la administración del evento, no la del hilo")
    void asynchronousListener() throws Exception {
      try (var a = AdministrationContext.open(LivDatabase.ADMIN_B)) {
        publisher.publish(new AdministrationEvent(LivDatabase.ADMIN_B));
      }

      assertThat(listener.withEventContext().get(10, TimeUnit.SECONDS))
          .containsExactly(LivDatabase.ADMIN_B);
      assertThat(listener.withoutContext().get(10, TimeUnit.SECONDS)).isEmpty();
    }
  }

  record AdministrationEvent(UUID administrationId) {}

  static class RlsProbe {
    private final JdbcClient jdbc;
    private final NewTransactionProbe inner;

    RlsProbe(JdbcClient jdbc, NewTransactionProbe inner) {
      this.jdbc = jdbc;
      this.inner = inner;
    }

    @Transactional(readOnly = true)
    public Set<UUID> visibleAdministrations() {
      return administrations(jdbc);
    }

    @Transactional(readOnly = true)
    public String setting() {
      return jdbc.sql("SELECT current_setting('app.administration_id', true)")
          .query(String.class)
          .list()
          .getFirst();
    }

    @Transactional(readOnly = true)
    public Set<UUID> visibleInANewTransaction() {
      return inner.visibleAdministrations();
    }
  }

  static class NewTransactionProbe {
    private final JdbcClient jdbc;

    NewTransactionProbe(JdbcClient jdbc) {
      this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Set<UUID> visibleAdministrations() {
      return administrations(jdbc);
    }
  }

  static class Publisher {
    private final ApplicationEventPublisher events;

    Publisher(ApplicationEventPublisher events) {
      this.events = events;
    }

    @Transactional
    public void publish(AdministrationEvent event) {
      events.publishEvent(event);
    }
  }

  /** Like a listener of another module: it opens the event's administration before its work. */
  static class Listener {
    private final CompletableFuture<Set<UUID>> withEventContext = new CompletableFuture<>();
    private final CompletableFuture<Set<UUID>> withoutContext = new CompletableFuture<>();
    private final RlsProbe probe;

    Listener(RlsProbe probe) {
      this.probe = probe;
    }

    // Through methods: the @Async proxy does not carry the fields of the bean.
    public CompletableFuture<Set<UUID>> withEventContext() {
      return withEventContext;
    }

    public CompletableFuture<Set<UUID>> withoutContext() {
      return withoutContext;
    }

    @Async
    @TransactionalEventListener
    public void on(AdministrationEvent event) {
      withoutContext.complete(probe.visibleAdministrations());
      try (var scope = AdministrationContext.open(event.administrationId())) {
        withEventContext.complete(probe.visibleAdministrations());
      }
    }
  }

  private static Set<UUID> administrations(JdbcClient jdbc) {
    return jdbc.sql("SELECT DISTINCT administration_id FROM core.admin_user")
        .query(UUID.class)
        .set();
  }
}
