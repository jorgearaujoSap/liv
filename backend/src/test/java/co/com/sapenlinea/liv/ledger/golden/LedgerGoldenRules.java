package co.com.sapenlinea.liv.ledger.golden;

import co.com.sapenlinea.liv.golden.GoldenFixtures;
import co.com.sapenlinea.liv.golden.GoldenFixtures.Problem;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * What the schema of the ledger fixtures cannot express (see {@code
 * src/test/resources/golden/README.md}):
 *
 * <ul>
 *   <li>{@code modality-twice}: the modality of the obligation is given once, in the {@code
 *       EMISSION} event or in the {@code obligation}, never in both;
 *   <li>{@code start-form}: a case starts from exactly one of an {@code EMISSION} event, {@code
 *       opening_movements} or an {@code ALLOCATION} event;
 *   <li>{@code allocation-obligation}: an {@code ALLOCATION} needs the {@code obligation}, because
 *       the distribution depends on its modality and its declared address;
 *   <li>{@code reversal-payment}: a {@code BANK_REVERSAL} comes after a {@code PAYMENT}.
 * </ul>
 */
final class LedgerGoldenRules {

  private static final String EMISSION = "EMISSION";
  private static final String PAYMENT = "PAYMENT";
  private static final String BANK_REVERSAL = "BANK_REVERSAL";
  private static final String ALLOCATION = "ALLOCATION";

  static final GoldenFixtures.Rule INPUT = LedgerGoldenRules::check;

  private LedgerGoldenRules() {}

  private static List<Problem> check(JsonNode fixture) {
    List<Problem> problems = new ArrayList<>();
    JsonNode cases = fixture.path("cases");
    for (int index = 0; index < cases.size(); index++) {
      checkCase(cases.get(index).path("input"), "/cases/" + index + "/input", problems);
    }
    return problems;
  }

  private static void checkCase(JsonNode input, String path, List<Problem> problems) {
    JsonNode events = input.path("events");
    boolean emission = hasEvent(events, EMISSION);
    boolean allocation = hasEvent(events, ALLOCATION);
    boolean opening = !input.path("opening_movements").isEmpty();
    boolean obligation = input.has("obligation");
    if (emission && obligation) {
      problems.add(new Problem(path, "modality-twice"));
    }
    int forms = (emission ? 1 : 0) + (opening ? 1 : 0) + (allocation ? 1 : 0);
    if (forms != 1) {
      problems.add(new Problem(path, "start-form"));
    }
    if (allocation && !obligation) {
      problems.add(new Problem(path + "/obligation", "allocation-obligation"));
    }
    boolean paid = false;
    for (int index = 0; index < events.size(); index++) {
      String type = typeOf(events.get(index));
      paid |= PAYMENT.equals(type);
      if (BANK_REVERSAL.equals(type) && !paid) {
        problems.add(new Problem(path + "/events/" + index, "reversal-payment"));
      }
    }
  }

  private static boolean hasEvent(JsonNode events, String type) {
    return events.valueStream().anyMatch(event -> type.equals(typeOf(event)));
  }

  private static String typeOf(JsonNode event) {
    JsonNode type = event.path("type");
    return type.isString() ? type.stringValue() : "";
  }
}
