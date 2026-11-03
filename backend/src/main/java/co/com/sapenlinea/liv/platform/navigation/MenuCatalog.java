package co.com.sapenlinea.liv.platform.navigation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * The menu of LIV (001-15): the options of Doc. 01 §3, each leaf with the permission that shows it
 * and whether it is built. A branch shows if any of its children shows, and is available if any of
 * its visible children is. Loaded once from {@code platform/menu-catalog.yaml}; a malformed catalog
 * stops the application from starting.
 */
public final class MenuCatalog {

  static final Resource LIV_CATALOG = new ClassPathResource("platform/menu-catalog.yaml");

  /** Menu numbers as the contract defines them (platform.yaml, Capability.code). */
  private static final Pattern CODE = Pattern.compile("[0-9]+(\\.[0-9]+)*");

  private static final int CODE_MAX_LENGTH = 16;

  /** {@code <module>.<resource>.<action>}, as in platform.app_permission. */
  private static final Pattern PERMISSION =
      Pattern.compile("[a-z]+(-[a-z]+)*\\.[a-z]+(-[a-z]+)*\\.[a-z]+(-[a-z]+)*");

  private final List<Option> items;

  private MenuCatalog(List<Option> items) {
    this.items = List.copyOf(items);
  }

  /**
   * An option of the catalog file: a leaf has its permission and status; a branch only its
   * children.
   */
  record Option(String code, String permission, CapabilityStatus status, List<Option> children) {

    Option {
      children = children == null ? List.of() : List.copyOf(children);
    }
  }

  static MenuCatalog load(Resource resource) {
    List<Option> items;
    try {
      List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("menu", resource);
      items =
          new Binder(ConfigurationPropertySources.from(sources))
              .bind("items", Bindable.listOf(Option.class))
              .orElse(List.of());
    } catch (IOException | BindException e) {
      throw new IllegalStateException("The menu catalog " + resource + " cannot be read", e);
    }
    require(!items.isEmpty(), "The menu catalog has no options");
    validate(items, null, new HashSet<>());
    return new MenuCatalog(items);
  }

  /** The permissions the leaves of the menu name. */
  Set<String> permissions() {
    throw new UnsupportedOperationException("T14, segunda ronda");
  }

  /** The options the holder of {@code permissions} sees, in the order of the menu. */
  List<Capability> visibleTo(Set<String> permissions) {
    return visible(items, permissions);
  }

  private static List<Capability> visible(List<Option> options, Set<String> permissions) {
    List<Capability> visible = new ArrayList<>();
    for (Option option : options) {
      if (option.children().isEmpty()) {
        if (permissions.contains(option.permission())) {
          visible.add(new Capability(option.code(), option.status(), List.of()));
        }
        continue;
      }
      List<Capability> children = visible(option.children(), permissions);
      if (!children.isEmpty()) {
        boolean available =
            children.stream().anyMatch(child -> child.status() == CapabilityStatus.AVAILABLE);
        visible.add(
            new Capability(
                option.code(),
                available ? CapabilityStatus.AVAILABLE : CapabilityStatus.COMING_SOON,
                children));
      }
    }
    return List.copyOf(visible);
  }

  private static void validate(List<Option> options, String parent, Set<String> seen) {
    for (Option option : options) {
      String code = option.code();
      require(
          code != null && code.length() <= CODE_MAX_LENGTH && CODE.matcher(code).matches(),
          "Not a menu number: " + code);
      boolean firstTime = seen.add(code);
      require(firstTime, "Repeated menu option: " + code);
      String expectedParent = code.contains(".") ? code.substring(0, code.lastIndexOf('.')) : null;
      require(
          Objects.equals(expectedParent, parent),
          "Option " + code + " is not numbered under " + (parent == null ? "the root" : parent));
      if (option.children().isEmpty()) {
        require(
            option.permission() != null && PERMISSION.matcher(option.permission()).matches(),
            "Option " + code + " needs a permission <module>.<resource>.<action>");
        require(option.status() != null, "Option " + code + " needs a status");
      } else {
        require(
            option.permission() == null && option.status() == null,
            "Option " + code + " has children: its visibility and status come from them");
        validate(option.children(), code, seen);
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
