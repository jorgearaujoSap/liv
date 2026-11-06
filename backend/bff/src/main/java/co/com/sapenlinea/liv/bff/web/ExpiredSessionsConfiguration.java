package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.ClosingSessionRepository;
import co.com.sapenlinea.liv.bff.session.ExpiredSessions;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * The sessions that are over end as they should, whether or not the person comes back (001-10,
 * 001-18; T19): a sweep every {@code liv.bff.session.sweep-interval}, and the repository of Spring
 * Session wrapped so that an expired session is not read and deleted silently ({@link
 * ClosingSessionRepository}). With several replicas only one sweeps at a time (ShedLock on {@code
 * bff.shedlock}, migration V202611061000), with the clock of the database, as the jobs of the API.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = ExpiredSessionsConfiguration.LOCK_AT_MOST_FOR)
class ExpiredSessionsConfiguration {

  static final String LOCK = "bff-expired-sessions";

  /** How long the lock of a sweep lasts at most, if its replica dies holding it. */
  static final String LOCK_AT_MOST_FOR = "PT10M";

  /** How long one sweep may last: half its lock, so that it always ends before the lock does. */
  static final Duration SWEEP_BUDGET = Duration.parse(LOCK_AT_MOST_FOR).dividedBy(2);

  @Bean
  LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .withTableName("bff.shedlock")
            .usingDbTime()
            .build());
  }

  /**
   * Wraps the repository of Spring Session, as Spring Session builds it. The wrapper reaches {@link
   * ExpiredSessions} at its first use: a post processor is created before the other beans.
   */
  @Bean
  static BeanPostProcessor wrapSessionRepository(ObjectProvider<ExpiredSessions> expired) {
    return new BeanPostProcessor() {
      @Override
      @SuppressWarnings({"unchecked", "rawtypes"})
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof JdbcIndexedSessionRepository sessions) {
          return new ClosingSessionRepository(sessions, expired::getObject);
        }
        return bean;
      }
    };
  }

  @Bean
  ExpiredSessions expiredSessions(
      DataSource dataSource,
      SessionCloser closer,
      BffProperties properties,
      Clock clock,
      @Value("${spring.session.jdbc.table-name}") String table,
      @Value("${spring.session.jdbc.cleanup-cron:}") String cleanupCron) {
    requireOwnCleanUpOff(cleanupCron);
    BffProperties.Session session = properties.session();
    return new ExpiredSessions(
        dataSource,
        closer,
        session.absoluteTimeout(),
        SWEEP_BUDGET,
        session.sweepGiveUpAfter(),
        clock,
        table);
  }

  /**
   * The clean up of Spring Session deletes the expired sessions without telling anyone: with it on,
   * the BFF does not start.
   */
  static void requireOwnCleanUpOff(String cleanupCron) {
    if (!"-".equals(cleanupCron)) {
      throw new IllegalStateException(
          "spring.session.jdbc.cleanup-cron debe ser \"-\": la limpieza de Spring Session borra las"
              + " sesiones vencidas sin avisar a LIV (T19)");
    }
  }

  @Bean
  ExpiredSessionsSweeper expiredSessionsSweeper(ExpiredSessions expired) {
    return new ExpiredSessionsSweeper(expired);
  }

  /** What runs the sweep on schedule, one replica at a time. */
  static class ExpiredSessionsSweeper {

    private final ExpiredSessions expired;

    ExpiredSessionsSweeper(ExpiredSessions expired) {
      this.expired = expired;
    }

    @Scheduled(
        fixedDelayString = "${liv.bff.session.sweep-interval}",
        initialDelayString = "${liv.bff.session.sweep-interval}")
    @SchedulerLock(name = LOCK, lockAtMostFor = LOCK_AT_MOST_FOR)
    public void sweep() {
      expired.sweep();
    }
  }
}
