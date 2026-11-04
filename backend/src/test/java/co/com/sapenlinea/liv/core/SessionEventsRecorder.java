package co.com.sapenlinea.liv.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records the events of {@code core} after their transaction commits, like the audit listeners
 * (T15): an event of a rolled back transaction never arrives. It keeps them without their id, time
 * and correlation, which vary in every run, so the tests compare the rest; the audit tests check
 * those.
 *
 * <p>It registers its own after-commit callback instead of being a {@code
 * TransactionalEventListener}: Spring Modulith would track such a listener in the outbox and, being
 * synchronous, record its delivery in a second connection while the request still holds the first.
 */
@TestConfiguration
class SessionEventsRecorder {

  @Bean
  Recorded recordedSessionEvents() {
    return new Recorded();
  }

  static class Recorded {
    private final List<Object> events = new CopyOnWriteArrayList<>();

    @EventListener
    public void on(SessionStarted event) {
      afterCommit(() ->
          events.add(
              new SessionStarted(
                  event.sid(),
                  event.userId(),
                  event.administrationId(),
                  event.startedAt(),
                  null,
                  null)));
    }

    @EventListener
    public void on(SessionEnded event) {
      afterCommit(() ->
          events.add(
              new SessionEnded(
                  event.sid(),
                  event.userId(),
                  event.administrationId(),
                  event.reason(),
                  event.endedAt(),
                  null,
                  null)));
    }

    @EventListener
    public void on(SessionDenied event) {
      afterCommit(() ->
          events.add(
              new SessionDenied(
                  event.code(),
                  event.sid(),
                  event.userId(),
                  event.registeredAdministrationId(),
                  event.claimedAdministrationId(),
                  null,
                  null,
                  null)));
    }

    @EventListener
    public void on(UserProvisioned event) {
      afterCommit(() ->
          events.add(
              new UserProvisioned(event.userId(), event.administrationId(), null, null, null)));
    }

    /**
     * Runs {@code record} once the current transaction commits; an event outside one is ignored.
     */
    private static void afterCommit(Runnable record) {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                record.run();
              }
            });
      }
    }

    public <T> List<T> of(Class<T> type) {
      return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
  }
}
