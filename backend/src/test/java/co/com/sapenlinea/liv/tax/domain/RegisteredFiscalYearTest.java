package co.com.sapenlinea.liv.tax.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("La vigencia tal como se guarda: ningún dato falta")
class RegisteredFiscalYearTest {

  private static final UUID ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final AdministrationFiscalYear YEAR =
      AdministrationFiscalYear.plan(UUID.fromString("10000000-0000-0000-0000-00000000000a"), 2026);
  private static final LocalDate STARTS = LocalDate.of(2026, 1, 1);
  private static final LocalDate ENDS = LocalDate.of(2026, 12, 31);

  @Test
  @DisplayName("Con todos sus datos se crea y los devuelve tal cual")
  void aCompleteOneIsCreated() {
    RegisteredFiscalYear registered = new RegisteredFiscalYear(ID, YEAR, STARTS, ENDS);

    assertThat(registered.id()).isEqualTo(ID);
    assertThat(registered.fiscalYear()).isEqualTo(YEAR);
    assertThat(registered.startsOn()).isEqualTo(STARTS);
    assertThat(registered.endsOn()).isEqualTo(ENDS);
  }

  @Test
  @DisplayName("Sin el id, la vigencia o sus fechas, no se crea")
  void aMissingDatumIsRefused() {
    assertThatThrownBy(() -> new RegisteredFiscalYear(null, YEAR, STARTS, ENDS))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RegisteredFiscalYear(ID, null, STARTS, ENDS))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RegisteredFiscalYear(ID, YEAR, null, ENDS))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RegisteredFiscalYear(ID, YEAR, STARTS, null))
        .isInstanceOf(NullPointerException.class);
  }
}
