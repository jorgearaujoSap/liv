package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A request that changes data needs the anti-CSRF token, which the frontend copies from the {@code
 * XSRF-TOKEN} cookie to the {@code X-XSRF-TOKEN} header (001-12). Without it the BFF refuses the
 * request and nothing reaches the API or the session.
 */
@Tag("001-12")
class CsrfTest extends BffFlowTest {

  private Browser signedIn() {
    Browser browser = browser();
    browser.signIn("/");
    BACKEND.reset();
    return browser;
  }

  @Test
  @DisplayName(
      "001-12 · Una mutación hacia /api sin el token anti-CSRF se rechaza y no llega a la API")
  void aMutationWithoutTheTokenIsRefused() {
    Browser browser = signedIn();

    Reply reply = browser.postWithoutCsrfToken(bffBase() + "/api/v1/liquidaciones", "x=1");

    assertThat(reply.status()).isEqualTo(403);
    assertThat(BACKEND.apiCalls()).isEmpty();
  }

  @Test
  @DisplayName("001-12 · Con un token anti-CSRF que no es el de la cookie, también se rechaza")
  void aWrongTokenIsRefused() {
    Browser browser = signedIn();

    Reply reply = browser.postWithoutCsrfToken(bffBase() + "/api/v1/liquidaciones", "x=1");
    Reply wrong =
        browser.post(
            bffBase() + "/api/v1/liquidaciones", "x=1", java.util.Map.of("X-XSRF-TOKEN", "otro"));

    assertThat(reply.status()).isEqualTo(403);
    assertThat(wrong.status()).isEqualTo(403);
    assertThat(BACKEND.apiCalls()).isEmpty();
  }

  @Test
  @DisplayName("001-12 · Salir sin el token anti-CSRF se rechaza y la sesión sigue abierta")
  void logoutWithoutTheTokenIsRefused() throws SQLException {
    Browser browser = signedIn();

    Reply reply = browser.postWithoutCsrfToken(bffBase() + "/bff/logout", "");

    assertThat(reply.status()).isEqualTo(403);
    assertThat(storedSessions()).isEqualTo(1);
    assertThat(BACKEND.endSessionCalls()).isEmpty();
    assertThat(BACKEND.revocationCalls()).isEmpty();
  }

  @Test
  @DisplayName("001-12 · Con el token anti-CSRF de la cookie, la mutación llega a la API")
  void aMutationWithTheTokenReachesTheApi() {
    Browser browser = signedIn();

    Reply reply = browser.post(bffBase() + "/api/v1/liquidaciones", "x=1");

    assertThat(reply.status()).isEqualTo(200);
    assertThat(BACKEND.apiCalls()).hasSize(1);
    assertThat(BACKEND.apiCalls().get(0).method()).isEqualTo("POST");
    assertThat(BACKEND.apiCalls().get(0).body()).isEqualTo("x=1");
  }

  @Test
  @DisplayName("001-12 · Una lectura (GET) no necesita el token anti-CSRF")
  void aReadDoesNotNeedTheToken() {
    Browser browser = signedIn();

    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "001-12 · La cookie XSRF-TOKEN lleva Secure y SameSite=Strict, y la lee el frontend (sin HttpOnly)")
  void theCsrfCookieAttributes() {
    Browser browser = browser();
    browser.signIn("/");

    List<String> cookies =
        browser.bffReplies().stream()
            .flatMap(reply -> reply.setCookies().stream())
            .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
            .toList();

    assertThat(cookies).isNotEmpty();
    for (String cookie : cookies) {
      String lower = cookie.toLowerCase(java.util.Locale.ROOT);
      assertThat(lower).contains("secure", "samesite=strict", "path=/").doesNotContain("httponly");
    }
  }

  @Test
  @DisplayName(
      "001-12 · El token anti-CSRF solo vale en la cabecera: un parámetro _csrf no se acepta")
  void theTokenIsOnlyReadFromTheHeader() {
    Browser browser = signedIn();
    String token = browser.cookie("XSRF-TOKEN");

    Reply reply =
        browser.postWithoutCsrfToken(bffBase() + "/api/v1/liquidaciones", "_csrf=" + token);

    assertThat(reply.status()).isEqualTo(403);
    assertThat(BACKEND.apiCalls()).isEmpty();
  }
}
