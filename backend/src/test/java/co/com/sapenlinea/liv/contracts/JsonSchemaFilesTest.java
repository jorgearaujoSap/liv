package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * Meta-test of every JSON Schema in {@code contracts/jsonschema/<name>/} (feature 004, plan step 1;
 * ADR 0032): the file names, the dialect and the identifier, and the portable subset of Draft
 * 2020-12. It is also the gate of 004-11: the schema fixes no tax value, because its only numeric
 * bounds are those of the convention, and {@code const}, {@code enum}, {@code pattern} and the
 * bounds appear only where the plan puts them. A new version that needs another keyword, pattern or
 * place changes these lists in the same review.
 */
@DisplayName("Archivos de JSON Schema de los contratos (004-11)")
class JsonSchemaFilesTest {

  private static final Path ROOT = CommonCases.JSON_SCHEMAS;
  private static final Pattern SCHEMA_FILE =
      Pattern.compile("(" + CommonCases.VERSION.pattern() + ")\\.json");
  private static final Pattern CASES_FILE =
      Pattern.compile("(" + CommonCases.VERSION.pattern() + ")\\.casos\\.json");
  private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

  /** The only keywords of the portable subset (no format, $dynamicRef, unevaluated*, default…). */
  private static final Set<String> KEYWORDS =
      Set.of(
          "$schema",
          "$id",
          "$comment",
          "$defs",
          "$ref",
          "title",
          "description",
          "type",
          "properties",
          "additionalProperties",
          "required",
          "items",
          "anyOf",
          "enum",
          "const",
          "pattern",
          "minLength",
          "minimum",
          "maximum",
          "exclusiveMinimum");

  /** A subschema says what it accepts with at least one of these. */
  private static final Set<String> CONSTRAINTS = Set.of("type", "$ref", "anyOf", "enum", "const");

  /**
   * The end of the text that every engine reads the same way. With {@code $}, Python ({@code
   * re.search}) and ECMA accept a final line break; networknt turns {@code $} into {@code \z}, so
   * in Java this list, and not the behaviour, is what keeps the patterns portable.
   */
  private static final String END = "(?![\\s\\S])";

  /** Text without control characters or line breaks, which the loader also rejects. */
  private static final String NO_CONTROL = "[^\\u0000-\\u001F\\u007F-\\u009F\\u2028\\u2029]";

  /** The text classes of the plan ("Clases de texto", "Fechas", "DANE", "PENDIENTE"). */
  private static final Set<String> PATTERNS =
      Set.of(
          "^(?!(PENDIENTE|POR_CONFIRMAR)" + END + ")[A-Z][A-Z0-9_]*" + END,
          "^[a-z][a-z0-9_]*" + END,
          "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+" + END,
          "^(?!PENDIENTE" + END + ")" + NO_CONTROL + "+" + END,
          "^PENDIENTE" + END,
          "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])" + END,
          "^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])" + END,
          "^[0-9]{2}([0-9]{3})?" + END,
          "^[0-9]{2}" + END,
          "^[0-9]{8}" + END);

  private static final String CALENDAR = "/properties/calendario/properties/vehiculos_nuevos";

  /**
   * Where enum may appear in each version: estado and the closed lists of 004-05, by their exact
   * path. A version without an entry fails, and never uses the places of another version.
   */
  private static final Map<String, Set<String>> ENUM_PATHS =
      Map.of(
          "1.0",
          Set.of(
              "/$defs/estado",
              "/properties/jurisdiccion/properties/tipo",
              "/properties/base_gravable/properties/vehiculo_nuevo/properties/fuente_base",
              "/properties/beneficios/items/properties/tipo",
              "/properties/redondeo/properties/metodo",
              CALENDAR + "/properties/plazo_pago_sin_intereses/properties/modo",
              CALENDAR + "/properties/descuento/properties/modo",
              "/properties/recaudo/properties/descuento_pago_parcial"));

  /** An enum value is a code, never a number or a tax value. */
  private static final Pattern ENUM_VALUE = Pattern.compile("[A-Z][A-Z0-9_]*");

  /**
   * The numeric bounds of the convention (004-11), each with the only places where it may appear: 0
   * and 1 for fractions, more than 0 for factors, 0 for the rest, and 1 as the minimum length.
   */
  private static final Map<String, Bound> BOUNDS =
      Map.of(
          "minimum", new Bound(BigDecimal.ZERO, null),
          "maximum", new Bound(BigDecimal.ONE, Set.of("/$defs/fraccion/anyOf/0")),
          "exclusiveMinimum", new Bound(BigDecimal.ZERO, Set.of("/$defs/factor")),
          "minLength", new Bound(BigDecimal.ONE, null));

  /** The only value of a bound and, if not null, the only places where it may appear. */
  private record Bound(BigDecimal value, Set<String> places) {}

  static Stream<Path> schemas() throws IOException {
    List<Path> schemas = new ArrayList<>();
    try (Stream<Path> folders = Files.list(ROOT)) {
      for (Path folder : folders.toList()) {
        try (Stream<Path> files = Files.list(folder)) {
          files.filter(f -> SCHEMA_FILE.matcher(name(f)).matches()).forEach(schemas::add);
        }
      }
    }
    assertThat(schemas).isNotEmpty();
    return schemas.stream();
  }

  @Test
  @DisplayName(
      "Cada archivo es <MAYOR.MENOR>.json o <MAYOR.MENOR>.casos.json, sin enlaces,"
          + " y cada esquema tiene sus casos")
  void fileNames() throws IOException {
    try (Stream<Path> folders = Files.list(ROOT)) {
      for (Path folder : folders.toList()) {
        assertThat(folder).isDirectory();
        assertThat(name(folder)).matches("[a-z][a-z0-9-]*");
        Set<String> schemaVersions = new TreeSet<>();
        Set<String> caseVersions = new TreeSet<>();
        try (Stream<Path> files = Files.list(folder)) {
          for (Path file : files.toList()) {
            assertThat(Files.isSymbolicLink(file)).as("%s es un enlace", file).isFalse();
            Matcher schema = SCHEMA_FILE.matcher(name(file));
            Matcher cases = CASES_FILE.matcher(name(file));
            if (schema.matches()) {
              schemaVersions.add(schema.group(1));
            } else if (cases.matches()) {
              caseVersions.add(cases.group(1));
            } else {
              throw new AssertionError(file + " no sigue la forma <MAYOR.MENOR>[.casos].json");
            }
          }
        }
        assertThat(caseVersions).isEqualTo(schemaVersions);
      }
    }
  }

  @ParameterizedTest(name = "{0} es válido contra el metaesquema de Draft 2020-12")
  @MethodSource("schemas")
  void validAgainstTheMetaSchema(Path file) {
    JsonNode schema = read(file);

    assertThat(JurisdictionConfigSchemas.metaSchemaErrors(schema)).isEmpty();
    assertThat(schema.get("$schema").stringValue()).isEqualTo(DIALECT);
  }

  @ParameterizedTest(name = "{0}: su $id, su esquema_version y sus casos nombran carpeta y versión")
  @MethodSource("schemas")
  void identity(Path file) {
    String folder = name(file.getParent());
    String version = version(file);
    JsonNode schema = read(file);
    JsonNode cases = read(file.resolveSibling(version + ".casos.json"));

    assertThat(schema.get("$id").stringValue())
        .isEqualTo("urn:liv:jsonschema:" + folder + ":" + version);
    assertThat(schema.at("/properties/esquema_version/const").stringValue()).isEqualTo(version);
    assertThat(schema.at("/properties/esquema_version/type").stringValue()).isEqualTo("string");
    assertThat(schema.get("required").valueStream().map(JsonNode::stringValue))
        .contains("esquema_version");
    assertThat(cases.get("esquema").stringValue()).isEqualTo(folder);
    assertThat(cases.get("version").stringValue()).isEqualTo(version);
  }

  @ParameterizedTest(
      name = "004-11 · {0} solo usa el subconjunto portable y no fija valores tributarios")
  @MethodSource("schemas")
  @Tag("004-11")
  void portableSubsetWithoutTaxValues(Path file) {
    String version = version(file);
    Set<String> enumPaths = ENUM_PATHS.get(version);
    assertThat(enumPaths).as("ENUM_PATHS de la versión %s", version).isNotNull();
    List<String> problems = new ArrayList<>();
    walk(read(file), "", enumPaths, problems);

    assertThat(problems).isEmpty();
  }

  @Test
  @DisplayName("004-11 · el validador no carga esquemas remotos (ADR 0032)")
  void noRemoteSchemas() {
    JsonNode schema =
        CommonCases.EXACT.readTree(
            "{\"$schema\": \"" + DIALECT + "\", \"$ref\": \"http://127.0.0.1:9/otro.json\"}");

    // With remote loading on, it tries to connect and fails with a SocketException; here it never
    // connects.
    assertThatThrownBy(() ->
            JurisdictionConfigSchemas.compile(schema).validate(CommonCases.EXACT.readTree("{}")))
        .isInstanceOf(RuntimeException.class)
        .satisfies(e -> {
          for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            assertThat(cause).isNotInstanceOf(SocketException.class);
          }
        });
  }

  @Test
  @DisplayName("004-11 · jackson-dataformat-yaml no está en el classpath de las pruebas (ADR 0032)")
  void noJacksonYaml() {
    // With it, Spring would register an application/yaml converter without the loader's rules.
    assertThatThrownBy(() -> Class.forName("tools.jackson.dataformat.yaml.YAMLMapper"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  /** Checks every schema position of the document and goes down into its subschemas. */
  private static void walk(
      JsonNode schema, String path, Set<String> enumPaths, List<String> problems) {
    if (!schema.isObject()) {
      problems.add(path + ": un esquema debe ser un objeto");
      return;
    }
    checkKeywords(schema, path, problems);
    checkObjectsAndPatterns(schema, path, problems);
    checkPlaces(schema, path, enumPaths, problems);
    for (String container : List.of("properties", "$defs")) {
      if (schema.has(container)) {
        schema
            .get(container)
            .properties()
            .forEach(e ->
                walk(
                    e.getValue(),
                    path + "/" + container + "/" + JurisdictionConfigSchemas.escape(e.getKey()),
                    enumPaths,
                    problems));
      }
    }
    if (schema.has("items")) {
      walk(schema.get("items"), path + "/items", enumPaths, problems);
    }
    if (schema.path("additionalProperties").isObject()) {
      walk(schema.get("additionalProperties"), path + "/additionalProperties", enumPaths, problems);
    }
    if (schema.has("anyOf")) {
      for (int i = 0; i < schema.get("anyOf").size(); i++) {
        walk(schema.get("anyOf").get(i), path + "/anyOf/" + i, enumPaths, problems);
      }
    }
  }

  /** Only keywords of the subset, a single type, a constraint and bounds of the convention. */
  private static void checkKeywords(JsonNode schema, String path, List<String> problems) {
    for (Map.Entry<String, JsonNode> entry : schema.properties()) {
      String keyword = entry.getKey();
      if (!KEYWORDS.contains(keyword)) {
        problems.add(path + ": palabra clave fuera del subconjunto: " + keyword);
      }
      Bound bound = BOUNDS.get(keyword);
      JsonNode value = entry.getValue();
      if (bound != null
          && (!value.isNumber()
              || bound.value().compareTo(value.decimalValue()) != 0
              || (bound.places() != null && !bound.places().contains(path)))) {
        problems.add(path + ": límite " + keyword + " = " + value + " fuera de la convención");
      }
    }
    if (schema.has("type") && !schema.get("type").isString()) {
      problems.add(path + ": type debe ser un solo tipo");
    }
    if (!path.isEmpty() && CONSTRAINTS.stream().noneMatch(schema::has)) {
      problems.add(path + ": un subesquema sin type, $ref, anyOf, enum ni const lo acepta todo");
    }
  }

  /** Every object is strict, and every pattern is a text class of the plan, on a string. */
  private static void checkObjectsAndPatterns(JsonNode schema, String path, List<String> problems) {
    if ((schema.has("properties") || isType(schema, "object"))
        && !(schema.path("additionalProperties").isBoolean()
            && !schema.get("additionalProperties").booleanValue())) {
      problems.add(path + ": un objeto sin additionalProperties: false");
    }
    if (schema.has("pattern")) {
      if (!isType(schema, "string")) {
        problems.add(path + ": pattern sin type: string");
      }
      if (!PATTERNS.contains(schema.get("pattern").stringValue())) {
        problems.add(path + ": pattern fuera de las clases del plan: " + schema.get("pattern"));
      }
    }
  }

  /** $ref, const, enum, $id and $schema only where the plan puts them. */
  private static void checkPlaces(
      JsonNode schema, String path, Set<String> enumPaths, List<String> problems) {
    if (schema.has("$ref") && !schema.get("$ref").stringValue().startsWith("#/$defs/")) {
      problems.add(path + ": $ref externo: " + schema.get("$ref"));
    }
    if (schema.has("const") && !path.equals("/properties/esquema_version")) {
      problems.add(path + ": const fuera de esquema_version");
    }
    if (schema.has("enum")) {
      if (!enumPaths.contains(path)) {
        problems.add(path + ": enum fuera de estado y de las listas cerradas de 004-05");
      }
      schema.get("enum").values().stream()
          .filter(v -> !v.isString() || !ENUM_VALUE.matcher(v.stringValue()).matches())
          .forEach(v -> problems.add(path + ": valor de enum que no es un código: " + v));
    }
    if (!path.isEmpty() && (schema.has("$id") || schema.has("$schema"))) {
      problems.add(path + ": $id o $schema fuera de la raíz");
    }
  }

  private static boolean isType(JsonNode schema, String type) {
    return schema.path("type").isString() && schema.get("type").stringValue().equals(type);
  }

  private static JsonNode read(Path file) {
    return CommonCases.EXACT.readTree(file.toFile());
  }

  private static String version(Path file) {
    Matcher matcher = SCHEMA_FILE.matcher(name(file));
    if (!matcher.matches()) {
      throw new IllegalArgumentException(file + " no es un esquema <MAYOR.MENOR>.json");
    }
    return matcher.group(1);
  }

  private static String name(Path path) {
    return path.getFileName().toString();
  }
}
