package co.com.sapenlinea.liv.golden;

import co.com.sapenlinea.liv.contracts.CommonCases;
import co.com.sapenlinea.liv.contracts.SafeYamlLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * The copy of the jurisdiction configuration that a fixture cites: where it is, its SHA-256 and its
 * content. The path is relative to {@code src/test/resources} and never leaves it (IN-01).
 */
public final class GoldenConfiguration {

  /** The test resources of the backend. */
  public static final Path RESOURCES =
      CommonCases.BACKEND.resolve(Path.of("src", "test", "resources"));

  private GoldenConfiguration() {}

  /**
   * The SHA-256 in lowercase hexadecimal of a text with LF line ends, as Doc. 03 section 9.2 does.
   * On Windows with {@code core.autocrlf} the copy on disk has CRLF and its direct hash differs.
   */
  public static String sha256Lf(String text) {
    try {
      byte[] bytes = text.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("la JVM no tiene SHA-256", e);
    }
  }

  /** The file, if the relative path stays inside {@code src/test/resources}; otherwise empty. */
  public static Optional<Path> insideResources(String relative) {
    if (isNotRelative(relative)) {
      return Optional.empty();
    }
    try {
      Path root = RESOURCES.normalize();
      Path file = root.resolve(relative).normalize();
      return file.startsWith(root) && !file.equals(root) ? Optional.of(file) : Optional.empty();
    } catch (InvalidPathException e) {
      return Optional.empty();
    }
  }

  /** Empty, absolute, with a drive letter or with back slashes: never relative to the resources. */
  private static boolean isNotRelative(String path) {
    return path.isBlank()
        || path.startsWith("/")
        || path.indexOf('\\') >= 0
        || path.indexOf(':') >= 0;
  }

  /** Loads a copy with the safe YAML loader; fails if the path is outside the resources. */
  public static JsonNode load(String relative) {
    Path file =
        insideResources(relative)
            .orElseThrow(() ->
                new IllegalArgumentException(
                    "la ruta sale de src/test/resources o no es relativa: " + relative));
    try {
      return SafeYamlLoader.load(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
