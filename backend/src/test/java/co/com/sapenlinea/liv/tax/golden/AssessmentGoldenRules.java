package co.com.sapenlinea.liv.tax.golden;

import co.com.sapenlinea.liv.golden.GoldenFixtures;

/** What the schema of the assessment fixtures cannot express. */
final class AssessmentGoldenRules {

  /**
   * Exactly one form of the base, its origin and its category (see
   * src/test/resources/golden/README.md).
   */
  static final GoldenFixtures.Rule INPUT =
      fixture -> {
        throw new UnsupportedOperationException("pendiente: la regla llega con los fixtures");
      };

  private AssessmentGoldenRules() {}
}
