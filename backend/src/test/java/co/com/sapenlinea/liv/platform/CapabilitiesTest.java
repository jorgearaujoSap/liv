package co.com.sapenlinea.liv.platform;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.isolation.LivDatabase;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/me/capabilities} (001-15): the menu options each role sees, against the matrix
 * approved at H2 (plan, «Permisos»). The matrix is written apart here as the leaves of the menu
 * (Doc. 01 §3) each role reaches; a module or option with children shows if any child shows.
 */
@DisplayName("Capacidades del menú: GET /api/v1/me/capabilities (T14)")
class CapabilitiesTest extends PlatformWebTest {

  private static final String CAPABILITIES = "/api/v1/me/capabilities";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** 1.5.1–1.5.8: base tables, with valuation.batch.read or tax.rate.read (same roles). */
  private static final List<String> TABLAS =
      List.of("1.5.1", "1.5.2", "1.5.3", "1.5.4", "1.5.5", "1.5.6", "1.5.7", "1.5.8");

  /** 1.6.1–1.6.7: queries and export, with valuation.catalog.read. */
  private static final List<String> CONSULTAS =
      List.of("1.6.1", "1.6.2", "1.6.3", "1.6.4", "1.6.5", "1.6.6", "1.6.7");

  private static final List<String> TODO =
      leaves(
          "1.1", "1.2", "1.3", "1.4", TABLAS, "1.5.9", CONSULTAS, "1.7", "1.8", "2", "3", "4", "5",
          "6", "7", "8", "9");

  /** The leaves each role sees: the rows of the matrix of the plan, role by role. */
  private static final Map<String, List<String>> MATRIX =
      Map.ofEntries(
          entry(
              "ADMIN_SISTEMA",
              leaves(
                  "1.1", "1.2", "1.3", "1.4", TABLAS, "1.5.9", CONSULTAS, "1.7", "1.8", "8", "9")),
          entry("CARGADOR_TABLAS", leaves("1.3", "1.4", TABLAS, "1.5.9", CONSULTAS, "1.7", "8")),
          entry("APROBADOR_TABLAS", leaves("1.3", "1.4", TABLAS, "1.5.9", CONSULTAS, "1.7", "8")),
          entry("LIQUIDADOR", leaves("2", "3", "8")),
          entry("APROBADOR_LIQUIDACION", leaves("2", "3", "8")),
          entry("RECAUDO", leaves("2", "4", "5", "8")),
          entry("TESORERIA", leaves("4", "5", "8")),
          entry("APROBADOR_RECAUDO", leaves("4", "5", "8")),
          entry("JURIDICO", leaves("1.5.9", "5", "6", "7", "8")),
          entry(
              "CONSULTA", leaves("1.3", "1.4", TABLAS, CONSULTAS, "1.7", "2", "3", "4", "5", "8")),
          entry("AUDITOR", TODO));

  @Autowired JdbcClient jdbc;

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · La matriz cubre todos los roles del catálogo de LIV")
  void matrixCoversEveryRole() {
    List<String> roles = jdbc.sql("SELECT code FROM platform.app_role").query(String.class).list();

    assertThat(MATRIX.keySet()).containsExactlyInAnyOrderElementsOf(roles);
  }

  static Stream<String> roles() {
    return MATRIX.keySet().stream().sorted();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("roles")
  @Tag("001-15")
  @DisplayName("001-15 · Cada rol ve solo las opciones de la matriz")
  void eachRoleSeesItsRowOfTheMatrix(String role) {
    JsonNode items = capabilitiesOf(List.of(role));

    assertThat(leavesOf(items)).containsExactlyInAnyOrderElementsOf(MATRIX.get(role));
    assertWellFormed(items, null);
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Con varios roles ve la unión de sus opciones")
  void severalRolesSeeTheUnion() {
    JsonNode items = capabilitiesOf(List.of("JURIDICO", "RECAUDO", "default-roles-liv"));

    assertThat(leavesOf(items)).containsExactlyInAnyOrder("1.5.9", "2", "4", "5", "6", "7", "8");
    assertWellFormed(items, null);
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · El menú sigue el orden de Doc. 01 §3")
  void menuFollowsTheOrderOfTheDocument() {
    JsonNode items = capabilitiesOf(List.of("AUDITOR"));

    assertThat(codes(items)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
    JsonNode administracion = items.get(0).get("children");
    assertThat(codes(administracion))
        .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8");
    List<String> tablas = new ArrayList<>(TABLAS);
    tablas.add("1.5.9");
    assertThat(codes(administracion.get(4).get("children"))).containsExactlyElementsOf(tablas);
    assertThat(codes(administracion.get(5).get("children"))).containsExactlyElementsOf(CONSULTAS);
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Las opciones aún no construidas vienen como COMING_SOON")
  void optionsNotBuiltYetComeAsComingSoon() {
    List<String> statuses = new ArrayList<>();
    collect(
        capabilitiesOf(List.of("AUDITOR")), node -> statuses.add(node.get("status").asString()));

    assertThat(statuses).isNotEmpty().containsOnly("COMING_SOON");
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Sin roles conocidos, el menú viene vacío")
  void withoutKnownRolesTheMenuIsEmpty() {
    for (List<String> roles : List.of(List.<String>of(), List.of("INVENTADO", "auditor"))) {
      assertThat(capabilitiesOf(roles)).as("menú de %s", roles).isEmpty();
    }
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Sin token, 401 con el código 001-14")
  void withoutTokenIsRejected() {
    HttpResponse<String> response = get(CAPABILITIES, Map.of());

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("001-14");
  }

  private JsonNode capabilitiesOf(List<String> roles) {
    String token =
        OidcTestProvider.userToken(
            ACTIVE, LivDatabase.ADMIN_A, claims -> claims.claim("roles", roles));
    HttpResponse<String> response = get(CAPABILITIES, bearer(token));
    assertThat(response.statusCode()).as("estado para %s", roles).isEqualTo(200);
    return JSON.readTree(response.body()).get("items");
  }

  /**
   * Every child numbered under its parent (1.5 → 1.5.9). A branch without visible children would
   * come as a leaf, so the comparison of leaves already rejects it.
   */
  private static void assertWellFormed(JsonNode items, String parent) {
    for (JsonNode node : items) {
      String code = node.get("code").asString();
      if (parent != null) {
        assertThat(code).startsWith(parent + ".");
      }
      assertWellFormed(node.get("children"), code);
    }
  }

  private static List<String> leavesOf(JsonNode items) {
    List<String> leaves = new ArrayList<>();
    collect(
        items,
        node -> {
          if (node.get("children").isEmpty()) {
            leaves.add(node.get("code").asString());
          }
        });
    return leaves;
  }

  private static void collect(JsonNode items, Consumer<JsonNode> visitor) {
    for (JsonNode node : items) {
      visitor.accept(node);
      collect(node.get("children"), visitor);
    }
  }

  private static List<String> codes(JsonNode items) {
    List<String> codes = new ArrayList<>();
    items.forEach(node -> codes.add(node.get("code").asString()));
    return codes;
  }

  /** Flattens codes and lists of codes into one list. */
  private static List<String> leaves(Object... parts) {
    List<String> leaves = new ArrayList<>();
    for (Object part : parts) {
      if (part instanceof List<?> list) {
        list.forEach(code -> leaves.add((String) code));
      } else {
        leaves.add((String) part);
      }
    }
    return List.copyOf(leaves);
  }
}
