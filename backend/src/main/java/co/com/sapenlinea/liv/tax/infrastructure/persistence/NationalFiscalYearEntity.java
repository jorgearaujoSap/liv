package co.com.sapenlinea.liv.tax.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/**
 * A year of the national table {@code tax.fiscal_year}, read only: {@code liv_app} has no privilege
 * to write it, and the migrations fill it.
 */
@Entity
@Immutable
@Table(schema = "tax", name = "fiscal_year")
class NationalFiscalYearEntity {

  @Id
  @Column(name = "year", updatable = false)
  private short year;

  @Column(name = "starts_on", nullable = false, updatable = false)
  private LocalDate startsOn;

  @Column(name = "ends_on", nullable = false, updatable = false)
  private LocalDate endsOn;

  /** For JPA. */
  protected NationalFiscalYearEntity() {}

  int year() {
    return year;
  }

  LocalDate startsOn() {
    return startsOn;
  }

  LocalDate endsOn() {
    return endsOn;
  }
}
