package co.com.sapenlinea.liv.golden;

import com.networknt.schema.Schema;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Finds, loads and checks the golden fixtures ({@code src/test/resources/golden/<module>/*.yaml}).
 * A fixture is read with the safe YAML loader and validated with the validator of the feature 004
 * (ADR 0032), against {@code golden/<module>.schema.json}.
 */
public final class GoldenFixtures {

  /** The modules that have golden fixtures. */
  public static final List<String> MODULES = List.of("tax", "ledger");

  public static final Path GOLDEN = GoldenConfiguration.RESOURCES.resolve("golden");

  private GoldenFixtures() {}

  /** A fixture file and its tree. */
  public record Fixture(Path file, JsonNode tree) {}

  /** What is wrong in a fixture: the JSON Pointer path and the rule that rejects it. */
  public record Problem(String path, String rule) {}

  /** A check of a module that its schema cannot express (for example, one form of the base). */
  public interface Rule {
    List<Problem> check(JsonNode fixture);
  }

  /** The schemas {@code golden/*.schema.json}. */
  public static List<Path> schemaFiles() {
    throw new UnsupportedOperationException("pendiente");
  }

  /** The schema of a module; it fails if the module has fixtures and no schema. */
  public static Schema schema(String module) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** A schema from its file, with the validator of the feature 004. */
  public static Schema schema(Path file) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** The errors of a schema against the meta-schema 2020-12; empty if it is valid. */
  public static List<String> metaSchemaProblems(JsonNode schema) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** The fixtures of a module, by file name; empty if the module has none. */
  public static List<Fixture> fixtures(String module) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** The cases of all the fixtures of a module; an id repeated between files is an error. */
  public static List<GoldenCase> cases(String module) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** The cases of one fixture, with the header (document, configuration, comparison). */
  public static List<GoldenCase> cases(JsonNode fixture) {
    throw new UnsupportedOperationException("pendiente");
  }

  /**
   * The problems of a fixture: its schema, the common checks and the extra rules; empty if valid.
   */
  public static List<Problem> problems(Schema schema, JsonNode fixture, Rule... extra) {
    throw new UnsupportedOperationException("pendiente");
  }
}
