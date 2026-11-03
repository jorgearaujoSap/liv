package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.application.PermissionProbe;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.method.AuthorizationAdvisor;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code @RequiresPermission} (001-16): a use case refuses a user without the permission even when
 * the option is hidden in the menu, and a use case method without a mark is refused to everyone.
 * The use cases and their controller exist only in the tests ({@link PermissionProbe}, {@link
 * PlatformWebTest.ApiProbe}).
 */
@DisplayName("Autorización de los casos de uso: @RequiresPermission (T14)")
class RequiresPermissionTest extends PlatformWebTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired PermissionProbe probe;
  @Autowired List<Advisor> advisors;

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Con el permiso, el caso de uso responde 200")
  void withThePermissionTheUseCaseRuns() {
    for (List<String> roles :
        List.of(List.of("ADMIN_SISTEMA"), List.of("AUDITOR"), List.of("CONSULTA", "AUDITOR"))) {
      int before = probe.runs();

      HttpResponse<String> response = get("/api/v1/probe/permission", bearer(tokenWith(roles)));

      assertThat(response.statusCode()).as("estado para %s", roles).isEqualTo(200);
      assertThat(JSON.readTree(response.body()).get("result").asString()).isEqualTo("ejecutado");
      assertThat(probe.runs()).isEqualTo(before + 1);
    }
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Sin el permiso, 403 con el código 001-16 y el caso de uso no se ejecuta")
  void withoutThePermissionIsForbidden() {
    for (List<String> roles :
        List.of(
            List.of("CONSULTA"),
            List.<String>of(),
            // A role LIV does not know gives nothing, even if it is named like a permission.
            List.of("INVENTADO", "core.user.read", "admin_sistema"))) {
      assertForbidden("/api/v1/probe/permission", roles);
    }
  }

  @Test
  @Tag("001-16")
  @DisplayName(
      "001-16 · Un método de caso de uso sin marca se niega a todos, también si es heredado")
  void unmarkedMethodsAreDeniedByDefault() {
    for (String path : List.of("/api/v1/probe/unmarked", "/api/v1/probe/inherited")) {
      assertForbidden(path, List.of("AUDITOR", "ADMIN_SISTEMA"));
    }
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Fuera de una petición, sin usuario, el caso de uso protegido se niega")
  void withoutAUserIsDenied() {
    int before = probe.runs();

    assertThatThrownBy(probe::run).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(probe::unmarked).isInstanceOf(AccessDeniedException.class);
    assertThat(probe.runs()).isEqualTo(before);
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Un caso de uso abierto corre sin usuario, como lo llamaría un oyente")
  void openUseCaseRunsWithoutAUser() {
    int before = probe.runs();

    assertThat(probe.open()).isEqualTo("ejecutado");
    assertThat(probe.runs()).isEqualTo(before + 1);
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · El permiso se comprueba antes de abrir la transacción del caso de uso")
  void permissionIsCheckedBeforeTheTransaction() {
    int authorization = orderOf(AuthorizationAdvisor.class);
    int transaction = orderOf(BeanFactoryTransactionAttributeSourceAdvisor.class);

    assertThat(authorization).isLessThan(transaction);
  }

  private void assertForbidden(String path, List<String> roles) {
    int before = probe.runs();

    HttpResponse<String> response = get(path, bearer(tokenWith(roles)));

    assertThat(response.statusCode()).as("estado de %s para %s", path, roles).isEqualTo(403);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    JsonNode problem = JSON.readTree(response.body());
    assertThat(problem.get("code").asString()).isEqualTo("001-16");
    assertThat(problem.get("status").asInt()).isEqualTo(403);
    assertThat(probe.runs()).as("ejecuciones de %s con %s", path, roles).isEqualTo(before);
  }

  private int orderOf(Class<?> type) {
    List<Integer> orders =
        advisors.stream()
            .filter(type::isInstance)
            .map(advisor -> ((Ordered) advisor).getOrder())
            .toList();
    assertThat(orders).as("advisors de %s", type.getSimpleName()).hasSize(1);
    return orders.getFirst();
  }

  private static String tokenWith(List<String> roles) {
    return OidcTestProvider.userToken(
        ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("roles", roles));
  }
}
