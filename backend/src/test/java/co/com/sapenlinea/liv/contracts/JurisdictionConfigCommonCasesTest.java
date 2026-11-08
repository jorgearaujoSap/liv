package co.com.sapenlinea.liv.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

/**
 * The validation cases of {@code 1.0.casos.json} (ADR 0032): each YAML is loaded with the portable
 * subset, its schema is picked by {@code esquema_version} and the error paths must be exactly the
 * expected ones. The root validator runs the same published file.
 */
@Tag("CJ01")
@DisplayName("Casos comunes de validación del esquema 1.0 (CJ01)")
class JurisdictionConfigCommonCasesTest {

  static Stream<Arguments> cases() {
    return CommonCases.load("1.0")
        .withArray("validacion")
        .valueStream()
        .map(c -> Arguments.of(c.get("caso").stringValue(), c));
  }

  @ParameterizedTest(name = "CJ01 · {0}")
  @MethodSource("cases")
  void validation(String name, JsonNode testCase) {
    // A case with "arbol" checks the schema alone, without the loader (e.g. a final line break).
    JsonNode configuration =
        testCase.has("arbol")
            ? testCase.get("arbol")
            : SafeYamlLoader.load(testCase.get("yaml").stringValue());
    Set<String> expected = new TreeSet<>();
    if (!testCase.get("valido").booleanValue()) {
      testCase.get("rutas").values().forEach(r -> expected.add(r.stringValue()));
      assertThat(expected).isNotEmpty();
    }

    assertThat(JurisdictionConfigSchemas.errors(configuration)).isEqualTo(expected);
  }
}
