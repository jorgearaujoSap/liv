package co.com.sapenlinea.liv.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The real configuration: the year of the business is worked out in the zone of {@code
 * liv.business-zone}, not in UTC, which the clock of the application keeps (ADR 0035).
 */
@DisplayName("El año en curso de las vigencias, en la zona de negocio (ADR 0035)")
class BusinessYearTest extends PlatformWebTest {

  @Autowired private BusinessYear businessYear;

  @Test
  @Tag("007-08")
  @DisplayName(
      "007-08 · Usa la zona America/Bogota: a las 23:30 del 31 de diciembre sigue siendo 2026")
  void usesTheZoneOfBogota() {
    assertThat(businessYear.zone()).isEqualTo(ZoneId.of("America/Bogota"));
    Instant lastNightOf2026InBogota = Instant.parse("2027-01-01T04:30:00Z");

    assertThat(businessYear.yearAt(lastNightOf2026InBogota)).isEqualTo(2026);
    assertThat(businessYear.yearAt(lastNightOf2026InBogota.plusSeconds(30 * 60L))).isEqualTo(2027);
  }
}
