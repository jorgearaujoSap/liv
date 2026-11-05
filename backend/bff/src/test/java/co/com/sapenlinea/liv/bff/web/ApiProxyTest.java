package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The BFF is the only door of the browser to the API (ADR 0010): {@code /api/**} goes to liv-api
 * with the access token of the session, which the browser never sees, and {@code /internal/**}
 * never goes anywhere (plan, step 10).
 */
class ApiProxyTest extends BffFlowTest {

  /** The access token of the person, as the BFF sent it to LIV when it started the session. */
  private String userToken;

  private Browser signedIn() {
    Browser browser = browser();
    browser.signIn("/");
    userToken = BACKEND.startSessionCalls().get(0).header("x-user-token");
    BACKEND.reset();
    return browser;
  }

  @Test
  @Tag("001-11")
  @DisplayName("001-11 · /api/** va a la API con el token de acceso de la sesión")
  void theApiReceivesTheAccessTokenOfTheSession() {
    Browser browser = signedIn();

    Reply reply = browser.get(bffBase() + "/api/v1/me?formato=resumen");

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.body()).isEqualTo("{\"ok\":true}");
    assertThat(BACKEND.apiCalls()).hasSize(1);
    assertThat(BACKEND.apiCalls().get(0).path()).isEqualTo("/api/v1/me");
    assertThat(BACKEND.apiCalls().get(0).header("authorization")).isEqualTo("Bearer " + userToken);
  }

  @Test
  @Tag("001-11")
  @DisplayName(
      "001-11 · Lo que el navegador manda como credencial no llega a la API: ni su Authorization, ni la cookie, ni X-User-Token")
  void theBrowserCredentialsAreNotForwarded() {
    Browser browser = signedIn();

    browser.get(
        bffBase() + "/api/v1/me",
        Map.of(
            "Authorization", "Bearer del-navegador",
            "X-User-Token", "del-navegador",
            "X-XSRF-TOKEN", "del-navegador"));

    var call = BACKEND.apiCalls().get(0);
    assertThat(call.header("authorization")).isEqualTo("Bearer " + userToken);
    assertThat(call.header("cookie")).as("cookie de sesión hacia la API").isNull();
    assertThat(call.header("x-user-token")).isNull();
    assertThat(call.header("x-xsrf-token")).isNull();
  }

  @Test
  @Tag("001-11")
  @DisplayName("001-11 · La respuesta de la API llega tal cual, sin ningún token y sin caché")
  void theAnswerOfTheApiPassesThroughWithoutCache() {
    Browser browser = signedIn();
    BACKEND.apiAnswers(403, "{\"status\":403,\"code\":\"001-16\"}");

    Reply reply = browser.get(bffBase() + "/api/v1/me/capabilities");

    assertThat(reply.status()).isEqualTo(403);
    assertThat(reply.body()).contains("001-16");
    assertThat(reply.header("cache-control")).contains("no-store");
    assertThat(JWT.matcher(reply.headers().toString() + reply.body()).find()).isFalse();
  }

  @Test
  @Tag("001-11")
  @DisplayName(
      "001-11 · Lo que la API pone en su respuesta (cookies, caché pública) no llega al navegador")
  void theApiCannotSetCookiesOrAllowCaching() {
    Browser browser = signedIn();
    BACKEND.apiAnswersWithHeader("Set-Cookie", "SESION=de-la-api; Path=/");
    BACKEND.apiAnswersWithHeader("Cache-Control", "public, max-age=3600");

    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.setCookies()).noneMatch(cookie -> cookie.startsWith("SESION="));
    assertThat(reply.headerValues("cache-control"))
        .as("todas las cabeceras Cache-Control")
        .anyMatch(value -> value.contains("no-store"))
        .noneMatch(value -> value.contains("public"));
  }

  @Test
  @Tag("001-14")
  @DisplayName("001-14 · Sin sesión, /api/** responde 401 y no llega a la API")
  void withoutASessionTheApiIsNotReached() {
    Reply reply = browser().get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(reply.location()).isNull();
    assertThat(BACKEND.apiCalls()).isEmpty();
  }

  @Test
  @Tag("001-20")
  @DisplayName(
      "001-20 · /internal/** responde 404 desde el BFF, con o sin sesión, y nunca llega a la API")
  void internalEndpointsAreNeverRouted() {
    Browser browser = signedIn();
    Browser withoutSession = browser();
    withoutSession.get(bffBase() + "/api/v1/me"); // the 401 hands out the anti-CSRF cookie

    assertThat(browser.get(bffBase() + "/internal/v1/sessions").status()).isEqualTo(404);
    assertThat(browser.post(bffBase() + "/internal/v1/sessions", "").status()).isEqualTo(404);
    assertThat(withoutSession.get(bffBase() + "/internal/v1/sessions").status()).isEqualTo(404);
    assertThat(withoutSession.post(bffBase() + "/internal/v1/sessions", "").status())
        .isEqualTo(404);
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @Tag("001-20")
  @DisplayName("001-20 · Una ruta de /api/** que intenta salirse hacia /internal no llega a la API")
  void pathsThatEscapeTheApiPrefixAreNotForwarded() {
    Browser browser = signedIn();

    for (String path :
        new String[] {
          "/api/%2e%2e/internal/v1/sessions",
          "/api/..;/internal/v1/sessions",
          "/api/../internal/v1/sessions",
          "//internal/v1/sessions",
          "/api/%2f%2finternal/v1/sessions",
        }) {
      Reply reply = browser.get(bffBase() + path);
      assertThat(reply.status()).as(path).isIn(400, 404);
    }
    assertThat(BACKEND.calls().stream().filter(call -> call.path().contains("internal"))).isEmpty();
  }

  @Test
  @Tag("001-11")
  @DisplayName(
      "001-11 · Las respuestas del BFF no llevan ningún JWT ni la cookie de sesión hacia la API")
  void noTokenInAnyHeader() {
    Browser browser = signedIn();

    browser.get(bffBase() + "/api/v1/me");

    for (Reply reply : browser.bffReplies()) {
      assertThat(reply.headers().toString().toLowerCase(Locale.ROOT)).doesNotContain("bearer");
      assertThat(JWT.matcher(reply.headers().toString() + reply.body()).find()).isFalse();
    }
  }
}
