package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.application.CreateFiscalYear;
import co.com.sapenlinea.liv.tax.application.FiscalYears;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import co.com.sapenlinea.liv.tax.domain.RegisteredFiscalYear;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Each test forces its own interleaving instead of hoping the requests meet (plan, step 6): the
 * real {@code UNIQUE} (the competing row appears after the adapter has decided), the opening that
 * loses the race, the update time against a row created later, and the lock of the creation with
 * its variant without the lock, which shows that the test detects the missing lock. The adapter
 * under test is the real one, connected as {@code liv_app}.
 */
@DisplayName("Intercalaciones de la persistencia de las vigencias (007-05, 007-10)")
class JpaFiscalYearsRacesTest extends FiscalYearsPersistenceTestBase {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  /** 31 December 2026, 23:30 in Bogotá (already 2027 in UTC), and one hour later. */
  private static final Instant BEFORE_MIDNIGHT = Instant.parse("2027-01-01T04:30:00Z");

  private static final Instant AFTER_MIDNIGHT = Instant.parse("2027-01-01T05:30:00Z");

  private static final long WAIT_SECONDS = 30;

  private final ExecutorService threads = Executors.newCachedThreadPool();

  @Autowired
  JpaFiscalYearsRacesTest(FiscalYears fiscalYears, PlatformTransactionManager transactionManager) {
    super(fiscalYears, transactionManager);
  }

  @AfterEach
  void stopThreads() throws InterruptedException {
    threads.shutdownNow();
    // The next test must not meet a session that this one left waiting.
    threads.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  // ---- el UNIQUE real

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName(
      "V-01 · 007-05 · El UNIQUE real: la fila rival aparece después de que el adaptador decidió, y da V-01")
  void theRealUniqueBecomesV01() throws Exception {
    Tenant tenant = newTenant();
    AtomicReference<Future<Throwable>> attempt = new AtomicReference<>();

    // The owner writes 2026 and leaves its transaction open: the adapter does not see the row, goes
    // to the INSERT and waits on the unique index until the owner commits. No lock, no policy.
    LivDatabase.writeAsOwnerUncommittedDuring(
        "INSERT INTO tax.administration_fiscal_year (administration_id, fiscal_year, created_by)"
            + " VALUES (?, 2026, ?)",
        new Object[] {tenant.administrationId(), tenant.userId()},
        "administration_fiscal_year",
        () -> attempt.set(threads.submit(() -> catchThrowable(() -> create(tenant, 2026)))));

    Throwable thrown = attempt.get().get(WAIT_SECONDS, TimeUnit.SECONDS);
    assertThat(thrown).isInstanceOf(RuleViolation.class);
    RuleViolation violation = (RuleViolation) thrown;
    assertThat(violation.code()).isEqualTo("V-01");
    assertThat(violation.type()).isEqualTo(RuleViolation.Type.CONFLICT);
    assertThat(violation.details()).containsExactlyEntriesOf(Map.of("year", 2026));
    assertThat(yearsOf(tenant)).containsExactly(2026);
  }

  // ---- la apertura

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · La apertura que pierde la carrera afecta 0 filas y da V-06 con EN_PREPARACION, nunca un 503")
  void theOpeningThatLosesTheRaceIsV06() {
    Tenant tenant = newTenant();
    create(tenant, 2026);

    Throwable thrown =
        catchThrowable(() ->
            inTransaction(
                tenant,
                () -> {
                  // T1 reads the year as PLANEADA ...
                  RegisteredFiscalYear seenByT1 =
                      fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
                  // ... T2 opens it and commits ...
                  inTransaction(
                      tenant,
                      TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                      () -> fiscalYears.persistOpened(seenByT1, tenant.userId()));
                  // ... and the UPDATE of T1 affects 0 rows.
                  return fiscalYears.persistOpened(seenByT1, tenant.userId());
                }));

    assertOpeningLost(thrown, tenant);
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Dos aperturas a la vez: la segunda espera el bloqueo de la fila, lo reevalúa y da V-06")
  void twoOpeningsAtTheSameTime() throws Exception {
    Tenant tenant = newTenant();
    create(tenant, 2026);
    CountDownLatch t2HoldsTheRow = new CountDownLatch(1);
    CountDownLatch t2Release = new CountDownLatch(1);

    // T2 updates the row and keeps its transaction open: it holds the row lock.
    Future<RegisteredFiscalYear> t2 =
        threads.submit(() ->
            inTransaction(
                tenant,
                () -> {
                  RegisteredFiscalYear seen =
                      fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
                  RegisteredFiscalYear opened = fiscalYears.persistOpened(seen, tenant.userId());
                  t2HoldsTheRow.countDown();
                  await(t2Release);
                  return opened;
                }));
    assertThat(t2HoldsTheRow.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

    // T1 reads PLANEADA (T2 has not committed) and its UPDATE waits for the row of T2.
    Future<Throwable> t1 =
        threads.submit(() ->
            catchThrowable(() ->
                inTransaction(
                    tenant,
                    () -> {
                      RegisteredFiscalYear seen =
                          fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
                      return fiscalYears.persistOpened(seen, tenant.userId());
                    })));
    LivDatabase.awaitAnApplicationSessionWaitingOnALock("UPDATE tax.administration_fiscal_year");
    assertThatThrownBy(() -> t1.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

    t2Release.countDown();
    assertThat(t2.get(WAIT_SECONDS, TimeUnit.SECONDS).fiscalYear().status())
        .isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertOpeningLost(t1.get(WAIT_SECONDS, TimeUnit.SECONDS), tenant);
  }

  private static void assertOpeningLost(Throwable thrown, Tenant tenant) {
    assertThat(thrown).isInstanceOf(RuleViolation.class);
    RuleViolation violation = (RuleViolation) thrown;
    assertThat(violation.code()).isEqualTo("V-06");
    assertThat(violation.type()).isEqualTo(RuleViolation.Type.CONFLICT);
    assertThat(violation.details())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("year", 2026, "currentStatus", "EN_PREPARACION"));
    assertThat(rowsOf(tenant).getFirst()[1]).isEqualTo("EN_PREPARACION");
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Abrir una vigencia que otra transacción creó después de empezar esta no rompe el CHECK de updated_at")
  void openingARowCreatedAfterThisTransactionStarted() {
    Tenant tenant = newTenant();

    RegisteredFiscalYear opened =
        inTransaction(
            tenant,
            () -> {
              // This transaction has started: its now() is fixed here ...
              fiscalYears.nationalYears();
              // ... another one creates the year and commits, with a later created_at ...
              inTransaction(
                  tenant,
                  TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                  () ->
                      fiscalYears.create(
                          AdministrationFiscalYear.plan(tenant.administrationId(), 2026),
                          tenant.userId()));
              // ... and this one opens it: updated_at must not be before created_at.
              RegisteredFiscalYear seen =
                  fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
              return fiscalYears.persistOpened(seen, tenant.userId());
            });

    assertThat(opened.fiscalYear().status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    Object[] row = rowsOf(tenant).getFirst();
    assertThat(((Timestamp) row[4]).getTime())
        .isGreaterThanOrEqualTo(((Timestamp) row[5]).getTime());
  }

  // ---- el bloqueo de la creación

  /**
   * The port with a pause the test controls: the thread that reads the national table (after the
   * years of the administration) stops there until the test releases it, so the decision is made
   * with data that the other creation can still change. {@code withLock} false empties {@code
   * lockForCreation}, which shows that the tests detect the missing lock.
   */
  private static final class PausingFiscalYears implements FiscalYears {

    private final FiscalYears delegate;
    private final boolean withLock;
    private final boolean pauses;
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    PausingFiscalYears(FiscalYears delegate, boolean withLock, boolean pauses) {
      this.delegate = delegate;
      this.withLock = withLock;
      this.pauses = pauses;
    }

    @Override
    public void lockForCreation(UUID administrationId) {
      if (withLock) {
        delegate.lockForCreation(administrationId);
      }
    }

    @Override
    public List<RegisteredFiscalYear> findAll(UUID administrationId) {
      return delegate.findAll(administrationId);
    }

    @Override
    public Optional<RegisteredFiscalYear> find(UUID administrationId, int year) {
      return delegate.find(administrationId, year);
    }

    @Override
    public List<NationalFiscalYear> nationalYears() {
      List<NationalFiscalYear> table = delegate.nationalYears();
      if (pauses) {
        paused.countDown();
        await(release);
      }
      return table;
    }

    @Override
    public RegisteredFiscalYear create(AdministrationFiscalYear fiscalYear, UUID createdBy) {
      return delegate.create(fiscalYear, createdBy);
    }

    @Override
    public RegisteredFiscalYear persistOpened(RegisteredFiscalYear current, UUID openedBy) {
      return delegate.persistOpened(current, openedBy);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("The test never released the pause");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private Future<RegisteredFiscalYear> createInAThread(
      Tenant tenant, FiscalYears port, Instant now, int year) {
    CreateFiscalYear useCase =
        new CreateFiscalYear(port, event -> {}, Clock.fixed(now, ZoneOffset.UTC), BOGOTA);
    return threads.submit(() -> inTransaction(tenant, () -> useCase.create(tenant.user(), year)));
  }

  /**
   * The creation of 2026 at 23:30 of 31 December (A, pausing after it has read the empty
   * administration) and the one of 2028 at 00:30 of 1 January (B). With the lock, B waits for A and
   * then sees {2026}; without it, B runs entirely while A is paused.
   */
  private void creationsAcrossMidnight(boolean withLock, Tenant tenant) throws Exception {
    PausingFiscalYears a = new PausingFiscalYears(fiscalYears, withLock, true);
    Future<RegisteredFiscalYear> creationA = createInAThread(tenant, a, BEFORE_MIDNIGHT, 2026);
    assertThat(a.paused.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("A read the years").isTrue();

    PausingFiscalYears b = new PausingFiscalYears(fiscalYears, withLock, false);
    Future<RegisteredFiscalYear> creationB = createInAThread(tenant, b, AFTER_MIDNIGHT, 2028);
    if (withLock) {
      // B waits in the database for the lock of A, and keeps waiting while A holds it.
      LivDatabase.awaitAnApplicationSessionWaitingOnALock("pg_advisory_xact_lock");
      assertThatThrownBy(() -> creationB.get(1, TimeUnit.SECONDS))
          .isInstanceOf(TimeoutException.class);
      assertThat(yearsOf(tenant)).isEmpty();
    } else {
      assertThat(creationB.get(WAIT_SECONDS, TimeUnit.SECONDS).fiscalYear().year()).isEqualTo(2028);
    }

    a.release.countDown();
    assertThat(creationA.get(WAIT_SECONDS, TimeUnit.SECONDS).fiscalYear().year()).isEqualTo(2026);
    if (withLock) {
      assertThatThrownBy(() -> creationB.get(WAIT_SECONDS, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(RuleViolation.class)
          .satisfies(e -> {
            RuleViolation violation = (RuleViolation) e.getCause();
            assertThat(violation.code()).isEqualTo("V-02");
            assertThat(violation.details())
                .containsExactlyInAnyOrderEntriesOf(Map.of("reason", "GAP", "requiredYear", 2027));
          });
    }
  }

  @Test
  @Tag("V-02")
  @Tag("007-05")
  @DisplayName(
      "007-05 · El bloqueo: la creación de después de la medianoche espera a la de antes y recibe V-02 GAP")
  void theLockSerializesTheCreationsAcrossMidnight() throws Exception {
    Tenant tenant = newTenant();

    creationsAcrossMidnight(true, tenant);

    assertThat(yearsOf(tenant)).containsExactly(2026);
  }

  @Test
  @Tag("007-05")
  @DisplayName(
      "007-05 · La misma prueba con lockForCreation vacío deja {2026, 2028}: la prueba detecta la falta del bloqueo")
  void withoutTheLockTheTestSeesTheGap() throws Exception {
    Tenant tenant = newTenant();

    creationsAcrossMidnight(false, tenant);

    assertThat(yearsOf(tenant)).containsExactly(2026, 2028);
  }

  @Test
  @Tag("007-05")
  @DisplayName(
      "007-05 · El bloqueo es por administración: otra administración crea sin esperar al que lo tiene")
  void theLockIsOfTheAdministration() throws Exception {
    Tenant holder = newTenant();
    Tenant other = newTenant();
    PausingFiscalYears a = new PausingFiscalYears(fiscalYears, true, true);
    Future<RegisteredFiscalYear> creationA = createInAThread(holder, a, BEFORE_MIDNIGHT, 2026);
    assertThat(a.paused.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("A holds the lock").isTrue();

    PausingFiscalYears b = new PausingFiscalYears(fiscalYears, true, false);
    // Another administration does not wait: it finishes while A still holds its own lock.
    assertThat(
            createInAThread(other, b, BEFORE_MIDNIGHT, 2026)
                .get(WAIT_SECONDS, TimeUnit.SECONDS)
                .fiscalYear()
                .year())
        .isEqualTo(2026);

    a.release.countDown();
    assertThat(creationA.get(WAIT_SECONDS, TimeUnit.SECONDS).fiscalYear().year()).isEqualTo(2026);
  }
}
