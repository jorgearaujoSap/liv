package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.Correlation;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import co.com.sapenlinea.liv.tax.domain.RegisteredFiscalYear;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens the preparation of a fiscal year: {@code PLANEADA} to {@code EN_PREPARACION} (007-10). The
 * order of the answers is the one of the contract: the permission first (403), then that the year
 * exists in the administration ({@link NotFound}, 404) and only then the state (V-06), so V-06
 * never reveals the state of the fiscal year of another administration.
 */
@Service
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
  @RequiresPermission(PERMISSION)
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public RegisteredFiscalYear open(CurrentUser user, int year) {
    RegisteredFiscalYear current =
        fiscalYears
            .find(user.administrationId(), year)
            .orElseThrow(() -> new NotFound("fiscal year " + year));
    // The domain decides from the state just read (V-06); the port repeats the check in the
    // UPDATE itself, for the request that changed it between the read and the write.
    current.fiscalYear().openPreparation();
    RegisteredFiscalYear opened = fiscalYears.persistOpened(current, user.userId());
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
