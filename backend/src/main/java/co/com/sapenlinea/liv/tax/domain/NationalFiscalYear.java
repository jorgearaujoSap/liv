package co.com.sapenlinea.liv.tax.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A year of the national table ({@code tax.fiscal_year}): the years that exist and their dates. The
 * dates come from the table, not from the code (V-01).
 *
 * @param year the year
 * @param startsOn first day of the year
 * @param endsOn last day of the year
 */
public record NationalFiscalYear(int year, LocalDate startsOn, LocalDate endsOn) {

  public NationalFiscalYear {
    Objects.requireNonNull(startsOn, "startsOn");
    Objects.requireNonNull(endsOn, "endsOn");
    if (endsOn.isBefore(startsOn)) {
      throw new IllegalArgumentException("The year " + year + " ends before it starts");
    }
  }
}
