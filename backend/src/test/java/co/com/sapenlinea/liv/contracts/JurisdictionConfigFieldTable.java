package co.com.sapenlinea.liv.contracts;

import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.AMOUNT;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.BOOLEAN;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.CODE;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.DANE;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.DANE_DEPARTMENT;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.DANE_IN_LIST;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.DATE;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.DIVIPOLA;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.ESTADO;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.FACTOR;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.FIELD_PATH;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.FRACTION;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.FREE_TEXT;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.IDENTIFIER;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.INTEGER;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.MONTH_DAY;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.RULE_OR_PENDING;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.SECRET_REFERENCE;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.TECHNICAL_TEXT;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.UVT_AMOUNT;
import static co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.YEAR_OR_PENDING;

import co.com.sapenlinea.liv.contracts.JurisdictionConfigFieldClasses.FieldClass;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The table of the plan of the feature 004 ("Contenido del esquema 1.0"): the class of every field
 * of the jurisdiction configuration, by its general path ({@code []} for any index), for each
 * version of the schema. The classes and their probes are in {@link
 * JurisdictionConfigFieldClasses}; {@code estado} has its own class there and is not listed here.
 */
final class JurisdictionConfigFieldTable {

  private static final String MODALITIES = "/competencia/modalidades/";

  /** The table of each version. A version without an entry fails, never takes another's. */
  private static final Map<String, Map<String, FieldClass>> BY_VERSION =
      Map.of("1.0", Map.copyOf(version10()), "1.1", Map.copyOf(version11()));

  private JurisdictionConfigFieldTable() {}

  static Set<String> versions() {
    return BY_VERSION.keySet();
  }

  static Map<String, FieldClass> classes(String version) {
    Map<String, FieldClass> classes = BY_VERSION.get(version);
    if (classes == null) {
      throw new IllegalArgumentException("no hay tabla de campos de la versión " + version);
    }
    return classes;
  }

  /** The table of the version 1.0, as the plan of the feature 004 gives it. */
  private static Map<String, FieldClass> version10() {
    Map<String, FieldClass> classes = new HashMap<>();
    put(
        classes,
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
        classes,
        IDENTIFIER,
        "/base_gravable/catalogo_ministerio/tablas[]/atributos[]",
        "/base_gravable/reglas_ubicacion/modelo_igual_o_anterior_anio_base/linea_no_encontrada",
        "/base_gravable/reglas_ubicacion/aproximacion_cilindraje/aplica_cuando",
        "/reportes/sibga_mensual/contenido",
        "/reportes/sibga_mensual/soportes[]");
    put(classes, FIELD_PATH, "/sujecion/no_gravados[]/excepto_si");
    put(
        classes,
        TECHNICAL_TEXT,
        "/base_gravable/catalogo_ministerio/columna_anio_base",
        "/base_gravable/catalogo_ministerio/tablas[]/nombre",
        "/base_gravable/reglas_ubicacion/modelo_igual_o_anterior_anio_base/usar_columna",
        "/base_gravable/ajustes[]/articulo",
        "/causacion/proporcionalidad_nuevos/formula_meses");
    put(
        classes,
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
        classes,
        FRACTION,
        MODALITIES + "UEDF_INTERNACION/distribucion[]/porcentaje",
        MODALITIES + "CONVENIO_DEPTO/distribucion[]/porcentaje",
        MODALITIES + "PARTICIPACION_20/distribucion[]/porcentaje",
        MODALITIES + "ADMINISTRACION_DIRECTA/distribucion[]/porcentaje",
        "/tarifas[]/tarifa_maxima",
        "/tarifas[]/rangos[]/tarifa",
        "/calendario/descuento_pronto_pago/tarifa",
        "/beneficios[]/tasa");
    put(classes, FACTOR, "/base_gravable/ajustes[]/factor");
    put(
        classes,
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
        classes,
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
        classes,
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
    put(classes, MONTH_DAY, "/causacion/general");
    put(classes, DANE, "/jurisdiccion/codigo_dane");
    put(classes, DANE_DEPARTMENT, "/jurisdiccion/departamento_padre_dane");
    put(classes, DIVIPOLA, "/jurisdiccion/codigo_divipola_8");
    put(
        classes,
        DANE_IN_LIST,
        "/base_gravable/ajustes[]/aplica_solo_dane[]",
        "/sujecion/servicio_publico/municipios_donde_esta_gravado[]");
    put(
        classes,
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
    JurisdictionConfigFieldClasses.closedLists("1.0")
        .forEach((path, values) ->
            put(classes, JurisdictionConfigFieldClasses.closedList(path, values), path));
    return classes;
  }

  /**
   * The table of the version 1.1 (feature 005, plan step 7): that of the 1.0, which the 1.1 keeps,
   * except {@code archivo_banco.formato}, which neither configuration of the 1.1 has any more, plus
   * the new fields of {@code recaudo} and {@code cartera}. The exceptions to the oracle (P2) are in
   * {@link JurisdictionConfigSchemaMutationTest}; {@code formato_por_banco[]} has no entry because
   * both lists are empty, and its keys are tested by the cases of {@code 1.1.casos.json}.
   */
  private static Map<String, FieldClass> version11() {
    Map<String, FieldClass> classes = new HashMap<>(version10());
    if (classes.remove("/recaudo/archivo_banco/formato") == null) {
      throw new IllegalStateException("la tabla de la 1.0 ya no trae archivo_banco.formato");
    }
    put(classes, ESTADO, "/recaudo/estado_bancos");
    put(
        classes,
        CODE,
        "/recaudo/bancos[]/codigo",
        "/recaudo/archivo_banco/formato_preferido",
        "/recaudo/archivo_banco/formato_alterno",
        "/recaudo/reporte_participacion_20/formato_propuesto",
        "/recaudo/reporte_participacion_20/periodicidad",
        "/cartera/intereses_mora/norma",
        "/cartera/intereses_mora/tasa_referencia",
        "/cartera/intereses_mora/formula",
        "/cartera/intereses_mora/dias_anio",
        "/cartera/intereses_mora/tramos",
        "/cartera/intereses_mora/inicio",
        "/cartera/intereses_mora/redondeo_causacion/empate",
        "/cartera/sanciones/reglas[]/codigo",
        "/cartera/sanciones/reglas[]/norma",
        "/cartera/sanciones/reglas[]/aplica_si",
        "/cartera/prescripcion/norma",
        "/cartera/prescripcion/eventos_interrupcion[]",
        "/cartera/prescripcion/reinicio",
        "/cartera/prescripcion/eventos_suspension[]",
        "/cartera/prescripcion/declaracion");
    put(
        classes,
        FREE_TEXT,
        "/recaudo/bancos[]/nombre",
        "/recaudo/reporte_participacion_20/remitente",
        "/cartera/prescripcion/competente");
    put(
        classes,
        FRACTION,
        "/cartera/intereses_mora/puntos_a_restar",
        "/cartera/sanciones/reglas[]/tarifa_mes_o_fraccion",
        "/cartera/sanciones/reglas[]/tope_sobre_impuesto");
    put(
        classes,
        AMOUNT,
        "/cartera/sanciones/uvt/valor",
        "/cartera/cartera_historica/tolerancia_conciliacion_pesos");
    put(classes, SECRET_REFERENCE, "/recaudo/bancos[]/cuenta_recaudo_ref");
    put(classes, UVT_AMOUNT, "/cartera/sanciones/sancion_minima_uvt");
    put(
        classes,
        INTEGER,
        "/cartera/intereses_mora/redondeo_causacion/multiplo",
        "/cartera/sanciones/uvt/vigencia",
        "/cartera/prescripcion/anios",
        "/cartera/prescripcion/semaforo_dias/rojo",
        "/cartera/prescripcion/semaforo_dias/amarillo",
        "/cartera/antiguedad/rangos_dias[]");
    put(classes, YEAR_OR_PENDING, "/cartera/cartera_historica/vigencias_desde");
    put(classes, RULE_OR_PENDING, "/cartera/sanciones/estatuto_territorial");
    put(classes, DATE, "/cartera/cartera_historica/fecha_corte_migracion");
    put(
        classes,
        BOOLEAN,
        "/cartera/intereses_mora/sobre_sanciones",
        "/cartera/intereses_mora/causacion_contable_mensual",
        "/cartera/intereses_mora/bloquear_si_falta_tasa",
        "/cartera/intereses_mora/obligatoria",
        "/cartera/sanciones/requiere_acto_en_firme",
        "/cartera/sanciones/reglas[]/activo",
        "/cartera/prescripcion/obligatoria");
    for (Map.Entry<String, List<String>> list :
        JurisdictionConfigFieldClasses.closedLists("1.1").entrySet()) {
      FieldClass fieldClass =
          JurisdictionConfigFieldClasses.closedList(list.getKey(), list.getValue());
      // The lists of the 1.0 are already there, and must be the same; the new ones are added.
      FieldClass kept = classes.putIfAbsent(list.getKey(), fieldClass);
      if (kept != null && !kept.equals(fieldClass)) {
        throw new IllegalStateException(
            "la lista cerrada cambia respecto de la 1.0: " + list.getKey());
      }
    }
    return classes;
  }

  private static void put(Map<String, FieldClass> classes, FieldClass fieldClass, String... paths) {
    for (String path : paths) {
      if (classes.put(path, fieldClass) != null) {
        throw new IllegalStateException("campo con dos clases: " + path);
      }
    }
  }
}
