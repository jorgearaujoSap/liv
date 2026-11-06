package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code returnTo} is the route LIV asked for before the sign in. Only a path of LIV itself may
 * come back: anything else would let a link on the internet send a person who just signed in to
 * another site (open redirect, 001-01).
 */
@Tag("001-01")
class ReturnToTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/",
        "/inicio",
        "/liquidaciones/2026",
        "/liquidaciones?anio=2026&placa=ABC123",
        "/consulta?nombre=Ana%20P%C3%A9rez",
        "/ruta#seccion",
      })
  @DisplayName("001-01 · Una ruta de LIV se conserva tal cual")
  void keepsAPathOfLiv(String path) {
    assertThat(ReturnTo.sanitize(path)).isEqualTo(path);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("001-01 · Sin returnTo, la persona vuelve al inicio")
  void withoutReturnToItGoesHome(String missing) {
    assertThat(ReturnTo.sanitize(missing)).isEqualTo("/");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://evil.example",
        "http://evil.example/inicio",
        "//evil.example",
        "///evil.example",
        "/\\evil.example",
        "\\evil.example",
        "/%5Cevil.example",
        "/%5cevil.example",
        "/%2F/evil.example",
        "/%2fevil.example",
        "javascript:alert(1)",
        "data:text/html,hola",
        "evil.example",
        "inicio",
        "/inicio\r\nSet-Cookie: a=b",
        "/inicio\n",
        "\t/evil.example",
        "/\t/evil.example",
        "/con espacio",
        "/ñandú",
        "/<script>",
        "/\"comilla",
      })
  @DisplayName("001-01 · Una dirección que no es una ruta de LIV se cambia por el inicio")
  void refusesAnythingThatLeavesLiv(String target) {
    assertThat(ReturnTo.sanitize(target)).isEqualTo("/");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/bff/logout",
        "/bff/login?returnTo=/x",
        "/login/oauth2/code/liv",
        "/%62ff/login",
        "/l%6fgin/oauth2/code/liv",
        "/./bff/login",
        "/%2e/bff/login",
        "/x/../bff/login",
        "/x/%2E%2e/bff/login",
        "/..",
        "/bff;x/login",
        "/x/..;/bff/login",
        "/login;a/oauth2/code/liv",
        "/%3Bx",
      })
  @DisplayName("001-01 · Las rutas del propio BFF no son destino de vuelta")
  void refusesTheRoutesOfTheBff(String target) {
    assertThat(ReturnTo.sanitize(target)).isEqualTo("/");
  }

  @Test
  @DisplayName("001-01 · Una ruta de más de 2.048 caracteres se cambia por el inicio")
  void refusesAVeryLongPath() {
    String path = "/" + "a".repeat(2048);
    assertThat(ReturnTo.sanitize(path)).isEqualTo("/");
    assertThat(ReturnTo.sanitize("/" + "a".repeat(2047))).hasSize(2048);
  }

  @ParameterizedTest
  @CsvSource({
    "/opcion%2F1.4, /opcion/1.4",
    "%2Fopcion%2F1.4, /opcion/1.4",
    "/opcion%2f1.4?vista=tabla, /opcion/1.4?vista=tabla",
    "/consulta?siguiente=%2Fopcion, /consulta?siguiente=%2Fopcion",
  })
  @DisplayName(
      "001-01 · Una ruta con %2F se acepta si, decodificada, sigue siendo una ruta de LIV, y vuelve decodificada")
  void acceptsAnEncodedSlashThatStaysInLiv(String target, String expected) {
    assertThat(ReturnTo.sanitize(target)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "%2F%2Fevil.example",
        "%2F%5Cevil.example",
        "/%2F%2Fevil.example",
        "%2Fbff%2Flogin",
        "/x%2F..%2Fbff/login",
        "/x%2F%2E%2E%2Fbff/login",
      })
  @DisplayName(
      "001-01 · Una ruta con %2F que, decodificada, sale de LIV o lleva al BFF se cambia por el inicio")
  void refusesAnEncodedSlashThatLeavesLiv(String target) {
    assertThat(ReturnTo.sanitize(target)).isEqualTo("/");
  }
}
