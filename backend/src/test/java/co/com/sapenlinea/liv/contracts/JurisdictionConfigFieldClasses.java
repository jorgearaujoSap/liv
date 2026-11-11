package co.com.sapenlinea.liv.contracts;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BigIntegerNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The class of every field of the jurisdiction configuration, written from the plan of the feature
 * 004 ("Contenido del esquema 1.0": text classes, fractions, amounts, integers, dates, DANE codes
 * and closed lists), not from the schema. Each class has probes, values with their expected result,
 * that {@link JurisdictionConfigSchemaMutationTest} writes in every field of that class. Paths use
 * {@code []} for any index; an item of a list of scalars is the path of the list plus {@code []}.
 */
final class JurisdictionConfigFieldClasses {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  /** A value and whether the field accepts it. */
  record Probe(JsonNode value, boolean valid) {}

  /** A class of field and its probes. */
  record FieldClass(String name, List<Probe> probes) {}

  static final FieldClass CODE =
      of(
          "código",
          ok(text("OTRO_CODIGO_2")),
          no(text("abc")),
          no(text("PENDIENTE")),
          no(text("POR_CONFIRMAR")),
          no(text("A B")),
          no(integer(1)),
          no(bool(true)));
  static final FieldClass IDENTIFIER =
      of(
          "identificador",
          ok(text("otro_id_2")),
          no(text("Otro")),
          no(text("PENDIENTE")),
          no(text("a.b")),
          no(text("")));
  static final FieldClass FIELD_PATH =
      of(
          "ruta",
          ok(text("seccion.campo_2")),
          no(text("campo")),
          no(text("PENDIENTE")),
          no(text("Seccion.campo")));
  static final FieldClass TECHNICAL_TEXT =
      of(
          "texto técnico",
          ok(text("Columna 2001")),
          ok(text("POR_CONFIRMAR")),
          no(text("PENDIENTE")),
          no(text("")),
          no(integer(1)));
  static final FieldClass FREE_TEXT =
      of(
          "texto libre",
          ok(text("Un texto")),
          ok(text("PENDIENTE")),
          ok(text("POR_CONFIRMAR")),
          no(text("")),
          no(integer(1)));
  static final FieldClass FRACTION =
      of(
          "fracción",
          ok(decimal("0.5")),
          ok(integer(0)),
          ok(integer(1)),
          ok(decimal("1.00")),
          ok(text("PENDIENTE")),
          no(decimal("1.5")),
          no(decimal("1.0000000000000001")),
          no(decimal("-0.1")),
          no(text("0.5")));
  static final FieldClass FACTOR =
      of(
          "factor",
          ok(decimal("1.5")),
          ok(decimal("0.5")),
          no(integer(0)),
          no(integer(-1)),
          no(text("PENDIENTE")));
  static final FieldClass AMOUNT =
      of(
          "monto",
          ok(integer(0)),
          ok(decimal("1000.5")),
          no(integer(-1)),
          no(text("PENDIENTE")),
          no(text("1000")));
  static final FieldClass INTEGER =
      of(
          "entero",
          ok(integer(0)),
          ok(integer(7)),
          no(integer(-1)),
          no(decimal("7.0")),
          no(decimal("0.5")),
          no(text("PENDIENTE")),
          no(text("7")));
  static final FieldClass DATE =
      of(
          "fecha",
          ok(text("2026-04-30")),
          ok(text("2026-02-30")),
          ok(text("PENDIENTE")),
          no(text("2026-13-01")),
          no(text("2026-00-10")),
          no(text("2026-04-32")),
          no(text("2026-04-00")),
          no(text("30/04/2026")),
          no(text("2026-4-30")));
  static final FieldClass MONTH_DAY =
      of(
          "mes-día",
          ok(text("12-31")),
          ok(text("01-01")),
          no(text("13-01")),
          no(text("00-10")),
          no(text("01-32")),
          no(text("01-00")),
          no(text("2026-01-01")),
          no(text("PENDIENTE")));
  static final FieldClass DANE =
      of(
          "DANE",
          ok(text("52")),
          ok(text("52356")),
          ok(text("PENDIENTE")),
          no(text("523")),
          no(text("5235A")),
          no(integer(52)));
  static final FieldClass DANE_DEPARTMENT =
      of(
          "DANE de departamento",
          ok(text("52")),
          ok(text("PENDIENTE")),
          no(text("52356")),
          no(text("5")));
  static final FieldClass DIVIPOLA =
      of(
          "DIVIPOLA de 8 dígitos",
          ok(text("52356000")),
          ok(text("PENDIENTE")),
          no(text("52356")),
          no(text("5235600A")));
  static final FieldClass DANE_IN_LIST =
      of(
          "DANE de una lista",
          ok(text("52")),
          ok(text("52356")),
          no(text("PENDIENTE")),
          no(text("523")));
  static final FieldClass BOOLEAN =
      of(
          "booleano",
          ok(bool(true)),
          ok(bool(false)),
          no(text("true")),
          no(integer(1)),
          no(text("PENDIENTE")));
  static final FieldClass ESTADO = closed("estado", "VIGENTE", "POR_CONFIRMAR", "PENDIENTE");

  /** Version 1.1 (P7): a year, an integer >= 0, that may also be PENDIENTE. */
  static final FieldClass YEAR_OR_PENDING =
      of(
          "año o PENDIENTE",
          ok(integer(0)),
          ok(integer(2019)),
          ok(text("PENDIENTE")),
          no(integer(-1)),
          no(decimal("2019.0")),
          no(decimal("0.5")),
          no(text("2019")),
          no(text("pendiente")),
          no(text("POR_CONFIRMAR")));

  /** Version 1.1 (P7): the code of a rule of {@code normas}, that may also be PENDIENTE. */
  static final FieldClass RULE_OR_PENDING =
      of(
          "norma o PENDIENTE",
          ok(text("ET_ART_641")),
          ok(text("PENDIENTE")),
          no(text("et_art_641")),
          no(text("POR_CONFIRMAR")),
          no(text("A B")),
          no(text("")),
          no(integer(1)),
          no(bool(true)));

  /** Version 1.1 (P8): an amount in UVT, a number >= 0 that is never PENDIENTE. */
  static final FieldClass UVT_AMOUNT =
      of(
          "cantidad de UVT",
          ok(integer(0)),
          ok(integer(10)),
          ok(decimal("10.5")),
          no(integer(-1)),
          no(decimal("-0.5")),
          no(text("PENDIENTE")),
          no(text("10")));

  /** The closed lists of 004-05 in the version 1.0, by field, with their exact values. */
  private static final Map<String, List<String>> CLOSED_LISTS_1_0 =
      Map.ofEntries(
          Map.entry(
              "/jurisdiccion/tipo",
              List.of("DEPARTAMENTO", "DISTRITO_CAPITAL", "DISTRITO", "MUNICIPIO")),
          Map.entry(
              "/base_gravable/vehiculo_nuevo/fuente_base",
              List.of("FACTURA", "DECLARACION_IMPORTACION")),
          Map.entry(
              "/beneficios[]/tipo",
              List.of("DESCUENTO_PORCENTUAL", "EXENCION_TOTAL", "VALOR_FIJO")),
          Map.entry(
              "/redondeo/metodo",
              List.of("MULTIPLO_MAS_CERCANO", "MULTIPLO_SUPERIOR", "SIN_REDONDEO")),
          Map.entry(
              "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/modo",
              List.of("FIN_DE_VIGENCIA", "DIAS_DESDE_CAUSACION", "FECHA_FIJA")),
          Map.entry(
              "/calendario/vehiculos_nuevos/descuento/modo",
              List.of("NO_APLICA", "DIAS_DESDE_CAUSACION")),
          Map.entry("/recaudo/descuento_pago_parcial", List.of("NO_APLICA", "PROPORCIONAL")));

  /**
   * The closed lists of the version 1.1: those of the 1.0, which it keeps, and the four of {@code
   * cartera} (P3 and P8).
   */
  private static final Map<String, List<String>> CLOSED_LISTS_1_1 =
      merge(
          CLOSED_LISTS_1_0,
          Map.ofEntries(
              Map.entry(
                  "/cartera/intereses_mora/redondeo_causacion/metodo",
                  List.of("MULTIPLO_MAS_CERCANO", "MULTIPLO_SUPERIOR", "SIN_REDONDEO")),
              Map.entry(
                  "/cartera/sanciones/sistema_determinacion",
                  List.of("LIQUIDACION_FACTURA", "DECLARACION", "PENDIENTE")),
              Map.entry(
                  "/cartera/prescripcion/inicio_termino",
                  List.of(
                      "FECHA_EJECUTORIA_FACTURA",
                      "FECHA_LIMITE_PAGO",
                      "FECHA_PRESENTACION_DECLARACION")),
              Map.entry(
                  "/cartera/cartera_historica/intereses_historicos",
                  List.of("RECALCULAR", "TOMAR_DEL_LEGADO"))));

  /** The closed lists of each version. A version without an entry fails, never takes another's. */
  private static final Map<String, Map<String, List<String>>> CLOSED_LISTS =
      Map.of("1.0", CLOSED_LISTS_1_0, "1.1", CLOSED_LISTS_1_1);

  private JurisdictionConfigFieldClasses() {}

  /** The lists of a version that keeps those of the previous one, plus the new ones. */
  private static Map<String, List<String>> merge(
      Map<String, List<String>> kept, Map<String, List<String>> added) {
    Map<String, List<String>> all = new HashMap<>(kept);
    added.forEach((path, values) -> {
      if (all.put(path, values) != null) {
        throw new IllegalStateException("lista cerrada repetida: " + path);
      }
    });
    return Map.copyOf(all);
  }

  /** The versions that have closed lists. */
  static Set<String> versions() {
    return CLOSED_LISTS.keySet();
  }

  /** The closed lists of 004-05 of a version, by field, with their exact values. */
  static Map<String, List<String>> closedLists(String version) {
    Map<String, List<String>> lists = CLOSED_LISTS.get(version);
    if (lists == null) {
      throw new IllegalArgumentException("no hay listas cerradas de la versión " + version);
    }
    return lists;
  }

  /**
   * The class of a field in a version, or null if the plan gives it none ({@code esquema_version},
   * which has its own tests). Every {@code estado} has the class estado.
   */
  static FieldClass of(String version, String generalPath) {
    if (generalPath.endsWith("/estado")) {
      return ESTADO;
    }
    return JurisdictionConfigFieldTable.classes(version).get(generalPath);
  }

  /**
   * Every classified field of a version, to check that the configurations have no field without a
   * class.
   */
  static Map<String, FieldClass> all(String version) {
    return Map.copyOf(JurisdictionConfigFieldTable.classes(version));
  }

  /** The class of a closed list of 004-05, with its documented values. */
  static FieldClass closedList(String path, List<String> values) {
    return closed(path, values.toArray(String[]::new));
  }

  private static FieldClass closed(String name, String... values) {
    List<Probe> probes = new ArrayList<>();
    for (String value : values) {
      probes.add(ok(text(value)));
    }
    probes.add(no(text("OTRO_VALOR")));
    probes.add(no(text(values[0].toLowerCase(java.util.Locale.ROOT))));
    if (!List.of(values).contains("PENDIENTE")) {
      probes.add(no(text("PENDIENTE")));
    }
    return of("lista cerrada " + name, probes.toArray(Probe[]::new));
  }

  /**
   * A class with its own probes plus one of each JSON type it never accepts: an object, a list and,
   * unless it is the type of the class, a boolean, a number and a text.
   */
  private static FieldClass of(String name, Probe... probes) {
    List<Probe> all = new ArrayList<>(List.of(probes));
    boolean text = all.stream().anyMatch(p -> p.valid() && p.value().isString());
    boolean number = all.stream().anyMatch(p -> p.valid() && p.value().isNumber());
    boolean bool = all.stream().anyMatch(p -> p.valid() && p.value().isBoolean());
    all.add(no(NODES.objectNode()));
    all.add(no(NODES.arrayNode()));
    if (!bool) {
      all.add(no(bool(true)));
    }
    if (!number) {
      all.add(no(decimal("1.5")));
    }
    if (!text) {
      all.add(no(text("x")));
    }
    return new FieldClass(name, List.copyOf(all));
  }

  private static Probe ok(JsonNode value) {
    return new Probe(value, true);
  }

  private static Probe no(JsonNode value) {
    return new Probe(value, false);
  }

  private static JsonNode text(String value) {
    return NODES.stringNode(value);
  }

  private static JsonNode decimal(String value) {
    return DecimalNode.valueOf(new BigDecimal(value));
  }

  private static JsonNode integer(long value) {
    return BigIntegerNode.valueOf(BigInteger.valueOf(value));
  }

  private static JsonNode bool(boolean value) {
    return NODES.booleanNode(value);
  }
}
