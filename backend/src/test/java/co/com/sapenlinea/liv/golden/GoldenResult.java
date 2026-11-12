package co.com.sapenlinea.liv.golden;

import tools.jackson.databind.JsonNode;

/**
 * What an engine returns for a golden case, seen by the comparator as a tree. Each module's result
 * type implements it, so the comparator knows nothing about {@code tax} or {@code ledger} and the
 * packages form no cycle. Amounts are decimal nodes and dates are ISO-8601 text. A key the expected
 * result gives as {@code null} must be emitted as {@code null}: an absent key differs.
 */
public interface GoldenResult {

  JsonNode asTree();
}
