package co.com.sapenlinea.liv.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Errores de negocio de shared (ADR 0035)")
class RuleViolationTest {

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · RuleViolation lleva el código de la regla, el tipo y los detalles")
  void carriesCodeTypeAndDetails() {
    RuleViolation violation =
        new RuleViolation(
            "V-02",
            RuleViolation.Type.UNPROCESSABLE,
            Map.of("reason", "GAP", "requiredYear", 2027));

    assertThat(violation.code()).isEqualTo("V-02");
    assertThat(violation.type()).isEqualTo(RuleViolation.Type.UNPROCESSABLE);
    assertThat(violation.details())
        .containsEntry("reason", "GAP")
        .containsEntry("requiredYear", 2027);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · Los dos tipos son CONFLICT (409) y UNPROCESSABLE (422), y no hay más")
  void hasExactlyTwoTypes() {
    assertThat(RuleViolation.Type.values())
        .containsExactly(RuleViolation.Type.CONFLICT, RuleViolation.Type.UNPROCESSABLE);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · Es una excepción sin marcar: un rechazo deshace la transacción")
  void isUnchecked() {
    assertThat(new RuleViolation("V-01", RuleViolation.Type.CONFLICT, Map.of()))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · NotFound es aparte: sin código de regla y no es una RuleViolation")
  void notFoundIsASeparateUncheckedError() {
    NotFound notFound = new NotFound("fiscal year");

    assertThat(notFound).isInstanceOf(RuntimeException.class).isNotInstanceOf(RuleViolation.class);
    assertThat(notFound.getMessage()).contains("fiscal year");
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · Los detalles quedan en el orden dado y no cambian si cambia el original")
  void detailsAreACopyInInsertionOrder() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("year", 2026);
    source.put("currentStatus", "EN_PREPARACION");

    RuleViolation violation = new RuleViolation("V-06", RuleViolation.Type.CONFLICT, source);
    source.put("extra", "x");

    assertThat(violation.details().keySet()).containsExactly("year", "currentStatus");
    assertThatThrownBy(() -> violation.details().put("other", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · Los detalles admiten texto, número y booleano")
  void acceptsScalarDetails() {
    RuleViolation violation =
        new RuleViolation(
            "V-05",
            RuleViolation.Type.UNPROCESSABLE,
            Map.of("reason", "FUTURE_LIMIT", "maxYear", 2027L, "flag", true));

    assertThat(violation.details()).hasSize(3);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · Un detalle que no es escalar se rechaza (lista, mapa, objeto, nulo)")
  void rejectsNonScalarDetails() {
    for (Object value : new Object[] {List.of(1), Map.of("a", 1), new Object()}) {
      assertThatThrownBy(
              () -> new RuleViolation("V-01", RuleViolation.Type.CONFLICT, Map.of("year", value)))
          .as(String.valueOf(value))
          .isInstanceOf(IllegalArgumentException.class);
    }
    Map<String, Object> withNull = new LinkedHashMap<>();
    withNull.put("year", null);
    assertThatThrownBy(() -> new RuleViolation("V-01", RuleViolation.Type.CONFLICT, withNull))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @Tag("ADR-0035")
  @ValueSource(strings = {"type", "title", "status", "detail", "instance", "code"})
  @DisplayName("ADR 0035 · Un detalle no puede usar una clave del Problem Details")
  void rejectsReservedKeys(String key) {
    assertThatThrownBy(
            () -> new RuleViolation("V-06", RuleViolation.Type.CONFLICT, Map.of(key, "x")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(key);
  }

  @ParameterizedTest
  @Tag("ADR-0035")
  @ValueSource(strings = {"", " ", "V‑01", "V 01"})
  @DisplayName("ADR 0035 · El código de la regla lleva el guion ASCII y no puede estar vacío")
  void rejectsMalformedCodes(String code) {
    assertThatThrownBy(() -> new RuleViolation(code, RuleViolation.Type.CONFLICT, Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · Sin código, sin tipo o sin detalles se rechaza")
  void rejectsNulls() {
    assertThatThrownBy(() -> new RuleViolation(null, RuleViolation.Type.CONFLICT, Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RuleViolation("V-01", null, Map.of()))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new RuleViolation("V-01", RuleViolation.Type.CONFLICT, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Tag("ADR-0035")
  @DisplayName("ADR 0035 · El mensaje del registro lleva el código y el tipo")
  void messageHasCodeAndType() {
    RuleViolation violation =
        new RuleViolation("V-01", RuleViolation.Type.CONFLICT, Map.of("year", 2026));

    assertThat(violation.getMessage()).contains("V-01").contains("CONFLICT");
  }
}
