package co.com.sapenlinea.liv.tax.domain;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
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
 * <p>Immutable: it copies what it receives.
 */
public final class FiscalYearOpeningPolicy {

  private static final String V_01 = "V-01";
  private static final String V_02 = "V-02";
  private static final String V_05 = "V-05";
  private static final String REASON = "reason";

  private final int currentYear;
  private final NavigableSet<Integer> administrationYears;
  private final Map<Integer, NationalFiscalYear> nationalYears;

  /**
   * @param currentYear the current year in the official zone
   * @param administrationYears the years the administration already has
   * @param nationalYears the national table: the years that can exist, with their dates
   */
  public FiscalYearOpeningPolicy(
      int currentYear,
      Collection<Integer> administrationYears,
      Collection<NationalFiscalYear> nationalYears) {
    this.currentYear = currentYear;
    this.administrationYears =
        Collections.unmodifiableNavigableSet(new TreeSet<>(administrationYears));
    TreeMap<Integer, NationalFiscalYear> table = new TreeMap<>();
    nationalYears.forEach(national -> table.put(national.year(), national));
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
    if (administrationYears.contains(year)) {
      throw new RuleViolation(V_01, RuleViolation.Type.CONFLICT, Map.of("year", year));
    }
    int nextYear = currentYear + 1;
    if (administrationYears.isEmpty()) {
      if (year != currentYear && year != nextYear) {
        throw unprocessable(
            V_05, Map.of(REASON, "FIRST_YEAR", "currentYear", currentYear, "nextYear", nextYear));
      }
      return;
    }
    int oldest = administrationYears.first();
    if (year < oldest) {
      throw unprocessable(V_02, Map.of(REASON, "HISTORICAL", "oldestYear", oldest));
    }
    int required = administrationYears.last() + 1;
    if (year > required) {
      throw unprocessable(V_02, Map.of(REASON, "GAP", "requiredYear", required));
    }
    if (year > nextYear) {
      throw unprocessable(V_05, Map.of(REASON, "FUTURE_LIMIT", "maxYear", nextYear));
    }
  }

  /**
   * The years that {@link #check} accepts, oldest first, with their dates from the national table.
   * Normally from zero to two. Always inside the national table.
   */
  public List<CreatableYear> creatableYears() {
    return nationalYears.values().stream()
        .filter(national -> accepts(national.year()))
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
    int maxYear = currentYear + 1;
    boolean reachedTheLimit =
        !administrationYears.isEmpty() && administrationYears.last() >= maxYear;
    if (creatableYears().isEmpty() && reachedTheLimit) {
      return Optional.of(new CreateBlocked(CreateBlocked.Reason.FUTURE_LIMIT, maxYear));
    }
    return Optional.empty();
  }

  private boolean accepts(int year) {
    try {
      check(year);
      return true;
    } catch (RuleViolation refusal) {
      return false;
    }
  }

  private YearRelation relationOf(NationalFiscalYear national) {
    if (national.year() < currentYear) {
      return YearRelation.PAST;
    }
    return national.year() == currentYear ? YearRelation.CURRENT : YearRelation.NEXT;
  }

  private static RuleViolation unprocessable(String code, Map<String, Object> details) {
    return new RuleViolation(code, RuleViolation.Type.UNPROCESSABLE, details);
  }
}
