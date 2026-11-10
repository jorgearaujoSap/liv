package co.com.sapenlinea.liv.contracts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
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

  /** A version {@code <MAJOR>.<MINOR>}, without leading zeros (as {@code jsonschema.sh}). */
  static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

  private static final Pattern CASES_FILE =
      Pattern.compile("(" + VERSION.pattern() + ")\\.casos\\.json");

  private CommonCases() {}

  static JsonNode load(String version) {
    return EXACT.readTree(FOLDER.resolve(version + ".casos.json").toFile());
  }

  /** The versions that have a cases file, from the lowest to the highest. */
  static List<String> versions() {
    try (Stream<Path> files = Files.list(FOLDER)) {
      return files
          .map(file -> CASES_FILE.matcher(file.getFileName().toString()))
          .filter(Matcher::matches)
          .map(matcher -> matcher.group(1))
          .sorted(CommonCases::compare)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The cases of a section ({@code carga} or {@code validacion}) of every cases file, as JUnit
   * arguments {@code (version, case name, case)}.
   */
  static Stream<Arguments> cases(String section) {
    return cases(section, testCase -> true);
  }

  /**
   * The same, only the cases that pass the filter. The version of a cases file is the one in its
   * header (ADR 0034), and it must be the one in its name. A file without the section, or with it
   * empty, fails instead of running no case.
   */
  static Stream<Arguments> cases(String section, Predicate<JsonNode> filter) {
    return versions().stream()
        .flatMap(version -> {
          JsonNode file = load(version);
          if (!file.path("version").isString()
              || !version.equals(file.get("version").stringValue())) {
            throw new IllegalStateException(
                version + ".casos.json: su versión de cabecera no es \"" + version + "\"");
          }
          if (!file.path(section).isArray() || file.get(section).isEmpty()) {
            throw new IllegalStateException(
                version + ".casos.json: no tiene casos en «" + section + "»");
          }
          return file.get(section)
              .valueStream()
              .filter(filter)
              .map(testCase -> Arguments.of(version, testCase.get("caso").stringValue(), testCase));
        });
  }

  /**
   * The same text if it is a version {@code <MAJOR>.<MINOR>}; otherwise, an error that names it.
   */
  static String requireVersion(String text) {
    if (!VERSION.matcher(text).matches()) {
      throw new IllegalArgumentException("no es una versión MAYOR.MENOR: " + text);
    }
    return text;
  }

  /**
   * Orders two versions {@code <MAJOR>.<MINOR>} as numbers, not as text: {@code 1.10} is higher
   * than {@code 1.9} (ADR 0034).
   */
  static int compare(String a, String b) {
    String[] first = requireVersion(a).split("\\.");
    String[] second = requireVersion(b).split("\\.");
    // MAJOR and MINOR; BigInteger because the version comes from a file name, with no size limit.
    for (int part = 0; part < 2; part++) {
      int order = new BigInteger(first[part]).compareTo(new BigInteger(second[part]));
      if (order != 0) {
        return order;
      }
    }
    return 0;
  }
}
