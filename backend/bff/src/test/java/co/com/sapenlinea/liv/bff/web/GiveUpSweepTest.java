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
 * If LIV never accepts the end of a session, the sweep stops trying after {@code
 * liv.bff.session.sweep-give-up-after} (1 day) and deletes it, so that no session with its tokens
 * stays forever (T19). Here the inactivity is 2 seconds, the sweep runs every second and it gives
 * up after 3 seconds.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
    properties = {
      "spring.session.timeout=2s",
      "liv.bff.session.sweep-interval=1s",
      "liv.bff.session.sweep-give-up-after=3s"
    })
class GiveUpSweepTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Si LIV nunca acepta el fin, la sesión vencida se borra al agotar el plazo de reintentos")
  void aSessionIsDeletedOnceTheRetriesRunOut() throws SQLException {
    browser().signIn("/");
    BACKEND.reset();
    BACKEND.endSessionAnswers(503);

    pause(3500);
    int keptWhileRetrying = storedSessions();
    pause(4000);

    assertThat(keptWhileRetrying).as("dentro del plazo, se reintenta").isEqualTo(1);
    assertThat(BACKEND.endSessionCalls()).as("lo intentó varias veces").hasSizeGreaterThan(1);
    assertThat(storedSessions()).as("pasado el plazo, se borra").isZero();
  }
}
