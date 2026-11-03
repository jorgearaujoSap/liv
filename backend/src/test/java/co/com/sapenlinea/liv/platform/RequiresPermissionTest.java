package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code @RequiresPermission} (001-16): a use case refuses a user without the permission even when
 * the option is hidden in the menu. The use case and its controller exist only in the tests ({@link
 * PlatformWebTest.PermissionProbe}); it requires {@code core.user.read}, which ADMIN_SISTEMA and
 * AUDITOR have and CONSULTA does not (plan, «Permisos»).
 */
@DisplayName("Autorización de los casos de uso: @RequiresPermission (T14)")
class RequiresPermissionTest extends PlatformWebTest {

  private static final String GUARDED = "/api/v1/probe/permission";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired PermissionProbe probe;

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Con el permiso, el caso de uso responde 200")
  void withThePermissionTheUseCaseRuns() {
    for (String role : List.of("ADMIN_SISTEMA", "AUDITOR")) {
      int before = probe.runs();

      HttpResponse<String> response = get(GUARDED, bearer(tokenWith(List.of(role))));

      assertThat(response.statusCode()).as("estado para %s", role).isEqualTo(200);
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
      int before = probe.runs();

      HttpResponse<String> response = get(GUARDED, bearer(tokenWith(roles)));

      assertThat(response.statusCode()).as("estado para %s", roles).isEqualTo(403);
      assertThat(response.headers().firstValue("Content-Type"))
          .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
      JsonNode problem = JSON.readTree(response.body());
      assertThat(problem.get("code").asString()).isEqualTo("001-16");
      assertThat(problem.get("status").asInt()).isEqualTo(403);
      assertThat(probe.runs()).as("ejecuciones con %s", roles).isEqualTo(before);
    }
  }

  @Test
  @Tag("001-16")
  @DisplayName("001-16 · Fuera de una petición, sin usuario, el caso de uso se niega")
  void withoutAUserIsDenied() {
    int before = probe.runs();

    assertThatThrownBy(probe::run).isInstanceOf(AccessDeniedException.class);
    assertThat(probe.runs()).isEqualTo(before);
  }

  private static String tokenWith(List<String> roles) {
    return OidcTestProvider.userToken(
        ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("roles", roles));
  }
}
