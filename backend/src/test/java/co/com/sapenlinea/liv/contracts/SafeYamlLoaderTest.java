package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.contracts.SafeYamlLoader.UnsafeYamlException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The portable YAML subset of the jurisdiction configuration (feature 004, plan "Subconjunto
 * portable de YAML"; ADR 0032), run over the load cases of {@code 1.0.casos.json}, which the root
 * validator also runs.
 */
@Tag("004-10")
@DisplayName("Carga segura del YAML de la configuración de jurisdicción (004-10)")
class SafeYamlLoaderTest {

  static Stream<Arguments> accepted() {
    return CommonCases.load("1.0")
        .withArray("carga")
        .valueStream()
        .filter(c -> c.get("aceptado").booleanValue())
        .map(c -> Arguments.of(c.get("caso").stringValue(), c));
  }

  static Stream<Arguments> rejected() {
    return CommonCases.load("1.0")
        .withArray("carga")
        .valueStream()
        .filter(c -> !c.get("aceptado").booleanValue())
        .map(c -> Arguments.of(c.get("caso").stringValue(), c));
  }

  @ParameterizedTest(name = "004-10 · se acepta: {0}")
  @MethodSource("accepted")
  void acceptsThePortableSubset(String name, JsonNode testCase) {
    JsonNode tree = SafeYamlLoader.load(testCase.get("yaml").stringValue());

    assertThat(tree).isEqualTo(testCase.get("arbol"));
  }

  @ParameterizedTest(name = "004-10 · se rechaza: {0}")
  @MethodSource("rejected")
  void rejectsEverythingElse(String name, JsonNode testCase) {
    String yaml = testCase.get("yaml").stringValue();

    assertThatThrownBy(() -> SafeYamlLoader.load(yaml))
        .isInstanceOf(UnsafeYamlException.class)
        .extracting(e -> ((UnsafeYamlException) e).reason().name())
        .isEqualTo(testCase.get("motivo").stringValue());
  }
}
