package co.com.sapenlinea.liv.audit.infrastructure;

import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents;
import co.com.sapenlinea.liv.audit.application.ImportIdentityProviderEvents.ImportResult;
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
 */
@Component
@ConditionalOnProperty(name = "liv.audit.importer.enabled", havingValue = "true")
public class IdentityProviderEventsJob {

  /** Name of the lock in {@code platform.shedlock}. */
  static final String LOCK = "identity-provider-events";

  private static final Logger LOG = LoggerFactory.getLogger(IdentityProviderEventsJob.class);

  private final ImportIdentityProviderEvents importer;

  IdentityProviderEventsJob(ImportIdentityProviderEvents importer) {
    this.importer = importer;
  }

  @Scheduled(
      fixedDelayString = "${liv.audit.importer.interval}",
      initialDelayString = "${liv.audit.importer.initial-delay}")
  @SchedulerLock(name = LOCK, lockAtMostFor = "PT10M")
  public void run() {
    try {
      ImportResult result = importer.importNew();
      LOG.debug(
          "Identity provider events imported: {}, watermark {}",
          result.imported(),
          result.cursor());
    } catch (RuntimeException e) {
      // The provider or the database may be down: the next run reads again from the watermark.
      LOG.error("The import of identity provider events failed; it will run again", e);
    }
  }
}
