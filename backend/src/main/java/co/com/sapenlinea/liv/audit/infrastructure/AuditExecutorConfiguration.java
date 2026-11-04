package co.com.sapenlinea.liv.audit.infrastructure;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The threads of the audit listeners, a few at a time: each one takes a connection of the pool, and
 * a burst of logins or a redelivery of the outbox must not leave the requests without one. A full
 * queue refuses the delivery, which the outbox keeps and delivers again later.
 */
@Configuration(proxyBeanMethods = false)
class AuditExecutorConfiguration {

  /** Name of the executor of {@code @Async} in the audit listeners. */
  static final String AUDIT_EXECUTOR = "auditExecutor";

  @Bean(AUDIT_EXECUTOR)
  ThreadPoolTaskExecutor auditExecutor(
      @Value("${liv.audit.listener-threads}") int threads,
      @Value("${liv.audit.listener-queue}") int queue) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(threads);
    executor.setMaxPoolSize(threads);
    executor.setQueueCapacity(queue);
    executor.setThreadNamePrefix("audit-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    // On shutdown, the listeners in course get some seconds; the rest stay in the outbox.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    return executor;
  }
}
