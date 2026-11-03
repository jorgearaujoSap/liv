package co.com.sapenlinea.liv.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records the events of {@code core} after their transaction commits, like the audit listeners will
 * (T15): an event of a rolled back transaction never arrives.
 */
@TestConfiguration
class SessionEventsRecorder {

  @Bean
  Recorded recordedSessionEvents() {
    return new Recorded();
  }

  static class Recorded {
    private final List<Object> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener
    public void on(SessionStarted event) {
      events.add(event);
    }

    @TransactionalEventListener
    public void on(SessionEnded event) {
      events.add(event);
    }

    @TransactionalEventListener
    public void on(SessionDenied event) {
      events.add(event);
    }

    @TransactionalEventListener
    public void on(UserProvisioned event) {
      events.add(event);
    }

    public <T> List<T> of(Class<T> type) {
      return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
  }
}
