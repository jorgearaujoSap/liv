package co.com.sapenlinea.liv.ledger.golden;

import co.com.sapenlinea.liv.golden.GoldenResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the ledger returns for a golden case. Every key is emitted, with {@code null} when the
 * ledger does not give it, so that the comparator tells "not calculated" from a value. Amounts are
 * decimal nodes and dates are ISO-8601 text. Lists are compared as exact multisets.
 *
 * @param obligation the obligation born from an emission
 * @param movements the movements of the ledger, in any order
 * @param allocations what the payment was imputed to each component
 * @param balances the balance of each component ({@code TAX}, {@code INTEREST}, {@code PENALTY})
 * @param totalBalance the sum of the balances
 * @param status the status of the obligation that does not depend on today ({@code PAGADA})
 * @param paymentStatus the status of the payment ({@code REVERSADO})
 * @param distribution the distribution of the imputation, by beneficiary
 */
record LedgerGoldenResult(
    Obligation obligation,
    List<Movement> movements,
    List<Allocation> allocations,
    Map<String, BigDecimal> balances,
    BigDecimal totalBalance,
    String status,
    String paymentStatus,
    List<DistributionLine> distribution)
    implements GoldenResult {

  /** The obligation: its original principal, its due date and the modality it took. */
  record Obligation(BigDecimal originalPrincipal, LocalDate dueDate, String originModality) {}

  /**
   * A movement of the ledger.
   *
   * @param reverses for a {@code REVERSAL}, the type of the movement it reverses (null otherwise)
   */
  record Movement(String type, String component, BigDecimal signedAmount, String reverses) {}

  /** The part of the payment imputed to a component. */
  record Allocation(String component, BigDecimal amount) {}

  /**
   * A line of the distribution.
   *
   * @param beneficiary who receives it
   * @param amount the amount, in pesos
   * @param disposition {@code RETENIDO} or {@code GIRADO} when the document says it (null if not)
   */
  record DistributionLine(String beneficiary, BigDecimal amount, String disposition) {}

  @Override
  public JsonNode asTree() {
    ObjectNode tree = JsonNodeFactory.instance.objectNode();
    putObligation(tree);
    putList(tree, "movements", movements, LedgerGoldenResult::movementTree);
    putList(tree, "allocations", allocations, LedgerGoldenResult::allocationTree);
    putBalances(tree);
    putAmount(tree, "total_balance", totalBalance);
    putText(tree, "status", status);
    putText(tree, "payment_status", paymentStatus);
    putList(tree, "distribution", distribution, LedgerGoldenResult::distributionTree);
    return tree;
  }

  private void putObligation(ObjectNode tree) {
    if (obligation == null) {
      tree.putNull("obligation");
      return;
    }
    String dueDate = obligation.dueDate() == null ? null : obligation.dueDate().toString();
    ObjectNode node = tree.putObject("obligation");
    putAmount(node, "original_principal", obligation.originalPrincipal());
    putText(node, "due_date", dueDate);
    putText(node, "origin_modality", obligation.originModality());
  }

  private void putBalances(ObjectNode tree) {
    if (balances == null) {
      tree.putNull("balances");
      return;
    }
    ObjectNode node = tree.putObject("balances");
    balances.forEach((component, balance) -> putAmount(node, component, balance));
  }

  private static ObjectNode movementTree(Movement movement) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    putText(node, "type", movement.type());
    putText(node, "component", movement.component());
    putAmount(node, "signed_amount", movement.signedAmount());
    putText(node, "reverses", movement.reverses());
    return node;
  }

  private static ObjectNode allocationTree(Allocation allocation) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    putText(node, "component", allocation.component());
    putAmount(node, "amount", allocation.amount());
    return node;
  }

  private static ObjectNode distributionTree(DistributionLine line) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    putText(node, "beneficiary", line.beneficiary());
    putAmount(node, "amount", line.amount());
    putText(node, "disposition", line.disposition());
    return node;
  }

  private static <T> void putList(
      ObjectNode tree, String key, List<T> items, Function<T, ObjectNode> toTree) {
    if (items == null) {
      tree.putNull(key);
      return;
    }
    ArrayNode list = tree.putArray(key);
    items.forEach(item -> list.add(toTree.apply(item)));
  }

  private static void putAmount(ObjectNode node, String key, BigDecimal amount) {
    if (amount == null) {
      node.putNull(key);
    } else {
      node.set(key, DecimalNode.valueOf(amount));
    }
  }

  private static void putText(ObjectNode node, String key, String text) {
    if (text == null) {
      node.putNull(key);
    } else {
      node.put(key, text);
    }
  }
}
