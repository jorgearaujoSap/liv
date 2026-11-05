package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffTest;
import co.com.sapenlinea.liv.bff.support.Browser;
import co.com.sapenlinea.liv.bff.support.Browser.SignIn;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A provider that does not send the {@code sid} claim: LIV identifies the session of the provider
 * by it (StartSession, EndSession), so without it the sign in is rejected without calling LIV, and
 * the tokens obtained are revoked (001-05).
 */
class MissingSessionIdTest extends BffTest {

  @DynamicPropertySource
  static void providerWithoutSid(DynamicPropertyRegistry registry) {
    configure(registry, OidcProvider::issuerWithoutSid);
  }

  @Test
  @Tag("001-05")
  @DisplayName(
      "001-05 · Si el proveedor no envía el sid, el inicio de sesión se rechaza sin llamar a LIV")
  void loginIsRejectedWithoutTheSessionId() throws SQLException {
    Browser browser = browser();

    SignIn signIn = browser.signIn("/");

    assertThat(signIn.callback().location()).isEqualTo("/bff/login");
    assertThat(BACKEND.startSessionCalls()).isEmpty();
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }
}
