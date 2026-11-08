package co.com.sapenlinea.liv.contracts;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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

  private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+");

  private static final SchemaRegistry REGISTRY =
      SchemaRegistry.withDefaultDialect(
          SpecificationVersion.DRAFT_2020_12,
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

  private JurisdictionConfigSchemas() {}

  /** The schema of a version, read from its file, or null if the version is not published. */
  static Schema forVersion(String version) {
    Path file = FOLDER.resolve(version + ".json");
    if (!VERSION.matcher(version).matches() || !Files.isRegularFile(file)) {
      return null;
    }
    return REGISTRY.getSchema(CommonCases.EXACT.readTree(file.toFile()));
  }

  /** The errors of a schema against the Draft 2020-12 meta-schema, which the validator bundles. */
  static List<Error> metaSchemaErrors(JsonNode schema) {
    return REGISTRY.getSchema(SchemaLocation.of(META_SCHEMA)).validate(schema);
  }

  /** The JSON Pointer paths of the errors of a configuration; empty if it is valid. */
  static Set<String> errors(JsonNode configuration) {
    JsonNode version = configuration.isObject() ? configuration.get("esquema_version") : null;
    Schema schema =
        version != null && version.isString() ? forVersion(version.stringValue()) : null;
    if (schema == null) {
      return Set.of(VERSION_PATH);
    }
    return paths(schema.validate(configuration));
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

  /** A key as a JSON Pointer token (RFC 6901). */
  static String escape(String key) {
    return key.replace("~", "~0").replace("/", "~1");
  }
}
