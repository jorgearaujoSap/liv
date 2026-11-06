package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * A session that nobody comes back to also ends as it should (001-10, 001-18; T19): the sweep finds
 * it expired, tells LIV (EndSession with {@code IDLE}), revokes it in the provider and deletes it.
 * Here the inactivity is 3 seconds and the sweep runs every second.
 */
@TestPropertySource(properties = {"spring.session.timeout=3s", "liv.bff.session.sweep-interval=1s"})
class ExpiredSessionSweepTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @Tag("001-18")
  @DisplayName(
      "001-10 · Una sesión inactiva termina aunque la persona no vuelva: EndSession con IDLE, revocación y borrado")
  void anIdleSessionEndsWithoutThePersonComingBack() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();

    pause(6000);

    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).path())
        .isEqualTo("/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end");
    assertThat(BACKEND.endSessionCalls().get(0).body())
        .contains("\"reason\":\"IDLE\"")
        .contains("\"subject\":\"" + OidcProvider.SUBJECT + "\"");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Un inicio de sesión que nadie terminó se borra al vencer, sin avisar a LIV")
  void anUnfinishedSignInIsDeleted() throws SQLException {
    assertThat(browser().get(bffBase() + "/bff/login").status()).isEqualTo(302);
    assertThat(storedSessions()).isEqualTo(1);

    pause(6000);

    assertThat(storedSessions()).isZero();
    assertThat(BACKEND.calls()).isEmpty();
  }

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · El barrido toma su bloqueo en bff.shedlock: con varias réplicas, una sola barre a la vez")
  void theSweepTakesItsLock() throws SQLException {
    pause(2500);

    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(),
                BffDatabase.OWNER,
                "SELECT count(*) FROM bff.shedlock WHERE name = 'bff-expired-sessions'"))
        .isEqualTo("1");
  }
}
