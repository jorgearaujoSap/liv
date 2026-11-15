package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import co.com.sapenlinea.liv.tax.application.FiscalYears;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.RegisteredFiscalYear;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the tests of the adapter share: PostgreSQL 18 in Testcontainers, the application connected
 * as {@code liv_app} like in production (row-level security and column privileges), and an
 * administration of its own for each test, because the container is shared and {@code liv_app}
 * cannot delete what it creates.
 */
abstract class FiscalYearsPersistenceTestBase extends PlatformWebTest {

  static final String ISSUER = "https://idp.test/realms/liv";

  protected final FiscalYears fiscalYears;
  private final PlatformTransactionManager transactionManager;

  protected FiscalYearsPersistenceTestBase(
      FiscalYears fiscalYears, PlatformTransactionManager transactionManager) {
    this.fiscalYears = fiscalYears;
    this.transactionManager = transactionManager;
  }

  /**
   * An administration with two users, created by the owner: nothing else touches its years. The
   * second one ({@code editorId}) opens what the first one created, so the editor of a row is never
   * taken from its author.
   */
  record Tenant(UUID administrationId, UUID userId, UUID editorId) {

    CurrentUser user() {
      return new CurrentUser(userId, administrationId, ISSUER, "subject-" + userId, Set.of("ROLE"));
    }

    CurrentUser editor() {
      return new CurrentUser(
          editorId, administrationId, ISSUER, "subject-" + editorId, Set.of("ROLE"));
    }
  }

  static Tenant newTenant() {
    UUID administration = UUID.randomUUID();
    LivDatabase.registerAdministration(administration, "Administración " + administration);
    LivDatabase.registerUser(administration, ISSUER, "jpa-a-" + administration, "ACTIVE");
    LivDatabase.registerUser(administration, ISSUER, "jpa-b-" + administration, "ACTIVE");
    List<Object[]> users =
        LivDatabase.readAsOwner(
            "SELECT id FROM core.admin_user WHERE administration_id = ?"
                + " ORDER BY identity_subject",
            administration);
    return new Tenant(administration, (UUID) users.get(0)[0], (UUID) users.get(1)[0]);
  }

  /**
   * Runs the work in a transaction at {@code READ_COMMITTED}, like the use cases, as the tenant.
   */
  protected <T> T inTransaction(Tenant tenant, int propagation, Supplier<T> work) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    template.setPropagationBehavior(propagation);
    try (AdministrationContext.Scope ignored =
        AdministrationContext.open(tenant.administrationId())) {
      return template.execute(status -> work.get());
    }
  }

  protected <T> T inTransaction(Tenant tenant, Supplier<T> work) {
    return inTransaction(tenant, TransactionDefinition.PROPAGATION_REQUIRED, work);
  }

  protected RegisteredFiscalYear create(Tenant tenant, int year) {
    return inTransaction(
        tenant,
        () ->
            fiscalYears.create(
                AdministrationFiscalYear.plan(tenant.administrationId(), year), tenant.userId()));
  }

  /** The rows of the tenant as the owner sees them: year, state, author, editor, editing time. */
  static List<Object[]> rowsOf(Tenant tenant) {
    return LivDatabase.readAsOwner(
        "SELECT fiscal_year, status, created_by, updated_by, updated_at, created_at"
            + " FROM tax.administration_fiscal_year WHERE administration_id = ?"
            + " ORDER BY fiscal_year",
        tenant.administrationId());
  }

  static Set<Integer> yearsOf(Tenant tenant) {
    Set<Integer> years = new TreeSet<>();
    rowsOf(tenant).forEach(row -> years.add(((Number) row[0]).intValue()));
    return years;
  }
}
