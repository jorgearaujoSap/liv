package co.com.sapenlinea.liv.golden;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Compares the result of an engine with the {@code expected} of a case. Only the keys of {@code
 * expected} are compared, and nothing is calculated here:
 *
 * <ul>
 *   <li>numbers by value ({@code compareTo}), rounded to the scale of the case where its {@code
 *       comparison} says so; a floating-point number is never accepted;
 *   <li>text, codes and dates (ISO-8601 text) by equality, flags by equality;
 *   <li>a list as an exact multiset: neither one element more nor one less, in any order;
 *   <li>an object by the keys of the expected one: a key the result adds is not compared, and a key
 *       of the test itself ({@code derived_from}) is not part of the result.
 * </ul>
 *
 * The scale of a {@code comparison} applies to every field with that name, at any depth.
 */
public final class GoldenComparator {

  /** Keys of the test itself, in an expected object: the engine does not return them. */
  private static final Set<String> METADATA = Set.of("derived_from");

  private GoldenComparator() {}

  /** Every difference, as {@code <JSON Pointer>: <what>}; empty if the result matches. */
  public static List<String> differences(GoldenCase goldenCase, GoldenResult result) {
    JsonNode actual = result == null ? null : result.asTree();
    if (actual == null || actual.isNull() || actual.isMissingNode()) {
      return List.of("/: el motor no devolvió resultado");
    }
    List<String> differences = new ArrayList<>();
    compare(goldenCase.expected(), actual, "", "", goldenCase.comparison(), differences);
    return differences;
  }

  /** Fails with all the differences together. */
  public static void assertMatches(GoldenCase goldenCase, GoldenResult result) {
    assertAll(
        goldenCase.id() + " · " + goldenCase.description(),
        differences(goldenCase, result).stream().map(difference -> () -> fail(difference)));
  }

  private static void compare(
      JsonNode expected,
      JsonNode actual,
      String path,
      String field,
      Map<String, GoldenCase.Comparison> comparison,
      List<String> differences) {
    if (expected.isObject()) {
      if (!actual.isObject()) {
        differences.add(difference(path, "se esperaba un objeto y se obtuvo " + actual));
        return;
      }
      int compared = 0;
      for (Map.Entry<String, JsonNode> entry : expected.properties()) {
        String key = entry.getKey();
        if (METADATA.contains(key)) {
          continue;
        }
        compared++;
        String child = path + "/" + GoldenFixtures.escape(key);
        JsonNode value = actual.get(key);
        if (value == null) {
          differences.add(difference(child, "falta la clave; se esperaba " + entry.getValue()));
        } else {
          compare(entry.getValue(), value, child, key, comparison, differences);
        }
      }
      if (compared == 0) {
        // An object that expects nothing would accept any result.
        differences.add(difference(path, "el objeto esperado no tiene ninguna clave que comparar"));
      }
    } else if (expected.isArray()) {
      compareLists(expected, actual, path, field, comparison, differences);
    } else {
      compareScalar(expected, actual, path, comparison.get(field), differences);
    }
  }

  private static void compareScalar(
      JsonNode expected,
      JsonNode actual,
      String path,
      GoldenCase.Comparison comparison,
      List<String> differences) {
    boolean same;
    if (expected.isNull()) {
      same = actual.isNull();
    } else if (expected.isNumber()) {
      // A double or a float has lost the exact decimal value; only exact numbers are compared.
      same =
          isExactNumber(actual)
              && sameNumber(expected.decimalValue(), actual.decimalValue(), comparison);
    } else if (expected.isBoolean()) {
      same = actual.isBoolean() && expected.booleanValue() == actual.booleanValue();
    } else if (expected.isString()) {
      same = actual.isString() && expected.stringValue().equals(actual.stringValue());
    } else {
      same = false;
    }
    if (!same) {
      differences.add(difference(path, "se esperaba " + expected + " y se obtuvo " + actual));
    }
  }

  private static boolean isExactNumber(JsonNode node) {
    return node.isIntegralNumber() || node.isBigDecimal();
  }

  /** Equal by value; with a comparison, both are first rounded to its scale. */
  private static boolean sameNumber(
      BigDecimal expected, BigDecimal actual, GoldenCase.Comparison comparison) {
    return scaled(expected, comparison).compareTo(scaled(actual, comparison)) == 0;
  }

  /** The value rounded as the comparison says; unchanged if the field has no comparison. */
  private static BigDecimal scaled(BigDecimal value, GoldenCase.Comparison comparison) {
    return comparison == null ? value : value.setScale(comparison.scale(), comparison.rounding());
  }

  /** An exact multiset: every expected element has its own matching element, and no one is left. */
  private static void compareLists(
      JsonNode expected,
      JsonNode actual,
      String path,
      String field,
      Map<String, GoldenCase.Comparison> comparison,
      List<String> differences) {
    if (!actual.isArray()) {
      differences.add(difference(path, "se esperaba una lista y se obtuvo " + actual));
      return;
    }
    boolean[][] fits = new boolean[expected.size()][actual.size()];
    for (int i = 0; i < expected.size(); i++) {
      for (int j = 0; j < actual.size(); j++) {
        List<String> scratch = new ArrayList<>();
        compare(expected.get(i), actual.get(j), path, field, comparison, scratch);
        fits[i][j] = scratch.isEmpty();
      }
    }
    int[] ownerOfActual = new int[actual.size()];
    Arrays.fill(ownerOfActual, -1);
    List<String> unmatched = new ArrayList<>();
    for (int i = 0; i < expected.size(); i++) {
      if (!claim(i, fits, ownerOfActual, new boolean[actual.size()])) {
        unmatched.add(expected.get(i).toString());
      }
    }
    if (expected.size() != actual.size() || !unmatched.isEmpty()) {
      differences.add(
          difference(
              path,
              "se esperaban "
                  + expected.size()
                  + " elementos y se obtuvieron "
                  + actual.size()
                  + (unmatched.isEmpty() ? "" : "; sin pareja: " + String.join(", ", unmatched))));
    }
  }

  /**
   * One step of the search for a maximum bipartite matching (Kuhn): finds an actual element for
   * expected element {@code i}, giving another one to the expected element that held it if needed.
   */
  private static boolean claim(int i, boolean[][] fits, int[] ownerOfActual, boolean[] visited) {
    for (int j = 0; j < ownerOfActual.length; j++) {
      if (fits[i][j] && !visited[j]) {
        visited[j] = true;
        if (ownerOfActual[j] < 0 || claim(ownerOfActual[j], fits, ownerOfActual, visited)) {
          ownerOfActual[j] = i;
          return true;
        }
      }
    }
    return false;
  }

  private static String difference(String path, String what) {
    return (path.isEmpty() ? "/" : path) + ": " + what;
  }
}
