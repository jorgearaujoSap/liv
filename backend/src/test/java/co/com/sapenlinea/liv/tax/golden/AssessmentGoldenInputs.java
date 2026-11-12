package co.com.sapenlinea.liv.tax.golden;

import co.com.sapenlinea.liv.golden.GoldenCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Converts a golden case into the input of the assessment engine. */
final class AssessmentGoldenInputs {

  private AssessmentGoldenInputs() {}

  /**
   * The input of a case. Amounts are read with {@code decimalValue()}, never as {@code double}. It
   * fails if {@code fiscal_year} or {@code new_vehicle} is missing or a date is not ISO-8601; the
   * other inputs are optional and stay {@code null}.
   */
  static AssessmentGoldenInput from(GoldenCase goldenCase) {
    JsonNode input = goldenCase.input();
    return new AssessmentGoldenInput(
        required(input, "fiscal_year").intValue(),
        text(input, "vehicle_category"),
        text(input, "base_source_type"),
        amount(input, "taxable_base"),
        amount(input, "table_value"),
        amount(input, "table_value_thousands"),
        amount(input, "invoice_value_without_vat"),
        adjustments(input.path("base_adjustments")),
        required(input, "new_vehicle").booleanValue(),
        date(input, "accrual_date"));
  }

  private static JsonNode required(JsonNode input, String key) {
    if (!input.has(key)) {
      throw new IllegalArgumentException("falta la entrada " + key);
    }
    return input.get(key);
  }

  private static String text(JsonNode input, String key) {
    return input.has(key) ? input.get(key).stringValue() : null;
  }

  private static BigDecimal amount(JsonNode input, String key) {
    return input.has(key) ? input.get(key).decimalValue() : null;
  }

  private static LocalDate date(JsonNode input, String key) {
    return input.has(key) ? LocalDate.parse(input.get(key).stringValue()) : null;
  }

  private static List<String> adjustments(JsonNode list) {
    return list.valueStream().map(JsonNode::stringValue).toList();
  }
}
