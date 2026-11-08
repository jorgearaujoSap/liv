package co.com.sapenlinea.liv.contracts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The two jurisdiction configurations of the tests: copies of the template and of Ipiales from the
 * platform repository ({@code docs/funcional/anexos/}). They are a snapshot: the backend does not
 * read outside backend/ (IN-01), and the root validator checks the real annexes (CJ01). Each call
 * gives a fresh tree, so a test can change one node without touching the others.
 */
final class TestConfigurations {

  static final String TEMPLATE = "config_jurisdiccion_plantilla_2026.yaml";
  static final String IPIALES = "config_jurisdiccion_52356_2026.yaml";
  static final List<String> ALL = List.of(TEMPLATE, IPIALES);

  private static final Path FOLDER =
      Path.of("src", "test", "resources", "contracts", "jsonschema", "config-jurisdiccion");

  private TestConfigurations() {}

  static ObjectNode load(String name) {
    try {
      return (ObjectNode) SafeYamlLoader.load(FOLDER.resolve(name));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Replaces the node at a JSON Pointer path, which must exist. */
  static ObjectNode set(ObjectNode root, String path, JsonNode value) {
    JsonPointer pointer = JsonPointer.compile(path);
    JsonNode parent = root.at(pointer.head());
    String last = pointer.last().getMatchingProperty();
    if (parent instanceof ArrayNode array) {
      array.set(pointer.last().getMatchingIndex(), value);
    } else {
      ((ObjectNode) parent).set(last, value);
    }
    return root;
  }

  /** Removes the key at a JSON Pointer path, which must exist. */
  static ObjectNode remove(ObjectNode root, String path) {
    JsonPointer pointer = JsonPointer.compile(path);
    ((ObjectNode) root.at(pointer.head())).remove(pointer.last().getMatchingProperty());
    return root;
  }
}
