package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.contracts.SafeYamlLoader.Reason;
import co.com.sapenlinea.liv.contracts.SafeYamlLoader.UnsafeYamlException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The portable YAML subset of the jurisdiction configuration (feature 004, plan "Subconjunto
 * portable de YAML"; ADR 0032), run over the load cases of every {@code <version>.casos.json},
 * which the root validator also runs. The loader is one for all versions (ADR 0034), so it has to
 * pass the cases of all of them. The rules and their order are in the {@code $comment} of 1.0.
 */
@Tag("004-10")
@DisplayName("Carga segura del YAML de la configuración de jurisdicción (004-10)")
class SafeYamlLoaderTest {

  static Stream<Arguments> accepted() {
    return cases(true);
  }

  static Stream<Arguments> rejected() {
    return cases(false);
  }

  private static Stream<Arguments> cases(boolean accepted) {
    return CommonCases.cases("carga", c -> c.get("aceptado").booleanValue() == accepted);
  }

  /** The YAML of a case: its text, repeated when the case says so (a file too big to write). */
  private static String yaml(JsonNode testCase) {
    int times = testCase.has("repetir") ? testCase.get("repetir").intValue() : 1;
    return testCase.get("yaml").stringValue().repeat(times);
  }

  @ParameterizedTest(name = "004-10 · [{0}] se acepta: {1}")
  @MethodSource("accepted")
  void acceptsThePortableSubset(String version, String name, JsonNode testCase) {
    JsonNode tree = SafeYamlLoader.load(yaml(testCase));

    // A repeated case has no tree: it is too big to write, and only its acceptance counts.
    if (testCase.has("arbol")) {
      assertThat(tree).isEqualTo(testCase.get("arbol"));
    }
  }

  @ParameterizedTest(name = "004-10 · [{0}] se rechaza: {1}")
  @MethodSource("rejected")
  void rejectsEverythingElse(String version, String name, JsonNode testCase) {
    String yaml = yaml(testCase);

    assertThatThrownBy(() -> SafeYamlLoader.load(yaml))
        .isInstanceOf(UnsafeYamlException.class)
        .satisfies(e -> {
          assertThat(((UnsafeYamlException) e).reason().name())
              .isEqualTo(testCase.get("motivo").stringValue());
          // A fragment of the YAML in the message can never start a line of a log.
          assertThat(e.getMessage()).doesNotContain("\n", "\r", "\u0085", "\u2028");
        });
  }

  @Test
  @DisplayName("004-10 · un archivo de más de 1 MiB se rechaza antes de leerlo y de decodificarlo")
  void sizeBeforeEncoding(@TempDir Path folder) throws Exception {
    Path file = folder.resolve("grande.yaml");
    byte[] bytes = new byte[SafeYamlLoader.MAX_BYTES + 1];
    Arrays.fill(bytes, (byte) 0xFF);
    Files.write(file, bytes);

    assertThatThrownBy(() -> SafeYamlLoader.load(file))
        .isInstanceOf(UnsafeYamlException.class)
        .extracting(e -> ((UnsafeYamlException) e).reason())
        .isEqualTo(Reason.TAMANO);
  }

  @Test
  @DisplayName("004-10 · un archivo que no es UTF-8 válido se rechaza con CODIFICACION")
  void invalidUtf8(@TempDir Path folder) throws Exception {
    Path file = folder.resolve("config.yaml");
    Files.write(file, new byte[] {'a', ':', ' ', (byte) 0xC3, (byte) 0x28, '\n'});

    assertThatThrownBy(() -> SafeYamlLoader.load(file))
        .isInstanceOf(UnsafeYamlException.class)
        .extracting(e -> ((UnsafeYamlException) e).reason())
        .isEqualTo(Reason.CODIFICACION);
  }

  @Test
  @DisplayName("004-10 · el mensaje de un error de sintaxis ocupa una sola línea")
  void syntaxErrorInOneLine() {
    assertThatThrownBy(() -> SafeYamlLoader.load("a: [1, 2\n"))
        .isInstanceOf(UnsafeYamlException.class)
        .extracting(Throwable::getMessage)
        .asString()
        .doesNotContain("\n", "\r");
  }
}
