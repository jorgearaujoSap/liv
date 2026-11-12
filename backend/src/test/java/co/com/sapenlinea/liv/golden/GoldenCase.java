package co.com.sapenlinea.liv.golden;

import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * One golden case: an example of a functional document, transcribed. The input and the expected
 * result are trees, so each module reads them with its own types.
 *
 * @param id the id of the case in the document table (for example "L9")
 * @param rules the rule codes of its row
 * @param source where the case comes from; the row is null when the document has no table row
 * @param assumptions closed values: what the document takes for granted (the schema lists them)
 * @param inferredInputs inputs the document does not give and the case needs, each with its reason
 * @param dependsOn the keys of the jurisdiction configuration whose value the case relies on
 * @param expected only the values the document gives
 * @param comparison scale and rounding by field name; a field not listed is compared exactly
 * @param configuration the copy of the jurisdiction configuration the case uses
 */
public record GoldenCase(
    String id,
    List<String> rules,
    String description,
    Source source,
    List<String> assumptions,
    List<InferredInput> inferredInputs,
    List<String> dependsOn,
    JsonNode input,
    JsonNode expected,
    Map<String, Comparison> comparison,
    Configuration configuration) {

  /** The document, its version, its section and the row of the table. */
  public record Source(String document, String version, String section, Integer row) {}

  /** An input of the case that the document does not give. */
  public record InferredInput(String field, String reason) {}

  /** How one field is compared: rounded to {@code scale} with {@code rounding}. */
  public record Comparison(int scale, RoundingMode rounding) {}

  /** The configuration file, relative to {@code src/test/resources}, and its SHA-256 with LF. */
  public record Configuration(String file, String sha256) {}
}
