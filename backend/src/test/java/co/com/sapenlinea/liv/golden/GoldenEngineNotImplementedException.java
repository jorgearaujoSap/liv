package co.com.sapenlinea.liv.golden;

/**
 * Thrown by the test adapter of an engine that does not exist yet. It is the only reason a golden
 * test may fail while the engine is missing.
 */
public final class GoldenEngineNotImplementedException extends UnsupportedOperationException {

  /**
   * @param engine the engine, in Spanish, as the message shows it (for example "cuenta corriente")
   * @param phase the phase of the roadmap that builds it
   */
  public GoldenEngineNotImplementedException(String engine, int phase) {
    super(engine + " no implementado (Fase " + phase + "); ver golden/README.md");
  }
}
