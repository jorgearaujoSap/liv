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
   * fails if {@code fiscal_year} or {@code new_vehicle} is missing, if an input has another type
   * (it is never read as 0 or false) or if a date is not ISO-8601; the other inputs are optional
   * and stay {@code null}.
   */
  static AssessmentGoldenInput from(GoldenCase goldenCase) {
    JsonNode input = goldenCase.input();
    return new AssessmentGoldenInput(
        requiredInteger(input, "fiscal_year"),
        text(input, "vehicle_category"),
        text(input, "base_source_type"),
        amount(input, "taxable_base"),
        amount(input, "table_value"),
        amount(input, "table_value_thousands"),
        amount(input, "invoice_value_without_vat"),
        adjustments(input.path("base_adjustments")),
        requiredBoolean(input, "new_vehicle"),
        date(input, "accrual_date"));
  }

  private static int requiredInteger(JsonNode input, String key) {
    JsonNode value = input.get(key);
    if (value == null || !value.isIntegralNumber()) {
      throw new IllegalArgumentException("la entrada " + key + " falta o no es un entero");
    }
    return value.intValue();
  }

  private static boolean requiredBoolean(JsonNode input, String key) {
    JsonNode value = input.get(key);
    if (value == null || !value.isBoolean()) {
      throw new IllegalArgumentException("la entrada " + key + " falta o no es un valor lógico");
    }
    return value.booleanValue();
  }

  private static String text(JsonNode input, String key) {
    return input.has(key) ? string(input, key) : null;
  }

  /** A number is read as an exact decimal; any other kind of value fails instead of reading 0. */
  private static BigDecimal amount(JsonNode input, String key) {
    if (!input.has(key)) {
      return null;
    }
    if (!input.get(key).isNumber()) {
      throw new IllegalArgumentException("la entrada " + key + " no es un número");
    }
    return input.get(key).decimalValue();
  }

  private static LocalDate date(JsonNode input, String key) {
    return input.has(key) ? LocalDate.parse(string(input, key)) : null;
  }

  private static String string(JsonNode input, String key) {
    if (!input.get(key).isString()) {
      throw new IllegalArgumentException("la entrada " + key + " no es un texto");
    }
    return input.get(key).stringValue();
  }

  private static List<String> adjustments(JsonNode list) {
    return list.valueStream()
        .map(adjustment -> {
          if (!adjustment.isString()) {
            throw new IllegalArgumentException("un ajuste de la base no es un texto");
          }
          return adjustment.stringValue();
        })
        .toList();
  }
}
