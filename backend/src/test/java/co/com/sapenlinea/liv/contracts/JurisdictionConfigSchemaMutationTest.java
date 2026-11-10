package co.com.sapenlinea.liv.contracts;

import static co.com.sapenlinea.liv.contracts.JurisdictionConfigSchemas.escape;
import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.FieldClass;
import co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.Probe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Changes every node of the two test configurations, one at a time, and checks the result against
 * an oracle written from the plan of the feature 004 ("Contenido del esquema 1.0"), not from the
 * schema:
 *
 * <ul>
 *   <li>general rule: every object rejects unknown keys; a key present in both configurations (in
 *       every element, for a list) is required; a key present in only one is optional;
 *   <li>exceptions: at the root only the six mandatory sections are required; no modality is
 *       required, and {@code activa} is required in each of them;
 *   <li>every field, also each item of a list, takes the probes of its class ({@link
 *       JurisdictionConfigFieldClasses}): PENDIENTE only where the team fills in, codes in
 *       capitals, fractions between 0 and 1, integers without a decimal part, the closed lists…;
 *   <li>{@code null} is valid only in the listed fields.
 * </ul>
 */
@Tag("004-01")
@Tag("004-02")
@Tag("004-03")
@Tag("004-04")
@Tag("004-05")
@Tag("004-06")
@Tag("004-07")
@Tag("004-08")
@DisplayName("Mutación de cada nodo de las configuraciones de prueba (004-01 a 004-08)")
class JurisdictionConfigSchemaMutationTest {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private static final String MODALITIES = "/competencia/modalidades";
  private static final String CONVENIO = MODALITIES + "/CONVENIO_DEPTO/convenio";

  /** What the oracle takes from the plan of a version, besides the general rule. */
  private record Rules(Set<String> rootRequired, Set<String> nullAllowed) {}

  /**
   * The rules of each version: the required sections of the root and the only fields where null is
   * valid. A version without an entry here fails, never gets the rules of another one.
   */
  private static final Map<String, Rules> RULES =
      Map.of(
          "1.0",
          new Rules(
              Set.of(
                  "esquema_version",
                  "jurisdiccion",
                  "competencia",
                  "vigencia",
                  "normas",
                  "distribucion_recaudo"),
              Set.of(
                  "/tarifas[]/rangos[]/hasta_inclusivo",
                  "/beneficios[]/tope_pesos",
                  "/beneficios[]/anios_desde_matricula",
                  "/jurisdiccion/departamento_padre_dane",
                  CONVENIO + "/numero",
                  CONVENIO + "/fecha_inicio",
                  CONVENIO + "/fecha_fin",
                  "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/dias",
                  "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/fecha",
                  "/calendario/vehiculos_nuevos/descuento/dias")));

  static Stream<String> versions() {
    return CommonCases.versions().stream();
  }

  static Stream<Arguments> mutations() {
    List<Arguments> all = new ArrayList<>();
    for (String version : CommonCases.versions()) {
      Rules rules = rules(version);
      Oracle oracle = Oracle.build(version, rules);
      for (String name : TestConfigurations.ALL) {
        new Generator(version, name, oracle, all)
            .walk(TestConfigurations.load(version, name), "", "");
      }
    }
    return all.stream();
  }

  private static Rules rules(String version) {
    Rules rules = RULES.get(version);
    if (rules == null) {
      throw new IllegalStateException("el oráculo no tiene las reglas de la versión " + version);
    }
    return rules;
  }

  @ParameterizedTest(name = "[{0}] {1}")
  @MethodSource("mutations")
  void mutation(
      String version,
      String description,
      String configuration,
      UnaryOperator<ObjectNode> change,
      Set<String> expected) {
    ObjectNode changed = change.apply(TestConfigurations.load(version, configuration));

    assertThat(JurisdictionConfigSchemas.errors(changed)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "Cada versión del esquema tiene sus copias, sus clases de campo y las reglas del oráculo")
  void everyVersionHasItsFixtures() {
    List<String> versions = CommonCases.versions();

    assertThat(RULES.keySet())
        .as("reglas del oráculo")
        .containsExactlyInAnyOrderElementsOf(versions);
    assertThat(JurisdictionConfigFieldClasses.versions())
        .as("tablas de campos")
        .containsExactlyInAnyOrderElementsOf(versions);
    assertThat(TestConfigurations.versions())
        .as("carpetas de copias")
        .containsExactlyInAnyOrderElementsOf(versions);
  }

  @ParameterizedTest(name = "004-05 · [{0}] cada campo tiene su clase, y cada clase, su campo")
  @MethodSource("versions")
  void everyFieldHasAClass(String version) {
    Set<String> leaves = new TreeSet<>();
    for (String name : TestConfigurations.ALL) {
      leaves(TestConfigurations.load(version, name), "", leaves, version);
    }
    leaves.remove("/esquema_version");

    assertThat(leaves.stream().filter(l -> JurisdictionConfigFieldClasses.of(version, l) == null))
        .as("campos sin clase")
        .isEmpty();
    assertThat(new TreeSet<>(JurisdictionConfigFieldClasses.all(version).keySet()))
        .as("clases sin campo")
        .isSubsetOf(leaves);
  }

  @ParameterizedTest(name = "004-05 · [{0}] cada lista cerrada tiene exactamente sus valores")
  @MethodSource("versions")
  void closedListsAreExact(String version) {
    JsonNode schema =
        CommonCases.EXACT.readTree(CommonCases.FOLDER.resolve(version + ".json").toFile());
    Map<String, List<String>> expected = new HashMap<>();
    JurisdictionConfigFieldClasses.closedLists(version)
        .forEach((path, values) -> expected.put(schemaPointer(path) + "/enum", values));
    expected.put("/$defs/estado/enum", List.of("VIGENTE", "POR_CONFIRMAR", "PENDIENTE"));

    expected.forEach((pointer, values) ->
        assertThat(schema.at(pointer).valueStream().map(JsonNode::stringValue))
            .as(pointer)
            .containsExactlyElementsOf(values));
  }

  /**
   * The path in the schema of a field: {@code /a[]/b} is {@code /properties/a/items/properties/b}.
   */
  private static String schemaPointer(String general) {
    StringBuilder pointer = new StringBuilder();
    for (String token : general.substring(1).split("/")) {
      boolean item = token.endsWith("[]");
      pointer.append("/properties/").append(item ? token.substring(0, token.length() - 2) : token);
      if (item) {
        pointer.append("/items");
      }
    }
    return pointer.toString();
  }

  /** The general path of every scalar, and of every list of scalars (with {@code []}). */
  private static void leaves(JsonNode node, String general, Set<String> out, String version) {
    if (node.isObject()) {
      node.properties()
          .forEach(e -> leaves(e.getValue(), general + "/" + e.getKey(), out, version));
    } else if (node.isArray() && !node.isEmpty() && !node.get(0).isValueNode()) {
      node.values().forEach(v -> leaves(v, general + "[]", out, version));
    } else if (node.isArray()) {
      // A list of scalars, or an empty list that the plan classifies as one.
      if (!node.isEmpty() || JurisdictionConfigFieldClasses.of(version, general + "[]") != null) {
        out.add(general + "[]");
      }
    } else {
      out.add(general);
    }
  }

  /** The general rule of the plan and its exceptions, computed from the two configurations. */
  private record Oracle(
      Rules rules, Map<String, List<ObjectNode>> objects, Map<String, Set<String>> presentIn) {

    static Oracle build(String version, Rules rules) {
      Oracle oracle = new Oracle(rules, new HashMap<>(), new HashMap<>());
      for (String name : TestConfigurations.ALL) {
        oracle.index(TestConfigurations.load(version, name), "", name);
      }
      return oracle;
    }

    private void index(JsonNode node, String general, String configuration) {
      if (node.isObject()) {
        objects.computeIfAbsent(general, k -> new ArrayList<>()).add((ObjectNode) node);
        presentIn.computeIfAbsent(general, k -> new HashSet<>()).add(configuration);
        node.properties()
            .forEach(e -> index(e.getValue(), general + "/" + e.getKey(), configuration));
      } else if (node.isArray()) {
        node.values().forEach(v -> index(v, general + "[]", configuration));
      }
    }

    boolean required(String general, String key) {
      if (general.isEmpty()) {
        return rules.rootRequired().contains(key);
      }
      if (general.equals(MODALITIES)) {
        return false;
      }
      if (general.startsWith(MODALITIES + "/")
          && general.indexOf('/', MODALITIES.length() + 1) < 0
          && key.equals("activa")) {
        return true;
      }
      return presentIn.get(general).size() == TestConfigurations.ALL.size()
          && objects.get(general).stream().allMatch(o -> o.has(key));
    }
  }

  /** Writes the mutations of one configuration. */
  private record Generator(
      String version, String configuration, Oracle oracle, List<Arguments> out) {

    void walk(JsonNode node, String path, String general) {
      if (!path.isEmpty()) {
        otherType(path);
      }
      if (node.isObject()) {
        object((ObjectNode) node, path, general);
      } else if (node.isArray()) {
        array(node, path, general);
      }
    }

    /** A list of scalars takes each probe as its only item; a list of objects, item by item. */
    private void array(JsonNode node, String path, String general) {
      FieldClass items = JurisdictionConfigFieldClasses.of(version, general + "[]");
      if (items != null) {
        add(
            "CJ01 · null como elemento de " + path,
            root -> TestConfigurations.set(root, path, NODES.arrayNode().add(NODES.nullNode())),
            Set.of(path + "/0"));
        for (Probe probe : items.probes()) {
          add(
              "004-05 · " + label(probe) + " en " + path + "/0 (" + items.name() + ")",
              root -> TestConfigurations.set(root, path, NODES.arrayNode().add(probe.value())),
              probe.valid() ? Set.of() : Set.of(path + "/0"));
        }
        return;
      }
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), path + "/" + i, general + "[]");
      }
    }

    private void object(ObjectNode node, String path, String general) {
      add(
          "004-02 · clave desconocida en " + (path.isEmpty() ? "la raíz" : path),
          root -> withKey(root, path, "clave_desconocida", NODES.stringNode("x")),
          Set.of(path + "/clave_desconocida"));
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        String key = entry.getKey();
        String child = path + "/" + escape(key);
        String childGeneral = general + "/" + key;
        boolean required = oracle.required(general, key);
        add(
            "004-01 · sin " + child + (required ? " (obligatoria)" : " (opcional)"),
            root -> TestConfigurations.remove(root, child),
            required ? Set.of(child) : Set.of());
        JsonNode value = entry.getValue();
        if (value.isValueNode()) {
          scalar(child, childGeneral);
        } else {
          walk(value, child, childGeneral);
        }
      }
    }

    private void scalar(String path, String general) {
      FieldClass fieldClass = JurisdictionConfigFieldClasses.of(version, general);
      if (fieldClass != null) {
        for (Probe probe : fieldClass.probes()) {
          add(
              "004-05 · " + label(probe) + " en " + path + " (" + fieldClass.name() + ")",
              root -> TestConfigurations.set(root, path, probe.value()),
              probe.valid() ? Set.of() : Set.of(path));
        }
      }
      boolean nullable = oracle.rules().nullAllowed().contains(general);
      add(
          "CJ01 · null en " + path + (nullable ? " (vale)" : " (no vale)"),
          root -> TestConfigurations.set(root, path, NODES.nullNode()),
          nullable ? Set.of() : Set.of(path));
    }

    /**
     * An object or a list is never null and never a text (the plan admits null only in scalars).
     */
    private void otherType(String path) {
      add(
          "CJ01 · null en lugar de " + path,
          root -> TestConfigurations.set(root, path, NODES.nullNode()),
          Set.of(path));
      add(
          "CJ01 · un texto en lugar de " + path,
          root -> TestConfigurations.set(root, path, NODES.stringNode("x")),
          Set.of(path));
    }

    private void add(String description, UnaryOperator<ObjectNode> change, Set<String> expected) {
      out.add(
          Arguments.of(
              version, description + " · " + configuration, configuration, change, expected));
    }

    private static String label(Probe probe) {
      return probe.value() + (probe.valid() ? " vale" : " no vale");
    }

    private static ObjectNode withKey(ObjectNode root, String path, String key, JsonNode value) {
      JsonNode target = path.isEmpty() ? root : root.at(path);
      ((ObjectNode) target).set(key, value);
      return root;
    }
  }
}
