package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import co.com.sapenlinea.liv.tax.application.FiscalYears.Stored;
import co.com.sapenlinea.liv.tax.domain.CreatableYear;
import co.com.sapenlinea.liv.tax.domain.CreateBlocked;
import co.com.sapenlinea.liv.tax.domain.FiscalYearOpeningPolicy;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fiscal years of the administration and the years that can be created (007-01, 007-03). It
 * uses the same policy and the same current year as {@link CreateFiscalYear}, so it never offers a
 * year that the creation rejects.
 */
@Service
public class ListFiscalYears {

  public static final String PERMISSION = "tax.fiscal-year.read";

  private final FiscalYears fiscalYears;
  private final PermissionCatalog catalog;
  private final BusinessYear businessYear;

  public ListFiscalYears(
      FiscalYears fiscalYears,
      PermissionCatalog catalog,
      Clock clock,
      @Value("${liv.business-zone}") ZoneId zone) {
    this.fiscalYears = fiscalYears;
    this.catalog = catalog;
    this.businessYear = new BusinessYear(clock, zone);
  }

  /**
   * What the list shows.
   *
   * @param items the fiscal years, the most recent first
   * @param canCreate whether the user has {@code tax.fiscal-year.create}
   * @param creatableYears the years the creation accepts now; empty without {@code canCreate}
   * @param createBlocked why nothing can be created, only with {@code canCreate}
   */
  public record Listing(
      List<Stored> items,
      boolean canCreate,
      List<CreatableYear> creatableYears,
      Optional<CreateBlocked> createBlocked) {

    public Listing {
      items = List.copyOf(items);
      creatableYears = List.copyOf(creatableYears);
    }
  }

  @RequiresPermission(PERMISSION)
  @Transactional(readOnly = true)
  public Listing list(CurrentUser user) {
    List<Stored> items = fiscalYears.findAll(user.administrationId());
    boolean canCreate =
        catalog
            .permissionsOf(catalog.knownRoles(user.roles()))
            .contains(CreateFiscalYear.PERMISSION);
    if (!canCreate) {
      return new Listing(items, false, List.of(), Optional.empty());
    }
    FiscalYearOpeningPolicy policy =
        new FiscalYearOpeningPolicy(
            businessYear.current(),
            items.stream().map(item -> item.fiscalYear().year()).toList(),
            fiscalYears.nationalYears());
    return new Listing(items, true, policy.creatableYears(), policy.createBlocked());
  }
}
