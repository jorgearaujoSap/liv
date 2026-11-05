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
 * revocation, deletion) and answers 401. Here the limit is 2 seconds.
 */
@TestPropertySource(properties = "liv.bff.session.absolute-timeout=2s")
class AbsoluteLimitTest extends BffFlowTest {

  private static void waitMillis(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Pasado el límite absoluto, aunque haya actividad, responde 401 y termina la sesión con ABSOLUTE")
  void thePastTheLimitSessionEnds() throws SQLException {
    Browser browser = browser();
    browser.signIn("/");
    waitMillis(1000);
    assertThat(browser.get(bffBase() + "/api/v1/me").status())
        .as("dentro del límite")
        .isEqualTo(200);
    BACKEND.reset();

    waitMillis(1500);
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
    waitMillis(2500);
    BACKEND.reset();
    Reply past = browser.get(bffBase() + "/bff/session");

    assertThat(open.status()).isEqualTo(200);
    assertThat(SessionStatusTest.seconds(open.body(), "absoluteSecondsLeft"))
        .isLessThanOrEqualTo(2L);
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
    waitMillis(2500);
    BACKEND.reset();

    Reply reply = browser.post(bffBase() + "/bff/session/keepalive", "");

    assertThat(reply.status()).isEqualTo(401);
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }
}
