package co.com.sapenlinea.liv.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.com.sapenlinea.liv.core.SessionDenied;
import co.com.sapenlinea.liv.tax.FiscalYearCreated;
import co.com.sapenlinea.liv.tax.api.contract.model.CreateFiscalYearRequest;
import co.com.sapenlinea.liv.tax.api.contract.model.FiscalYearList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON rules of the fiscal years of {@code tax} (a field that is an integer rejects a text or a
 * decimal, and an optional field without a value is omitted) belong to the DTO of that API only.
 * Everything else keeps the defaults of Jackson: the answers of the other endpoints and the
 * serialization of the events of the outbox (doc. 02, ADR 0035). The mapper is the one of the
 * application, as the web layer and the outbox of Spring Modulith use it.
 */
@DisplayName("Alcance de las reglas de JSON de las vigencias (007)")
class JacksonScopeTest extends PlatformWebTest {

  /** Any DTO that does not belong to {@code tax}. */
  record Sample(String text, Integer number) {}

  private final JsonMapper mapper;

  @Autowired
  JacksonScopeTest(JsonMapper mapper) {
    this.mapper = mapper;
  }

  @Test
  @Tag("007-05")
  @DisplayName("Otras respuestas y eventos siguen escribiendo un valor nulo como null")
  void otherTypesKeepWritingNulls() {
    assertThat(mapper.writeValueAsString(new Sample(null, null)))
        .contains("\"text\":null")
        .contains("\"number\":null");

    // Events of the outbox: the 007 one and the 001 one.
    FiscalYearCreated created =
        new FiscalYearCreated(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            2026,
            null,
            "PLANEADA",
            Instant.parse("2026-11-15T12:00:00Z"),
            null);
    assertThat(mapper.writeValueAsString(created))
        .contains("\"previousStatus\":null")
        .contains("\"correlationId\":null");
    SessionDenied denied =
        new SessionDenied(
            "001-05",
            "sid",
            null,
            null,
            null,
            Instant.parse("2026-11-15T12:00:00Z"),
            UUID.randomUUID(),
            null);
    assertThat(mapper.writeValueAsString(denied)).contains("\"userId\":null");
  }

  @Test
  @DisplayName(
      "Otros tipos siguen aceptando el texto «5» y el decimal 5.5 en un entero, como Jackson")
  void otherTypesKeepTheDefaultCoercion() {
    assertThat(mapper.readValue("{\"number\":\"5\"}", Sample.class).number()).isEqualTo(5);
    assertThat(mapper.readValue("{\"number\":5.5}", Sample.class).number()).isEqualTo(5);
  }

  @Test
  @Tag("007-03")
  @DisplayName(
      "La lista de vigencias omite createBlocked cuando no hay motivo, y no lo escribe null")
  void theListOmitsAnAbsentCreateBlocked() {
    FiscalYearList list = new FiscalYearList(List.of(), true, List.of());

    assertThat(mapper.writeValueAsString(list)).doesNotContain("createBlocked");
  }

  @Test
  @Tag("007-04")
  @DisplayName(
      "El año de la petición de crear solo acepta un entero: «2026» y 2026.5 no se convierten")
  void theYearOfTheRequestIsStrict() {
    assertThat(mapper.readValue("{\"year\":2026}", CreateFiscalYearRequest.class).getYear())
        .isEqualTo(2026);
    for (String body :
        new String[] {
          "{\"year\":\"2026\"}", "{\"year\":2026.5}", "{\"year\":2026.0}", "{\"year\":true}"
        }) {
      assertThatThrownBy(() -> mapper.readValue(body, CreateFiscalYearRequest.class))
          .as(body)
          .isInstanceOf(tools.jackson.core.JacksonException.class);
    }
  }
}
