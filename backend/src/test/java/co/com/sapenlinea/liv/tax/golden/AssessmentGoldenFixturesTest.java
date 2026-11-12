package co.com.sapenlinea.liv.tax.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import co.com.sapenlinea.liv.contracts.SafeYamlLoader;
import co.com.sapenlinea.liv.golden.GoldenCase;
import co.com.sapenlinea.liv.golden.GoldenComparator;
import co.com.sapenlinea.liv.golden.GoldenConfiguration;
import co.com.sapenlinea.liv.golden.GoldenFixtures;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Fixture;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Problem;
import com.networknt.schema.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Checks that the assessment golden fixtures L1 to L15 are complete and say what their document
 * says (006-01, 006-04, 006-05), and that the comparison finds any difference in a real case. The
 * cases come from {@code golden/tax}; the damaged variants start from a synthetic valid text
 * written here. The tax values are not repeated here: they are in the fixtures and are compared
 * with the documents.
 */
class AssessmentGoldenFixturesTest {

  private static final String MODULE = "tax";

  private static final List<String> IDS =
      IntStream.rangeClosed(1, 15).mapToObj(number -> "L" + number).toList();

  private static final String IPIALES_FILE =
      "contracts/jsonschema/config-jurisdiccion/1.1/config_jurisdiccion_52356_2026.yaml";

  /** Doc. 03 section 9.2: the SHA-256 of the copy of Ipiales, with LF. */
  private static final String IPIALES_SHA256 =
      "e038e12a30223ce8944cb2220869b6dc52914c6a8cc90a93c273ec869680c872";

  /** L1, L2 and L5: the base is an input, so the document only gives rate, months and gross. */
  private static final Set<String> RATE_MONTHS_GROSS =
      Set.of("applied_rate", "taxable_months", "gross_tax");

  /** L3 and L4: the document also gives the base. */
  private static final Set<String> WITH_BASE =
      Set.of("taxable_base", "applied_rate", "taxable_months", "gross_tax");

  /** L7 to L12: the whole row of the Doc. 03 section 9.3 table. */
  private static final Set<String> WITH_NET =
      Set.of(
          "taxable_base",
          "applied_rate",
          "taxable_months",
          "gross_tax",
          "net_tax",
          "effective_discount",
          "net_tax_with_discount",
          "discount_deadline");

  /** What each case compares: the columns with a value in the table of the spec. */
  private static final Map<String, Set<String>> EXPECTED_KEYS =
      Map.ofEntries(
          Map.entry("L1", RATE_MONTHS_GROSS),
          Map.entry("L2", RATE_MONTHS_GROSS),
          Map.entry("L3", WITH_BASE),
          Map.entry("L4", WITH_BASE),
          Map.entry("L5", RATE_MONTHS_GROSS),
          Map.entry("L6", Set.of("applied_rate")),
          Map.entry("L7", WITH_NET),
          Map.entry("L8", WITH_NET),
          Map.entry("L9", WITH_NET),
          Map.entry("L10", WITH_NET),
          Map.entry("L11", WITH_NET),
          Map.entry("L12", WITH_NET),
          Map.entry(
              "L13",
              Set.of(
                  "taxable_base",
                  "applied_rate",
                  "taxable_months",
                  "gross_tax",
                  "net_tax",
                  "discount_applies",
                  "due_date")),
          Map.entry("L14", Set.of("discount_deadline", "due_date", "default_from")),
          Map.entry("L15", Set.of("discount_applies", "due_date", "default_from")));

  /** The rule codes of each row of the spec table. */
  private static final Map<String, Set<String>> RULES =
      Map.ofEntries(
          Map.entry("L1", Set.of("006-02")),
          Map.entry("L2", Set.of("006-02")),
          Map.entry("L3", Set.of("CA-L04")),
          Map.entry("L4", Set.of("CA-L03")),
          Map.entry("L5", Set.of("CA-L01")),
          Map.entry("L6", Set.of("CA-L02")),
          Map.entry("L7", Set.of("LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L8", Set.of("LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L9", Set.of("CA-L04", "LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L10", Set.of("LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L11", Set.of("LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L12", Set.of("LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L13", Set.of("CA-L03", "LQ-C01", "LQ-C02", "LQ-C04")),
          Map.entry("L14", Set.of("006-14")),
          Map.entry("L15", Set.of("006-14")));

  /** The inputs the document does not give but the case needs (declared inferred), by case. */
  private static final Map<String, Set<String>> INFERRED =
      Map.ofEntries(
          Map.entry("L1", Set.of("vehicle_category", "taxable_base")),
          Map.entry("L2", Set.of("vehicle_category", "taxable_base")),
          Map.entry("L3", Set.of("vehicle_category", "table_value")),
          Map.entry("L4", Set.of("vehicle_category")),
          Map.entry("L5", Set.of("vehicle_category", "taxable_base")),
          Map.entry("L6", Set.of("vehicle_category", "taxable_base", "fiscal_year")),
          Map.entry("L7", Set.of("vehicle_category")),
          Map.entry("L8", Set.of("vehicle_category")),
          Map.entry("L9", Set.of("vehicle_category")),
          Map.entry("L10", Set.of("vehicle_category")),
          Map.entry("L11", Set.of("vehicle_category")),
          Map.entry("L12", Set.of("vehicle_category")),
          Map.entry("L13", Set.of("vehicle_category")),
          Map.entry("L14", Set.of()),
          Map.entry("L15", Set.of()));

  /** The cases the document computes as if the convenio (modalidad B) were active (section 9.3). */
  private static final Set<String> HYPOTHETICAL_B = Set.of("L7", "L8", "L9", "L11", "L12", "L13");

  /** The configuration values each case relies on ({@code depends_on}); the others have none. */
  private static final Map<String, Set<String>> DEPENDS_ON =
      Map.of(
          "L4", Set.of("causacion.proporcionalidad_nuevos"),
          "L13", Set.of("causacion.proporcionalidad_nuevos"),
          "L8", Set.of("redondeo.empate"),
          "L11", Set.of("tarifas.MOTO", "categorias", "sujecion"));

  /** Where each case comes from: document, version, section and row (null when it has none). */
  private static final Map<String, GoldenCase.Source> SOURCES = sources();

  private static Map<String, GoldenCase.Source> sources() {
    Map<String, GoldenCase.Source> sources = new LinkedHashMap<>();
    // L1 to L5 are rows 1 to 5 of the Doc. 02 section B.9 table.
    for (int row = 1; row <= 5; row++) {
      sources.put("L" + row, new GoldenCase.Source("Doc. 02", "0.2", "§B.9", row));
    }
    sources.put("L6", new GoldenCase.Source("Doc. 02", "0.2", "§11", null));
    // L7 to L13 are rows 1 to 7 of the Doc. 03 section 9.3 table.
    int firstOf93 = 7;
    for (int row = 1; row <= 7; row++) {
      sources.put(
          "L" + (firstOf93 + row - 1), new GoldenCase.Source("Doc. 03", "0.5", "§9.3", row));
    }
    sources.put("L14", new GoldenCase.Source("Doc. 03", "0.5", "§9.7", 1));
    sources.put("L15", new GoldenCase.Source("Doc. 03", "0.5", "§9.7", 3));
    return sources;
  }

  // ---- the fixtures are complete (006-01, 006-05) ----

  @Test
  @DisplayName("Los casos de golden/tax son exactamente L1 a L15 (006-05)")
  void theCasesAreExactlyL1ToL15() {
    assertThat(GoldenFixtures.cases(MODULE))
        .extracting(GoldenCase::id)
        .containsExactlyInAnyOrderElementsOf(IDS);
  }

  @Test
  @DisplayName("Los cuatro fixtures de liquidación son válidos contra su esquema (006-05)")
  void theFourFixturesAreValid() {
    List<Fixture> fixtures = GoldenFixtures.fixtures(MODULE);
    Schema schema = GoldenFixtures.schema(MODULE);

    assertThat(fixtures)
        .extracting(fixture -> fixture.file().getFileName().toString())
        .containsExactly("doc02-11.yaml", "doc02-b9.yaml", "doc03-9-3.yaml", "doc03-9-7.yaml");
    assertAll(
        fixtures.stream()
            .map(fixture ->
                () ->
                    assertThat(
                            GoldenFixtures.problems(
                                schema, fixture.tree(), AssessmentGoldenRules.INPUT))
                        .as(fixture.file().toString())
                        .isEmpty()));
  }

  @Test
  @DisplayName("Cada caso dice de qué documento, versión, sección y fila sale (006-01, 006-10)")
  void everyCaseSaysWhereItComesFrom() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.source())
                .as(goldenCase.id())
                .isEqualTo(SOURCES.get(goldenCase.id())));
  }

  @Test
  @DisplayName("Cada caso lleva los códigos de regla de su fila de la spec (006-07)")
  void everyCaseCarriesTheRuleCodesOfItsRow() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.rules())
                .as(goldenCase.id())
                .containsExactlyInAnyOrderElementsOf(RULES.get(goldenCase.id())));
  }

  @Test
  @DisplayName(
      "Cada caso compara exactamente las columnas con valor de la tabla de la spec (006-01)")
  void everyCaseComparesExactlyTheColumnsThatHaveAValue() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(keys(goldenCase.expected()))
                .as(goldenCase.id())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS.get(goldenCase.id())));
  }

  @Test
  @DisplayName("Cada caso usa la copia de Ipiales por su ruta y su SHA-256 (006-01, 006-09)")
  void everyCaseUsesTheCopyOfIpiales() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.configuration())
                .as(goldenCase.id())
                .isEqualTo(new GoldenCase.Configuration(IPIALES_FILE, IPIALES_SHA256)));
  }

  @Test
  @DisplayName("Las entradas inferidas están declaradas con su motivo y su supuesto (006-01)")
  void theInferredInputsAreDeclared() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          List<String> fields =
              goldenCase.inferredInputs().stream().map(GoldenCase.InferredInput::field).toList();
          assertThat(fields)
              .as(goldenCase.id() + " · entradas inferidas")
              .containsExactlyInAnyOrderElementsOf(INFERRED.get(goldenCase.id()));
          assertThat(goldenCase.inferredInputs())
              .as(goldenCase.id() + " · motivos")
              .allSatisfy(input -> assertThat(input.reason()).isNotBlank());
          assertThat(goldenCase.assumptions().contains("ENTRADA_INFERIDA"))
              .as(goldenCase.id() + " · ENTRADA_INFERIDA solo si hay entradas inferidas")
              .isEqualTo(!fields.isEmpty());
          assertThat(fields)
              .as(goldenCase.id() + " · cada entrada inferida es una entrada del caso")
              .allSatisfy(field -> assertThat(goldenCase.input().has(field)).isTrue());
        });
  }

  @Test
  @DisplayName(
      "Solo los casos de la nota del Doc. 03 §9.3 llevan la modalidad B hipotética (006-01)")
  void onlyTheCasesComputedWithBAreHypothetical() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.assumptions().contains("MODALIDAD_B_HIPOTETICA"))
                .as(goldenCase.id())
                .isEqualTo(HYPOTHETICAL_B.contains(goldenCase.id())));
  }

  @Test
  @DisplayName("Cada caso declara los valores de la configuración de los que depende (006-01)")
  void everyCaseDeclaresWhatItDependsOn() {
    JsonNode configuration = GoldenConfiguration.load(IPIALES_FILE);

    assertThat(cases())
        .allSatisfy(goldenCase -> {
          assertThat(goldenCase.dependsOn())
              .as(goldenCase.id())
              .containsExactlyInAnyOrderElementsOf(
                  DEPENDS_ON.getOrDefault(goldenCase.id(), Set.of()));
          assertThat(goldenCase.dependsOn())
              .as(goldenCase.id() + " · existen en la configuración")
              .allSatisfy(path -> assertThat(resolves(configuration, path)).as(path).isTrue());
        });
  }

  @Test
  @DisplayName("Cada caso se convierte en la entrada del adaptador sin error (006-04)")
  void everyCaseConvertsToTheAdapterInput() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          AssessmentGoldenInput input = AssessmentGoldenInputs.from(goldenCase);
          JsonNode tree = goldenCase.input();
          assertThat(input.fiscalYear()).isEqualTo(tree.get("fiscal_year").intValue());
          assertThat(input.newVehicle()).isEqualTo(tree.get("new_vehicle").booleanValue());
          assertThat(input.baseAdjustments())
              .isEqualTo(
                  tree.path("base_adjustments").valueStream().map(JsonNode::stringValue).toList());
          assertThat(input.vehicleCategory()).isEqualTo(textOrNull(tree, "vehicle_category"));
          assertThat(input.baseSourceType()).isEqualTo(textOrNull(tree, "base_source_type"));
          assertThat(input.accrualDate())
              .isEqualTo(
                  tree.has("accrual_date")
                      ? LocalDate.parse(tree.get("accrual_date").stringValue())
                      : null);
          assertSameAmount(input.taxableBase(), tree, "taxable_base");
          assertSameAmount(input.tableValue(), tree, "table_value");
          assertSameAmount(input.tableValueThousands(), tree, "table_value_thousands");
          assertSameAmount(input.invoiceValueWithoutVat(), tree, "invoice_value_without_vat");
        });
  }

  // ---- damaged variants (006-05) ----

  /** A synthetic valid fixture: its values are not tax values and it is not a golden case. */
  private static String validText() {
    return """
    document: {code: "Doc. 03", version: "9.9", section: "§0.0"}
    configuration:
      file: "%s"
      sha256: "%s"
    comparison:
      gross_tax: {scale: 2, rounding: "HALF_UP"}
    cases:
      - id: "L1"
        row: 1
        rules: ["LQ-C01"]
        description: "Caso sintético uno"
        assumptions: ["ENTRADA_INFERIDA"]
        inferred_inputs:
          - {field: "vehicle_category", reason: "motivo sintético"}
        depends_on: ["redondeo.empate"]
        input:
          fiscal_year: 2030
          vehicle_category: "PARTICULAR"
          base_source_type: "OFFICIAL_TABLE"
          table_value_thousands: 7
          base_adjustments: ["BLINDADO"]
          new_vehicle: false
        expected:
          taxable_base: 7000
          applied_rate: 0.125
          taxable_months: 12
          gross_tax: 875.00
          net_tax: 875
          discount_deadline: 2030-01-15
      - id: "L2"
        rules: ["006-14"]
        description: "Caso sintético dos"
        input:
          fiscal_year: 2030
          new_vehicle: true
          accrual_date: 2030-01-10
        expected:
          discount_applies: false
          due_date: 2030-02-01
          default_from: 2030-02-02
    """
        .formatted(IPIALES_FILE, IPIALES_SHA256);
  }

  @Test
  @DisplayName("El fixture sintético válido no tiene problemas (006-05)")
  void theValidControlHasNoProblems() {
    JsonNode valid = SafeYamlLoader.load(validText());

    assertThat(
            GoldenFixtures.problems(
                GoldenFixtures.schema(MODULE), valid, AssessmentGoldenRules.INPUT))
        .isEmpty();
    assertThat(GoldenFixtures.cases(valid)).extracting(GoldenCase::id).containsExactly("L1", "L2");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("damagedVariants")
  @DisplayName(
      "Una variante dañada del esquema de liquidación se rechaza solo por su motivo (006-05)")
  void aDamagedVariantIsRejectedForTheRightReason(String from, String to, List<Problem> expected) {
    JsonNode damaged = SafeYamlLoader.load(replaceOnce(validText(), from, to));

    assertThat(
            GoldenFixtures.problems(
                GoldenFixtures.schema(MODULE), damaged, AssessmentGoldenRules.INPUT))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  static Stream<Arguments> damagedVariants() {
    return Stream.of(
        variant(
            "un monto entre comillas",
            "net_tax: 875",
            "net_tax: \"875\"",
            problem("/cases/0/expected/net_tax", "type")),
        variant(
            "un monto en pesos con decimales",
            "net_tax: 875",
            "net_tax: 875.50",
            problem("/cases/0/expected/net_tax", "type")),
        variant(
            "un valor de tabla en miles con decimales",
            "table_value_thousands: 7",
            "table_value_thousands: 7.5",
            problem("/cases/0/input/table_value_thousands", "type")),
        variant(
            "un caso sin expected",
            "    expected:\n      discount_applies: false\n      due_date: 2030-02-01\n      default_from: 2030-02-02\n",
            "",
            problem("/cases/1/expected", "required")),
        variant(
            "dos formas de base",
            "table_value_thousands: 7\n",
            "table_value_thousands: 7\n      taxable_base: 7000\n",
            problem("/cases/0/input", "base-form")),
        variant(
            "sin base en un caso que compara montos",
            "      base_source_type: \"OFFICIAL_TABLE\"\n      table_value_thousands: 7\n",
            "",
            problem("/cases/0/input", "base-required")),
        variant(
            "un origen que no corresponde a la forma de la base",
            "base_source_type: \"OFFICIAL_TABLE\"",
            "base_source_type: \"INVOICE\"",
            problem("/cases/0/input/base_source_type", "base-source")),
        variant(
            "sin categoría en un caso que compara montos",
            "      vehicle_category: \"PARTICULAR\"\n",
            "",
            problem("/cases/0/input/vehicle_category", "category-required")),
        variant(
            "un depends_on fuera de la lista",
            "depends_on: [\"redondeo.empate\"]",
            "depends_on: [\"redondeo.otro\"]",
            problem("/cases/0/depends_on/0", "enum")),
        variant(
            "un código de regla fuera de la lista",
            "rules: [\"LQ-C01\"]",
            "rules: [\"LQ-C03\"]",
            problem("/cases/0/rules/0", "enum")),
        variant(
            "un supuesto de otro módulo",
            "assumptions: [\"ENTRADA_INFERIDA\"]",
            "assumptions: [\"DEUDA_DADA_A_LA_FECHA_DEL_PAGO\"]",
            problem("/cases/0/assumptions/0", "enum")),
        variant(
            "una entrada inferida que no es una entrada",
            "field: \"vehicle_category\"",
            "field: \"gross_tax\"",
            problem("/cases/0/inferred_inputs/0/field", "enum")),
        variant(
            "un bruto con tres decimales",
            "gross_tax: 875.00",
            "gross_tax: 875.005",
            problem("/cases/0/expected/gross_tax", "scale")),
        variant(
            "una escala de comparación distinta de la fijada",
            "scale: 2",
            "scale: 3",
            problem("/comparison/gross_tax/scale", "const")),
        variant(
            "otro redondeo de comparación",
            "rounding: \"HALF_UP\"",
            "rounding: \"HALF_DOWN\"",
            problem("/comparison/gross_tax/rounding", "const")),
        variant(
            "una copia de la configuración con otro SHA-256",
            "sha256: \"" + IPIALES_SHA256 + "\"",
            "sha256: \"" + "0".repeat(64) + "\"",
            problem("/configuration/sha256", "const"),
            problem("/configuration/sha256", "configuration-sha256")),
        variant(
            "una fecha mal escrita",
            "due_date: 2030-02-01",
            "due_date: \"2030-2-1\"",
            problem("/cases/1/expected/due_date", "pattern")),
        variant(
            "una clave desconocida en la entrada",
            "new_vehicle: false",
            "new_vehicle: false\n      unknown: 1",
            problem("/cases/0/input/unknown", "additionalProperties")),
        variant(
            "un id fuera de L1 a L15",
            "id: \"L2\"",
            "id: \"L16\"",
            problem("/cases/1/id", "pattern")),
        variant(
            "un documento que no es de liquidación",
            "code: \"Doc. 03\"",
            "code: \"Doc. 04\"",
            problem("/document/code", "enum")));
  }

  // ---- the comparison finds a difference in a real case (006-05) ----

  @Test
  @DisplayName("El adaptador que devuelve lo esperado pasa en los quince casos (006-05)")
  void anAdapterReturningTheExpectedPasses() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          assertThat(GoldenComparator.differences(goldenCase, echo(goldenCase)))
              .as(goldenCase.id())
              .isEmpty();
          GoldenComparator.assertMatches(goldenCase, echo(goldenCase));
        });
  }

  @Test
  @DisplayName("Un adaptador que no devuelve resultado falla en todos los casos (006-05)")
  void noResultFailsEveryCase() {
    assertThat(cases())
        .allSatisfy(
            goldenCase -> assertThat(GoldenComparator.differences(goldenCase, null)).isNotEmpty());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mutations")
  @DisplayName("Cualquier diferencia en una clave esperada de un caso real falla (006-01, 006-05)")
  void aMutationOfARealCaseIsDetected(
      GoldenCase goldenCase, String key, Consumer<ObjectNode> change) {
    ObjectNode tree = (ObjectNode) echo(goldenCase).asTree().deepCopy();
    change.accept(tree);

    List<String> differences = GoldenComparator.differences(goldenCase, () -> tree);

    assertThat(differences).isNotEmpty();
    assertThat(differences)
        .allSatisfy(difference -> assertThat(difference).startsWith("/" + key + ":"));
  }

  /** For every key of every real case: the changes the comparison must not let pass. */
  static Stream<Arguments> mutations() {
    List<Arguments> arguments = new ArrayList<>();
    for (GoldenCase goldenCase : cases()) {
      for (Map.Entry<String, JsonNode> entry : goldenCase.expected().properties()) {
        String key = entry.getKey();
        for (Map.Entry<String, Consumer<ObjectNode>> change :
            changesFor(key, entry.getValue(), goldenCase.comparison().get(key)).entrySet()) {
          String name = goldenCase.id() + " · " + key + " · " + change.getKey();
          arguments.add(Arguments.of(Named.of(name, goldenCase), key, change.getValue()));
        }
      }
    }
    return arguments.stream();
  }

  /** The changes for one key, by what the expected value is; a comparison gives the step. */
  private static Map<String, Consumer<ObjectNode>> changesFor(
      String key, JsonNode value, GoldenCase.Comparison comparison) {
    Map<String, Consumer<ObjectNode>> byName = new LinkedHashMap<>();
    byName.put("nulo", tree -> tree.putNull(key));
    byName.put("clave ausente", tree -> tree.remove(key));
    if (value.isBoolean()) {
      byName.put("valor lógico invertido", tree -> tree.put(key, !value.booleanValue()));
      byName.put(
          "valor lógico como texto", tree -> tree.put(key, String.valueOf(value.booleanValue())));
    } else if (value.isString()) {
      LocalDate date = LocalDate.parse(value.stringValue());
      byName.put("un día más", tree -> tree.put(key, date.plusDays(1).toString()));
      byName.put("un día menos", tree -> tree.put(key, date.minusDays(1).toString()));
      byName.put("otro texto", tree -> tree.put(key, value.stringValue() + "X"));
      byName.put("fecha como número", tree -> tree.put(key, 1));
    } else if (value.isNumber()) {
      BigDecimal step = step(value, comparison);
      byName.put(
          "más " + step.toPlainString(),
          tree -> tree.set(key, decimal(value.decimalValue().add(step))));
      byName.put(
          "menos " + step.toPlainString(),
          tree -> tree.set(key, decimal(value.decimalValue().subtract(step))));
      byName.put("como texto", tree -> tree.put(key, value.decimalValue().toPlainString()));
      byName.put("como double", tree -> tree.set(key, DoubleNode.valueOf(value.doubleValue())));
    }
    return byName;
  }

  /** One cent at the scale of a comparison; one unit for an integer; a thousandth for a rate. */
  private static BigDecimal step(JsonNode value, GoldenCase.Comparison comparison) {
    if (comparison != null) {
      return BigDecimal.ONE.movePointLeft(comparison.scale());
    }
    return value.isIntegralNumber() ? BigDecimal.ONE : new BigDecimal("0.001");
  }

  // ---- helpers ----

  private static List<GoldenCase> cases() {
    return GoldenFixtures.cases(MODULE);
  }

  private static List<String> keys(JsonNode object) {
    return object.properties().stream().map(Map.Entry::getKey).toList();
  }

  /** The typed result an adapter would return if it gave exactly the expected values. */
  private static AssessmentGoldenResult echo(GoldenCase goldenCase) {
    JsonNode expected = goldenCase.expected();
    return new AssessmentGoldenResult(
        amount(expected, "taxable_base"),
        amount(expected, "applied_rate"),
        expected.has("taxable_months") ? expected.get("taxable_months").intValue() : null,
        amount(expected, "gross_tax"),
        amount(expected, "net_tax"),
        amount(expected, "effective_discount"),
        amount(expected, "net_tax_with_discount"),
        expected.has("discount_applies") ? expected.get("discount_applies").booleanValue() : null,
        date(expected, "discount_deadline"),
        date(expected, "due_date"),
        date(expected, "default_from"));
  }

  private static BigDecimal amount(JsonNode node, String key) {
    return node.has(key) ? node.get(key).decimalValue() : null;
  }

  private static LocalDate date(JsonNode node, String key) {
    return node.has(key) ? LocalDate.parse(node.get(key).stringValue()) : null;
  }

  private static String textOrNull(JsonNode node, String key) {
    return node.has(key) ? node.get(key).stringValue() : null;
  }

  private static void assertSameAmount(BigDecimal actual, JsonNode input, String key) {
    if (input.has(key)) {
      assertThat(actual).as(key).isEqualByComparingTo(input.get(key).decimalValue());
    } else {
      assertThat(actual).as(key).isNull();
    }
  }

  private static DecimalNode decimal(BigDecimal value) {
    return DecimalNode.valueOf(value);
  }

  /** Whether a dotted path exists in the configuration; a list is searched by its code. */
  private static boolean resolves(JsonNode root, String path) {
    JsonNode node = root;
    for (String part : path.split("\\.")) {
      node = child(node, part);
      if (node == null) {
        return false;
      }
    }
    return true;
  }

  private static JsonNode child(JsonNode node, String name) {
    if (node.isObject()) {
      return node.get(name);
    }
    if (node.isArray()) {
      for (JsonNode element : node) {
        if (name.equals(textOrNull(element, "categoria"))
            || name.equals(textOrNull(element, "codigo"))) {
          return element;
        }
      }
    }
    return null;
  }

  private static Problem problem(String path, String rule) {
    return new Problem(path, rule);
  }

  /** Arguments: the text to replace (shown as the name), its replacement and the exact problems. */
  private static Arguments variant(String name, String from, String to, Problem... problems) {
    return Arguments.of(Named.of(name, from), to, List.of(problems));
  }

  /** The text with one change; a replacement that finds nothing or finds two fails the test. */
  private static String replaceOnce(String text, String from, String to) {
    int first = text.indexOf(from);
    assertThat(first).as("«" + from + "» está en el fixture válido").isNotNegative();
    assertThat(text.indexOf(from, first + 1)).as("«" + from + "» está una sola vez").isNegative();
    return text.replace(from, to);
  }
}
