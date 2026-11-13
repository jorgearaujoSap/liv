package co.com.sapenlinea.liv.tax.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    RuleViolation violation = catchThrowableOfType(RuleViolation.class, () -> policy.check(year));
    assertThat(violation).as("el año %d debe rechazarse", year).isNotNull();
    return violation;
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
  @DisplayName(
      "V-02 · 007-07 · Con vigencias, un año anterior a la más antigua es HISTORICAL aunque no sea el en curso")
  void historicalDoesNotDependOnTheCurrentYear() {
    // 2020 no es el año en curso ni el siguiente, pero la administración ya tiene vigencias.
    RuleViolation violation = refusal(policy(2026, 2026), 2020);

    assertThat(violation.details()).containsEntry("reason", "HISTORICAL");
  }

  @Test
  @Tag("V-02")
  @Tag("V-05")
  @Tag("007-07")
  @DisplayName("V-02 · V-05 · 007-07 · Si fallan HISTORICAL y FUTURE_LIMIT, gana HISTORICAL")
  void historicalComesBeforeFutureLimit() {
    // 2028 es anterior a la más antigua (2029) y 2029 ya pasa del límite (2027).
    RuleViolation violation = refusal(policy(2026, 2029), 2028);

    assertRefusal(
        violation,
        "V-02",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "HISTORICAL", "oldestYear", 2029));
  }

  @Test
  @Tag("V-02")
  @Tag("V-05")
  @Tag("007-07")
  @Tag("007-08")
  @DisplayName(
      "V-02 · V-05 · 007-07 · 007-08 · Quien crea como primera vigencia el año siguiente ya no puede crear el en curso")
  void firstYearNextBlocksTheCurrentOne() {
    FiscalYearOpeningPolicy policy = policy(2026, 2027);

    assertRefusal(
        refusal(policy, 2026),
        "V-02",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "HISTORICAL", "oldestYear", 2027));
    assertThat(policy.creatableYears()).isEmpty();
    assertThat(policy.createBlocked())
        .contains(new CreateBlocked(CreateBlocked.Reason.FUTURE_LIMIT, 2027));
  }

  // ---- V-05 FIRST_YEAR

  @ParameterizedTest(name = "año {0}")
  @Tag("V-05")
  @Tag("007-08")
  @ValueSource(ints = {2025, 2028})
  @DisplayName(
      "V-05 · 007-08 · Sin vigencias, un año que no es el en curso ni el siguiente es FIRST_YEAR")
  void firstYearMustBeCurrentOrNext(int year) {
    assertRefusal(
        refusal(policy(2026), year),
        "V-05",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "FIRST_YEAR", "currentYear", 2026, "nextYear", 2027));
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
  @DisplayName("Un año fuera de la tabla nacional no es una regla: es una invariante técnica")
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
        .containsExactly(tuple(2027, YearRelation.NEXT));
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
  @Tag("V-02")
  @Tag("V-05")
  @Tag("007-06")
  @Tag("007-08")
  @DisplayName("V-02 · V-05 · 007-06 · 007-08 · Los años que se ofrecen en cada situación")
  void creatableYearsInEachSituation() {
    assertThat(years(policy(2026))).as("sin vigencias").containsExactly(2026, 2027);
    assertThat(years(policy(2027))).as("sin vigencias, en 2027").containsExactly(2027, 2028);
    assertThat(years(policy(2026, 2026)))
        .as("la más reciente es la en curso")
        .containsExactly(2027);
    assertThat(years(policy(2026, 2027)))
        .as("la más antigua es la siguiente: 2026 es histórico y 2028 pasa del límite")
        .isEmpty();
    assertThat(years(policy(2028, 2026))).as("2027 sigue pendiente").containsExactly(2027);
    assertThat(years(policy(2027, 2026, 2027)))
        .as("en 2027, el siguiente es 2028")
        .containsExactly(2028);
    assertThat(years(policy(2026, 2026, 2027))).as("ya existe el límite").isEmpty();
    assertThat(years(policy(2026, 2025, 2026, 2027))).as("ya existe el límite").isEmpty();
  }

  private static List<Integer> years(FiscalYearOpeningPolicy policy) {
    return policy.creatableYears().stream().map(CreatableYear::year).toList();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · Un año dentro de un hueco entre vigencias existentes se admite (no salta ninguno)")
  void yearInsideAnInteriorGapIsAccepted() {
    FiscalYearOpeningPolicy policy = policy(2028, 2026, 2028);

    assertThatCode(() -> policy.check(2027)).doesNotThrowAnyException();
    assertThat(years(policy)).containsExactly(2027);
  }

  @Test
  @Tag("V-01")
  @Tag("007-04")
  @DisplayName("V-01 · 007-04 · Las fechas de cada año salen de la tabla nacional, no del código")
  void datesComeFromTheNationalTable() {
    LocalDate startsOn = LocalDate.of(2026, 4, 1);
    LocalDate endsOn = LocalDate.of(2027, 3, 31);
    FiscalYearOpeningPolicy policy =
        new FiscalYearOpeningPolicy(
            2026, List.of(), List.of(new NationalFiscalYear(2026, startsOn, endsOn)));

    assertThat(policy.creatableYears())
        .containsExactly(new CreatableYear(2026, startsOn, endsOn, YearRelation.CURRENT));
  }

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName("V-05 · 007-08 · creatableYears se queda dentro de la tabla nacional")
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
  @Tag("007-06")
  @DisplayName(
      "V-05 · 007-06 · createBlocked no sale si lo único que falta es el año en la tabla nacional")
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
  @DisplayName("La política no cambia si cambian las listas con las que se creó")
  void policyCopiesItsInputs() {
    List<Integer> years = new ArrayList<>(List.of(2026));
    FiscalYearOpeningPolicy policy = new FiscalYearOpeningPolicy(2026, years, NATIONAL_TABLE);

    years.add(2027);

    assertThat(policy.creatableYears()).extracting(CreatableYear::year).containsExactly(2027);
  }

  @Test
  @DisplayName("Una tabla nacional con el mismo año dos veces o con fechas invertidas se rechaza")
  void rejectsAnInconsistentNationalTable() {
    NationalFiscalYear first =
        new NationalFiscalYear(2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    NationalFiscalYear again =
        new NationalFiscalYear(2026, LocalDate.of(2026, 1, 2), LocalDate.of(2026, 12, 31));

    assertThatThrownBy(() -> new FiscalYearOpeningPolicy(2026, List.of(), List.of(first, again)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2026");
    assertThatThrownBy(() ->
            new NationalFiscalYear(2026, LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Sin años de la administración o sin tabla nacional no hay política")
  void rejectsMissingInputs() {
    assertThatThrownBy(() -> new FiscalYearOpeningPolicy(2026, null, NATIONAL_TABLE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new FiscalYearOpeningPolicy(2026, List.of(), null))
        .isInstanceOf(NullPointerException.class);
    List<Integer> withNull = new ArrayList<>();
    withNull.add(null);
    assertThatThrownBy(() -> new FiscalYearOpeningPolicy(2026, withNull, NATIONAL_TABLE))
        .isInstanceOf(NullPointerException.class);
  }
}
