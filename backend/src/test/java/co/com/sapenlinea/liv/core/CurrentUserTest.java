package co.com.sapenlinea.liv.core;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import co.com.sapenlinea.liv.platform.OidcTestProvider;
import co.com.sapenlinea.liv.platform.PermissionCatalog;
import co.com.sapenlinea.liv.platform.PlatformWebTest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

  /** The permissions of CONSULTA in the matrix approved at H2 (plan, «Permisos»), written apart. */
  private static final List<String> CONSULTA =
      List.of(
          "documents.legal-instrument.read",
          "tax.fiscal-year.read",
          "valuation.batch.read",
          "tax.rate.read",
          "valuation.catalog.read",
          "valuation.vehicle-reference.read",
          "core.vehicle.read",
          "tax.assessment.read",
          "ledger.obligation.read",
          "portfolio.balance.read",
          "documents.document.read");

  /** AUDITOR sees every option of the menu: all the permissions of the approved matrix. */
  private static final List<String> AUDITOR =
      List.of(
          "core.administration.read",
          "core.user.read",
          "documents.legal-instrument.read",
          "tax.fiscal-year.read",
          "valuation.batch.read",
          "tax.rate.read",
          "jurisdiction.config.read",
          "valuation.catalog.read",
          "valuation.vehicle-reference.read",
          "core.parameter.read",
          "core.vehicle.read",
          "tax.assessment.read",
          "ledger.obligation.read",
          "portfolio.balance.read",
          "collection.case.read",
          "enforcement.measure.read",
          "documents.document.read",
          "audit.event.read");

  @Autowired PermissionCatalog catalog;

  @Test
  @Tag("001-13")
  @DisplayName("001-13 · Responde identificador, nombre, correo, administración, roles y permisos")
  void currentUserFromTheServer() {
    HttpResponse<String> response =
        get(ME, bearer(OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A)));

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode me = JSON.readTree(response.body());
    Registered registered = registered(ACTIVE);
    assertThat(me.get("id").asString()).isEqualTo(registered.id().toString());
    // The registry, not the token: the token says "Usuario de prueba api-activo".
    assertThat(me.get("fullName").asString()).isEqualTo(registered.fullName());
    assertThat(me.get("email").asString()).isEqualTo(registered.email());
    assertThat(me.get("administration").get("id").asString())
        .isEqualTo(LivDatabase.ADMIN_A.toString());
    assertThat(me.get("administration").get("name").asString())
        .isEqualTo(administrationName(LivDatabase.ADMIN_A));
    assertThat(texts(me.get("roles"))).containsExactly("CONSULTA");
    assertThat(texts(me.get("permissions"))).containsExactlyInAnyOrderElementsOf(CONSULTA);
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

    assertThat(texts(me.get("roles"))).containsExactly("AUDITOR", "CONSULTA");
    // CONSULTA is contained in AUDITOR: the union is AUDITOR's list, once each.
    assertThat(AUDITOR).containsAll(CONSULTA);
    assertThat(texts(me.get("permissions"))).containsExactlyInAnyOrderElementsOf(AUDITOR);
  }

  @Test
  @Tag("001-13")
  @DisplayName("001-13 · Sin roles conocidos entra a Inicio, sin roles ni permisos")
  void withoutKnownRoles() {
    for (List<String> roles : List.of(List.<String>of(), List.of("INVENTADO", "consulta"))) {
      String token =
          OidcTestProvider.userToken(
              ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("roles", roles));

      HttpResponse<String> response = get(ME, bearer(token));

      assertThat(response.statusCode()).isEqualTo(200);
      JsonNode me = JSON.readTree(response.body());
      assertThat(texts(me.get("roles"))).as("roles de %s", roles).isEmpty();
      assertThat(texts(me.get("permissions"))).as("permisos de %s", roles).isEmpty();
    }
  }

  @Test
  @Tag("001-17")
  @DisplayName("001-17 · Cada usuario ve su administración y sus roles; lo del navegador no cuenta")
  void eachUserSeesItsOwnAdministration() {
    String subjectOfB = "me-b-" + UUID.randomUUID();
    LivDatabase.registerUser(LivDatabase.ADMIN_B, OidcTestProvider.issuer(), subjectOfB, "ACTIVE");
    String tokenOfB =
        OidcTestProvider.userToken(
            subjectOfB, LivDatabase.ADMIN_B, claims -> claims.claim("roles", List.of("AUDITOR")));
    Map<String, String> headers = bearer(tokenOfB);
    headers.put("X-Administration-Id", LivDatabase.ADMIN_A.toString());

    JsonNode ofB =
        JSON.readTree(get(ME + "?administrationId=" + LivDatabase.ADMIN_A, headers).body());
    JsonNode ofA =
        JSON.readTree(
            get(ME, bearer(OidcTestProvider.userToken(ACTIVE, LivDatabase.ADMIN_A))).body());

    assertThat(ofB.get("id").asString()).isEqualTo(registered(subjectOfB).id().toString());
    assertThat(ofB.get("administration").get("id").asString())
        .isEqualTo(LivDatabase.ADMIN_B.toString());
    assertThat(ofB.get("administration").get("name").asString())
        .isEqualTo(administrationName(LivDatabase.ADMIN_B));
    assertThat(texts(ofB.get("roles"))).containsExactly("AUDITOR");
    assertThat(texts(ofB.get("permissions"))).containsExactlyInAnyOrderElementsOf(AUDITOR);
    assertThat(ofA.get("administration").get("id").asString())
        .isEqualTo(LivDatabase.ADMIN_A.toString());
    assertThat(texts(ofA.get("permissions"))).doesNotContain("audit.event.read");
  }

  @Test
  @Tag("001-13")
  @DisplayName("001-13 · El catálogo no da permisos por roles que no conoce")
  void catalogIgnoresUnknownRoles() {
    assertThat(catalog.permissionsOf(Set.of("INVENTADO"))).isEmpty();
    assertThat(catalog.permissionsOf(Set.of("CONSULTA", "INVENTADO")))
        .containsExactlyInAnyOrderElementsOf(CONSULTA);
  }

  private record Registered(UUID id, String fullName, String email) {}

  private static Registered registered(String subject) {
    Object[] row =
        LivDatabase.readAsOwner(
                "SELECT id, full_name, email FROM core.admin_user"
                    + " WHERE identity_subject = ? AND identity_issuer = ?",
                subject,
                OidcTestProvider.issuer())
            .getFirst();
    return new Registered((UUID) row[0], (String) row[1], (String) row[2]);
  }

  private static String administrationName(UUID administration) {
    return (String)
        LivDatabase.readAsOwner(
                "SELECT name FROM core.tax_administration WHERE id = ?", administration)
            .getFirst()[0];
  }

  private static List<String> texts(JsonNode array) {
    List<String> values = new ArrayList<>();
    array.forEach(value -> values.add(value.asString()));
    return values;
  }
}
