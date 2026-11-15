package co.com.sapenlinea.liv.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * The real configuration: the zone of the business comes from {@code liv.business-zone} and the
 * clock of the application stays in UTC, so the year of the business is not the year of UTC (ADR
 * 0035). It is the same pair the use cases receive.
 */
@DisplayName("El año en curso de las vigencias, en la zona de negocio (ADR 0035)")
class BusinessYearTest extends PlatformWebTest {

  @Autowired private Clock clock;

  @Value("${liv.business-zone}")
  private ZoneId zone;

  @Test
  @Tag("007-08")
  @DisplayName(
      "007-08 · La zona configurada es America/Bogota y el reloj de la aplicación sigue en UTC")
  void theConfiguredZoneIsBogota() {
    assertThat(zone).isEqualTo(ZoneId.of("America/Bogota"));
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  @Tag("007-08")
  @DisplayName(
      "007-08 · Con la configuración real, a las 23:30 del 31 de diciembre sigue siendo 2026")
  void lastNightOf2026IsStill2026() {
    Instant lastNight = Instant.parse("2027-01-01T04:30:00Z");
    BusinessYear businessYear = new BusinessYear(Clock.fixed(lastNight, ZoneOffset.UTC), zone);

    assertThat(businessYear.current()).isEqualTo(2026);
    assertThat(businessYear.yearAt(lastNight.plusSeconds(30 * 60L))).isEqualTo(2027);
  }
}
