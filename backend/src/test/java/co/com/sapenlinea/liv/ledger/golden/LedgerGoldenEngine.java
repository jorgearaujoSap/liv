package co.com.sapenlinea.liv.ledger.golden;

import tools.jackson.databind.JsonNode;

/**
 * The test adapter of the ledger (Phase 4). It is given what the functional document takes for
 * granted and tests only what the example calculates. Whatever the example does not calculate is an
 * input or does not apply, and the adapter must not evaluate it: a correct ledger would otherwise
 * reject cases that the document computes with assumptions.
 *
 * <p><b>It exercises</b> (Doc. 04):
 *
 * <ul>
 *   <li>the birth of the obligation (section 3.1);
 *   <li>the imputation of a payment already reconciled (section 5.1);
 *   <li>the bank reversal of a payment (section 6.2);
 *   <li>the distribution of an imputation (section 7).
 * </ul>
 *
 * <p><b>It does not evaluate</b>: the reconciliation of the payment (section 4.4), the accrual of
 * interest (Module 5: the opening debt of a case is the debt on the date of the payment, and the
 * ledger accrues nothing more) and the competence of the administration.
 *
 * <p><b>The modality is always an input.</b> It is never derived. In an {@code EMISSION} event it
 * travels with the event, because the liquidation decided it, and the obligation takes it from
 * there (section 3.1). In a case that starts from an imputation it travels in the {@code
 * obligation}. A case that starts from opening movements does not use it.
 *
 * <p><b>The expected values come from the ledger.</b> The beneficiaries and whether a share is
 * retained or sent are read from what the ledger returns, never rebuilt from the input (for example
 * from the name of the declared address): the case would then prove nothing. The opening debt of
 * cases C4 and C5 is a datum of the case; the adapter builds it with the real use cases (or a stub
 * of the interest port of Module 5 in test code), never with a free-write port in production code.
 *
 * <p>The real adapter of Phase 4 only translates the input to the application port of {@code
 * ledger} and reads the configuration with the production reader and model. It must not impute,
 * round or distribute by itself: the cases would then test the adapter and not the ledger.
 *
 * <p>The configuration is only a source of parameters: calendar, rounding, {@code recaudo} and the
 * distribution block of the modality B, which is read even if B is not active. No tax value lives
 * in the adapter. The cases and the way to run them are described in {@code
 * src/test/resources/golden/README.md}.
 */
interface LedgerGoldenEngine {

  /**
   * Runs the events of a golden case on the ledger.
   *
   * @param input what the document gives for the case
   * @param configuration the jurisdiction configuration the case cites (the copy of Ipiales)
   * @return what the ledger holds afterwards; a value it does not calculate is {@code null}
   */
  LedgerGoldenResult apply(LedgerGoldenInput input, JsonNode configuration);
}
