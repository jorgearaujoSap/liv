package co.com.sapenlinea.liv.golden;

import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * The copy of the jurisdiction configuration that a fixture cites: where it is, its SHA-256 and its
 * content. The path is relative to {@code src/test/resources} and never leaves it (IN-01).
 */
public final class GoldenConfiguration {

  /** The test resources of the backend. */
  public static final Path RESOURCES = Path.of("src", "test", "resources");

  private GoldenConfiguration() {}

  /**
   * The SHA-256 in lowercase hexadecimal of a text with LF line ends, as Doc. 03 section 9.2 does.
   * On Windows with {@code core.autocrlf} the copy on disk has CRLF and its direct hash differs.
   */
  public static String sha256Lf(String text) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** The file, if the relative path stays inside {@code src/test/resources}; otherwise empty. */
  public static Optional<Path> insideResources(String relative) {
    throw new UnsupportedOperationException("pendiente");
  }

  /** Loads a copy with the safe YAML loader; fails if the path is outside the resources. */
  public static JsonNode load(String relative) {
    throw new UnsupportedOperationException("pendiente");
  }
}
