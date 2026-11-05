package co.com.sapenlinea.liv.bff.logout;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.FakeBackend.Call;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Signing out (001-09): the session ends in LIV (EndSession), in the identity provider (the refresh
 * token is revoked, RFC 7009) and in the BFF, and the browser is told where to go next without a
 * token or a reason.
 */
@Tag("001-09")
class LogoutFlowTest extends BffFlowTest {

  private static final String END_PATH =
      "/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end";

  private Browser signedIn() {
    Browser browser = browser();
    browser.signIn("/");
    return browser;
  }

  private Reply logout(Browser browser) {
    return browser.post(bffBase() + "/bff/logout", "");
  }

  @Test
  @DisplayName(
      "001-09 · Salir responde la ruta /bff/login, sin tokens ni motivo, y no se guarda en caché")
  void logoutAnswersTheLoginRoute() {
    Reply reply = logout(signedIn());

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.header("content-type")).startsWith("application/json");
    assertThat(reply.body().replaceAll("\\s", "")).isEqualTo("{\"redirectTo\":\"/bff/login\"}");
    assertThat(reply.header("cache-control")).contains("no-store");
    assertThat(JWT.matcher(reply.headers().toString() + reply.body()).find()).isFalse();
  }

  @Test
  @DisplayName("001-09 · Salir termina la sesión en LIV con el motivo LOGOUT")
  void logoutEndsTheSessionInLiv() {
    logout(signedIn());

    List<Call> calls = BACKEND.endSessionCalls();
    assertThat(calls).hasSize(1);
    Call call = calls.get(0);
    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo(END_PATH);
    assertThat(call.body())
        .contains("\"issuer\":\"" + OidcProvider.issuer() + "\"")
        .contains("\"subject\":\"" + OidcProvider.SUBJECT + "\"")
        .contains("\"reason\":\"LOGOUT\"");
    assertThat(call.header("authorization")).startsWith("Bearer ");
    assertThat(call.header("x-user-token")).as("EndSession solo usa el token de servicio").isNull();
  }

  @Test
  @DisplayName("001-09 · Salir revoca el refresh token en el proveedor, con el cliente liv-bff")
  void logoutRevokesTheRefreshToken() {
    logout(signedIn());

    assertThat(BACKEND.revocationCalls()).hasSize(1);
    Call call = BACKEND.revocationCalls().get(0);
    assertThat(call.body())
        .contains("token_type_hint=refresh_token")
        .containsPattern("token=[^&]+");
    assertThat(call.header("authorization"))
        .isEqualTo(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString(
                        "liv-bff:secreto-de-prueba-del-bff".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("001-09 · Primero se avisa a LIV y después se revoca en el proveedor")
  void theOrderIsEndSessionThenRevocation() {
    logout(signedIn());

    List<String> order =
        BACKEND.calls().stream()
            .map(Call::path)
            .filter(path -> path.equals(END_PATH) || path.equals("/revoke"))
            .toList();
    assertThat(order).containsExactly(END_PATH, "/revoke");
  }

  @Test
  @DisplayName("001-09 · Salir borra la sesión del BFF y la cookie")
  void logoutDeletesTheSession() throws SQLException {
    Browser browser = signedIn();
    assertThat(storedSessions()).isEqualTo(1);

    Reply reply = logout(browser);

    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
    assertThat(reply.setCookies()).anyMatch(cookie -> cookie.startsWith("LIV_SESSION="));
  }

  @Test
  @DisplayName("001-09 · Salir de nuevo con la cookie vieja responde igual y no repite nada")
  void aSecondLogoutDoesNothing() {
    Browser browser = signedIn();
    String oldCookie = browser.cookie("LIV_SESSION");
    logout(browser);
    BACKEND.reset();

    // A tab that did not hear about the logout still sends the old cookie.
    browser.setCookie("LIV_SESSION", oldCookie);
    Reply second = logout(browser);

    assertThat(second.status()).isEqualTo(200);
    assertThat(second.body().replaceAll("\\s", "")).isEqualTo("{\"redirectTo\":\"/bff/login\"}");
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @DisplayName("001-09 · Salir sin sesión responde igual y no llama a nadie")
  void logoutWithoutASessionDoesNothing() {
    Reply reply = logout(browser());

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.body().replaceAll("\\s", "")).isEqualTo("{\"redirectTo\":\"/bff/login\"}");
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @DisplayName("001-09 · Si LIV no responde, igual se revoca en el proveedor y se cierra la sesión")
  void logoutWorksEvenIfLivFails() throws SQLException {
    Browser browser = signedIn();
    BACKEND.endSessionAnswers(500);

    Reply reply = logout(browser);

    assertThat(reply.status()).isEqualTo(200);
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @DisplayName("001-09 · Si la revocación falla, igual se avisa a LIV y se cierra la sesión")
  void logoutWorksEvenIfTheRevocationFails() throws SQLException {
    Browser browser = signedIn();
    BACKEND.revocationAnswers(503);

    Reply reply = logout(browser);

    assertThat(reply.status()).isEqualTo(200);
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @DisplayName("001-09 · Si LIV y el proveedor fallan a la vez, igual se cierra la sesión del BFF")
  void logoutWorksEvenIfEverythingElseFails() throws SQLException {
    Browser browser = signedIn();
    BACKEND.endSessionAnswers(500);
    BACKEND.revocationAnswers(500);

    Reply reply = logout(browser);

    assertThat(reply.status()).isEqualTo(200);
    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
  }

  @Test
  @DisplayName("001-09 · La revocación lleva el refresh token, no el de acceso")
  void theRevokedTokenIsTheRefreshToken() {
    logout(signedIn());

    // The provider of the tests issues the refresh token as an unsigned JWT ({"alg":"none"}); its
    // access token is signed and starts with another header.
    assertThat(BACKEND.revocationCalls().get(0).body()).contains("token=eyJhbGciOiJub25lIn0");
  }

  @Test
  @DisplayName("001-09 · Salir solo se acepta con POST")
  void logoutIsNotAGet() {
    Browser browser = signedIn();

    Reply reply = browser.get(bffBase() + "/bff/logout");

    assertThat(reply.status()).isEqualTo(405);
    assertThat(BACKEND.endSessionCalls()).isEmpty();
  }
}
