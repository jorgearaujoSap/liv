package co.com.sapenlinea.liv.ledger.golden;

import co.com.sapenlinea.liv.golden.GoldenCase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Converts a golden case into the input of the ledger. */
final class LedgerGoldenInputs {

  private LedgerGoldenInputs() {}

  /**
   * The input of a case. Amounts are read with {@code decimalValue()}, never as {@code double}. It
   * fails if a required value is missing (only {@code obligation} and {@code opening_movements} are
   * optional: they stay {@code null} and empty), if a value has another type (it is never read as 0
   * or false), if a date is not ISO-8601 or if an event has an unknown type.
   */
  static LedgerGoldenInput from(GoldenCase goldenCase) {
    JsonNode input = goldenCase.input();
    return new LedgerGoldenInput(
        obligation(input.get("obligation")),
        movements(input.get("opening_movements"), "opening_movements"),
        events(input.get("events")));
  }

  private static LedgerGoldenInput.Obligation obligation(JsonNode node) {
    if (node == null) {
      return null;
    }
    JsonNode address = object(node, "declared_address");
    boolean sameAsJurisdiction = flag(address, "same_as_jurisdiction");
    return new LedgerGoldenInput.Obligation(
        text(node, "origin_modality"),
        new LedgerGoldenInput.DeclaredAddress(
            sameAsJurisdiction, sameAsJurisdiction ? null : text(address, "name")));
  }

  private static List<LedgerGoldenInput.Movement> movements(JsonNode list, String name) {
    List<LedgerGoldenInput.Movement> movements = new ArrayList<>();
    if (list == null) {
      return movements;
    }
    for (JsonNode movement : array(list, name)) {
      movements.add(
          new LedgerGoldenInput.Movement(
              text(movement, "type"),
              text(movement, "component"),
              amount(movement, "signed_amount")));
    }
    return movements;
  }

  private static List<LedgerGoldenInput.Event> events(JsonNode list) {
    if (list == null) {
      throw new IllegalArgumentException("la entrada events falta");
    }
    List<LedgerGoldenInput.Event> events = new ArrayList<>();
    for (JsonNode event : array(list, "events")) {
      events.add(event(event));
    }
    return events;
  }

  private static LedgerGoldenInput.Event event(JsonNode event) {
    String type = text(event, "type");
    return switch (type) {
      case "EMISSION" ->
          new LedgerGoldenInput.Emission(
              amount(event, "net_tax"),
              amount(event, "net_tax_with_discount"),
              text(event, "origin_modality"));
      case "PAYMENT" ->
          new LedgerGoldenInput.Payment(
              date(event, "date"), amount(event, "amount"), text(event, "status"));
      case "BANK_REVERSAL" -> new LedgerGoldenInput.BankReversal();
      case "ALLOCATION" -> {
        JsonNode payment = object(event, "payment");
        yield new LedgerGoldenInput.Allocation(
            text(payment, "status"),
            amount(payment, "amount"),
            movements(requiredValue(event, "movements"), "movements"));
      }
      default -> throw new IllegalArgumentException("el tipo de evento " + type + " no existe");
    };
  }

  private static JsonNode requiredValue(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null) {
      throw new IllegalArgumentException("la entrada " + key + " falta");
    }
    return value;
  }

  private static JsonNode object(JsonNode node, String key) {
    JsonNode value = requiredValue(node, key);
    if (!value.isObject()) {
      throw new IllegalArgumentException("la entrada " + key + " no es un objeto");
    }
    return value;
  }

  private static JsonNode array(JsonNode value, String name) {
    if (!value.isArray()) {
      throw new IllegalArgumentException("la entrada " + name + " no es una lista");
    }
    return value;
  }

  private static String text(JsonNode node, String key) {
    JsonNode value = requiredValue(node, key);
    if (!value.isString()) {
      throw new IllegalArgumentException("la entrada " + key + " no es un texto");
    }
    return value.stringValue();
  }

  private static boolean flag(JsonNode node, String key) {
    JsonNode value = requiredValue(node, key);
    if (!value.isBoolean()) {
      throw new IllegalArgumentException("la entrada " + key + " no es un valor lógico");
    }
    return value.booleanValue();
  }

  /** A number is read as an exact decimal; any other kind of value fails instead of reading 0. */
  private static BigDecimal amount(JsonNode node, String key) {
    JsonNode value = requiredValue(node, key);
    if (!value.isNumber()) {
      throw new IllegalArgumentException("la entrada " + key + " no es un número");
    }
    return value.decimalValue();
  }

  private static LocalDate date(JsonNode node, String key) {
    try {
      return LocalDate.parse(text(node, key));
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("la entrada " + key + " no es una fecha ISO-8601", e);
    }
  }
}
