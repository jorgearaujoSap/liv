package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.tax.application.CreateFiscalYear;
import co.com.sapenlinea.liv.tax.application.FiscalYears;
import co.com.sapenlinea.liv.tax.application.GetFiscalYear;
import co.com.sapenlinea.liv.tax.application.ListFiscalYears;
import co.com.sapenlinea.liv.tax.application.OpenFiscalYearPreparation;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The four use cases of the fiscal years are beans of the real context, behind the proxy that
 * checks their permission (001-16) and opens their transaction, with the real catalog of roles. It
 * proves the step from plain classes to beans: calling them without the permission fails, and with
 * it they work through the adapter.
 */
@DisplayName("Los casos de uso de las vigencias son beans con su permiso (007-02, 007-09)")
class FiscalYearsBeansTest extends FiscalYearsPersistenceTestBase {

  private final ListFiscalYears list;
  private final GetFiscalYear get;
  private final CreateFiscalYear create;
  private final OpenFiscalYearPreparation open;

  @Autowired
  FiscalYearsBeansTest(
      FiscalYears fiscalYears,
      PlatformTransactionManager transactionManager,
      ListFiscalYears list,
      GetFiscalYear get,
      CreateFiscalYear create,
      OpenFiscalYearPreparation open) {
    super(fiscalYears, transactionManager);
    this.list = list;
    this.get = get;
    this.create = create;
    this.open = open;
  }

  /** The user of a request with the given role of the catalog (the roles of its token). */
  private static CurrentUser withRole(Tenant tenant, String role) {
    return new CurrentUser(
        tenant.userId(),
        tenant.administrationId(),
        ISSUER,
        "subject-" + tenant.userId(),
        Set.of(role));
  }

  /** Runs the work as the request of the user: the filter would have resolved it like this. */
  private static <T> T asRequestOf(CurrentUser user, Supplier<T> work) {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    try (AdministrationContext.Scope ignored =
        AdministrationContext.open(user.administrationId())) {
      RequestContextHolder.currentRequestAttributes()
          .setAttribute(CurrentUser.ATTRIBUTE, user, RequestAttributes.SCOPE_REQUEST);
      return work.get();
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }
  }

  /** The current year of the business, which the real clock gives to the use cases. */
  private static int currentYear() {
    return LocalDate.now(ZoneId.of("America/Bogota")).getYear();
  }

  @Test
  @Tag("007-02")
  @DisplayName("007-02 · Los cuatro casos de uso son beans con proxy: la autorización se aplica")
  void theUseCasesAreProxiedBeans() {
    for (Object useCase : List.of(list, get, create, open)) {
      assertThat(AopUtils.isAopProxy(useCase)).as(useCase.getClass().getName()).isTrue();
    }
  }

  @Test
  @Tag("007-02")
  @DisplayName("007-02 · Fuera de una petición, sin usuario ni permiso, ningún caso de uso corre")
  void outsideARequestNothingRuns() {
    Tenant tenant = newTenant();
    CurrentUser user = withRole(tenant, "ADMIN_SISTEMA");

    assertThatThrownBy(() -> list.list(user)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> get.get(user, 2026)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> create.create(user, currentYear()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> open.open(user, currentYear()))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(yearsOf(tenant)).isEmpty();
  }

  @Test
  @Tag("007-09")
  @DisplayName("007-09 · Con el rol CONSULTA se lista y se consulta, pero no se crea ni se abre")
  void aReaderCannotCreateNorOpen() {
    Tenant tenant = newTenant();
    int year = currentYear();
    create(tenant, year);
    CurrentUser reader = withRole(tenant, "CONSULTA");

    ListFiscalYears.Listing listing = asRequestOf(reader, () -> list.list(reader));
    assertThat(listing.canCreate()).isFalse();
    assertThat(listing.creatableYears()).isEmpty();
    assertThat(asRequestOf(reader, () -> get.get(reader, year)).availableActions()).isEmpty();
    assertThatThrownBy(() -> asRequestOf(reader, () -> create.create(reader, year + 1)))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> asRequestOf(reader, () -> open.open(reader, year)))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(yearsOf(tenant)).containsExactly(year);
    assertThat(rowsOf(tenant).getFirst()[1]).isEqualTo("PLANEADA");
  }

  @Test
  @Tag("007-04")
  @Tag("007-10")
  @DisplayName(
      "007-04 · 007-10 · Con ADMIN_SISTEMA se crea y se abre por los beans, con su transacción real")
  void anAdministratorCreatesAndOpens() {
    Tenant tenant = newTenant();
    int year = currentYear();
    CurrentUser admin = withRole(tenant, "ADMIN_SISTEMA");

    ListFiscalYears.Listing before = asRequestOf(admin, () -> list.list(admin));
    assertThat(before.canCreate()).isTrue();
    assertThat(before.creatableYears()).extracting(item -> item.year()).contains(year);

    var created = asRequestOf(admin, () -> create.create(admin, year));
    assertThat(created.fiscalYear().status()).isEqualTo(FiscalYearStatus.PLANEADA);
    GetFiscalYear.Detail planned = asRequestOf(admin, () -> get.get(admin, year));
    assertThat(planned.availableActions()).containsExactly("OPEN_PREPARATION");

    // Another administrator of the same administration opens it: the editor is the one who opens.
    CurrentUser editor =
        new CurrentUser(
            tenant.editorId(),
            tenant.administrationId(),
            ISSUER,
            "subject-" + tenant.editorId(),
            Set.of("ADMIN_SISTEMA"));
    var opened = asRequestOf(editor, () -> open.open(editor, year));
    assertThat(opened.fiscalYear().status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertThat(asRequestOf(admin, () -> get.get(admin, year)).availableActions()).isEmpty();
    Object[] row = rowsOf(tenant).getFirst();
    assertThat(row[2]).isEqualTo(tenant.userId());
    assertThat(row[3]).isEqualTo(tenant.editorId());
  }
}
