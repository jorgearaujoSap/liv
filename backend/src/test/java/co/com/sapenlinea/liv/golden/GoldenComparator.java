package co.com.sapenlinea.liv.golden;

import java.util.List;

/**
 * Compares the result of an engine with the {@code expected} of a case. Only the keys of {@code
 * expected} are compared, and nothing is calculated here.
 */
public final class GoldenComparator {

  private GoldenComparator() {}

  /** Every difference, as {@code <JSON Pointer>: <what>}; empty if the result matches. */
  public static List<String> differences(GoldenCase goldenCase, GoldenResult result) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** Fails with all the differences together. */
  public static void assertMatches(GoldenCase goldenCase, GoldenResult result) {
    throw new UnsupportedOperationException("pendiente");
  }
}
