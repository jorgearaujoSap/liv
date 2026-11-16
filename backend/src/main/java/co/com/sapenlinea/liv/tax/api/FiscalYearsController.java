package co.com.sapenlinea.liv.tax.api;

import co.com.sapenlinea.liv.platform.AuthenticationRejectedException;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.ErrorCodes;
import co.com.sapenlinea.liv.tax.api.contract.FiscalYearsApi;
import co.com.sapenlinea.liv.tax.api.contract.model.CreatableYear;
import co.com.sapenlinea.liv.tax.api.contract.model.CreateBlocked;
import co.com.sapenlinea.liv.tax.api.contract.model.CreateFiscalYearRequest;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYear;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearDetail;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearList;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.api.contract.model.Preparation;
import co.com.sapenlinea.liv.tax.api.contract.model.PreparationItem;
import co.com.sapenlinea.liv.tax.api.contract.model.PreparationItemStatus;
import co.com.sapenlinea.liv.tax.application.CreateFiscalYear;
import co.com.sapenlinea.liv.tax.application.GetFiscalYear;
import co.com.sapenlinea.liv.tax.application.ListFiscalYears;
import co.com.sapenlinea.liv.tax.application.OpenFiscalYearPreparation;
import co.com.sapenlinea.liv.tax.domain.PreparationChecklist;
import co.com.sapenlinea.liv.tax.domain.RegisteredFiscalYear;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fiscal years of the administration (tax.yaml 1.1.0, menu 1.4). It has no logic (CC-07): it
 * takes the user of the request, calls the use case and turns what it answers into the models of
 * the contract. The rules, the permissions and the errors (409, 422, 404) belong to the use cases
 * and to {@code ProblemDetailsHandler}.
 */
@RestController
class FiscalYearsController implements FiscalYearsApi {

  private final ListFiscalYears listFiscalYears;
  private final GetFiscalYear getFiscalYear;
  private final CreateFiscalYear createFiscalYear;
  private final OpenFiscalYearPreparation openFiscalYearPreparation;

  FiscalYearsController(
      ListFiscalYears listFiscalYears,
      GetFiscalYear getFiscalYear,
      CreateFiscalYear createFiscalYear,
      OpenFiscalYearPreparation openFiscalYearPreparation) {
    this.listFiscalYears = listFiscalYears;
    this.getFiscalYear = getFiscalYear;
    this.createFiscalYear = createFiscalYear;
    this.openFiscalYearPreparation = openFiscalYearPreparation;
  }

  @Override
  public ResponseEntity<FiscalYearList> listFiscalYears() {
    ListFiscalYears.Listing listing = listFiscalYears.list(user());
    FiscalYearList body =
        new FiscalYearList(
            listing.items().stream().map(FiscalYearsController::toContract).toList(),
            listing.canCreate(),
            listing.creatableYears().stream()
                .map(year ->
                    new CreatableYear(
                        year.year(), year.startsOn(), year.endsOn(), year.relation().name()))
                .toList());
    listing.createBlocked().ifPresent(blocked -> body.setCreateBlocked(toContract(blocked)));
    return ResponseEntity.ok(body);
  }

  private static CreateBlocked toContract(co.com.sapenlinea.liv.tax.domain.CreateBlocked blocked) {
    CreateBlocked contract = new CreateBlocked(blocked.reason().name(), blocked.maxYear());
    contract.setMinYear(blocked.minYear());
    return contract;
  }

  @Override
  public ResponseEntity<FiscalYearDetail> getFiscalYear(Integer year) {
    GetFiscalYear.Detail detail = getFiscalYear.get(user(), year);
    FiscalYear fiscalYear = toContract(detail.fiscalYear());
    return ResponseEntity.ok(
        new FiscalYearDetail(
            fiscalYear.getYear(),
            fiscalYear.getStartsOn(),
            fiscalYear.getEndsOn(),
            fiscalYear.getStatus(),
            fiscalYear.getAssessmentsEnabled(),
            detail.availableActions(),
            toContract(detail.preparation())));
  }

  @Override
  public ResponseEntity<FiscalYear> createFiscalYear(CreateFiscalYearRequest request) {
    RegisteredFiscalYear created = createFiscalYear.create(user(), request.getYear());
    return ResponseEntity.status(HttpStatus.CREATED).body(toContract(created));
  }

  @Override
  public ResponseEntity<FiscalYear> openFiscalYearPreparation(Integer year) {
    return ResponseEntity.ok(toContract(openFiscalYearPreparation.open(user(), year)));
  }

  /**
   * The user of this request, the same one the authorization of the use case evaluates: the use
   * cases get it as an argument, and a different one would be authorized with another user's
   * permissions.
   */
  private static CurrentUser user() {
    return CurrentUser.current()
        .orElseThrow(() ->
            new AuthenticationRejectedException(ErrorCodes.NOT_AUTHENTICATED, "No current user"));
  }

  private static FiscalYear toContract(RegisteredFiscalYear stored) {
    return new FiscalYear(
        stored.fiscalYear().year(),
        stored.startsOn(),
        stored.endsOn(),
        FiscalYearStatus.fromValue(stored.fiscalYear().status().name()),
        stored.fiscalYear().assessmentsEnabled());
  }

  private static Preparation toContract(PreparationChecklist checklist) {
    return new Preparation(
        checklist.items().stream()
            .map(item ->
                new PreparationItem(
                    item.number(),
                    item.code(),
                    item.required(),
                    item.option(),
                    PreparationItemStatus.fromValue(item.status().name()),
                    item.reason()))
            .toList(),
        checklist.canEnable(),
        checklist.missing());
  }
}
