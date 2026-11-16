package co.com.sapenlinea.liv.tax.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A fiscal year of an administration as it is stored: the row, the state and the dates of the
 * national year. It is a type of the domain so that the API can use what a use case answers without
 * depending on the port of the persistence.
 *
 * @param id the id of the row
 * @param fiscalYear the fiscal year of the administration
 * @param startsOn first day of the year, from the national table
 * @param endsOn last day of the year, from the national table
 */
public record RegisteredFiscalYear(
    UUID id, AdministrationFiscalYear fiscalYear, LocalDate startsOn, LocalDate endsOn) {

  public RegisteredFiscalYear {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(fiscalYear, "fiscalYear");
    Objects.requireNonNull(startsOn, "startsOn");
    Objects.requireNonNull(endsOn, "endsOn");
  }
}
