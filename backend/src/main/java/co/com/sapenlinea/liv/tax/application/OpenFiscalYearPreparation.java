package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.Correlation;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import co.com.sapenlinea.liv.tax.application.FiscalYears.Stored;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens the preparation of a fiscal year: {@code PLANEADA} to {@code EN_PREPARACION} (007-10). The
 * order of the answers is the one of the contract: the permission first (403), then that the year
 * exists in the administration ({@link NotFound}, 404) and only then the state (V-06), so V-06
 * never reveals the state of the fiscal year of another administration.
 *
 * <p>Not a bean yet: the adapter of the port arrives with the persistence (6a2), which makes it a
 * {@code @Service} with {@code @RequiresPermission(PERMISSION)}. Until then nothing can call it
 * from the API, and the permission is not applied.
 */
public class OpenFiscalYearPreparation {

  public static final String PERMISSION = "tax.fiscal-year.prepare";

  private final FiscalYears fiscalYears;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public OpenFiscalYearPreparation(
      FiscalYears fiscalYears, ApplicationEventPublisher events, Clock clock) {
    this.fiscalYears = fiscalYears;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @throws NotFound if the administration has no fiscal year of that year
   * @throws RuleViolation V-06 if the fiscal year is not {@code PLANEADA}, also when another
   *     request opened it an instant before
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Stored open(CurrentUser user, int year) {
    Stored current =
        fiscalYears
            .find(user.administrationId(), year)
            .orElseThrow(() -> new NotFound("fiscal year " + year));
    // The domain decides from the state just read (V-06); the port repeats the check in the
    // UPDATE itself, for the request that changed it between the read and the write.
    current.fiscalYear().openPreparation();
    Stored opened = fiscalYears.persistOpened(current, user.userId());
    events.publishEvent(
        new FiscalYearStatusChanged(
            UUID.randomUUID(),
            user.administrationId(),
            user.userId(),
            opened.id(),
            year,
            current.fiscalYear().status().name(),
            opened.fiscalYear().status().name(),
            clock.instant(),
            Correlation.current().orElse(null)));
    return opened;
  }
}
