package co.com.sapenlinea.liv.tax.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules for creating a fiscal year, in the order of the plan (Doc. 01 v0.6 §5): the first one
 * that fails is the one answered. Any year from 2002 to the one after the current year is accepted
 * in any order: V-02 no longer rejects. The national table is a test input, like the migration
 * fills it.
 */
@DisplayName("Reglas para crear una vigencia fiscal (V-01 y V-05; V-02 ya no rechaza)")
class FiscalYearOpeningPolicyTest {

  private static final List<NationalFiscalYear> NATIONAL_TABLE =
      IntStream.rangeClosed(1999, 2100)
          .mapToObj(year ->
              new NationalFiscalYear(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)))
          .toList();

  private static FiscalYearOpeningPolicy policy(int currentYear, Integer... administrationYears) {
    return new FiscalYearOpeningPolicy(currentYear, List.of(administrationYears), NATIONAL_TABLE);
  }

  private static Integer[] range(int from, int to) {
    return IntStream.rangeClosed(from, to).boxed().toArray(Integer[]::new);
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

  private static List<Integer> years(FiscalYearOpeningPolicy policy) {
    return policy.creatableYears().stream().map(CreatableYear::year).toList();
  }

  private static CreatableYear creatable(int year, YearRelation relation) {
    return new CreatableYear(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), relation);
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

  @ParameterizedTest(name = "año {0}")
  @Tag("V-01")
  @Tag("007-05")
  @ValueSource(ints = {2001, 2030})
  @DisplayName(
      "V-01 · 007-05 · V-01 va antes que cualquier V-05, aunque el año esté fuera del rango")
  void existingYearBeatsEveryV05(int year) {
    // 2001 queda antes de 2002 y 2030 pasa del año siguiente al en curso, pero ya existen.
    RuleViolation violation = refusal(policy(2026, year), year);

    assertRefusal(violation, "V-01", RuleViolation.Type.CONFLICT, Map.of("year", year));
  }

  // ---- V-02 ya no rechaza: cualquier orden

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · Con {2026} y en 2026 se pueden crear 2002, 2015 y 2027, en cualquier orden")
  void anyYearOfTheRangeIsAcceptedInAnyOrder() {
    FiscalYearOpeningPolicy policy = policy(2026, 2026);

    assertThatCode(() -> policy.check(2002)).doesNotThrowAnyException();
    assertThatCode(() -> policy.check(2015)).doesNotThrowAnyException();
    assertThatCode(() -> policy.check(2027)).doesNotThrowAnyException();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · Con {2010, 2020} se aceptan un año anterior, uno intermedio y uno posterior")
  void yearsBeforeBetweenAndAfterAreAccepted() {
    FiscalYearOpeningPolicy policy = policy(2026, 2010, 2020);

    assertThatCode(() -> policy.check(2005)).doesNotThrowAnyException();
    assertThatCode(() -> policy.check(2015)).doesNotThrowAnyException();
    assertThatCode(() -> policy.check(2025)).doesNotThrowAnyException();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName("V-02 · 007-06 · {2026, 2028} es válido: saltar un año ya no es un hueco")
  void skippingAYearIsValid() {
    assertThatCode(() -> policy(2027, 2026).check(2028)).doesNotThrowAnyException();
    assertThatCode(() -> policy(2027, 2026, 2028).check(2027)).doesNotThrowAnyException();
  }

  @Test
  @Tag("V-02")
  @Tag("007-06")
  @DisplayName(
      "V-02 · 007-06 · Ningún año de 1999 a 2100 da un rechazo con V-02, GAP, HISTORICAL ni FIRST_YEAR")
  void noYearIsEverRejectedWithV02() {
    List<FiscalYearOpeningPolicy> policies =
        List.of(
            policy(2026), policy(2026, 2026), policy(2026, 2010, 2020), policy(2026, 2002, 2027));

    for (FiscalYearOpeningPolicy policy : policies) {
      for (int year = 1999; year <= 2100; year++) {
        RuleViolation violation = catchRuleViolation(policy, year);
        if (violation != null) {
          assertThat(violation.code()).as("año %d", year).isIn("V-01", "V-05");
          assertThat(violation.details().get("reason"))
              .as("año %d", year)
              .isNotIn("GAP", "HISTORICAL", "FIRST_YEAR");
        }
      }
    }
  }

  private static RuleViolation catchRuleViolation(FiscalYearOpeningPolicy policy, int year) {
    return catchThrowableOfType(RuleViolation.class, () -> policy.check(year));
  }

  // ---- V-05: de 2002 al año siguiente al año en curso

  @ParameterizedTest(name = "con vigencias: {0}")
  @Tag("V-05")
  @Tag("007-07")
  @ValueSource(booleans = {true, false})
  @DisplayName("V-05 · 007-07 · 2001 es PAST_LIMIT con minYear 2002, con vigencias y sin ellas")
  void beforeTheFirstCreatableYearIsPastLimit(boolean withYears) {
    FiscalYearOpeningPolicy policy = withYears ? policy(2026, 2026) : policy(2026);

    assertRefusal(
        refusal(policy, 2001),
        "V-05",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "PAST_LIMIT", "minYear", 2002));
  }

  @Test
  @Tag("V-05")
  @Tag("007-07")
  @DisplayName(
      "V-05 · 007-07 · El año en curso + 2 es FUTURE_LIMIT con maxYear, con vigencias y sin ellas")
  void afterTheNextYearIsFutureLimit() {
    for (FiscalYearOpeningPolicy policy : List.of(policy(2026), policy(2026, 2026, 2027))) {
      assertRefusal(
          refusal(policy, 2028),
          "V-05",
          RuleViolation.Type.UNPROCESSABLE,
          Map.of("reason", "FUTURE_LIMIT", "maxYear", 2027));
    }
  }

  @Test
  @Tag("V-05")
  @Tag("007-07")
  @DisplayName(
      "V-05 · 007-07 · El límite futuro sigue al año en curso y no a la vigencia más reciente")
  void futureLimitFollowsTheCurrentYear() {
    RuleViolation violation = refusal(policy(2027, 2027, 2028), 2029);

    assertThat(violation.details())
        .containsEntry("reason", "FUTURE_LIMIT")
        .containsEntry("maxYear", 2028);
  }

  @Test
  @Tag("V-05")
  @Tag("007-07")
  @DisplayName(
      "V-05 · 007-07 · Un año fuera de la tabla nacional es una invariante técnica, no una regla")
  void yearOutsideTheNationalTableIsATechnicalError() {
    FiscalYearOpeningPolicy policy = policy(2026);

    assertThatThrownBy(() -> policy.check(1998)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.check(2101)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Tag("V-05")
  @Tag("007-07")
  @Tag("E4-1")
  @DisplayName("V-05 · E4-1 · El primer año que se puede crear es 2002")
  void theFirstCreatableYearIs2002() {
    // Falla si alguien mueve la constante: 2002 se acepta y 2001 da PAST_LIMIT con minYear 2002.
    assertThat(FiscalYearOpeningPolicy.FIRST_CREATABLE_YEAR).isEqualTo(2002);
    assertThatCode(() -> policy(2026).check(2002)).doesNotThrowAnyException();
    assertRefusal(
        refusal(policy(2026), 2001),
        "V-05",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "PAST_LIMIT", "minYear", 2002));
  }

  // ---- creatableYears: del más reciente al más antiguo

  @Test
  @Tag("V-05")
  @Tag("007-08")
  @DisplayName(
      "V-05 · 007-08 · Sin vigencias, en 2026, se ofrecen los 26 años de 2027 a 2002, y cualquiera se acepta")
  void withoutYearsEveryYearOfTheRangeIsOffered() {
    FiscalYearOpeningPolicy policy = policy(2026);

    assertThat(years(policy))
        .containsExactlyElementsOf(
            IntStream.rangeClosed(2002, 2027).boxed().sorted(Comparator.reverseOrder()).toList());
    assertThat(policy.creatableYears()).hasSize(26);
    years(policy)
        .forEach(year -> assertThatCode(() -> policy.check(year)).doesNotThrowAnyException());
    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-03")
  @DisplayName(
      "007-03 · creatableYears va de mayor a menor, con sus fechas, y relation NEXT, CURRENT y PAST")
  void creatableYearsGoFromTheMostRecentToTheOldest() {
    List<CreatableYear> years = policy(2026, 2024).creatableYears();

    assertThat(years)
        .extracting(CreatableYear::year)
        .isSortedAccordingTo(Comparator.reverseOrder())
        .doesNotHaveDuplicates()
        .doesNotContain(2024);
    assertThat(years.subList(0, 3))
        .containsExactly(
            creatable(2027, YearRelation.NEXT),
            creatable(2026, YearRelation.CURRENT),
            creatable(2025, YearRelation.PAST));
    assertThat(years.getLast()).isEqualTo(creatable(2002, YearRelation.PAST));
    assertThat(years)
        .filteredOn(year -> year.year() < 2026)
        .allMatch(year -> year.relation() == YearRelation.PAST);
  }

  @Test
  @Tag("V-05")
  @Tag("007-03")
  @DisplayName("007-03 · Un año que falta en la tabla nacional no se ofrece")
  void aYearMissingFromTheNationalTableIsNotOffered() {
    List<NationalFiscalYear> table =
        NATIONAL_TABLE.stream().filter(national -> national.year() != 2010).toList();
    FiscalYearOpeningPolicy policy = new FiscalYearOpeningPolicy(2026, List.of(), table);

    assertThat(years(policy)).doesNotContain(2010).contains(2011, 2009);
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
  @DisplayName("V-05 · 007-08 · Los años que la administración ya tiene no se ofrecen")
  void existingYearsAreNotOffered() {
    List<Integer> years = years(policy(2026, 2026, 2027, 2002));

    assertThat(years).doesNotContain(2026, 2027, 2002).hasSize(23);
    assertThat(years.getFirst()).isEqualTo(2025);
  }

  // ---- createBlocked: ALL_CREATED

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Con 2002 a 2027 creados no se ofrece nada y el motivo es ALL_CREATED")
  void everyYearCreatedIsBlockedByAllCreated() {
    FiscalYearOpeningPolicy policy = policy(2026, range(2002, 2027));

    assertThat(policy.creatableYears()).isEmpty();
    assertThat(policy.createBlocked())
        .contains(new CreateBlocked(CreateBlocked.Reason.ALL_CREATED, 2002, 2027));
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName("V-05 · 007-23 · Si falta un solo año, no hay createBlocked y ese año se ofrece")
  void oneMissingYearIsNotBlocked() {
    Integer[] allButOne =
        IntStream.rangeClosed(2002, 2027)
            .filter(year -> year != 2015)
            .boxed()
            .toArray(Integer[]::new);
    FiscalYearOpeningPolicy policy = policy(2026, allButOne);

    assertThat(years(policy)).containsExactly(2015);
    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Si falta 2002, tampoco hay createBlocked aunque existan los demás años")
  void missingTheFirstYearIsNotBlocked() {
    assertThat(policy(2026, range(2003, 2027)).createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Si la tabla nacional no trae un año del rango (fallo técnico), no hay createBlocked")
  void notBlockedWhenOnlyTheTableIsShort() {
    List<NationalFiscalYear> table =
        NATIONAL_TABLE.stream().filter(national -> national.year() != 2010).toList();
    List<Integer> allButTheMissing =
        IntStream.rangeClosed(2002, 2027).filter(year -> year != 2010).boxed().toList();
    FiscalYearOpeningPolicy policy = new FiscalYearOpeningPolicy(2026, allButTheMissing, table);

    assertThat(policy.creatableYears()).isEmpty();
    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Si falta solo el último año (el siguiente al en curso), no hay createBlocked y se ofrece solo ese")
  void missingOnlyTheLastYearIsNotBlocked() {
    FiscalYearOpeningPolicy policy = policy(2026, range(2002, 2026));

    assertThat(years(policy)).containsExactly(2027);
    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Con todos los años creados, crear uno es V-01 si existe o V-05 si queda fuera del rango")
  void everyYearCreatedStillAnswersTheRules() {
    FiscalYearOpeningPolicy policy = policy(2026, range(2002, 2027));

    assertRefusal(refusal(policy, 2010), "V-01", RuleViolation.Type.CONFLICT, Map.of("year", 2010));
    assertRefusal(
        refusal(policy, 2028),
        "V-05",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "FUTURE_LIMIT", "maxYear", 2027));
    assertRefusal(
        refusal(policy, 2001),
        "V-05",
        RuleViolation.Type.UNPROCESSABLE,
        Map.of("reason", "PAST_LIMIT", "minYear", 2002));
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · Con un año en curso imposible (anterior a 2001) no hay createBlocked: el rango está vacío")
  void anEmptyRangeIsNotBlocked() {
    FiscalYearOpeningPolicy policy = policy(2000);

    assertThat(policy.createBlocked()).isEmpty();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName(
      "V-05 · 007-23 · La frontera: con un rango de un solo año (2002) y esa vigencia creada, ALL_CREATED")
  void aRangeOfOneYearCanBeAllCreated() {
    assertThat(policy(2001, 2002).createBlocked())
        .contains(new CreateBlocked(CreateBlocked.Reason.ALL_CREATED, 2002, 2002));
    assertThatCode(() -> new CreateBlocked(CreateBlocked.Reason.ALL_CREATED, 2027, 2027))
        .doesNotThrowAnyException();
  }

  @Test
  @Tag("V-05")
  @Tag("007-23")
  @DisplayName("V-05 · 007-23 · Un createBlocked con el rango invertido no se puede crear")
  void createBlockedNeedsAValidRange() {
    assertThatThrownBy(() -> new CreateBlocked(CreateBlocked.Reason.ALL_CREATED, 2028, 2027))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- Lo que no cambia

  @Test
  @DisplayName("La política no cambia si cambian las listas con las que se creó")
  void policyCopiesItsInputs() {
    List<Integer> years = new ArrayList<>(List.of(2026));
    FiscalYearOpeningPolicy policy = new FiscalYearOpeningPolicy(2026, years, NATIONAL_TABLE);

    years.add(2027);

    // La política copió {2026}: 2027, que se agregó después a la lista, se sigue ofreciendo.
    assertThat(years(policy)).doesNotContain(2026).contains(2027);
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
    List<NationalFiscalYear> nullYear = new ArrayList<>();
    nullYear.add(null);
    assertThatThrownBy(() -> new FiscalYearOpeningPolicy(2026, List.of(), nullYear))
        .isInstanceOf(NullPointerException.class);
  }
}
