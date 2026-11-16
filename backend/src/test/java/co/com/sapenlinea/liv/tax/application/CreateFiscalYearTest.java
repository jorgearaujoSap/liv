package co.com.sapenlinea.liv.tax.application;

import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.ADMINISTRATION;
import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.com.sapenlinea.liv.platform.Correlation;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Creating a fiscal year with a double of the port: each rejection, its code and its extensions.
 */
@DisplayName("Crear una vigencia fiscal (007-04 a 007-08)")
class CreateFiscalYearTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

  /** 31 December 2026, 23:30 in Bogotá; already 2027 in UTC. */
  private static final Instant LAST_NIGHT_OF_2026 = Instant.parse("2027-01-01T04:30:00Z");

  private final FakeFiscalYears fiscalYears = new FakeFiscalYears();
  private final List<Object> events = new ArrayList<>();

  @BeforeEach
  void putTheCorrelationOfTheRequest() {
    MDC.put(Correlation.MDC_KEY, "correlation-1");
  }

  @AfterEach
  void removeTheCorrelation() {
    MDC.remove(Correlation.MDC_KEY);
  }

  private CreateFiscalYear useCase(Instant now) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return new CreateFiscalYear(fiscalYears, events::add, clock, BOGOTA);
  }

  private RuleViolation rejected(CreateFiscalYear useCase, int year) {
    RuleViolation violation =
        catchThrowableOfType(
            RuleViolation.class, () -> useCase.create(FakeFiscalYears.user(), year));
    assertThat(violation).as("el año %d debe rechazarse", year).isNotNull();
    return violation;
  }

  @Test
  @Tag("007-04")
  @DisplayName("007-04 · Crea la vigencia PLANEADA con las fechas del año, bajo el bloqueo")
  void createsThePlannedYear() {
    var stored = useCase(LAST_NIGHT_OF_2026).create(FakeFiscalYears.user(), 2026);

    assertThat(fiscalYears.calls().subList(0, 4))
        .containsExactly("lock", "findAll", "national", "create");
    assertThat(stored.fiscalYear().status()).isEqualTo(FiscalYearStatus.PLANEADA);
    assertThat(stored.fiscalYear().assessmentsEnabled()).isFalse();
    assertThat(stored.startsOn()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(stored.endsOn()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(fiscalYears.findAll(ADMINISTRATION)).hasSize(1);
    // The lock is of the administration, not of the user, and goes before any read.
    assertThat(fiscalYears.lockedAdministrations()).containsExactly(ADMINISTRATION);
  }

  @Test
  @Tag("007-04")
  @DisplayName(
      "007-04 · Publica FiscalYearCreated con la fila, el año, el usuario y el estado nuevo")
  void publishesTheEvent() {
    var stored = useCase(LAST_NIGHT_OF_2026).create(FakeFiscalYears.user(), 2026);

    assertThat(events).hasSize(1);
    FiscalYearCreated event = (FiscalYearCreated) events.getFirst();
    assertThat(event.eventId()).isNotNull();
    assertThat(event.administrationId()).isEqualTo(ADMINISTRATION);
    assertThat(event.userId()).isEqualTo(USER);
    assertThat(event.id()).isEqualTo(stored.id());
    assertThat(event.year()).isEqualTo(2026);
    assertThat(event.previousStatus()).isNull();
    assertThat(event.newStatus()).isEqualTo("PLANEADA");
    assertThat(event.occurredAt()).isEqualTo(LAST_NIGHT_OF_2026);
    assertThat(event.correlationId()).isEqualTo("correlation-1");
  }

  @Test
  @Tag("007-04")
  @DisplayName("007-04 · Cada evento lleva su propio eventId: la auditoría guarda cada uno una vez")
  void everyEventHasItsOwnId() {
    CreateFiscalYear useCase = useCase(LAST_NIGHT_OF_2026);

    useCase.create(FakeFiscalYears.user(), 2026);
    useCase.create(FakeFiscalYears.user(), 2027);

    assertThat(events).hasSize(2);
    assertThat(
            events.stream().map(event -> ((FiscalYearCreated) event).eventId()).distinct().count())
        .isEqualTo(2);
  }

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName("V-01 · 007-05 · Un año que la administración ya tiene da V-01 y no publica nada")
  void existingYear() {
    useCase(LAST_NIGHT_OF_2026).create(FakeFiscalYears.user(), 2026);
    events.clear();

    RuleViolation violation = rejected(useCase(LAST_NIGHT_OF_2026), 2026);

    assertThat(violation.code()).isEqualTo("V-01");
    assertThat(violation.type()).isEqualTo(RuleViolation.Type.CONFLICT);
    assertThat(violation.details()).containsEntry("year", 2026);
    assertThat(events).isEmpty();
  }

  @Test
  @Tag("007-05")
  @DisplayName("007-05 · El mismo año en otra administración no choca con el de esta")
  void sameYearInAnotherAdministrationIsNotAConflict() {
    fiscalYears.given(FakeFiscalYears.OTHER_ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);

    var stored = useCase(LAST_NIGHT_OF_2026).create(FakeFiscalYears.user(), 2026);

    assertThat(stored.fiscalYear().administrationId()).isEqualTo(ADMINISTRATION);
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · Un año anterior a la vigencia más antigua se crea: V-02 ya no rechaza")
  void anOlderYearIsCreated() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);

    var stored = useCase(LAST_NIGHT_OF_2026).create(FakeFiscalYears.user(), 2025);

    assertThat(stored.fiscalYear().year()).isEqualTo(2025);
    assertThat(stored.fiscalYear().status()).isEqualTo(FiscalYearStatus.PLANEADA);
  }

  @Test
  @Tag("V-05")
  @Tag("007-07")
  @DisplayName("V-05 · 007-07 · Antes de 2002 da V-05 PAST_LIMIT con minYear, sin vigencias")
  void pastLimit() {
    RuleViolation violation = rejected(useCase(LAST_NIGHT_OF_2026), 2001);

    assertThat(violation.code()).isEqualTo("V-05");
    assertThat(violation.type()).isEqualTo(RuleViolation.Type.UNPROCESSABLE);
    assertThat(violation.details())
        .containsExactlyInAnyOrderEntriesOf(Map.of("reason", "PAST_LIMIT", "minYear", 2002));
    assertThat(events).isEmpty();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · Saltarse años se crea: 2028 con {2026} no es un hueco")
  void skippingYearsIsNotAGap() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    Instant in2028 = Instant.parse("2028-06-15T12:00:00Z");

    var stored = useCase(in2028).create(FakeFiscalYears.user(), 2028);

    assertThat(stored.fiscalYear().year()).isEqualTo(2028);
    RuleViolation violation = rejected(useCase(LAST_NIGHT_OF_2026), 2029);
    assertThat(violation.code()).isEqualTo("V-05");
    assertThat(violation.details()).containsEntry("reason", "FUTURE_LIMIT");
  }

  @Test
  @Tag("V-05")
  @Tag("007-06")
  @DisplayName("V-05 · 007-06 · Pasar del año siguiente al en curso da V-05 FUTURE_LIMIT")
  void futureLimit() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    fiscalYears.given(ADMINISTRATION, 2027, FiscalYearStatus.PLANEADA);

    RuleViolation violation = rejected(useCase(LAST_NIGHT_OF_2026), 2028);

    assertThat(violation.code()).isEqualTo("V-05");
    assertThat(violation.details())
        .containsExactlyInAnyOrderEntriesOf(Map.of("reason", "FUTURE_LIMIT", "maxYear", 2027));
  }

  @Test
  @Tag("007-08")
  @DisplayName(
      "007-08 · La frontera del año: a las 23:30 del 31 de diciembre en Bogotá se crean 2026 y 2027")
  void yearBoundaryInTheBusinessZone() {
    CreateFiscalYear useCase = useCase(LAST_NIGHT_OF_2026);

    assertThat(useCase.create(FakeFiscalYears.user(), 2026).fiscalYear().year()).isEqualTo(2026);
    assertThat(useCase.create(FakeFiscalYears.user(), 2027).fiscalYear().year()).isEqualTo(2027);
    RuleViolation violation = rejected(useCase, 2028);
    assertThat(violation.code()).isEqualTo("V-05");
    assertThat(violation.details()).containsEntry("maxYear", 2027);
  }

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName(
      "V-05 · 007-08 · Con el reloj en UTC ya sería 2027: la zona de negocio manda, no la de la máquina")
  void theBusinessZoneDecidesNotUtc() {
    // In UTC it is already 2027 (the limit would be 2028); in Bogotá it is still 2026.
    RuleViolation violation = rejected(useCase(LAST_NIGHT_OF_2026), 2028);

    assertThat(violation.details())
        .containsExactlyInAnyOrderEntriesOf(Map.of("reason", "FUTURE_LIMIT", "maxYear", 2027));
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · Un año pasado pendiente (PAST) se puede crear, en cualquier orden")
  void aPastYearCanBeCreatedInAnyOrder() {
    // The latest is 2026 and it is 2028: 2027 is past, 2028 current and 2029 next.
    Instant inTheMiddleOf2028 = Instant.parse("2028-06-15T12:00:00Z");
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    CreateFiscalYear useCase = useCase(inTheMiddleOf2028);

    assertThat(useCase.create(FakeFiscalYears.user(), 2028).fiscalYear().year()).isEqualTo(2028);
    assertThat(useCase.create(FakeFiscalYears.user(), 2027).fiscalYear().year()).isEqualTo(2027);
    assertThat(useCase.create(FakeFiscalYears.user(), 2002).fiscalYear().year()).isEqualTo(2002);
    assertThat(rejected(useCase, 2030).details())
        .containsExactlyInAnyOrderEntriesOf(Map.of("reason", "FUTURE_LIMIT", "maxYear", 2029));
  }
}
