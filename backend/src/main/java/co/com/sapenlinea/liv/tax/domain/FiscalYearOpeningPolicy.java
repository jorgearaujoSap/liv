package co.com.sapenlinea.liv.tax.domain;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Decides which fiscal years an administration can create (Doc. 01 v0.6 §5: V-01 and V-05; V-02 no
 * longer rejects). It knows the years the administration already has, the current year (of the
 * official hour of Colombia, which the caller works out with the injected clock) and the national
 * table.
 *
 * <p>Any year from {@link #FIRST_CREATABLE_YEAR} to the one after the current year can be created,
 * in any order. The rules apply in this order and the first one that fails is the one answered, so
 * each case gives one reason with its extensions:
 *
 * <ol>
 *   <li>V-01: the year already exists.
 *   <li>V-05 {@code PAST_LIMIT}: the year is before {@link #FIRST_CREATABLE_YEAR}.
 *   <li>V-05 {@code FUTURE_LIMIT}: the year is after the one following the current year.
 * </ol>
 *
 * <p>The national table is expected to hold every year of that range: when it does not, the missing
 * year is not offered and {@link #createBlocked()} stays empty (a technical fault, not a rule).
 *
 * <p>Immutable: it copies what it receives.
 */
public final class FiscalYearOpeningPolicy {

  /**
   * The first year that can be created (V-05, Doc. 01 v0.6). It is an operational limit that the
   * functional owner decided on 2026-11-16, not a tax value, and it is a constant of the domain
   * because it is not read from the jurisdiction configuration: an explicit exception to the
   * prohibition of values in the code of {@code AGENTS.md} §4.5, approved in the H1 of amendment 4
   * of feature 007 (E4-1). Changing it needs a new version of Doc. 01 and of the contract.
   */
  public static final int FIRST_CREATABLE_YEAR = 2002;

  private static final String REASON = "reason";

  private final int currentYear;
  private final NavigableSet<Integer> administrationYears;
  private final NavigableMap<Integer, NationalFiscalYear> nationalYears;

  /** The rule a year breaks, before it becomes an exception: evaluating a year does not throw. */
  private record Refusal(String code, RuleViolation.Type type, Map<String, Object> details) {

    RuleViolation toViolation() {
      return new RuleViolation(code, type, details);
    }
  }

  /**
   * @param currentYear the current year in the official zone
   * @param administrationYears the years the administration already has
   * @param nationalYears the national table: the years that can exist, with their dates
   * @throws IllegalArgumentException if the national table repeats a year
   */
  public FiscalYearOpeningPolicy(
      int currentYear,
      Collection<Integer> administrationYears,
      Collection<NationalFiscalYear> nationalYears) {
    Objects.requireNonNull(administrationYears, "administrationYears");
    Objects.requireNonNull(nationalYears, "nationalYears");
    this.currentYear = currentYear;
    this.administrationYears =
        Collections.unmodifiableNavigableSet(new TreeSet<>(administrationYears));
    TreeMap<Integer, NationalFiscalYear> table = new TreeMap<>();
    nationalYears.forEach(national -> {
      if (table.put(national.year(), national) != null) {
        throw new IllegalArgumentException(
            "The national table repeats the year " + national.year());
      }
    });
    this.nationalYears = Collections.unmodifiableNavigableMap(table);
  }

  /**
   * Checks that the year can be created.
   *
   * @throws RuleViolation the first rule the year breaks: V-01 (conflict), or V-05 (unprocessable)
   *     with its reason and extensions
   * @throws IllegalArgumentException if the year is not in the national table: a technical
   *     invariant, not a rule, that the contract rejects before
   */
  public void check(int year) {
    if (!nationalYears.containsKey(year)) {
      throw new IllegalArgumentException("The year is not in the national table: " + year);
    }
    Optional<Refusal> refusal = refusalOf(year);
    if (refusal.isPresent()) {
      throw refusal.get().toViolation();
    }
  }

  /**
   * The years that {@link #check} accepts, the most recent first, with their dates from the
   * national table: from the year after the current one down to {@link #FIRST_CREATABLE_YEAR}, but
   * except the ones the administration already has. Always inside the national table.
   */
  public List<CreatableYear> creatableYears() {
    return nationalYears.descendingMap().values().stream()
        .filter(national -> refusalOf(national.year()).isEmpty())
        .map(national ->
            new CreatableYear(
                national.year(), national.startsOn(), national.endsOn(), relationOf(national)))
        .toList();
  }

  /**
   * Why nothing can be created: the administration already has every year of the range (V-05).
   * Empty while any year of the range is missing, and also when the national table is the only
   * thing missing.
   */
  public Optional<CreateBlocked> createBlocked() {
    boolean hasEveryYear =
        IntStream.rangeClosed(FIRST_CREATABLE_YEAR, maxYear())
            .allMatch(administrationYears::contains);
    if (hasEveryYear) {
      return Optional.of(
          new CreateBlocked(CreateBlocked.Reason.ALL_CREATED, FIRST_CREATABLE_YEAR, maxYear()));
    }
    return Optional.empty();
  }

  /** The last year that can be created: the one after the current year (V-05, H2-6). */
  private int maxYear() {
    return currentYear + 1;
  }

  private Optional<Refusal> refusalOf(int year) {
    if (administrationYears.contains(year)) {
      return conflict("V-01", Map.of("year", year));
    }
    if (year < FIRST_CREATABLE_YEAR) {
      return unprocessable("V-05", Map.of(REASON, "PAST_LIMIT", "minYear", FIRST_CREATABLE_YEAR));
    }
    if (year > maxYear()) {
      return unprocessable("V-05", Map.of(REASON, "FUTURE_LIMIT", "maxYear", maxYear()));
    }
    return Optional.empty();
  }

  private static Optional<Refusal> conflict(String code, Map<String, Object> details) {
    return Optional.of(new Refusal(code, RuleViolation.Type.CONFLICT, details));
  }

  private static Optional<Refusal> unprocessable(String code, Map<String, Object> details) {
    return Optional.of(new Refusal(code, RuleViolation.Type.UNPROCESSABLE, details));
  }

  private YearRelation relationOf(NationalFiscalYear national) {
    if (national.year() < currentYear) {
      return YearRelation.PAST;
    }
    return national.year() == currentYear ? YearRelation.CURRENT : YearRelation.NEXT;
  }
}
