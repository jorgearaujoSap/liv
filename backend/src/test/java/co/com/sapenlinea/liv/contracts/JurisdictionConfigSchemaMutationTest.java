package co.com.sapenlinea.liv.contracts;

import static co.com.sapenlinea.liv.contracts.JurisdictionConfigSchemas.escape;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
 *   <li>{@code estado} only takes its three values;
 *   <li>{@code PENDIENTE} is valid only in dates, fractions, DANE codes and the listed free texts;
 *   <li>{@code null} is valid only in the listed fields.
 * </ul>
 */
@Tag("004-01")
@Tag("004-02")
@Tag("004-03")
@Tag("004-04")
@Tag("004-08")
@DisplayName("Mutación de cada nodo de las configuraciones de prueba (004-01 a 004-08)")
class JurisdictionConfigSchemaMutationTest {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private static final Set<String> ROOT_REQUIRED =
      Set.of(
          "esquema_version",
          "jurisdiccion",
          "competencia",
          "vigencia",
          "normas",
          "distribucion_recaudo");

  private static final String MODALITIES = "/competencia/modalidades";
  private static final String CONVENIO = MODALITIES + "/CONVENIO_DEPTO/convenio";

  /** Fields (with {@code []} for any index) where the team may still write PENDIENTE. */
  private static final Set<String> PENDIENTE_ALLOWED =
      Set.of(
          // DANE codes
          "/jurisdiccion/codigo_dane",
          "/jurisdiccion/codigo_divipola_8",
          "/jurisdiccion/departamento_padre_dane",
          // free texts
          "/jurisdiccion/nombre",
          "/jurisdiccion/departamento",
          "/jurisdiccion/administracion/nombre",
          "/jurisdiccion/administracion/nit",
          "/normas[]/numero",
          "/normas[]/descripcion",
          "/normas[]/autoridad",
          CONVENIO + "/numero",
          "/sujecion/motos_electricas_criterio_sujecion",
          "/recaudo/archivo_banco/formato",
          // dates
          "/vigencia/inicio",
          "/vigencia/fin",
          "/normas[]/fecha",
          CONVENIO + "/fecha_inicio",
          CONVENIO + "/fecha_fin",
          "/calendario/grupos[]/fecha_inicio_pago",
          "/calendario/grupos[]/fecha_limite_descuento",
          "/calendario/grupos[]/fecha_limite_pago_sin_intereses",
          "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/fecha");

  /** Rates and percentages are fractions, which also accept PENDIENTE. */
  private static final Set<String> FRACTION_KEYS =
      Set.of("tarifa", "tarifa_maxima", "porcentaje", "tasa");

  /** The only fields where null is valid. */
  private static final Set<String> NULL_ALLOWED =
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
          "/calendario/vehiculos_nuevos/descuento/dias");

  static Stream<Arguments> mutations() {
    Map<String, List<ObjectNode>> objects = new HashMap<>();
    Map<String, Set<String>> presentIn = new HashMap<>();
    for (String name : TestConfigurations.ALL) {
      index(TestConfigurations.load(name), "", name, objects, presentIn);
    }
    Oracle oracle = new Oracle(objects, presentIn);
    List<Arguments> all = new ArrayList<>();
    for (String name : TestConfigurations.ALL) {
      new Generator(name, oracle, all).walk(TestConfigurations.load(name), "", "");
    }
    return all.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mutations")
  void mutation(
      String description,
      String configuration,
      UnaryOperator<ObjectNode> change,
      Set<String> expected) {
    ObjectNode changed = change.apply(TestConfigurations.load(configuration));

    assertThat(JurisdictionConfigSchemas.errors(changed)).isEqualTo(expected);
  }

  /** Records every object of a configuration under its general path ({@code []} for an index). */
  private static void index(
      JsonNode node,
      String general,
      String configuration,
      Map<String, List<ObjectNode>> objects,
      Map<String, Set<String>> presentIn) {
    if (node.isObject()) {
      objects.computeIfAbsent(general, k -> new ArrayList<>()).add((ObjectNode) node);
      presentIn.computeIfAbsent(general, k -> new java.util.HashSet<>()).add(configuration);
      node.properties()
          .forEach(e ->
              index(e.getValue(), general + "/" + e.getKey(), configuration, objects, presentIn));
    } else if (node.isArray()) {
      node.values().forEach(v -> index(v, general + "[]", configuration, objects, presentIn));
    }
  }

  /** The general rule of the plan and its exceptions. */
  private record Oracle(Map<String, List<ObjectNode>> objects, Map<String, Set<String>> presentIn) {

    boolean required(String general, String key) {
      if (general.isEmpty()) {
        return ROOT_REQUIRED.contains(key);
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

    boolean acceptsPendiente(String general, String key) {
      return PENDIENTE_ALLOWED.contains(general) || FRACTION_KEYS.contains(key);
    }

    boolean acceptsNull(String general) {
      return NULL_ALLOWED.contains(general);
    }
  }

  /** Writes the mutations of one configuration. */
  private record Generator(String configuration, Oracle oracle, List<Arguments> out) {

    void walk(JsonNode node, String path, String general) {
      if (node.isObject()) {
        object((ObjectNode) node, path, general);
      } else if (node.isArray()) {
        for (int i = 0; i < node.size(); i++) {
          walk(node.get(i), path + "/" + i, general + "[]");
        }
      }
    }

    private void object(ObjectNode node, String path, String general) {
      String unknown = path + "/clave_desconocida";
      add(
          "004-02 · clave desconocida en " + label(path),
          root -> withKey(root, path, "clave_desconocida", NODES.stringNode("x")),
          Set.of(unknown));
      if (node.has("estado")) {
        add(
            "004-03 · estado APROBADO en " + label(path),
            root -> TestConfigurations.set(root, path + "/estado", NODES.stringNode("APROBADO")),
            Set.of(path + "/estado"));
      }
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
          scalar(child, childGeneral, key);
        } else {
          walk(value, child, childGeneral);
        }
      }
    }

    private void scalar(String path, String general, String key) {
      boolean pendiente = oracle.acceptsPendiente(general, key);
      add(
          "004-08 · PENDIENTE en " + path + (pendiente ? " (vale)" : " (no vale)"),
          root -> TestConfigurations.set(root, path, NODES.stringNode("PENDIENTE")),
          pendiente ? Set.of() : Set.of(path));
      boolean nullable = oracle.acceptsNull(general);
      add(
          "CJ01 · null en " + path + (nullable ? " (vale)" : " (no vale)"),
          root -> TestConfigurations.set(root, path, NODES.nullNode()),
          nullable ? Set.of() : Set.of(path));
    }

    private void add(String description, UnaryOperator<ObjectNode> change, Set<String> expected) {
      String name = description + " · " + configuration;
      out.add(Arguments.of(name, configuration, change, expected));
    }

    private static ObjectNode withKey(ObjectNode root, String path, String key, JsonNode value) {
      JsonNode target = path.isEmpty() ? root : root.at(path);
      ((ObjectNode) target).set(key, value);
      return root;
    }

    private static String label(String path) {
      return path.isEmpty() ? "la raíz" : path;
    }
  }
}
