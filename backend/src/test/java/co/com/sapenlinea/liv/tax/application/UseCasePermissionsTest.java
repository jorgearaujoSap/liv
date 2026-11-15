package co.com.sapenlinea.liv.tax.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.platform.CurrentUser;
import co.com.sapenlinea.liv.platform.RequiresPermission;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The permission of each use case is the one of the approved matrix (Doc. 01 §4: read for the five
 * roles, create and prepare only for {@code ADMIN_SISTEMA}). The use cases are not beans yet, so
 * the proxy that applies the annotation is tested with their activation; this fixes the exact value
 * of each annotation, so that swapping two permissions fails here.
 */
@DisplayName("Permiso de cada caso de uso de las vigencias (007-02, 007-09)")
class UseCasePermissionsTest {

  private static String permissionOf(Class<?> useCase, String method, Class<?>... parameters)
      throws NoSuchMethodException {
    Method found = useCase.getMethod(method, parameters);
    RequiresPermission required = found.getAnnotation(RequiresPermission.class);
    assertThat(required).as("%s.%s", useCase.getSimpleName(), method).isNotNull();
    return required.value();
  }

  @Test
  @Tag("007-02")
  @Tag("007-09")
  @DisplayName("007-02 · 007-09 · Listar y consultar piden read; crear, create; abrir, prepare")
  void eachUseCaseRequiresItsPermission() throws NoSuchMethodException {
    Map<String, String> permissions =
        Map.of(
            "ListFiscalYears.list",
            permissionOf(ListFiscalYears.class, "list", CurrentUser.class),
            "GetFiscalYear.get",
            permissionOf(GetFiscalYear.class, "get", CurrentUser.class, int.class),
            "CreateFiscalYear.create",
            permissionOf(CreateFiscalYear.class, "create", CurrentUser.class, int.class),
            "OpenFiscalYearPreparation.open",
            permissionOf(OpenFiscalYearPreparation.class, "open", CurrentUser.class, int.class));

    assertThat(permissions)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "ListFiscalYears.list", "tax.fiscal-year.read",
                "GetFiscalYear.get", "tax.fiscal-year.read",
                "CreateFiscalYear.create", "tax.fiscal-year.create",
                "OpenFiscalYearPreparation.open", "tax.fiscal-year.prepare"));
  }
}
