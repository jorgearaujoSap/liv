package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * A session past its maximum duration ends even if nobody uses it again (001-10, 001-18; T19). Here
 * the limit is 3 seconds, the inactivity a minute and the sweep runs every second; the context
 * closes at the end, so its sweep does not touch the sessions of other tests.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "liv.bff.session.absolute-timeout=3s",
      "spring.session.timeout=1m",
      "liv.bff.session.sweep-interval=1s"
    })
class AbsoluteLimitSweepTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @Tag("001-18")
  @DisplayName(
      "001-10 · Pasado el límite absoluto, la sesión termina con ABSOLUTE aunque nadie vuelva a usarla")
  void aSessionPastTheLimitEndsWithoutBeingUsed() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();

    pause(5500);

    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).body()).contains("\"reason\":\"ABSOLUTE\"");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }
}
