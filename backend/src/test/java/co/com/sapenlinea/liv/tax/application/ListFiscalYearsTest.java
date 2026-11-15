package co.com.sapenlinea.liv.tax.application;

import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.ADMINISTRATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The list: its order, what can be created, and that it never offers what the creation rejects. */
@DisplayName("Listar las vigencias fiscales (007-01, 007-03)")
class ListFiscalYearsTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
  private static final Instant LAST_NIGHT_OF_2026 = Instant.parse("2027-01-01T04:30:00Z");
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
      "007-03 · Sin vigencias, ofrece el año en curso y el siguiente con sus fechas y relación")
  void emptyListOffersTheCreatableYears() {
    var result = list(LAST_NIGHT_OF_2026, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.items()).isEmpty();
    assertThat(result.canCreate()).isTrue();
    assertThat(result.creatableYears())
        .extracting(CreatableYear::year, CreatableYear::relation)
        .containsExactly(tuple(2026, YearRelation.CURRENT), tuple(2027, YearRelation.NEXT));
    assertThat(result.creatableYears().getFirst().startsOn()).isEqualTo(LocalDate.of(2026, 1, 1));
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
  @DisplayName(
      "V-05 · Con permiso y sin años disponibles, createBlocked trae FUTURE_LIMIT y el último año")
  void createBlocked() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    fiscalYears.given(ADMINISTRATION, 2027, FiscalYearStatus.PLANEADA);

    var result = list(LAST_NIGHT_OF_2026, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.creatableYears()).isEmpty();
    assertThat(result.createBlocked())
        .contains(new CreateBlocked(CreateBlocked.Reason.FUTURE_LIMIT, 2027));
  }

  @Test
  @Tag("V-02")
  @DisplayName("V-02 · Un año pasado pendiente se ofrece con la relación PAST")
  void pastYearIsOffered() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    // It is 2028 and nobody created 2027.
    Instant inTheMiddleOf2028 = Instant.parse("2028-06-15T12:00:00Z");

    var result = list(inTheMiddleOf2028, READ, CREATE).list(FakeFiscalYears.user());

    assertThat(result.creatableYears())
        .extracting(CreatableYear::year, CreatableYear::relation)
        .containsExactly(tuple(2027, YearRelation.PAST));
  }

  @Test
  @Tag("007-08")
  @DisplayName(
      "007-08 · La lista nunca ofrece un año que la creación rechace, en la frontera del año")
  void neverOffersWhatTheCreationRejects() {
    Clock clock = Clock.fixed(LAST_NIGHT_OF_2026, ZoneOffset.UTC);
    var create = new CreateFiscalYear(fiscalYears, event -> {}, clock, BOGOTA);
    var offered =
        list(LAST_NIGHT_OF_2026, READ, CREATE).list(FakeFiscalYears.user()).creatableYears();

    assertThat(offered).hasSize(2);
    for (int year = 1999; year <= 2100; year++) {
      boolean accepted = year == 2026 || year == 2027;
      int candidate = year;
      boolean inTheList = offered.stream().anyMatch(item -> item.year() == candidate);
      assertThat(inTheList).as("año %d", year).isEqualTo(accepted);
      if (!accepted) {
        assertThatThrownBy(() -> create.create(FakeFiscalYears.user(), candidate))
            .isInstanceOf(RuleViolation.class);
      }
    }
  }
}
