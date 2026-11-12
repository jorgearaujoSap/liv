package co.com.sapenlinea.liv.ledger.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

import co.com.sapenlinea.liv.golden.GoldenCase;
import co.com.sapenlinea.liv.golden.GoldenComparator;
import co.com.sapenlinea.liv.golden.GoldenConfiguration;
import co.com.sapenlinea.liv.golden.GoldenFixtures;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;
import tools.jackson.databind.JsonNode;

/**
 * The golden cases of the ledger, C1 to C9: the examples of the Doc. 04 against the ledger. They
 * would fail while the ledger does not exist (Phase 4), so they carry {@code @Disabled} with their
 * issue and the CI skips them; how to run them red is in {@code
 * src/test/resources/golden/README.md}.
 *
 * <p>One method per criterion of the spec, tagged with the codes of the criterion (JUnit allows no
 * tag per invocation), so a case runs in the method of its criterion. The codes are written in the
 * {@code @Tag}s and in {@code @RuleCases}; {@code LedgerGoldenFixturesTest} checks that they are
 * the same.
 */
@Tag("golden")
@Disabled(
    "Issue #173: la cuenta corriente no existe (Fase 4); ver src/test/resources/golden/README.md")
@DisplayName("Pruebas golden de la cuenta corriente (en rojo hasta la Fase 4)")
class LedgerGoldenTest {

  private static final String MODULE = "ledger";

  /** The hyphen the documents use in the codes (U+2011); the tags use the ASCII one. */
  private static final char NON_BREAKING_HYPHEN = '‑';

  @ParameterizedTest(name = "{0}")
  @Tag("CA-4.01")
  @Tag("LQ-C04")
  @RuleCases({"CA-4.01", "LQ-C04"})
  @DisplayName(
      "La obligación nace con el neto, sin restar el descuento, y toma la modalidad de la emisión"
          + " (CA-4.01, LQ-C04)")
  void theObligationIsBornWithTheNet(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-4.02")
  @RuleCases({"CA-4.02"})
  @DisplayName("Un pago dentro del plazo por el valor con descuento salda la obligación (CA-4.02)")
  void aPaymentWithTheDiscountSettlesTheObligation(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-4.03")
  @RuleCases({"CA-4.03"})
  @DisplayName("Un pago parcial dentro del plazo no genera descuento (CA-4.03)")
  void aPartialPaymentGeneratesNoDiscount(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-4.04")
  @Tag("IM-01")
  @Tag("IM-03")
  @RuleCases({"CA-4.04", "IM-01", "IM-03"})
  @DisplayName(
      "Un pago en mora se reparte en proporción entre los componentes (CA-4.04, IM-01, IM-03)")
  void aPaymentInArrearsIsAllocatedProportionally(GoldenCase goldenCase) {
    LedgerGoldenResult result = assertCase(goldenCase);
    assertAllocationInvariants(LedgerGoldenInputs.from(goldenCase), result);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-4.08")
  @RuleCases({"CA-4.08"})
  @DisplayName("El reverso bancario reversa el pago y el descuento (CA-4.08)")
  void aBankReversalReversesThePaymentAndTheDiscount(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-4.09")
  @RuleCases({"CA-4.09"})
  @DisplayName("La distribución da al departamento su parte y al municipio el residuo (CA-4.09)")
  void theDistributionGivesTheDepartmentItsShare(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  /** The visible name of a case: {@code {codes} · {id} · {description}}, as in the documents. */
  static String displayName(GoldenCase goldenCase) {
    return codes(goldenCase) + " · " + goldenCase.id() + " · " + goldenCase.description();
  }

  /** The rule codes of a case, comma separated, with the non-breaking hyphen of the documents. */
  static String codes(GoldenCase goldenCase) {
    return String.join(", ", goldenCase.rules()).replace('-', NON_BREAKING_HYPHEN);
  }

  /** The cases that cite any of the codes of a criterion: the ones its method runs. */
  static List<GoldenCase> casesFor(List<String> codes) {
    return GoldenFixtures.cases(MODULE).stream()
        .filter(goldenCase -> goldenCase.rules().stream().anyMatch(codes::contains))
        .toList();
  }

  /**
   * Runs one case against the ledger of {@link LedgerGoldenEngines#current()} and compares it.
   *
   * @return what the ledger returned, for the checks that go beyond the expected values
   */
  static LedgerGoldenResult assertCase(GoldenCase goldenCase) {
    JsonNode configuration = GoldenConfiguration.load(goldenCase.configuration().file());
    LedgerGoldenResult result =
        LedgerGoldenEngines.current().apply(LedgerGoldenInputs.from(goldenCase), configuration);
    GoldenComparator.assertMatches(goldenCase, result);
    assertAll(reversesProblems(result).stream().map(problem -> () -> fail(problem)));
    return result;
  }

  /**
   * IM-01 and IM-03 on the result of an imputation: what was allocated adds up to the payment, and
   * no component is allocated more than its debt (it would be left negative), nor has a negative
   * balance.
   */
  static void assertAllocationInvariants(LedgerGoldenInput input, LedgerGoldenResult result) {
    assertAll(allocationProblems(input, result).stream().map(problem -> () -> fail(problem)));
  }

  /** The violations of IM-01 and IM-03; empty if the result respects them. */
  static List<String> allocationProblems(LedgerGoldenInput input, LedgerGoldenResult result) {
    List<String> problems = new ArrayList<>();
    BigDecimal paid = paymentOf(input);
    Map<String, BigDecimal> debt = new LinkedHashMap<>();
    input
        .openingMovements()
        .forEach(m -> debt.merge(m.component(), m.signedAmount(), BigDecimal::add));
    Map<String, BigDecimal> allocated = new LinkedHashMap<>();
    List<LedgerGoldenResult.Allocation> allocations =
        result.allocations() == null ? List.of() : result.allocations();
    allocations.forEach(a -> allocated.merge(a.component(), a.amount(), BigDecimal::add));
    BigDecimal total = allocated.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(paid) != 0) {
      problems.add("IM-01: lo imputado (" + total + ") no es el pago (" + paid + ")");
    }
    allocated.forEach((component, amount) -> {
      BigDecimal left = debt.getOrDefault(component, BigDecimal.ZERO).subtract(amount);
      if (left.signum() < 0) {
        problems.add("IM-03: el componente " + component + " quedaría en " + left);
      }
    });
    if (result.balances() != null) {
      result
          .balances()
          .forEach((component, balance) -> {
            BigDecimal left =
                debt.getOrDefault(component, BigDecimal.ZERO)
                    .subtract(allocated.getOrDefault(component, BigDecimal.ZERO));
            if (balance.signum() < 0) {
              problems.add("IM-03: el saldo de " + component + " es negativo (" + balance + ")");
            }
            if (balance.compareTo(left) != 0) {
              problems.add(
                  "IM-03: el saldo de "
                      + component
                      + " ("
                      + balance
                      + ") no es la deuda menos lo"
                      + " imputado ("
                      + left
                      + ")");
            }
          });
    }
    return problems;
  }

  /** The violations of {@code reverses}: only a {@code REVERSAL} reverses; empty if respected. */
  static List<String> reversesProblems(LedgerGoldenResult result) {
    List<String> problems = new ArrayList<>();
    if (result.movements() != null) {
      result
          .movements()
          .forEach(movement -> {
            if (movement.reverses() != null && !"REVERSAL".equals(movement.type())) {
              problems.add(
                  "el movimiento " + movement.type() + " dice que reversa " + movement.reverses());
            }
          });
    }
    return problems;
  }

  /** The payment of a case that starts from opening movements: exactly one, and no emission. */
  private static BigDecimal paymentOf(LedgerGoldenInput input) {
    List<BigDecimal> payments =
        input.events().stream()
            .filter(LedgerGoldenInput.Payment.class::isInstance)
            .map(event -> ((LedgerGoldenInput.Payment) event).amount())
            .toList();
    boolean emission =
        input.events().stream().anyMatch(LedgerGoldenInput.Emission.class::isInstance);
    if (payments.size() != 1 || emission) {
      throw new IllegalArgumentException(
          "IM-01 e IM-03 se comprueban en un caso con apertura y un solo pago, sin emisión");
    }
    return payments.get(0);
  }

  /**
   * The cases that cite any of the codes of a criterion, for its method. The values must be the
   * same as the {@code @Tag}s of the method; {@code LedgerGoldenFixturesTest} checks it.
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @ArgumentsSource(RuleCasesProvider.class)
  @interface RuleCases {
    String[] value();
  }

  /** Gives each method the cases of its criterion, named for the report. */
  static final class RuleCasesProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(
        ParameterDeclarations parameters, ExtensionContext context) {
      String[] codes = context.getRequiredTestMethod().getAnnotation(RuleCases.class).value();
      return casesFor(Arrays.asList(codes)).stream()
          .map(goldenCase -> Arguments.of(Named.of(displayName(goldenCase), goldenCase)));
    }
  }
}
