package co.com.sapenlinea.liv.tax.golden;

/**
 * The only place that chooses the assessment adapter. In Phase 2 only {@link #current()} changes;
 * the cases do not.
 */
final class AssessmentGoldenEngines {

  private AssessmentGoldenEngines() {}

  static AssessmentGoldenEngine current() {
    return new NotImplementedAssessmentEngine();
  }
}
