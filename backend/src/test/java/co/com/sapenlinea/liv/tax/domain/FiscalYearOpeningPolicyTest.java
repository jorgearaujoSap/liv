package co.com.sapenlinea.liv.tax.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The rules for creating a fiscal year, in the order of the plan (Doc. 01 §5): the first one that
 * fails is the one answered. The national table is a test input, like the migration fills it.
 */
@DisplayName("Reglas para crear una vigencia fiscal (V-01, V-02 y V-05)")
class FiscalYearOpeningPolicyTest {

  private static final List<NationalFiscalYear> NATIONAL_TABLE =
      IntStream.rangeClosed(1999, 2100)
          .mapToObj(year ->
              new NationalFiscalYear(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
          .toList();

  private static FiscalYearOpeningPolicy policy(int currentYear, Integer... administrationYears) {
    return new FiscalYearOpeningPolicy(currentYear, List.of(administrationYears), NATIONAL_TABLE);
  }

  private static RuleViolation refusal(FiscalYearOpeningPolicy policy, int year) {
    return (RuleViolation)
        assertThatThrownBy(() -> policy.check(year)).isInstanceOf(RuleViolation.class).actual();
  }

  private static void assertRefusal(
      RuleViolation violation,
      String code,
      RuleViolation.Type type,
      Map<String, Object> expectedDetails) {
    assertThat(violation.code()).isEqualTo(code);
    assertThat(violation.type()).isEqualTo(type);
    assertThat(violation.details()).containsExactlyInAnyOrderEntriesOf(expectedDetails);
  }

  // ---- V-01

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName("V-01 · 007-05 · Un año que la administración ya tiene se rechaza con V-01 y el año")
  void existingYearIsRejected() {
    RuleViolation violation = refusal(policy(2026, 2026, 2027), 2026);

    assertRefusal(violation, "V-01", RuleViolation.Type.CONFLICT, Map.of("year", 2026));
  }

  @Test
  @Tag("V-01")
  @Tag("007-05")
  @DisplayName("V-01 · 007-05 · V-01 es la primera regla: gana aunque otra también falle")
  void existingYearBeatsEveryOtherRule() {
    // 2030 pasa del límite de años futuros (2027), pero ya existe.
    RuleViolation violation = refusal(policy(2026, 2030), 2030);

    assertRefusal(violation, "V-01", RuleViolation.Type.CONFLICT, Map.of("year", 2030));
  }

  // ---- V-02 HISTORICAL

  @Test
  @Tag("V-02")
  @Tag("007-07")
  @DisplayName("V-02 · 007-07 · Un año anterior a la vigencia más antigua es HISTORICAL")
  void yearBeforeTheOldestIsHistorical() {
    RuleViolation violation = refusal(policy(2026, 2026, 2027), 2025);

    assertRefusal(
        violation,
        "V-02",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "HISTORICAL", "oldestYear", 2026));
  }

  @Test
  @Tag("V-02")
  @Tag("007-07")
  @DisplayName("V-02 · 007-07 · HISTORICAL va antes que V-05 FIRST_YEAR y V-02 GAP")
  void historicalComesBeforeTheLaterRules() {
    // 2020 no es el año en curso ni el siguiente, pero la administración ya tiene vigencias.
    RuleViolation violation = refusal(policy(2026, 2026), 2020);

    assertThat(violation.details()).containsEntry("reason", "HISTORICAL");
  }

  // ---- V-05 FIRST_YEAR

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName(
      "V-05 · 007-08 · Sin vigencias, un año que no es el en curso ni el siguiente es FIRST_YEAR")
  void firstYearMustBeCurrentOrNext() {
    FiscalYearOpeningPolicy policy = policy(2026);

    for (int year : new int[] {2025, 2028}) {
      assertRefusal(
          refusal(policy, year),
          "V-05",
          RuleViolation.Type.UNPROCESSABLE,
          Map.of("reason", "FIRST_YEAR", "currentYear", 2026, "nextYear", 2027));
    }
  }

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName("V-05 · 007-08 · Sin vigencias se admiten el año en curso y el siguiente")
  void firstYearAcceptsCurrentAndNext() {
    FiscalYearOpeningPolicy policy = policy(2026);

    assertThatCode(() -> policy.check(2026)).doesNotThrowAnyException();
    assertThatCode(() -> policy.check(2027)).doesNotThrowAnyException();
  }

  // ---- V-02 GAP

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · Saltar un año es GAP y dice cuál se debe crear primero")
  void skippingAYearIsAGap() {
    RuleViolation violation = refusal(policy(2026, 2026), 2029);

    assertRefusal(
        violation,
        "V-02",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "GAP", "requiredYear", 2027));
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · El hueco se mide desde la vigencia más reciente, no desde la primera")
  void gapIsMeasuredFromTheLatestYear() {
    RuleViolation violation = refusal(policy(2026, 2025, 2026, 2027), 2029);

    assertThat(violation.details())
        .containsEntry("reason", "GAP")
        .containsEntry("requiredYear", 2028);
  }

  // ---- V-05 FUTURE_LIMIT

  @Test
  @Tag("V-05")
  @Tag("007-06")
  @DisplayName(
      "V-05 · 007-06 · Pasar del año siguiente al en curso es FUTURE_LIMIT con el último año")
  void beyondTheNextYearIsFutureLimit() {
    RuleViolation violation = refusal(policy(2026, 2026, 2027), 2028);

    assertRefusal(
        violation,
        "V-05",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "FUTURE_LIMIT", "maxYear", 2027));
  }

  @Test
  @Tag("V-05")
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · V-05 · 007-06 · Si fallan las dos, GAP gana a FUTURE_LIMIT")
  void gapComesBeforeFutureLimit() {
    // 2029 pasa del límite (2027) y además deja huecos: se responde el hueco.
    RuleViolation violation = refusal(policy(2026, 2026), 2029);

    assertThat(violation.code()).isEqualTo("V-02");
    assertThat(violation.details()).containsEntry("reason", "GAP");
  }

  @Test
  @Tag("V-05")
  @Tag("007-06")
  @DisplayName("V-05 · 007-06 · El límite sigue al año en curso y no a la vigencia más reciente")
  void futureLimitFollowsTheCurrentYear() {
    // La más reciente es 2028 y estamos en 2027: el siguiente (2029) pasa del límite 2028.
    RuleViolation violation = refusal(policy(2027, 2027, 2028), 2029);

    assertThat(violation.details())
        .containsEntry("reason", "FUTURE_LIMIT")
        .containsEntry("maxYear", 2028);
  }

  // ---- Años que sí se pueden crear

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · El año siguiente a la más reciente se admite si no pasa del límite")
  void yearAfterTheLatestIsAccepted() {
    assertThatCode(() -> policy(2026, 2026).check(2027)).doesNotThrowAnyException();
    assertThatCode(() -> policy(2027, 2026, 2027).check(2028)).doesNotThrowAnyException();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · Un año pasado que sigue pendiente se admite (2027 estando en 2028)")
  void pendingPastYearIsAccepted() {
    assertThatCode(() -> policy(2028, 2026).check(2027)).doesNotThrowAnyException();
  }

  @Test
  @Tag("V-01")
  @DisplayName(
      "V-01 · Un año fuera de la tabla nacional no es una regla: es una invariante técnica")
  void yearOutsideTheNationalTableIsATechnicalError() {
    FiscalYearOpeningPolicy policy = policy(2026);

    assertThatThrownBy(() -> policy.check(1998)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.check(2101)).isInstanceOf(IllegalArgumentException.class);
  }

  // ---- creatableYears y createBlocked

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName(
      "V-05 · 007-08 · Sin vigencias se pueden crear el año en curso y el siguiente, con sus fechas")
  void firstYearsAreCurrentAndNext() {
    List<CreatableYear> years = policy(2026).creatableYears();

    assertThat(years)
        .containsExactly(
            new CreatableYear(
                2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), YearRelation.CURRENT),
            new CreatableYear(
                2027, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), YearRelation.NEXT));
    assertThat(policy(2026).createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · Con la vigencia en curso, solo se puede crear la siguiente (NEXT)")
  void onlyTheNextYearAfterTheCurrentOne() {
    assertThat(policy(2026, 2026).creatableYears())
        .extracting(CreatableYear::year, CreatableYear::relation)
        .containsExactly(org.assertj.core.api.Assertions.tuple(2027, YearRelation.NEXT));
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · Un año pasado pendiente se ofrece como PAST (la más reciente es 2026, estamos en 2028)")
  void pendingPastYearIsOffered() {
    FiscalYearOpeningPolicy policy = policy(2028, 2026);

    assertThat(policy.creatableYears())
        .containsExactly(
            new CreatableYear(
                2027, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), YearRelation.PAST));
    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-06")
  @DisplayName(
      "V-05 · 007-06 · Cuando no queda ningún año, createBlocked dice FUTURE_LIMIT y el último año")
  void nothingLeftIsBlockedByTheFutureLimit() {
    FiscalYearOpeningPolicy policy = policy(2026, 2026, 2027);

    assertThat(policy.creatableYears()).isEmpty();
    assertThat(policy.createBlocked())
        .contains(new CreateBlocked(CreateBlocked.Reason.FUTURE_LIMIT, 2027));
  }

  @Test
  @Tag("V-05")
  @Tag("007-06")
  @DisplayName("V-05 · 007-06 · Nada de lo que se ofrece falla en check y nada de lo demás pasa")
  void creatableYearsAreExactlyTheYearsThatCheckAccepts() {
    List<FiscalYearOpeningPolicy> policies =
        List.of(
            policy(2026),
            policy(2026, 2026),
            policy(2026, 2027),
            policy(2028, 2026),
            policy(2027, 2026, 2027, 2028),
            policy(2026, 2026, 2027));
    for (FiscalYearOpeningPolicy policy : policies) {
      List<Integer> accepted =
          NATIONAL_TABLE.stream()
              .map(NationalFiscalYear::year)
              .filter(year -> accepts(policy, year))
              .toList();

      assertThat(policy.creatableYears()).extracting(CreatableYear::year).isEqualTo(accepted);
    }
  }

  private static boolean accepts(FiscalYearOpeningPolicy policy, int year) {
    try {
      policy.check(year);
      return true;
    } catch (RuleViolation violation) {
      return false;
    }
  }

  @Test
  @Tag("V-05")
  @DisplayName("V-05 · creatableYears se queda dentro de la tabla nacional")
  void creatableYearsStayInsideTheNationalTable() {
    FiscalYearOpeningPolicy policy =
        new FiscalYearOpeningPolicy(
            2026,
            List.of(),
            List.of(
                new NationalFiscalYear(
                    2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));

    assertThat(policy.creatableYears()).extracting(CreatableYear::year).containsExactly(2026);
  }

  @Test
  @Tag("V-05")
  @DisplayName("V-05 · createBlocked no sale si faltar el año en la tabla es la única razón")
  void notBlockedWhenOnlyTheTableIsShort() {
    FiscalYearOpeningPolicy policy =
        new FiscalYearOpeningPolicy(
            2026,
            List.of(2026),
            List.of(
                new NationalFiscalYear(
                    2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))));

    assertThat(policy.creatableYears()).isEmpty();
    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · La política no cambia si cambian las listas con las que se creó")
  void policyCopiesItsInputs() {
    List<Integer> years = new java.util.ArrayList<>(List.of(2026));
    FiscalYearOpeningPolicy policy = new FiscalYearOpeningPolicy(2026, years, NATIONAL_TABLE);

    years.add(2027);

    assertThat(policy.creatableYears()).extracting(CreatableYear::year).containsExactly(2027);
  }
}
