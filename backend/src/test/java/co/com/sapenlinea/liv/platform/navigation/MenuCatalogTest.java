package co.com.sapenlinea.liv.platform.navigation;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/**
 * The menu catalog (001-15): which options a set of permissions reaches, how the status of a branch
 * comes from its visible children, and the catalogs that must not load.
 */
@DisplayName("Catálogo del menú (T14)")
class MenuCatalogTest {

  private static final String SMALL =
      """
      items:
        - code: "1"
          children:
            - code: "1.1"
              permission: core.user.read
              status: AVAILABLE
            - code: "1.2"
              permission: tax.rate.read
              status: COMING_SOON
        - code: "2"
          permission: core.vehicle.read
          status: COMING_SOON
      """;

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Una rama se ve si se ve alguno de sus hijos, y solo con esos hijos")
  void branchShowsWithItsVisibleChildren() {
    MenuCatalog catalog = load(SMALL);

    assertThat(catalog.visibleTo(Set.of("tax.rate.read")))
        .containsExactly(
            new Capability(
                "1",
                CapabilityStatus.COMING_SOON,
                List.of(new Capability("1.2", CapabilityStatus.COMING_SOON, List.of()))));
    assertThat(catalog.visibleTo(Set.of("core.vehicle.read")))
        .containsExactly(new Capability("2", CapabilityStatus.COMING_SOON, List.of()));
    assertThat(catalog.visibleTo(Set.of())).isEmpty();
    assertThat(catalog.visibleTo(Set.of("audit.event.read"))).isEmpty();
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Una rama está disponible si alguno de sus hijos visibles lo está")
  void branchIsAvailableIfAVisibleChildIs() {
    MenuCatalog catalog = load(SMALL);

    List<Capability> both = catalog.visibleTo(Set.of("core.user.read", "tax.rate.read"));
    assertThat(both.getFirst().status()).isEqualTo(CapabilityStatus.AVAILABLE);
    assertThat(both.getFirst().children())
        .extracting(Capability::code)
        .containsExactly("1.1", "1.2");
    // 1.1 is available but hidden: it does not make its branch available.
    assertThat(catalog.visibleTo(Set.of("tax.rate.read")).getFirst().status())
        .isEqualTo(CapabilityStatus.COMING_SOON);
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · El catálogo del menú de LIV carga")
  void livCatalogLoads() {
    assertThat(MenuCatalog.load(MenuCatalog.LIV_CATALOG).visibleTo(Set.of("audit.event.read")))
        .extracting(Capability::code)
        .containsExactly("9");
  }

  /**
   * Each permission of the matrix and the leaves it shows: decisions of 2026-11-03, written apart.
   */
  private static final Map<String, List<String>> LEAVES_BY_PERMISSION =
      Map.ofEntries(
          entry("core.administration.read", List.of("1.1")),
          entry("core.user.read", List.of("1.2")),
          entry("documents.legal-instrument.read", List.of("1.3")),
          entry("tax.fiscal-year.read", List.of("1.4")),
          entry("valuation.batch.read", List.of("1.5.1", "1.5.2", "1.5.3", "1.5.4")),
          entry("tax.rate.read", List.of("1.5.5", "1.5.6", "1.5.7", "1.5.8")),
          entry("jurisdiction.config.read", List.of("1.5.9")),
          entry(
              "valuation.catalog.read",
              List.of("1.6.1", "1.6.2", "1.6.3", "1.6.4", "1.6.5", "1.6.6", "1.6.7")),
          entry("valuation.vehicle-reference.read", List.of("1.7")),
          entry("core.parameter.read", List.of("1.8")),
          entry("core.vehicle.read", List.of("2")),
          entry("tax.assessment.read", List.of("3")),
          entry("ledger.obligation.read", List.of("4")),
          entry("portfolio.balance.read", List.of("5")),
          entry("collection.case.read", List.of("6")),
          entry("enforcement.measure.read", List.of("7")),
          entry("documents.document.read", List.of("8")),
          entry("audit.event.read", List.of("9")));

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Cada permiso muestra exactamente sus opciones del menú de LIV")
  void eachPermissionShowsItsLeaves() {
    MenuCatalog catalog = MenuCatalog.load(MenuCatalog.LIV_CATALOG);

    assertThat(catalog.permissions()).isEqualTo(LEAVES_BY_PERMISSION.keySet());
    LEAVES_BY_PERMISSION.forEach((permission, leaves) ->
        assertThat(leavesOf(catalog.visibleTo(Set.of(permission))))
            .as(permission)
            .containsExactlyElementsOf(leaves));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedCatalogs")
  @Tag("001-15")
  @DisplayName("001-15 · Un catálogo mal escrito no carga, y dice por qué")
  void malformedCatalogIsRejected(String why, String yaml, String reason) {
    assertThatThrownBy(() -> load(yaml))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(reason);
  }

  static Stream<Arguments> malformedCatalogs() {
    return Stream.of(
        arguments(
            "hoja sin permiso",
            """
            items:
              - code: "1"
                status: COMING_SOON
            """,
            "needs a permission"),
        arguments(
            "permiso que no es <módulo>.<recurso>.<acción>",
            """
            items:
              - code: "1"
                permission: ADMIN_SISTEMA
                status: COMING_SOON
            """,
            "needs a permission"),
        arguments(
            "hoja sin estado",
            """
            items:
              - code: "1"
                permission: core.user.read
            """,
            "needs a status"),
        arguments(
            "estado desconocido",
            """
            items:
              - code: "1"
                permission: core.user.read
                status: LISTO
            """,
            "cannot be read"),
        arguments(
            "rama con permiso propio",
            """
            items:
              - code: "1"
                permission: core.user.read
                status: COMING_SOON
                children:
                  - code: "1.1"
                    permission: core.user.read
                    status: COMING_SOON
            """,
            "has children"),
        arguments(
            "hijo numerado fuera de su padre",
            """
            items:
              - code: "1"
                children:
                  - code: "2.1"
                    permission: core.user.read
                    status: COMING_SOON
            """,
            "is not numbered under 1"),
        arguments(
            "nieto colgado del abuelo",
            """
            items:
              - code: "1"
                children:
                  - code: "1.5.9"
                    permission: core.user.read
                    status: COMING_SOON
            """,
            "is not numbered under 1"),
        arguments(
            "código repetido",
            """
            items:
              - code: "2"
                permission: core.user.read
                status: COMING_SOON
              - code: "2"
                permission: tax.rate.read
                status: COMING_SOON
            """,
            "Repeated menu option: 2"),
        arguments(
            "código que no es un número de menú",
            """
            items:
              - code: "Inicio"
                permission: core.user.read
                status: COMING_SOON
            """,
            "Not a menu number: Inicio"),
        arguments(
            "código con cero a la izquierda, igual a otro",
            """
            items:
              - code: "01"
                permission: core.user.read
                status: COMING_SOON
            """,
            "Not a menu number: 01"),
        arguments(
            "código de más de 16 caracteres",
            """
            items:
              - code: "12345678901234567"
                permission: core.user.read
                status: COMING_SOON
            """,
            "Not a menu number"),
        arguments(
            "YAML roto",
            """
            items: [
            """,
            "cannot be read"),
        arguments(
            "menú vacío",
            """
            items: []
            """,
            "has no options"));
  }

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Un catálogo que no existe no carga")
  void missingCatalogIsRejected() {
    assertThatThrownBy(() -> MenuCatalog.load(new ClassPathResource("platform/no-existe.yaml")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be read");
  }

  private static List<String> leavesOf(List<Capability> items) {
    List<String> leaves = new ArrayList<>();
    for (Capability item : items) {
      if (item.children().isEmpty()) {
        leaves.add(item.code());
      }
      leaves.addAll(leavesOf(item.children()));
    }
    return leaves;
  }

  private static MenuCatalog load(String yaml) {
    return MenuCatalog.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
  }
}
