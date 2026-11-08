package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
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
 * 2020-12. It also proves 004-11: the schema fixes no tax value, because its only numeric bounds
 * are those of the convention, and {@code const}, {@code enum} and {@code pattern} appear only
 * where the plan puts them.
 */
@DisplayName("Archivos de JSON Schema de los contratos (004-11)")
class JsonSchemaFilesTest {

  private static final Path ROOT = Path.of("contracts", "jsonschema");
  private static final Pattern SCHEMA_FILE = Pattern.compile("([0-9]+\\.[0-9]+)\\.json");
  private static final Pattern CASES_FILE = Pattern.compile("([0-9]+\\.[0-9]+)\\.casos\\.json");
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

  /** The text classes of the plan ("Clases de texto", "Fechas", "DANE", "PENDIENTE"). */
  private static final Set<String> PATTERNS =
      Set.of(
          "^(?!(PENDIENTE|POR_CONFIRMAR)$)[A-Z][A-Z0-9_]*$",
          "^[a-z][a-z0-9_]*$",
          "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$",
          "^(?!PENDIENTE$).+$",
          "^PENDIENTE$",
          "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
          "^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
          "^[0-9]{2}([0-9]{3})?$",
          "^[0-9]{2}$",
          "^[0-9]{8}$");

  /** Where enum may appear: estado and the closed lists of 004-05, by their exact path. */
  private static final Set<String> ENUM_PATHS =
      Set.of(
          "/$defs/estado",
          "/properties/jurisdiccion/properties/tipo",
          "/properties/base_gravable/properties/vehiculo_nuevo/properties/fuente_base",
          "/properties/beneficios/items/properties/tipo",
          "/properties/redondeo/properties/metodo",
          "/properties/calendario/properties/vehiculos_nuevos/properties/plazo_pago_sin_intereses/properties/modo",
          "/properties/calendario/properties/vehiculos_nuevos/properties/descuento/properties/modo",
          "/properties/recaudo/properties/descuento_pago_parcial");

  /** The numeric bounds of the convention (004-11): 0 and 1 for fractions, 0 for the rest. */
  private static final Map<String, Set<BigDecimal>> BOUNDS =
      Map.of(
          "minimum", Set.of(BigDecimal.ZERO),
          "exclusiveMinimum", Set.of(BigDecimal.ZERO),
          "maximum", Set.of(BigDecimal.ONE),
          "minLength", Set.of(BigDecimal.ONE));

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
      "004-12 · cada archivo es <MAYOR.MENOR>.json o <MAYOR.MENOR>.casos.json, y cada esquema tiene sus casos")
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

  @ParameterizedTest(
      name = "{0}: su $id, su esquema_version y sus casos nombran la carpeta y la versión")
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
    List<String> problems = new ArrayList<>();
    check(read(file), "", problems);

    assertThat(problems).isEmpty();
  }

  /** Walks every schema position of the document and writes what breaks the rules. */
  private static void check(JsonNode schema, String path, List<String> problems) {
    if (!schema.isObject()) {
      problems.add(path + ": un esquema debe ser un objeto");
      return;
    }
    for (Map.Entry<String, JsonNode> entry : schema.properties()) {
      String keyword = entry.getKey();
      JsonNode value = entry.getValue();
      if (!KEYWORDS.contains(keyword)) {
        problems.add(path + ": palabra clave fuera del subconjunto: " + keyword);
      }
      if (BOUNDS.containsKey(keyword)
          && !(value.isNumber()
              && BOUNDS.get(keyword).stream()
                  .anyMatch(b -> b.compareTo(value.decimalValue()) == 0))) {
        problems.add(path + ": límite " + keyword + " = " + value);
      }
    }
    if ((schema.has("properties") || isType(schema, "object"))
        && !(schema.has("additionalProperties")
            && schema.get("additionalProperties").isBoolean()
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
    if (schema.has("$ref") && !schema.get("$ref").stringValue().startsWith("#/$defs/")) {
      problems.add(path + ": $ref externo: " + schema.get("$ref"));
    }
    if (schema.has("const") && !path.equals("/properties/esquema_version")) {
      problems.add(path + ": const fuera de esquema_version");
    }
    if (schema.has("enum") && !ENUM_PATHS.contains(path)) {
      problems.add(path + ": enum fuera de estado y de las listas cerradas de 004-05");
    }
    for (String container : List.of("properties", "$defs")) {
      if (schema.has(container)) {
        schema
            .get(container)
            .properties()
            .forEach(e ->
                check(
                    e.getValue(),
                    path + "/" + container + "/" + JurisdictionConfigSchemas.escape(e.getKey()),
                    problems));
      }
    }
    if (schema.has("items")) {
      check(schema.get("items"), path + "/items", problems);
    }
    if (schema.has("anyOf")) {
      for (int i = 0; i < schema.get("anyOf").size(); i++) {
        check(schema.get("anyOf").get(i), path + "/anyOf/" + i, problems);
      }
    }
  }

  private static boolean isType(JsonNode schema, String type) {
    return schema.has("type")
        && schema.get("type").isString()
        && schema.get("type").stringValue().equals(type);
  }

  private static JsonNode read(Path file) {
    return CommonCases.EXACT.readTree(file.toFile());
  }

  private static String version(Path file) {
    Matcher matcher = SCHEMA_FILE.matcher(name(file));
    assertThat(matcher.matches()).isTrue();
    return matcher.group(1);
  }

  private static String name(Path path) {
    return path.getFileName().toString();
  }
}
