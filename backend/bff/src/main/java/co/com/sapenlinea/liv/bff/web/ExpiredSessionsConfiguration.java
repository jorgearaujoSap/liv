package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.session.ClosingSessionRepository;
import co.com.sapenlinea.liv.bff.session.ExpiredSessions;
import co.com.sapenlinea.liv.bff.session.OidcSessions;
import co.com.sapenlinea.liv.bff.session.ProviderLogout;
import co.com.sapenlinea.liv.bff.session.SessionCloser;
import co.com.sapenlinea.liv.bff.session.StoredSessions;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  static BeanPostProcessor wrapSessionRepository(
      ObjectProvider<ExpiredSessions> expired, ObjectProvider<OidcSessions> oidc) {
    return new BeanPostProcessor() {
      @Override
      @SuppressWarnings({"unchecked", "rawtypes"})
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof JdbcIndexedSessionRepository sessions) {
          return new ClosingSessionRepository(sessions, expired::getObject, oidc::getObject);
        }
        return bean;
      }
    };
  }

  @Bean
  StoredSessions storedSessions(
      DataSource dataSource, @Value("${spring.session.jdbc.table-name}") String table) {
    return new StoredSessions(dataSource, table);
  }

  @Bean
  OidcSessions oidcSessions(
      DataSource dataSource,
      Clock clock,
      @Value("${spring.session.jdbc.table-name}") String table) {
    return new OidcSessions(dataSource, clock, table);
  }

  @Bean
  ProviderLogout providerLogout(
      StoredSessions stored,
      OidcSessions oidc,
      SessionCloser closer,
      BffProperties properties,
      Clock clock) {
    return new ProviderLogout(stored, oidc, closer, properties.session().sweepGiveUpAfter(), clock);
  }

  @Bean
  ExpiredSessions expiredSessions(
      StoredSessions stored,
      OidcSessions oidc,
      SessionCloser closer,
      BffProperties properties,
      Clock clock,
      @Value("${spring.session.jdbc.cleanup-cron:}") String cleanupCron) {
    requireOwnCleanUpOff(cleanupCron);
    BffProperties.Session session = properties.session();
    return new ExpiredSessions(
        stored,
        oidc,
        closer,
        session.absoluteTimeout(),
        SWEEP_BUDGET,
        session.sweepGiveUpAfter(),
        clock);
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
  ExpiredSessionsSweeper expiredSessionsSweeper(
      ExpiredSessions expired, ProviderLogout providerLogout, OidcSessions oidc) {
    return new ExpiredSessionsSweeper(expired, providerLogout, oidc);
  }

  /**
   * What runs the sweep on schedule, one replica at a time: the sessions that are over, the ends of
   * sessions the provider closed still to be told to LIV, and the rows of {@code bff.oidc_session}
   * whose session is gone. A failure in one does not stop the others.
   */
  static class ExpiredSessionsSweeper {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredSessionsSweeper.class);

    /** Rows of bff.oidc_session removed per sweep. */
    private static final int FORGET_BATCH = 1000;

    private final ExpiredSessions expired;
    private final ProviderLogout providerLogout;
    private final OidcSessions oidc;

    ExpiredSessionsSweeper(
        ExpiredSessions expired, ProviderLogout providerLogout, OidcSessions oidc) {
      this.expired = expired;
      this.providerLogout = providerLogout;
      this.oidc = oidc;
    }

    @Scheduled(
        fixedDelayString = "${liv.bff.session.sweep-interval}",
        initialDelayString = "${liv.bff.session.sweep-interval}")
    @SchedulerLock(name = LOCK, lockAtMostFor = LOCK_AT_MOST_FOR)
    public void sweep() {
      run("las sesiones vencidas", expired::sweep);
      run("los fines pendientes", providerLogout::tellThePendingEnds);
      run("las sesiones del proveedor sin sesión", () -> oidc.forgetTheGone(FORGET_BATCH));
    }

    private static void run(String what, Runnable step) {
      try {
        step.run();
      } catch (RuntimeException e) {
        LOG.warn("El barrido de {} falló: {}", what, e.getClass().getSimpleName());
      }
    }
  }
}
