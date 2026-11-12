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

  /** The folder of the golden schemas, fixtures and README. */
  public static final Path GOLDEN = GoldenConfiguration.RESOURCES.resolve("golden");

  /** The folder of the synthetic schema and fixture of the tests of this package. */
  private static final String TEST_ONLY = "test-only";

  private static final String README = "README.md";

  /**
   * What cites the Doc. 05 (interest), which has no golden case until its open items 2, 10, 11 and
   * 12 are resolved: its name, its file, its rule codes and its configuration section.
   */
  private static final Pattern DOC_05 =
      Pattern.compile(
          "(?i)\\bdoc(?:umento)?s?\\.?[\\s\\u00A0\\u202F]*0?5\\b|05_cartera|\\bcartera\\.[a-z_]"
              + "|\\b(?-i:(?:MO|SA|PS|CH)[-\\u2010-\\u2013][0-9])");

  /** The keywords of the portable subset (no {@code oneOf} and no {@code multipleOf}). */
  private static final Set<String> KEYWORDS =
      Set.of(
          "$schema",
          "$id",
          "$ref",
          "$defs",
          "title",
          "description",
          "$comment",
          "type",
          "enum",
          "const",
          "properties",
          "patternProperties",
          "required",
          "additionalProperties",
          "propertyNames",
          "minProperties",
          "maxProperties",
          "items",
          "prefixItems",
          "contains",
          "minItems",
          "maxItems",
          "uniqueItems",
          "minLength",
          "maxLength",
          "pattern",
          "minimum",
          "maximum",
          "exclusiveMinimum",
          "exclusiveMaximum",
          "anyOf",
          "allOf",
          "not",
          "if",
          "then",
          "else");

  /** Keywords whose value maps names to schemas, lists schemas or is one schema. */
  private static final Set<String> SCHEMA_MAPS = Set.of("properties", "patternProperties", "$defs");

  private static final Set<String> SCHEMA_LISTS = Set.of("anyOf", "allOf", "prefixItems");
  private static final Set<String> SCHEMAS =
      Set.of(
          "additionalProperties",
          "propertyNames",
          "items",
          "contains",
          "not",
          "if",
          "then",
          "else");

  private static final List<String> TEXT_LISTS = List.of("rules", "assumptions", "depends_on");

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
    return entries(GOLDEN).stream()
        .filter(file -> file.getFileName().toString().endsWith(".schema.json"))
        .toList();
  }

  /**
   * What is in {@code golden/} and is neither a module, the synthetic folder, a schema nor README.
   */
  public static List<String> unexpectedEntries() {
    return entries(GOLDEN).stream()
        .filter(entry -> {
          String name = entry.getFileName().toString();
          return Files.isDirectory(entry)
              ? !MODULES.contains(name) && !TEST_ONLY.equals(name)
              : !README.equals(name) && !name.endsWith(".schema.json");
        })
        .map(entry -> entry.getFileName().toString())
        .toList();
  }

  /** The schema of a module; it fails if {@code golden/<module>.schema.json} does not exist. */
  public static Schema schema(String module) {
    Path file = GOLDEN.resolve(module + ".schema.json");
    if (!Files.isRegularFile(file)) {
      throw new IllegalStateException("el módulo " + module + " no tiene esquema: " + file);
    }
    return schema(file);
  }

  /** A schema from its file, with the validator of the feature 004. */
  public static Schema schema(Path file) {
    try {
      return JurisdictionConfigSchemas.compile(CommonCases.EXACT.readTree(file.toFile()));
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "no se pudo cargar el esquema " + file + ": " + e.getMessage(), e);
    }
  }

  /**
   * What is wrong in a schema: its errors against the meta-schema 2020-12 and every keyword that is
   * not in the portable subset (the meta-schema accepts a misspelled keyword, which would turn a
   * rule off in silence); empty if it is valid.
   */
  public static List<String> metaSchemaProblems(JsonNode schema) {
    List<String> problems =
        new ArrayList<>(
            JurisdictionConfigSchemas.metaSchemaErrors(schema).stream()
                .map(error -> error.getInstanceLocation() + " " + error.getKeyword())
                .toList());
    checkKeywords(schema, "", problems);
    return problems;
  }

  private static void checkKeywords(JsonNode schema, String path, List<String> problems) {
    if (!schema.isObject()) {
      return;
    }
    for (Map.Entry<String, JsonNode> entry : schema.properties()) {
      String keyword = entry.getKey();
      JsonNode value = entry.getValue();
      String child = path + "/" + escape(keyword);
      if (!KEYWORDS.contains(keyword)) {
        problems.add(child + " palabra clave desconocida o fuera del subconjunto portable");
      } else if (SCHEMA_MAPS.contains(keyword) && value.isObject()) {
        for (Map.Entry<String, JsonNode> named : value.properties()) {
          checkKeywords(named.getValue(), child + "/" + escape(named.getKey()), problems);
        }
      } else if (SCHEMA_LISTS.contains(keyword) && value.isArray()) {
        for (int index = 0; index < value.size(); index++) {
          checkKeywords(value.get(index), child + "/" + index, problems);
        }
      } else if (SCHEMAS.contains(keyword)) {
        checkKeywords(value, child, problems);
      }
    }
  }

  /**
   * The fixtures of a module, by file name; empty if the module has no folder. Anything in the
   * folder that is not a {@code .yaml} file is an error, so that a fixture is never skipped.
   */
  public static List<Fixture> fixtures(String module) {
    Path folder = GOLDEN.resolve(module);
    if (!Files.isDirectory(folder)) {
      return List.of();
    }
    List<Fixture> fixtures = new ArrayList<>();
    for (Path file : entries(folder)) {
      if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".yaml")) {
        throw new IllegalStateException(
            "golden/" + module + " solo admite archivos .yaml: " + file);
      }
      fixtures.add(new Fixture(file, load(file)));
    }
    return fixtures;
  }

  /** The cases of all the fixtures of a module; an id repeated between files is an error. */
  public static List<GoldenCase> cases(String module) {
    List<GoldenCase> cases = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (Fixture fixture : fixtures(module)) {
      for (GoldenCase goldenCase : casesOf(fixture)) {
        if (!ids.add(goldenCase.id())) {
          throw new IllegalStateException(
              "el id " + goldenCase.id() + " se repite en " + fixture.file());
        }
        cases.add(goldenCase);
      }
    }
    return cases;
  }

  private static List<GoldenCase> casesOf(Fixture fixture) {
    try {
      return cases(fixture.tree());
    } catch (RuntimeException e) {
      throw new IllegalStateException(fixture.file() + ": " + e.getMessage(), e);
    }
  }

  /**
   * The cases of one fixture, with the header (document, configuration, comparison). The fixture is
   * expected to have no problems; a malformed comparison is an error that names it.
   */
  public static List<GoldenCase> cases(JsonNode fixture) {
    JsonNode document = fixture.path("document");
    GoldenCase.Configuration configuration =
        new GoldenCase.Configuration(
            text(fixture.path("configuration"), "file"),
            text(fixture.path("configuration"), "sha256"));
    Map<String, GoldenCase.Comparison> comparison = comparison(fixture);
    return fixture
        .path("cases")
        .valueStream()
        .map(node ->
            new GoldenCase(
                text(node, "id"),
                strings(node.path("rules")),
                text(node, "description"),
                source(document, node.path("row")),
                strings(node.path("assumptions")),
                inferredInputs(node.path("inferred_inputs")),
                strings(node.path("depends_on")),
                node.path("input"),
                node.path("expected"),
                comparison,
                configuration))
        .toList();
  }

  private static GoldenCase.Source source(JsonNode document, JsonNode row) {
    return new GoldenCase.Source(
        text(document, "code"),
        text(document, "version"),
        text(document, "section"),
        row.isIntegralNumber() ? row.intValue() : null);
  }

  private static List<GoldenCase.InferredInput> inferredInputs(JsonNode list) {
    return list.valueStream()
        .map(input -> new GoldenCase.InferredInput(text(input, "field"), text(input, "reason")))
        .toList();
  }

  private static Map<String, GoldenCase.Comparison> comparison(JsonNode fixture) {
    Map<String, GoldenCase.Comparison> comparison = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> field : fixture.path("comparison").properties()) {
      if (!isValidComparison(field.getValue())) {
        throw new IllegalStateException(
            "comparison." + field.getKey() + " no tiene un scale entero y un rounding válido");
      }
      comparison.put(
          field.getKey(),
          new GoldenCase.Comparison(
              field.getValue().get("scale").intValue(),
              RoundingMode.valueOf(field.getValue().get("rounding").stringValue())));
    }
    return Map.copyOf(comparison);
  }

  /** A non-negative integer scale and a rounding mode that rounds (not {@code UNNECESSARY}). */
  private static boolean isValidComparison(JsonNode spec) {
    JsonNode scale = spec.path("scale");
    JsonNode rounding = spec.path("rounding");
    if (!scale.isIntegralNumber() || scale.intValue() < 0 || !rounding.isString()) {
      return false;
    }
    try {
      return RoundingMode.valueOf(rounding.stringValue()) != RoundingMode.UNNECESSARY;
    } catch (IllegalArgumentException e) {
      return false;
    }
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
    checkComparison(fixture, problems);
    JsonNode cases = fixture.path("cases");
    checkCaseIds(cases, problems);
    Map<String, Integer> scales = scales(fixture);
    for (int index = 0; index < cases.size(); index++) {
      checkCase(cases.get(index), "/cases/" + index, scales, problems);
    }
    scanForDoc05(fixture, "", problems);
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

  private static void checkConfiguration(JsonNode configuration, List<Problem> problems) {
    String file = text(configuration, "file");
    if (file.isBlank()) {
      problems.add(new Problem("/configuration/file", "configuration-required"));
    }
    if (text(configuration, "sha256").isBlank()) {
      problems.add(new Problem("/configuration/sha256", "configuration-required"));
    }
    if (file.isBlank()) {
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
      throw new UncheckedIOException("no se pudo leer " + file, e);
    }
  }

  private static void checkComparison(JsonNode fixture, List<Problem> problems) {
    for (Map.Entry<String, JsonNode> field : fixture.path("comparison").properties()) {
      if (!isValidComparison(field.getValue())) {
        problems.add(new Problem("/comparison/" + escape(field.getKey()), "comparison"));
      }
    }
  }

  private static void checkCaseIds(JsonNode cases, List<Problem> problems) {
    Set<String> ids = new HashSet<>();
    for (int index = 0; index < cases.size(); index++) {
      String id = text(cases.get(index), "id");
      String path = "/cases/" + index + "/id";
      if (id.isEmpty()) {
        problems.add(new Problem(path, "case-id-required"));
      } else if (!ids.add(id)) {
        problems.add(new Problem(path, "unique-id"));
      }
    }
  }

  /**
   * The lists of text hold text, the expected result holds no empty object, and a number under a
   * field with a comparison has no more decimals than its scale.
   */
  private static void checkCase(
      JsonNode testCase, String path, Map<String, Integer> scales, List<Problem> problems) {
    for (String list : TEXT_LISTS) {
      JsonNode values = testCase.path(list);
      for (int index = 0; index < values.size(); index++) {
        if (!values.get(index).isString()) {
          problems.add(new Problem(path + "/" + list + "/" + index, "not-text"));
        }
      }
    }
    checkNoEmptyObjects(testCase.path("expected"), path + "/expected", problems);
    checkScale(testCase.path("expected"), path + "/expected", "", scales, problems);
  }

  /** An empty object expects nothing, so it would accept any result. */
  private static void checkNoEmptyObjects(JsonNode node, String path, List<Problem> problems) {
    if (node.isObject() && node.isEmpty()) {
      problems.add(new Problem(path, "empty-expected"));
    }
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        checkNoEmptyObjects(field.getValue(), path + "/" + escape(field.getKey()), problems);
      }
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        checkNoEmptyObjects(node.get(index), path + "/" + index, problems);
      }
    }
  }

  /** The scale of every valid entry of the comparison, by field name. */
  private static Map<String, Integer> scales(JsonNode fixture) {
    Map<String, Integer> scales = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> field : fixture.path("comparison").properties()) {
      if (isValidComparison(field.getValue())) {
        scales.put(field.getKey(), field.getValue().get("scale").intValue());
      }
    }
    return scales;
  }

  private static void checkScale(
      JsonNode node,
      String path,
      String field,
      Map<String, Integer> scales,
      List<Problem> problems) {
    if (node.isObject()) {
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        checkScale(
            entry.getValue(),
            path + "/" + escape(entry.getKey()),
            entry.getKey(),
            scales,
            problems);
      }
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

  private static void scanForDoc05(JsonNode node, String path, List<Problem> problems) {
    if (node.isString() && DOC_05.matcher(node.stringValue()).find()) {
      problems.add(new Problem(path, "no-doc-05"));
    } else if (node.isObject()) {
      for (Map.Entry<String, JsonNode> field : node.properties()) {
        String child = path + "/" + escape(field.getKey());
        if (DOC_05.matcher(field.getKey()).find()) {
          problems.add(new Problem(child, "no-doc-05"));
        }
        scanForDoc05(field.getValue(), child, problems);
      }
    } else if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        scanForDoc05(node.get(index), path + "/" + index, problems);
      }
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

  /** A key as a JSON Pointer token (RFC 6901). */
  static String escape(String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }

  /** The entries of a folder, sorted; it fails with the absolute path if the folder is missing. */
  private static List<Path> entries(Path folder) {
    if (!Files.isDirectory(folder)) {
      throw new IllegalStateException(
          "no está la carpeta "
              + folder.toAbsolutePath()
              + " (¿corre fuera de Maven? basedir="
              + System.getProperty("basedir")
              + ")");
    }
    try (Stream<Path> entries = Files.list(folder)) {
      return entries.sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode load(Path file) {
    try {
      return SafeYamlLoader.load(file);
    } catch (IOException | RuntimeException e) {
      throw new IllegalStateException("no se pudo cargar " + file + ": " + e.getMessage(), e);
    }
  }

  /** The text under a key; a missing or non-text value reads as empty. */
  private static String text(JsonNode node, String key) {
    JsonNode value = node.path(key);
    return value.isString() ? value.stringValue() : "";
  }

  /** The elements that are text; the others are reported by {@code checkCase}. */
  private static List<String> strings(JsonNode list) {
    return list.valueStream().filter(JsonNode::isString).map(JsonNode::stringValue).toList();
  }
}
