package co.com.sapenlinea.liv.tax.golden;

import co.com.sapenlinea.liv.golden.GoldenEngineNotImplementedException;
import tools.jackson.databind.JsonNode;

/** The adapter while the assessment engine does not exist: it always fails, for that reason. */
final class NotImplementedAssessmentEngine implements AssessmentGoldenEngine {

  /** The phase of the roadmap that builds the assessment engine. */
  private static final int ENGINE_PHASE = 2;

  @Override
  public AssessmentGoldenResult assess(AssessmentGoldenInput input, JsonNode configuration) {
    throw new GoldenEngineNotImplementedException("motor de liquidación", ENGINE_PHASE);
  }
}
