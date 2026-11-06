package co.com.sapenlinea.liv.bff.logout;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.persistence.BffDatabase;
import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * The end of a session the provider closed, that LIV did not accept at the time, is told to LIV by
 * the sweep (001-18, T19). Here the sweep runs every second.
 */
@Tag("001-09")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "liv.bff.session.sweep-interval=1s")
class PendingEndSweepTest extends BffFlowTest {

  @Test
  @Tag("001-18")
  @DisplayName(
      "001-18 · El barrido avisa a LIV el fin pendiente de una sesión que cerró el proveedor")
  void theSweepTellsThePendingEnd() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    BACKEND.endSessionAnswers(503);
    assertThat(
            new Browser(bffBase())
                .postWithoutCsrfToken(
                    bffBase() + "/bff/backchannel-logout",
                    "logout_token=" + OidcProvider.logoutToken(OidcProvider.LOGOUT))
                .status())
        .isEqualTo(200);

    BACKEND.endSessionAnswers(204);
    pause(2500);

    assertThat(BACKEND.endSessionCalls().size()).as("lo volvió a intentar").isGreaterThan(1);
    assertThat(BACKEND.endSessionCalls().get(BACKEND.endSessionCalls().size() - 1).body())
        .contains("\"reason\":\"PROVIDER\"");
    assertThat(
            BffDatabase.queryValue(
                BffDatabase.jdbcUrl(), BffDatabase.OWNER, "SELECT count(*) FROM bff.oidc_session"))
        .as("y lo olvidó")
        .isEqualTo("0");
  }
}
