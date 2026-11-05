package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What happens at the sign in when the browser already has an open session, or when the answer of
 * the provider cannot be used. A session that LIV and the provider still know is never dropped
 * without ending it there too: the audit has to show how it ended (001-09, 001-18).
 */
class OpenSessionLoginTest extends BffFlowTest {

  private Browser signedIn() {
    Browser browser = browser();
    browser.signIn("/");
    return browser;
  }

  @Test
  @Tag("001-09")
  @DisplayName(
      "001-09 · Empezar otro inicio de sesión cierra antes la sesión abierta, en LIV y en el proveedor")
  void startingAnotherLoginEndsTheOpenSession() throws SQLException {
    Browser browser = signedIn();
    BACKEND.reset();

    Reply reply = browser.get(bffBase() + "/bff/login");

    assertThat(reply.status()).isEqualTo(302);
    assertThat(reply.location()).startsWith("http");
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"LOGOUT\"");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).as("solo queda la sesión nueva").isEqualTo(1);
    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT count(*) FROM bff.spring_session WHERE principal_name IS NOT NULL"))
        .as("sesiones con persona")
        .isEqualTo("0");
  }

  @Test
  @Tag("001-09")
  @DisplayName("001-09 · Un regreso inválido del proveedor no destruye una sesión abierta")
  void aStaleCallbackLeavesAnOpenSessionAlone() throws SQLException {
    Browser browser = signedIn();
    BACKEND.reset();

    Reply reply = browser.get(bffBase() + "/login/oauth2/code/liv?code=viejo&state=viejo");

    assertThat(reply.status()).isEqualTo(302);
    assertThat(reply.location()).isEqualTo("/bff/login");
    assertThat(storedSessions()).isEqualTo(1);
    assertThat(browser.cookie("LIV_SESSION")).isNotNull();
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @Tag("001-01")
  @DisplayName("001-01 · Si la persona cancela en el proveedor (access_denied) vuelve a /bff/login")
  void aCancelledSignInGoesBackToLogin() {
    Browser browser = browser();
    browser.get(bffBase() + "/bff/login");

    Reply reply = browser.get(bffBase() + "/login/oauth2/code/liv?error=access_denied&state=otro");

    assertThat(reply.status()).isEqualTo(302);
    assertThat(reply.location()).isEqualTo("/bff/login");
    assertThat(BACKEND.startSessionCalls()).isEmpty();
  }

  @Test
  @Tag("001-01")
  @DisplayName(
      "001-01 · El returnTo se usa una sola vez: el siguiente inicio de sesión sin él va al inicio")
  void returnToIsUsedOnce() {
    Browser browser = browser();
    assertThat(browser.signIn("/liquidaciones").callback().location()).isEqualTo("/liquidaciones");

    // Same browser, new sign in without returnTo (the previous session is ended first).
    assertThat(browser.signIn(null).callback().location()).isEqualTo("/");
  }
}
