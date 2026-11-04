package co.com.sapenlinea.liv.platform.persistence;

import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Delivers again the events of the outbox whose listener failed or never finished (a database down,
 * an instance that stopped after the commit), so the audit gets them (001-18). It replaces Spring
 * Modulith's redelivery on restart, which needs the database at startup (plan, ajustes de T12); it
 * also retries without a restart. Only events older than {@code liv.outbox.resubmit-older-than} are
 * taken, so a listener still running is left alone.
 */
@Component
public class OutboxResubmissionJob {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxResubmissionJob.class);

  private final ObjectProvider<IncompleteEventPublications> incomplete;
  private final Duration olderThan;

  OutboxResubmissionJob(
      ObjectProvider<IncompleteEventPublications> incomplete,
      @Value("${liv.outbox.resubmit-older-than}") Duration olderThan) {
    this.incomplete = incomplete;
    this.olderThan = olderThan;
  }

  @Scheduled(
      fixedDelayString = "${liv.outbox.resubmission-interval}",
      initialDelayString = "${liv.outbox.resubmission-interval}")
  @SchedulerLock(name = "outbox-resubmission")
  public void run() {
    try {
      incomplete.getObject().resubmitIncompletePublicationsOlderThan(olderThan);
    } catch (RuntimeException e) {
      LOG.warn("The outbox could not deliver its pending events again; next run will retry", e);
    }
  }
}
