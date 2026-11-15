package co.com.sapenlinea.liv.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The permission of each use case is the one of the approved matrix (Doc. 01 §4: read for the five
 * roles, create and prepare only for {@code ADMIN_SISTEMA}). The use cases are not beans yet, so
 * the annotation that applies it arrives with their activation (6a2), which also tests that calling
 * them without the permission fails. Until then this fixes the exact value of each constant, so
 * that swapping two permissions fails here.
 */
@DisplayName("Permiso de cada caso de uso de las vigencias (007-02, 007-09)")
class UseCasePermissionsTest {

  @Test
  @Tag("007-02")
  @Tag("007-09")
  @DisplayName("007-02 · 007-09 · Listar y consultar piden read; crear, create; abrir, prepare")
  void eachUseCaseHasItsPermission() {
    assertThat(
            Map.of(
                "ListFiscalYears", ListFiscalYears.PERMISSION,
                "GetFiscalYear", GetFiscalYear.PERMISSION,
                "CreateFiscalYear", CreateFiscalYear.PERMISSION,
                "OpenFiscalYearPreparation", OpenFiscalYearPreparation.PERMISSION))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "ListFiscalYears", "tax.fiscal-year.read",
                "GetFiscalYear", "tax.fiscal-year.read",
                "CreateFiscalYear", "tax.fiscal-year.create",
                "OpenFiscalYearPreparation", "tax.fiscal-year.prepare"));
  }

  private static String annotated(Class<?> useCase, String method, Class<?>... parameters)
      throws NoSuchMethodException {
    RequiresPermission required =
        useCase.getMethod(method, parameters).getAnnotation(RequiresPermission.class);
    assertThat(required).as("%s.%s", useCase.getSimpleName(), method).isNotNull();
    return required.value();
  }

  @Test
  @Tag("007-02")
  @Tag("007-09")
  @DisplayName(
      "007-02 · 007-09 · Cada método lleva @RequiresPermission con el permiso de su acción")
  void eachMethodRequiresItsPermission() throws NoSuchMethodException {
    assertThat(annotated(ListFiscalYears.class, "list", CurrentUser.class))
        .isEqualTo("tax.fiscal-year.read");
    assertThat(annotated(GetFiscalYear.class, "get", CurrentUser.class, int.class))
        .isEqualTo("tax.fiscal-year.read");
    assertThat(annotated(CreateFiscalYear.class, "create", CurrentUser.class, int.class))
        .isEqualTo("tax.fiscal-year.create");
    assertThat(annotated(OpenFiscalYearPreparation.class, "open", CurrentUser.class, int.class))
        .isEqualTo("tax.fiscal-year.prepare");
  }
}
