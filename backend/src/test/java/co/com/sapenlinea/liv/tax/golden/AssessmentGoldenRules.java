package co.com.sapenlinea.liv.tax.golden;

import co.com.sapenlinea.liv.golden.GoldenFixtures;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * What the schema of the assessment fixtures cannot express (see {@code
 * src/test/resources/golden/README.md}):
 *
 * <ul>
 *   <li>{@code base-form}: a case gives at most one form of the base;
 *   <li>{@code base-required}: a case that compares amounts gives one; only a case that compares
 *       the calendar (dates and whether the discount applies) may give none;
 *   <li>{@code base-source}: the origin matches the form (a table value comes from the official
 *       table, an invoice value from the invoice, and a base given already adjusted has none);
 *   <li>{@code base-adjusted}: a base given already adjusted lists no adjustments, so that none is
 *       applied twice;
 *   <li>{@code category-required}: a case that compares amounts gives the vehicle category.
 * </ul>
 */
final class AssessmentGoldenRules {

  /** The form of a base that comes already adjusted: no origin and no adjustments to apply. */
  private static final String ADJUSTED_BASE = "taxable_base";

  /** The origin of a base given already adjusted: it has none. */
  private static final String NO_ORIGIN = "";

  /** The four forms of the base, and the origin each one takes. */
  private static final Map<String, String> BASE_FORMS =
      Map.of(
          ADJUSTED_BASE,
          NO_ORIGIN,
          "table_value",
          "OFFICIAL_TABLE",
          "table_value_thousands",
          "OFFICIAL_TABLE",
          "invoice_value_without_vat",
          "INVOICE");

  /**
   * The keys of a case that only asks for the calendar: its dates and whether the discount applies.
   */
  private static final Set<String> CALENDAR_ONLY_KEYS =
      Set.of("discount_deadline", "due_date", "default_from", "discount_applies");

  static final GoldenFixtures.Rule INPUT = AssessmentGoldenRules::check;

  private AssessmentGoldenRules() {}

  private static List<Problem> check(JsonNode fixture) {
    List<Problem> problems = new ArrayList<>();
    JsonNode cases = fixture.path("cases");
    for (int index = 0; index < cases.size(); index++) {
      checkCase(cases.get(index), "/cases/" + index + "/input", problems);
    }
    return problems;
  }

  private static void checkCase(JsonNode testCase, String path, List<Problem> problems) {
    JsonNode expected = testCase.path("expected");
    if (!expected.isObject()) {
      // The schema already rejects it; there is nothing to compare here.
      return;
    }
    JsonNode input = testCase.path("input");
    List<String> forms = BASE_FORMS.keySet().stream().filter(input::has).sorted().toList();
    boolean calendarOnly =
        !expected.isEmpty()
            && expected.properties().stream()
                .allMatch(entry -> CALENDAR_ONLY_KEYS.contains(entry.getKey()));
    if (forms.size() > 1) {
      problems.add(new Problem(path, "base-form"));
    } else if (forms.isEmpty() && !calendarOnly) {
      problems.add(new Problem(path, "base-required"));
    } else if (forms.size() == 1) {
      checkSource(input, path, BASE_FORMS.get(forms.get(0)), problems);
      if (forms.get(0).equals(ADJUSTED_BASE) && !input.path("base_adjustments").isEmpty()) {
        problems.add(new Problem(path + "/base_adjustments", "base-adjusted"));
      }
    }
    if (forms.isEmpty() && input.has("base_source_type")) {
      problems.add(new Problem(path + "/base_source_type", "base-source"));
    }
    if (!calendarOnly && !input.has("vehicle_category")) {
      problems.add(new Problem(path + "/vehicle_category", "category-required"));
    }
  }

  private static void checkSource(
      JsonNode input, String path, String expectedSource, List<Problem> problems) {
    String source =
        input.has("base_source_type") ? input.get("base_source_type").stringValue() : NO_ORIGIN;
    if (!source.equals(expectedSource)) {
      problems.add(new Problem(path + "/base_source_type", "base-source"));
    }
  }
}
