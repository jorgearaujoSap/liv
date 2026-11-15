package co.com.sapenlinea.liv.tax.application;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.tax.application.FiscalYears.Stored;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.PreparationChecklist;
import co.com.sapenlinea.liv.tax.domain.PreparationItemCheck;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fiscal year of the administration with what the user can do with it and its preparation
 * checklist (007-10 to 007-12). A year that does not exist in the administration, also one of
 * another, is {@link NotFound} (CA-12).
 *
 * <p>Not a bean yet: the adapter of the port arrives with the persistence (6a2), which makes it a
 * {@code @Service} with {@code @RequiresPermission(PERMISSION)}. Until then nothing can call it
 * from the API, and the permission is not applied.
 */
public class GetFiscalYear {

  public static final String PERMISSION = "tax.fiscal-year.read";

  /** The only action of feature 007; later features add theirs. */
  static final String OPEN_PREPARATION = "OPEN_PREPARATION";

  private final FiscalYears fiscalYears;
  private final PermissionCatalog catalog;
  private final ObjectProvider<PreparationItemCheck> checks;

  public GetFiscalYear(
      FiscalYears fiscalYears,
      PermissionCatalog catalog,
      ObjectProvider<PreparationItemCheck> checks) {
    this.fiscalYears = fiscalYears;
    this.catalog = catalog;
    this.checks = checks;
  }

  /**
   * @param fiscalYear the fiscal year
   * @param availableActions what the user can do now, as codes of the contract
   * @param preparation the checklist of the preparation
   */
  public record Detail(
      Stored fiscalYear, List<String> availableActions, PreparationChecklist preparation) {

    public Detail {
      availableActions = List.copyOf(availableActions);
    }
  }

  @Transactional(readOnly = true)
  public Detail get(CurrentUser user, int year) {
    Stored stored =
        fiscalYears
            .find(user.administrationId(), year)
            .orElseThrow(() -> new NotFound("fiscal year " + year));
    FiscalYearStatus status = stored.fiscalYear().status();
    boolean canPrepare =
        catalog
            .permissionsOf(catalog.knownRoles(user.roles()))
            .contains(OpenFiscalYearPreparation.PERMISSION);
    List<String> actions =
        canPrepare && status == FiscalYearStatus.PLANEADA ? List.of(OPEN_PREPARATION) : List.of();
    PreparationChecklist checklist =
        PreparationChecklist.evaluate(
            user.administrationId(),
            year,
            status != FiscalYearStatus.PLANEADA,
            checks.orderedStream().toList());
    return new Detail(stored, actions, checklist);
  }
}
