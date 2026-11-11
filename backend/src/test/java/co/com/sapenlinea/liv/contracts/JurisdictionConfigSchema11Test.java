package co.com.sapenlinea.liv.contracts;

import static co.com.sapenlinea.liv.contracts.JurisdictionConfigSchemas.errors;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.IPIALES;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.TEMPLATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The jurisdiction configuration schema 1.1 (feature 005, plan step 7) against what it promises:
 * the template and Ipiales of the 1.1 are valid (CJ01), the cases of the 1.0 do not change when the
 * 1.1 is published (P18, ADR 0034) and everything that is valid in the 1.0 is valid in the 1.1
 * (005-18). The mutation of every node of the 1.1 and the cases of {@code 1.1.casos.json} are in
 * {@link JurisdictionConfigSchemaMutationTest} and {@link JurisdictionConfigCommonCasesTest}.
 */
@DisplayName("Esquema 1.1 de la configuración de jurisdicción (feature 005)")
class JurisdictionConfigSchema11Test {

  private static final String OLD = "1.0";
  private static final String NEW = "1.1";
  private static final String VERSION_PATH = JurisdictionConfigSchemas.VERSION_PATH;
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private static final String FORMATO = "/recaudo/archivo_banco/formato";

  /**
   * The cases of the 1.0 that 005-18 leaves out because they are about the version itself or have
   * no object as root: unknown version, numeric version, PENDIENTE version, no version and a list
   * as root. Counted, so that a change of the filter cannot leave out more in silence.
   */
  private static final int EXCLUDED_CASES = 5;

  /**
   * The mutations of the 1.0 that 005-18 leaves out because they change the version itself:
   * removing it and writing null in it, in each configuration.
   */
  private static final int EXCLUDED_MUTATIONS = 2 * TestConfigurations.ALL.size();

  @Test
  @Tag("CJ01")
  @DisplayName("CJ01 · la plantilla y la configuración de Ipiales 1.1 son válidas")
  void bothConfigurationsAreValid() {
    assertThat(errors(TestConfigurations.load(NEW, TEMPLATE), NEW)).isEmpty();
    assertThat(errors(TestConfigurations.load(NEW, IPIALES), NEW)).isEmpty();
  }

  @Test
  @Tag("P18")
  @DisplayName(
      "P18 · los casos de la 1.0 dan lo mismo con la 1.1 publicada: «versión desconocida» sigue"
          + " dando su error")
  void unknownVersionCaseOfThe10KeepsItsError() {
    JsonNode unknown = null;
    for (JsonNode testCase : CommonCases.load(OLD).get("validacion")) {
      if (testCase.get("caso").stringValue().equals("versión desconocida")) {
        unknown = testCase;
      }
    }
    assertThat(unknown).as("el caso «versión desconocida» de 1.0.casos.json").isNotNull();
    JsonNode configuration = SafeYamlLoader.load(unknown.get("yaml").stringValue());
    assertThat(configuration.get("esquema_version").stringValue()).isEqualTo(NEW);

    // With every published version, the 1.1 is known and the configuration is valid.
    assertThat(JurisdictionConfigSchemas.forVersion(NEW)).isPresent();
    assertThat(errors(configuration)).isEmpty();
    // The cases of the 1.0 run with the versions up to the 1.0 (ADR 0034), so it is still unknown.
    assertThat(errors(configuration, OLD)).containsExactly(VERSION_PATH);
  }

  @Test
  @Tag("005-18")
  @DisplayName("005-18 · archivo_banco.formato es obligatorio en la 1.0 y opcional en la 1.1")
  void formatoIsOptionalOnlyInThe11() {
    for (String name : TestConfigurations.ALL) {
      ObjectNode old = TestConfigurations.remove(TestConfigurations.load(OLD, name), FORMATO);
      assertThat(errors(old, OLD)).as("1.0 · %s", name).containsExactly(FORMATO);

      assertThat(errors(withVersion(old, NEW))).as("1.1 · %s", name).isEmpty();
    }
  }

  static Stream<Arguments> mutationsOfThe10() {
    List<Arguments> kept = new ArrayList<>();
    int excluded = 0;
    for (Arguments arguments : JurisdictionConfigSchemaMutationTest.mutations().toList()) {
      // The arguments of a mutation: version, description, configuration, change, expected errors.
      Object[] mutation = arguments.get();
      String version = (String) mutation[0];
      String description = (String) mutation[1];
      if (!OLD.equals(version)) {
        continue;
      }
      if (description.contains("/esquema_version")) {
        excluded++;
        continue;
      }
      kept.add(Arguments.of(description, mutation[2], mutation[3], mutation[4]));
    }
    assertThat(excluded).isEqualTo(EXCLUDED_MUTATIONS);
    return kept.stream();
  }

  @ParameterizedTest(name = "005-18 · {0}")
  @MethodSource("mutationsOfThe10")
  @Tag("005-18")
  void whatIsValidInThe10IsValidInThe11(
      String description,
      String configuration,
      UnaryOperator<ObjectNode> change,
      Set<String> expected) {
    // The version first, then the mutation: the mutation of a node writes its value over the 1.1.
    ObjectNode changed =
        change.apply(withVersion(TestConfigurations.load(OLD, configuration), NEW));

    assertThat(errors(changed)).isEqualTo(expectedIn11(description, expected));
  }

  /**
   * The errors that the same mutation gives in the 1.1. They are the ones of the 1.0 except for the
   * only key that the 1.0 requires and the 1.1 does not: removing {@code archivo_banco.formato}.
   */
  private static Set<String> expectedIn11(String description, Set<String> expectedIn10) {
    if (!description.contains("sin " + FORMATO + " (obligatoria)")) {
      return expectedIn10;
    }
    assertThat(expectedIn10).containsExactly(FORMATO);
    return Set.of();
  }

  private static ObjectNode withVersion(ObjectNode configuration, String version) {
    return TestConfigurations.set(configuration, "/esquema_version", NODES.stringNode(version));
  }

  static Stream<Arguments> casesOfThe10() {
    List<Arguments> kept = new ArrayList<>();
    int excluded = 0;
    for (Arguments arguments : CommonCases.cases("validacion").toList()) {
      Object[] row = arguments.get();
      if (!OLD.equals(row[0])) {
        continue;
      }
      JsonNode testCase = (JsonNode) row[2];
      JsonNode tree =
          testCase.has("arbol")
              ? testCase.get("arbol")
              : SafeYamlLoader.load(testCase.get("yaml").stringValue());
      Set<String> expected = new TreeSet<>();
      if (!testCase.get("valido").booleanValue()) {
        testCase.get("rutas").values().forEach(r -> expected.add(r.stringValue()));
      }
      boolean aboutTheVersion = expected.contains(VERSION_PATH);
      if (!tree.isObject() || aboutTheVersion) {
        excluded++;
        continue;
      }
      kept.add(Arguments.of(row[1], tree, expected));
    }
    assertThat(excluded).isEqualTo(EXCLUDED_CASES);
    return kept.stream();
  }

  @ParameterizedTest(name = "005-18 · caso de la 1.0 con la versión 1.1: {0}")
  @MethodSource("casesOfThe10")
  @Tag("005-18")
  void casesOfThe10GiveTheSameInThe11(String name, JsonNode tree, Set<String> expected) {
    ObjectNode configuration = ((ObjectNode) tree).deepCopy();
    assertThat(configuration.get("esquema_version").stringValue()).isEqualTo(OLD);

    assertThat(errors(withVersion(configuration, NEW))).isEqualTo(expected);
  }
}
