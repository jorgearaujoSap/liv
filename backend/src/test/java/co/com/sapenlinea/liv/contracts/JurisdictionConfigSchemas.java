package co.com.sapenlinea.liv.contracts;

import com.networknt.schema.Error;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaContext;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialect;
import com.networknt.schema.dialect.Dialects;
import com.networknt.schema.keyword.Keyword;
import com.networknt.schema.keyword.KeywordValidator;
import com.networknt.schema.keyword.TypeValidator;
import com.networknt.schema.path.PathType;
import com.networknt.schema.utils.JsonType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Picks the published version of the jurisdiction configuration schema that a configuration
 * declares in {@code esquema_version}, before validating (004-09): a version that does not exist is
 * an error, never a validation against another version. The validator follows ADR 0032: no remote
 * loading of schemas and a decimal is never an integer ({@code 1000.0} is not {@code 1000}).
 */
final class JurisdictionConfigSchemas {

  static final Path FOLDER = CommonCases.FOLDER;

  /** The path of every error about the version, also when the configuration has none. */
  static final String VERSION_PATH = "/esquema_version";

  private static final String META_SCHEMA = "https://json-schema.org/draft/2020-12/schema";

  private static final Pattern VERSION = CommonCases.VERSION;

  /**
   * Draft 2020-12 with a stricter {@code type}: since Draft 6 the standard takes {@code 1000.0} for
   * an integer, and the validator has no option to turn it off. ADR 0032 asks for the opposite, as
   * the root validator does, so {@code integer} only takes a number written without a decimal part.
   */
  private static final Dialect DIALECT =
      Dialect.builder(Dialects.getDraft202012()).keyword(new StrictType()).build();

  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(
          DIALECT,
          builder ->
              builder
                  .schemaRegistryConfig(
                      SchemaRegistryConfig.builder()
                          .pathType(PathType.JSON_POINTER)
                          .losslessNarrowing(false)
                          .typeLoose(false)
                          .formatAssertionsEnabled(false)
                          .build())
                  .schemaLoader(loader -> loader.fetchRemoteResources(false)));

  private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

  private JurisdictionConfigSchemas() {}

  /**
   * The schema of a version, read from its file, or empty if the version is not published. The form
   * of the version is checked before it becomes part of a path.
   */
  static Optional<Schema> forVersion(String version) {
    if (!VERSION.matcher(version).matches()) {
      return Optional.empty();
    }
    if (!Files.isDirectory(FOLDER)) {
      throw new IllegalStateException(
          "no está la carpeta de los esquemas: " + FOLDER.toAbsolutePath());
    }
    Path file = FOLDER.resolve(version + ".json");
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    return Optional.of(
        CACHE.computeIfAbsent(version, v -> compile(CommonCases.EXACT.readTree(file.toFile()))));
  }

  /** Whether {@code version} is lower than or equal to {@code limit}, compared as numbers. */
  static boolean isUpTo(String version, String limit) {
    return CommonCases.compare(version, limit) <= 0;
  }

  /**
   * The schema of a version in the registry that holds only the versions up to {@code
   * casesVersion}, the version of the cases file being run (ADR 0034). A later version, though
   * published, is not resolved, so a published case does not change its result when a new version
   * appears.
   */
  static Optional<Schema> forVersion(String version, String casesVersion) {
    if (!VERSION.matcher(casesVersion).matches()) {
      throw new IllegalArgumentException(
          "la versión de los casos no es MAYOR.MENOR: " + casesVersion);
    }
    if (!VERSION.matcher(version).matches() || !isUpTo(version, casesVersion)) {
      return Optional.empty();
    }
    return forVersion(version);
  }

  /** A schema with the configuration of ADR 0032 (no remote loading, strict integers). */
  static Schema compile(JsonNode schema) {
    return REGISTRY.getSchema(schema);
  }

  /** The errors of a schema against the Draft 2020-12 meta-schema, which the validator bundles. */
  static List<Error> metaSchemaErrors(JsonNode schema) {
    return REGISTRY.getSchema(SchemaLocation.of(META_SCHEMA)).validate(schema);
  }

  /**
   * The JSON Pointer paths of the errors of a configuration, with every published version; empty if
   * it is valid.
   */
  static Set<String> errors(JsonNode configuration) {
    return errorsWith(configuration, JurisdictionConfigSchemas::forVersion);
  }

  /** The same, with the versions up to {@code casesVersion} (ADR 0034). */
  static Set<String> errors(JsonNode configuration, String casesVersion) {
    return errorsWith(configuration, version -> forVersion(version, casesVersion));
  }

  private static Set<String> errorsWith(
      JsonNode configuration, Function<String, Optional<Schema>> resolver) {
    JsonNode version = configuration.isObject() ? configuration.get("esquema_version") : null;
    Optional<Schema> schema =
        version != null && version.isString()
            ? resolver.apply(version.stringValue())
            : Optional.empty();
    if (schema.isEmpty()) {
      return Set.of(VERSION_PATH);
    }
    return paths(schema.get().validate(configuration));
  }

  /**
   * The path of each error. For {@code required} and {@code additionalProperties} it is the path of
   * the missing or unknown key, not the one of its object, so that it names the key (004-01,
   * 004-02).
   */
  static Set<String> paths(List<Error> errors) {
    Set<String> paths = new TreeSet<>();
    for (Error error : errors) {
      String path = error.getInstanceLocation().toString();
      String keyword = error.getKeyword();
      if (("required".equals(keyword) || "additionalProperties".equals(keyword))
          && error.getProperty() != null) {
        path = path + "/" + escape(error.getProperty());
      }
      paths.add(path);
    }
    return paths;
  }

  /** The {@code type} keyword, where a decimal never matches {@code integer}. */
  private static final class StrictType implements Keyword {

    @Override
    public String getValue() {
      return "type";
    }

    @Override
    public KeywordValidator newValidator(
        SchemaLocation location, JsonNode node, Schema parent, SchemaContext context) {
      return new TypeValidator(location, node, parent, context) {
        @Override
        public boolean equalsToSchemaType(JsonNode value, ExecutionContext execution) {
          if (getSchemaType() == JsonType.INTEGER
              && value.isNumber()
              && !value.isIntegralNumber()) {
            return false;
          }
          return super.equalsToSchemaType(value, execution);
        }
      };
    }
  }

  /** A key as a JSON Pointer token (RFC 6901). */
  static String escape(String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }
}
