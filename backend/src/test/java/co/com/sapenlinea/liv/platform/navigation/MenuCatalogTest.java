package co.com.sapenlinea.liv.platform.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

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

  @Test
  @Tag("001-15")
  @DisplayName("001-15 · Un catálogo mal escrito no carga")
  void malformedCatalogsAreRejected() {
    List<String> malformed =
        List.of(
            // A leaf without permission.
            """
            items:
              - code: "1"
                status: COMING_SOON
            """,
            // A leaf without status.
            """
            items:
              - code: "1"
                permission: core.user.read
            """,
            // A branch with its own permission: its visibility comes only from its children.
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
            // A child numbered outside its parent.
            """
            items:
              - code: "1"
                children:
                  - code: "2.1"
                    permission: core.user.read
                    status: COMING_SOON
            """,
            // A repeated code.
            """
            items:
              - code: "2"
                permission: core.user.read
                status: COMING_SOON
              - code: "2"
                permission: tax.rate.read
                status: COMING_SOON
            """,
            // A code that is not a menu number.
            """
            items:
              - code: "Inicio"
                permission: core.user.read
                status: COMING_SOON
            """,
            // A permission that is not <module>.<resource>.<action>.
            """
            items:
              - code: "1"
                permission: ADMIN_SISTEMA
                status: COMING_SOON
            """,
            // An empty menu.
            """
            items: []
            """);

    for (String yaml : malformed) {
      assertThatThrownBy(() -> load(yaml)).as(yaml).isInstanceOf(IllegalStateException.class);
    }
  }

  private static MenuCatalog load(String yaml) {
    return MenuCatalog.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
  }
}
