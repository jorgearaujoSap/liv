package co.com.sapenlinea.liv.core;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/me} (001-13): who the user is, from the server — the LIV registry for the user
 * and its administration, the token's roles that LIV knows, and the permissions LIV gives them —
 * and never from data the browser sends (001-17).
 */
@DisplayName("Usuario de la sesión: GET /api/v1/me (T13)")
class CurrentUserTest extends PlatformWebTest {

  private static final String ME = "/api/v1/me";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Test
  @Tag("001-13")
  @DisplayName("001-13 · Responde identificador, nombre, correo, administración, roles y permisos")
  void currentUserFromTheServer() {
    HttpResponse<String> response =
        get(ME, bearer(OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A)));

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode me = JSON.readTree(response.body());
    Object[] registered = registered(ACTIVE);
    assertThat(me.get("id").asString()).isEqualTo(registered[0].toString());
    // The registry, not the token: the token says "Usuario de prueba api-activo".
    assertThat(me.get("fullName").asString()).isEqualTo(registered[1]);
    assertThat(me.get("email").asString()).isEqualTo(registered[2]);
    assertThat(me.get("administration").get("id").asString())
        .isEqualTo(LivDatabase.ADMIN_A.toString());
    assertThat(me.get("administration").get("name").asString()).isEqualTo("Admin A");
    assertThat(texts(me.get("roles"))).containsExactly("CONSULTA");
    assertThat(texts(me.get("permissions")))
        .containsExactlyInAnyOrderElementsOf(permissionsOf("CONSULTA"))
        .isNotEmpty();
  }

  @Test
  @Tag("001-13")
  @DisplayName("001-13 · Solo cuentan los roles del catálogo de LIV y sus permisos se unen")
  void onlyRolesLivKnows() {
    String token =
        OidcTestProvider.userToken(
            ACTIVE,
            LivDatabase.ADMIN_A,
            claims ->
                claims.claim(
                    "roles", List.of("CONSULTA", "AUDITOR", "default-roles-liv", "INVENTADO")));

    JsonNode me = JSON.readTree(get(ME, bearer(token)).body());

    assertThat(texts(me.get("roles"))).containsExactlyInAnyOrder("CONSULTA", "AUDITOR");
    List<String> expected = new ArrayList<>(permissionsOf("CONSULTA"));
    permissionsOf("AUDITOR").stream().filter(p -> !expected.contains(p)).forEach(expected::add);
    assertThat(texts(me.get("permissions"))).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Cada usuario ve su administración; lo que mande el navegador no cuenta")
  void eachUserSeesItsOwnAdministration() {
    String subjectOfB = "me-b-" + UUID.randomUUID();
    LivDatabase.registerUser(LivDatabase.ADMIN_B, OidcTestProvider.issuer(), subjectOfB, "ACTIVE");
    Map<String, String> headers =
        bearer(OidcTestProvider.userToken(subjectOfB, LivDatabase.ADMIN_B));
    headers.put("X-Administration-Id", LivDatabase.ADMIN_A.toString());

    JsonNode me =
        JSON.readTree(get(ME + "?administrationId=" + LivDatabase.ADMIN_A, headers).body());

    assertThat(me.get("id").asString()).isEqualTo(registered(subjectOfB)[0].toString());
    assertThat(me.get("administration").get("id").asString())
        .isEqualTo(LivDatabase.ADMIN_B.toString());
    assertThat(me.get("administration").get("name").asString()).isEqualTo("Admin B");
  }

  private static Object[] registered(String subject) {
    return LivDatabase.readAsOwner(
            "SELECT id, full_name, email FROM core.admin_user"
                + " WHERE identity_subject = ? AND identity_issuer = ?",
            subject,
            OidcTestProvider.issuer())
        .getFirst();
  }

  private static List<String> permissionsOf(String role) {
    return LivDatabase.readAsOwner(
            "SELECT permission_code FROM platform.role_permission WHERE role_code = ?", role)
        .stream()
        .map(row -> (String) row[0])
        .toList();
  }

  private static List<String> texts(JsonNode array) {
    List<String> values = new ArrayList<>();
    array.forEach(value -> values.add(value.asString()));
    return values;
  }
}
