package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.application.PermissionProbe;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Doc. 02 §8 on the paths of T14: if the catalog of permissions cannot be read, the check fails
 * closed with 503 and SYS-503 (not 403, not 500), and the use case does not run. The rest of the
 * API, the registry included, stays up; only the catalog is replaced.
 */
@DisplayName("Catálogo de permisos caído (T14)")
class PermissionCatalogDownTest extends PlatformWebTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @MockitoBean PermissionCatalog catalog;
  @Autowired PermissionProbe probe;

  @BeforeEach
  void catalogDown() {
    DataAccessResourceFailureException down =
        new DataAccessResourceFailureException("catálogo de permisos caído");
    when(catalog.permissionsOf(any())).thenThrow(down);
    when(catalog.knownRoles(any())).thenThrow(down);
  }

  @Test
  @Tag("001-16")
  @DisplayName(
      "001-16 · Si no se pueden leer los permisos, el caso de uso no corre y la API responde 503")
  void guardedUseCase() {
    int before = probe.runs();

    assertUnavailable(get("/api/v1/probe/permission", bearer(token())));
    assertThat(probe.runs()).isEqualTo(before);
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Si no se pueden leer los permisos, el menú responde 503")
  void capabilities() {
    assertUnavailable(get("/api/v1/me/capabilities", bearer(token())));
  }

  private static String token() {
    return OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A);
  }

  private static void assertUnavailable(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    JsonNode body = JSON.readTree(response.body());
    assertThat(body.get("code").asString()).isEqualTo(ErrorCodes.SERVICE_UNAVAILABLE);
  }
}
