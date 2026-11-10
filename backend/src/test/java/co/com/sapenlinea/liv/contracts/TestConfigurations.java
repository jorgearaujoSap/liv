package co.com.sapenlinea.liv.contracts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The two jurisdiction configurations of the tests, for each version of the schema: copies of the
 * template and of Ipiales from the platform repository ({@code docs/funcional/anexos/}), in a
 * folder named after the version. They are a snapshot: the backend does not read outside backend/
 * (IN-01), and the root validator checks the real annexes (CJ01). Each call gives a fresh tree, so
 * a test can change one node without touching the others.
 */
final class TestConfigurations {

  static final String TEMPLATE = "config_jurisdiccion_plantilla_2026.yaml";
  static final String IPIALES = "config_jurisdiccion_52356_2026.yaml";
  static final List<String> ALL = List.of(TEMPLATE, IPIALES);

  private static final Path FOLDER =
      CommonCases.BACKEND.resolve(
          Path.of("src", "test", "resources", "contracts", "jsonschema", "config-jurisdiccion"));

  private TestConfigurations() {}

  /** The versions that have a folder of copies, from the lowest to the highest. */
  static List<String> versions() {
    try (Stream<Path> folders = Files.list(FOLDER)) {
      return folders
          .filter(Files::isDirectory)
          .map(folder -> folder.getFileName().toString())
          .map(CommonCases::requireVersion)
          .sorted(CommonCases::compare)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The text of a configuration of a version, as it is on disk. */
  static String text(String version, String name) {
    try {
      return Files.readString(FOLDER.resolve(version).resolve(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static ObjectNode load(String version, String name) {
    try {
      JsonNode tree = SafeYamlLoader.load(FOLDER.resolve(version).resolve(name));
      if (!(tree instanceof ObjectNode object)) {
        throw new IllegalStateException(version + "/" + name + " no es un objeto");
      }
      return object;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Replaces the node at a JSON Pointer path, which must exist. */
  static ObjectNode set(ObjectNode root, String path, JsonNode value) {
    JsonPointer pointer = JsonPointer.compile(path);
    existing(root, path);
    JsonNode parent = root.at(pointer.head());
    if (parent instanceof ArrayNode array) {
      array.set(pointer.last().getMatchingIndex(), value);
    } else {
      ((ObjectNode) parent).set(pointer.last().getMatchingProperty(), value);
    }
    return root;
  }

  /** Removes the key at a JSON Pointer path, which must exist. */
  static ObjectNode remove(ObjectNode root, String path) {
    JsonPointer pointer = JsonPointer.compile(path);
    existing(root, path);
    ((ObjectNode) root.at(pointer.head())).remove(pointer.last().getMatchingProperty());
    return root;
  }

  /** A typo in the path of a test fails with the path, not with a NullPointerException. */
  private static void existing(ObjectNode root, String path) {
    if (root.at(path).isMissingNode()) {
      throw new IllegalArgumentException("la ruta no existe en la configuración: " + path);
    }
  }
}
