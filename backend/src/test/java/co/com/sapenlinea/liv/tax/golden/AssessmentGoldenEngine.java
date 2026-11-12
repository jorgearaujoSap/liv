package co.com.sapenlinea.liv.tax.golden;

import tools.jackson.databind.JsonNode;

/**
 * The test adapter of the assessment engine (Phase 2). It is given what the functional document
 * takes for granted and tests only the calculation of the example. Whatever the example does not
 * calculate is an input or does not apply, and the adapter must not evaluate it: a correct engine
 * would otherwise reject cases that the document computes with assumptions.
 *
 * <p><b>It exercises</b> (Doc. 02 sections B.6 to B.8 and Doc. 03 sections 9.6 and 9.7):
 *
 * <ul>
 *   <li>the base adjustments and the table multiplier (B.6);
 *   <li>the rate of the bracket and the taxable months (B.7);
 *   <li>the gross tax, the rounded net tax and the early-payment discount (B.8);
 *   <li>the dates of the calendar of the configuration.
 * </ul>
 *
 * <p><b>It does not evaluate</b>: the prerequisites and the subjection of the vehicle (B.3, for
 * example the engine size of a motorcycle), the competence and the universe of the modality (Doc.
 * 03 section 9.5, OB-02 and OB-03), the homologation and the derivation of the category. The
 * category, the origin of the base and the adjustments are inputs.
 *
 * <p>The configuration is only a source of parameters: rates, ranges, adjustments, rounding and
 * calendar. No tax value lives in the adapter. The cases and the way to run them are described in
 * {@code src/test/resources/golden/README.md}.
 */
interface AssessmentGoldenEngine {

  /**
   * Assesses the vehicle of a golden case.
   *
   * @param input what the document gives for the case
   * @param configuration the jurisdiction configuration the case cites (the copy of Ipiales)
   * @return the values of the assessment; a value the engine does not calculate is {@code null}
   */
  AssessmentGoldenResult assess(AssessmentGoldenInput input, JsonNode configuration);
}
