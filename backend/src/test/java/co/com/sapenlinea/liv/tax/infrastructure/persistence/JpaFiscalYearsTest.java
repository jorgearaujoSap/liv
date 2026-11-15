package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.AdministrationContext;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.application.CreateFiscalYear;
import co.com.sapenlinea.liv.tax.application.FiscalYears;
import co.com.sapenlinea.liv.tax.application.FiscalYears.Stored;
import co.com.sapenlinea.liv.tax.application.OpenFiscalYearPreparation;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The adapter of the port with PostgreSQL 18 in Testcontainers, connected as {@code liv_app} like
 * the application, with its row-level security and its column privileges. Each test forces its own
 * interleaving instead of hoping the requests meet: the real {@code UNIQUE}, the opening that loses
 * the race and the lock of the creation, with its variant without the lock (plan, step 6). Every
 * test works with an administration of its own, because the container is shared and {@code liv_app}
 * cannot delete what it creates.
 */
@DisplayName("Persistencia de las vigencias con el adaptador real (007-05, 007-10, CA-12)")
class JpaFiscalYearsTest extends PlatformWebTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final String ISSUER = "https://idp.test/realms/liv";

  /** 31 December 2026, 23:30 in Bogotá (already 2027 in UTC), and one hour later. */
  private static final Instant BEFORE_MIDNIGHT = Instant.parse("2027-01-01T04:30:00Z");

  private static final Instant AFTER_MIDNIGHT = Instant.parse("2027-01-01T05:30:00Z");

  @Autowired private FiscalYears fiscalYears;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private final ExecutorService threads = Executors.newCachedThreadPool();

  @AfterEach
  void stopThreads() {
    threads.shutdownNow();
  }

  /** An administration with one user, created by the owner: nothing else touches its years. */
  private record Tenant(UUID administrationId, UUID userId) {

    CurrentUser user() {
      return new CurrentUser(userId, administrationId, ISSUER, "subject-" + userId, Set.of("ROLE"));
    }
  }

  private static Tenant newTenant() {
    UUID administration = UUID.randomUUID();
    LivDatabase.registerAdministration(administration, "Administración " + administration);
    LivDatabase.registerUser(administration, ISSUER, "jpa-" + administration, "ACTIVE");
    UUID user =
        (UUID)
            LivDatabase.readAsOwner(
                    "SELECT id FROM core.admin_user WHERE administration_id = ?", administration)
                .getFirst()[0];
    return new Tenant(administration, user);
  }

  private <T> T inTransaction(Tenant tenant, int propagation, Supplier<T> work) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    template.setPropagationBehavior(propagation);
    try (AdministrationContext.Scope ignored =
        AdministrationContext.open(tenant.administrationId())) {
      return template.execute(status -> work.get());
    }
  }

  private <T> T inTransaction(Tenant tenant, Supplier<T> work) {
    return inTransaction(tenant, TransactionDefinition.PROPAGATION_REQUIRED, work);
  }

  private Stored create(Tenant tenant, int year) {
    return inTransaction(
        tenant,
        () ->
            fiscalYears.create(
                AdministrationFiscalYear.plan(tenant.administrationId(), year), tenant.userId()));
  }

  private static List<Object[]> rowsOf(Tenant tenant) {
    return LivDatabase.readAsOwner(
        "SELECT fiscal_year, status, created_by, updated_by, updated_at, created_at"
            + " FROM tax.administration_fiscal_year WHERE administration_id = ?"
            + " ORDER BY fiscal_year",
        tenant.administrationId());
  }

  private static Set<Integer> yearsOf(Tenant tenant) {
    Set<Integer> years = new TreeSet<>();
    rowsOf(tenant).forEach(row -> years.add(((Number) row[0]).intValue()));
    return years;
  }

  // ---- lo que la entidad escribe y lee

  @Test
  @Tag("007-04")
  @DisplayName(
      "007-04 · Crea con liv_app: solo inserta administración, año y autor; el resto lo pone la base")
  void creationInsertsOnlyThePermittedColumns() {
    Tenant tenant = newTenant();

    Stored stored = create(tenant, 2026);

    assertThat(stored.id()).isNotNull();
    assertThat(stored.fiscalYear().status()).isEqualTo(FiscalYearStatus.PLANEADA);
    assertThat(stored.fiscalYear().assessmentsEnabled()).isFalse();
    assertThat(stored.startsOn()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(stored.endsOn()).isEqualTo(LocalDate.of(2026, 12, 31));
    Object[] row = rowsOf(tenant).getFirst();
    assertThat(row[1]).isEqualTo("PLANEADA");
    assertThat(row[2]).isEqualTo(tenant.userId());
    assertThat(row[3]).isNull();
    assertThat(row[4]).isNull();
    assertThat(
            LivDatabase.readAsOwner(
                    "SELECT id FROM tax.administration_fiscal_year WHERE administration_id = ?",
                    tenant.administrationId())
                .getFirst()[0])
        .isEqualTo(stored.id());
  }

  @Test
  @DisplayName("La tabla nacional sale de la base, completa y con sus fechas")
  void nationalYearsComeFromTheTable() {
    Tenant tenant = newTenant();

    List<NationalFiscalYear> national = inTransaction(tenant, () -> fiscalYears.nationalYears());

    assertThat(national).hasSize(102);
    assertThat(national)
        .contains(
            new NationalFiscalYear(2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    assertThat(national.getFirst().year()).isEqualTo(1999);
    assertThat(national.getLast().year()).isEqualTo(2100);
  }

  @Test
  @Tag("007-01")
  @DisplayName(
      "007-01 · findAll devuelve las vigencias de la administración, la más reciente primero")
  void findAllIsNewestFirstAndOnlyOwn() {
    Tenant tenant = newTenant();
    Tenant other = newTenant();
    create(tenant, 2026);
    create(tenant, 2027);
    create(other, 2026);

    List<Stored> found =
        inTransaction(tenant, () -> fiscalYears.findAll(tenant.administrationId()));

    assertThat(found).extracting(item -> item.fiscalYear().year()).containsExactly(2027, 2026);
  }

  // ---- el UNIQUE real

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName(
      "V-01 · 007-05 · El UNIQUE real: guardar un año que ya existe, sin pasar por el bloqueo, da V-01")
  void theRealUniqueBecomesV01() {
    Tenant tenant = newTenant();
    LivDatabase.writeAsOwner(
        "INSERT INTO tax.administration_fiscal_year (administration_id, fiscal_year, created_by)"
            + " VALUES (?, 2026, ?)",
        tenant.administrationId(),
        tenant.userId());

    // No lockForCreation and no policy: the INSERT reaches the UNIQUE (administration_id, year).
    Throwable thrown =
        catchThrowableOfType(
            Throwable.class,
            () ->
                inTransaction(
                    tenant,
                    () ->
                        fiscalYears.create(
                            AdministrationFiscalYear.plan(tenant.administrationId(), 2026),
                            tenant.userId())));

    assertThat(thrown).isInstanceOf(RuleViolation.class);
    RuleViolation violation = (RuleViolation) thrown;
    assertThat(violation.code()).isEqualTo("V-01");
    assertThat(violation.type()).isEqualTo(RuleViolation.Type.CONFLICT);
    assertThat(violation.details()).containsExactlyEntriesOf(Map.of("year", 2026));
    assertThat(yearsOf(tenant)).containsExactly(2026);
  }

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName("V-01 · 007-05 · Otra falla de integridad no se disfraza de V-01")
  void anotherIntegrityFailureIsNotV01() {
    Tenant tenant = newTenant();
    UUID aUserOfAnotherAdministration = newTenant().userId();

    // The composite FK (created_by, administration_id) rejects an author of another administration.
    assertThatThrownBy(() ->
            inTransaction(
                tenant,
                () ->
                    fiscalYears.create(
                        AdministrationFiscalYear.plan(tenant.administrationId(), 2026),
                        aUserOfAnotherAdministration)))
        .isNotInstanceOf(RuleViolation.class);
    assertThat(yearsOf(tenant)).isEmpty();
  }

  // ---- la apertura

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · La apertura que gana dice EN_PREPARACION y Hibernate no emite ningún UPDATE propio")
  void theOpeningThatWinsEmitsNoHibernateUpdate() {
    Tenant tenant = newTenant();
    Stored planned = create(tenant, 2026);
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    Stored opened =
        inTransaction(
            tenant,
            () -> {
              Stored seen = fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
              return fiscalYears.persistOpened(seen, tenant.userId());
            });

    assertThat(opened.id()).isEqualTo(planned.id());
    assertThat(opened.fiscalYear().status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertThat(opened.fiscalYear().assessmentsEnabled()).isFalse();
    assertThat(statistics.getEntityUpdateCount()).as("UPDATE de entidades").isZero();
    Object[] row = rowsOf(tenant).getFirst();
    assertThat(row[1]).isEqualTo("EN_PREPARACION");
    assertThat(row[3]).isEqualTo(tenant.userId());
    Timestamp updatedAt = (Timestamp) toTimestamp(row[4]);
    Timestamp createdAt = (Timestamp) toTimestamp(row[5]);
    assertThat(updatedAt).isNotNull().isAfterOrEqualTo(createdAt);
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · La apertura que pierde la carrera afecta 0 filas y da V-06 con EN_PREPARACION, nunca un 503")
  void theOpeningThatLosesTheRaceIsV06() {
    Tenant tenant = newTenant();
    create(tenant, 2026);

    Throwable thrown =
        catchThrowableOfType(
            Throwable.class,
            () ->
                inTransaction(
                    tenant,
                    () -> {
                      // T1 reads the year as PLANEADA ...
                      Stored seenByT1 =
                          fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
                      // ... T2 opens it and commits ...
                      inTransaction(
                          tenant,
                          TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                          () -> fiscalYears.persistOpened(seenByT1, tenant.userId()));
                      // ... and the UPDATE of T1 affects 0 rows.
                      return fiscalYears.persistOpened(seenByT1, tenant.userId());
                    }));

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
      "007-10 · Un reintento después de un éxito también da V-06, con la vigencia ya abierta")
  void aRetryAfterASuccessIsV06() {
    Tenant tenant = newTenant();
    create(tenant, 2026);
    Clock clock = Clock.fixed(BEFORE_MIDNIGHT, ZoneOffset.UTC);
    OpenFiscalYearPreparation open = new OpenFiscalYearPreparation(fiscalYears, event -> {}, clock);

    Stored first = inTransaction(tenant, () -> open.open(tenant.user(), 2026));
    RuleViolation retry =
        catchThrowableOfType(
            RuleViolation.class, () -> inTransaction(tenant, () -> open.open(tenant.user(), 2026)));

    assertThat(first.fiscalYear().status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertThat(retry.code()).isEqualTo("V-06");
    assertThat(retry.details()).containsEntry("currentStatus", "EN_PREPARACION");
  }

  @Test
  @Tag("CA-12")
  @DisplayName("CA-12 · Una vigencia de otra administración da NotFound, también al abrirla")
  void aFiscalYearOfAnotherAdministrationDoesNotExist() {
    Tenant owner = newTenant();
    Tenant stranger = newTenant();
    create(owner, 2026);
    Clock clock = Clock.fixed(BEFORE_MIDNIGHT, ZoneOffset.UTC);
    OpenFiscalYearPreparation open = new OpenFiscalYearPreparation(fiscalYears, event -> {}, clock);

    // Even asking for the id of the owner, the row-level security of the stranger hides the row.
    assertThat(inTransaction(stranger, () -> fiscalYears.find(owner.administrationId(), 2026)))
        .isEmpty();
    assertThatThrownBy(() -> inTransaction(stranger, () -> open.open(stranger.user(), 2026)))
        .isInstanceOf(NotFound.class);
    assertThat(rowsOf(owner).getFirst()[1]).isEqualTo("PLANEADA");
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Crear y abrir corren en READ_COMMITTED: lo exige el bloqueo de la creación")
  void creationAndOpeningUseReadCommitted() throws NoSuchMethodException {
    Method create = CreateFiscalYear.class.getMethod("create", CurrentUser.class, int.class);
    Method open = OpenFiscalYearPreparation.class.getMethod("open", CurrentUser.class, int.class);

    for (Method method : List.of(create, open)) {
      Transactional transactional = method.getAnnotation(Transactional.class);
      assertThat(transactional).as(method.getName()).isNotNull();
      assertThat(transactional.isolation())
          .as(method.getName())
          .isEqualTo(org.springframework.transaction.annotation.Isolation.READ_COMMITTED);
    }
  }

  // ---- el bloqueo de la creación

  /**
   * The port with a pause the test controls: it stops the thread that crosses it until the test
   * releases it. {@code withLock} false empties {@code lockForCreation}, which shows that the tests
   * detect the missing lock.
   */
  private static final class PausingFiscalYears implements FiscalYears {

    enum PausePoint {
      NEVER,
      AFTER_LOCK,
      AFTER_READS
    }

    private final FiscalYears delegate;
    private final boolean withLock;
    private final PausePoint pausePoint;
    final CountDownLatch paused = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    PausingFiscalYears(FiscalYears delegate, boolean withLock, PausePoint pausePoint) {
      this.delegate = delegate;
      this.withLock = withLock;
      this.pausePoint = pausePoint;
    }

    private void pauseAt(PausePoint point) {
      if (point != pausePoint) {
        return;
      }
      paused.countDown();
      try {
        if (!release.await(30, TimeUnit.SECONDS)) {
          throw new IllegalStateException("The test never released the pause");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void lockForCreation(UUID administrationId) {
      if (withLock) {
        delegate.lockForCreation(administrationId);
      }
      pauseAt(PausePoint.AFTER_LOCK);
    }

    @Override
    public List<Stored> findAll(UUID administrationId) {
      return delegate.findAll(administrationId);
    }

    @Override
    public java.util.Optional<Stored> find(UUID administrationId, int year) {
      return delegate.find(administrationId, year);
    }

    @Override
    public List<NationalFiscalYear> nationalYears() {
      List<NationalFiscalYear> table = delegate.nationalYears();
      // CreateFiscalYear reads the years of the administration first and the national table after.
      pauseAt(PausePoint.AFTER_READS);
      return table;
    }

    @Override
    public Stored create(AdministrationFiscalYear fiscalYear, UUID createdBy) {
      return delegate.create(fiscalYear, createdBy);
    }

    @Override
    public Stored persistOpened(Stored current, UUID openedBy) {
      return delegate.persistOpened(current, openedBy);
    }
  }

  private Future<Stored> createInAThread(Tenant tenant, FiscalYears port, Instant now, int year) {
    CreateFiscalYear useCase =
        new CreateFiscalYear(port, event -> {}, Clock.fixed(now, ZoneOffset.UTC), BOGOTA);
    return threads.submit(() -> inTransaction(tenant, () -> useCase.create(tenant.user(), year)));
  }

  private static void awaitOrFail(CountDownLatch latch, String what) throws InterruptedException {
    assertThat(latch.await(30, TimeUnit.SECONDS)).as(what).isTrue();
  }

  /** Waits until some session of the database waits for an advisory lock it has not been given. */
  private static void awaitASessionWaitingForTheLock() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Number waiting =
          (Number)
              LivDatabase.readAsOwner(
                      "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted")
                  .getFirst()[0];
      if (waiting.longValue() > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("No session ever waited for the advisory lock");
  }

  @Test
  @Tag("V-02")
  @Tag("007-05")
  @DisplayName(
      "007-05 · El bloqueo: la creación de después de la medianoche espera a la de antes y recibe V-02 GAP")
  void theLockSerializesTheCreationsAcrossMidnight() throws Exception {
    Tenant tenant = newTenant();
    // A works at 23:30 of 31 December (the year is 2026), B at 00:30 of 1 January (2027).
    PausingFiscalYears a =
        new PausingFiscalYears(fiscalYears, true, PausingFiscalYears.PausePoint.AFTER_LOCK);
    Future<Stored> creationA = createInAThread(tenant, a, BEFORE_MIDNIGHT, 2026);
    awaitOrFail(a.paused, "A holds the lock");

    PausingFiscalYears b =
        new PausingFiscalYears(fiscalYears, true, PausingFiscalYears.PausePoint.NEVER);
    Future<Stored> creationB = createInAThread(tenant, b, AFTER_MIDNIGHT, 2028);
    awaitASessionWaitingForTheLock();
    // B is waiting in the database, and it keeps waiting while A holds the lock.
    assertThatThrownBy(() -> creationB.get(1, TimeUnit.SECONDS))
        .isInstanceOf(TimeoutException.class);
    assertThat(yearsOf(tenant)).isEmpty();

    a.release.countDown();
    assertThat(creationA.get(30, TimeUnit.SECONDS).fiscalYear().year()).isEqualTo(2026);
    assertThatThrownBy(() -> creationB.get(30, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(RuleViolation.class)
        .satisfies(e -> {
          RuleViolation violation = (RuleViolation) e.getCause();
          assertThat(violation.code()).isEqualTo("V-02");
          assertThat(violation.details())
              .containsExactlyInAnyOrderEntriesOf(Map.of("reason", "GAP", "requiredYear", 2027));
        });
    assertThat(yearsOf(tenant)).containsExactly(2026);
  }

  @Test
  @Tag("007-05")
  @DisplayName(
      "007-05 · La misma prueba con lockForCreation vacío deja {2026, 2028}: la prueba detecta la falta del bloqueo")
  void withoutTheLockTheTestSeesTheGap() throws Exception {
    Tenant tenant = newTenant();
    // A reads the empty administration and stops before it writes; B runs entirely meanwhile.
    PausingFiscalYears a =
        new PausingFiscalYears(fiscalYears, false, PausingFiscalYears.PausePoint.AFTER_READS);
    Future<Stored> creationA = createInAThread(tenant, a, BEFORE_MIDNIGHT, 2026);
    awaitOrFail(a.paused, "A read the empty administration");

    PausingFiscalYears b =
        new PausingFiscalYears(fiscalYears, false, PausingFiscalYears.PausePoint.NEVER);
    assertThat(
            createInAThread(tenant, b, AFTER_MIDNIGHT, 2028)
                .get(30, TimeUnit.SECONDS)
                .fiscalYear()
                .year())
        .isEqualTo(2028);

    a.release.countDown();
    assertThat(creationA.get(30, TimeUnit.SECONDS).fiscalYear().year()).isEqualTo(2026);
    assertThat(yearsOf(tenant)).containsExactly(2026, 2028);
  }

  private static Object toTimestamp(Object value) {
    return value instanceof java.time.OffsetDateTime offset
        ? Timestamp.from(offset.toInstant())
        : value;
  }
}
