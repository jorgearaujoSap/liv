package co.com.sapenlinea.liv.tax.golden;

import co.com.sapenlinea.liv.golden.GoldenCase;
import co.com.sapenlinea.liv.golden.GoldenComparator;
import co.com.sapenlinea.liv.golden.GoldenConfiguration;
import co.com.sapenlinea.liv.golden.GoldenFixtures;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
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
 * The golden cases of the assessment, L1 to L15: the examples of the Docs. 02 and 03 against the
 * engine. They fail while the engine does not exist (Phase 2), so they carry {@code @Disabled} with
 * their issue; how to run them red is in {@code src/test/resources/golden/README.md}.
 *
 * <p>One method per criterion of the spec, tagged with its code (JUnit allows no tag per
 * invocation), so a case with several codes runs in several methods. {@code 006-02} and {@code
 * 006-14} are criteria of the spec of the feature, not functional rules.
 */
@Tag("golden")
@Disabled("Issue #171: el motor de liquidación no existe (Fase 2); ver golden/README.md")
@DisplayName("Pruebas golden de la liquidación (en rojo hasta la Fase 2)")
class AssessmentGoldenTest {

  private static final String MODULE = "tax";

  /** The hyphen the documents use in the codes (U+2011); the tags use the ASCII one. */
  private static final char NON_BREAKING_HYPHEN = '‑';

  @ParameterizedTest(name = "{0}")
  @Tag("CA-L01")
  @RuleCases("CA-L01")
  @DisplayName("La base en el borde de un tramo paga la tarifa de ese tramo (CA-L01)")
  void boundaryBaseKeepsItsBracket(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-L02")
  @RuleCases("CA-L02")
  @DisplayName("Un peso sobre el borde de un tramo paga la tarifa del tramo siguiente (CA-L02)")
  void oneAboveTheBoundaryPaysTheNextBracket(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-L03")
  @RuleCases("CA-L03")
  @DisplayName("Un vehículo nuevo paga los meses que quedan de la vigencia (CA-L03)")
  void newVehiclePaysTheRemainingMonths(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("CA-L04")
  @RuleCases("CA-L04")
  @DisplayName("El ajuste de la base puede cambiar el tramo y la tarifa (CA-L04)")
  void adjustmentCanChangeTheBracket(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("006-02")
  @RuleCases("006-02")
  @DisplayName("Los automóviles usados de la tabla dan su tarifa y su bruto (006-02)")
  void usedCarsGiveTheirRateAndGross(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("006-14")
  @RuleCases("006-14")
  @DisplayName(
      "Las fechas de un vehículo nuevo son las del calendario de la configuración (006-14)")
  void newVehicleDatesComeFromTheCalendar(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("LQ-C01")
  @RuleCases("LQ-C01")
  @DisplayName("El redondeo se aplica solo al final del cálculo (LQ-C01)")
  void roundingHappensOnlyAtTheEnd(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("LQ-C02")
  @RuleCases("LQ-C02")
  @DisplayName("El neto y el valor con descuento se redondean como dice la configuración (LQ-C02)")
  void roundingFollowsTheConfiguration(GoldenCase goldenCase) {
    assertCase(goldenCase);
  }

  @ParameterizedTest(name = "{0}")
  @Tag("LQ-C04")
  @RuleCases("LQ-C04")
  @DisplayName("El descuento por pronto pago no reduce el neto (LQ-C04)")
  void discountDoesNotReduceTheNet(GoldenCase goldenCase) {
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

  /** The cases that cite a rule code: the ones the method of that code runs. */
  static List<GoldenCase> casesFor(String code) {
    return GoldenFixtures.cases(MODULE).stream()
        .filter(goldenCase -> goldenCase.rules().contains(code))
        .toList();
  }

  /** Runs one case against the engine of {@link AssessmentGoldenEngines#current()}. */
  static void assertCase(GoldenCase goldenCase) {
    JsonNode configuration = GoldenConfiguration.load(goldenCase.configuration().file());
    AssessmentGoldenResult result =
        AssessmentGoldenEngines.current()
            .assess(AssessmentGoldenInputs.from(goldenCase), configuration);
    GoldenComparator.assertMatches(goldenCase, result);
  }

  /**
   * The cases that cite a rule code, for the method of that code. The value must be the same as the
   * {@code @Tag} of the method; {@code AssessmentGoldenFixturesTest} checks it.
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @ArgumentsSource(RuleCasesProvider.class)
  @interface RuleCases {
    String value();
  }

  /** Gives each method the cases of its code, named for the report. */
  static final class RuleCasesProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(
        ParameterDeclarations parameters, ExtensionContext context) {
      String code = context.getRequiredTestMethod().getAnnotation(RuleCases.class).value();
      return casesFor(code).stream()
          .map(goldenCase -> Arguments.of(Named.of(displayName(goldenCase), goldenCase)));
    }
  }
}
