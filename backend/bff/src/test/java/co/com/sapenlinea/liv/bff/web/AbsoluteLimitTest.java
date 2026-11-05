package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.Reply;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * No session lasts more than 8 hours, however much it is used (001-10). The BFF checks it on every
 * request of the person: past the limit it ends the session (EndSession with {@code ABSOLUTE},
 * revocation, deletion) and answers 401. Here the limit is 4 seconds.
 */
@TestPropertySource(properties = "liv.bff.session.absolute-timeout=4s")
class AbsoluteLimitTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Pasado el límite absoluto, aunque haya actividad, responde 401 y termina la sesión con ABSOLUTE")
  void thePastTheLimitSessionEnds() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");
    pause(1500);
    assertThat(browser.get(bffBase() + "/api/v1/me").status())
        .as("dentro del límite")
        .isEqualTo(200);
    BACKEND.reset();

    pause(3000);
    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(BACKEND.apiCalls()).isEmpty();
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).path())
        .isEqualTo("/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end");
    assertThat(BACKEND.endSessionCalls().get(0).body())
        .contains("\"reason\":\"ABSOLUTE\"")
        .contains("\"subject\":\"" + OidcProvider.SUBJECT + "\"");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
    assertThat(browser.cookie("LIV_SESSION")).isNull();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · /bff/session informa el límite y, vencido, responde 401 sin terminar nada por su cuenta")
  void theStatusReportsTheLimit() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");

    Reply open = browser.get(bffBase() + "/bff/session");
    pause(4500);
    BACKEND.reset();
    Reply past = browser.get(bffBase() + "/bff/session");

    assertThat(open.status()).isEqualTo(200);
    assertThat(seconds(open.body(), "absoluteSecondsLeft")).isBetween(1L, 4L);
    assertThat(past.status()).isEqualTo(401);
    assertThat(BACKEND.calls()).as("preguntar no termina la sesión").isEmpty();
    assertThat(storedSessions()).as("la termina la siguiente petición de la persona").isEqualTo(1);
  }

  @Test
  @Tag("001-10")
  @DisplayName("001-10 · keepalive no pasa del límite absoluto")
  void keepAliveDoesNotPassTheLimit() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");
    pause(4500);
    BACKEND.reset();

    Reply reply = browser.post(bffBase() + "/bff/session/keepalive", "");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Salir pasado el límite absoluto responde lo de siempre y se audita como ABSOLUTE, no como LOGOUT")
  void logoutPastTheLimitIsAuditedAsAbsolute() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");
    pause(4500);
    BACKEND.reset();

    Reply reply = browser.post(bffBase() + "/bff/logout", "");

    assertThat(reply.status()).isEqualTo(200);
    assertThat(reply.body().replaceAll("\\s", "")).isEqualTo("{\"redirectTo\":\"/bff/login\"}");
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"ABSOLUTE\"");
    assertThat(storedSessions()).isZero();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Volver a /bff/login pasado el límite cierra la sesión vencida como ABSOLUTE y empieza otro inicio")
  void loginPastTheLimitEndsTheOldSessionAsAbsolute() {
    Browser browser = browser();
    browser.signIn("/");
    pause(4500);
    BACKEND.reset();

    Reply reply = browser.get(bffBase() + "/bff/login");

    assertThat(reply.status()).isEqualTo(302);
    assertThat(reply.location()).startsWith(OidcProvider.issuer());
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"ABSOLUTE\"");
  }

  @Test
  @Tag("001-11")
  @DisplayName("001-11 · El 401 por el límite no se guarda en caché")
  void theRejectionIsNotCached() {
    Browser browser = browser();
    browser.signIn("/");
    pause(4500);

    Reply reply = browser.get(bffBase() + "/api/v1/me");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(reply.header("cache-control")).contains("no-store");
  }
}
