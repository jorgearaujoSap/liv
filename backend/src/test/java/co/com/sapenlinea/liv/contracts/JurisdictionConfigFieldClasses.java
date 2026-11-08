package co.com.sapenlinea.liv.contracts;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /** The closed lists of 004-05, by field, with their exact values. */
  static final Map<String, List<String>> CLOSED_LISTS =
      Map.of(
          "/jurisdiccion/tipo",
              List.of("DEPARTAMENTO", "DISTRITO_CAPITAL", "DISTRITO", "MUNICIPIO"),
          "/base_gravable/vehiculo_nuevo/fuente_base",
              List.of("FACTURA", "DECLARACION_IMPORTACION"),
          "/beneficios[]/tipo", List.of("DESCUENTO_PORCENTUAL", "EXENCION_TOTAL", "VALOR_FIJO"),
          "/redondeo/metodo", List.of("MULTIPLO_MAS_CERCANO", "MULTIPLO_SUPERIOR", "SIN_REDONDEO"),
          "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/modo",
              List.of("FIN_DE_VIGENCIA", "DIAS_DESDE_CAUSACION", "FECHA_FIJA"),
          "/calendario/vehiculos_nuevos/descuento/modo",
              List.of("NO_APLICA", "DIAS_DESDE_CAUSACION"),
          "/recaudo/descuento_pago_parcial", List.of("NO_APLICA", "PROPORCIONAL"));

  private static final String MODALITIES = "/competencia/modalidades/";
  private static final Map<String, FieldClass> CLASSES = new HashMap<>();

  static {
    put(
        CODE,
        "/normas[]/id",
        "/normas[]/tipo",
        MODALITIES + "UEDF_INTERNACION/universo",
        MODALITIES + "UEDF_INTERNACION/ajuste_base",
        MODALITIES + "UEDF_INTERNACION/norma",
        MODALITIES + "UEDF_INTERNACION/distribucion[]/beneficiario",
        MODALITIES + "CONVENIO_DEPTO/universo",
        MODALITIES + "CONVENIO_DEPTO/norma",
        MODALITIES + "CONVENIO_DEPTO/convenio/periodicidad_giro_departamento",
        MODALITIES + "CONVENIO_DEPTO/distribucion[]/beneficiario",
        MODALITIES + "CONVENIO_DEPTO/distribucion[]/tratamiento",
        MODALITIES + "PARTICIPACION_20/universo",
        MODALITIES + "PARTICIPACION_20/norma",
        MODALITIES + "PARTICIPACION_20/fuente_informacion",
        MODALITIES + "PARTICIPACION_20/control",
        MODALITIES + "PARTICIPACION_20/distribucion[]/beneficiario",
        MODALITIES + "PARTICIPACION_20/distribucion[]/tratamiento",
        MODALITIES + "ADMINISTRACION_DIRECTA/universo",
        MODALITIES + "ADMINISTRACION_DIRECTA/norma",
        MODALITIES + "ADMINISTRACION_DIRECTA/distribucion[]/beneficiario",
        "/base_gravable/catalogo_ministerio/norma",
        "/base_gravable/catalogo_ministerio/llave_referencia",
        "/base_gravable/catalogo_ministerio/tablas[]/unidad_potencia",
        "/base_gravable/catalogo_ministerio/tablas[]/unidad_potencia_electricos",
        "/base_gravable/reglas_ubicacion/aproximacion_cilindraje/metodo",
        "/base_gravable/reglas_ubicacion/no_incluido_en_tablas/metodo",
        "/base_gravable/reglas_ubicacion/vigencias_anteriores/no_incluido",
        "/base_gravable/ajustes[]/codigo",
        "/base_gravable/ajustes[]/sobre",
        "/base_gravable/ajustes[]/norma",
        "/base_gravable/orden_ajustes[]",
        "/sujecion/no_gravados[]/codigo",
        "/sujecion/no_gravados[]/clases[]",
        "/categorias/reglas[]/codigo",
        "/categorias/reglas[]/si/tecnologia",
        "/categorias/reglas[]/si/servicio",
        "/tarifas[]/categoria",
        "/tarifas[]/norma",
        "/causacion/vehiculo_nuevo",
        "/calendario/norma",
        "/calendario/grupos[]/grupo",
        "/calendario/inicio_mora",
        "/calendario/descuento_pronto_pago/base",
        "/calendario/vehiculos_nuevos/causacion_hasta_limite_general",
        "/beneficios[]/codigo",
        "/beneficios[]/aplica_a/categoria",
        "/beneficios[]/norma",
        "/redondeo/momento",
        "/redondeo/empate",
        "/redondeo/aplica_a[]",
        "/distribucion_recaudo/origen",
        "/recaudo/canales[]",
        "/recaudo/recibo_pago/referencia",
        "/recaudo/recibo_pago/codigo_barras",
        "/recaudo/recibo_pago/validez_antes_limite_descuento",
        "/recaudo/recibo_pago/validez_en_mora",
        "/recaudo/archivo_banco/frecuencia",
        "/recaudo/imputacion/entre_vigencias",
        "/recaudo/imputacion/sin_indicacion",
        "/recaudo/imputacion/dentro_de_la_obligacion",
        "/recaudo/imputacion/redondeo_componentes",
        "/recaudo/saldo_a_favor/usos[]",
        "/recaudo/distribucion/base[]",
        "/recaudo/distribucion/redondeo");
    put(
        IDENTIFIER,
        "/base_gravable/catalogo_ministerio/tablas[]/atributos[]",
        "/base_gravable/reglas_ubicacion/modelo_igual_o_anterior_anio_base/linea_no_encontrada",
        "/base_gravable/reglas_ubicacion/aproximacion_cilindraje/aplica_cuando",
        "/reportes/sibga_mensual/contenido",
        "/reportes/sibga_mensual/soportes[]");
    put(FIELD_PATH, "/sujecion/no_gravados[]/excepto_si");
    put(
        TECHNICAL_TEXT,
        "/base_gravable/catalogo_ministerio/columna_anio_base",
        "/base_gravable/catalogo_ministerio/tablas[]/nombre",
        "/base_gravable/reglas_ubicacion/modelo_igual_o_anterior_anio_base/usar_columna",
        "/base_gravable/ajustes[]/articulo",
        "/causacion/proporcionalidad_nuevos/formula_meses");
    put(
        FREE_TEXT,
        "/jurisdiccion/nombre",
        "/jurisdiccion/departamento",
        "/jurisdiccion/administracion/nombre",
        "/jurisdiccion/administracion/nit",
        "/normas[]/numero",
        "/normas[]/descripcion",
        "/normas[]/autoridad",
        MODALITIES + "CONVENIO_DEPTO/convenio/numero",
        "/sujecion/motos_electricas_criterio_sujecion",
        "/recaudo/archivo_banco/formato");
    put(
        FRACTION,
        MODALITIES + "UEDF_INTERNACION/distribucion[]/porcentaje",
        MODALITIES + "CONVENIO_DEPTO/distribucion[]/porcentaje",
        MODALITIES + "PARTICIPACION_20/distribucion[]/porcentaje",
        MODALITIES + "ADMINISTRACION_DIRECTA/distribucion[]/porcentaje",
        "/tarifas[]/tarifa_maxima",
        "/tarifas[]/rangos[]/tarifa",
        "/calendario/descuento_pronto_pago/tarifa",
        "/beneficios[]/tasa");
    put(FACTOR, "/base_gravable/ajustes[]/factor");
    put(
        AMOUNT,
        "/tarifas[]/rangos[]/desde_exclusivo",
        "/tarifas[]/rangos[]/hasta_inclusivo",
        "/beneficios[]/tope_pesos",
        "/recaudo/saldo_a_favor/valor_minimo_devolucion",
        "/parametros_sistema/importacion/rango_valor_pesos/min",
        "/parametros_sistema/importacion/rango_valor_pesos/max",
        "/parametros_sistema/importacion/rango_valor_pesos_tabla_5/min",
        "/parametros_sistema/importacion/rango_valor_pesos_tabla_5/max",
        "/parametros_sistema/importacion/mediana_lote_pesos/min",
        "/parametros_sistema/importacion/mediana_lote_pesos/max");
    put(
        INTEGER,
        "/vigencia/anio",
        "/vigencia/version_config",
        "/base_gravable/catalogo_ministerio/multiplicador_tablas",
        "/base_gravable/catalogo_ministerio/anio_base",
        "/base_gravable/catalogo_ministerio/tablas[]/numero",
        "/base_gravable/reglas_ubicacion/aproximacion_cilindraje/umbral_redondeo_superior",
        "/sujecion/no_gravados[]/cilindraje_max",
        "/categorias/reglas[]/si/tabla",
        "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/dias",
        "/calendario/vehiculos_nuevos/descuento/dias",
        "/beneficios[]/anios_desde_matricula",
        "/redondeo/multiplo",
        "/recaudo/distribucion/dias_maximos_giro",
        "/parametros_sistema/homologacion/auto_aprobacion_min",
        "/parametros_sistema/homologacion/revision_min",
        "/parametros_sistema/homologacion/tolerancia_cilindraje_cc",
        "/parametros_sistema/exportacion/filas_descarga_inmediata",
        "/parametros_sistema/liquidacion_masiva/tamano_bloque");
    put(
        DATE,
        "/vigencia/inicio",
        "/vigencia/fin",
        "/normas[]/fecha",
        MODALITIES + "CONVENIO_DEPTO/convenio/fecha_inicio",
        MODALITIES + "CONVENIO_DEPTO/convenio/fecha_fin",
        "/calendario/grupos[]/fecha_inicio_pago",
        "/calendario/grupos[]/fecha_limite_descuento",
        "/calendario/grupos[]/fecha_limite_pago_sin_intereses",
        "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/fecha");
    put(MONTH_DAY, "/causacion/general");
    put(DANE, "/jurisdiccion/codigo_dane");
    put(DANE_DEPARTMENT, "/jurisdiccion/departamento_padre_dane");
    put(DIVIPOLA, "/jurisdiccion/codigo_divipola_8");
    put(
        DANE_IN_LIST,
        "/base_gravable/ajustes[]/aplica_solo_dane[]",
        "/sujecion/servicio_publico/municipios_donde_esta_gravado[]");
    put(
        BOOLEAN,
        MODALITIES + "UEDF_INTERNACION/activa",
        MODALITIES + "UEDF_INTERNACION/liquida",
        MODALITIES + "UEDF_INTERNACION/requiere/jurisdiccion.zona_frontera",
        MODALITIES + "CONVENIO_DEPTO/activa",
        MODALITIES + "CONVENIO_DEPTO/liquida",
        MODALITIES + "PARTICIPACION_20/activa",
        MODALITIES + "PARTICIPACION_20/liquida",
        MODALITIES + "ADMINISTRACION_DIRECTA/activa",
        MODALITIES + "ADMINISTRACION_DIRECTA/liquida",
        "/jurisdiccion/zona_frontera",
        "/base_gravable/vehiculo_nuevo/incluye_iva",
        "/base_gravable/reglas_ubicacion/no_incluido_en_tablas/requiere_aprobacion_segundo_usuario",
        "/base_gravable/reglas_ubicacion/no_incluido_en_tablas/reportar_a_sibga",
        "/base_gravable/reglas_ubicacion/autoliquidacion_superior_voluntaria",
        "/base_gravable/reglas_ubicacion/vigencias_anteriores/permite_modificar_base_publicada",
        "/sujecion/servicio_publico/gravado",
        "/conservacion_tarifa/activa",
        "/causacion/proporcionalidad_nuevos/activa",
        "/causacion/proporcionalidad_nuevos/fraccion_mes_cuenta_completo",
        "/calendario/descuento_pronto_pago/resultado_redondeado",
        "/calendario/obligatoria",
        "/beneficios[]/activo",
        "/redondeo/obligatoria",
        "/recaudo/saldo_a_favor/compensacion_automatica",
        "/reportes/sibga_mensual/activo");
    CLOSED_LISTS.forEach((path, values) -> put(closed(path, values.toArray(String[]::new)), path));
  }

  private JurisdictionConfigFieldClasses() {}

  /**
   * The class of a field, or null if the plan gives it none ({@code esquema_version}, which has its
   * own tests). Every {@code estado} has the class estado.
   */
  static FieldClass of(String generalPath) {
    if (generalPath.endsWith("/estado")) {
      return ESTADO;
    }
    return CLASSES.get(generalPath);
  }

  /** Every classified field, to check that the configurations have no field without a class. */
  static Map<String, FieldClass> all() {
    return Map.copyOf(CLASSES);
  }

  private static void put(FieldClass fieldClass, String... paths) {
    for (String path : paths) {
      if (CLASSES.put(path, fieldClass) != null) {
        throw new IllegalStateException("campo con dos clases: " + path);
      }
    }
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
    return new FieldClass("lista cerrada " + name, List.copyOf(probes));
  }

  private static FieldClass of(String name, Probe... probes) {
    return new FieldClass(name, List.of(probes));
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
