package co.com.sapenlinea.liv.bff.web;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * A person who signs in again in the same session of the provider (the same {@code sid}) leaves an
 * old session of the BFF behind. When it expires, the sweep deletes it without ending the session
 * in LIV or in the provider: that would end the one in use (T19). In the tests every sign in gets
 * the same {@code sid}. Here the inactivity is 4 seconds and the sweep runs every second.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"spring.session.timeout=4s", "liv.bff.session.sweep-interval=1s"})
class SharedProviderSessionSweepTest extends BffFlowTest {

  @Test
  @Tag("001-10")
  @DisplayName(
      "001-10 · Una sesión vieja con el mismo sid que otra abierta se borra sin terminar la que está en uso")
  void anOldSessionWithTheSidOfAnOpenOneIsOnlyDeleted() throws SQLException {
    browser().signIn("/");
    pause(2000);
    Browser again = browser();
    again.signIn("/");
    BACKEND.reset();

    pause(3000);
    int whileTheNewIsOpen = storedSessions();
    int endsWhileTheNewIsOpen = BACKEND.endSessionCalls().size();
    int revocationsWhileTheNewIsOpen = BACKEND.revocationCalls().size();
    pause(3500);

    assertThat(whileTheNewIsOpen).as("la vieja se borró, la nueva sigue").isEqualTo(1);
    assertThat(endsWhileTheNewIsOpen).as("sin terminar la sesión en uso").isZero();
    assertThat(revocationsWhileTheNewIsOpen).isZero();
    assertThat(storedSessions()).as("la nueva también vence").isZero();
    assertThat(BACKEND.endSessionCalls()).as("y esa sí termina en LIV").hasSize(1);
  }
}
