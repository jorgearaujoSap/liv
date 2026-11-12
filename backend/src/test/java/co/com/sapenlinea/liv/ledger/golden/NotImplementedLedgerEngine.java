package co.com.sapenlinea.liv.ledger.golden;

import co.com.sapenlinea.liv.golden.GoldenEngineNotImplementedException;
import tools.jackson.databind.JsonNode;

/** The adapter while the ledger does not exist: it always fails, for that reason. */
final class NotImplementedLedgerEngine implements LedgerGoldenEngine {

  /** The phase of the roadmap that builds the ledger. */
  private static final int LEDGER_PHASE = 4;

  @Override
  public LedgerGoldenResult apply(LedgerGoldenInput input, JsonNode configuration) {
    throw new GoldenEngineNotImplementedException("cuenta corriente", LEDGER_PHASE);
  }
}
