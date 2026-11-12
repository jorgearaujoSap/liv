package co.com.sapenlinea.liv.ledger.golden;

/**
 * The only place that chooses the ledger adapter. In Phase 4 only {@link #current()} changes; the
 * cases do not.
 */
final class LedgerGoldenEngines {

  private LedgerGoldenEngines() {}

  static LedgerGoldenEngine current() {
    return new NotImplementedLedgerEngine();
  }
}
