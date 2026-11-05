package co.com.sapenlinea.liv.bff.login;

import static org.assertj.core.api.Assertions.assertThat;

import co.com.sapenlinea.liv.bff.support.BffFlowTest;
import co.com.sapenlinea.liv.bff.support.Browser.SignIn;
import co.com.sapenlinea.liv.bff.support.OidcProvider;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * An API that answers after the timeout: the BFF cannot tell whether LIV created the session, so it
 * rejects the sign in and ends that session in LIV with the same sid, issuer and subject
 * (EndSession answers 204 even when there is nothing to end). A clear refusal does not need it
 * (001-05, 001-18).
 */
@TestPropertySource(properties = "liv.bff.api.read-timeout=300ms")
class SlowApiTest extends BffFlowTest {

  @Test
  @Tag("001-05")
  @DisplayName(
      "001-05 · Si StartSession no responde a tiempo, se rechaza y se compensa con EndSession")
  void aSlowStartSessionIsRejectedAndCompensated() throws SQLException {
    BACKEND.startSessionAnswersAfter(1500);

    SignIn signIn = browser().signIn("/liquidaciones");

    assertThat(signIn.callback().location()).isEqualTo("/bff/login");
    assertThat(BACKEND.endSessionCalls()).hasSize(1);
    assertThat(BACKEND.endSessionCalls().get(0).path())
        .isEqualTo("/internal/v1/sessions/" + OidcProvider.SESSION_ID + "/end");
    assertThat(BACKEND.endSessionCalls().get(0).body())
        .contains("\"reason\":\"PROVIDER\"")
        .contains("\"subject\":\"" + OidcProvider.SUBJECT + "\"")
        .contains("\"issuer\":\"" + OidcProvider.issuer() + "\"");
    assertThat(BACKEND.revocationCalls()).hasSize(1);
    assertThat(storedSessions()).isZero();
  }
}
