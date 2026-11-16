package co.com.sapenlinea.liv.tax.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.api.contract.model.PreparationItemStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The controller turns the enumerations of the domain into the ones of the contract by their name
 * ({@code fromValue(name())}). If a state is added to one side only, that conversion would answer
 * 500 for a datum that is right: this test fails first, in the build.
 */
@DisplayName("Los estados del dominio y los del contrato son los mismos (tax.yaml)")
class FiscalYearsContractParityTest {

  private static List<String> names(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private static List<String> contractValues(Object[] values) {
    return Arrays.stream(values).map(Object::toString).toList();
  }

  @Test
  @Tag("contrato")
  @DisplayName(
      "007-10 · Los cinco estados de la vigencia del dominio son los del contrato (Doc. 01 §5)")
  void fiscalYearStatuses() {
    assertThat(contractValues(FiscalYearStatus.values()))
        .containsExactlyInAnyOrderElementsOf(
            names(co.com.sapenlinea.liv.tax.domain.FiscalYearStatus.values()));
  }

  @Test
  @Tag("contrato")
  @DisplayName("007-11 · Los estados de un punto de la lista del dominio son los del contrato")
  void preparationItemStatuses() {
    assertThat(contractValues(PreparationItemStatus.values()))
        .containsExactlyInAnyOrderElementsOf(
            names(co.com.sapenlinea.liv.tax.domain.PreparationItemStatus.values()));
  }
}
