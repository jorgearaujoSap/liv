package co.com.sapenlinea.liv.tax.application;

import static co.com.sapenlinea.liv.tax.application.FakeFiscalYears.ADMINISTRATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.shared.NotFound;
import co.com.sapenlinea.liv.tax.domain.FiscalYearStatus;
import co.com.sapenlinea.liv.tax.domain.PreparationItem;
import co.com.sapenlinea.liv.tax.domain.PreparationItemCheck;
import co.com.sapenlinea.liv.tax.domain.PreparationItemStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

@DisplayName("Consultar una vigencia y su lista de preparación (007-10 a 007-12)")
class GetFiscalYearTest {

  private static final String READ = "tax.fiscal-year.read";
  private static final String PREPARE = "tax.fiscal-year.prepare";

  private final FakeFiscalYears fiscalYears = new FakeFiscalYears();

  /** No implementation of the port exists in feature 007: the provider is empty. */
  private GetFiscalYear useCase(String... permissions) {
    return new GetFiscalYear(
        fiscalYears,
        FakeFiscalYears.catalogWith(permissions),
        new DefaultListableBeanFactory().getBeanProvider(PreparationItemCheck.class));
  }

  @Test
  @Tag("007-11")
  @DisplayName(
      "007-11 · En EN_PREPARACION el punto 1 está cumplido y los demás dicen que la opción no existe")
  void checklistOfAYearInPreparation() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.EN_PREPARACION);

    var detail = useCase(READ, PREPARE).get(FakeFiscalYears.user(), 2026);

    List<PreparationItem> items = detail.preparation().items();
    assertThat(items).hasSize(9);
    assertThat(items.getFirst().status()).isEqualTo(PreparationItemStatus.MET);
    assertThat(items.subList(1, 9))
        .allSatisfy(item -> {
          assertThat(item.status()).isEqualTo(PreparationItemStatus.PENDING);
          assertThat(item.reason()).isEqualTo("OPTION_NOT_AVAILABLE");
        });
  }

  @Test
  @Tag("007-11")
  @DisplayName("007-11 · En PLANEADA el punto 1 está pendiente con FISCAL_YEAR_PLANNED")
  void checklistOfAPlannedYear() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);

    var detail = useCase(READ).get(FakeFiscalYears.user(), 2026);

    assertThat(detail.preparation().items().getFirst().reason()).isEqualTo("FISCAL_YEAR_PLANNED");
  }

  @Test
  @Tag("V-03")
  @Tag("007-12")
  @DisplayName("V-03 · 007-12 · La liquidación no se puede habilitar y faltan los obligatorios")
  void cannotEnable() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.EN_PREPARACION);

    var detail = useCase(READ).get(FakeFiscalYears.user(), 2026);

    assertThat(detail.preparation().canEnable()).isFalse();
    assertThat(detail.preparation().missing()).containsExactly(2, 3, 4, 5, 8, 9);
  }

  @Test
  @Tag("007-09")
  @Tag("007-10")
  @DisplayName("007-10 · OPEN_PREPARATION solo con tax.fiscal-year.prepare y en estado PLANEADA")
  void availableActions() {
    fiscalYears.given(ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);
    fiscalYears.given(ADMINISTRATION, 2027, FiscalYearStatus.EN_PREPARACION);

    assertThat(useCase(READ, PREPARE).get(FakeFiscalYears.user(), 2026).availableActions())
        .containsExactly("OPEN_PREPARATION");
    assertThat(useCase(READ).get(FakeFiscalYears.user(), 2026).availableActions()).isEmpty();
    assertThat(useCase(READ, PREPARE).get(FakeFiscalYears.user(), 2027).availableActions())
        .isEmpty();
  }

  @Test
  @Tag("CA-12")
  @DisplayName("CA-12 · Una vigencia de otra administración o inexistente da NotFound")
  void notFound() {
    fiscalYears.given(FakeFiscalYears.OTHER_ADMINISTRATION, 2026, FiscalYearStatus.PLANEADA);

    assertThatThrownBy(() -> useCase(READ).get(FakeFiscalYears.user(), 2026))
        .isInstanceOf(NotFound.class);
  }
}
