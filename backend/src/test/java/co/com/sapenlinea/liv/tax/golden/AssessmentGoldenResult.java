package co.com.sapenlinea.liv.tax.golden;

import co.com.sapenlinea.liv.golden.GoldenResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the assessment engine returns for a golden case. Every key is emitted, with {@code null}
 * when the engine does not give it, so that the comparator tells "not calculated" from a value.
 * Amounts are decimal nodes and dates are ISO-8601 text.
 *
 * @param taxableBase the base after adjustments, in pesos
 * @param appliedRate the rate of the bracket, as a fraction
 * @param taxableMonths the months charged
 * @param grossTax the gross tax, compared to the cent
 * @param netTax the net tax, already rounded
 * @param effectiveDiscount the net tax minus the rounded amount to pay with the discount
 * @param netTaxWithDiscount the rounded amount to pay with the discount
 * @param discountApplies false when the vehicle has no early-payment discount
 * @param discountDeadline the last day to pay with the discount
 * @param dueDate the last day to pay without interest
 * @param defaultFrom the first day of default
 */
record AssessmentGoldenResult(
    BigDecimal taxableBase,
    BigDecimal appliedRate,
    Integer taxableMonths,
    BigDecimal grossTax,
    BigDecimal netTax,
    BigDecimal effectiveDiscount,
    BigDecimal netTaxWithDiscount,
    Boolean discountApplies,
    LocalDate discountDeadline,
    LocalDate dueDate,
    LocalDate defaultFrom)
    implements GoldenResult {

  @Override
  public JsonNode asTree() {
    ObjectNode tree = JsonNodeFactory.instance.objectNode();
    putAmount(tree, "taxable_base", taxableBase);
    putAmount(tree, "applied_rate", appliedRate);
    if (taxableMonths == null) {
      tree.putNull("taxable_months");
    } else {
      tree.put("taxable_months", taxableMonths.intValue());
    }
    putAmount(tree, "gross_tax", grossTax);
    putAmount(tree, "net_tax", netTax);
    putAmount(tree, "effective_discount", effectiveDiscount);
    putAmount(tree, "net_tax_with_discount", netTaxWithDiscount);
    if (discountApplies == null) {
      tree.putNull("discount_applies");
    } else {
      tree.put("discount_applies", discountApplies.booleanValue());
    }
    putDate(tree, "discount_deadline", discountDeadline);
    putDate(tree, "due_date", dueDate);
    putDate(tree, "default_from", defaultFrom);
    return tree;
  }

  private static void putAmount(ObjectNode tree, String key, BigDecimal amount) {
    if (amount == null) {
      tree.putNull(key);
    } else {
      tree.set(key, DecimalNode.valueOf(amount));
    }
  }

  private static void putDate(ObjectNode tree, String key, LocalDate date) {
    if (date == null) {
      tree.putNull(key);
    } else {
      tree.put(key, date.toString());
    }
  }
}
