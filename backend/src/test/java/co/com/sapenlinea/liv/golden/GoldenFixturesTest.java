package co.com.sapenlinea.liv.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import co.com.sapenlinea.liv.contracts.CommonCases;
import co.com.sapenlinea.liv.contracts.SafeYamlLoader;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Fixture;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Problem;
import com.networknt.schema.Schema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The golden fixtures are complete and well formed (006-05, 006-09, 006-10, 006-11), and the common
 * checks reject a damaged fixture for the right reason. The cases of {@code golden/tax} and {@code
 * golden/ledger} come with the tests of each module; the damaged variants here start from a
 * synthetic valid fixture ({@code golden/test-only}), which is not a golden case.
 */
class GoldenFixturesTest {

  private static final Path TEST_ONLY = GoldenFixtures.GOLDEN.resolve("test-only");
  private static final Path TEST_SCHEMA = TEST_ONLY.resolve("test.schema.json");
  private static final Path VALID = TEST_ONLY.resolve("valid.yaml");

  // The next two constants must match configuration in test-only/valid.yaml.
  private static final String IPIALES_FILE =
      "contracts/jsonschema/config-jurisdiccion/1.1/config_jurisdiccion_52356_2026.yaml";

  /** Doc. 03 section 9.2: the SHA-256 of the copy of Ipiales, with LF. */
  private static final String IPIALES_SHA256 =
      "e038e12a30223ce8944cb2220869b6dc52914c6a8cc90a93c273ec869680c872";

  @Test
  @DisplayName("Cada esquema golden es válido contra el meta-esquema 2020-12 (006-05)")
  void everySchemaIsValidAgainstTheMetaSchema() {
    List<Path> schemas = new ArrayList<>(GoldenFixtures.schemaFiles());
    schemas.add(TEST_SCHEMA);
    assertAll(
        schemas.stream()
            .map(schema ->
                () ->
                    assertThat(GoldenFixtures.metaSchemaProblems(read(schema)))
                        .as(schema.toString())
                        .isEmpty()));
  }

  @Test
  @DisplayName("Un esquema con una palabra clave mal escrita no pasa el meta-esquema (006-05)")
  void aMisspelledKeywordIsFoundByTheMetaSchema() {
    JsonNode schema = CommonCases.EXACT.readTree("{\"type\": \"objet\"}");

    assertThat(GoldenFixtures.metaSchemaProblems(schema)).isNotEmpty();
  }

  @Test
  @DisplayName("Todo fixture de tax y ledger carga, es válido y trae sus campos (006-05, 006-10)")
  void everyFixtureIsValid() {
    for (String module : GoldenFixtures.MODULES) {
      List<Fixture> fixtures = GoldenFixtures.fixtures(module);
      if (fixtures.isEmpty()) {
        continue;
      }
      Schema schema = GoldenFixtures.schema(module);
      assertAll(
          fixtures.stream()
              .map(fixture ->
                  () ->
                      assertThat(GoldenFixtures.problems(schema, fixture.tree()))
                          .as(fixture.file().toString())
                          .isEmpty()));
      assertThat(GoldenFixtures.cases(module))
          .as("casos de " + module)
          .extracting(GoldenCase::id)
          .doesNotHaveDuplicates();
    }
  }

  @Test
  @DisplayName("El fixture sintético válido no tiene problemas y sus casos se leen (006-05)")
  void theValidControlHasNoProblems() {
    JsonNode tree = valid();

    assertThat(GoldenFixtures.problems(GoldenFixtures.schema(TEST_SCHEMA), tree)).isEmpty();

    List<GoldenCase> cases = GoldenFixtures.cases(tree);
    assertThat(cases).extracting(GoldenCase::id).containsExactly("T1", "T2");
    GoldenCase first = cases.get(0);
    assertThat(first.rules()).containsExactly("X-01");
    assertThat(first.source()).isEqualTo(new GoldenCase.Source("Doc. 02", "9.9", "§0.0", 1));
    assertThat(first.assumptions()).containsExactly("SYNTHETIC");
    assertThat(first.dependsOn()).containsExactly("recaudo");
    assertThat(first.configuration())
        .isEqualTo(new GoldenCase.Configuration(IPIALES_FILE, IPIALES_SHA256));
    assertThat(first.comparison()).containsOnlyKeys("scaled_value");
    assertThat(first.expected().get("scaled_value").decimalValue().scale()).isEqualTo(2);
    GoldenCase second = cases.get(1);
    assertThat(second.source().row()).isNull();
    assertThat(second.inferredInputs())
        .containsExactly(new GoldenCase.InferredInput("kind", "motivo sintético"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("damagedVariants")
  @DisplayName("Una variante dañada se rechaza por el motivo correcto (006-05, 006-09, 006-11)")
  void aDamagedVariantIsRejectedForTheRightReason(
      String from, String to, String path, String rule) {
    JsonNode damaged = SafeYamlLoader.load(replaceOnce(validText(), from, to));

    assertThat(GoldenFixtures.problems(GoldenFixtures.schema(TEST_SCHEMA), damaged))
        .contains(new Problem(path, rule));
  }

  static Stream<Arguments> damagedVariants() {
    String sha = "sha256: \"" + IPIALES_SHA256 + "\"";
    return Stream.of(
        variant("un id repetido", "id: \"T2\"", "id: \"T1\"", "/cases/1/id", "unique-id"),
        variant(
            "un SHA-256 distinto",
            sha,
            "sha256: \"" + "0".repeat(64) + "\"",
            "/configuration/sha256",
            "configuration-sha256"),
        variant(
            "una ruta fuera de src/test/resources",
            "file: \"" + IPIALES_FILE + "\"",
            "file: \"../../pom.xml\"",
            "/configuration/file",
            "configuration-path"),
        variant(
            "una ruta absoluta",
            "file: \"" + IPIALES_FILE + "\"",
            "file: \"/etc/hosts\"",
            "/configuration/file",
            "configuration-path"),
        variant(
            "un archivo de configuración que no existe",
            "file: \"" + IPIALES_FILE + "\"",
            "file: \"contracts/no-existe.yaml\"",
            "/configuration/file",
            "configuration-missing"),
        variant(
            "el Doc. 05 como documento",
            "code: \"Doc. 02\"",
            "code: \"Doc. 05\"",
            "/document/code",
            "no-doc-05"),
        variant(
            "el Doc. 05 citado en una descripción",
            "description: \"Caso sintético uno\"",
            "description: \"Según el Doc. 05 §4.3\"",
            "/cases/0/description",
            "no-doc-05"),
        variant(
            "un documento sin versión",
            "  version: \"9.9\"\n",
            "",
            "/document/version",
            "document-version"),
        variant(
            "un documento sin versión (esquema)",
            "  version: \"9.9\"\n",
            "",
            "/document/version",
            "required"),
        variant(
            "una clave desconocida en la cabecera",
            "cases:\n",
            "unknown_key: 1\ncases:\n",
            "/unknown_key",
            "additionalProperties"),
        variant(
            "un valor con más decimales que su comparación",
            "scaled_value: 2500.00",
            "scaled_value: 2500.005",
            "/cases/0/expected/scaled_value",
            "scale"));
  }

  private static Arguments variant(String name, String from, String to, String path, String rule) {
    return Arguments.of(Named.of(name, from), to, path, rule);
  }

  @Test
  @DisplayName("Una ruta que sale de src/test/resources no se lee (006-09)")
  void aPathOutsideTheResourcesIsNotResolved() {
    assertThat(GoldenConfiguration.insideResources(IPIALES_FILE)).isPresent();
    assertAll(
        Stream.of("../pom.xml", "contracts/../../pom.xml", "/etc/hosts", "C:/Windows/win.ini", "")
            .map(path ->
                () -> assertThat(GoldenConfiguration.insideResources(path)).as(path).isEmpty()));
    assertThatThrownBy(() -> GoldenConfiguration.load("../pom.xml"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("El SHA-256 se calcula con fin de línea LF, también en Windows (006-09)")
  void theHashIsComputedWithLf() throws IOException {
    assertThat(GoldenConfiguration.sha256Lf("a\nb\n"))
        .isEqualTo("911169ddaaf146aff539f58c26c489af3b892dff0fe283c1c264c65ae5aa59a2");
    assertThat(GoldenConfiguration.sha256Lf("a\r\nb\n"))
        .isEqualTo(GoldenConfiguration.sha256Lf("a\nb\n"));
    assertThat(GoldenConfiguration.sha256Lf("a\r\nb\r\n"))
        .isEqualTo(GoldenConfiguration.sha256Lf("a\nb\n"));

    String text =
        Files.readString(GoldenConfiguration.insideResources(IPIALES_FILE).orElseThrow())
            .replace("\r\n", "\n");
    assertThat(GoldenConfiguration.sha256Lf(text)).isEqualTo(IPIALES_SHA256);
    assertThat(GoldenConfiguration.sha256Lf(text.replace("\n", "\r\n"))).isEqualTo(IPIALES_SHA256);
  }

  @Test
  @DisplayName("Un id repetido es el único problema de la variante, en el segundo caso (006-05)")
  void everyRepeatedIdIsReported() {
    String text = replaceOnce(validText(), "id: \"T2\"", "id: \"T1\"");
    Set<String> paths = new HashSet<>();
    GoldenFixtures.problems(GoldenFixtures.schema(TEST_SCHEMA), SafeYamlLoader.load(text))
        .forEach(problem -> paths.add(problem.rule() + problem.path()));

    assertThat(paths).containsExactly("unique-id/cases/1/id");
  }

  private static String validText() {
    try {
      // The file has CRLF on Windows with core.autocrlf; the variants use LF.
      return Files.readString(VALID).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode valid() {
    return SafeYamlLoader.load(validText());
  }

  private static JsonNode read(Path file) {
    return CommonCases.EXACT.readTree(file.toFile());
  }

  /** The text with one change; a replacement that finds nothing or finds two fails the test. */
  private static String replaceOnce(String text, String from, String to) {
    int first = text.indexOf(from);
    assertThat(first).as("«" + from + "» está en el fixture válido").isNotNegative();
    assertThat(text.indexOf(from, first + 1)).as("«" + from + "» está una sola vez").isNegative();
    return text.replace(from, to);
  }
}
