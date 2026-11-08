package co.com.sapenlinea.liv.contracts;

import static co.com.sapenlinea.liv.contracts.JurisdictionConfigSchemas.errors;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.IPIALES;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.TEMPLATE;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.load;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.remove;
import static co.com.sapenlinea.liv.contracts.TestConfigurations.set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.contracts.SafeYamlLoader.Reason;
import co.com.sapenlinea.liv.contracts.SafeYamlLoader.UnsafeYamlException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BigIntegerNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The jurisdiction configuration schema 1.0 against the acceptance criteria of the feature 004
 * (platform repository, {@code spec/features/004-esquema-configuracion-jurisdiccion/spec.md}). Each
 * invalid case starts from a valid configuration, changes one node and checks the path of the
 * error. The mutation of every node is in {@link JurisdictionConfigSchemaMutationTest}.
 */
@DisplayName("Esquema 1.0 de la configuración de jurisdicción (feature 004)")
class JurisdictionConfigSchemaTest {

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
  private static final List<String> MANDATORY =
      List.of(
          "esquema_version",
          "jurisdiccion",
          "competencia",
          "vigencia",
          "normas",
          "distribucion_recaudo");

  private static JsonNode text(String value) {
    return NODES.stringNode(value);
  }

  private static JsonNode decimal(String value) {
    return DecimalNode.valueOf(new BigDecimal(value));
  }

  private static JsonNode integer(long value) {
    return BigIntegerNode.valueOf(BigInteger.valueOf(value));
  }

  @Test
  @Tag("CJ01")
  @DisplayName("CJ01 · la plantilla y la configuración de Ipiales, tal como están, son válidas")
  void bothConfigurationsAreValid() {
    assertThat(errors(load(TEMPLATE))).isEmpty();
    assertThat(errors(load(IPIALES))).isEmpty();
  }

  @ParameterizedTest(name = "004-01 · sin la sección {0}, el error la nombra")
  @ValueSource(
      strings = {
        "esquema_version",
        "jurisdiccion",
        "competencia",
        "vigencia",
        "normas",
        "distribucion_recaudo"
      })
  @Tag("004-01")
  void mandatorySectionIsMissing(String section) {
    Set<String> expected =
        section.equals("esquema_version")
            ? Set.of(JurisdictionConfigSchemas.VERSION_PATH)
            : Set.of("/" + section);

    assertThat(errors(remove(load(IPIALES), "/" + section))).isEqualTo(expected);
  }

  @Test
  @Tag("004-01")
  @DisplayName(
      "004-01 · con solo las seis secciones obligatorias (un municipio que no liquida), es válida")
  void onlyTheMandatorySections() {
    ObjectNode configuration = load(IPIALES);
    configuration.retain(MANDATORY);

    assertThat(configuration.propertyNames()).containsExactlyInAnyOrderElementsOf(MANDATORY);
    assertThat(errors(configuration)).isEmpty();
  }

  @Test
  @Tag("004-02")
  @DisplayName(
      "004-02 · una clave que el esquema no declara, en cualquier nivel, da error con su ruta")
  void unknownKeys() {
    ObjectNode configuration = load(IPIALES);
    ((ObjectNode) configuration.at("/calendario/grupos/0"))
        .set("fecha_limite_pago_sin_interes", text("2026-04-30"));
    configuration.set("tarifa", decimal("0.015"));

    assertThat(errors(configuration))
        .containsExactlyInAnyOrder("/tarifa", "/calendario/grupos/0/fecha_limite_pago_sin_interes");
  }

  @ParameterizedTest(name = "004-03 · estado {0} es inválido")
  @ValueSource(strings = {"APROBADO", "vigente", "PUBLICADO", ""})
  @Tag("004-03")
  void estadoOutOfTheList(String estado) {
    ObjectNode configuration = set(load(IPIALES), "/tarifas/1/estado", text(estado));

    assertThat(errors(configuration)).containsExactly("/tarifas/1/estado");
  }

  @Test
  @Tag("004-03")
  @DisplayName("004-03 · los tres valores de estado son válidos")
  void estadoValues() {
    for (String estado : List.of("VIGENTE", "POR_CONFIRMAR", "PENDIENTE")) {
      assertThat(errors(set(load(IPIALES), "/redondeo/estado", text(estado)))).isEmpty();
    }
  }

  @Test
  @Tag("004-04")
  @DisplayName("004-04 · una modalidad desconocida da error con su ruta")
  void unknownModality() {
    ObjectNode configuration = load(IPIALES);
    ((ObjectNode) configuration.at("/competencia/modalidades"))
        .set("MODALIDAD_E", NODES.objectNode().put("activa", false));

    assertThat(errors(configuration)).containsExactly("/competencia/modalidades/MODALIDAD_E");
  }

  @Test
  @Tag("004-04")
  @DisplayName("004-04 · una modalidad sin activa o con activa no booleano es inválida")
  void modalityWithoutActiva() {
    String activa = "/competencia/modalidades/UEDF_INTERNACION/activa";

    assertThat(errors(remove(load(IPIALES), activa))).containsExactly(activa);
    assertThat(errors(set(load(IPIALES), activa, text("true")))).containsExactly(activa);
    assertThat(
            errors(
                set(
                    load(TEMPLATE),
                    activa.replace("UEDF_INTERNACION", "ADMINISTRACION_DIRECTA"),
                    integer(1))))
        .containsExactly(activa.replace("UEDF_INTERNACION", "ADMINISTRACION_DIRECTA"));
  }

  @Test
  @Tag("004-04")
  @DisplayName("004-04 · una modalidad ausente es válida (Ipiales no trae ADMINISTRACION_DIRECTA)")
  void absentModality() {
    assertThat(load(IPIALES).at("/competencia/modalidades/ADMINISTRACION_DIRECTA").isMissingNode())
        .isTrue();
    assertThat(errors(remove(load(TEMPLATE), "/competencia/modalidades/CONVENIO_DEPTO"))).isEmpty();
  }

  @ParameterizedTest(name = "004-05 · {0} fuera de su lista es inválido")
  @ValueSource(
      strings = {
        "/jurisdiccion/tipo",
        "/base_gravable/vehiculo_nuevo/fuente_base",
        "/beneficios/0/tipo",
        "/redondeo/metodo",
        "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/modo",
        "/calendario/vehiculos_nuevos/descuento/modo",
        "/recaudo/descuento_pago_parcial"
      })
  @Tag("004-05")
  void closedListOutOfRange(String path) {
    assertThat(errors(set(load(IPIALES), path, text("OTRO_VALOR")))).containsExactly(path);
  }

  @Test
  @Tag("004-05")
  @DisplayName("004-05 · cada valor de las listas cerradas es válido")
  void closedListValues() {
    assertValid("/jurisdiccion/tipo", "DEPARTAMENTO", "DISTRITO_CAPITAL", "DISTRITO", "MUNICIPIO");
    assertValid("/base_gravable/vehiculo_nuevo/fuente_base", "FACTURA", "DECLARACION_IMPORTACION");
    assertValid("/beneficios/0/tipo", "DESCUENTO_PORCENTUAL", "EXENCION_TOTAL", "VALOR_FIJO");
    assertValid("/redondeo/metodo", "MULTIPLO_MAS_CERCANO", "MULTIPLO_SUPERIOR", "SIN_REDONDEO");
    assertValid(
        "/calendario/vehiculos_nuevos/plazo_pago_sin_intereses/modo",
        "FIN_DE_VIGENCIA",
        "DIAS_DESDE_CAUSACION",
        "FECHA_FIJA");
    assertValid("/calendario/vehiculos_nuevos/descuento/modo", "NO_APLICA", "DIAS_DESDE_CAUSACION");
    assertValid("/recaudo/descuento_pago_parcial", "NO_APLICA", "PROPORCIONAL");
  }

  @Test
  @Tag("004-05")
  @DisplayName("004-05 · normas[].tipo queda abierto: cualquier código vale")
  void normaTipoIsOpen() {
    assertValid("/normas/0/tipo", "DECRETO_TERRITORIAL", "CIRCULAR", "ACTO_ADMINISTRATIVO");
    assertThat(errors(set(load(IPIALES), "/normas/0/tipo", text("resolución"))))
        .containsExactly("/normas/0/tipo");
  }

  @ParameterizedTest(name = "004-06 · la tarifa {0} no es una fracción entre 0 y 1")
  @ValueSource(strings = {"1.5", "1.0000000000000001", "-0.01", "15"})
  @Tag("004-06")
  void rateIsNotAFraction(String value) {
    String path = "/tarifas/0/rangos/0/tarifa";

    assertThat(errors(set(load(IPIALES), path, decimal(value)))).containsExactly(path);
  }

  @Test
  @Tag("004-06")
  @DisplayName("004-06 · 0, 1 y 1.00 son fracciones válidas")
  void fractionBounds() {
    for (String value : List.of("0", "1", "1.00", "0.0")) {
      assertThat(errors(set(load(IPIALES), "/tarifas/0/rangos/0/tarifa", decimal(value))))
          .isEmpty();
    }
  }

  @Test
  @Tag("004-06")
  @DisplayName("004-06 · un monto negativo, un entero negativo o un factor de 0 son inválidos")
  void negativeAmounts() {
    assertThat(errors(set(load(IPIALES), "/tarifas/0/rangos/1/desde_exclusivo", integer(-1))))
        .containsExactly("/tarifas/0/rangos/1/desde_exclusivo");
    assertThat(errors(set(load(IPIALES), "/recaudo/distribucion/dias_maximos_giro", integer(-1))))
        .containsExactly("/recaudo/distribucion/dias_maximos_giro");
    assertThat(errors(set(load(IPIALES), "/base_gravable/ajustes/0/factor", decimal("0"))))
        .containsExactly("/base_gravable/ajustes/0/factor");
  }

  @Test
  @Tag("004-06")
  @DisplayName("004-06 · un decimal no vale en un campo entero (1000.0 no es 1000)")
  void decimalIsNotAnInteger() {
    assertThat(errors(set(load(IPIALES), "/redondeo/multiplo", decimal("1000.0"))))
        .containsExactly("/redondeo/multiplo");
  }

  @ParameterizedTest(name = "004-07 · la fecha {0} no tiene la forma AAAA-MM-DD")
  @ValueSource(
      strings = {"30/04/2026", "2026-13-01", "2026-04-32", "2026-00-10", "2026-4-30", "26-04-30"})
  @Tag("004-07")
  void malformedDate(String value) {
    String path = "/calendario/grupos/0/fecha_limite_descuento";

    assertThat(errors(set(load(IPIALES), path, text(value)))).containsExactly(path);
  }

  @Test
  @Tag("004-07")
  @DisplayName("004-07 · una fecha con forma válida pasa aunque no exista (la revisa la 1.5.9)")
  void dateShapeOnly() {
    assertThat(errors(set(load(IPIALES), "/vigencia/fin", text("2026-02-30")))).isEmpty();
    assertThat(errors(set(load(IPIALES), "/causacion/general", text("13-01"))))
        .containsExactly("/causacion/general");
  }

  @ParameterizedTest(name = "004-07 · el código DANE {0} no tiene 2 ni 5 dígitos")
  @ValueSource(strings = {"5", "523", "5235", "523560", "5235A", "52 356"})
  @Tag("004-07")
  void malformedDane(String value) {
    assertThat(errors(set(load(IPIALES), "/jurisdiccion/codigo_dane", text(value))))
        .containsExactly("/jurisdiccion/codigo_dane");
  }

  @Test
  @Tag("004-07")
  @DisplayName("004-07 · un código DANE de 2 o 5 dígitos es válido")
  void validDane() {
    assertValid("/jurisdiccion/codigo_dane", "52", "52356", "88");
    assertThat(errors(set(load(IPIALES), "/jurisdiccion/departamento_padre_dane", text("52356"))))
        .containsExactly("/jurisdiccion/departamento_padre_dane");
  }

  @ParameterizedTest(name = "004-08 · PENDIENTE es válido en {0}")
  @ValueSource(
      strings = {
        "/calendario/grupos/0/fecha_limite_descuento",
        "/calendario/descuento_pronto_pago/tarifa",
        "/tarifas/0/rangos/0/tarifa",
        "/jurisdiccion/codigo_dane",
        "/jurisdiccion/departamento_padre_dane",
        "/jurisdiccion/nombre",
        "/jurisdiccion/administracion/nit",
        "/normas/0/numero",
        "/sujecion/motos_electricas_criterio_sujecion",
        "/recaudo/archivo_banco/formato"
      })
  @Tag("004-08")
  void pendienteWhereTheTeamFillsIn(String path) {
    assertThat(errors(set(load(IPIALES), path, text("PENDIENTE")))).isEmpty();
  }

  @ParameterizedTest(name = "004-08 · PENDIENTE es inválido en {0}")
  @ValueSource(
      strings = {
        "/redondeo/multiplo",
        "/competencia/modalidades/UEDF_INTERNACION/universo",
        "/causacion/proporcionalidad_nuevos/formula_meses",
        "/redondeo/obligatoria",
        "/redondeo/metodo",
        "/tarifas/0/rangos/0/desde_exclusivo",
        "/reportes/sibga_mensual/contenido",
        "/sujecion/no_gravados/5/excepto_si"
      })
  @Tag("004-08")
  void pendienteElsewhere(String path) {
    assertThat(errors(set(load(IPIALES), path, text("PENDIENTE")))).containsExactly(path);
  }

  @Test
  @Tag("004-08")
  @DisplayName("004-08 · un código no admite PENDIENTE ni POR_CONFIRMAR; un texto libre sí")
  void codesAndFreeText() {
    String code = "/distribucion_recaudo/origen";
    assertThat(errors(set(load(IPIALES), code, text("POR_CONFIRMAR")))).containsExactly(code);
    assertThat(errors(set(load(IPIALES), "/jurisdiccion/nombre", text("POR_CONFIRMAR")))).isEmpty();
    assertThat(errors(set(load(IPIALES), "/jurisdiccion/nombre", text(""))))
        .containsExactly("/jurisdiccion/nombre");
  }

  @ParameterizedTest(name = "004-09 · esquema_version {0} no es una versión publicada")
  @ValueSource(strings = {"2.0", "1.1", "1", "1.0.0", "PENDIENTE", "../1.0", ""})
  @Tag("004-09")
  void unknownVersion(String version) {
    ObjectNode configuration = set(load(IPIALES), "/esquema_version", text(version));

    assertThat(JurisdictionConfigSchemas.forVersion(version)).isNull();
    assertThat(errors(configuration)).containsExactly(JurisdictionConfigSchemas.VERSION_PATH);
  }

  @Test
  @Tag("004-09")
  @DisplayName("004-09 · una versión numérica no se acepta, y el esquema 1.0 solo acepta \"1.0\"")
  void versionMustBeText() {
    assertThat(errors(set(load(IPIALES), "/esquema_version", decimal("1.0"))))
        .containsExactly(JurisdictionConfigSchemas.VERSION_PATH);
    ObjectNode otherVersion = set(load(IPIALES), "/esquema_version", text("1.1"));
    assertThat(
            JurisdictionConfigSchemas.paths(
                JurisdictionConfigSchemas.forVersion("1.0").validate(otherVersion)))
        .containsExactly("/esquema_version");
  }

  @Test
  @Tag("004-10")
  @DisplayName("004-10 · una clave repetida en una configuración se rechaza antes de validar")
  void duplicateKeyInAConfiguration() throws Exception {
    String yaml =
        Files.readString(
            Path.of(
                "src",
                "test",
                "resources",
                "contracts",
                "jsonschema",
                "config-jurisdiccion",
                IPIALES));
    String duplicated =
        yaml.replaceFirst("(?m)^(  multiplo: 1000)(\\r?\\n)", "$1$2  multiplo: 100$2");
    assertThat(duplicated).isNotEqualTo(yaml);

    assertThatThrownBy(() -> SafeYamlLoader.load(duplicated))
        .isInstanceOf(UnsafeYamlException.class)
        .extracting(e -> ((UnsafeYamlException) e).reason())
        .isEqualTo(Reason.CLAVE_REPETIDA);
  }

  private static void assertValid(String path, String... values) {
    for (String value : values) {
      assertThat(errors(set(load(IPIALES), path, text(value))))
          .as("%s = %s", path, value)
          .isEmpty();
    }
  }
}
