package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.ClosingSessionRepository;
import co.com.sapenlinea.liv.bff.session.ExpiredSessions;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import java.time.Clock;
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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * The sessions that are over end as they should, whether or not the person comes back (001-10,
 * 001-18; T19): a sweep every {@code liv.bff.session.sweep-interval}, and the repository of Spring
 * Session wrapped so that reading an expired session ends it too ({@link
 * ClosingSessionRepository}). With several replicas only one sweeps at a time (ShedLock on {@code
 * bff.shedlock}, migration V202611061000), with the clock of the database, as the jobs of the API.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class ExpiredSessionsConfiguration {

  static final String LOCK = "bff-expired-sessions";

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
   * The repository of Spring Session itself, as Spring Session builds it, before it is wrapped. The
   * wrapper reaches {@link ExpiredSessions} lazily, so this does not depend on itself.
   */
  @Bean
  static BeanPostProcessor closingSessionRepository(ObjectProvider<ExpiredSessions> expired) {
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
      FindByIndexNameSessionRepository<? extends Session> sessions,
      SessionCloser closer,
      BffProperties properties,
      Clock clock,
      @Value("${spring.session.jdbc.table-name}") String table) {
    return new ExpiredSessions(
        new JdbcTemplate(dataSource),
        sessions,
        closer,
        properties.session().absoluteTimeout(),
        clock,
        table);
  }

  @Bean
  ExpiredSessionsSweep expiredSessionsSweep(ExpiredSessions expired) {
    return new ExpiredSessionsSweep(expired);
  }

  /** The sweep itself, one replica at a time. */
  static class ExpiredSessionsSweep {

    private final ExpiredSessions expired;

    ExpiredSessionsSweep(ExpiredSessions expired) {
      this.expired = expired;
    }

    @Scheduled(
        fixedDelayString = "${liv.bff.session.sweep-interval:1m}",
        initialDelayString = "${liv.bff.session.sweep-interval:1m}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT10M")
    public void sweep() {
      expired.sweep();
    }
  }
}
