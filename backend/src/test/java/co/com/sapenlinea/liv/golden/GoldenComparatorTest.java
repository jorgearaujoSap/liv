package co.com.sapenlinea.liv.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.contracts.CommonCases;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.MultipleFailuresError;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The comparison detects a wrong result with synthetic values, before there is any golden case
 * (006-05). The values are not tax values: they only have the shape of a result. The mutations on
 * the real cases are in the tests of each module.
 */
class GoldenComparatorTest {

  /** An expected result with every kind of value: numbers, text, a flag, a date and two lists. */
  private static final String EXPECTED =
      """
      {"amount": 1000, "ratio": 0.125, "months": 7, "scaled_value": 2500.00,
       "applies": true, "kind": "KIND_A", "due_date": "2030-01-15",
       "movements": [
         {"type": "T_ONE", "component": "C_ONE", "amount": 1000},
         {"type": "T_TWO", "component": "C_ONE", "amount": -400, "reverses": "T_ONE"}],
       "codes": ["K1", "K2"]}
      """;

  private static final GoldenCase.Source SOURCE =
      new GoldenCase.Source("Doc. SINTETICO", "0.0", "§0", 1);

  @Test
  @DisplayName("Un resultado igual al esperado pasa (006-05)")
  void anEqualResultPasses() {
    assertThat(GoldenComparator.differences(caseOf(expected()), result(expected()))).isEmpty();
    assertThatCode(() -> GoldenComparator.assertMatches(caseOf(expected()), result(expected())))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName(
      "Solo se comparan las claves esperadas; lo que el resultado agrega no cuenta (006-01)")
  void extraKeysOfTheResultAreNotCompared() {
    ObjectNode actual = expected();
    actual.put("extra", "x");
    ((ObjectNode) actual.get("movements").get(0)).put("extra", "x");

    assertThat(GoldenComparator.differences(caseOf(expected()), result(actual))).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("equalButWrittenDifferently")
  @DisplayName(
      "Un valor igual escrito con otra escala o con redondeo dentro de la escala pasa (006-05)")
  void equalValuesPass(Consumer<ObjectNode> change) {
    ObjectNode actual = expected();
    change.accept(actual);

    assertThat(GoldenComparator.differences(caseOf(expected()), result(actual))).isEmpty();
  }

  static Stream<Arguments> equalButWrittenDifferently() {
    return Stream.of(
        mutation("un monto 1000.00 es 1000", n -> n.set("amount", decimal("1000.00"))),
        mutation("una tasa 0.1250 es 0.125", n -> n.set("ratio", decimal("0.1250"))),
        mutation(
            "el bruto 2500.004 se redondea a 2500.00",
            n -> n.set("scaled_value", decimal("2500.004"))),
        mutation(
            "el bruto 2499.995 sube a 2500.00", n -> n.set("scaled_value", decimal("2499.995"))),
        mutation("el bruto 2500 es 2500.00", n -> n.put("scaled_value", 2500)),
        mutation(
            "los movimientos en otro orden",
            n -> n.set("movements", reversed(n.get("movements")))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failingMutations")
  @DisplayName("Un resultado que difiere en una sola clave falla en esa clave (006-05)")
  void aMutationFails(String path, Consumer<ObjectNode> change) {
    ObjectNode actual = expected();
    change.accept(actual);

    List<String> differences = GoldenComparator.differences(caseOf(expected()), result(actual));

    assertThat(differences).as("diferencias").hasSize(1);
    assertThat(differences.get(0)).startsWith(path + ":");
  }

  static Stream<Arguments> failingMutations() {
    return Stream.of(
        // Amounts in pesos: exact.
        mutationAt("/amount", "monto +1 peso", n -> n.put("amount", 1001)),
        mutationAt("/amount", "monto -1 peso", n -> n.put("amount", 999)),
        mutationAt("/amount", "monto con decimales", n -> n.set("amount", decimal("1000.01"))),
        // The gross: scale 2 with HALF_UP.
        mutationAt("/scaled_value", "bruto +0,01", n -> n.set("scaled_value", decimal("2500.01"))),
        mutationAt("/scaled_value", "bruto -0,01", n -> n.set("scaled_value", decimal("2499.99"))),
        mutationAt(
            "/scaled_value",
            "bruto 2500.005 sube a 2500.01",
            n -> n.set("scaled_value", decimal("2500.005"))),
        // A rate: exact, three decimals matter.
        mutationAt("/ratio", "razón +0,001", n -> n.set("ratio", decimal("0.126"))),
        mutationAt("/ratio", "razón -0,001", n -> n.set("ratio", decimal("0.124"))),
        // A date: one day.
        mutationAt("/due_date", "fecha +1 día", n -> n.put("due_date", "2030-01-16")),
        mutationAt("/due_date", "fecha -1 día", n -> n.put("due_date", "2030-01-14")),
        // An integer that is not an amount.
        mutationAt("/months", "entero +1", n -> n.put("months", 8)),
        mutationAt("/months", "entero -1", n -> n.put("months", 6)),
        // Text, code and flag.
        mutationAt("/kind", "otro código", n -> n.put("kind", "KIND_B")),
        mutationAt("/kind", "un código con otra capitalización", n -> n.put("kind", "kind_a")),
        mutationAt("/kind", "un código con un espacio", n -> n.put("kind", "KIND_A ")),
        mutationAt("/applies", "valor lógico invertido", n -> n.put("applies", false)),
        // Types: a number is not text, and text is not a number.
        mutationAt("/amount", "monto como texto", n -> n.put("amount", "1000")),
        mutationAt("/due_date", "fecha como número", n -> n.put("due_date", 20300115)),
        mutationAt("/applies", "valor lógico como texto", n -> n.put("applies", "true")),
        mutationAt(
            "/ratio",
            "razón como double, no decimal exacto",
            n -> n.set("ratio", DoubleNode.valueOf(0.125))),
        // Nulls and absent keys, for every kind of value.
        mutationAt("/amount", "monto nulo", n -> n.putNull("amount")),
        mutationAt("/scaled_value", "bruto nulo", n -> n.putNull("scaled_value")),
        mutationAt("/ratio", "razón nula", n -> n.putNull("ratio")),
        mutationAt("/due_date", "fecha nula", n -> n.putNull("due_date")),
        mutationAt("/kind", "código nulo", n -> n.putNull("kind")),
        mutationAt("/applies", "valor lógico nulo", n -> n.putNull("applies")),
        mutationAt("/movements", "lista nula", n -> n.putNull("movements")),
        mutationAt("/amount", "monto ausente", n -> n.remove("amount")),
        mutationAt("/ratio", "razón ausente", n -> n.remove("ratio")),
        mutationAt("/months", "entero ausente", n -> n.remove("months")),
        mutationAt("/scaled_value", "bruto ausente", n -> n.remove("scaled_value")),
        mutationAt("/due_date", "fecha ausente", n -> n.remove("due_date")),
        mutationAt("/kind", "código ausente", n -> n.remove("kind")),
        mutationAt("/applies", "valor lógico ausente", n -> n.remove("applies")),
        mutationAt("/movements", "lista ausente", n -> n.remove("movements")),
        mutationAt("/codes", "lista de códigos ausente", n -> n.remove("codes")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("failingListMutations")
  @DisplayName(
      "Una lista con un elemento de más, de menos o cambiado falla (multiconjunto exacto, 006-05)")
  void aListMutationFails(String path, Consumer<ObjectNode> change) {
    ObjectNode actual = expected();
    change.accept(actual);

    List<String> differences = GoldenComparator.differences(caseOf(expected()), result(actual));

    assertThat(differences).isNotEmpty();
    assertThat(differences).allSatisfy(difference -> assertThat(difference).startsWith(path + ":"));
  }

  static Stream<Arguments> failingListMutations() {
    return Stream.of(
        mutationAt(
            "/movements",
            "un movimiento de más",
            n -> movements(n).add(movement("T_THREE", "C_ONE", 5))),
        mutationAt(
            "/movements",
            "un movimiento repetido de más",
            n -> movements(n).add(movements(n).get(0).deepCopy())),
        mutationAt("/movements", "un movimiento de menos", n -> movements(n).remove(1)),
        mutationAt("/movements", "sin movimientos", n -> n.set("movements", nodes().arrayNode())),
        mutationAt("/movements", "el tipo cambiado", n -> element(n, 0).put("type", "T_THREE")),
        mutationAt(
            "/movements", "el componente cambiado", n -> element(n, 0).put("component", "C_TWO")),
        mutationAt("/movements", "el monto cambiado +1", n -> element(n, 0).put("amount", 1001)),
        mutationAt("/movements", "el signo cambiado", n -> element(n, 1).put("amount", 400)),
        mutationAt("/movements", "reverses cambiado", n -> element(n, 1).put("reverses", "T_TWO")),
        mutationAt("/movements", "reverses ausente", n -> element(n, 1).remove("reverses")),
        mutationAt("/movements", "reverses nulo", n -> element(n, 1).putNull("reverses")),
        mutationAt(
            "/movements",
            "el movimiento 1 duplicado y el 2 perdido",
            n -> n.set("movements", copies(movements(n).get(0), 2))),
        mutationAt("/codes", "un código de más", n -> codes(n).add("K3")),
        mutationAt("/codes", "un código de menos", n -> codes(n).remove(1)),
        mutationAt("/codes", "un código cambiado", n -> codes(n).set(1, nodes().stringNode("K9"))),
        mutationAt(
            "/codes",
            "los códigos repetidos en lugar de distintos",
            n -> n.set("codes", copies(nodes().stringNode("K1"), 2))));
  }

  @Test
  @DisplayName(
      "Una lista no depende del orden de sus elementos, pero sí de cuántas veces aparecen (006-05)")
  void aListIsAMultisetNotASequence() {
    GoldenCase twice = caseOf(object("{\"codes\": [\"K1\", \"K1\", \"K2\"]}"));

    assertThat(
            GoldenComparator.differences(
                twice, result(object("{\"codes\": [\"K2\", \"K1\", \"K1\"]}"))))
        .isEmpty();
    assertThat(
            GoldenComparator.differences(
                twice, result(object("{\"codes\": [\"K1\", \"K2\", \"K2\"]}"))))
        .isNotEmpty();
  }

  @Test
  @DisplayName("Un resultado nulo falla (006-05)")
  void aNullResultFails() {
    assertThat(GoldenComparator.differences(caseOf(expected()), null)).isNotEmpty();
    assertThat(GoldenComparator.differences(caseOf(expected()), () -> null)).isNotEmpty();
    assertThat(GoldenComparator.differences(caseOf(expected()), () -> nodes().nullNode()))
        .isNotEmpty();
  }

  @Test
  @DisplayName("Reporta todas las diferencias juntas, no solo la primera (006-05)")
  void allDifferencesAreReportedTogether() {
    ObjectNode actual = expected();
    actual.put("amount", 1001);
    actual.put("kind", "KIND_B");
    actual.remove("months");
    movements(actual).remove(0);

    assertThat(GoldenComparator.differences(caseOf(expected()), result(actual))).hasSize(4);
    assertThatThrownBy(() -> GoldenComparator.assertMatches(caseOf(expected()), result(actual)))
        .isInstanceOfSatisfying(
            MultipleFailuresError.class, error -> assertThat(error.getFailures()).hasSize(4));
  }

  @Test
  @DisplayName(
      "La comparación de un campo sin escala es exacta, con la escala del caso donde la trae (006-05)")
  void theScaleComesFromTheCase() {
    ObjectNode expectedExact = object("{\"scaled_value\": 2500.00}");
    GoldenCase exact = caseOf(expectedExact, Map.of());

    assertThat(GoldenComparator.differences(exact, result(object("{\"scaled_value\": 2500.004}"))))
        .isNotEmpty();
    assertThat(GoldenComparator.differences(exact, result(object("{\"scaled_value\": 2500.0}"))))
        .isEmpty();

    GoldenCase tenths =
        caseOf(
            object("{\"scaled_value\": 2500.0}"),
            Map.of("scaled_value", new GoldenCase.Comparison(1, RoundingMode.HALF_UP)));
    assertThat(GoldenComparator.differences(tenths, result(object("{\"scaled_value\": 2500.04}"))))
        .isEmpty();
    assertThat(GoldenComparator.differences(tenths, result(object("{\"scaled_value\": 2500.05}"))))
        .isNotEmpty();
  }

  // ---- helpers ----

  private static ObjectNode expected() {
    return object(EXPECTED);
  }

  private static ObjectNode object(String json) {
    return (ObjectNode) CommonCases.EXACT.readTree(json);
  }

  private static GoldenCase caseOf(ObjectNode expected) {
    return caseOf(
        expected, Map.of("scaled_value", new GoldenCase.Comparison(2, RoundingMode.HALF_UP)));
  }

  private static GoldenCase caseOf(
      ObjectNode expected, Map<String, GoldenCase.Comparison> comparison) {
    return new GoldenCase(
        "T1",
        List.of("X-01"),
        "Caso sintético",
        SOURCE,
        List.of(),
        List.of(),
        List.of(),
        CommonCases.EXACT.createObjectNode(),
        expected,
        comparison,
        new GoldenCase.Configuration("a.yaml", "0".repeat(64)));
  }

  private static GoldenResult result(JsonNode tree) {
    return () -> tree;
  }

  private static JsonNodeFactory nodes() {
    return tools.jackson.databind.node.JsonNodeFactory.instance;
  }

  private static DecimalNode decimal(String value) {
    return DecimalNode.valueOf(new BigDecimal(value));
  }

  private static ArrayNode movements(ObjectNode node) {
    return (ArrayNode) node.get("movements");
  }

  private static ArrayNode codes(ObjectNode node) {
    return (ArrayNode) node.get("codes");
  }

  private static ObjectNode element(ObjectNode node, int index) {
    return (ObjectNode) movements(node).get(index);
  }

  private static ObjectNode movement(String type, String component, int amount) {
    return nodes().objectNode().put("type", type).put("component", component).put("amount", amount);
  }

  private static ArrayNode copies(JsonNode element, int times) {
    ArrayNode array = nodes().arrayNode();
    for (int i = 0; i < times; i++) {
      array.add(element.deepCopy());
    }
    return array;
  }

  private static ArrayNode reversed(JsonNode list) {
    ArrayNode array = nodes().arrayNode();
    for (int i = list.size() - 1; i >= 0; i--) {
      array.add(list.get(i));
    }
    return array;
  }

  private static Arguments mutation(String name, Consumer<ObjectNode> change) {
    return Arguments.of(Named.of(name, change));
  }

  /** A mutation with the path of the one difference it must cause; shown as "path · name". */
  private static Arguments mutationAt(String path, String name, Consumer<ObjectNode> change) {
    return Arguments.of(Named.of(path + " · " + name, path), change);
  }
}
