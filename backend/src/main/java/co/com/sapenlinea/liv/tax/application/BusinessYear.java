package co.com.sapenlinea.liv.tax.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The current year of the business: the one of the official hour of Colombia, not the one of UTC
 * (ADR 0035). The clock of {@code platform} stays in UTC; this class combines it with the zone of
 * {@code liv.business-zone}. It is the only source of the current year of the fiscal years, so the
 * list never offers a year that the creation rejects.
 *
 * <p>Plain Java, not a bean: each use case builds its own with the clock and the zone it receives
 * (a bean of {@code application} would be a use case and need a permission).
 */
public class BusinessYear {

  private final Clock clock;
  private final ZoneId zone;

  public BusinessYear(Clock clock, ZoneId zone) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  /** The current year in the zone of the business. */
  public int current() {
    return yearAt(clock.instant());
  }

  /** The year in the zone of the business at that instant. */
  public int yearAt(Instant instant) {
    return LocalDate.ofInstant(instant, zone).getYear();
  }

  public ZoneId zone() {
    return zone;
  }
}
