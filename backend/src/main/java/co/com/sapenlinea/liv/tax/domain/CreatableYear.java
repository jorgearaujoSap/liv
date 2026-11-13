package co.com.sapenlinea.liv.tax.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A year the creation would accept now, with its dates (V-01) and its relation to the current year.
 *
 * @param year the year
 * @param startsOn first day of the year
 * @param endsOn last day of the year
 * @param relation how the year relates to the current one
 */
public record CreatableYear(int year, LocalDate startsOn, LocalDate endsOn, YearRelation relation) {

  public CreatableYear {
    Objects.requireNonNull(startsOn, "startsOn");
    Objects.requireNonNull(endsOn, "endsOn");
    Objects.requireNonNull(relation, "relation");
  }
}
