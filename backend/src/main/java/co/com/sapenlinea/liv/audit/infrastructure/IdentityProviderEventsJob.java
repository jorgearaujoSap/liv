package co.com.sapenlinea.liv.audit.infrastructure;

import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents;
import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents.ImportResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The scheduled import of the events of the identity provider (001-18, plan step 9): every {@code
 * liv.audit.importer.interval}, on one replica at a time (ShedLock on {@code platform.shedlock}). A
 * failed run is logged and the next one reads again from the watermark.
 *
 * <p>Two metrics tell whether the audit of the provider advances, also when every run fails: {@code
 * liv.audit.importer.lag} (seconds since the watermark of the last run that finished) and {@code
 * liv.audit.importer.failures}.
 */
@Component
@ConditionalOnProperty(name = "liv.audit.importer.enabled", havingValue = "true")
public class IdentityProviderEventsJob {

  /** Name of the lock in {@code platform.shedlock}. */
  public static final String LOCK_NAME = "identity-provider-events";

  private static final Logger LOG = LoggerFactory.getLogger(IdentityProviderEventsJob.class);

  private final ImportIdentityProviderEvents importer;
  private final Clock clock;
  private final Counter failures;
  private final AtomicReference<Instant> watermark;

  IdentityProviderEventsJob(
      ImportIdentityProviderEvents importer, Clock clock, MeterRegistry metrics) {
    this.importer = importer;
    this.clock = clock;
    // Until the first run finishes, the lag counts from startup.
    this.watermark = new AtomicReference<>(clock.instant());
    this.failures = metrics.counter("liv.audit.importer.failures");
    Gauge.builder("liv.audit.importer.lag", this, IdentityProviderEventsJob::lagSeconds)
        .baseUnit("seconds")
        .register(metrics);
  }

  /**
   * Each run reads at most {@code page-size × max-pages} events per stream, with a timeout of 10 s
   * per request: it ends well before the lock expires.
   */
  @Scheduled(
      fixedDelayString = "${liv.audit.importer.interval}",
      initialDelayString = "${liv.audit.importer.initial-delay}")
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT15M", lockAtLeastFor = "PT10S")
  public void run() {
    try {
      ImportResult result = importer.importNew();
      watermark.set(result.cursor());
      LOG.debug(
          "Identity provider events imported: {}, watermark {}",
          result.imported(),
          result.cursor());
    } catch (RuntimeException e) {
      failures.increment();
      // The provider or the database may be down: the next run reads again from the watermark.
      LOG.error(
          "The import of identity provider events failed; the audit of the provider is {} behind",
          Duration.ofSeconds((long) lagSeconds()),
          e);
    }
  }

  private double lagSeconds() {
    return Duration.between(watermark.get(), clock.instant()).toSeconds();
  }
}
