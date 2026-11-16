package co.com.sapenlinea.liv.tax.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.platform.PlatformWebTest;
import co.com.sapenlinea.liv.tax.api.contract.model.CreateBlocked;
import co.com.sapenlinea.liv.tax.api.contract.model.CreateFiscalYearRequest;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON rules that {@code tax.yaml} asks for, with the mapper of the application: the year of
 * the request is an integer, and an absent {@code createBlocked} or an absent {@code minYear} of it
 * is omitted. That they stay inside these DTO is fixed by {@code JacksonScopeTest}.
 */
@DisplayName("Reglas de JSON de las vigencias, de tax.yaml (007)")
class TaxJsonConfigurationTest extends PlatformWebTest {

  private final JsonMapper mapper;

  @Autowired
  TaxJsonConfigurationTest(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Test
  @DisplayName("tax.yaml · La lista omite createBlocked cuando no hay motivo y no lo escribe null")
  void theListOmitsAnAbsentCreateBlocked() {
    FiscalYearList list = new FiscalYearList(List.of(), true, List.of());

    assertThat(mapper.writeValueAsString(list)).doesNotContain("createBlocked");
  }

  @Test
  @DisplayName(
      "V-05 · 007-23 · createBlocked omite minYear cuando no hay y no lo escribe null; con él, lo escribe")
  void createBlockedOmitsAnAbsentMinYear() {
    // Solo prueba la omisión de lo que falta; qué motivo lleva minYear lo decide el servidor.
    CreateBlocked withoutMin = new CreateBlocked("OTHER_REASON", 2027);
    CreateBlocked withMin = new CreateBlocked("ALL_CREATED", 2027);
    withMin.setMinYear(2002);

    assertThat(mapper.writeValueAsString(withoutMin))
        .doesNotContain("minYear")
        .contains("\"maxYear\":2027");
    assertThat(mapper.writeValueAsString(withMin)).contains("\"minYear\":2002");
  }

  @Test
  @DisplayName(
      "tax.yaml · El year de la petición solo acepta un entero: no se convierte un texto ni un decimal")
  void theYearOfTheRequestIsStrict() {
    assertThat(mapper.readValue("{\"year\":2026}", CreateFiscalYearRequest.class).getYear())
        .isEqualTo(2026);
    for (String body :
        new String[] {
          "{\"year\":\"2026\"}", "{\"year\":2026.5}", "{\"year\":2026.0}", "{\"year\":true}"
        }) {
      assertThatThrownBy(() -> mapper.readValue(body, CreateFiscalYearRequest.class))
          .as(body)
          .isInstanceOf(JacksonException.class);
    }
  }
}
