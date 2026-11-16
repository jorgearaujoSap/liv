package co.com.sapenlinea.liv.tax.application;

import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.ADMINISTRATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.domain.CreatableYear;
import co.com.sapenlinea.liv.tax.domain.CreateBlocked;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.YearRelation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The list: its order, what can be created, and that it never offers what the creation rejects. */
@DisplayName("Listar las vigencias fiscales (007-01, 007-03)")
class ListFiscalYearsTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final Instant LAST_NIGHT_OF_2026 = Instant.parse("2027-01-01T04:30:00Z");
  private static final Instant AFTER_MIDNIGHT = Instant.parse("2027-01-01T05:30:00Z");
  private static final String CREATE = "tax.fiscal-year.create";
  private static final String READ = "tax.fiscal-year.read";

  private final FakeFiscalYears fiscalYears = new FakeFiscalYears();

  private ListFiscalYears list(Instant now, String... permissions) {
    Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    return new ListFiscalYears(
        fiscalYears, FakeFiscalYears.catalogWith(permissions), clock, BOGOTA);
  }

  @Test
  @Tag("007-01")
  @DisplayName(
      "007-01 · Las vigencias van de la más reciente a la más antigua y solo las de la administración")
  void newestFirstAndOnlyOwn() {
    fiscalYears.given(ADMINISTRATION, 2025, FiscalYearStatus.EN_PREPARACION);
    fiscalYears.given(ADMINISTRATION, 2027, FiscalYearStatus.PLANEADA);
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    fiscalYears.given(FakeFiscalYears.OTHER_ADMINISTRATION, 2028, FiscalYearStatus.PLANEADA);

    var result = list(LAST_NIGHT_OF_2026, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.items())
        .extracting(item -> item.fiscalYear().year())
        .containsExactly(2027, 2026, 2025);
  }

  @Test
  @Tag("007-03")
  @DisplayName(
      "007-03 · Sin vigencias, ofrece de 2027 a 2002 con sus fechas y su relación, del más reciente")
  void emptyListOffersTheCreatableYears() {
    var result = list(LAST_NIGHT_OF_2026, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.items()).isEmpty();
    assertThat(result.canCreate()).isTrue();
    assertThat(result.creatableYears())
        .extracting(CreatableYear::year)
        .containsExactlyElementsOf(
            IntStream.rangeClosed(2002, 2027).boxed().sorted(Comparator.reverseOrder()).toList());
    assertThat(result.creatableYears())
        .extracting(CreatableYear::year, CreatableYear::relation)
        .startsWith(
            tuple(2027, YearRelation.NEXT),
            tuple(2026, YearRelation.CURRENT),
            tuple(2025, YearRelation.PAST));
    assertThat(result.creatableYears().getFirst().startsOn()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(result.createBlocked()).isEmpty();
  }

  @Test
  @Tag("007-09")
  @DisplayName(
      "007-09 · Sin tax.fiscal-year.create, canCreate es falso y no se ofrece ni se bloquea nada")
  void withoutCreatePermission() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    fiscalYears.given(ADMINISTRATION, 2027, FiscalYearStatus.PLANEADA);

    var result = list(LAST_NIGHT_OF_2026, READ).list(FakeFiscalYears.user());

    assertThat(result.canCreate()).isFalse();
    assertThat(result.creatableYears()).isEmpty();
    assertThat(result.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Con permiso y todos los años creados, createBlocked trae ALL_CREATED con el rango")
  void createBlocked() {
    IntStream.rangeClosed(2002, 2027)
        .forEach(year -> fiscalYears.given(ADMINISTRATION, year, FiscalYearStatus.PLANEADA));

    var result = list(LAST_NIGHT_OF_2026, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.creatableYears()).isEmpty();
    assertThat(result.createBlocked())
        .contains(new CreateBlocked(CreateBlocked.Reason.ALL_CREATED, 2002, 2027));
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · Todo año anterior al en curso se ofrece con la relación PAST")
  void pastYearsAreOffered() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    Instant inTheMiddleOf2028 = Instant.parse("2028-06-15T12:00:00Z");

    var result = list(inTheMiddleOf2028, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.creatableYears())
        .extracting(CreatableYear::year, CreatableYear::relation)
        .startsWith(
            tuple(2029, YearRelation.NEXT),
            tuple(2028, YearRelation.CURRENT),
            tuple(2027, YearRelation.PAST))
        .doesNotContain(tuple(2026, YearRelation.PAST));
  }

  /**
   * The list and the creation agree: for each scenario (an empty administration, the border of the
   * year, a past year still pending, a full one) and for every year of the national table, the
   * creation of a fresh administration with the same years accepts it exactly when the list offered
   * it.
   */
  @Test
  @Tag("007-08")
  @DisplayName(
      "007-08 · La lista ofrece exactamente los años que la creación acepta, en cada escenario")
  void theListOffersExactlyWhatTheCreationAccepts() {
    record Scenario(Instant now, int... existing) {}
    for (Scenario scenario :
        List.of(
            new Scenario(LAST_NIGHT_OF_2026),
            new Scenario(AFTER_MIDNIGHT),
            new Scenario(LAST_NIGHT_OF_2026, 2026),
            new Scenario(LAST_NIGHT_OF_2026, 2026, 2027),
            new Scenario(Instant.parse("2028-06-15T12:00:00Z"), 2026),
            new Scenario(Instant.parse("2028-06-15T12:00:00Z"), 2026, 2028),
            new Scenario(LAST_NIGHT_OF_2026, 2010, 2020),
            new Scenario(LAST_NIGHT_OF_2026, 2002, 2027),
            new Scenario(LAST_NIGHT_OF_2026, IntStream.rangeClosed(2002, 2026).toArray()),
            new Scenario(LAST_NIGHT_OF_2026, IntStream.rangeClosed(2002, 2027).toArray()))) {
      List<Integer> offered = offeredYears(scenario.now(), scenario.existing());
      // La tabla nacional del doble está completa: o se ofrece algún año o el bloqueo es
      // ALL_CREATED.
      assertThat(listed(scenario.now(), scenario.existing()).createBlocked().isPresent())
          .as("createBlocked sale si y solo si no se ofrece ningún año")
          .isEqualTo(offered.isEmpty());
      assertThat(offered).as("la lista nunca repite un año").doesNotHaveDuplicates();
      for (int year = 1999; year <= 2100; year++) {
        assertThat(offered.contains(year))
            .as(
                "año %d, ahora %s, con %s",
                year, scenario.now(), Arrays.toString(scenario.existing()))
            .isEqualTo(accepts(scenario.now(), scenario.existing(), year));
      }
    }
  }

  private List<Integer> offeredYears(Instant now, int... existing) {
    return listed(now, existing).creatableYears().stream().map(CreatableYear::year).toList();
  }

  private ListFiscalYears.Listing listed(Instant now, int... existing) {
    var clock = Clock.fixed(now, ZoneOffset.UTC);
    return new ListFiscalYears(
            portWith(existing), FakeFiscalYears.catalogWith(READ, CREATE), clock, BOGOTA)
        .list(FakeFiscalYears.user());
  }

  private boolean accepts(Instant now, int[] existing, int year) {
    var clock = Clock.fixed(now, ZoneOffset.UTC);
    try {
      new CreateFiscalYear(portWith(existing), event -> {}, clock, BOGOTA)
          .create(FakeFiscalYears.user(), year);
      return true;
    } catch (RuleViolation rejected) {
      return false;
    }
  }

  private static FakeFiscalYears portWith(int... existing) {
    FakeFiscalYears port = new FakeFiscalYears();
    for (int year : existing) {
      port.given(ADMINISTRATION, year, FiscalYearStatus.PLANEADA);
    }
    return port;
  }
}
