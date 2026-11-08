package co.com.sapenlinea.liv.contracts;

import java.nio.file.Path;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The common cases of a version of the jurisdiction configuration schema ({@code
 * <MAJOR.MINOR>.casos.json}, ADR 0032). Numbers are read exactly, as the loader reads them:
 * integers as {@code BigInteger} and decimals as {@code BigDecimal} with their scale, so {@code
 * 1000.0} and {@code 1000} stay different. A repeated key in a schema or a cases file is an error,
 * so that a second definition of a field can never win in silence.
 */
final class CommonCases {

  /** The backend folder: Maven sets {@code basedir}; otherwise, the working directory. */
  static final Path BACKEND = Path.of(System.getProperty("basedir", ""));

  static final Path JSON_SCHEMAS = BACKEND.resolve(Path.of("contracts", "jsonschema"));
  static final Path FOLDER = JSON_SCHEMAS.resolve("config-jurisdiccion");

  static final JsonMapper EXACT =
      JsonMapper.builder()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
          .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .build();

  private CommonCases() {}

  static JsonNode load(String version) {
    return EXACT.readTree(FOLDER.resolve(version + ".casos.json").toFile());
  }
}
