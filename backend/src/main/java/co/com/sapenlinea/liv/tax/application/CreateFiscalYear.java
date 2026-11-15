package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.Correlation;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.application.FiscalYears.Stored;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearOpeningPolicy;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the fiscal year of an administration in {@code PLANEADA} (007-04 to 007-08). Reading the
 * years, deciding and writing happen in one transaction, behind the lock of the administration:
 * without it, two creations that cross midnight of 31 December with different current years could
 * both see an empty administration and leave a gap that cannot be deleted.
 *
 * <p>The isolation is {@code READ_COMMITTED} on purpose: the lock only helps if every statement
 * after it sees what the other creation committed, and a snapshot taken before the lock would not.
 *
 * <p>Not a bean yet: the adapter of the port arrives with the persistence (6a2), which makes it a
 * {@code @Service} with {@code @RequiresPermission(PERMISSION)}. Until then nothing can call it
 * from the API, and the permission is not applied.
 */
public class CreateFiscalYear {

  public static final String PERMISSION = "tax.fiscal-year.create";

  private final FiscalYears fiscalYears;
  private final BusinessYear businessYear;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  /**
   * @param zone the zone of the business ({@code liv.business-zone}), with which the current year
   *     is worked out (ADR 0035)
   */
  public CreateFiscalYear(
      FiscalYears fiscalYears,
      ApplicationEventPublisher events,
      Clock clock,
      @Value("${liv.business-zone}") ZoneId zone) {
    this.fiscalYears = fiscalYears;
    this.events = events;
    this.clock = clock;
    this.businessYear = new BusinessYear(clock, zone);
  }

  /**
   * @throws RuleViolation V-01, V-02 or V-05, the first rule the year breaks
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Stored create(CurrentUser user, int year) {
    UUID administrationId = user.administrationId();
    fiscalYears.lockForCreation(administrationId);
    var existing = fiscalYears.findAll(administrationId);
    new FiscalYearOpeningPolicy(
            businessYear.current(),
            existing.stream().map(item -> item.fiscalYear().year()).toList(),
            fiscalYears.nationalYears())
        .check(year);
    Stored created =
        fiscalYears.create(AdministrationFiscalYear.plan(administrationId, year), user.userId());
    events.publishEvent(
        new FiscalYearCreated(
            UUID.randomUUID(),
            administrationId,
            user.userId(),
            created.id(),
            year,
            null,
            created.fiscalYear().status().name(),
            clock.instant(),
            Correlation.current().orElse(null)));
    return created;
  }
}
