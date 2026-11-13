package co.com.sapenlinea.liv.tax.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.shared.RuleViolation;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Vigencia fiscal de una administración (Doc. 01 §5)")
class AdministrationFiscalYearTest {

  private static final UUID ADMINISTRATION =
      UUID.fromString("00000000-0000-7000-8000-000000000001");

  @Test
  @Tag("V-01")
  @DisplayName("Doc. 01 §5 · Los cinco estados y sus códigos son los del documento")
  void fiveStatesWithTheCodesOfTheDocument() {
    assertThat(Arrays.stream(FiscalYearStatus.values()).map(Enum::name))
        .containsExactly("PLANEADA", "EN_PREPARACION", "LISTA", "HABILITADA", "CERRADA");
  }

  @Test
  @Tag("V-01")
  @Tag("007-04")
  @DisplayName("V-01 · 007-04 · Una vigencia nueva nace PLANEADA y sin la liquidación habilitada")
  void newYearIsPlannedWithAssessmentsDisabled() {
    AdministrationFiscalYear fiscalYear = AdministrationFiscalYear.plan(ADMINISTRATION, 2026);

    assertThat(fiscalYear.administrationId()).isEqualTo(ADMINISTRATION);
    assertThat(fiscalYear.year()).isEqualTo(2026);
    assertThat(fiscalYear.status()).isEqualTo(FiscalYearStatus.PLANEADA);
    assertThat(fiscalYear.assessmentsEnabled()).isFalse();
  }

  @Test
  @Tag("007-10")
  @DisplayName("007-10 · Abrir la preparación de una vigencia PLANEADA da EN_PREPARACION")
  void openPreparationFromPlanned() {
    AdministrationFiscalYear planned = AdministrationFiscalYear.plan(ADMINISTRATION, 2026);

    AdministrationFiscalYear opened = planned.openPreparation();

    assertThat(opened.status()).isEqualTo(FiscalYearStatus.EN_PREPARACION);
    assertThat(opened.administrationId()).isEqualTo(ADMINISTRATION);
    assertThat(opened.year()).isEqualTo(2026);
    assertThat(planned.status()).as("el original no cambia").isEqualTo(FiscalYearStatus.PLANEADA);
  }

  @ParameterizedTest
  @Tag("V-06")
  @Tag("007-10")
  @EnumSource(
      value = FiscalYearStatus.class,
      names = {"EN_PREPARACION", "LISTA", "HABILITADA", "CERRADA"})
  @DisplayName("V-06 · 007-10 · Desde cualquier otro estado se rechaza con V-06 y el estado actual")
  void openPreparationFromAnyOtherStateIsRejected(FiscalYearStatus status) {
    AdministrationFiscalYear fiscalYear =
        AdministrationFiscalYear.restore(ADMINISTRATION, 2026, status, false);

    assertThatThrownBy(fiscalYear::openPreparation)
        .isInstanceOfSatisfying(
            RuleViolation.class,
            violation -> {
              assertThat(violation.code()).isEqualTo("V-06");
              assertThat(violation.type()).isEqualTo(RuleViolation.Type.CONFLICT);
              assertThat(violation.details())
                  .containsEntry("year", 2026)
                  .containsEntry("currentStatus", status.name())
                  .hasSize(2);
            });
    assertThat(fiscalYear.status()).as("no cambia nada").isEqualTo(status);
  }

  @Test
  @Tag("V-03")
  @Tag("007-12")
  @DisplayName("V-03 · 007-12 · assessmentsEnabled no cambia al abrir la preparación")
  void assessmentsEnabledNeverChanges() {
    AdministrationFiscalYear opened =
        AdministrationFiscalYear.plan(ADMINISTRATION, 2026).openPreparation();

    assertThat(opened.assessmentsEnabled()).isFalse();
  }

  @Test
  @Tag("V-03")
  @Tag("007-12")
  @DisplayName("V-03 · 007-12 · restore conserva el valor que trae y ninguna operación lo cambia")
  void restoreKeepsAssessmentsEnabled() {
    AdministrationFiscalYear restored =
        AdministrationFiscalYear.restore(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA, true);

    assertThat(restored.assessmentsEnabled()).isTrue();
    assertThat(restored.openPreparation().assessmentsEnabled()).isTrue();
  }

  @Test
  @Tag("V-01")
  @DisplayName("V-01 · Sin administración o sin estado no hay vigencia")
  void requiresAdministrationAndStatus() {
    assertThatThrownBy(() -> AdministrationFiscalYear.plan(null, 2026))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> AdministrationFiscalYear.restore(ADMINISTRATION, 2026, null, false))
        .isInstanceOf(NullPointerException.class);
  }
}
