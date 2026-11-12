package co.com.sapenlinea.liv.ledger.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import co.com.sapenlinea.liv.contracts.CommonCases;
import co.com.sapenlinea.liv.contracts.SafeYamlLoader;
import co.com.sapenlinea.liv.golden.GoldenCase;
import co.com.sapenlinea.liv.golden.GoldenComparator;
import co.com.sapenlinea.liv.golden.GoldenConfiguration;
import co.com.sapenlinea.liv.golden.GoldenEngineNotImplementedException;
import co.com.sapenlinea.liv.golden.GoldenFixtures;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Fixture;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Problem;
import com.networknt.schema.Schema;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.support.AnnotationSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Checks that the ledger golden fixtures C1 to C9 are complete and say what their document says
 * (006-03, 006-04, 006-05, 006-10), and that the comparison finds any difference in a real case: an
 * element more or less in a list, a changed component, a changed {@code reverses}, a changed
 * beneficiary or a changed "retained or sent". The cases come from {@code golden/ledger}; the
 * damaged variants start from a synthetic valid text written here (its values are not tax values).
 * The values are not repeated here: they are in the fixtures and are compared with the documents.
 */
class LedgerGoldenFixturesTest {

  private static final String MODULE = "ledger";

  private static final List<String> IDS =
      IntStream.rangeClosed(1, 9).mapToObj(number -> "C" + number).toList();

  private static final String IPIALES_FILE =
      "contracts/jsonschema/config-jurisdiccion/1.1/config_jurisdiccion_52356_2026.yaml";

  /** Doc. 03 section 9.2: the SHA-256 of the copy of Ipiales, with LF. */
  private static final String IPIALES_SHA256 =
      "e038e12a30223ce8944cb2220869b6dc52914c6a8cc90a93c273ec869680c872";

  /** What each case compares: only what the document gives. */
  private static final Map<String, Set<String>> EXPECTED_KEYS =
      Map.ofEntries(
          Map.entry("C1", Set.of("obligation", "movements")),
          Map.entry("C2", Set.of("movements", "balances", "status")),
          Map.entry("C3", Set.of("movements", "balances")),
          Map.entry("C4", Set.of("allocations", "balances", "total_balance")),
          Map.entry("C5", Set.of("allocations")),
          Map.entry("C6", Set.of("movements", "balances", "payment_status")),
          Map.entry("C7", Set.of("distribution")),
          Map.entry("C8", Set.of("distribution")),
          Map.entry("C9", Set.of("distribution")));

  /** The rule codes of each case: those of its row in the spec, or the code of its criterion. */
  private static final Map<String, Set<String>> RULES =
      Map.ofEntries(
          Map.entry("C1", Set.of("CA-4.01", "LQ-C04")),
          Map.entry("C2", Set.of("CA-4.02")),
          Map.entry("C3", Set.of("CA-4.03")),
          Map.entry("C4", Set.of("CA-4.04", "IM-01", "IM-03")),
          Map.entry("C5", Set.of("CA-4.04", "IM-01", "IM-03")),
          Map.entry("C6", Set.of("CA-4.08")),
          Map.entry("C7", Set.of("CA-4.09")),
          Map.entry("C8", Set.of("CA-4.09")),
          Map.entry("C9", Set.of("CA-4.09")));

  /** Where each case comes from: the document, its version, the section and the row or example. */
  private static final Map<String, GoldenCase.Source> SOURCES =
      Map.ofEntries(
          Map.entry("C1", new GoldenCase.Source("Doc. 04", "0.3", "§13", 1)),
          Map.entry("C2", new GoldenCase.Source("Doc. 04", "0.3", "§5.2", 1)),
          Map.entry("C3", new GoldenCase.Source("Doc. 04", "0.3", "§13", 3)),
          Map.entry("C4", new GoldenCase.Source("Doc. 04", "0.3", "§5.2", 2)),
          Map.entry("C5", new GoldenCase.Source("Doc. 04", "0.3", "§5.2", 2)),
          Map.entry("C6", new GoldenCase.Source("Doc. 04", "0.3", "§13", 8)),
          Map.entry("C7", new GoldenCase.Source("Doc. 04", "0.3", "§7", 1)),
          Map.entry("C8", new GoldenCase.Source("Doc. 04", "0.3", "§7", 2)),
          Map.entry("C9", new GoldenCase.Source("Doc. 04", "0.3", "§7", 3)));

  /** The inputs the document does not give but the case needs, as JSON Pointers, by case. */
  private static final Map<String, Set<String>> INFERRED =
      Map.ofEntries(
          Map.entry("C1", Set.of()),
          Map.entry("C2", Set.of()),
          Map.entry("C3", Set.of("/events/0/net_tax_with_discount", "/events/0/origin_modality")),
          Map.entry("C4", Set.of("/events/0/date")),
          Map.entry("C5", Set.of("/events/0/date")),
          Map.entry("C6", Set.of()),
          Map.entry("C7", Set.of("/events/0/movements/0")),
          Map.entry("C8", Set.of("/events/0/movements/0", "/events/0/movements/1")),
          Map.entry("C9", Set.of("/events/0/movements/0")));

  /** The cases the document computes as if the convenio (modalidad B) were active (section 7). */
  private static final Set<String> HYPOTHETICAL_B = Set.of("C7", "C8", "C9");

  /** The cases whose debt is the debt on the date of the payment (Doc. 04 section 5.2). */
  private static final Set<String> GIVEN_DEBT = Set.of("C4", "C5");

  /** The configuration values each case relies on ({@code depends_on}); the others have none. */
  private static final Map<String, Set<String>> DEPENDS_ON =
      Map.of(
          "C3", Set.of("recaudo", "recaudo.descuento_pago_parcial"),
          "C5", Set.of("recaudo.imputacion.redondeo_componentes"),
          "C7", Set.of("recaudo", "redondeo.empate"),
          "C8", Set.of("recaudo", "redondeo.empate"),
          "C9", Set.of("recaudo"));

  /** The expected movements that come from a rule and not from a figure of the example. */
  private static final Map<String, Set<String>> DERIVED =
      Map.of("C3", Set.of("PAYMENT"), "C6", Set.of("REVERSAL"));

  /** The modality of the obligation, and where the case gives it. */
  private static final Set<String> MODALITY_IN_EMISSION = Set.of("C1", "C2", "C3", "C6");

  /** C7 to C9 start from an imputation: the modality travels in the obligation. */
  private static final Set<String> MODALITY_IN_OBLIGATION = Set.of("C7", "C8", "C9");

  /** C4 and C5 have no emission: they start from the debt on the date of the payment. */
  private static final Set<String> STARTS_FROM_OPENING = Set.of("C4", "C5");

  private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

  /** The values a swapped text takes: another one of the same field, so the schema still fits. */
  private static final Map<String, Map<String, String>> SWAPS =
      Map.of(
          "component", Map.of("TAX", "INTEREST", "INTEREST", "TAX", "PENALTY", "TAX"),
          "reverses",
              Map.of("PAYMENT", "EARLY_PAYMENT_DISCOUNT", "EARLY_PAYMENT_DISCOUNT", "PAYMENT"),
          "beneficiary",
              Map.of(
                  "DEPARTAMENTO_NARINO", "MUNICIPIO_IPIALES",
                  "MUNICIPIO_IPIALES", "MUNICIPIO_PASTO",
                  "MUNICIPIO_PASTO", "DEPARTAMENTO_NARINO"),
          "disposition", Map.of("RETENIDO", "GIRADO", "GIRADO", "RETENIDO"));

  private static final Map<String, String> SWAP_NAMES =
      Map.of(
          "component", "componente cambiado",
          "reverses", "reverses cambiado",
          "beneficiary", "beneficiario cambiado",
          "disposition", "«se retiene o se gira» cambiado");

  /** One change of a result and the place it is made. */
  private record Mutation(String where, String name, Consumer<ObjectNode> change) {}

  // ---- the fixtures are complete (006-03, 006-05) ----

  @Test
  @DisplayName("Los casos de golden/ledger son exactamente C1 a C9 (006-05)")
  void theCasesAreExactlyC1ToC9() {
    assertThat(GoldenFixtures.cases(MODULE))
        .extracting(GoldenCase::id)
        .containsExactlyInAnyOrderElementsOf(IDS);
  }

  @Test
  @DisplayName("Los tres fixtures de la cuenta corriente son válidos contra su esquema (006-05)")
  void theThreeFixturesAreValid() {
    List<Fixture> fixtures = GoldenFixtures.fixtures(MODULE);
    Schema schema = GoldenFixtures.schema(MODULE);

    assertThat(fixtures)
        .extracting(fixture -> fixture.file().getFileName().toString())
        .containsExactly("doc04-13.yaml", "doc04-5-2.yaml", "doc04-7.yaml");
    assertAll(
        fixtures.stream()
            .map(fixture ->
                () ->
                    assertThat(
                            GoldenFixtures.problems(
                                schema, fixture.tree(), LedgerGoldenRules.INPUT))
                        .as(fixture.file().toString())
                        .isEmpty()));
  }

  @Test
  @DisplayName("Cada caso dice de qué documento, versión, sección y fila sale (006-03, 006-10)")
  void everyCaseSaysWhereItComesFrom() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.source())
                .as(goldenCase.id())
                .isEqualTo(SOURCES.get(goldenCase.id())));
  }

  @Test
  @DisplayName("Cada caso lleva los códigos de regla de su criterio de la spec (006-07)")
  void everyCaseCarriesTheRuleCodesOfItsCriterion() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.rules())
                .as(goldenCase.id())
                .containsExactlyInAnyOrderElementsOf(RULES.get(goldenCase.id())));
  }

  @Test
  @DisplayName(
      "Cada caso compara exactamente lo que el documento da: el estado solo en C2, sin saldos en C5 (006-03)")
  void everyCaseComparesExactlyWhatTheDocumentGives() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(keys(goldenCase.expected()))
                .as(goldenCase.id())
                .containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS.get(goldenCase.id())));
  }

  @Test
  @DisplayName("Ningún caso compara un estado que dependa de «hoy»: VIGENTE ni EN_MORA (§3.3)")
  void noCaseComparesAStatusThatDependsOnToday() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.expected().toString())
                .as(goldenCase.id())
                .doesNotContain("VIGENTE", "EN_MORA", "PAGADA_PARCIAL"));
  }

  @Test
  @DisplayName("Cada caso usa la copia de Ipiales por su ruta y su SHA-256 (006-03, 006-09)")
  void everyCaseUsesTheCopyOfIpiales() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(goldenCase.configuration())
                .as(goldenCase.id())
                .isEqualTo(new GoldenCase.Configuration(IPIALES_FILE, IPIALES_SHA256)));
  }

  @Test
  @DisplayName("La modalidad es siempre una entrada, dada una sola vez y donde la frontera dice")
  void theModalityIsAlwaysAnInputGivenOnce() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          JsonNode input = goldenCase.input();
          String id = goldenCase.id();
          boolean inEmission = hasEvent(input, "EMISSION");
          assertThat(inEmission)
              .as(id + " · EMISSION")
              .isEqualTo(MODALITY_IN_EMISSION.contains(id));
          assertThat(input.has("obligation"))
              .as(id + " · obligation")
              .isEqualTo(MODALITY_IN_OBLIGATION.contains(id));
          assertThat(input.has("opening_movements"))
              .as(id + " · opening_movements")
              .isEqualTo(STARTS_FROM_OPENING.contains(id));
          if (inEmission) {
            assertThat(input.at("/events/0/type").stringValue()).as(id).isEqualTo("EMISSION");
            assertThat(input.at("/events/0/origin_modality").stringValue())
                .as(id)
                .isEqualTo("UEDF_INTERNACION");
          }
          if (input.has("obligation")) {
            assertThat(input.at("/obligation/origin_modality").stringValue())
                .as(id)
                .isEqualTo("CONVENIO_DEPTO");
          }
        });
  }

  @Test
  @DisplayName("C1 comprueba que la obligación toma la modalidad del evento de emisión (§3.1)")
  void c1TakesTheModalityFromTheEmission() {
    GoldenCase c1 = byId("C1");

    assertThat(c1.input().at("/events/0/origin_modality"))
        .isEqualTo(c1.expected().at("/obligation/origin_modality"));
  }

  @Test
  @DisplayName("La dirección declarada de C7 y C8 es Ipiales y la de C9 es Pasto (§7)")
  void theDeclaredAddressesAreThoseOfTheDocument() {
    assertThat(byId("C7").input().at("/obligation/declared_address").toString())
        .isEqualTo("{\"same_as_jurisdiction\":true}");
    assertThat(byId("C8").input().at("/obligation/declared_address").toString())
        .isEqualTo("{\"same_as_jurisdiction\":true}");
    assertThat(byId("C9").input().at("/obligation/declared_address").toString())
        .isEqualTo("{\"same_as_jurisdiction\":false,\"name\":\"Pasto\"}");
  }

  @Test
  @DisplayName("Las entradas inferidas están declaradas con su motivo y su supuesto (006-03)")
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
              .allSatisfy(pointer ->
                  assertThat(goldenCase.input().at(pointer).isMissingNode()).as(pointer).isFalse());
        });
  }

  @Test
  @DisplayName(
      "Los supuestos son los de la frontera: B hipotética en C7 a C9, deuda dada en C4 y C5")
  void theAssumptionsAreThoseOfTheBoundary() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          assertThat(goldenCase.assumptions().contains("MODALIDAD_B_HIPOTETICA"))
              .as(goldenCase.id() + " · MODALIDAD_B_HIPOTETICA")
              .isEqualTo(HYPOTHETICAL_B.contains(goldenCase.id()));
          assertThat(goldenCase.assumptions().contains("DEUDA_DADA_A_LA_FECHA_DEL_PAGO"))
              .as(goldenCase.id() + " · DEUDA_DADA_A_LA_FECHA_DEL_PAGO")
              .isEqualTo(GIVEN_DEBT.contains(goldenCase.id()));
        });
  }

  @Test
  @DisplayName("Cada caso declara los valores de la configuración de los que depende (006-03)")
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
  @DisplayName("Los movimientos que salen de una regla llevan derived_from y los demás no (006-03)")
  void theDerivedMovementsSayWhereTheyComeFrom() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          Set<String> derived = new HashSet<>();
          goldenCase
              .expected()
              .path("movements")
              .forEach(movement -> {
                if (movement.has("derived_from")) {
                  derived.add(movement.get("type").stringValue());
                }
              });
          assertThat(derived)
              .as(goldenCase.id())
              .isEqualTo(DERIVED.getOrDefault(goldenCase.id(), Set.of()));
        });
  }

  @Test
  @DisplayName("Solo los reversos de C6 llevan reverses, y apuntan al pago y al descuento (§6.2)")
  void onlyTheReversalsCarryReverses() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          List<String> reverses = new ArrayList<>();
          goldenCase
              .expected()
              .path("movements")
              .forEach(movement -> {
                boolean reversal = "REVERSAL".equals(movement.get("type").stringValue());
                assertThat(movement.has("reverses")).as(goldenCase.id()).isEqualTo(reversal);
                if (reversal) {
                  reverses.add(movement.get("reverses").stringValue());
                }
              });
          assertThat(reverses)
              .as(goldenCase.id())
              .containsExactlyInAnyOrderElementsOf(
                  goldenCase.id().equals("C6")
                      ? List.of("PAYMENT", "EARLY_PAYMENT_DISCOUNT")
                      : List.of());
        });
  }

  // ---- the examples add up (they are checked, not calculated) ----

  @Test
  @DisplayName("El saldo de impuesto esperado es la suma de los movimientos esperados (§2)")
  void theExpectedTaxBalanceIsTheSumOfTheExpectedMovements() {
    assertThat(cases())
        .filteredOn(goldenCase -> goldenCase.expected().has("movements"))
        .filteredOn(goldenCase -> goldenCase.expected().has("balances"))
        .isNotEmpty()
        .allSatisfy(goldenCase -> {
          BigDecimal sum = BigDecimal.ZERO;
          for (JsonNode movement : goldenCase.expected().get("movements")) {
            sum = sum.add(movement.get("signed_amount").decimalValue());
          }
          assertThat(goldenCase.expected().at("/balances/TAX").decimalValue())
              .as(goldenCase.id())
              .isEqualByComparingTo(sum);
        });
  }

  @Test
  @DisplayName(
      "En C4 y C5 la suma imputada es el pago y ningún saldo queda negativo (IM-01, IM-03)")
  void inC4AndC5TheAllocationAddsUpToThePaymentAndNoBalanceIsNegative() {
    assertThat(List.of(byId("C4"), byId("C5")))
        .allSatisfy(goldenCase -> {
          BigDecimal paid = paymentAmount(goldenCase);
          Map<String, BigDecimal> debt = openingDebt(goldenCase);
          BigDecimal allocated = BigDecimal.ZERO;
          for (JsonNode allocation : goldenCase.expected().get("allocations")) {
            BigDecimal amount = allocation.get("amount").decimalValue();
            allocated = allocated.add(amount);
            assertThat(debt.get(allocation.get("component").stringValue()).subtract(amount))
                .as(goldenCase.id() + " · saldo de " + allocation.get("component"))
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
          }
          assertThat(allocated).as(goldenCase.id()).isEqualByComparingTo(paid);
        });
  }

  @Test
  @DisplayName("En C4 los saldos esperados son la deuda menos lo imputado (§5.2)")
  void inC4TheBalancesAreTheDebtMinusTheAllocation() {
    GoldenCase c4 = byId("C4");
    Map<String, BigDecimal> debt = openingDebt(c4);
    BigDecimal total = BigDecimal.ZERO;

    for (JsonNode allocation : c4.expected().get("allocations")) {
      String component = allocation.get("component").stringValue();
      BigDecimal balance = debt.get(component).subtract(allocation.get("amount").decimalValue());
      assertThat(c4.expected().at("/balances/" + component).decimalValue())
          .as(component)
          .isEqualByComparingTo(balance);
      total = total.add(balance);
    }
    assertThat(c4.expected().get("total_balance").decimalValue()).isEqualByComparingTo(total);
  }

  @Test
  @DisplayName("La distribución suma el dinero recibido y no incluye el descuento (§7, C8)")
  void theDistributionAddsUpToTheMoneyReceivedWithoutTheDiscount() {
    assertThat(List.of(byId("C7"), byId("C8"), byId("C9")))
        .allSatisfy(goldenCase -> {
          BigDecimal received = BigDecimal.ZERO;
          for (JsonNode movement : goldenCase.input().at("/events/0/movements")) {
            if ("PAYMENT".equals(movement.get("type").stringValue())) {
              received = received.add(movement.get("signed_amount").decimalValue().negate());
            }
          }
          BigDecimal distributed = BigDecimal.ZERO;
          for (JsonNode line : goldenCase.expected().get("distribution")) {
            distributed = distributed.add(line.get("amount").decimalValue());
          }
          assertThat(distributed).as(goldenCase.id()).isEqualByComparingTo(received);
          assertThat(received)
              .as(goldenCase.id() + " · el pago de la imputación")
              .isEqualByComparingTo(
                  goldenCase.input().at("/events/0/payment/amount").decimalValue());
        });
    assertThat(hasMovementOfType(byId("C8"), "EARLY_PAYMENT_DISCOUNT"))
        .as("C8 trae el descuento en la imputación, para probar que no se distribuye")
        .isTrue();
  }

  // ---- conversion to the input of the adapter (006-04) ----

  @Test
  @DisplayName("Cada caso se convierte en la entrada del adaptador sin error (006-04)")
  void everyCaseConvertsToTheAdapterInput() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          LedgerGoldenInput input = LedgerGoldenInputs.from(goldenCase);
          JsonNode tree = goldenCase.input();
          assertThat(input.events()).hasSize(tree.get("events").size());
          for (int index = 0; index < input.events().size(); index++) {
            assertThat(input.events().get(index))
                .as(goldenCase.id() + " · evento " + index)
                .isInstanceOf(eventClass(tree.at("/events/" + index + "/type").stringValue()));
          }
          assertThat(input.openingMovements()).hasSize(tree.path("opening_movements").size());
          assertThat(input.obligation() != null).isEqualTo(tree.has("obligation"));
        });
  }

  @Test
  @DisplayName("La conversión lee cada valor tal como está en el fixture (006-04)")
  void theConversionReadsEveryValueAsItIs() {
    LedgerGoldenInput c2 = LedgerGoldenInputs.from(byId("C2"));
    LedgerGoldenInput c4 = LedgerGoldenInputs.from(byId("C4"));
    LedgerGoldenInput c9 = LedgerGoldenInputs.from(byId("C9"));

    assertThat(c2.events().get(0)).isInstanceOf(LedgerGoldenInput.Emission.class);
    LedgerGoldenInput.Emission emission = (LedgerGoldenInput.Emission) c2.events().get(0);
    assertThat(emission.originModality()).isEqualTo("UEDF_INTERNACION");
    assertThat(emission.netTax()).isEqualByComparingTo(amountAt(byId("C2"), "/events/0/net_tax"));
    LedgerGoldenInput.Payment payment = (LedgerGoldenInput.Payment) c2.events().get(1);
    assertThat(payment.date())
        .isEqualTo(LocalDate.parse(byId("C2").input().at("/events/1/date").stringValue()));
    assertThat(payment.amount()).isEqualByComparingTo(amountAt(byId("C2"), "/events/1/amount"));
    assertThat(payment.status()).isEqualTo("CONCILIADO");
    assertThat(c4.openingMovements().get(0).signedAmount())
        .isEqualByComparingTo(amountAt(byId("C4"), "/opening_movements/0/signed_amount"));
    assertThat(c9.obligation().declaredAddress())
        .isEqualTo(new LedgerGoldenInput.DeclaredAddress(false, "Pasto"));
    LedgerGoldenInput.Allocation allocation = (LedgerGoldenInput.Allocation) c9.events().get(0);
    assertThat(allocation.movements()).hasSize(1);
    assertThat(allocation.paymentAmount())
        .isEqualByComparingTo(amountAt(byId("C9"), "/events/0/payment/amount"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("inputsOfTheWrongShape")
  @DisplayName("Una entrada mal formada falla al convertirse y no se lee como 0 ni false (006-04)")
  void anInputOfTheWrongShapeFailsToConvert(String caseId, Consumer<ObjectNode> change) {
    GoldenCase table = byId(caseId);
    ObjectNode input = (ObjectNode) table.input().deepCopy();
    change.accept(input);
    GoldenCase damaged =
        new GoldenCase(
            table.id(),
            table.rules(),
            table.description(),
            table.source(),
            table.assumptions(),
            table.inferredInputs(),
            table.dependsOn(),
            input,
            table.expected(),
            table.comparison(),
            table.configuration());

    assertThatThrownBy(() -> LedgerGoldenInputs.from(damaged))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static Stream<Arguments> inputsOfTheWrongShape() {
    return Stream.of(
        wrongInput(
            "el valor del pago como texto",
            "C2",
            input -> ((ObjectNode) input.at("/events/1")).put("amount", "308000")),
        wrongInput(
            "la fecha del pago sin formato ISO",
            "C2",
            input -> ((ObjectNode) input.at("/events/1")).put("date", "20-04-2026")),
        wrongInput(
            "la fecha del pago como número",
            "C2",
            input -> ((ObjectNode) input.at("/events/1")).put("date", 20260420)),
        wrongInput(
            "un tipo de evento desconocido",
            "C2",
            input -> ((ObjectNode) input.at("/events/0")).put("type", "REFUND")),
        wrongInput(
            "un evento sin tipo",
            "C2",
            input -> ((ObjectNode) input.at("/events/0")).remove("type")),
        wrongInput(
            "el estado del pago como número",
            "C2",
            input -> ((ObjectNode) input.at("/events/1")).put("status", 1)),
        wrongInput(
            "la modalidad de la emisión como número",
            "C2",
            input -> ((ObjectNode) input.at("/events/0")).put("origin_modality", 7)),
        wrongInput(
            "falta el neto de la emisión",
            "C2",
            input -> ((ObjectNode) input.at("/events/0")).remove("net_tax")),
        wrongInput("los eventos como objeto", "C2", input -> input.putObject("events")),
        wrongInput("los eventos ausentes", "C2", input -> input.remove("events")),
        wrongInput(
            "un movimiento de apertura como texto",
            "C4",
            input ->
                ((ObjectNode) input.at("/opening_movements/0")).put("signed_amount", "342000")),
        wrongInput(
            "un componente de apertura como número",
            "C4",
            input -> ((ObjectNode) input.at("/opening_movements/1")).put("component", 3)),
        wrongInput("la apertura como texto", "C4", input -> input.put("opening_movements", "x")),
        wrongInput(
            "la dirección idéntica como texto",
            "C7",
            input ->
                ((ObjectNode) input.at("/obligation/declared_address"))
                    .put("same_as_jurisdiction", "true")),
        wrongInput(
            "otro municipio sin nombre",
            "C9",
            input -> ((ObjectNode) input.at("/obligation/declared_address")).remove("name")),
        wrongInput(
            "la dirección ausente",
            "C9",
            input -> ((ObjectNode) input.get("obligation")).remove("declared_address")),
        wrongInput(
            "los movimientos de la imputación ausentes",
            "C7",
            input -> ((ObjectNode) input.at("/events/0")).remove("movements")),
        wrongInput(
            "el valor del pago de la imputación como texto",
            "C7",
            input -> ((ObjectNode) input.at("/events/0/payment")).put("amount", "2012000")));
  }

  private static Arguments wrongInput(String name, String caseId, Consumer<ObjectNode> change) {
    return Arguments.of(Named.of(name, caseId), change);
  }

  // ---- damaged variants (006-05) ----

  /** A synthetic valid fixture: its values are not tax values and it is not a golden case. */
  private static String validText() {
    return """
    document: {code: "Doc. 04", version: "9.9", section: "§0.0"}
    configuration:
      file: "%s"
      sha256: "%s"
    cases:
      - id: "C1"
        row: 1
        rules: ["CA-4.01", "LQ-C04"]
        description: "Caso sintético uno"
        assumptions: ["ENTRADA_INFERIDA"]
        inferred_inputs:
          - {field: "/events/0/net_tax_with_discount", reason: "motivo sintético"}
        depends_on: ["recaudo"]
        input:
          events:
            - {type: "EMISSION", net_tax: 1111, net_tax_with_discount: 999, origin_modality: "UEDF_INTERNACION"}
            - {type: "PAYMENT", date: 2030-01-10, amount: 999, status: "CONCILIADO"}
            - {type: "BANK_REVERSAL"}
        expected:
          obligation: {original_principal: 1111, due_date: 2030-02-01, origin_modality: "UEDF_INTERNACION"}
          movements:
            - {type: "ASSESSMENT", component: "TAX", signed_amount: 1111}
            - {type: "REVERSAL", component: "TAX", signed_amount: 999, reverses: "PAYMENT", derived_from: "§0.0"}
          balances: {TAX: 1111}
          status: "PAGADA"
          payment_status: "REVERSADO"
      - id: "C2"
        rules: ["CA-4.04", "IM-01", "IM-03"]
        description: "Caso sintético dos"
        input:
          opening_movements:
            - {type: "ASSESSMENT", component: "TAX", signed_amount: 2000}
            - {type: "INTEREST_ACCRUAL", component: "INTEREST", signed_amount: 200}
          events:
            - {type: "PAYMENT", date: 2030-03-01, amount: 700, status: "CONCILIADO"}
        expected:
          allocations:
            - {component: "TAX", amount: 637}
            - {component: "INTEREST", amount: 63}
          total_balance: 1500
      - id: "C3"
        rules: ["CA-4.09"]
        description: "Caso sintético tres"
        input:
          obligation:
            origin_modality: "CONVENIO_DEPTO"
            declared_address: {same_as_jurisdiction: false, name: "Otro"}
          events:
            - type: "ALLOCATION"
              payment: {status: "CONCILIADO", amount: 3000}
              movements:
                - {type: "PAYMENT", component: "TAX", signed_amount: -3000}
        expected:
          distribution:
            - {beneficiary: "DEPARTAMENTO_NARINO", amount: 2400}
            - {beneficiary: "MUNICIPIO_PASTO", amount: 600, disposition: "GIRADO"}
    """
        .formatted(IPIALES_FILE, IPIALES_SHA256);
  }

  @Test
  @DisplayName("El fixture sintético válido no tiene problemas (006-05)")
  void theValidControlHasNoProblems() {
    JsonNode valid = SafeYamlLoader.load(validText());

    assertThat(
            GoldenFixtures.problems(GoldenFixtures.schema(MODULE), valid, LedgerGoldenRules.INPUT))
        .isEmpty();
    assertThat(GoldenFixtures.cases(valid))
        .extracting(GoldenCase::id)
        .containsExactly("C1", "C2", "C3");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("damagedVariants")
  @DisplayName(
      "Una variante dañada del esquema de la cuenta corriente se rechaza solo por su motivo (006-05)")
  void aDamagedVariantIsRejectedForTheRightReason(String from, String to, List<Problem> expected) {
    JsonNode damaged = SafeYamlLoader.load(replaceOnce(validText(), from, to));

    assertThat(
            GoldenFixtures.problems(
                GoldenFixtures.schema(MODULE), damaged, LedgerGoldenRules.INPUT))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  private static final String C1_EVENTS = "      events:\n        - {type: \"EMISSION\"";

  private static final String C3_OBLIGATION =
      "      obligation:\n"
          + "        origin_modality: \"CONVENIO_DEPTO\"\n"
          + "        declared_address: {same_as_jurisdiction: false, name: \"Otro\"}\n";

  static Stream<Arguments> damagedVariants() {
    return Stream.of(
        variant(
            "un monto entre comillas",
            "signed_amount: 2000}",
            "signed_amount: \"2000\"}",
            problem("/cases/1/input/opening_movements/0/signed_amount", "type")),
        variant(
            "un monto en pesos con decimales",
            "original_principal: 1111",
            "original_principal: 1111.50",
            problem("/cases/0/expected/obligation/original_principal", "type")),
        variant(
            "un caso sin expected",
            "    expected:\n      distribution:\n"
                + "        - {beneficiary: \"DEPARTAMENTO_NARINO\", amount: 2400}\n"
                + "        - {beneficiary: \"MUNICIPIO_PASTO\", amount: 600, disposition: \"GIRADO\"}\n",
            "",
            problem("/cases/2/expected", "required")),
        variant(
            "una clave desconocida en un evento",
            "amount: 700, status: \"CONCILIADO\"}",
            "amount: 700, status: \"CONCILIADO\", unknown: 1}",
            problem("/cases/1/input/events/0/unknown", "additionalProperties")),
        variant(
            "una comparación en un fixture de la cuenta corriente",
            "cases:\n",
            "comparison:\n  total_balance: {scale: 2, rounding: \"HALF_UP\"}\ncases:\n",
            problem("/comparison", "additionalProperties")),
        variant(
            "un depends_on fuera de la lista",
            "depends_on: [\"recaudo\"]",
            "depends_on: [\"recaudo.otro\"]",
            problem("/cases/0/depends_on/0", "enum")),
        variant(
            "un código de regla fuera de la lista",
            "rules: [\"CA-4.01\", \"LQ-C04\"]",
            "rules: [\"CA-4.01\", \"LQ-C03\"]",
            problem("/cases/0/rules/1", "enum")),
        variant(
            "un supuesto fuera de la lista",
            "assumptions: [\"ENTRADA_INFERIDA\"]",
            "assumptions: [\"ENTRADA_INVENTADA\"]",
            problem("/cases/0/assumptions/0", "enum")),
        variant(
            "una entrada inferida que no es un puntero a la entrada",
            "field: \"/events/0/net_tax_with_discount\"",
            "field: \"net_tax_with_discount\"",
            problem("/cases/0/inferred_inputs/0/field", "pattern")),
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
            problem("/cases/0/expected/obligation/due_date", "pattern")),
        variant(
            "un id fuera de C1 a C9",
            "id: \"C2\"",
            "id: \"C10\"",
            problem("/cases/1/id", "pattern")),
        variant(
            "un documento que no es de la cuenta corriente",
            "code: \"Doc. 04\"",
            "code: \"Doc. 03\"",
            problem("/document/code", "const")),
        variant(
            "un tipo de evento desconocido",
            "{type: \"BANK_REVERSAL\"}",
            "{type: \"REFUND\"}",
            problem("/cases/0/input/events/2/type", "enum")),
        variant(
            "un pago sin fecha",
            "date: 2030-01-10, ",
            "",
            problem("/cases/0/input/events/1/date", "required")),
        variant(
            "un pago que no está conciliado",
            "amount: 999, status: \"CONCILIADO\"",
            "amount: 999, status: \"PENDIENTE\"",
            problem("/cases/0/input/events/1/status", "const")),
        variant(
            "una modalidad desconocida en la emisión",
            "net_tax_with_discount: 999, origin_modality: \"UEDF_INTERNACION\"",
            "net_tax_with_discount: 999, origin_modality: \"ALGUNA\"",
            problem("/cases/0/input/events/0/origin_modality", "enum")),
        variant(
            "un componente desconocido",
            "{type: \"INTEREST_ACCRUAL\", component: \"INTEREST\"",
            "{type: \"INTEREST_ACCRUAL\", component: \"OTRO\"",
            problem("/cases/1/input/opening_movements/1/component", "enum")),
        variant(
            "un reverso que apunta a un tipo que no se reversa",
            "reverses: \"PAYMENT\"",
            "reverses: \"ASSESSMENT\"",
            problem("/cases/0/expected/movements/1/reverses", "enum")),
        variant(
            "un derived_from vacío",
            "derived_from: \"§0.0\"",
            "derived_from: \"\"",
            problem("/cases/0/expected/movements/1/derived_from", "minLength")),
        variant(
            "un estado que depende de «hoy»",
            "status: \"PAGADA\"",
            "status: \"VIGENTE\"",
            problem("/cases/0/expected/status", "const")),
        variant(
            "un saldo esperado vacío",
            "balances: {TAX: 1111}",
            "balances: {}",
            problem("/cases/0/expected/balances", "minProperties"),
            problem("/cases/0/expected/balances", "empty-expected")),
        variant(
            "un beneficiario desconocido",
            "beneficiary: \"MUNICIPIO_PASTO\"",
            "beneficiary: \"MUNICIPIO_OTRO\"",
            problem("/cases/2/expected/distribution/1/beneficiary", "enum")),
        variant(
            "un «se retiene o se gira» desconocido",
            "disposition: \"GIRADO\"",
            "disposition: \"DEVUELTO\"",
            problem("/cases/2/expected/distribution/1/disposition", "enum")),
        variant(
            "otro municipio sin nombre",
            "declared_address: {same_as_jurisdiction: false, name: \"Otro\"}",
            "declared_address: {same_as_jurisdiction: false}",
            problem("/cases/2/input/obligation/declared_address/name", "required")),
        variant(
            "la dirección del municipio de la configuración con nombre",
            "declared_address: {same_as_jurisdiction: false, name: \"Otro\"}",
            "declared_address: {same_as_jurisdiction: true, name: \"Otro\"}",
            problem("/cases/2/input/obligation/declared_address", "not")),
        variant(
            "la modalidad dada dos veces",
            C1_EVENTS,
            "      obligation:\n        origin_modality: \"UEDF_INTERNACION\"\n"
                + "        declared_address: {same_as_jurisdiction: true}\n"
                + C1_EVENTS,
            problem("/cases/0/input", "modality-twice")),
        variant(
            "una emisión y una apertura a la vez",
            C1_EVENTS,
            "      opening_movements:\n"
                + "        - {type: \"ASSESSMENT\", component: \"TAX\", signed_amount: 1111}\n"
                + C1_EVENTS,
            problem("/cases/0/input", "start-form")),
        variant(
            "un caso que no parte de nada",
            "      opening_movements:\n"
                + "        - {type: \"ASSESSMENT\", component: \"TAX\", signed_amount: 2000}\n"
                + "        - {type: \"INTEREST_ACCRUAL\", component: \"INTEREST\", signed_amount: 200}\n",
            "",
            problem("/cases/1/input", "start-form")),
        variant(
            "una imputación sin obligación",
            C3_OBLIGATION,
            "",
            problem("/cases/2/input/obligation", "allocation-obligation")),
        variant(
            "un reverso bancario sin pago",
            "        - {type: \"PAYMENT\", date: 2030-01-10, amount: 999, status: \"CONCILIADO\"}\n",
            "",
            problem("/cases/0/input/events/1", "reversal-payment")));
  }

  // ---- the comparison finds a difference in a real case (006-05) ----

  @Test
  @DisplayName("El adaptador que devuelve lo esperado pasa en los nueve casos (006-05)")
  void anAdapterReturningTheExpectedPasses() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          assertThat(GoldenComparator.differences(goldenCase, expectedAsResult(goldenCase)))
              .as(goldenCase.id())
              .isEmpty();
          GoldenComparator.assertMatches(goldenCase, expectedAsResult(goldenCase));
        });
  }

  @Test
  @DisplayName("Un adaptador que no devuelve resultado falla en todos los casos (006-05)")
  void noResultFailsEveryCase() {
    assertThat(cases())
        .allSatisfy(
            goldenCase -> assertThat(GoldenComparator.differences(goldenCase, null)).isNotEmpty());
  }

  @Test
  @DisplayName("El orden de las listas no importa: la comparación es de multiconjunto (006-05)")
  void theOrderOfTheListsDoesNotMatter() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          ObjectNode tree = (ObjectNode) expectedAsResult(goldenCase).asTree().deepCopy();
          tree.forEach(value -> {
            if (value instanceof ArrayNode list) {
              List<JsonNode> reversed = new ArrayList<>(list.valueStream().toList());
              Collections.reverse(reversed);
              list.removeAll();
              reversed.forEach(list::add);
            }
          });

          assertThat(GoldenComparator.differences(goldenCase, () -> tree))
              .as(goldenCase.id())
              .isEmpty();
        });
  }

  @Test
  @DisplayName("Un descuento indebido en C3 falla aunque todos los demás movimientos coincidan")
  void anUndueDiscountInC3IsDetected() {
    GoldenCase c3 = byId("C3");
    ObjectNode tree = (ObjectNode) expectedAsResult(c3).asTree().deepCopy();
    ObjectNode discount = ((ArrayNode) tree.get("movements")).addObject();
    discount.put("type", "EARLY_PAYMENT_DISCOUNT");
    discount.put("component", "TAX");
    discount.put("signed_amount", -34000);
    discount.putNull("reverses");

    assertThat(GoldenComparator.differences(c3, () -> tree))
        .isNotEmpty()
        .allSatisfy(difference -> assertThat(difference).startsWith("/movements:"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mutations")
  @DisplayName("Cualquier diferencia en un valor esperado de un caso real falla (006-03, 006-05)")
  void aMutationOfARealCaseIsDetected(
      GoldenCase goldenCase, String key, Consumer<ObjectNode> change) {
    ObjectNode tree = (ObjectNode) expectedAsResult(goldenCase).asTree().deepCopy();
    change.accept(tree);

    List<String> differences = GoldenComparator.differences(goldenCase, () -> tree);

    assertThat(differences).isNotEmpty();
    assertThat(differences)
        .allSatisfy(difference -> assertThat(difference).matches("^/" + key + "[:/].*"));
  }

  /** For every value of every real case: the changes the comparison must not let pass. */
  static Stream<Arguments> mutations() {
    List<Arguments> arguments = new ArrayList<>();
    for (GoldenCase goldenCase : cases()) {
      for (Map.Entry<String, JsonNode> entry : goldenCase.expected().properties()) {
        List<Mutation> found = new ArrayList<>();
        collect(entry.getValue(), List.of(entry.getKey()), found);
        for (Mutation mutation : found) {
          String name = goldenCase.id() + " · " + mutation.where() + " · " + mutation.name();
          arguments.add(
              Arguments.of(Named.of(name, goldenCase), entry.getKey(), mutation.change()));
        }
      }
    }
    return arguments.stream();
  }

  private static void collect(JsonNode node, List<String> path, List<Mutation> out) {
    String key = path.get(path.size() - 1);
    if ("derived_from".equals(key)) {
      return; // a datum of the fixture: the result does not carry it
    }
    String where = "/" + String.join("/", path);
    out.add(new Mutation(where, "nulo", tree -> set(tree, path, NullNode.getInstance())));
    out.add(new Mutation(where, "ausente", tree -> remove(tree, path)));
    if (node.isObject()) {
      node.properties()
          .forEach(child -> collect(child.getValue(), append(path, child.getKey()), out));
    } else if (node.isArray()) {
      listMutations(node, path, where, out);
    } else if (node.isNumber()) {
      numberMutations(node, path, where, out);
    } else if (node.isString()) {
      textMutations(node.stringValue(), key, path, where, out);
    }
  }

  private static void listMutations(
      JsonNode list, List<String> path, String where, List<Mutation> out) {
    out.add(
        new Mutation(
            where, "lista vacía", tree -> set(tree, path, JsonNodeFactory.instance.arrayNode())));
    out.add(
        new Mutation(where, "no es una lista", tree -> set(tree, path, StringNode.valueOf("x"))));
    out.add(
        new Mutation(
            where,
            "un elemento de más",
            tree -> ((ArrayNode) at(tree, path)).add(at(tree, append(path, "0")).deepCopy())));
    for (int index = 0; index < list.size(); index++) {
      List<String> element = append(path, String.valueOf(index));
      out.add(
          new Mutation(where + "/" + index, "un elemento de menos", tree -> remove(tree, element)));
      collect(list.get(index), element, out);
    }
  }

  private static void numberMutations(
      JsonNode value, List<String> path, String where, List<Mutation> out) {
    BigDecimal number = value.decimalValue();
    for (String step : List.of("1", "0.1")) {
      out.add(
          new Mutation(
              where,
              "más " + step,
              tree -> set(tree, path, DecimalNode.valueOf(number.add(new BigDecimal(step))))));
      out.add(
          new Mutation(
              where,
              "menos " + step,
              tree -> set(tree, path, DecimalNode.valueOf(number.subtract(new BigDecimal(step))))));
    }
    out.add(
        new Mutation(
            where,
            "como texto",
            tree -> set(tree, path, StringNode.valueOf(number.toPlainString()))));
    out.add(
        new Mutation(
            where,
            "como double",
            tree -> set(tree, path, DoubleNode.valueOf(number.doubleValue()))));
  }

  private static void textMutations(
      String text, String key, List<String> path, String where, List<Mutation> out) {
    out.add(
        new Mutation(where, "otro texto", tree -> set(tree, path, StringNode.valueOf(text + "X"))));
    out.add(
        new Mutation(
            where,
            "texto como número",
            tree -> set(tree, path, DecimalNode.valueOf(BigDecimal.ONE))));
    if (DATE.matcher(text).matches()) {
      LocalDate date = LocalDate.parse(text);
      out.add(
          new Mutation(
              where,
              "un día más",
              tree -> set(tree, path, StringNode.valueOf(date.plusDays(1).toString()))));
      out.add(
          new Mutation(
              where,
              "un día menos",
              tree -> set(tree, path, StringNode.valueOf(date.minusDays(1).toString()))));
    }
    Map<String, String> swaps = SWAPS.get(key);
    if (swaps != null) {
      out.add(
          new Mutation(
              where,
              SWAP_NAMES.get(key),
              tree -> set(tree, path, StringNode.valueOf(swaps.get(text)))));
    }
  }

  private static List<String> append(List<String> path, String segment) {
    List<String> longer = new ArrayList<>(path);
    longer.add(segment);
    return List.copyOf(longer);
  }

  private static JsonNode at(JsonNode root, List<String> path) {
    JsonNode node = root;
    for (String segment : path) {
      node = node.isArray() ? node.get(Integer.parseInt(segment)) : node.get(segment);
    }
    return node;
  }

  private static void set(ObjectNode root, List<String> path, JsonNode value) {
    JsonNode parent = at(root, path.subList(0, path.size() - 1));
    String last = path.get(path.size() - 1);
    if (parent instanceof ArrayNode list) {
      list.set(Integer.parseInt(last), value);
    } else {
      ((ObjectNode) parent).set(last, value);
    }
  }

  private static void remove(ObjectNode root, List<String> path) {
    JsonNode parent = at(root, path.subList(0, path.size() - 1));
    String last = path.get(path.size() - 1);
    if (parent instanceof ArrayNode list) {
      list.remove(Integer.parseInt(last));
    } else {
      ((ObjectNode) parent).remove(last);
    }
  }

  // ---- LedgerGoldenTest is disabled only with its issue (006-06, 006-07) ----

  /** A reason that starts with the number of its issue. */
  private static final Pattern ISSUE = Pattern.compile("^Issue #(\\d+)");

  /** The package of the conditional annotations of JUnit (operating system, JRE, properties). */
  private static final String JUNIT_CONDITIONS = "org.junit.jupiter.api.condition";

  /** The package of {@code @ParameterizedTest}: its own extensions are not a way to skip a case. */
  private static final String JUNIT_PARAMS = "org.junit.jupiter.params";

  /** The criteria of the spec, each with the codes that tag its method. */
  private static final List<List<String>> CRITERIA =
      List.of(
          List.of("CA-4.01", "LQ-C04"),
          List.of("CA-4.02"),
          List.of("CA-4.03"),
          List.of("CA-4.04", "IM-01", "IM-03"),
          List.of("CA-4.08"),
          List.of("CA-4.09"));

  /**
   * LedgerGoldenTest with its fields, constructors, methods, parameters and nested classes:
   * everywhere a {@code @Disabled}, a condition or an extension could skip a case.
   */
  private static List<AnnotatedElement> goldenTestElements() {
    List<AnnotatedElement> elements = new ArrayList<>();
    addMembers(LedgerGoldenTest.class, elements);
    return elements;
  }

  private static void addMembers(Class<?> type, List<AnnotatedElement> elements) {
    elements.add(type);
    elements.addAll(Arrays.asList(type.getDeclaredFields()));
    elements.addAll(Arrays.asList(type.getDeclaredConstructors()));
    for (Method method : type.getDeclaredMethods()) {
      elements.add(method);
      elements.addAll(Arrays.asList(method.getParameters()));
    }
    for (Class<?> nested : type.getDeclaredClasses()) {
      addMembers(nested, elements);
    }
  }

  /** The reasons of every {@code @Disabled}, also the ones inside an annotation of the project. */
  private static List<String> disabledReasons() {
    return goldenTestElements().stream()
        .flatMap(element -> AnnotationSupport.findAnnotation(element, Disabled.class).stream())
        .map(Disabled::value)
        .toList();
  }

  @Test
  @DisplayName(
      "Todo @Disabled de LedgerGoldenTest, de clase o de método, cita el mismo issue (006-06)")
  void everyDisabledCitesTheSameIssue() {
    List<String> reasons = disabledReasons();

    assertThat(reasons).isNotEmpty();
    assertThat(reasons).allSatisfy(reason -> assertThat(reason).containsPattern(ISSUE));
    assertThat(
            reasons.stream()
                .map(ISSUE::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .distinct())
        .hasSizeLessThanOrEqualTo(1);
  }

  @Test
  @DisplayName(
      "LedgerGoldenTest no tiene condiciones de JUnit ni extensiones que omitan casos (006-06)")
  void noConditionAndNoExtensionCanSkipACase() {
    assertAll(
        goldenTestElements().stream()
            .map(element ->
                () ->
                    assertThat(conditionsAndExtensions(element)).as(element.toString()).isEmpty()));
  }

  /** The conditions and the extensions of an element, also inside annotations of the project. */
  private static List<String> conditionsAndExtensions(AnnotatedElement element) {
    List<String> found = new ArrayList<>();
    for (Annotation annotation : element.getAnnotations()) {
      collectConditions(annotation.annotationType(), found, new HashSet<>());
    }
    // Repeated and meta-present @ExtendWith too (JUnit wraps repeated ones in @Extensions).
    for (ExtendWith extendWith :
        AnnotationSupport.findRepeatableAnnotations(element, ExtendWith.class)) {
      for (Class<?> extension : extendWith.value()) {
        if (!extension.getPackageName().startsWith(JUNIT_PARAMS)) {
          found.add(extension.getName());
        }
      }
    }
    if (element.isAnnotationPresent(RegisterExtension.class)) {
      found.add(RegisterExtension.class.getName());
    }
    return found;
  }

  /** Adds the type if it is a JUnit condition, and searches the annotations of the project. */
  private static void collectConditions(
      Class<? extends Annotation> type, List<String> found, Set<Class<?>> seen) {
    if (!seen.add(type)) {
      return;
    }
    if (type.getPackageName().equals(JUNIT_CONDITIONS)) {
      found.add(type.getName());
    }
    // Not searched inside the annotations of JUnit (@ParameterizedTest itself carries an
    // extension); only the annotations of the project, which could hide one, are searched.
    if (!type.getPackageName().startsWith("org.junit.")
        && !type.getPackageName().startsWith("java.")) {
      for (Annotation meta : type.getAnnotations()) {
        collectConditions(meta.annotationType(), found, seen);
      }
    }
  }

  @Test
  @DisplayName(
      "Una cuenta corriente real no queda oculta por el @Disabled de la clase: se mueve a los métodos (006-06)")
  void aRealLedgerIsNotHiddenByAClassLevelDisabled() {
    boolean ledgerExists = !(LedgerGoldenEngines.current() instanceof NotImplementedLedgerEngine);

    assertThat(
            ledgerExists && AnnotationSupport.isAnnotated(LedgerGoldenTest.class, Disabled.class))
        .as(
            "current() ya devuelve la cuenta corriente real; el @Disabled de la clase oculta los casos")
        .isFalse();
  }

  @Test
  @DisplayName(
      "Mientras no hay cuenta corriente, cada invocación en rojo falla por GoldenEngineNotImplementedException (006-04)")
  void whileThereIsNoLedgerEveryInvocationFailsForThatReason() {
    if (!(LedgerGoldenEngines.current() instanceof NotImplementedLedgerEngine)) {
      return; // Phase 4: the golden test itself decides.
    }
    List<GoldenCase> invocations =
        goldenMethodCodes().stream()
            .flatMap(codes -> LedgerGoldenTest.casesFor(codes).stream())
            .toList();

    assertThat(invocations).hasSize(IDS.size());
    assertAll(
        invocations.stream()
            .map(goldenCase ->
                () ->
                    assertThatThrownBy(() -> LedgerGoldenTest.assertCase(goldenCase))
                        .as(goldenCase.id())
                        .isInstanceOf(GoldenEngineNotImplementedException.class)));
  }

  /** The codes each criterion method of LedgerGoldenTest runs. */
  private static List<List<String>> goldenMethodCodes() {
    return goldenMethods().stream()
        .map(method -> List.of(method.getAnnotation(LedgerGoldenTest.RuleCases.class).value()))
        .toList();
  }

  private static List<Method> goldenMethods() {
    return Arrays.stream(LedgerGoldenTest.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(ParameterizedTest.class))
        .toList();
  }

  @Test
  @DisplayName(
      "LedgerGoldenTest tiene un método por criterio de la spec, con las etiquetas de sus códigos y sus casos (006-07)")
  void thereIsOneMethodPerCriterionAndEachHasCases() {
    Set<String> codes = RULES.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

    assertThat(
            AnnotationSupport.findRepeatableAnnotations(LedgerGoldenTest.class, Tag.class).stream()
                .map(Tag::value))
        .containsExactly("golden");
    assertThat(goldenMethods())
        .allSatisfy(method ->
            assertThat(AnnotationSupport.findRepeatableAnnotations(method, Tag.class))
                .as(method.getName() + " · las etiquetas son los códigos de su criterio")
                .extracting(Tag::value)
                .containsExactlyElementsOf(
                    List.of(method.getAnnotation(LedgerGoldenTest.RuleCases.class).value())));
    assertThat(goldenMethodCodes()).containsExactlyInAnyOrderElementsOf(CRITERIA);
    assertThat(CRITERIA.stream().flatMap(List::stream).toList())
        .containsExactlyInAnyOrderElementsOf(codes);
    assertThat(CRITERIA)
        .allSatisfy(criterion ->
            assertThat(LedgerGoldenTest.casesFor(criterion))
                .as(criterion + " · casos")
                .isNotEmpty());
    assertThat(schemaRuleCodes()).containsExactlyInAnyOrderElementsOf(codes);
  }

  @Test
  @DisplayName("Cada caso corre en exactamente un método: el de su criterio (006-07)")
  void everyCaseRunsInExactlyOneMethod() {
    assertThat(cases())
        .allSatisfy(goldenCase ->
            assertThat(
                    goldenMethodCodes().stream()
                        .filter(codes -> LedgerGoldenTest.casesFor(codes).contains(goldenCase))
                        .count())
                .as(goldenCase.id())
                .isEqualTo(1));
  }

  /** The codes the schema accepts in {@code rules}. */
  private static List<String> schemaRuleCodes() {
    JsonNode schema =
        CommonCases.EXACT.readTree(GoldenFixtures.GOLDEN.resolve("ledger.schema.json").toFile());
    return schema
        .at("/$defs/case/properties/rules/items/enum")
        .valueStream()
        .map(JsonNode::stringValue)
        .toList();
  }

  @Test
  @DisplayName("El nombre visible de un caso lleva sus códigos, su id y su descripción (006-07)")
  void theDisplayNameCarriesTheCodesTheIdAndTheDescription() {
    assertThat(cases())
        .allSatisfy(goldenCase -> {
          String name = LedgerGoldenTest.displayName(goldenCase);
          assertThat(name)
              .startsWith(LedgerGoldenTest.codes(goldenCase) + " · ")
              .contains(" · " + goldenCase.id() + " · ")
              .endsWith(goldenCase.description());
        });
  }

  // ---- IM-01 and IM-03 are checked on the result (CA-4.04) ----

  @Test
  @DisplayName("Un resultado que respeta IM-01 e IM-03 no tiene problemas en C4 ni en C5")
  void aResultThatRespectsTheInvariantsHasNoProblems() {
    assertThat(List.of(byId("C4"), byId("C5")))
        .allSatisfy(goldenCase ->
            assertThat(
                    LedgerGoldenTest.allocationProblems(
                        LedgerGoldenInputs.from(goldenCase), expectedAsResult(goldenCase)))
                .as(goldenCase.id())
                .isEmpty());
  }

  @Test
  @DisplayName("Un resultado que imputa de más o de menos que el pago falla en IM-01")
  void anAllocationThatIsNotThePaymentBreaksIm01() {
    GoldenCase c4 = byId("C4");
    LedgerGoldenResult expected = expectedAsResult(c4);
    List<LedgerGoldenResult.Allocation> more =
        List.of(
            new LedgerGoldenResult.Allocation("TAX", new BigDecimal("191598")),
            new LedgerGoldenResult.Allocation("INTEREST", new BigDecimal("8403")));
    List<LedgerGoldenResult.Allocation> fewer = List.of(expected.allocations().get(0));

    assertThat(
            LedgerGoldenTest.allocationProblems(
                LedgerGoldenInputs.from(c4), withAllocations(expected, more)))
        .anyMatch(problem -> problem.startsWith("IM-01"));
    assertThat(
            LedgerGoldenTest.allocationProblems(
                LedgerGoldenInputs.from(c4), withAllocations(expected, fewer)))
        .anyMatch(problem -> problem.startsWith("IM-01"));
  }

  @Test
  @DisplayName("Un resultado que imputa más que la deuda de un componente falla en IM-03")
  void anAllocationAboveTheDebtBreaksIm03() {
    GoldenCase c4 = byId("C4");
    List<LedgerGoldenResult.Allocation> overInterest =
        List.of(
            new LedgerGoldenResult.Allocation("TAX", new BigDecimal("184000")),
            new LedgerGoldenResult.Allocation("INTEREST", new BigDecimal("16000")));

    assertThat(
            LedgerGoldenTest.allocationProblems(
                LedgerGoldenInputs.from(c4), withAllocations(expectedAsResult(c4), overInterest)))
        .anyMatch(problem -> problem.startsWith("IM-03"));
  }

  @Test
  @DisplayName("Un resultado con un saldo negativo falla en IM-03")
  void aNegativeBalanceBreaksIm03() {
    GoldenCase c4 = byId("C4");
    LedgerGoldenResult expected = expectedAsResult(c4);
    LedgerGoldenResult negative = withBalances(expected, Map.of("TAX", new BigDecimal("-1")));

    assertThat(LedgerGoldenTest.allocationProblems(LedgerGoldenInputs.from(c4), negative))
        .anyMatch(problem -> problem.startsWith("IM-03"));
  }

  private static LedgerGoldenResult withBalances(
      LedgerGoldenResult result, Map<String, BigDecimal> balances) {
    return new LedgerGoldenResult(
        result.obligation(),
        result.movements(),
        result.allocations(),
        balances,
        result.totalBalance(),
        result.status(),
        result.paymentStatus(),
        result.distribution());
  }

  private static LedgerGoldenResult withAllocations(
      LedgerGoldenResult result, List<LedgerGoldenResult.Allocation> allocations) {
    return new LedgerGoldenResult(
        result.obligation(),
        result.movements(),
        allocations,
        result.balances(),
        result.totalBalance(),
        result.status(),
        result.paymentStatus(),
        result.distribution());
  }

  // ---- helpers ----

  private static List<GoldenCase> cases() {
    return GoldenFixtures.cases(MODULE);
  }

  private static GoldenCase byId(String id) {
    return cases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
  }

  private static List<String> keys(JsonNode object) {
    return object.properties().stream().map(Map.Entry::getKey).toList();
  }

  private static boolean hasEvent(JsonNode input, String type) {
    return input.path("events").valueStream().anyMatch(e -> type.equals(text(e, "type")));
  }

  private static boolean hasMovementOfType(GoldenCase goldenCase, String type) {
    return goldenCase
        .input()
        .at("/events/0/movements")
        .valueStream()
        .anyMatch(movement -> type.equals(text(movement, "type")));
  }

  private static BigDecimal amountAt(GoldenCase goldenCase, String pointer) {
    return goldenCase.input().at(pointer).decimalValue();
  }

  private static BigDecimal paymentAmount(GoldenCase goldenCase) {
    return goldenCase.input().at("/events/0/amount").decimalValue();
  }

  /** The debt each component starts from: the sum of the opening movements. */
  private static Map<String, BigDecimal> openingDebt(GoldenCase goldenCase) {
    Map<String, BigDecimal> debt = new LinkedHashMap<>();
    for (JsonNode movement : goldenCase.input().get("opening_movements")) {
      debt.merge(
          movement.get("component").stringValue(),
          movement.get("signed_amount").decimalValue(),
          BigDecimal::add);
    }
    return debt;
  }

  private static Class<?> eventClass(String type) {
    return switch (type) {
      case "EMISSION" -> LedgerGoldenInput.Emission.class;
      case "PAYMENT" -> LedgerGoldenInput.Payment.class;
      case "BANK_REVERSAL" -> LedgerGoldenInput.BankReversal.class;
      case "ALLOCATION" -> LedgerGoldenInput.Allocation.class;
      default -> throw new IllegalArgumentException(type);
    };
  }

  /** The typed result a ledger would return if it gave exactly the expected values. */
  private static LedgerGoldenResult expectedAsResult(GoldenCase goldenCase) {
    JsonNode expected = goldenCase.expected();
    return new LedgerGoldenResult(
        expected.has("obligation")
            ? new LedgerGoldenResult.Obligation(
                amount(expected.get("obligation"), "original_principal"),
                date(expected.get("obligation"), "due_date"),
                text(expected.get("obligation"), "origin_modality"))
            : null,
        list(
            expected,
            "movements",
            movement ->
                new LedgerGoldenResult.Movement(
                    text(movement, "type"),
                    text(movement, "component"),
                    amount(movement, "signed_amount"),
                    text(movement, "reverses"))),
        list(
            expected,
            "allocations",
            allocation ->
                new LedgerGoldenResult.Allocation(
                    text(allocation, "component"), amount(allocation, "amount"))),
        balances(expected),
        amount(expected, "total_balance"),
        text(expected, "status"),
        text(expected, "payment_status"),
        list(
            expected,
            "distribution",
            line ->
                new LedgerGoldenResult.DistributionLine(
                    text(line, "beneficiary"), amount(line, "amount"), text(line, "disposition"))));
  }

  private static <T> List<T> list(JsonNode expected, String key, Function<JsonNode, T> toItem) {
    return expected.has(key) ? expected.get(key).valueStream().map(toItem).toList() : null;
  }

  private static Map<String, BigDecimal> balances(JsonNode expected) {
    if (!expected.has("balances")) {
      return null;
    }
    Map<String, BigDecimal> balances = new LinkedHashMap<>();
    expected
        .get("balances")
        .properties()
        .forEach(entry -> balances.put(entry.getKey(), entry.getValue().decimalValue()));
    return balances;
  }

  private static BigDecimal amount(JsonNode node, String key) {
    return node.has(key) ? node.get(key).decimalValue() : null;
  }

  private static LocalDate date(JsonNode node, String key) {
    return node.has(key) ? LocalDate.parse(node.get(key).stringValue()) : null;
  }

  private static String text(JsonNode node, String key) {
    return node.has(key) ? node.get(key).stringValue() : null;
  }

  /** Whether a dotted path exists in the configuration. */
  private static boolean resolves(JsonNode root, String path) {
    JsonNode node = root;
    for (String part : path.split("\\.")) {
      node = node.isObject() ? node.get(part) : null;
      if (node == null) {
        return false;
      }
    }
    return true;
  }

  private static Problem problem(String path, String rule) {
    return new Problem(path, rule);
  }

  /**
   * Arguments: the name of the variant, the text to replace, its replacement and the exact
   * problems.
   */
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
