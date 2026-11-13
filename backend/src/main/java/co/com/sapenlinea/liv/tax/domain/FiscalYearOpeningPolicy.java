package co.com.sapenlinea.liv.tax.domain;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decides which fiscal years an administration can create (Doc. 01 §5: V-01, V-02 and V-05). It
 * knows the years the administration already has, the current year (of the official hour of
 * Colombia, which the caller works out with the injected clock) and the national table.
 *
 * <p>The rules apply in this order and the first one that fails is the one answered, so each case
 * gives one reason with its extensions:
 *
 * <ol>
 *   <li>V-01: the year already exists.
 *   <li>V-02 {@code HISTORICAL}: the year is before the oldest one.
 *   <li>V-05 {@code FIRST_YEAR}: with no years, only the current year or the next one.
 *   <li>V-02 {@code GAP}: the year skips one after the latest.
 *   <li>V-05 {@code FUTURE_LIMIT}: the year is after the one following the current year.
 * </ol>
 *
 * <p>A year inside a gap between two existing years skips nothing, so it is accepted. The national
 * table is expected to hold the current year and the next one: when it does not, nothing is offered
 * and {@link #createBlocked()} stays empty (a technical fault, not a rule).
 *
 * <p>Immutable: it copies what it receives.
 */
public final class FiscalYearOpeningPolicy {

  private static final String REASON = "reason";

  /** The rule a year breaks, before it becomes an exception: evaluating a year does not throw. */
  private record Refusal(String code, RuleViolation.Type type, Map<String, Object> details) {

    RuleViolation toViolation() {
      return new RuleViolation(code, type, details);
    }
  }

  private final int currentYear;
  private final NavigableSet<Integer> administrationYears;
  private final Map<Integer, NationalFiscalYear> nationalYears;

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
    this.nationalYears = Collections.unmodifiableMap(table);
  }

  /**
   * Checks that the year can be created.
   *
   * @throws RuleViolation the first rule the year breaks: V-01 (conflict), or V-02 and V-05
   *     (unprocessable) with their reason and extensions
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
   * The years that {@link #check} accepts, oldest first, with their dates from the national table.
   * Normally from zero to two. Always inside the national table.
   */
  public List<CreatableYear> creatableYears() {
    return nationalYears.values().stream()
        .filter(national -> refusalOf(national.year()).isEmpty())
        .map(national ->
            new CreatableYear(
                national.year(), national.startsOn(), national.endsOn(), relationOf(national)))
        .toList();
  }

  /**
   * Why nothing can be created, when no year is offered and the administration already has the last
   * year that can be created. Empty while some year is offered, and also when the national table is
   * the only thing missing.
   */
  public Optional<CreateBlocked> createBlocked() {
    boolean reachedTheLimit =
        !administrationYears.isEmpty() && administrationYears.last() >= maxYear();
    if (reachedTheLimit && creatableYears().isEmpty()) {
      return Optional.of(new CreateBlocked(CreateBlocked.Reason.FUTURE_LIMIT, maxYear()));
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
    if (administrationYears.isEmpty()) {
      return year == currentYear || year == maxYear()
          ? Optional.empty()
          : unprocessable(
              "V-05",
              Map.of(REASON, "FIRST_YEAR", "currentYear", currentYear, "nextYear", maxYear()));
    }
    int oldest = administrationYears.first();
    if (year < oldest) {
      return unprocessable("V-02", Map.of(REASON, "HISTORICAL", "oldestYear", oldest));
    }
    int required = administrationYears.last() + 1;
    if (year > required) {
      return unprocessable("V-02", Map.of(REASON, "GAP", "requiredYear", required));
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
