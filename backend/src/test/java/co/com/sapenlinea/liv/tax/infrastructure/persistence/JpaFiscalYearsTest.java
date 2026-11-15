package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.application.CreateFiscalYear;
import co.com.sapenlinea.liv.tax.application.FiscalYears;
import co.com.sapenlinea.liv.tax.application.FiscalYears.Stored;
import co.com.sapenlinea.liv.tax.application.OpenFiscalYearPreparation;
import co.com.sapenlinea.liv.tax.domain.AdministrationFiscalYear;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.NationalFiscalYear;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The adapter of the port with PostgreSQL 18 in Testcontainers, connected as {@code liv_app} like
 * the application: what the entity writes and reads, the opening that wins, the isolation between
 * administrations and the errors that must not be mistaken for a rule. The interleavings (the
 * {@code UNIQUE}, the races of the opening, the lock of the creation) are in {@link
 * JpaFiscalYearsRacesTest}.
 */
@DisplayName("Persistencia de las vigencias con el adaptador real (007-04, 007-10, CA-12)")
class JpaFiscalYearsTest extends FiscalYearsPersistenceTestBase {

  private static final Instant NOW = Instant.parse("2027-01-01T04:30:00Z");

  private final EntityManagerFactory entityManagerFactory;
  private final Statistics statistics;

  @Autowired
  JpaFiscalYearsTest(
      FiscalYears fiscalYears,
      PlatformTransactionManager transactionManager,
      EntityManagerFactory entityManagerFactory) {
    super(fiscalYears, transactionManager);
    this.entityManagerFactory = entityManagerFactory;
    this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  @AfterEach
  void restoreStatistics() {
    statistics.setStatisticsEnabled(false);
  }

  private OpenFiscalYearPreparation openUseCase() {
    return new OpenFiscalYearPreparation(
        fiscalYears, event -> {}, Clock.fixed(NOW, ZoneOffset.UTC));
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
  @Tag("007-04")
  @DisplayName(
      "007-04 · El mapeo de la entidad: se inserta solo lo permitido y ninguna columna se actualiza")
  void theEntityOnlyInsertsThePermittedColumnsAndNeverUpdates() {
    EntityPersister persister =
        entityManagerFactory
            .unwrap(SessionFactoryImplementor.class)
            .getMappingMetamodel()
            .getEntityDescriptor(AdministrationFiscalYearEntity.class);
    String[] names = persister.getPropertyNames();
    boolean[] insertable = persister.getPropertyInsertability();
    boolean[] updatable = persister.getPropertyUpdateability();

    assertThat(names).isNotEmpty();
    for (int i = 0; i < names.length; i++) {
      boolean permitted = List.of("administrationId", "fiscalYear", "createdBy").contains(names[i]);
      assertThat(insertable[i]).as("insertable %s", names[i]).isEqualTo(permitted);
      assertThat(updatable[i]).as("updatable %s", names[i]).isFalse();
    }
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Una entidad modificada en memoria no genera ningún UPDATE de Hibernate al vaciar")
  void aDirtyEntityNeverProducesAnUpdate() {
    Tenant tenant = newTenant();
    Stored stored = create(tenant, 2026);
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    inTransaction(
        tenant,
        () -> {
          EntityManager em =
              EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
          AdministrationFiscalYearEntity row =
              em.find(AdministrationFiscalYearEntity.class, stored.id());
          change(row, "status", "LISTA");
          change(row, "updatedBy", tenant.userId());
          change(row, "updatedAt", Instant.now());
          em.flush();
          return null;
        });

    assertThat(statistics.getEntityUpdateCount()).as("UPDATE de entidades").isZero();
    assertThat(rowsOf(tenant).getFirst()[1]).isEqualTo("PLANEADA");
  }

  private static void change(Object target, String field, Object value) {
    try {
      Field found = target.getClass().getDeclaredField(field);
      found.setAccessible(true);
      found.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  @Tag("007-03")
  @DisplayName(
      "007-03 · La tabla nacional sale de la base, completa y con sus fechas: de ella salen los años que se ofrecen")
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

  // ---- errores que no son una regla

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · Otra falla de integridad (la FK del autor) no se disfraza de V-01")
  void anotherIntegrityFailureIsNotV01() {
    Tenant tenant = newTenant();
    UUID aUserOfAnotherAdministration = newTenant().userId();

    // The composite FK (created_by, administration_id) rejects an author of another administration.
    DataIntegrityViolationException thrown =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () ->
                inTransaction(
                    tenant,
                    () ->
                        fiscalYears.create(
                            AdministrationFiscalYear.plan(tenant.administrationId(), 2026),
                            aUserOfAnotherAdministration)));

    assertThat(thrown).isNotNull();
    Throwable root = thrown;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    assertThat(((SQLException) root).getSQLState()).isEqualTo("23503");
    assertThat(yearsOf(tenant)).isEmpty();
  }

  // ---- la apertura

  @Test
  @Tag("007-10")
  @DisplayName("007-10 · La apertura que gana dice EN_PREPARACION, con su autor y su hora")
  void theOpeningThatWins() {
    Tenant tenant = newTenant();
    Stored planned = create(tenant, 2026);

    Stored opened =
        inTransaction(
            tenant,
            () -> {
              Stored seen = fiscalYears.find(tenant.administrationId(), 2026).orElseThrow();
              return fiscalYears.persistOpened(seen, tenant.editorId());
            });

    assertThat(opened.id()).isEqualTo(planned.id());
    assertThat(opened.fiscalYear().status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertThat(opened.fiscalYear().assessmentsEnabled()).isFalse();
    Object[] row = rowsOf(tenant).getFirst();
    assertThat(row[1]).isEqualTo("EN_PREPARACION");
    // The editor is who opened it, not the author of the row.
    assertThat(row[3]).isEqualTo(tenant.editorId()).isNotEqualTo(row[2]);
    assertThat((Timestamp) row[4]).isNotNull();
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Un reintento después de un éxito también da V-06, con la vigencia ya abierta")
  void aRetryAfterASuccessIsV06() {
    Tenant tenant = newTenant();
    create(tenant, 2026);
    OpenFiscalYearPreparation open = openUseCase();

    Stored first = inTransaction(tenant, () -> open.open(tenant.editor(), 2026));
    RuleViolation retry =
        catchThrowableOfType(
            RuleViolation.class,
            () -> inTransaction(tenant, () -> open.open(tenant.editor(), 2026)));

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
    create(stranger, 2026);
    OpenFiscalYearPreparation open = openUseCase();

    // Asking for the id of the owner, the stranger finds nothing: the filter by administration and
    // the row-level security each hide the row of the owner (the stranger has its own 2026).
    assertThat(inTransaction(stranger, () -> fiscalYears.find(owner.administrationId(), 2026)))
        .isEmpty();
    assertThat(rowsOf(owner).getFirst()[1]).isEqualTo("PLANEADA");
    assertThat(rowsOf(stranger).getFirst()[1]).isEqualTo("PLANEADA");
  }

  @Test
  @Tag("CA-12")
  @DisplayName(
      "CA-12 · Un año que no cabe en SMALLINT no apunta a otra vigencia: no existe, y no se abre nada")
  void aYearOutsideSmallintDoesNotWrapAround() {
    Tenant tenant = newTenant();
    create(tenant, 2026);
    OpenFiscalYearPreparation open = openUseCase();
    // (short) 67562 is 2026: a cast without a range check would open the year of 2026.
    int wrapsTo2026 = 65_536 + 2026;

    assertThat(
            inTransaction(tenant, () -> fiscalYears.find(tenant.administrationId(), wrapsTo2026)))
        .isEmpty();
    assertThatThrownBy(() -> inTransaction(tenant, () -> open.open(tenant.user(), wrapsTo2026)))
        .isInstanceOf(NotFound.class);
    assertThat(rowsOf(tenant).getFirst()[1]).isEqualTo("PLANEADA");
  }

  // ---- el aislamiento de los casos de uso

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Crear y abrir declaran READ_COMMITTED, lo que exige el bloqueo de la creación")
  void creationAndOpeningDeclareReadCommitted() throws NoSuchMethodException {
    Method create = CreateFiscalYear.class.getMethod("create", CurrentUser.class, int.class);
    Method open = OpenFiscalYearPreparation.class.getMethod("open", CurrentUser.class, int.class);

    for (Method method : List.of(create, open)) {
      Transactional transactional = method.getAnnotation(Transactional.class);
      assertThat(transactional).as(method.getName()).isNotNull();
      assertThat(transactional.isolation())
          .as(method.getName())
          .isEqualTo(Isolation.READ_COMMITTED);
    }
  }

  @Test
  @Tag("CA-12")
  @DisplayName("CA-12 · Crear un año que no cabe en SMALLINT falla: no se trunca a otra vigencia")
  void creatingAYearOutsideSmallintFails() {
    Tenant tenant = newTenant();
    int wrapsTo2026 = 65_536 + 2026;

    assertThatThrownBy(() ->
            inTransaction(
                tenant,
                () ->
                    fiscalYears.create(
                        AdministrationFiscalYear.plan(tenant.administrationId(), wrapsTo2026),
                        tenant.userId())))
        .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
        .hasMessageContaining("does not fit the column");
    assertThat(yearsOf(tenant)).isEmpty();
  }
}
