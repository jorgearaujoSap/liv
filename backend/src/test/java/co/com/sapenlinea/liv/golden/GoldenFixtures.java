package co.com.sapenlinea.liv.golden;

import co.com.sapenlinea.liv.contracts.CommonCases;
import co.com.sapenlinea.liv.contracts.JurisdictionConfigSchemas;
import co.com.sapenlinea.liv.contracts.SafeYamlLoader;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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

  /**
   * The Doc. 05 (interest) has no golden case until its open items 2, 10, 11 and 12 are resolved.
   */
  private static final Pattern DOC_05 = Pattern.compile("(?i)\\bdoc(?:umento)?\\.?\\s*0?5\\b");

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
    return files(GOLDEN, ".schema.json");
  }

  /** The schema of a module; it fails if the module has fixtures and no schema. */
  public static Schema schema(String module) {
    Path file = GOLDEN.resolve(module + ".schema.json");
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException("el módulo " + module + " no tiene esquema: " + file);
    }
    return schema(file);
  }

  /** A schema from its file, with the validator of the feature 004. */
  public static Schema schema(Path file) {
    return JurisdictionConfigSchemas.compile(CommonCases.EXACT.readTree(file.toFile()));
  }

  /** The errors of a schema against the meta-schema 2020-12; empty if it is valid. */
  public static List<String> metaSchemaProblems(JsonNode schema) {
    return JurisdictionConfigSchemas.metaSchemaErrors(schema).stream()
        .map(error -> error.getInstanceLocation() + " " + error.getKeyword())
        .toList();
  }

  /** The fixtures of a module, by file name; empty if the module has none. */
  public static List<Fixture> fixtures(String module) {
    Path folder = GOLDEN.resolve(module);
    if (!Files.isDirectory(folder)) {
      return List.of();
    }
    List<Fixture> fixtures = new ArrayList<>();
    for (Path file : files(folder, ".yaml")) {
      try {
        fixtures.add(new Fixture(file, SafeYamlLoader.load(file)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return fixtures;
  }

  /** The cases of all the fixtures of a module; an id repeated between files is an error. */
  public static List<GoldenCase> cases(String module) {
    List<GoldenCase> cases = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (Fixture fixture : fixtures(module)) {
      for (GoldenCase goldenCase : cases(fixture.tree())) {
        if (!ids.add(goldenCase.id())) {
          throw new IllegalStateException(
              "el id " + goldenCase.id() + " se repite en " + fixture.file());
        }
        cases.add(goldenCase);
      }
    }
    return cases;
  }

  /** The cases of one fixture, with the header (document, configuration, comparison). */
  public static List<GoldenCase> cases(JsonNode fixture) {
    JsonNode document = fixture.path("document");
    GoldenCase.Configuration configuration =
        new GoldenCase.Configuration(
            text(fixture.path("configuration"), "file"),
            text(fixture.path("configuration"), "sha256"));
    Map<String, GoldenCase.Comparison> comparison = new LinkedHashMap<>();
    fixture
        .path("comparison")
        .properties()
        .forEach(field ->
            comparison.put(
                field.getKey(),
                new GoldenCase.Comparison(
                    field.getValue().path("scale").intValue(),
                    RoundingMode.valueOf(text(field.getValue(), "rounding")))));
    return fixture
        .path("cases")
        .valueStream()
        .map(node -> {
          JsonNode row = node.path("row");
          return new GoldenCase(
              text(node, "id"),
              strings(node.path("rules")),
              text(node, "description"),
              new GoldenCase.Source(
                  text(document, "code"),
                  text(document, "version"),
                  text(document, "section"),
                  row.isIntegralNumber() ? row.intValue() : null),
              strings(node.path("assumptions")),
              node.path("inferred_inputs")
                  .valueStream()
                  .map(input ->
                      new GoldenCase.InferredInput(text(input, "field"), text(input, "reason")))
                  .toList(),
              strings(node.path("depends_on")),
              node.path("input"),
              node.path("expected"),
              comparison,
              configuration);
        })
        .toList();
  }

  /**
   * The problems of a fixture: its schema, the common checks and the extra rules; empty if valid.
   */
  public static List<Problem> problems(Schema schema, JsonNode fixture, Rule... extra) {
    Set<Problem> problems = new LinkedHashSet<>();
    for (Error error : schema.validate(fixture)) {
      problems.add(new Problem(path(error), error.getKeyword()));
    }
    problems.addAll(commonProblems(fixture));
    for (Rule rule : extra) {
      problems.addAll(rule.check(fixture));
    }
    return List.copyOf(problems);
  }

  /** What every fixture must satisfy, whatever its module. */
  private static List<Problem> commonProblems(JsonNode fixture) {
    List<Problem> problems = new ArrayList<>();
    checkDocument(fixture.path("document"), problems);
    checkConfiguration(fixture.path("configuration"), problems);
    checkCaseIds(fixture.path("cases"), problems);
    scanForDoc05(fixture, "", problems);
    checkScales(fixture, problems);
    return problems;
  }

  private static void checkDocument(JsonNode document, List<Problem> problems) {
    if (text(document, "code").isBlank()) {
      problems.add(new Problem("/document/code", "document-code"));
    }
    if (text(document, "version").isBlank()) {
      problems.add(new Problem("/document/version", "document-version"));
    }
  }

  private static void checkCaseIds(JsonNode cases, List<Problem> problems) {
    Set<String> ids = new HashSet<>();
    for (int index = 0; index < cases.size(); index++) {
      String id = text(cases.get(index), "id");
      if (!id.isEmpty() && !ids.add(id)) {
        problems.add(new Problem("/cases/" + index + "/id", "unique-id"));
      }
    }
  }

  private static void checkScales(JsonNode fixture, List<Problem> problems) {
    Map<String, Integer> scales = new LinkedHashMap<>();
    fixture
        .path("comparison")
        .properties()
        .forEach(field -> scales.put(field.getKey(), field.getValue().path("scale").intValue()));
    JsonNode cases = fixture.path("cases");
    for (int index = 0; index < cases.size(); index++) {
      checkScale(
          cases.get(index).path("expected"), "/cases/" + index + "/expected", "", scales, problems);
    }
  }

  private static void checkConfiguration(JsonNode configuration, List<Problem> problems) {
    String file = text(configuration, "file");
    if (file.isEmpty()) {
      return;
    }
    Optional<Path> copy = GoldenConfiguration.insideResources(file);
    if (copy.isEmpty()) {
      problems.add(new Problem("/configuration/file", "configuration-path"));
    } else if (!Files.isRegularFile(copy.get())) {
      problems.add(new Problem("/configuration/file", "configuration-missing"));
    } else if (!text(configuration, "sha256").equals(sha256(copy.get()))) {
      problems.add(new Problem("/configuration/sha256", "configuration-sha256"));
    }
  }

  private static String sha256(Path file) {
    try {
      return GoldenConfiguration.sha256Lf(Files.readString(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void scanForDoc05(JsonNode node, String path, List<Problem> problems) {
    if (node.isString() && DOC_05.matcher(node.stringValue()).find()) {
      problems.add(new Problem(path, "no-doc-05"));
    } else if (node.isObject()) {
      node.properties()
          .forEach(field ->
              scanForDoc05(field.getValue(), path + "/" + escape(field.getKey()), problems));
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        scanForDoc05(node.get(index), path + "/" + index, problems);
      }
    }
  }

  /** A number under a field with a comparison has no more decimals than that comparison's scale. */
  private static void checkScale(
      JsonNode node,
      String path,
      String field,
      Map<String, Integer> scales,
      List<Problem> problems) {
    if (node.isObject()) {
      node.properties()
          .forEach(entry ->
              checkScale(
                  entry.getValue(),
                  path + "/" + escape(entry.getKey()),
                  entry.getKey(),
                  scales,
                  problems));
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        checkScale(node.get(index), path + "/" + index, field, scales, problems);
      }
    } else if (node.isNumber()
        && scales.containsKey(field)
        && node.decimalValue().scale() > scales.get(field)) {
      problems.add(new Problem(path, "scale"));
    }
  }

  /** The path of an error; for a missing or unknown key, the path of that key. */
  private static String path(Error error) {
    String path = error.getInstanceLocation().toString();
    String keyword = error.getKeyword();
    if (("required".equals(keyword) || "additionalProperties".equals(keyword))
        && error.getProperty() != null) {
      path = path + "/" + escape(error.getProperty());
    }
    return path;
  }

  private static String escape(String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }

  private static List<Path> files(Path folder, String suffix) {
    try (Stream<Path> files = Files.list(folder)) {
      return files
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(suffix))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The text under a key; a missing or non-text value reads as empty. */
  private static String text(JsonNode node, String key) {
    JsonNode value = node.path(key);
    return value.isString() ? value.stringValue() : "";
  }

  private static List<String> strings(JsonNode list) {
    return list.valueStream().map(JsonNode::stringValue).toList();
  }
}
