package co.com.sapenlinea.liv.tax.application;

import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.ADMINISTRATION;
import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.shared.RuleViolation;
import co.com.sapenlinea.liv.tax.FiscalYearStatusChanged;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Abrir la preparación de una vigencia (007-10)")
class OpenFiscalYearPreparationTest {

  private static final Instant NOW = Instant.parse("2026-03-10T15:00:00Z");

  private final FakeFiscalYears fiscalYears = new FakeFiscalYears();
  private final List<Object> events = new ArrayList<>();
  private final OpenFiscalYearPreparation useCase =
      new OpenFiscalYearPreparation(fiscalYears, events::add, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Una vigencia PLANEADA pasa a EN_PREPARACION y publica FiscalYearStatusChanged")
  void opensThePreparation() {
    var planned = fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);

    var opened = useCase.open(FakeFiscalYears.user(), 2026);

    assertThat(opened.fiscalYear().status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertThat(opened.fiscalYear().assessmentsEnabled()).isFalse();
    assertThat(events).hasSize(1);
    FiscalYearStatusChanged event = (FiscalYearStatusChanged) events.getFirst();
    assertThat(event.eventId()).isNotNull();
    assertThat(event.administrationId()).isEqualTo(ADMINISTRATION);
    assertThat(event.userId()).isEqualTo(USER);
    assertThat(event.id()).isEqualTo(planned.id());
    assertThat(event.year()).isEqualTo(2026);
    assertThat(event.previousStatus()).isEqualTo("PLANEADA");
    assertThat(event.newStatus()).isEqualTo("EN_PREPARACION");
    assertThat(event.occurredAt()).isEqualTo(NOW);
  }

  @Test
  @Tag("007-10")
  @DisplayName(
      "007-10 · Desde cualquier otro estado da V-06 con el año y el estado actual, sin publicar")
  void rejectedFromAnyOtherState() {
    for (FiscalYearStatus status : FiscalYearStatus.values()) {
      if (status == FiscalYearStatus.PLANEADA) {
        continue;
      }
      int year = 2000 + status.ordinal();
      fiscalYears.given(ADMINISTRATION, year, status);

      RuleViolation violation =
          catchThrowableOfType(
              RuleViolation.class, () -> useCase.open(FakeFiscalYears.user(), year));

      assertThat(violation).as(status.name()).isNotNull();
      assertThat(violation.code()).isEqualTo("V-06");
      assertThat(violation.type()).isEqualTo(RuleViolation.Type.CONFLICT);
      assertThat(violation.details())
          .containsExactlyInAnyOrderEntriesOf(Map.of("year", year, "currentStatus", status.name()));
    }
    assertThat(events).isEmpty();
  }

  @Test
  @Tag("CA-12")
  @DisplayName(
      "CA-12 · Una vigencia que no existe en la administración da NotFound, también si es de otra")
  void notFound() {
    fiscalYears.given(FakeFiscalYears.OTHER_ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);

    assertThatThrownBy(() -> useCase.open(FakeFiscalYears.user(), 2026))
        .isInstanceOf(NotFound.class);
    assertThatThrownBy(() -> useCase.open(FakeFiscalYears.user(), 2031))
        .isInstanceOf(NotFound.class);
    assertThat(events).isEmpty();
  }
}
